'use strict';
/* Ultra Sentinel evidence memory — deterministic/offline; never learns from unverified bot output. */
const VERSION='1.0.0',MAX_ENTRIES=500,MAX_QUERY=1200;
const STOP=new Set(['the','and','for','this','from','into','with','para','que','los','las','una','por','del','error','file','test','code','kotlin','android','github','line','app','src','main','java','com','gamehubultra']);
const KNOWN=[
 {id:'pattern-voice-retry',type:'playbook',domain:'voice',rule:'SPEECH_REENTRANT_RETRY',
  summary:'SpeechRecognizer onError synchronous startListening recursion stack overflow restart retry gate lifecycle',
  advice:'Reutilizar VoiceRecognitionRetryGate; programar reintento cancelable mediante Handler y comprobar ciclo de vida.',
  tests:['100 callbacks onError sin recursión','destruir sesión cancela el próximo reintento']},
 {id:'pattern-android-scope',type:'playbook',domain:'android',rule:'UNSCOPED_COROUTINE',
  summary:'GlobalScope launch coroutine cancelled Activity ViewModel Service leak lifecycle',
  advice:'Usar el CoroutineScope existente ligado al propietario, no elegir lifecycleScope sin comprobar el componente.',
  tests:['cierre del propietario cancela jobs','CancellationException se propaga']},
 {id:'pattern-recursion',type:'playbook',domain:'android',rule:'UNBOUNDED_RECURSION',
  summary:'recursive function stack overflow node graph cycle visited depth limit terminating',
  advice:'Revisar función completa; acotar profundidad o visited sin introducir fallback inválido.',
  tests:['grafo cíclico acotado','entrada profunda sin StackOverflowError']},
 {id:'pattern-ci-trust',type:'playbook',domain:'ci',rule:'PRIVILEGED_UNTRUSTED_CHECKOUT',
  summary:'pull_request_target privileged checkout fork untrusted code secrets permissions',
  advice:'Ejecutar scripts de la rama base confiable en job privilegiado; tratar cambios del PR como datos.',
  tests:['fork no accede a secrets','permisos mínimos por job']}
];
function terms(value){
 const s=String(value||'').slice(0,MAX_QUERY).toLowerCase().replace(/([a-z])([A-Z])/g,'$1 $2');
 return [...new Set((s.match(/[a-z0-9_]{3,}/g)||[]).filter(x=>!STOP.has(x)))];
}
function vetted(entry){
 if(!entry||typeof entry!=='object')return false;
 if(entry.type==='playbook')return KNOWN.some(x=>x.id===entry.id);
 if(entry.type!=='verified_fix')return false;
 const hex=x=>/^[a-f0-9]{40}$/i.test(x||'');
 const validUrl=u=>typeof u==='string'&&/^https:\/\/github\.com\/cardenaspiero255-lang\/gamehub-ultra\/(?:pull|commit)\/[a-z0-9]+(?:\/)?$/.test(u);
 return /^[a-z0-9_-]{6,80}$/i.test(entry.id||'')&&hex(entry.fixSha)&&hex(entry.testSha)&&
  validUrl(entry.evidenceUrl)&&String(entry.summary||'').length>=20&&
  String(entry.advice||'').length>=20&&Array.isArray(entry.tests)&&entry.tests.length>0;
}
function search(query,extra=[],max=3){
 const input=query&&typeof query==='object'?query:{text:String(query||'')};
 const q=terms([input.rule,input.path,input.reason,input.text].filter(Boolean).join(' '));
 if(!q.length)return [];
 const corpus=[...KNOWN,...(Array.isArray(extra)?extra.slice(0,MAX_ENTRIES):[])].filter(vetted);
 const df=new Map();for(const e of corpus){for(const t of terms(e.summary+' '+e.rule+' '+e.domain)){df.set(t,(df.get(t)||0)+1)}}
 const scores=[];
 for(const e of corpus){
  const tokens=terms(e.summary+' '+e.rule+' '+e.domain);
  let score=0;
  for(const t of q){if(tokens.includes(t))score+=Math.log(1+(corpus.length+1)/(df.get(t)||1));}
  if(input.rule&&e.rule===input.rule)score+=7;
  if(input.domain&&e.domain===input.domain)score+=2;
  if(score<=0)continue;
  scores.push({id:e.id,type:e.type,domain:e.domain||'general',rule:e.rule||'',
    score:Number(score.toFixed(3)),advice:String(e.advice).slice(0,380),
    tests:e.tests.slice(0,4).map(t=>String(t).slice(0,180)),
    evidenceUrl:e.type==='verified_fix'?e.evidenceUrl:null,verification:e.type==='verified_fix'?'confirmed_tests':'curated_playbook'});
 }
 return scores.sort((a,b)=>b.score-a.score||a.id.localeCompare(b.id)).slice(0,Math.max(0,Math.min(5,Number(max)||3)));
}
module.exports={VERSION,terms,vetted,search,KNOWN};
