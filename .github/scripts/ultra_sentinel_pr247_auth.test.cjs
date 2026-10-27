'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {evaluatePr247,allowPr247ManualStructural}=require('./ultra_sentinel_pr247_auth.cjs');
const HEAD='8d53e229a9108869a665d35ce1836ecff8b37f3c';
const MAIN='4bdaf1d35380c7053f836d66ab969afc8040e744';
const REPO='cardenaspiero255-lang/gamehub-ultra';
const FILES=[
 ['.github/scripts/find_sonar_coverage_run.py','added','bfa756c0441a66dbe00ee943f8928537249533c2'],
 ['.github/scripts/test_find_sonar_coverage_run.py','added','d5cdd9357207cbe1747e05b037c117aa85c4e104'],
 ['.github/scripts/test_verify_sonar_jacoco.py','added','14f414e87d05385782b6493aab6c522c1ed0e81c'],
 ['.github/scripts/verify_sonar_jacoco.py','added','e60ace269e32fe738f05f8eefa4ccd3327054739'],
 ['.github/workflows/sonar.yml','modified','5d74541253fa867a7dbea9370be5facfc1be7bf8']
].map(([filename,status,sha])=>({filename,status,sha}));
function fixture(){
 const pr={number:247,state:'open',draft:false,changed_files:5,
  user:{login:'cardenaspiero255-lang',id:322222186,type:'User'},author_association:'OWNER',
  head:{sha:HEAD,repo:{full_name:REPO}},
  base:{sha:MAIN,ref:'main',repo:{full_name:REPO}}};
 const reviews=[{id:100,state:'APPROVED',commit_id:HEAD,author_association:'COLLABORATOR',
  user:{login:'trusted-reviewer',type:'User'}}];
 return {pr,files:FILES.map(x=>({...x})),reviews,mainSha:MAIN,
   baseWorkflowSha:'f2831ae24e9687c8a0472c9a42df05f708815be5',
   headWorkflowSha:'5d74541253fa867a7dbea9370be5facfc1be7bf8'};
}
test('a current independent authenticated GitHub approval can bind only exact PR247 files',()=>{
 const got=evaluatePr247(fixture());
 assert.equal(got.status,'AUTHENTICATED_MANUAL_PR247');
 assert.equal(got.approvalId,100);
 assert.equal(got.autoApproveAllowed,false);
 assert.equal(got.autoMergeAllowed,false);
});
test('no review, self review and bot review never approve',()=>{
 for(const change of [
  d=>d.reviews=[],
  d=>d.reviews[0].user.login=d.pr.user.login,
  d=>d.reviews[0].user.type='Bot',
  d=>d.reviews[0].author_association='NONE'
 ]){
  const d=fixture();change(d);assert.equal(evaluatePr247(d).status,'INCOMPLETE');
 }
});
test('stale SHA approval and newer changes requested fail closed',()=>{
 for(const change of [
  d=>d.reviews[0].commit_id='a'.repeat(40),
  d=>d.reviews.push({...d.reviews[0],id:101,state:'CHANGES_REQUESTED'}),
  d=>d.reviews.push({...d.reviews[0],id:102,state:'DISMISSED'})
 ]){
  const d=fixture();change(d);assert.equal(evaluatePr247(d).status,'INCOMPLETE');
 }
});
test('any changed PR SHA, base, draft or repository identity invalidates the proof',()=>{
 for(const change of [
  d=>d.pr.head.sha='b'.repeat(40),
  d=>d.pr.base.sha='c'.repeat(40),
  d=>d.pr.draft=true,
  d=>d.pr.base.repo.full_name='attacker/fork',
  d=>d.pr.number=248,
  d=>d.pr.author_association='CONTRIBUTOR',
  d=>d.mainSha='d'.repeat(40)
 ]){
  const d=fixture();change(d);assert.equal(evaluatePr247(d).status,'INCOMPLETE');
 }
});
test('extra, missing, duplicate, modified or reordered files cannot silently change scope',()=>{
 for(const change of [
  d=>{d.files.push({...d.files[0]});d.pr.changed_files=6;},
  d=>{d.files.pop();d.pr.changed_files=4;},
  d=>d.files[0].sha='a'.repeat(40),
  d=>d.files[0].status='modified',
  d=>d.files[0].filename='.github/workflows/android.yml',
  d=>{d.files[1]={...d.files[0]};}
 ]){
  const d=fixture();change(d);assert.equal(evaluatePr247(d).status,'INCOMPLETE');
 }
 const reordered=fixture();reordered.files.reverse();
 assert.equal(evaluatePr247(reordered).status,'AUTHENTICATED_MANUAL_PR247');
});
test('changed scanner workflow or trusted baseline blob is never accepted',()=>{
 for(const change of [
  d=>d.headWorkflowSha='a'.repeat(40),
  d=>d.baseWorkflowSha='b'.repeat(40),
  d=>d.reviews[0].id=0
 ]){
  const d=fixture();change(d);assert.equal(evaluatePr247(d).status,'INCOMPLETE');
 }
});
const scoped=()=>({status:'INCOMPLETE',findings:[],
 coverage:{partial:true,requested:1,scanned:1,
 reasons:['WORKFLOW_SEMANTIC_INCOMPLETE','SENSITIVE_ACTION_ENV']},
 results:[{path:'.github/workflows/sonar.yml',status:'INCOMPLETE',reasons:['SENSITIVE_ACTION_ENV']}]});
test('manual YAML acceptance is limited to one sonar.yml and the documented opaque handoff',()=>{
 assert.equal(allowPr247ManualStructural(scoped()),true);
 const changes=[
  d=>d.coverage.requested=2,
  d=>d.results[0].path='.github/workflows/android.yml',
  d=>d.coverage.reasons.push('OPAQUE_ARITHMETIC'),
  d=>d.findings.push({severity:'HIGH'}),
  d=>d.coverage.partial=false,
  d=>d.status='NO_RISK_PATTERN',
  d=>d.results[0].status='NO_RISK_PATTERN',
  d=>d.coverage.reasons=[],
  d=>d.results.push({...d.results[0]})
 ];
 for(const change of changes){
  const d=scoped();change(d);assert.equal(allowPr247ManualStructural(d),false);
 }
});
