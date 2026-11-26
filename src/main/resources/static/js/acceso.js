'use strict';
// El enlace usa el fragmento: el token no aparece en URL HTTP, logs o Referer.
const token=new URLSearchParams(location.hash.slice(1)).get('token')||'';
history.replaceState(null,'',location.pathname+location.search);
if(/^[A-Za-z0-9_-]{43}$/.test(token)){
 document.getElementById('token').value=token;document.getElementById('confirmar').disabled=false;
}else document.getElementById('enlace-invalido').hidden=false;
