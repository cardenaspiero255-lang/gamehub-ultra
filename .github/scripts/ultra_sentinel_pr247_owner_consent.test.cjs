'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {selectAuthenticatedOwnerRun}=require('./ultra_sentinel_pr247_owner_consent.cjs');
const MAIN='458be1a1c7ec29d6c29f6d0944971f55a0ce8ac7';
const HEAD='bca6129ac6be7a42f68f1111a8d0dd89991e8431';
const WF_PATH='.github/workflows/ultra-sentinel-pr247-owner-consent.yml';
const WF_NAME='CAR29 PR247 Sole-Maintainer Consent';
const JOB='Authenticated owner consent for PR247 exact SHA';
const ID=123456;
function goodRun(){
 return {id:9101,workflow_id:ID,name:'CAR29-PR247-owner-'+HEAD,
  path:WF_PATH,display_title:'CAR29-PR247-owner-'+HEAD,
  event:'workflow_dispatch',head_branch:'main',head_sha:MAIN,
  repository:{full_name:'cardenaspiero255-lang/gamehub-ultra'},
  head_repository:{full_name:'cardenaspiero255-lang/gamehub-ultra'},
  actor:{login:'cardenaspiero255-lang',id:322222186,type:'User'},
  triggering_actor:{login:'cardenaspiero255-lang',id:322222186,type:'User'},
  status:'completed',conclusion:'success',run_attempt:1,
  run_started_at:'2026-10-26T23:30:00Z'};
}
function fixture(){return{runs:{total_count:1,workflow_runs:[goodRun()]},
 workflow:{id:ID,name:WF_NAME,path:WF_PATH,state:'active'},
 mainSha:MAIN,headSha:HEAD,
 jobs:{total_count:1,jobs:[{id:801,run_id:9101,name:JOB,status:'completed',conclusion:'success'}]}
};}
const judge=d=>selectAuthenticatedOwnerRun(d);
test('an owner-triggered completed main-only workflow binds exactly the current PR head',()=>{
 const r=judge(fixture());assert.equal(r.status,'AUTHENTICATED_OWNER_CONSENT_PR247');
 assert.equal(r.headSha,HEAD);assert.equal(r.mainSha,MAIN);
 assert.equal(r.ownerConsentRunId,9101);assert.equal(r.ownerConsentAttempt,1);
 assert.equal(r.autoApproveAllowed,false);assert.equal(r.autoMergeAllowed,false);
});
test('missing consent and unauthenticated workflow identities never pass',()=>{
 for(const change of [
  d=>d.runs.total_count=0,
  d=>d.workflow.name='Attacker',
  d=>d.workflow.state='disabled_manually',
  d=>d.workflow.path='.github/workflows/untrusted.yml',
  d=>d.runs.workflow_runs[0].workflow_id=ID+1,
  d=>d.runs.workflow_runs[0].path='.github/workflows/untrusted.yml',
  d=>d.runs.workflow_runs[0].path=WF_PATH+'@main',
  d=>d.runs.workflow_runs[0].name=WF_NAME,
  d=>d.runs.workflow_runs[0].name='CAR29-PR247-owner-'+'a'.repeat(40),
  d=>d.runs.workflow_runs[0].repository.full_name='attacker/repo',
  d=>d.runs.workflow_runs[0].event='pull_request_target'
 ]){const d=fixture();change(d);assert.equal(judge(d).status,'INCOMPLETE');}
});
test('bot, wrong account, reruns and non-main or stale-SHA proof are rejected',()=>{
 for(const change of [
  d=>d.runs.workflow_runs[0].actor.id=23,
  d=>d.runs.workflow_runs[0].actor.login='malicious',
  d=>d.runs.workflow_runs[0].actor.type='Bot',
  d=>d.runs.workflow_runs[0].triggering_actor.id=50,
  d=>d.runs.workflow_runs[0].run_attempt=2,
  d=>d.runs.workflow_runs[0].head_sha='c'.repeat(40),
  d=>d.runs.workflow_runs[0].head_branch='security/attacker',
  d=>d.runs.workflow_runs[0].display_title='CAR29-PR247-owner-'+'c'.repeat(40),
  d=>d.mainSha='a'.repeat(40)
 ]){const d=fixture();change(d);assert.equal(judge(d).status,'INCOMPLETE');}
});
test('latest failed or pending manual attempt supersedes prior green proof',()=>{
 for(const conclusion of ['failure',null]){
  const d=fixture();const next={...goodRun(),id:9102,run_started_at:'2026-10-26T23:31:00Z',
   status:conclusion===null?'in_progress':'completed',conclusion};
  d.runs.workflow_runs.push(next);d.runs.total_count=2;
  assert.equal(judge(d).status,'INCOMPLETE');
 }
});
test('a historical retry must not poison a newer owner dispatch on its first attempt',()=>{
 const d=fixture();
 const prior={...goodRun(),id:9009,run_attempt:2,run_started_at:'2026-10-26T23:29:00Z'};
 d.runs.workflow_runs.push(prior);
 d.runs.total_count=2;
 assert.equal(judge(d).status,'AUTHENTICATED_OWNER_CONSENT_PR247');
});
test('run and GitHub job identity require exact successful unique provenance',()=>{
 for(const change of [
  d=>d.jobs.total_count=2,
  d=>d.jobs.jobs[0].name='Untrusted simulated job',
  d=>d.jobs.jobs[0].conclusion='skipped',
  d=>d.jobs.jobs[0].run_id=10,
  d=>d.jobs.jobs[0].id=0,
  d=>d.runs.workflow_runs[0].run_started_at='bad',
  d=>{d.runs.workflow_runs.push({...goodRun()});d.runs.total_count=2;}
 ]){const d=fixture();change(d);assert.equal(judge(d).status,'INCOMPLETE');}
});
test('the owner confirmation is evidence, never a PR approval or permission to merge',()=>{
 const r=judge(fixture());
 assert.equal(r.status,'AUTHENTICATED_OWNER_CONSENT_PR247');
 assert.equal(r.independentReviewVerified,false);
 assert.equal(r.manualReviewRequired,true);
 assert.equal(r.autoApproveAllowed,false);
 assert.equal(r.autoMergeAllowed,false);
});
