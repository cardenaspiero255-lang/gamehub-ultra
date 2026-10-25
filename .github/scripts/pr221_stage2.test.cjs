'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {attestScopedPr221,latestProtectedRun,validatedJob}=require('./ultra_sentinel_pr221_stage2.cjs');
const {attestScopedPr172,permitScopedOpaqueYaml}=require('./ultra_sentinel_pr172_scope.cjs');
const HEAD='a'.repeat(40),MAIN='b'.repeat(40);
const ID=742399111;
const RUN_NAME='Ultra Sentinel PR221 Protected Exact Delta';
const JOB_NAME='Trusted main PR221 exact blobs (manual review required)';
function run(overrides={}){
 return {id:123,run_attempt:1,run_started_at:'2026-10-24T22:30:00Z',
  workflow_id:ID,name:RUN_NAME,event:'pull_request_target',head_sha:HEAD,
  head_repository:{full_name:'cardenaspiero255-lang/gamehub-ultra'},
  pull_requests:[{number:221,head:{sha:HEAD},base:{sha:MAIN}}],
  status:'completed',conclusion:'success',...overrides};
}
function latest(records){
 return latestProtectedRun({total_count:records.length,workflow_runs:records},ID,HEAD,MAIN);
}
test('latest trusted PR221 run is scoped to exact authenticated linkage',()=>{
 assert.equal(latest([run()]).id,123);
 const fresh=run({id:124,run_attempt:2,run_started_at:'2026-10-24T22:31:00Z'});
 assert.equal(latest([run(),fresh]).id,124);
 const failed=run({id:125,run_attempt:3,run_started_at:'2026-10-24T22:32:00Z',
  conclusion:'failure'});
 assert.throws(()=>latest([run(),failed]),/LATEST_PROTECTED_RUN_NOT_SUCCESS/);
});
test('GitHub pull_request_target run can omit pull_requests linkage; protected job must still prove PR221',()=>{
 const unlinkedRun=run({pull_requests:[]});
 assert.equal(latest([unlinkedRun]).id,123);
 const protectedJob={id:200,run_id:123,name:JOB_NAME,status:'completed',conclusion:'success'};
 assert.doesNotThrow(()=>validatedJob({total_count:1,jobs:[protectedJob]},123));
 for(const conclusion of ['skipped','failure','cancelled'])
  assert.throws(()=>validatedJob({total_count:1,jobs:[{...protectedJob,conclusion}]},123));
});
test('refuses wrong PR, SHA, repository, workflow identity, or event',()=>{
 const attacks=[
  {event:'push'},{head_sha:'c'.repeat(40)},{name:'Other'},
  {workflow_id:ID+1},{head_repository:{full_name:'other/repo'}},
  {pull_requests:[{number:222,head:{sha:HEAD},base:{sha:MAIN}}]},
  {pull_requests:[{number:221,head:{sha:'d'.repeat(40)},base:{sha:MAIN}}]},
  {pull_requests:[{number:221,head:{sha:HEAD},base:{sha:'d'.repeat(40)}}]},
 ];
 for(const attack of attacks)assert.throws(()=>latest([run(attack)]));
});
test('refuses incomplete, duplicate, pending, and invalid-run provenance',()=>{
 assert.throws(()=>latest([]));
 assert.throws(()=>latest([run(),run()]),/DUPLICATE_RUN_ID/);
 assert.throws(()=>latest([run({status:'in_progress',conclusion:null})]));
 assert.throws(()=>latest([run({run_started_at:'not-a-time'})]));
 const ambiguous=run({id:124,run_attempt:2,run_started_at:'2026-10-24T22:30:00Z'});
 assert.throws(()=>latest([run(),ambiguous]),/AMBIGUOUS_RUN_ORDER/);
 assert.throws(()=>latestProtectedRun({total_count:2,workflow_runs:[run()]},ID,HEAD,MAIN));
});
test('protected job must be the sole exact completed success',()=>{
 const job={id:200,run_id:123,name:JOB_NAME,status:'completed',conclusion:'success'};
 assert.doesNotThrow(()=>validatedJob({total_count:1,jobs:[job]},123));
 for(const mutated of [
  {...job,conclusion:'failure'}, {...job,name:'untrusted'},
  {...job,run_id:999}, {...job,status:'queued'}
 ])assert.throws(()=>validatedJob({total_count:1,jobs:[mutated]},123));
 assert.throws(()=>validatedJob({total_count:2,jobs:[job,job]},123));
});
test('PR221 opacity exception is only the original android.yml path, never unknown YAML',()=>{
 const original={status:'INCOMPLETE',findings:[],coverage:{
  partial:true,requested:1,scanned:1,reasons:['WORKFLOW_SEMANTIC_INCOMPLETE','SHELL_FUNCTION']
 },results:[{path:'.github/workflows/android.yml',status:'INCOMPLETE'}]};
 assert.equal(permitScopedOpaqueYaml(original),true);
 for(const attack of [
  {...original,results:[{path:'.github/workflows/coverage.yml',status:'INCOMPLETE'}]},
  {...original,coverage:{...original.coverage,requested:2}},
  {...original,coverage:{...original.coverage,reasons:['UNMODELED_WORKFLOW_FEATURE']}},
  {...original,findings:[{severity:'HIGH'}]},
  {...original,results:[]},
  {...original,status:'NO_RISK_PATTERN'},
 ])assert.equal(permitScopedOpaqueYaml(attack),false);
});
test('both protected reviewers use the same trusted scope bridge, no bypass',()=>{
 for(const workflow of ['ultra-sentinel-auto-review.yml','ultra-sentinel-independent-review.yml']){
  const text=fs.readFileSync(path.resolve(__dirname,'../workflows/'+workflow),'utf8');
  assert.match(text,/ultra_sentinel_pr172_scope\.cjs/);
  assert.match(text,/attestScopedPr172/);
  assert.match(text,/core\.setFailed/);
 }
 const bridge=fs.readFileSync(path.resolve(__dirname,'ultra_sentinel_pr172_scope.cjs'),'utf8');
 assert.match(bridge,/pr\?\.number===221/);
 assert.match(bridge,/attestScopedPr221/);
});
test('missing authenticated GitHub services never approve PR221',async()=>{
 const stub={number:221,head:{sha:HEAD},base:{sha:MAIN}};
 const a=await attestScopedPr221({github:null,owner:'cardenaspiero255-lang',repo:'gamehub-ultra',pr:stub,files:[]});
 const b=await attestScopedPr172({github:null,owner:'cardenaspiero255-lang',repo:'gamehub-ultra',pr:stub,files:[]});
 for(const verdict of [a,b]){
  assert.equal(verdict.status,'INCOMPLETE');
  assert.equal(verdict.autoMergeAllowed,false);
  assert.equal(verdict.autoApproveAllowed,false);
 }
});
