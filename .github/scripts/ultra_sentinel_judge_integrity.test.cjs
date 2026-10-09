'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {candidate,judge}=require('./ultra_sentinel_orchestrator.cjs');
const FILE='app/src/main/java/com/cardenaspiero255/gamehubultra/App.kt',SHA='a'.repeat(40);
const SOURCE='fun a() {\n  System.gc()\n  println("ok")\n}\n';
const FINDING={rule:'FORCED_GC',path:FILE,line:2,severity:'MEDIUM'};
const good=()=>candidate({filename:FILE,content:SOURCE,sha:SHA,findings:[FINDING]});
const checks={'sentinel-core-tests':'success','android-build':'success',
 'unit-test-coverage':'success','architecture-boundary':'success'};
test('US-027: valid one-line human-review draft remains eligible, never executable',()=>{
 const r=judge(good(),{sha:SHA,checks});
 assert.equal(r.status,'ELIGIBLE_FOR_HUMAN_REVIEW');
 assert.equal(r.autoCommitAllowed,false);assert.equal(r.autoMergeAllowed,false);
});
test('US-027: Judge refuses tampered or second-target patch despite green CI',()=>{
 const approved=good();
 const variants=[
  {...approved,patch:approved.patch.replace('--- a/'+FILE,'--- a/app/src/main/java/Other.kt')},
  {...approved,patch:approved.patch.replace('-  System.gc()','-  println("ok")')},
  {...approved,patch:approved.patch+'\n@@ -1,1 +1,1 @@\n-danger()\n+malicious()\n'},
  {...approved,patch:approved.patch.replace('@@ -1,','@@ -999,')},
  {...approved,patch:approved.patch.replace('+++ b/'+FILE,'+++ b/../../outside.kt')},
  {...approved,patch:approved.patch.replace('-  System.gc()','-  System.gc()\n-  println("ok")')}
 ];
 for(const modified of variants){
  const r=judge(modified,{sha:SHA,checks});
  assert.equal(r.status,'REJECT',JSON.stringify({rule:modified.rule,status:r.status,patch:modified.patch.slice(0,100)}));
 }
});
