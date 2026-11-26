/* Mi Plan TAI: aplicación estática sin servidor de corrección. Los datos son tratados como contenido. */
'use strict';
const ROOT=document.querySelector('#root');
const KEY='mi-plan-tai-v1';
const SIZES=[10,15,20,30,40,60,80];
const CASES_WITH_MISSING_ATTACHMENTS=new Set(['2026-05:III']);
function hasFullCase(ex,block){let p=ex.partes.find(x=>x.bloque===block);return !!(p&&p.caso&&p.q?.length>=20&&!CASES_WITH_MISSING_ATTACHMENTS.has(ex.id+':'+block))}
const BLOCKS={I:'Organización del Estado y administración electrónica',II:'Tecnología básica',III:'Desarrollo de sistemas',IV:'Sistemas y comunicaciones',SIN_CLASIFICAR:'Pendientes de clasificar'};
let DOCUMENTARY_AUDIT=null,AUDIT_BLOCKED_IDS=new Set();
let DATA=[],GENERATED=[],DOCUMENTS=[],EXAMS=[],view='home',chosen={kind:'theory',block:'ALL',size:20,mix:false,practiceBlock:'III',origin:'historic'},active=null,ticker=null;
function saved(){return {}}
let state=saved();state.history=state.history||[];state.errors=state.errors||{};state.total=state.total||0;
function persist(){PlanTaiBridge.save(state)}
function esc(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function btn(label,fn,cls='btn'){let b=document.createElement('button');b.className=cls;b.textContent=label;b.addEventListener('click',fn);return b}
function trainingPool(origin='historic'){
 const historical=DATA.filter(q=>!AUDIT_BLOCKED_IDS.has(q.id) && q.kind==='theory' && !(['2021','2022','2024','2025'].includes(q.year)&&Number(q.row)>86) && !(/^C1_TAI_(2005|2006|2007|2008|2016)_preguntas\.csv$/.test(q.source||'')) && !(/^C1_TAI_2012_CSIC_preguntas\.csv$/.test(q.source||'')) && q.status!=='ANULADA' && !['PREGUNTA_ADAPTADA','ANULADA_REFORMULADA'].includes(q.review_status));
 return origin==='generated'?GENERATED:origin==='all'?[...historical,...GENERATED]:historical;
}
function filtered(kind,block='ALL',topic='ALL',origin='historic'){
 const src=kind==='theory'?trainingPool(origin):DATA;
 return src.filter(q=>q.kind===kind&&(block==='ALL'||q.block===block)&&(topic==='ALL'||q.topic_id===topic));
}
function count(kind,block='ALL',topic='ALL',origin='historic'){return filtered(kind,block,topic,origin).length}
function known(kind,block='ALL',topic='ALL',origin='historic'){return filtered(kind,block,topic,origin).filter(q=>q.answer!==null).length}
function timerText(s){s=Math.max(0,Math.ceil(s));let h=Math.floor(s/3600),m=Math.floor(s%3600/60),sec=s%60;return (h?h+':':'')+String(m).padStart(2,'0')+':'+String(sec).padStart(2,'0')}
function shell(content){
 ROOT.innerHTML=PlanTaiBridge.html(`<main class="shell">
   <header class="top"><button class="brand brand-button" type="button" id="goHome" aria-label="Ir al inicio"><span class="logo">TAI</span><span class="brand-copy">Mi Plan TAI<small>Integrado en OposicionApp · v2.0.1</small></span></button><a class="native-library" href="/c1-tai/examenes">Biblioteca de exámenes</a><span class="tag">Guardado en tu cuenta</span></header>
   <div id="main"></div>
   <footer class="footer">Mi Plan TAI es una herramienta independiente. No está afiliada al INAP. El banco contiene respuestas de procedencia diversa; consulta las plantillas definitivas para estudiar los exámenes oficiales. Tus resultados se guardan de forma privada en tu cuenta de OposicionApp.</footer>
 </main>`);
 document.querySelector('#main').append(content);
 document.getElementById('goHome').onclick=home;
}
function element(tag,html,cls=''){let d=document.createElement(tag);d.className=cls;d.innerHTML=PlanTaiBridge.html(html);return d}
function card(icon,title,desc,click){
 let e=element('button',`<span class="emoji">${icon}</span><span class="card-copy"><b>${esc(title)}</b><small>${esc(desc)}</small></span><span class="card-arrow" aria-hidden="true">›</span>`,'card');
 e.type='button';e.onclick=click;return e;
}
function home(){
 stopTicker();if(state.historicalSession&&view==='historical-run'){state.historicalSession.paused=true;persist()}active=null;view='home';let frag=document.createDocumentFragment();
 const done=state.total||0, todayCount=state.history.filter(s=>new Date(s.date).toDateString()===new Date().toDateString()).reduce((a,s)=>a+s.size-(s.blank||0),0),goal=Math.min(10,todayCount),validHistory=state.history.filter(s=>!(s.examId==='2019'&&s.parts?.practical?.count>0&&!['1.9.1','2.0.0'].includes(s.sourceRevision))),acc=validHistory.reduce((a,s)=>a+(s.right||0),0),answered=validHistory.reduce((a,s)=>a+(s.right||0)+(s.wrong||0),0);
 const pct=answered?Math.round(100*acc/answered):0;
 frag.append(element('section',`<div class="hero-topline">⚡ Tu preparación C1 TAI <span class="hero-update">Histórico + generado</span></div><h1>Tu plan para llegar preparado al examen</h1><p>Preguntas de convocatorias anteriores, supuestos prácticos y simulacros con corrección y nota sobre 10.</p><div class="hero-ribbon">📝 ${(DATA.length+GENERATED.length).toLocaleString('es-ES')} registros · ${GENERATED.length} didácticos no oficiales · progreso privado</div>`,'hero'));
 frag.append(element('section',`<div class="stat"><span>Respondidas</span><b>${done}</b></div><div class="stat"><span>% aciertos</span><b>${pct}%</b></div><div class="stat"><span>Para repasar</span><b>${Object.keys(state.errors).length}</b></div>`,'stats'));
 let meta=element('div',`<div class="goal-line"><b>🎯 Meta de hoy: ${goal}/10 preguntas</b><span>${goal===10?'¡Objetivo alcanzado!':'Sigue practicando'}</span></div><div class="goal-progress"><div style="width:${goal*10}%"></div></div>`,'goal');frag.append(meta);
 if(state.session){let p=element('section',`<div><b>⏳ Tienes un test sin terminar</b><p>Reanuda con las mismas respuestas y el tiempo que quedaba.</p></div>`,'resume');p.append(btn('Reanudar →',()=>{active=state.session;renderQuiz()},'btn'));frag.append(p)}
 if(state.auditWarning)frag.append(element('div','<b>Auditoría documental:</b> '+esc(state.auditWarning),'note'));
 if(state.historicalSourceWarning)frag.append(element('div','<b>Revisión de fuentes 2018/2019:</b> '+esc(state.historicalSourceWarning),'note'));
 if(state.historicalSession){let h=element('section','<div><b>Examen histórico guardado</b><p>Continúa desde la pregunta o página guardada.</p></div>','resume');h.append(btn('Reanudar histórico',()=>historicalRun()));frag.append(h)}
 if(DOCUMENTARY_AUDIT)frag.append(element('details','<summary>Estado documental de la versión 2.0</summary><p>2.477 posiciones históricas anteriores a 2026: 1.409 con cotejo textual automático; 97 incidencias consultables en el original; 796 borradores auxiliares con original obligatorio; 175 fragmentos originales. 647 claves provisionales y 66 sin plantilla. Auditoría visual pendiente. Los exámenes de 2026 mantienen su estado previo.</p>','panel'));
 frag.append(element('div','<h2>📚 Todo tu entrenamiento</h2><p>Elige cómo quieres practicar hoy.</p>','sectionline'));
 let grid=element('div','','actionlist');
 grid.append(card('📝','Exámenes históricos','INAP y documentación de otros organismos · selector único',()=>officialConfig()));
 grid.append(card('🧠','Banco generado · NO OFICIAL',`${GENERATED.length} preguntas didácticas · 4 bloques · revisión pendiente`,()=>config('theory','ALL','generated')));
 grid.append(card('⚡','Entrenamiento de 10 preguntas','Solo teoría; sin preguntas de supuestos descontextualizadas',()=>{config('theory','ALL');chosen.size=10;drawConfig()}));
 grid.append(card('🎯','Simulacro de 30 preguntas','Preguntas de los cuatro bloques',()=>{config('theory','ALL');chosen.size=30;drawConfig()}));
 grid.append(card('🎛️','Test personalizado','10, 15, 20, 30, 40, 60 u 80 preguntas; tema y bloque',()=>config('theory','ALL')));
 grid.append(card('🧩','Supuestos prácticos','Todos los años disponibles · selector único',()=>practicals()));
 grid.append(card('📚','Biblioteca de apuntes',`${DOCUMENTS.length} temas · lectura integrada sin descarga`,()=>library()));
 frag.append(grid);
 frag.append(element('div','<h2>📊 Tu progreso</h2><p>Consulta tus resultados y vuelve a practicar lo que fallaste.</p>','sectionline'));
 let track=element('div','','actionlist');track.append(card('📈','Mi análisis',`${state.history.length} pruebas terminadas · notas sobre 10`,()=>analysis()));track.append(card('📕','Cuaderno de errores',`${Object.keys(state.errors).length} preguntas pendientes de repaso`,()=>errors()));frag.append(track);
 frag.append(element('div','<h2>🧠 Entrenar por bloques</h2><p>Selecciona un bloque para configurar un test específico.</p>','sectionline'));
 let blocks=element('div','','blockgrid');[['I','🏛️'],['II','💻'],['III','🧩'],['IV','🌐']].forEach(([b,ico])=>blocks.append(card(ico,`Bloque ${b}`,`${BLOCKS[b]} · ${count('theory',b)} históricas · ${count('theory',b,'ALL','generated')} generadas`,()=>config('theory',b))));frag.append(blocks);
 frag.append(element('div','<b>Calificación transparente:</b> la aplicación muestra siempre una nota de práctica de 0 a 10. En exámenes completos también muestra teoría y supuesto por separado; esta nota orientativa no equivale automáticamente a la calificación transformada del INAP. Las explicaciones históricas conservan los comentarios y borradores aportados por la fuente, con su estado de validación. Las 480 preguntas generadas son NO OFICIALES y están pendientes de revisión independiente. Algunas claves históricas proceden de plantillas provisionales o adaptaciones.','note small-note'));
 shell(frag);
}
function config(kind='theory',block='ALL',origin='historic'){if(kind==='practical')return practicals();view='config';chosen={kind,block,size:20,mix:false,practiceBlock:'III',topic:'ALL',origin};drawConfig()}
function drawConfig(){let frag=document.createDocumentFragment();frag.append(btn('← Volver al inicio',home,'btn quiet'));frag.append(element('div',`<h2>${chosen.kind==='practical'?'Configurar supuesto práctico':'Configurar test'}</h2><p class="muted">Duración proporcional a un examen de 100 preguntas en 120 minutos: <b>72 segundos por pregunta</b>. Podrás pausar el temporizador y guardar el avance.</p>`,'sectionline'));let panel=element('div','','panel');let f=element('div','','fields');let d1=element('div','<label>Tipo de preguntas</label>','field');let select=document.createElement('select');[['theory','Test teórico'],['practical','Supuestos prácticos']].forEach(([v,t])=>{let o=new Option(t,v);select.add(o)});select.value=chosen.kind;select.onchange=()=>{if(select.value==='practical')return practicals();chosen.kind='theory';chosen.block='ALL';chosen.topic='ALL';drawConfig()};d1.append(select);let d2=element('div','<label>Bloque</label>','field');let sb=document.createElement('select');let variants=chosen.kind==='practical'?[['III','Bloque III · Desarrollo'],['IV','Bloque IV · Sistemas'],]:[['ALL','Todos los bloques'],...['I','II','III','IV'].map(k=>[k,`Bloque ${k} · ${BLOCKS[k]}`])];for(let [v,t] of variants)sb.add(new Option(t,v));if(!variants.some(x=>x[0]===chosen.block))chosen.block=variants[0][0];sb.value=chosen.block;sb.onchange=()=>{chosen.block=sb.value;chosen.topic='ALL';drawConfig()};d2.append(sb);f.append(d1,d2);
if(chosen.block!=='ALL'){
 const d3=element('div','<label>Tema del programa (clasificación orientativa)</label>','field');const st=document.createElement('select');st.add(new Option('Todos los temas del bloque','ALL'));
 let topics=[...new Map(filtered(chosen.kind,chosen.block,'ALL',chosen.origin).map(q=>[q.topic_id,q.topic_label])).entries()].sort((a,b)=>Number(a[0].split('.')[1])-Number(b[0].split('.')[1]));
 topics.forEach(([id,name])=>st.add(new Option(id+' · '+name,id)));
 st.value=chosen.topic||'ALL';st.onchange=()=>{chosen.topic=st.value;drawConfig()};d3.append(st);f.append(d3);
}
if(chosen.kind==='theory'){
 const d4=element('div','<label>Procedencia del banco</label>','field');const ss=document.createElement('select');
 [['historic','Histórico · claves de diversa procedencia'],['generated','Generadas · NO OFICIALES'],['all','Combinado · histórico + generado']].forEach(([v,t])=>ss.add(new Option(t,v)));
 ss.value=chosen.origin||'historic';ss.onchange=()=>{chosen.origin=ss.value;chosen.topic='ALL';chosen.mix=false;drawConfig()};d4.append(ss);f.append(d4);
}
panel.append(f);
if(chosen.origin!=='historic')panel.append(element('p','⚠️ Preguntas de elaboración didáctica: claves y explicaciones NO oficiales, pendientes de revisión técnica individual. Nunca se usan para reconstruir exámenes oficiales.','note'));
panel.append(element('h3','Cantidad de preguntas'));let pills=element('div','','pillset');SIZES.forEach(n=>pills.append(btn(String(n),()=>{chosen.size=n;drawConfig()},'pill'+(chosen.size===n?' active':''))));panel.append(pills);
if(chosen.kind==='theory'&&chosen.size>=40&&chosen.block==='ALL'&&chosen.origin==='historic'){
 const mix=element('div','<label style="display:block;margin:10px 0"> <input type="checkbox" id="mixpr"> Incluir un 20 % de supuesto práctico (entrenamiento mixto)</label>','field');
 panel.append(mix);mix.querySelector('input').checked=!!chosen.mix;mix.querySelector('input').onchange=e=>{chosen.mix=e.target.checked;drawConfig()};
 if(chosen.mix){let b=element('div','<label>Elegir bloque del supuesto</label>','field');let sel=document.createElement('select');[['III','Bloque III · Desarrollo'],['IV','Bloque IV · Sistemas']].forEach(([v,l])=>sel.add(new Option(l,v)));sel.value=chosen.practiceBlock;sel.onchange=e=>{chosen.practiceBlock=e.target.value;drawConfig()};b.append(sel);panel.append(b)}
}
let partN=chosen.mix&&chosen.origin==='historic'&&chosen.kind==='theory'&&chosen.size>=40&&chosen.block==='ALL'?chosen.size/5:0;
let avail=count(chosen.kind,chosen.block,chosen.topic||'ALL',chosen.origin);if(partN)avail=count('theory','ALL')>=chosen.size-partN?chosen.size:0;
panel.append(element('p',`Disponibles en este filtro: <b>${avail}</b> · Con clave conocida: <b>${known(chosen.kind,chosen.block,chosen.topic||'ALL',chosen.origin)}</b> · Tiempo inicial: <b>${timerText(chosen.size*72)}</b>`, 'muted'));let btns=element('div','','row');let start=btn('Comenzar test',startTest);start.disabled=avail<chosen.size;btns.append(start);if(state.session)btns.append(btn('Reanudar test anterior',()=>{active=state.session;renderQuiz()},'btn secondary'));panel.append(btns);if(avail<chosen.size)panel.append(element('p','No hay suficientes preguntas para el tamaño elegido. Selecciona una cantidad menor o un filtro más amplio.','note'));panel.append(element('p','Las preguntas se eligen aleatoriamente sin repetir el registro dentro del test. La navegación, las respuestas y el contador se guardan en tu cuenta.','muted'));frag.append(panel);shell(frag)}
function pick(arr,n){let copy=arr.slice();for(let i=copy.length-1;i>0;i--){let j=Math.floor(Math.random()*(i+1));[copy[i],copy[j]]=[copy[j],copy[i]]}return copy.slice(0,n)}
function startTest(){if(chosen.kind!=='theory')return practicals();let pool=trainingPool(chosen.origin).filter(q=>(chosen.block==='ALL'||q.block===chosen.block)&&(!chosen.topic||chosen.topic==='ALL'||q.topic_id===chosen.topic));if(pool.length<chosen.size&&!chosen.mix)return;let size=chosen.size,practN=chosen.mix&&chosen.origin==='historic'&&chosen.kind==='theory'&&size>=40&&chosen.block==='ALL'?size/5:0;
let selected;
if(practN){let exam=pick(EXAMS.filter(e=>hasFullCase(e,chosen.practiceBlock)),1)[0];if(!exam){alert('No hay casos completos con contexto para ese bloque');return}let part=exam.partes.find(p=>p.bloque===chosen.practiceBlock);
 if(!part||part.q.length<practN)return;
 let practicalQuestions=part.q.map((q,i)=>officialQuestion(q,'practical',exam,i,part.caso||'',chosen.practiceBlock));
 practicalQuestions.forEach(q=>DATA_BY_ID.set(q.id,q));selected=[...pick(pool,size-practN),...pick(practicalQuestions,practN)];
}else selected=pick(pool,size);
active={ids:selected.map(q=>q.id),answers:Array(size).fill(null),at:0,seconds:size*72,paused:false,lastTick:Date.now(),kind:practN?'mixed':chosen.kind,origin:chosen.origin,block:chosen.block,practicalBlock:chosen.practiceBlock,practicalCount:practN,started:new Date().toISOString()};state.session=active;persist();view='quiz';renderQuiz()}
function stopTicker(){if(ticker){clearInterval(ticker);ticker=null}}
function tick(){if(!active||view!=='quiz'||active.paused)return;let now=Date.now(),d=Math.max(0,(now-active.lastTick)/1000);active.seconds=Math.max(0,active.seconds-d);active.lastTick=now;state.session=active;persist();let t=document.getElementById('timer');if(t)t.textContent=timerText(active.seconds);if(active.seconds<=0)finishTest()}
function renderQuiz(){stopTicker();view='quiz';if(!active)return home();let questions=active.ids.map(id=>DATA_BY_ID.get(id)).filter(Boolean);if(questions.length!==active.ids.length){state.session=null;persist();return home()}let q=questions[active.at];let frag=document.createDocumentFragment();let top=element('div',`<div><b>${active.kind==='official'?'Examen completo':active.kind==='mixed'?'Test mixto':active.kind==='practical'?'Supuesto práctico':'Test teórico'}</b><div style="color:#bfcdf3;font-size:12px">Pregunta ${active.at+1} de ${active.ids.length}</div></div><div class="row"><div class="clock ${active.paused?'paused':''}" id="timer">${timerText(active.seconds)}</div></div>`,'quizhead');let pause=btn(active.paused?'▶ Reanudar':'⏸ Pausar',()=>{tick();active.paused=!active.paused;active.lastTick=Date.now();state.session=active;persist();renderQuiz()},'btn secondary');top.lastElementChild.append(pause);top.lastElementChild.append(btn('Guardar y salir',()=>{tick();state.session=active;persist();home()},'btn quiet'));frag.append(top);frag.append(element('div',`<div style="width:${100*(active.at+1)/active.ids.length}%"></div>`,'progressbar'));
if(active.paused){frag.append(element('div','⏸ Temporizador pausado. Reanuda para responder y avanzar.','pausedveil'))}
let pane=element('div','','panel question');pane.append(element('div',`<span class="badge">${q.origin==='GENERADA'?'Simulacro didáctico':esc(q.year)} · ${esc('Bloque '+q.block)}</span> <span class="badge">${q.origin==='GENERADA'?'GENERADA · NO OFICIAL · revisión pendiente':q.answer===null?'Pendiente de plantilla oficial':q.answer_status==='PROVISIONAL'?'Clave provisional':q.answer_status==='REVISION_TECNICA_NO_OFICIAL'?'Clave de revisión técnica (no oficial)':'Clave documental disponible'}</span>`));if(q.caseText)pane.append(element('div',`<details open><summary>📄 Contexto del supuesto (abrir/cerrar)</summary><p class="muted" style="white-space:pre-wrap;max-height:260px;overflow:auto">${esc(q.caseText)}</p></details>`,'note'));if(q.topic_id)pane.append(element('p',`<span class="badge">${esc('Tema '+q.topic_id+' · '+q.topic_label)}</span> ${q.topic_confidence==='baja'?'<span class="badge">Clasificación temática estimada (baja confianza)</span>':''}`,'tiny'));pane.append(element('h2',esc(q.question)));q.options.forEach((o,i)=>{let b=element('button',`<span class="optionletter">${'ABCD'[i]}.</span> ${esc(o)}`,'opt'+(active.answers[active.at]===i?' selected':''));b.disabled=active.paused;b.addEventListener('click',()=>{tick();if(!active)return;active.answers[active.at]=i;state.session=active;persist();renderQuiz()});pane.append(b)});pane.append(element('p',`Fuente: ${esc(q.source||'Examen incorporado')} · ${q.row?'Fila '+esc(q.row)+' · ':''}${q.classification==='ESTIMADA'?'Bloque estimado ('+esc(q.classification_confidence)+') · ':''}${q.origin==='GENERADA'?'Clave didáctica no oficial · revisión individual pendiente · ':''}La respuesta no se revela durante el test.`,'tiny'));let tools=element('div','','row');let prev=btn('← Anterior',()=>move(-1),'btn quiet');prev.disabled=active.paused||active.at===0;tools.append(prev);let next=btn('Siguiente →',()=>move(1),'btn secondary');next.disabled=active.paused||active.at===active.ids.length-1;tools.append(next);let finish=btn('Finalizar y corregir',finishTest,'btn');finish.disabled=active.paused;tools.append(finish);pane.append(tools);let nav=element('div','','qnav');questions.forEach((it,i)=>{let b=btn(String(i+1),()=>{tick();if(!active)return;active.at=i;active.lastTick=Date.now();state.session=active;persist();renderQuiz()},'');b.className=(active.answers[i]!==null?'done ':'')+(i===active.at?'current':'');b.disabled=active.paused;nav.append(b)});pane.append(nav);frag.append(pane);let wrap=element('div','','quiz');wrap.append(frag);shell(wrap);active.lastTick=Date.now();if(!active.paused)ticker=setInterval(tick,1000)}
function move(step){tick();active.at=Math.max(0,Math.min(active.ids.length-1,active.at+step));state.session=active;persist();renderQuiz()}
function finishTest(){
 if(!active)return;stopTicker();
 const items=active.ids.map((id,i)=>({q:DATA_BY_ID.get(id),choice:active.answers[i]}));
 const parts={theory:{right:0,wrong:0,blank:0,ungraded:0,count:0},practical:{right:0,wrong:0,blank:0,ungraded:0,count:0}};
 items.forEach(({q,choice})=>{let sec=q.kind==='practical'?'practical':'theory',p=parts[sec];p.count++;
  if(choice===null){p.blank++;return}
  if(q.answer===null||q.answer===undefined){p.ungraded++;return}
  if(choice===q.answer){p.right++;delete state.errors[q.id]}
  else{p.wrong++;state.errors[q.id]=(state.errors[q.id]||0)+1}
 });
 let totals=Object.values(parts).reduce((a,p)=>({right:a.right+p.right,wrong:a.wrong+p.wrong,blank:a.blank+p.blank,ungraded:a.ungraded+p.ungraded}),{right:0,wrong:0,blank:0,ungraded:0});
 let graded=totals.right+totals.wrong,answered=items.length-totals.blank;
 const scoring=TAIScore.calculate(parts,active.kind);
 let complete=totals.ungraded===0;
 state.total+=answered;
 const summary={date:new Date().toISOString(),size:items.length,kind:active.kind,block:active.block,examId:active.examId||null,origin:active.origin||'historic',generatedCount:items.filter(x=>x.q.origin==='GENERADA').length,
   right:totals.right,wrong:totals.wrong,ungraded:totals.ungraded,blank:totals.blank,score:Number(scoring.rawTotal.toFixed(2)),
   grade10:scoring.grade10,grade100:scoring.grade100,graded,complete,parts};
 state.history.unshift(summary);state.history=state.history.slice(0,100);state.session=null;persist();active=null;view='result';
 const fmt=x=>Number(x).toLocaleString('es-ES',{minimumFractionDigits:2,maximumFractionDigits:2});
 const hasPractical=parts.theory.count>0&&parts.practical.count>0;
 const noteLabel=complete?'Nota de entrenamiento':'Nota parcial, hay preguntas sin clave';
 const status=gradeLabel(scoring.grade10);
 const provisional=items.filter(x=>x.choice!==null&&x.q.answer_status==='PROVISIONAL').length;
 const notOfficial=items.filter(x=>x.choice!==null&&x.q.origin!=='GENERADA'&&['REVISION_TECNICA_NO_OFICIAL','REVISION_RAZONADA','PREGUNTA_ADAPTADA','ANULADA_REFORMULADA'].includes(x.q.review_status||x.q.answer_status)).length;
 const frag=document.createDocumentFragment();frag.append(btn('← Volver al inicio',home,'btn quiet'));frag.append(element('div',`<h1>🏁 Resultado de tu ${activeName(summary.kind)}</h1><p>Has finalizado ${items.length} preguntas. Esta es tu calificación de simulación con penalización por errores.</p>`,'result-title'));
 const gradeCard=element('section',`<div class="grade-ring" style="--grade:${scoring.grade10*10}%"><div><b>${fmt(scoring.grade10)}</b><small>/ 10</small></div></div><div class="grade-main"><span class="grade-eyebrow">${esc(noteLabel)}</span><h2>${status}</h2><p><b>${fmt(scoring.grade100)} / 100</b> · nota proporcional de esta simulación</p><p>Correcta +1 · Incorrecta −⅓ · En blanco 0</p></div>`,'grade-card');frag.append(gradeCard);
 frag.append(element('section',`<div class="rstat"><span>Aciertos</span><b class="okay">${totals.right}</b></div><div class="rstat"><span>Fallos</span><b class="error">${totals.wrong}</b></div><div class="rstat"><span>En blanco</span><b>${totals.blank}</b></div><div class="rstat"><span>Contestadas</span><b>${answered}</b></div>${totals.ungraded?`<div class="rstat"><span>Sin clave</span><b>${totals.ungraded}</b></div>`:''}`,'results'));
 if(hasPractical){
   let p=element('section',`<h2>Calificación por partes</h2><div class="part-grades">
      <div class="part-grade"><span>📚 Cuestionario teórico · ${parts.theory.count} preguntas</span><strong>${fmt(scoring.theory.grade10)} <small>/ 10</small></strong><small>Puntos directos: ${fmt(scoring.theory.raw)} / ${parts.theory.count}</small></div>
      <div class="part-grade"><span>🧩 Supuesto práctico · ${parts.practical.count} preguntas</span><strong>${fmt(scoring.practical.grade10)} <small>/ 10</small></strong><small>Puntos directos: ${fmt(scoring.practical.raw)} / ${parts.practical.count}</small></div>
      </div><p class="muted">La nota global pondera ambas partes por igual (50 % + 50 %), como aproximación de estudio. En el INAP las dos partes son eliminatorias y se transforman con sus propios baremos; una nota global alta no sustituye superar cada parte.</p>`,'panel');
   frag.append(p);
 }else{
   frag.append(element('section',`<h2>Detalle de la puntuación</h2><p><strong>${totals.right} − ${totals.wrong}/3 = ${fmt(scoring.rawTotal)}</strong> puntos directos de ${items.length} posibles.</p><p>Nota de simulación: max(0, puntuación directa / ${items.length} × 10) = <strong>${fmt(scoring.grade10)} / 10</strong>.</p>`,'panel'));
 }
 if(summary.kind==='official'){
   const exam=EXAMS.find(e=>e.id===summary.examId);const mins=exam&&Array.isArray(exam.min)&&exam.min.length===2?exam.min:null;
   if(mins){
     const res1=scoring.theory.raw>=Number(mins[0]); const res2=scoring.practical.raw>=Number(mins[1]);
     frag.append(element('div',`<b>Referencia de mínimos documentados para esta convocatoria:</b> teoría ${fmt(scoring.theory.raw)} / ${fmt(mins[0])} (${res1?'alcanza':'no alcanza'} el mínimo directo); supuesto ${fmt(scoring.practical.raw)} / ${fmt(mins[1])} (${res2?'alcanza':'no alcanza'} el mínimo directo).<p>La comparación es informativa. La calificación transformada oficial sobre 100 no se calcula con una simple regla de tres.</p>`,'note'));
   }
 }
 let alerts='';if(provisional)alerts+=`<p>⚠️ ${provisional} respuestas corregidas con plantilla provisional; conviene revisar su versión definitiva.</p>`;
 if(notOfficial)alerts+=`<p>⚠️ ${notOfficial} respuestas proceden de revisión técnica o adaptación didáctica, no de una plantilla oficial definitiva.</p>`;
 if(summary.generatedCount)alerts+=`<p>⚠️ ${summary.generatedCount} preguntas GENERADAS · NO OFICIALES, con claves y explicaciones pendientes de revisión individual. La nota es exclusivamente orientativa.</p>`;
 if(!complete)alerts+=`<p>⚠️ ${totals.ungraded} preguntas no tienen clave asignada. La nota mostrada es parcial.</p>`;
 if(alerts)frag.append(element('div',alerts,'note'));
 const list=element('section','','panel review-list');list.append(element('h2','🔎 Revisión de tus respuestas'));
 items.forEach(({q,choice},i)=>{
   const marker=choice===null?'En blanco':q.answer===null||q.answer===undefined?'Pendiente de clave':choice===q.answer?'Correcta':'Incorrecta';
   const cls=choice===null?'blank':q.answer===null||q.answer===undefined?'blank':choice===q.answer?'good':'bad';
   let txt=`<div class="review-heading"><span class="review-marker ${cls}">${i+1}. ${marker}</span></div><strong>${esc(q.question)}</strong><p class="muted">Tu respuesta: ${choice===null?'Sin responder':esc('ABCD'[choice]+'. '+q.options[choice])}</p>`;
   if(q.answer!==null&&q.answer!==undefined)txt+=`<p class="muted">${q.origin==='GENERADA'?'Clave didáctica (NO OFICIAL)':'Respuesta asignada'}: <b>${'ABCD'[q.answer]}. ${esc(q.options[q.answer])}</b>${q.review_status==='REVISION_RAZONADA'||q.review_status==='PREGUNTA_ADAPTADA'||q.review_status==='ANULADA_REFORMULADA'?' <em>(adaptada o no oficial)</em>':''}</p>`;
   if(q.explanation)txt+=`<div class="explain-box"><b>📘 Explicación</b><p>${esc(q.explanation)}</p>${q.common_error?`<b>⚠️ Error frecuente</b><p>${esc(q.common_error)}</p>`:''}<small>${q.origin==='GENERADA'?'Clave y explicación didácticas no verificadas individualmente.':q.explanation_method==='BORRADOR_BASADO_EN_CLAVE'?'Borrador basado en la clave; justificación técnica pendiente.':'Comentario didáctico aún no validado individualmente.'}</small>${q.historical_warning?`<p class="historical-warning">${esc(q.historical_warning)}</p>`:''}</div>`;
   const row=element('div',txt,'review-row');
   if(q.origin==='GENERADA'){
     row.append(element('p',`<span class="badge">GENERADA · NO OFICIAL</span> <span class="tiny">${esc(q.validation_status)}</span>`));
     if(q.reference_label)row.append(element('p',`Referencia declarada por el banco (no cotejada): ${esc(q.reference_label)}`,'tiny'));
     if(typeof q.reference_url==='string'&&/^https:\/\/[^\s<>"']+$/i.test(q.reference_url)){
       const link=document.createElement('a');link.href=q.reference_url;link.target='_blank';link.rel='noopener noreferrer';link.textContent='Consultar referencia externa';link.className='tiny';row.append(link);
     }
     const documentForTopic=DOCUMENTS.find(d=>d.id===q.topic_id);
     if(documentForTopic){
       const docLink=document.createElement('a');docLink.href='#';docLink.onclick=e=>{e.preventDefault();readMaterial(documentForTopic)};docLink.textContent='Consultar apunte del tema '+q.topic_id;
       docLink.className='tiny';const linkHolder=document.createElement('p');linkHolder.className='tiny';linkHolder.append(docLink);row.append(linkHolder);
     }
   }
   list.append(row);
 });
 frag.append(list);frag.append(btn('Volver a entrenar →',()=>config('theory','ALL'),'btn result-cta'));
 shell(frag);
}
function gradeLabel(g){return g>=8?'Muy buen entrenamiento':g>=5?'Buen avance; sigue practicando':'Hay conceptos que reforzar';}
function activeName(k){return k==='historical'?'examen histórico':k==='official'?'examen completo':k==='mixed'?'test mixto':k==='practical'?'supuesto práctico':'test';}
function officialConfig(){return historicalCatalog(false)}
function legacyOfficialConfig(){return historicalCatalog(false)}
function startOfficial(ex,block){
 let th=ex.partes.find(p=>p.p===1),pr=ex.partes.find(p=>p.bloque===block);if(!th||!pr||th.q.length!==80||pr.q.length!==20||!hasFullCase(ex,block)){alert('No se dispone del contexto completo de ese supuesto');return}
 let questions=[...th.q.map((q,i)=>officialQuestion(q,'theory',ex,i)),...pr.q.map((q,i)=>officialQuestion(q,'practical',ex,i,pr.caso||'',block))];
 questions.forEach(q=>DATA_BY_ID.set(q.id,q));let size=questions.length;
 active={ids:questions.map(q=>q.id),answers:Array(size).fill(null),at:0,seconds:7200,paused:false,lastTick:Date.now(),kind:'official',examId:ex.id,block,started:new Date().toISOString()};state.session=active;persist();renderQuiz();
}
function officialQuestion(q,kind,ex,i,caseText='',block='III'){
 return {id:`official:${ex.id}:${kind}:${kind==='theory'?'T':block}:${i}`,question:q.q,options:q.o,answer:Number.isInteger(q.r)?q.r:null,kind,year:ex.id,block:kind==='theory'?'I–IV':block,source:ex.tit,caseText:kind==='practical'?caseText:'',explanation:q.e||'',common_error:q.common_error||'',explanation_method:'COMENTARIO_EXAMEN_INTEGRADO',explanation_verified:false};
}
function library(){
 stopTicker();view='library';let frag=document.createDocumentFragment();frag.append(btn('← Inicio',home,'btn quiet'));
 frag.append(element('div',`<h1>📚 Biblioteca de apuntes</h1><p>${DOCUMENTS.length} temas originales aportados, agrupados por bloque. Consulta los contenidos dentro de la aplicación. Estos apuntes no constituyen cuestionarios ni verifican las claves del banco.</p>`,'sectionline'));
 for(const block of ['I','II','III','IV']){
   const docs=DOCUMENTS.filter(d=>d.block===block).sort((a,b)=>a.topic-b.topic);
   frag.append(element('h2',`Bloque ${block} · ${esc(BLOCKS[block])}`,'library-heading'));
   const section=element('section','','panel library-items');
   docs.forEach(d=>{
      const line=element('div',`<div><b>Tema ${d.topic} · ${esc(d.title)}</b><small>Lectura integrada · aportado por el usuario · ${Math.ceil(d.bytes/1024)} KB</small></div>`,'library-item');
      const a=btn('Leer tema',()=>readMaterial(d),'btn secondary');
      line.append(a);section.append(line);
   });frag.append(section);
 }
 shell(frag);
}
function practicals(){return historicalCatalog(true)}
function readMaterial(d){stopTicker();view='library-reader';const f=document.createDocumentFragment();f.append(btn('← Biblioteca',library,'btn quiet'));f.append(element('h1',esc(d.title)));const frame=document.createElement('iframe');frame.title='Apuntes de '+d.title;frame.className='study-frame';frame.setAttribute('sandbox','allow-same-origin');frame.src='./lectura/'+encodeURIComponent(d.filename.replace(/\.docx$/i,'.html'));frame.addEventListener('load',()=>{try{for(const event of ['copy','cut','contextmenu','dragstart','selectstart'])frame.contentDocument.addEventListener(event,e=>e.preventDefault())}catch(e){console.warn('Restricción de lectura no disponible',e)}});f.append(frame);f.append(element('p','Lectura integrada. La selección y copia convencional se restringen, pero no constituyen protección anticopia.','tiny'));shell(f)}
function legacyPracticals(){return historicalCatalog(true)}
function startCase(ex,block){
 if(!hasFullCase(ex,block))return;let p=ex.partes.find(x=>x.bloque===block);
 let questions=p.q.map((q,i)=>officialQuestion(q,'practical',ex,i,p.caso||'',block));questions.forEach(q=>DATA_BY_ID.set(q.id,q));
 active={ids:questions.map(q=>q.id),answers:Array(questions.length).fill(null),at:0,seconds:questions.length*72,paused:false,lastTick:Date.now(),kind:'practical',examId:ex.id,block,started:new Date().toISOString()};state.session=active;persist();renderQuiz();
}
function analysis(){
 let frag=document.createDocumentFragment();frag.append(btn('← Inicio',home,'btn quiet'));
 frag.append(element('div','<h1>📊 Mi análisis</h1><p>Todos tus test finalizados, con notas comparables de 0 a 10.</p>','result-title'));
 let hist=element('section','','panel');hist.append(element('h2',`Historial (${state.history.length})`));
 if(!state.history.length)hist.append(element('p','Todavía no has terminado ningún test.','muted'));
 state.history.forEach(s=>{const v=s.grade10!==undefined?s.grade10:TAIScore.calculate(s.parts||{},s.kind).grade10;
    const legacy=s.examId==='2019'&&s.parts?.practical?.count>0&&!['1.9.1','2.0.0'].includes(s.sourceRevision);const grade=legacy?'Pendiente de revisión':Number(v).toLocaleString('es-ES',{minimumFractionDigits:2,maximumFractionDigits:2});
    hist.append(element('div',`<div><strong>${esc(s.examTitle||activeName(s.kind))} · ${s.size} preguntas${s.generatedCount?' · '+s.generatedCount+' generadas (NO OFICIALES)':''}${s.kind==='historical'&&s.sourceRevision!=='2.0.0'?' · anterior a la revisión documental v2.0':''}</strong><small>${new Date(s.date).toLocaleString('es-ES')}</small><p>${s.right} aciertos · ${s.wrong} fallos · ${s.blank} en blanco</p></div><strong class="history-grade">${grade}${legacy?'':'<small>/10</small>'}</strong>`,'history-row'))});
 frag.append(hist);shell(frag);
}
function errors(){let frag=document.createDocumentFragment();frag.append(btn('← Inicio',home,'btn quiet'));frag.append(element('h1','Cuaderno de errores'));let entries=Object.entries(state.errors).sort((a,b)=>b[1]-a[1]);let pan=element('div','','panel');if(!entries.length)pan.append(element('p','No hay errores corregibles registrados todavía. Solo se guardan como fallos las respuestas de preguntas cuya clave está incluida en el maestro.','muted'));entries.forEach(([id,n])=>{let q=DATA_BY_ID.get(id);if(!q)return;if(AUDIT_BLOCKED_IDS.has(id)){pan.append(element('div','<b>Pregunta retirada por auditoría documental</b><p class="muted">Historial de errores conservado: '+n+'. Texto y clave no disponibles para repaso hasta revisión.</p>','listrow'));return;}pan.append(element('div',`<strong>${esc(q.question)}</strong>${q.origin==='GENERADA'?'<span class="badge">GENERADA · NO OFICIAL</span>':''}<span class="badge">Fallada ${n} veces</span><p class="muted">Clave en el maestro: ${q.answer===null?'Sin clave':esc('ABCD'[q.answer]+'. '+q.options[q.answer])}</p>${q.explanation?'<details class="explain-box"><summary>Explicación aportada · pendiente de validación</summary><p>'+esc(q.explanation)+'</p>'+(q.common_error?'<p>'+esc(q.common_error)+'</p>':'')+'</details>':''}`,'listrow'))});frag.append(pan);shell(frag)}
let DATA_BY_ID=new Map();
async function bootstrap(){try{state={history:[],errors:{},total:0,...await PlanTaiBridge.load()};let response=await fetch('./data/questions.json');if(!response.ok)throw Error('HTTP '+response.status);DATA=await response.json();
 const auditResponse=await fetch('./data/documentary_audit.json',{cache:'no-store'});if(!auditResponse.ok)throw Error('Auditoría documental HTTP '+auditResponse.status);DOCUMENTARY_AUDIT=await auditResponse.json();if(DOCUMENTARY_AUDIT.version!=='2.0.0'||!Array.isArray(DOCUMENTARY_AUDIT.blockedMasterIds)||!Array.isArray(DOCUMENTARY_AUDIT.changedPresentationIds))throw Error('Control documental no válido');AUDIT_BLOCKED_IDS=new Set(DOCUMENTARY_AUDIT.blockedMasterIds);
 let genResponse=await fetch('./data/generated_questions.json');if(!genResponse.ok)throw Error('Banco generado HTTP '+genResponse.status);GENERATED=await genResponse.json();
 let docsResponse=await fetch('./data/materiales_estudio.json');if(!docsResponse.ok)throw Error('Biblioteca HTTP '+docsResponse.status);DOCUMENTS=await docsResponse.json();
 let histResponse=await fetch('./data/historical_exams.json',{cache:'no-store'});if(!histResponse.ok)throw Error('Histórico HTTP '+histResponse.status);HISTORICAL=await histResponse.json();try{const mobileResponse=await fetch('./data/historical_mobile.json',{cache:'no-store'});if(!mobileResponse.ok)throw Error('HTTP '+mobileResponse.status);HISTORICAL_MOBILE=await mobileResponse.json()||{};}catch(e){console.warn('Lectura adaptada no disponible; se conserva el original.',e);HISTORICAL_MOBILE={};}if(state.historicalSession&&!['1.9.1','2.0.0'].includes(state.historicalSession.sourceRevision)&&['2018','2019'].includes(state.historicalSession.examId)&&state.historicalSession.sectionIds?.some(id=>id!=='T')){state.archivedHistoricalSessions=state.archivedHistoricalSessions||[];state.archivedHistoricalSessions.push(state.historicalSession);state.historicalSession=null;state.historicalSourceWarning='Se ha conservado la sesión práctica anterior en tu historial privado, pero no se reanuda porque su documento de origen ha cambiado. Inicia el ejercicio corregido.';}if(state.history.some(h=>h.examId==='2019'&&h.parts?.practical?.count>0&&!['1.9.1','2.0.0'].includes(h.sourceRevision))&&!state.historicalSourceWarning?.includes('Los resultados prácticos'))state.historicalSourceWarning=(state.historicalSourceWarning||'')+' Los resultados prácticos de 2019 anteriores a v1.9.1 deben revisarse: la asociación documental original era incorrecta.';if(state.historicalSession){const hs=state.historicalSession;if(hs.sourceRevision!=='2.0.0'){const changed=new Set(DOCUMENTARY_AUDIT.changedPresentationIds);const affected=Object.keys(hs.answers||{}).filter(k=>changed.has(hs.examId+':'+k));if(affected.length){state.archivedHistoricalSessions||=[];state.archivedHistoricalSessions.push(JSON.parse(JSON.stringify(hs)));affected.forEach(k=>delete hs.answers[k]);state.auditWarning='Se ha archivado una copia de tu examen previo. '+affected.length+' respuestas a posiciones con presentación documental modificada deberán contestarse de nuevo según el original; el resto se conserva.';}hs.sourceRevision='2.0.0';}hs.paused=true;}persist();
 let examResponse=await fetch('./data/official_exams.json');if(!examResponse.ok)throw Error('Exámenes HTTP '+examResponse.status);EXAMS=await examResponse.json();if(!Array.isArray(DATA)||!Array.isArray(GENERATED)||!Array.isArray(DOCUMENTS))throw Error('Formato de bancos inválido');
 if(GENERATED.some(q=>q.origin!=='GENERADA'||q.official!==false||q.kind!=='theory'||q.options?.length!==4||!Number.isInteger(q.answer)||q.answer<0||q.answer>3))throw Error('Banco generado no apto');
 DATA_BY_ID=new Map([...DATA,...GENERATED].map(q=>[q.id,q]));if(DATA_BY_ID.size!==DATA.length+GENERATED.length)throw Error('IDs duplicados entre bancos');EXAMS.forEach(ex=>ex.partes.forEach(p=>p.q.forEach((q,i)=>{let block=p.bloque||'III',obj=officialQuestion(q,p.p===1?'theory':'practical',ex,i,p.caso||'',block);DATA_BY_ID.set(obj.id,obj)}))); if(state.session&&(state.session.ids||[]).some(id=>AUDIT_BLOCKED_IDS.has(id))){state.archivedTrainingSessions||=[];state.archivedTrainingSessions.push(state.session);state.session=null;state.auditWarning=(state.auditWarning?state.auditWarning+' ':'')+'Se ha conservado un test anterior en tu historial privado, pero no se puede reanudar porque incluye preguntas retiradas por la auditoría.';}if(state.session){const ids=state.session.ids||[];const complete=ids.every(id=>DATA_BY_ID.has(id))&&ids.every(id=>{const q=DATA_BY_ID.get(id);return q.kind!=='practical'||!!q.caseText});if(complete){active=state.session;active.paused=true;state.session=active;}else{state.session=null;active=null;}persist()}home()}catch(err){ROOT.innerHTML=`<div class="shell"><div class="panel"><h2>No se pudo cargar el banco</h2><p>No se ha podido conectar con tu cuenta o con los contenidos. Vuelve a iniciar sesión y recarga esta página.</p><small>${esc(err.message)}</small></div></div>`}}
bootstrap();
