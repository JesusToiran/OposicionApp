/* Puntuaciones puras: 0–10 para la simulación; no reproduce la transformación oficial INAP */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.TAIScore=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const clamp=(n,min,max)=>Math.min(max,Math.max(min,n));
  function section(data){
    const p=data||{},count=Number(p.count)||0,right=Number(p.right)||0,wrong=Number(p.wrong)||0;
    const raw=right-wrong/3,grade10=count?clamp(raw/count*10,0,10):0;
    return {count,right,wrong,blank:Number(p.blank)||0,ungraded:Number(p.ungraded)||0,raw,grade10};
  }
  function calculate(parts,kind){
    const theory=section((parts||{}).theory),practical=section((parts||{}).practical);
    const hasBoth=theory.count>0&&practical.count>0;
    const isMixed=kind==='mixed'||kind==='official';
    const rawTotal=theory.raw+practical.raw;
    const sumCount=theory.count+practical.count;
    const grade10=hasBoth&&isMixed?(theory.grade10+practical.grade10)/2:
      (sumCount?clamp(rawTotal/sumCount*10,0,10):0);
    return {theory,practical,rawTotal,grade10,grade100:grade10*10};
  }
  return {calculate,section};
});
