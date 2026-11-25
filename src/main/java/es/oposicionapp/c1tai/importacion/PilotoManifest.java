package es.oposicionapp.c1tai.importacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

public record PilotoManifest(String clave, String nombre, int anio, String indiceSha256,
		List<Documento> documentos) {

	public PilotoManifest {
		if (anio != 2025 || !shaValido(indiceSha256) || nombre == null || nombre.isBlank() || nombre.length() > 300
				|| !urlValida(clave) || clave.length() > 500 || documentos == null || documentos.isEmpty() || documentos.size() > 6) {
			throw new IllegalArgumentException("Metadatos del piloto 2025 no válidos.");
		}
		documentos = List.copyOf(documentos);
		if (documentos.stream().map(Documento::sha256).distinct().count() != documentos.size()) {
			throw new IllegalArgumentException("El manifiesto repite una identidad documental.");
		}
	}

	public static PilotoManifest leer(Path archivo) throws IOException {
		if (Files.isSymbolicLink(archivo) || !Files.isRegularFile(archivo) || Files.size(archivo) > 65536) {
			throw new IllegalArgumentException("Manifiesto no admitido.");
		}
		Properties p = new Properties();
		try (var reader = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
			p.load(reader);
		}
		int cantidad = Integer.parseInt(p.getProperty("documentos"));
		if (cantidad < 1 || cantidad > 6) throw new IllegalArgumentException("Cantidad no admitida.");
		List<Documento> documentos = new ArrayList<>();
		for (int i = 1; i <= cantidad; i++) {
			String prefijo = "documento." + i + ".";
			documentos.add(new Documento(p.getProperty(prefijo + "archivo"), p.getProperty(prefijo + "sha256"),
					p.getProperty(prefijo + "tipo"), p.getProperty(prefijo + "examen"), p.getProperty(prefijo + "titulo"),
					p.getProperty(prefijo + "url"), p.getProperty(prefijo + "evidencia")));
		}
		return new PilotoManifest(p.getProperty("convocatoria.clave"), p.getProperty("convocatoria.nombre"),
				Integer.parseInt(p.getProperty("convocatoria.anio")), p.getProperty("indice.sha256"), documentos);
	}

	public void verificarArchivos(Path directorio) throws IOException {
		Path raiz = directorio.toRealPath();
		if (Files.isSymbolicLink(directorio)) throw new IllegalArgumentException("Directorio simbólico no admitido.");
		for (Documento documento : documentos) {
			Path archivo = raiz.resolve(documento.archivo()).normalize();
			if (!archivo.startsWith(raiz) || Files.isSymbolicLink(archivo) || !Files.isRegularFile(archivo)
					|| !archivo.toRealPath().startsWith(raiz) || Files.size(archivo) > 32 * 1024 * 1024) {
				throw new IllegalArgumentException("Archivo fuera de alcance o formato no admitido.");
			}
			byte[] bytes;
			try (var stream = Files.newInputStream(archivo)) {
				bytes = stream.readNBytes(32 * 1024 * 1024 + 1);
			}
			if (bytes.length > 32 * 1024 * 1024 || bytes.length < 5
					|| !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
					|| !hash(bytes).equals(documento.sha256())) {
				throw new IllegalArgumentException("Firma PDF o SHA-256 no coincide con el inventario.");
			}
		}
	}

	static String hash(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no disponible.", exception);
		}
	}

	private static boolean shaValido(String sha) {
		return sha != null && sha.matches("[0-9a-f]{64}");
	}

	private static boolean urlValida(String value) {
		try {
			URI uri = URI.create(value);
			return "https".equals(uri.getScheme()) && "sede.inap.gob.es".equals(uri.getHost())
					&& uri.getUserInfo() == null && uri.getPort() == -1 && uri.getFragment() == null;
		} catch (IllegalArgumentException | NullPointerException exception) {
			return false;
		}
	}

	public record Documento(String archivo, String sha256, String tipo, String examen,
			String titulo, String url, String evidencia) {
		public Documento {
			if (archivo == null || !archivo.matches("[A-Za-z0-9_ -]+\\.pdf") || !shaValido(sha256)
					|| !("CUESTIONARIO".equals(tipo) || "PLANTILLA_PROVISIONAL".equals(tipo))
					|| !("ordinario".equals(examen) || "extraordinario".equals(examen))
					|| titulo == null || titulo.isBlank() || titulo.length() > 500 || !urlValida(url) || url.length() > 2048
					|| evidencia == null || evidencia.isBlank() || evidencia.length() > 700) {
				throw new IllegalArgumentException("Referencia documental no válida.");
			}
		}
	}
}
