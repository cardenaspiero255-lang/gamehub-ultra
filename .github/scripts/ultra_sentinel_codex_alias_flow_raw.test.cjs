'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const SHA='a'.repeat(40),WF='.github/workflows/codex-failclosed.yml',PIN='actions/checkout@'+'f'.repeat(40);
const REF='$'+'{{ github.head_ref }}';
const review=y=>reviewWorkflows({sha:SHA,expected:[WF],sources:{[WF]:y}});
const flagged=(r,rule)=>r.findings.some(f=>f.rule===rule);
const checkout='jobs:\n  scan:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: '+PIN+'\n        with:\n          ref: "'+REF+'"\n';
const ktest=lines=>analyze([{filename:'app/src/main/java/com/cardenaspiero255/gamehubultra/core/Codex.kt',status:'modified',
 changes:lines.length,patch:'@@ -0,0 +1,'+lines.length+' @@\n'+lines.map(l=>'+'+l).join('\n')+'\n'}],{sha:SHA});
test('Codex P1: anchored privileged trigger cannot be silently allowed',()=>{
 for(const prefix of ['name: &danger pull_request_target\non: *danger\n','name: &danger workflow_run\non: *danger\n']){
  const r=review(prefix+checkout);
  assert.ok(flagged(r,'PRIVILEGED_PR_CODE_CHECKOUT')||r.status==='INCOMPLETE',JSON.stringify(r));
  assert.equal(r.autoApproveAllowed,false);
 }
});
test('Codex P1: unresolved on alias is an explicit incomplete audit',()=>{
 for(const trigger of ['on: *unresolved\n','on: "*unresolved"\n']){
  const r=review(trigger+'jobs:\n  scan:\n    steps:\n      - uses: '+PIN+'\n');
  assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
  assert.equal(r.coverage.partial,true);
 }
});
test('Codex P1: multiline flow YAML checkout is detected even when with precedes uses',()=>{
 const variants=[
 '      - {\n          with: {ref: "'+REF+'"},\n          uses: '+PIN+'\n        }\n',
 '      - {name: probe,\n          with: {fetch-depth: 0, ref: "'+REF+'"},\n          uses: '+PIN+',\n          if: always()\n        }\n',
 '      - {\n          uses: actions/checkout@v6,\n          with: {ref: "'+REF+'"}\n        }\n'
 ];
 for(const step of variants){
  const r=review('on: pull_request_target\njobs:\n  scan:\n    steps:\n'+step);
  assert.ok(flagged(r,'PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
  if(step.includes('@v6'))assert.ok(flagged(r,'UNPINNED_ACTION'),JSON.stringify(r));
 }
});
test('Codex P1: multiline flow benign checkouts are not falsely blocked',()=>{
 for(const ref of ['main','refs/heads/main',SHA]){
  const step='      - {\n          with: {ref: "'+ref+'"},\n          uses: '+PIN+'\n        }\n';
  const r=review('on: pull_request_target\njobs:\n  scan:\n    steps:\n'+step);
  assert.ok(!flagged(r,'PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
 }
});
test('Codex P2: raw Kotlin string templates execute blocking expressions',()=>{
 const cases=[
 ['val value = """'+'$'+'{runBlocking { work() }}"""'],
 ['val value = """normal text '+'$'+'{runBlocking { work() }} more text"""'],
 ['val value = """','$'+'{runBlocking { work() }}','"""'],
 ['val value = """','before '+'$'+'{runBlocking { work() }}','after"""']
 ];
 for(const lines of cases){
  const out=ktest(lines);
  assert.ok(flagged(out,'BLOCKING_ANDROID_CALL'),JSON.stringify({lines,findings:out.findings}));
 }
});
test('Codex P2: raw Kotlin documentation without interpolation remains inert',()=>{
 const lines=['val description = """','runBlocking { work() }','"""','val safe = 1'];
 const out=ktest(lines);
 assert.ok(!flagged(out,'BLOCKING_ANDROID_CALL'),JSON.stringify(out));
});
test('Codex P2: inline raw templates keep real calls after closing triple quotes',()=>{
 const out=ktest(['val a = """hello"""; runBlocking { work() }']);
 assert.ok(flagged(out,'BLOCKING_ANDROID_CALL'));
});
