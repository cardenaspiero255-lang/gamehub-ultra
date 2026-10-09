'use strict';
/*
 * Small labeled risk-detection acceptance corpus. This tests only the rules represented
 * here; it is NOT an independent comparison against any proprietary AI reviewer.
 */
const core=require('./ultra_sentinel_core.cjs');
const ROOT='app/src/main/java/com/cardenaspiero255/gamehubultra/';
const d=(path,lines)=>({filename:path,changes:lines.length,patch:
 '@@ -1,0 +1,'+lines.length+' @@\n'+lines.map(v=>'+'+v).join('\n')});
const cases=[
 {name:'unscoped coroutine',rule:'UNSCOPED_COROUTINE',present:true,files:[d(ROOT+'Game.kt',['GlobalScope.launch { work() }'])]},
 {name:'scoped coroutine',rule:'UNSCOPED_COROUTINE',present:false,files:[d(ROOT+'Game.kt',['lifecycleScope.launch { work() }'])]},
 {name:'android blocking',rule:'BLOCKING_ANDROID_CALL',present:true,files:[d(ROOT+'Game.kt',['Thread.sleep(100)'])]},
 {name:'nonblocking coroutine delay',rule:'BLOCKING_ANDROID_CALL',present:false,files:[d(ROOT+'Game.kt',['delay(100)'])]},
 {name:'reentrant voice',rule:'SPEECH_REENTRANT_RETRY',present:true,files:[d(ROOT+'voice/Wake.kt',['override fun onError(code: Int) {','startListening(intent)','}'])]},
 {name:'deferred voice',rule:'SPEECH_REENTRANT_RETRY',present:false,files:[d(ROOT+'voice/Wake.kt',['override fun onError(code: Int) {','handler.postDelayed({ startListening(intent) }, 100)','}'])]},
 {name:'unbounded recursion',rule:'UNBOUNDED_RECURSION',present:true,files:[d(ROOT+'voice/Session.kt',['fun loop() {','loop()','}'])]},
 {name:'depth-guarded recursion',rule:'UNBOUNDED_RECURSION',present:false,files:[d(ROOT+'voice/Session.kt',['fun loop(depth: Int) {','if (depth >= 10) return','loop(depth+1)','}'])]},
 {name:'untrusted privileged checkout',rule:'PRIVILEGED_UNTRUSTED_CHECKOUT',present:true,files:[d('.github/workflows/unsafe.yml',['pull_request_target:','ref: ${{ github.event.pull_request.head.sha }}'])]},
 {name:'trusted main checkout',rule:'PRIVILEGED_UNTRUSTED_CHECKOUT',present:false,files:[d('.github/workflows/safe.yml',['pull_request_target:','ref: ${{ github.event.repository.default_branch }}'])]},
 {name:'literal credential risk',rule:'POTENTIAL_HARDCODED_SECRET',present:true,files:[d(ROOT+'Login.kt',['SENTRY_AUTH_TOKEN="exampleplaceholder12345"'])]},
 {name:'secret env reference',rule:'POTENTIAL_HARDCODED_SECRET',present:false,files:[d(ROOT+'Login.kt',['val token = System.getenv("SENTRY_AUTH_TOKEN")'])]},
 {name:'uncancelled loop',rule:'NON_CANCELLABLE_LOOP',present:true,files:[d(ROOT+'Service.kt',['while (true) {','work()','}'])]},
 {name:'cancelled loop',rule:'NON_CANCELLABLE_LOOP',present:false,files:[d(ROOT+'Service.kt',['while (isActive) {','work()','}'])]},
 {name:'forced GC',rule:'FORCED_GC',present:true,files:[d(ROOT+'Service.kt',['System.gc()'])]},
 {name:'no forced GC',rule:'FORCED_GC',present:false,files:[d(ROOT+'Service.kt',['System.nanoTime()'])]},
 {name:'cross-function CAR51',rule:'UNBOUNDED_RECURSION',present:false,files:[d(ROOT+'platform/RuntimeDiagnostics.kt',['fun connectivity(context: Context): ConnectivityTelemetry =','runCatching { readConnectivity(context) }','fun get(context: Context): RuntimeDiagnostics {','return RuntimeDiagnostics(connectivity = connectivity(context))','}'])]},
 {name:'diff-gap CAR51',rule:'UNBOUNDED_RECURSION',present:false,files:[{filename:ROOT+'platform/RuntimeDiagnostics.kt',patch:['@@ -98,6 +98,9 @@','+fun connectivity(context: Context): ConnectivityTelemetry =','+  runCatching { readConnectivity(context) }',' fun get(context: Context): RuntimeDiagnostics {','@@ -107,8 +110,7 @@','- connectivity = readConnectivity(context),','+ connectivity = connectivity(context),'].join('\n'),changes:4}]},
 {name:'comment false positive',rule:'UNSCOPED_COROUTINE',present:false,files:[d(ROOT+'Game.kt',['// GlobalScope.launch { }'])]}
];
function evaluate(){
 const rows=cases.map(c=>{
  const result=core.analyze(c.files);
  return {name:c.name,rule:c.rule,expected:c.present,
   detected:result.findings.some(x=>x.rule===c.rule)}
 });
 const tp=rows.filter(x=>x.expected&&x.detected).length,fn=rows.filter(x=>x.expected&&!x.detected).length,
   tn=rows.filter(x=>!x.expected&&!x.detected).length,fp=rows.filter(x=>!x.expected&&x.detected).length;
 return {type:'targeted-synthetic-rule-corpus',cases:rows.length,counts:{tp,fn,tn,fp},
  precision:tp+fp?tp/(tp+fp):null,recall:tp+fn?tp/(tp+fn):null,
  specificity:tn+fp?tn/(tn+fp):null,failures:rows.filter(x=>x.expected!==x.detected),
  caveat:'Curated fixture tests, not real-world accuracy, model quality or SSS certification.'};
}
if(require.main===module){const r=evaluate();console.log(JSON.stringify(r,null,2));if(r.failures.length)process.exitCode=1}
module.exports={cases,evaluate};
