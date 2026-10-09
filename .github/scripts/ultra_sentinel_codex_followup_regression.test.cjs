'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const SHA='a'.repeat(40),WF='.github/workflows/new-codex-red.yml',PIN='actions/checkout@'+'f'.repeat(40);
const REF='$'+'{{ github.head_ref }}';
const review=y=>reviewWorkflows({sha:SHA,expected:[WF],sources:{[WF]:y}});
const has=(result,rule)=>result.findings.some(x=>x.rule===rule);
const chk='jobs:\n  scan:\n    steps:\n      - uses: '+PIN+'\n        with:\n          ref: "'+REF+'"\n';
const kotlin=lines=>analyze([{filename:'app/src/main/java/com/cardenaspiero255/gamehubultra/stress/Codex.kt',status:'modified',changes:lines.length,
 patch:'@@ -0,0 +1,'+lines.length+' @@\n'+lines.map(x=>'+'+x).join('\n')+'\n'}],{sha:SHA});
test('Codex P1: alias in on flow sequence cannot be silently passed as safe',()=>{
 for(const start of [
  'name: &danger pull_request_target\non: [*danger]\n',
  'name: &danger workflow_run\non: [push, *danger]\n',
  'name: &danger pull_request_target\non:\n  - *danger\n',
  'name: &danger workflow_run\non: {*danger: {}}\n'
 ]){
  const r=review(start+chk);
  assert.ok(has(r,'PRIVILEGED_PR_CODE_CHECKOUT')||r.status==='INCOMPLETE',JSON.stringify({start,r}));
  assert.equal(r.autoApproveAllowed,false);
 }
});
test('Codex P2: sensitive permission aliases fail closed at root and job level',()=>{
 for(const permissions of [
  'name: &write write-all\npermissions: *write\n',
  'name: &write write-all\npermissions: [*write]\n',
  'name: &write write-all\njobs:\n  a:\n    permissions: *write\n    steps:\n      - uses: '+PIN+'\n',
  'name: &scopes {contents: write}\npermissions: *scopes\n'
 ]){
  const r=review(permissions);
  assert.ok(has(r,'PRIVILEGED_WRITE_TOKEN')||r.status==='INCOMPLETE',JSON.stringify({permissions,r}));
 }
});
test('Codex P1: nested flow with multi-line mapping detects dangerous checkout',()=>{
 const starts=[
 '      - {uses: '+PIN+',\n          with: {\n            ref: "'+REF+'"\n          }\n        }\n',
 '      - {with: {\n            ref: "'+REF+'",\n            fetch-depth: 0\n          },\n          uses: '+PIN+'\n        }\n',
 '      - {\n          uses: actions/checkout@v6,\n          with: {\n            fetch-depth: 0,\n            ref: "'+REF+'"\n          }\n        }\n'
 ];
 for(const step of starts){
  const r=review('on: pull_request_target\njobs:\n  scan:\n    steps:\n'+step);
  assert.ok(has(r,'PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify({step,r}));
  if(step.includes('@v6'))assert.ok(has(r,'UNPINNED_ACTION'));
 }
});
test('Codex P1: multiline benign flow with nested with is not dangerous',()=>{
 for(const ref of ['main','refs/heads/main',SHA]){
  const step='      - {with: {\n            fetch-depth: 0,\n            ref: "'+ref+'"\n          },\n          uses: '+PIN+'\n        }\n';
  assert.ok(!has(review('on: pull_request_target\njobs:\n  scan:\n    steps:\n'+step),'PRIVILEGED_PR_CODE_CHECKOUT'));
 }
});
test('Codex P2: Kotlin raw interpolations retain nested regular-string templates',()=>{
 for(const lines of [
  ['val x = """'+'$'+'{"nested '+'$'+'{runBlocking { work() }}"}"""'],
  ['val x = """prefix '+'$'+'{"nested '+'$'+'{System.gc()}"} suffix"""'],
  ['val x = """','before '+'$'+'{"nested '+'$'+'{runBlocking { work() }}"}','after"""']
 ]){
  const r=kotlin(lines);
  assert.ok(has(r,'BLOCKING_ANDROID_CALL')||has(r,'FORCED_GC'),JSON.stringify({lines,r}));
 }
});
test('Codex P2: nested raw string prose remains inert without interpolation',()=>{
 const r=kotlin(['val x = """runBlocking { wait() } and System.gc()"""']);
 assert.ok(!has(r,'BLOCKING_ANDROID_CALL')&&!has(r,'FORCED_GC'),JSON.stringify(r));
});
test('Security: unresolved aliases in non-sensitive fields do not automatically break audit',()=>{
 const code='name: &hello hello\non: push\njobs:\n  scan:\n    steps:\n      - name: *hello\n        uses: '+PIN+'\n        with:\n          ref: main\n';
 const r=review(code);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});

// Default-branch review / comment events are privileged even when no
// pull_request_target or workflow_run trigger appears in the YAML.
test('Codex P1: default-branch comment/review triggers are privileged in legacy fallback',()=>{
 for(const trigger of ['issue_comment','pull_request_review','pull_request_review_comment','discussion_comment']){
  const yaml=['on: '+trigger,'jobs:','  audit:','    steps:',
   '      - uses: actions/checkout@'+PIN,
   '        with:', '          ref: refs/pull/42/head'].join('\n');
  const out=review(yaml);
  assert.ok(has(out,'PRIVILEGED_PR_CODE_CHECKOUT')||has(out,'PRIVILEGED_UNTRUSTED_CHECKOUT'),
   JSON.stringify({trigger,out}));
 }
});
