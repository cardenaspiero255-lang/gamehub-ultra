'use strict';
/* Ultra Sentinel evidence memory — deterministic/offline; never learns from unverified bot output. */
const VERSION='1.0.0',MAX_ENTRIES=500,MAX_QUERY=1200;
const STOP=new Set(['the','and','for','this','from','into','with','para','que','los','las','una','por','del','error','file','test','code','kotlin','android','github','line','app','src','main','java','com','gamehubultra']);
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
function terms(value){
 const s=String(value||'').slice(0,MAX_QUERY).replace(/([a-z])([A-Z])/g,'$1 $2').toLowerCase();
 return [...new Set((s.match(/[a-z0-9_]{3,}/g)||[]).filter(x=>!STOP.has(x)))];
}
function vetted(entry){
 if(!entry||typeof entry!=='object')return false;
 // A matching public ID is not sufficient: an untrusted record could
 // impersonate a curated playbook and inject fabricated repair advice.
 if(entry.type==='playbook')return KNOWN.includes(entry);
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
 return KNOWN.find(entry=>entry.id===id)||null;
}
// Avoid conditional unions of inferred object provenance in the trusted
// AST reviewer; playbooks have no URL and verified fixes carry an attested URL.
function evidenceLink(entry){
 if(entry.type==='verified_fix')return entry.evidenceUrl;
 return null;
}
function search(query,extra=[],max=3){
 // Keep primitive search fields separate; a conditional union of an unknown
 // object and a literal object cannot be independently AST-attested.
 let rule,path,reason,text,domain;
 if(query&&typeof query==='object'){
  rule=query.rule;path=query.path;reason=query.reason;
  text=query.text;domain=query.domain;
 }else{text=String(query||'');}
 const q=terms([rule,path,reason,text].filter(Boolean).join(' '));
 if(!q.length)return [];
 // Materialize one bounded list without a ternary union of arrays that
 // the trusted capability analyser cannot establish as a single provenance.
 const collection=[...KNOWN];
 if(Array.isArray(extra))collection.push(...extra.slice(0,MAX_ENTRIES));
 const corpus=collection.filter(vetted);
 const df=new Map();for(const e of corpus){for(const t of terms(e.summary+' '+e.rule+' '+e.domain)){df.set(t,(df.get(t)||0)+1)}}
 const scores=[];
 for(const e of corpus){
  const tokens=terms(e.summary+' '+e.rule+' '+e.domain);
  let score=0;
  for(const t of q){if(tokens.includes(t))score+=Math.log(1+(corpus.length+1)/(df.get(t)||1));}
  if(rule&&e.rule===rule)score+=7;
  if(domain&&e.domain===domain)score+=2;
  if(score<=0)continue;
  scores.push({id:e.id,type:e.type,domain:e.domain||'general',rule:e.rule||'',
    score:Number(score.toFixed(3)),advice:String(e.advice).slice(0,380),
    tests:e.tests.slice(0,4).map(t=>String(t).slice(0,180)),
    evidenceUrl:evidenceLink(e),verification:e.type==='verified_fix'?'confirmed_tests':'curated_playbook'});
 }
 return scores.sort((a,b)=>b.score-a.score||a.id.localeCompare(b.id)).slice(0,Math.max(0,Math.min(5,Number(max)||3)));
}
module.exports={VERSION,terms,vetted,search,getCuratedPlaybookById};
