'use strict';
/* Ultra Sentinel evidence memory — deterministic/offline; never learns from unverified bot output. */
const MAX_QUERY=1200;
const STOP=Object.freeze(['the','and','for','this','from','into','with','para','que','los','las','una','por','del','error','file','test','code','kotlin','android','github','line','app','src','main','java','com','gamehubultra']);
// Capture arithmetic intrinsics while loading trusted code, before consumers
// can alter their global prototypes or Math properties.
const TRUSTED_LOG=Math.log,TRUSTED_ROUND=Math.round;
const TRUSTED_ARRAY_FROM=Array.from;
const TRUSTED_STRING=String;
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
 // Parse ASCII tokens without mutable String/RegExp prototype dispatch.
 // The packed representation has no externally writable array slots.
 const input=TRUSTED_STRING(value||'');
 const end=input.length<MAX_QUERY?input.length:MAX_QUERY;
 const upper='ABCDEFGHIJKLMNOPQRSTUVWXYZ',lower='abcdefghijklmnopqrstuvwxyz';
 let packed='',word='',count=0,lastLower=false;
 for(let i=0;i<=end;i++){
  const c=i===end?' ':input[i];
  const isLower=c>='a'&&c<='z';
  const isUpper=c>='A'&&c<='Z';
  const isToken=isLower||isUpper||(c>='0'&&c<='9')||c==='_';
  const boundary=!isToken||(lastLower&&isUpper);
  if(boundary){
   if(word.length>=3&&!isStopWord(word)){
    let duplicate=false,previous='';
    for(let j=0;j<packed.length;j++){
     if(packed[j]===' '){
      if(previous===word){duplicate=true;break;}
      previous='';
     }else previous+=packed[j];
    }
    if(!duplicate){packed+=word+' ';count++;}
   }
   word='';
  }
  if(isToken){
   if(isUpper){
    for(let j=0;j<26;j++){
     if(upper[j]===c){word+=lower[j];break;}
    }
   }else word+=c;
  }
  lastLower=isLower;
 }
 // A captured native Array.from constructs dense slots without consulting
 // Array.prototype.push, the iterator, or mutable regex/string methods.
 const tokenPack=packed,tokenCount=count;
 return TRUSTED_ARRAY_FROM({length:tokenCount},(_,wanted)=>{
  let seen=0,token='';
  for(let i=0;i<tokenPack.length;i++){
   const c=tokenPack[i];
   if(c===' '){
    if(seen===wanted)return token;
    seen++;token='';
   }else token+=c;
  }
  return '';
 });
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
function lowerAscii(c){return c>='a'&&c<='z';}
function upperAscii(c){return c>='A'&&c<='Z';}
function digitAscii(c){return c>='0'&&c<='9';}
function validMemoryId(x){
 if(typeof x!=='string'||x.length<6||x.length>80)return false;
 for(let i=0;i<x.length;i++){
  const c=x[i];
  if(!lowerAscii(c)&&!upperAscii(c)&&!digitAscii(c)&&c!=='_'&&c!=='-')return false;
 }
 return true;
}
function validMemorySha(x){
 if(typeof x!=='string'||x.length!==40)return false;
 for(let i=0;i<40;i++){
  const c=x[i];
  if(!digitAscii(c)&&!(c>='a'&&c<='f')&&!(c>='A'&&c<='F'))return false;
 }
 return true;
}
function memoryPrefix(s,p){
 if(s.length<p.length)return false;
 for(let i=0;i<p.length;i++)if(s[i]!==p[i])return false;
 return true;
}
function validMemoryUrl(x){
 if(typeof x!=='string'||x.length>230)return false;
 const pull='https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/';
 const commit='https://github.com/cardenaspiero255-lang/gamehub-ultra/commit/';
 const start=memoryPrefix(x,pull)?pull.length:memoryPrefix(x,commit)?commit.length:-1;
 if(start<0)return false;
 const end=x[x.length-1]==='/'?x.length-1:x.length;
 if(end<=start)return false;
 for(let i=start;i<end;i++){
  const c=x[i];
  if(!lowerAscii(c)&&!digitAscii(c))return false;
 }
 return true;
}
// Test evidence must contain at least one Unicode non-whitespace character.
// Do not dispatch String.prototype.trim, which downstream consumers can patch.
function nonBlankEvidence(s){
 for(let i=0;i<s.length;i++){
  const c=s[i];
  if(c===' '||(c>='\t'&&c<='\r')||c==='\u00a0'||c==='\u1680'||
     (c>='\u2000'&&c<='\u200a')||c==='\u2028'||c==='\u2029'||
     c==='\u202f'||c==='\u205f'||c==='\u3000'||c==='\ufeff')continue;
  return true;
 }
 return false;
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
 if(!validMemoryId(entry.id)||!validMemorySha(entry.fixSha)||
    !validMemorySha(entry.testSha)||!validMemoryUrl(entry.evidenceUrl)||
    typeof entry.summary!=='string'||entry.summary.length<20||entry.summary.length>1500||
    typeof entry.advice!=='string'||entry.advice.length<20||entry.advice.length>1500||
    !Array.isArray(entry.tests)||entry.tests.length<1||entry.tests.length>12)
  return false;
 for(let i=0;i<entry.tests.length;i++){
  const t=entry.tests[i];
  if(typeof t!=='string'||t.length>220||!nonBlankEvidence(t))return false;
 }
 return true;
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
 }else{text=TRUSTED_STRING(query||'');}
 const q=terms(TRUSTED_STRING(rule||'')+' '+TRUSTED_STRING(path||'')+' '+TRUSTED_STRING(reason||'')+' '+TRUSTED_STRING(text||''));
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
