/* Adaptador de persistencia; los motores de Plan TAI conservan su lógica original. */
'use strict';
globalThis.PlanTaiBridge=(()=>{
  let revision=0,usuario='',queue=[],busy=false,timer=null,conflict=false,message='Conectando con tu cuenta…';
  const styleRules=new Map();
  const csrf=()=>({[document.querySelector('meta[name="csrf-header"]').content]:document.querySelector('meta[name="csrf-token"]').content});
  const storageKey=()=>`oposicionapp:plan-tai:pendiente:${usuario}`;
  function remember(){try{if(queue.length)localStorage.setItem(storageKey(),JSON.stringify(queue));else localStorage.removeItem(storageKey())}catch{status('No se ha podido preparar la copia de recuperación. Mantén abierta esta ventana.')}}
  function status(text){message=text;const badge=document.querySelector('.top>.tag');if(badge&&badge.textContent!==text){badge.textContent=text;badge.setAttribute('role','status');badge.setAttribute('aria-live','polite')}}
  function conflictNotice(){
    if(document.getElementById('plan-conflicto'))return;
    const box=document.createElement('aside');box.id='plan-conflicto';box.className='note';box.setAttribute('role','alert');
    const text=document.createElement('p');text.textContent='Otra ventana ha guardado cambios. Tus respuestas pendientes se conservan en este navegador y no sobrescribirán los otros resultados.';box.append(text);
    const button=document.createElement('button');button.className='btn secondary';button.textContent='Descargar copia pendiente y abrir la versión guardada';
    button.addEventListener('click',()=>{const blob=new Blob([JSON.stringify({usuario,pendiente:queue},null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download='plan-tai-recuperacion.json';link.click();URL.revokeObjectURL(url);queue=[];remember();location.reload()});box.append(button);
    document.querySelector('.shell')?.prepend(box);
  }
  async function flush(){
    clearTimeout(timer);timer=null;if(busy||!queue.length||conflict)return;
    busy=true;const entry=queue[0];entry.intentada=true;remember();status('Guardando tus cambios…');
    try{
      const response=await fetch('api/estado',{method:'PUT',credentials:'same-origin',headers:{'Content-Type':'application/json',...csrf()},body:JSON.stringify({revision:entry.revision,operacion:entry.operacion,estado:entry.estado}),keepalive:true});
      if(response.status===409){conflict=true;status('Cambios pendientes en otra ventana');conflictNotice();return}
      if(!response.ok)throw Error('Guardado pendiente');
      const result=await response.json();revision=result.revision;queue.shift();remember();status(queue.length?'Guardando tus cambios…':'Guardado en tu cuenta');
    }catch{status('Pendiente de guardar · se conserva una copia local');timer=setTimeout(flush,5000)}
    finally{busy=false;if(queue.length&&!conflict&&!timer)timer=setTimeout(flush,0)}
  }
  async function load(){
    const response=await fetch('api/estado',{credentials:'same-origin',cache:'no-store'});
    if(!response.ok)throw Error('No se ha podido recuperar tu progreso. Vuelve a iniciar sesión.');
    const result=await response.json();revision=result.revision;usuario=result.usuario;
    try{queue=JSON.parse(localStorage.getItem(storageKey())||'[]');if(!Array.isArray(queue))queue=[]}catch{queue=[]}
    if(queue.length){const state=queue.at(-1).estado;timer=null;await flush();return state}
    status('Guardado en tu cuenta');return result.estado||{};
  }
  function save(state){
    const copy=JSON.parse(JSON.stringify(state));
    const tail=queue.at(-1);
    if(tail&&!tail.intentada){tail.estado=copy;tail.operacion=crypto.randomUUID()}
    else queue.push({revision:queue.length?queue.at(-1).revision+1:revision,operacion:crypto.randomUUID(),estado:copy,intentada:false});
    remember();status('Guardando tus cambios…');clearTimeout(timer);timer=setTimeout(()=>{timer=null;flush()},200);
  }
  function html(text){
    const template=document.createElement('template');
    const clean=text.replace(/\sstyle=(["'])([\s\S]*?)\1/g,(_,quote,style)=>{
      if(!styleRules.has(style)){
        const sheet=document.querySelector('link[href^="bridge.css"]').sheet;
        const name='plan-estilo-'+styleRules.size;
        sheet.insertRule('.'+name+'{'+style+'}',sheet.cssRules.length);styleRules.set(style,name);
      }
      return ' data-plan-style="'+styleRules.get(style)+'"';
    });
    template.innerHTML=clean;
    for(const node of template.content.querySelectorAll('[data-plan-style]')){
      node.classList.add(node.getAttribute('data-plan-style'));node.removeAttribute('data-plan-style');
    }
    return template.innerHTML;
  }
  new MutationObserver(()=>{status(message);if(conflict)conflictNotice()}).observe(document.querySelector('#root'),{childList:true,subtree:true});
  addEventListener('online',()=>{timer=null;flush()});
  addEventListener('pagehide',()=>{timer=null;flush()});
  document.addEventListener('visibilitychange',()=>{if(document.hidden){timer=null;flush()}});
  return {load,save,flush,html,get revision(){return revision},get pending(){return queue.length}};
})();
