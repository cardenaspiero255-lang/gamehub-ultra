'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {attestCi,summarizeDrafts}=require('./ultra_sentinel_frontier_integrity.cjs');
const SHA='a'.repeat(40),OLD='b'.repeat(40),REPO='cardenaspiero255-lang/gamehub-ultra';
const run=(name,overrides={})=>({
 id: name==='Android build'?100:200,run_number:8,run_attempt:1,
 name,head_sha:SHA,event:'pull_request',head_branch:'feature/test',
 workflow_id:name==='Android build'?101:202,
 path:name==='Android build'?'.github/workflows/android.yml':'.github/workflows/coverage.yml',
 status:'completed',conclusion:'success',
 repository:{full_name:REPO},head_repository:{full_name:REPO},
 ...overrides
});
const TRUST={'Android build':{id:101,path:'.github/workflows/android.yml'},'Unit Test Coverage':{id:202,path:'.github/workflows/coverage.yml'}};
const ci=(runs,options={})=>attestCi({sha:SHA,repo:REPO,runs,
 trustedWorkflows:TRUST,changedFiles:[],changedFilesComplete:true,...options});
test('the latest verified Android and Coverage runs are both required',()=>{
 const out=ci([run('Android build'),run('Unit Test Coverage')]);
 assert.equal(out.status,'PASS');assert.equal(out.counts.success,2);
 assert.equal(out.autoMergeAllowed,false);assert.equal(out.autoApproveAllowed,false);
});
test('never counts an earlier SHA as a green review',()=>{
 const out=ci([run('Android build',{head_sha:OLD}),run('Unit Test Coverage')]);
 assert.notEqual(out.status,'PASS');assert.deepEqual(out.counts,{success:1,pending:1,failed:0});
});
test('unfinished build never passes just because coverage passes',()=>{
 const out=ci([run('Android build',{status:'in_progress',conclusion:null}),run('Unit Test Coverage')]);
 assert.equal(out.status,'WAITING');
});
test('failed newest run takes precedence over older success',()=>{
 const out=ci([run('Android build',{id:88,run_number:7}),
  run('Android build',{id:101,run_number:9,conclusion:'failure'}),run('Unit Test Coverage')]);
 assert.equal(out.status,'FAILED');assert.equal(out.counts.failed,1);
});
test('rerun with later run_attempt wins over previous failure',()=>{
 const out=ci([run('Android build',{id:120,run_attempt:1,conclusion:'failure'}),
 run('Android build',{id:120,run_attempt:2,conclusion:'success'}),run('Unit Test Coverage')]);
 assert.equal(out.status,'PASS');
});
test('foreign repositories, unrelated workflows and forged events are never evidence',()=>{
 const out=ci([run('Android build',{repository:{full_name:'evil/fork'}}),
   run('Unit Test Coverage',{event:'repository_dispatch'}),
   run('Release Other Workflow')]);
 assert.notEqual(out.status,'PASS');assert.equal(out.counts.success,0);
});
test('failed API or incomplete page cannot certify any release',()=>{
 assert.equal(ci([run('Android build'),run('Unit Test Coverage')],{apiComplete:false}).status,'UNKNOWN');
 assert.equal(ci(null).status,'UNKNOWN');
 assert.equal(ci([run('Android build'),run('Unit Test Coverage')],{sha:'bad'}).status,'UNKNOWN');
});
test('never trusts unrecognized states, missing IDs or unverified repository',()=>{
 const out=ci([run('Android build',{id:-1}),run('Unit Test Coverage',{status:'done'}),run('Android build',{repository:null})]);
 assert.equal(out.counts.success,0);assert.equal(out.status,'WAITING');
});
test('reports no payload URLs or tokens, and has bounded public metadata',()=>{
 const out=ci([run('Android build',{html_url:'https://attacker.invalid/?token=ABC'}),
 run('Unit Test Coverage')]);
 assert.equal(out.status,'PASS');
 assert.doesNotMatch(JSON.stringify(out),/attacker|token|Bearer|https:/);
});
test('repair assistant emits metadata only and never marks patches approved',()=>{
 const path='app/src/main/java/com/cardenaspiero255/gamehubultra/App.kt';
 const src='fun a() {\n  System.gc()\n  println("ok")\n}\n';
 const analysis={coverage:{partial:false},findings:[{rule:'FORCED_GC',path,line:2}]};
 const r=summarizeDrafts({analysis,sources:{[path]:src},sha:SHA});
 assert.equal(r.generated,1);assert.equal(r.candidates[0].status,'REVIEW_PENDING');
 assert.equal(r.autoMergeAllowed,false);assert.equal(r.autoApplyAllowed,false);
 assert.doesNotMatch(JSON.stringify(r),/diff --git|println|System.gc/);
});
test('partial diff is explicitly inconclusive despite a candidate',()=>{
 const path='app/src/main/java/com/cardenaspiero255/gamehubultra/App.kt';
 const r=summarizeDrafts({analysis:{coverage:{partial:true},findings:[{rule:'FORCED_GC',path,line:2}]},
 sources:{[path]:'fun a() {\n  System.gc()\n  println("ok")\n}\n'},sha:SHA});
 assert.equal(r.status,'INCOMPLETE');assert.equal(r.autoApplyAllowed,false);
});
test('workflow uses trusted main with read-only actions access and no PR code execution',()=>{
 const yaml=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-auto-review.yml'),'utf8');
 assert.match(yaml,/actions: read/);
 assert.match(yaml,/ultra_sentinel_frontier_integrity\.cjs/);
 assert.match(yaml,/listWorkflowRunsForRepo/);
 assert.match(yaml,/summarizeDrafts/);
 assert.match(yaml,/ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
 assert.match(yaml,/persist-credentials: false/);
 assert.doesNotMatch(yaml,/issues: write|pull-requests: write|ref: \$\{\{ github\.event\.pull_request\.head\.sha \}\}/);
});

test('Codex P2: missing ordering metadata cannot let stale green CI pass',()=>{
 const old=run('Android build',{id:90,run_number:7,run_attempt:1});
 const coverage=run('Unit Test Coverage');
 for(const broken of [
  {id:101,run_number:undefined,run_attempt:1,conclusion:'failure'},
  {id:101,run_number:9,run_attempt:undefined,conclusion:'failure'},
  {id:undefined,run_number:9,run_attempt:1,conclusion:'failure'},
  {id:101,run_number:9,run_attempt:1,status:'new_unknown_status',conclusion:undefined}
 ]){
  const result=ci([old,run('Android build',broken),coverage]);
  assert.notEqual(result.status,'PASS',JSON.stringify({broken,result}));
  assert.ok(result.counts.pending>0||result.counts.failed>0,JSON.stringify({broken,result}));
 }
});
