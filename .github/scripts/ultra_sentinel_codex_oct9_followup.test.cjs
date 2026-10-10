'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {inspectWorkflow,reviewWorkflowSources}=require('./ultra_sentinel_yaml_ast.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const SHA='a'.repeat(40), REPO='cardenaspiero255-lang/gamehub-ultra';
const K='app/src/main/java/com/cardenaspiero255/gamehubultra/Engine.kt';
function checkout(repository,ref){
 return ['on: pull_request_target','permissions: read-all','jobs:','  test:','    runs-on: ubuntu-latest','    steps:',
 '      - uses: actions/checkout@'+SHA,'        with:',
 '          repository: '+repository,'          ref: '+ref].join('\n');
}
test('P1: privileged checkout of a different repository at mutable branch is blocked',()=>{
 const result=inspectWorkflow(checkout('attacker/evil','main'),{trustedRepository:REPO});
 assert.ok(result.findings.some(f=>f.severity==='BLOCKER'&&f.rule==='PRIVILEGED_EXTERNAL_MUTABLE_CHECKOUT'),JSON.stringify(result));
});
test('P1: safe checkouts of same repository or immutable external commit remain accepted',()=>{
 for(const [repository,ref] of [[REPO,'main'],['outside/build',SHA]]){
  const result=inspectWorkflow(checkout(repository,ref),{trustedRepository:REPO});
  assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
 }
});
test('P1: identity missing cannot certify literal external checkout as safe',()=>{
 const result=inspectWorkflow(checkout('attacker/evil','main'));
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
});
test('P1: multi-file structural reviewer propagates trusted repo identity',()=>{
 const wf='.github/workflows/external.yml';
 const result=reviewWorkflowSources({expected:[wf],sources:{[wf]:checkout('attacker/evil','main')},trustedRepository:REPO});
 assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EXTERNAL_MUTABLE_CHECKOUT'),JSON.stringify(result));
});
test('P2: CRLF patch and immutable CRLF full-source can be compared',()=>{
 const patch=['@@ -5,2 +5,3 @@',' val answer = 42',
  '+runBlocking { realWork() }',' val safe = true'].join('\r\n');
 const fullSource=['val a = 1','val b = 2','val c = 3','val d = 4',
  'val answer = 42','runBlocking { realWork() }','val safe = true'].join('\r\n');
 const result=analyze([{filename:K,patch,changes:1,fullSource}]);
 assert.equal(result.coverage.partial,false,JSON.stringify(result.warnings));
 assert.ok(result.findings.some(f=>f.rule==='BLOCKING_ANDROID_CALL'),JSON.stringify(result.findings));
});
test('P1: independent trusted workflow retrieves Kotlin source at immutable SHA before analyze',()=>{
 const source=fs.readFileSync(path.join(__dirname,'../workflows/ultra-sentinel-independent-review.yml'),'utf8');
 const fetched=source.indexOf('const evidenceFiles=files.map(item=>({...item}));');
 const analyzeCall=source.indexOf('const result = analyze(evidenceFiles,{sha:pr.head.sha});');
 assert.ok(fetched>0&&analyzeCall>fetched,'full Kotlin context must be acquired before independent analyze');
 assert.match(source,/ref:pr\.head\.sha/);
 assert.match(source,/item\.fullSource=bytes\.toString\("utf8"\)/);
});
