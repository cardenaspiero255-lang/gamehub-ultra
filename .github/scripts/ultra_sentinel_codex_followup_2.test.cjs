'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const {selectRepairTargets}=require('./ultra_sentinel_orchestrator.cjs');
const SHA='a'.repeat(40),FILE='.github/workflows/adversarial-codex.yml',ACTION='actions/checkout@'+'f'.repeat(40);
const ref='$'+'{{ github.head_ref }}';
const testWorkflow=source=>reviewWorkflows({sha:SHA,expected:[FILE],sources:{[FILE]:source}});
const has=(report,rule)=>report.findings.some(f=>f.rule===rule);
const android='app/src/main/java/com/cardenaspiero255/gamehubultra/VoiceController.kt';
const patch=lines=>lines.join('\n')+'\n';
const inspect=source=>analyze([{filename:android,status:'modified',patch:source,changes:1}],{sha:SHA});
test('Codex P1: checkout flow alias ref fails closed, never NO_RISK_PATTERN',()=>{
 for(const body of [
  'on: pull_request_target\njobs:\n  scan:\n    steps:\n      - {uses: '+ACTION+', with: {ref: *danger}}\n',
  'on: pull_request_target\njobs:\n  scan:\n    steps:\n      - {with: {fetch-depth: 0, ref: *danger}, uses: '+ACTION+'}\n',
  'on: pull_request_target\njobs:\n  scan:\n    steps:\n      - {uses: '+ACTION+', with: {\n          ref: *danger\n        }}\n'
 ]){
  const r=testWorkflow('name: &danger pull_request_target\n'+body);
  assert.ok(r.status==='INCOMPLETE'||has(r,'PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
  assert.equal(r.autoApproveAllowed,false);
 }
});
test('Codex P1: benign flow checkout without ref alias stays non-blocking',()=>{
 const code='on: pull_request_target\njobs:\n  scan:\n    steps:\n      - {uses: '+ACTION+', with: {ref: main}}\n';
 const r=testWorkflow(code);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});
test('Codex P2: context line opens raw Kotlin string, added prose is not runnable',()=>{
 const a=inspect(patch([
 '@@ -3,3 +3,4 @@',
 ' val docs = """',
 '+runBlocking { documentationOnly() }',
 ' inside existing docs',
 ' """'
 ]));
 assert.ok(!has(a,'BLOCKING_ANDROID_CALL'),JSON.stringify(a.findings));
});
test('Codex P2: context line opens Kotlin block comment, inserted prose is not executable',()=>{
 const a=inspect(patch([
 '@@ -6,3 +6,4 @@',
 ' /*',
 '+System.gc() should never be done in UI',
 ' old documentation text',
 ' */'
 ]));
 assert.ok(!has(a,'FORCED_GC'),JSON.stringify(a.findings));
});
test('Codex P2: context-fed state still recognizes interpolated raw Kotlin code',()=>{
 const a=inspect(patch([
 '@@ -5,3 +5,4 @@',
 ' val docs = """',
 '+'+'$'+'{runBlocking { realCall() }}',
 ' some text',
 ' """'
 ]));
 assert.ok(has(a,'BLOCKING_ANDROID_CALL'),JSON.stringify(a.findings));
});
test('Codex P2: context closing raw string re-enables runtime detection',()=>{
 const a=inspect(patch([
 '@@ -5,3 +5,4 @@',
 ' val docs = """',
 ' """',
 '+runBlocking { executesNow() }',
 ' after'
 ]));
 assert.ok(has(a,'BLOCKING_ANDROID_CALL'),JSON.stringify(a.findings));
});
test('US-026: source selection provides both fixed GC and private Logcat drafts safely',()=>{
 const f=[
  {rule:'FORCED_GC',path:android,line:9},
  {rule:'POTENTIAL_PRIVATE_LOG',path:'app/src/main/java/com/cardenaspiero255/gamehubultra/Logger.kt',line:10},
  {rule:'POTENTIAL_PRIVATE_LOG',path:'app/src/main/java/com/cardenaspiero255/gamehubultra/Logger.kt',line:11},
  {rule:'UNSCOPED_COROUTINE',path:'app/src/main/java/com/cardenaspiero255/gamehubultra/Other.kt',line:1},
  {rule:'POTENTIAL_PRIVATE_LOG',path:'../../outside.kt',line:2}
 ];
 assert.deepEqual(selectRepairTargets(f,3),[
  android,'app/src/main/java/com/cardenaspiero255/gamehubultra/Logger.kt'
 ]);
 assert.deepEqual(selectRepairTargets(f,1),[android]);
 assert.deepEqual(selectRepairTargets(f,0),[]);
});
