'use strict';
/* Ultra Sentinel evidence memory — deterministic/offline; never learns from unverified bot output. */
const MAX_QUERY=1200;
const STOP=Object.freeze(['the','and','for','this','from','into','with','para','que','los','las','una','por','del','error','file','test','code','kotlin','android','github','line','app','src','main','java','com','gamehubultra']);
// Capture arithmetic intrinsics while loading trusted code, before consumers
// can alter their global prototypes or Math properties.
const TRUSTED_LOG=Math.log,TRUSTED_ROUND=Math.round;
const KNOWN=Object.freeze([
 Object.freeze({id:'pattern-voice-retry',type:'playbook',domain:'voice',rule:'SPEECH_REENTRANT_RETRY',
  summary:'SpeechRecognizer onError synchronous startListening recursion stack overflow restart retry gate lifecycle',
  advice:'Reutilizar VoiceRecognitionRetryGate; programar reintento cancelable mediante Handler y comprobar ciclo de vida.',
  tests:Object.freeze(['100 callbacks onError sin recursión','destruir sesión cancela el próximo reintento'])}),
 Object.freeze({id:'pattern-android-scope',type:'playbook',domain:'android',rule:'UNSCOPED_COROUTINE',
  summary:'GlobalScope launch coroutine cancelled Activity ViewModel Service leak lifecycle',
  advice:'Usar el CoroutineScope existente ligado al propietario, no elegir lifecycleScope sin comprobar el componente.',
  tests:Object.freeze(['cierre del propietario cancela jobs','CancellationException se propaga'])}),
 Object.freeze({id:'pattern-recursion',type:'playbook',domain:'android',rule:'UNBOUNDED_RECURSION',
  summary:'recursive function stack overflow node graph cycle visited depth limit terminating',
  advice:'Revisar función completa; acotar profundidad o visited sin introducir fallback inválido.',
  tests:Object.freeze(['grafo cíclico acotado','entrada profunda sin StackOverflowError'])}),
 Object.freeze({id:'pattern-ci-trust',type:'playbook',domain:'ci',rule:'PRIVILEGED_UNTRUSTED_CHECKOUT',
  summary:'pull_request_target privileged checkout fork untrusted code secrets permissions',
  advice:'Ejecutar scripts de la rama base confiable en job privilegiado; tratar cambios del PR como datos.',
  tests:Object.freeze(['fork no accede a secrets','permisos mínimos por job'])})
]);
// Freeze each source entry and nested tests at construction time. Keeping
// the authority private prevents untrusted consumer mutation or ID spoofing.
function isStopWord(token){
 for(let i=0;i<STOP.length;i++){
  if(STOP[i]===token)return true;
 }
 return false;
}
function terms(value){
 const s=String(value||'').slice(0,MAX_QUERY).replace(/([a-z])([A-Z])/g,'$1 $2').toLowerCase();
 const matched=s.match(/[a-z0-9_]{3,}/g);
 if(!matched)return [];
 // Only regex-delimited [a-z0-9_] tokens enter this string. Building
 // the output with the native regex engine avoids mutable Array/Set methods,
 // inherited iterators and dynamic indexed writes rejected by trusted AST.
 let accepted='';
 for(let i=0;i<matched.length;i++){
  const token=matched[i];
  if(isStopWord(token))continue;
  let duplicate=false;
  for(let j=0;j<i;j++){
   if(matched[j]===token){duplicate=true;break;}
  }
  if(!duplicate)accepted+=token+' ';
 }
 return accepted.match(/[a-z0-9_]{3,}/g)||[];
}
function hasToken(tokens,needle){
 for(let i=0;i<tokens.length;i++){
  if(tokens[i]===needle)return true;
 }
 return false;
}
function curatedTests(tests){
 if(tests.length<=4)return tests;
 return [tests[0],tests[1],tests[2],tests[3]];
}
function documentFrequency(token){
 let count=0;
 for(let i=0;i<KNOWN.length;i++){
  const entry=KNOWN[i];
  if(hasToken(terms(entry.summary+' '+entry.rule+' '+entry.domain),token))count++;
 }
 return count;
}
function vetted(entry){
 if(!entry||typeof entry!=='object')return false;
 // A matching public ID is not sufficient: an untrusted record could
 // impersonate a curated playbook and inject fabricated repair advice.
 if(entry.type==='playbook'){
  for(let i=0;i<KNOWN.length;i++){
   if(KNOWN[i]===entry)return true;
  }
  return false;
 }
 if(entry.type!=='verified_fix')return false;
 const hex=x=>/^[a-f0-9]{40}$/i.test(x||'');
 const validUrl=u=>typeof u==='string'&&/^https:\/\/github\.com\/cardenaspiero255-lang\/gamehub-ultra\/(?:pull|commit)\/[a-z0-9]+(?:\/)?$/.test(u);
 return /^[a-z0-9_-]{6,80}$/i.test(entry.id||'')&&hex(entry.fixSha)&&hex(entry.testSha)&&
  validUrl(entry.evidenceUrl)&&String(entry.summary||'').length>=20&&
  String(entry.advice||'').length>=20&&Array.isArray(entry.tests)&&entry.tests.length>0;
}
// Only a read-only lookup is exposed; the curated authority remains private.
// External records can never be registered as playbooks through this module.
function getCuratedPlaybookById(id){
 if(typeof id!=='string')return null;
 // A for-index walk does not consult mutable Array.prototype.find or iterator
 // hooks for the private frozen authority registry.
 for(let i=0;i<KNOWN.length;i++){
  const entry=KNOWN[i];
  if(entry.id===id)return entry;
 }
 return null;
}
// Avoid conditional unions of inferred object provenance in the trusted
// AST reviewer; playbooks have no URL and verified fixes carry an attested URL.
function evidenceLink(entry){
 if(entry.type==='verified_fix')return entry.evidenceUrl;
 return null;
}
// Direct search has no authority to promote caller-supplied historical fixes.
// The optional legacy array is deliberately ignored: only curated items rank.
function search(query,_extra=[],max=3){
 let rule,path,reason,text,domain;
 if(query&&typeof query==='object'){
  rule=query.rule;path=query.path;reason=query.reason;
  text=query.text;domain=query.domain;
 }else{text=String(query||'');}
 const q=terms(String(rule||'')+' '+String(path||'')+' '+String(reason||'')+' '+String(text||''));
 if(!q.length)return [];
 const limit=Math.max(0,Math.min(5,Number(max)||3));
 let first=null,second=null,third=null,fourth=null,fifth=null;
 for(let n=0;n<limit;n++){
  let best=null;
  // An Array.prototype[Symbol.iterator] override must never supply an
  // external record to the private authority. Use the frozen slots directly.
  for(let i=0;i<KNOWN.length;i++){
   const e=KNOWN[i];
   if((first!==null&&e.id===first.id)||
      (second!==null&&e.id===second.id)||
      (third!==null&&e.id===third.id)||
      (fourth!==null&&e.id===fourth.id)||
      (fifth!==null&&e.id===fifth.id))continue;
   const tokens=terms(e.summary+' '+e.rule+' '+e.domain);
   let score=0;
   for(let j=0;j<q.length;j++){
    const t=q[j];
    if(hasToken(tokens,t)){
     const freq=documentFrequency(t)||1;
     score+=TRUSTED_LOG(1+(KNOWN.length+1)/freq);
    }
   }
   if(rule&&e.rule===rule)score+=7;
   if(domain&&e.domain===domain)score+=2;
   if(score<=0)continue;
   const row={id:e.id,type:'playbook',domain:e.domain,rule:e.rule,
    score:TRUSTED_ROUND(score*1000)/1000,advice:e.advice,
    tests:curatedTests(e.tests),evidenceUrl:null,
    verification:'curated_playbook'};
   if(best===null||row.score>best.score||
      (row.score===best.score&&row.id<best.id))best=row;
  }
  if(best===null)break;
  if(n===0)first=best;
  else if(n===1)second=best;
  else if(n===2)third=best;
  else if(n===3)fourth=best;
  else fifth=best;
 }
 if(fifth!==null)return [first,second,third,fourth,fifth];
 if(fourth!==null)return [first,second,third,fourth];
 if(third!==null)return [first,second,third];
 if(second!==null)return [first,second];
 if(first!==null)return [first];
 return [];
}
// A literal export is independently attestable by the trusted-main CommonJS scanner.
module.exports={VERSION:'1.0.0',terms,vetted,search,getCuratedPlaybookById};
Object.freeze(module.exports);
