'use strict';
/**
 * Trusted-main read-only bridge for the one reviewed V2 delta in PR #172.
 * This does NOT certify opaque YAML semantics, approve, or merge anything.
 * A candidate PR cannot supply GitHub API evidence: only main-checkout
 * github-script with authenticated Actions:read may call this function.
 */
const REPO='cardenaspiero255-lang/gamehub-ultra';
const OWNER='cardenaspiero255-lang',REPOSITORY='gamehub-ultra';
const REVIEWER_ID=382593687;
const REVIEWER_NAME='Ultra Sentinel PR172 Protected Delta Review';
const REVIEWER_PATH='.github/workflows/ultra-sentinel-ci-delta-protected.yml';
const TRUSTED_JOB='Trusted main PR172 SHA/tree/31 jobs (manual review required)';
const CANDIDATE_JOB='Unprivileged protected-delta regression tests';
const SHA=/^[a-f0-9]{40}$/;
const FILES=Object.freeze({
 '.github/workflows/android.yml':['modified','0f78d28d5e8974b988490bd9facdf63cb6be6485'],
 '.github/workflows/coverage.yml':['modified','e1042881b00273aec31c2a64131ff0df7042e4ef'],
 '.github/scripts/check-ci-safety-contract.py':['modified','58357888ddd96d2980061293100ebc0795fdb800'],
 '.github/scripts/test-ci-safety-contract-regressions.py':['modified','04ab3e26f8eb108323827df2a2e99c72c3216af8'],
 'docs/CI_ACCELERATION_172_V2.md':['added','786e327cdecc2b8985df1b08e1bb081d17d8e3b4']
});
const notApplicable=Object.freeze({status:'NOT_APPLICABLE',provenanceAuthenticated:false,
 manualReviewRequired:true,autoApproveAllowed:false,autoMergeAllowed:false});
function incomplete(reason){return Object.freeze({status:'INCOMPLETE',reason,
 provenanceAuthenticated:false,manualReviewRequired:true,
 autoApproveAllowed:false,autoMergeAllowed:false});}
function validatePr(pr,head,main){
 if(!pr||pr.number!==172||pr.state!=='open'||pr.changed_files!==5||
  pr.head?.sha!==head||pr.base?.sha!==main||
  pr.head?.repo?.full_name!==REPO||pr.base?.repo?.full_name!==REPO||
  pr.base?.ref!=='main'||pr.user?.login!==OWNER||
  pr.user?.id!==322222186||pr.user?.type!=='User'||
  pr.author_association!=='OWNER')throw Error('PR_IDENTITY_CHANGED');
}
function validateFiles(files){
 if(!Array.isArray(files)||files.length!==5)throw Error('FILES_INCOMPLETE');
 const seen=new Set();
 for(const file of files){
  if(!file||typeof file.filename!=='string'||seen.has(file.filename))throw Error('DUPLICATE_FILE');
  const reviewed=Object.hasOwn(FILES,file.filename)?FILES[file.filename]:null;
  if(!reviewed||file.status!==reviewed[0]||file.sha!==reviewed[1])throw Error('UNREVIEWED_DELTA');
  seen.add(file.filename);
 }
}
function parsedRunTime(r){
 if(!Number.isSafeInteger(r?.id)||r.id<1||!Number.isSafeInteger(r.run_attempt)||r.run_attempt<1||
    !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(r.run_started_at||''))
  throw Error('INVALID_RUN_ORDER');
 const ms=Date.parse(r.run_started_at);
 if(!Number.isFinite(ms))throw Error('INVALID_RUN_ORDER');
 return ms;
}
function latestRun(runs,head,base){
 let best=null,bestAt=-Infinity;
 const distinct=new Set();
 for(const r of runs){
  const started=parsedRunTime(r);
  if(distinct.has(r.id))throw Error('DUPLICATE_RUN');
  distinct.add(r.id);
  // The runner GITHUB_SHA is the base for pull_request_target, but the
  // Actions REST workflow_runs.head_sha is the PR HEAD (verified in live runs).
  if(r.workflow_id!==REVIEWER_ID||r.name!==REVIEWER_NAME||
   r.event!=='pull_request_target'||r.head_sha!==head||
   r.head_repository?.full_name!==REPO||
   !Array.isArray(r.pull_requests)||r.pull_requests.length!==1)
   throw Error('UNTRUSTED_RUN_PROVENANCE');
  // An unlinked run could be a newer pending/failed PR172 attempt:
  // never silently skip it and reuse an earlier green attestation.
  // Another PR can refer to the same commit; only accept exact PR linkage.
  const linked=r.pull_requests[0];
  if(linked?.number!==172)continue;
  if(linked.head?.sha!==head||linked.base?.sha!==base)
   throw Error('UNTRUSTED_RUN_PROVENANCE');
  if(started===bestAt&&r.id!==best?.id)throw Error('AMBIGUOUS_RUN_ORDER');
  if(started>bestAt||(started===bestAt&&r.run_attempt>best.run_attempt)){
   best=r;bestAt=started;
  }
 }
 if(!best||best.status!=='completed'||best.conclusion!=='success')
  throw Error('LATEST_TRUSTED_RUN_NOT_SUCCESS');
 return best;
}
function validateJobs(result,runId){
 if(!result||result.total_count!==2||!Array.isArray(result.jobs)||
    result.jobs.length!==2)throw Error('INCOMPLETE_JOBS');
 const names=new Map();
 for(const j of result.jobs){
  if(!j||!Number.isSafeInteger(j.id)||j.id<1||j.run_id!==runId||
   typeof j.name!=='string'||names.has(j.name)||j.status!=='completed')
   throw Error('INVALID_JOB');
  names.set(j.name,j.conclusion);
 }
 if(names.get(TRUSTED_JOB)!=='success'||names.get(CANDIDATE_JOB)!=='skipped')
  throw Error('TRUSTED_JOB_NOT_SUCCESS');
}
async function attestScopedPr172({github,owner,repo,pr,files}={}){
 if(pr?.number===221)return require('./ultra_sentinel_pr221_stage2.cjs')
  .attestScopedPr221({github,owner,repo,pr,files});
 if(pr?.number!==172)return notApplicable;
 try{
  if(owner!==OWNER||repo!==REPOSITORY||!github?.rest?.git?.getRef||
     !github.rest.pulls?.get||!github.rest.actions?.getWorkflow||
     !github.rest.actions?.listWorkflowRuns||!github.rest.actions?.listJobsForWorkflowRun)
   return incomplete('NO_TRUSTED_READ_ONLY_CLIENT');
  const head=pr.head?.sha;
  if(!SHA.test(head||''))throw Error('INVALID_HEAD');
  const getMain=async()=>{
   const data=(await github.rest.git.getRef({owner,repo,ref:'heads/main'})).data;
   if(!SHA.test(data?.object?.sha||''))throw Error('INVALID_MAIN_REF');
   return data.object.sha;
  };
  const main=await getMain();
  validatePr(pr,head,main);
  validateFiles(files);
  const wf=(await github.rest.actions.getWorkflow({
   owner,repo,workflow_id:'ultra-sentinel-ci-delta-protected.yml'})).data;
  if(wf?.id!==REVIEWER_ID||wf?.name!==REVIEWER_NAME||
     wf?.path!==REVIEWER_PATH||wf?.state!=='active')
   throw Error('UNTRUSTED_WORKFLOW_ID');
  const getLatest=async()=>{
   const result=(await github.rest.actions.listWorkflowRuns({owner,repo,
     workflow_id:wf.id,head_sha:head,event:'pull_request_target',
     per_page:100,page:1})).data;
   if(!Number.isSafeInteger(result?.total_count)||result.total_count<1||
      result.total_count>100||!Array.isArray(result.workflow_runs)||
      result.workflow_runs.length!==result.total_count)
    throw Error('INCOMPLETE_RUN_HISTORY');
   return latestRun(result.workflow_runs,head,main);
  };
  const latest=await getLatest();
  const jobs=(await github.rest.actions.listJobsForWorkflowRun({owner,repo,
    run_id:latest.id,per_page:100,filter:'latest'})).data;
  validateJobs(jobs,latest.id);
  const after=(await github.rest.pulls.get({owner,repo,pull_number:172})).data;
  validatePr(after,head,main);
  // A failed/pending newer attempt must supersede a previously green run.
  const terminal=await getLatest();
  if(terminal.id!==latest.id||terminal.run_attempt!==latest.run_attempt)
   throw Error('PROTECTED_RUN_CHANGED_DURING_VERIFICATION');
  if((await getMain())!==main)throw Error('MAIN_MOVED_DURING_VERIFICATION');
  return Object.freeze({status:'SCOPED_AUTHENTICATED_MANUAL_REVIEW',
   headSha:head,mainSha:main,protectedRunId:latest.id,
   protectedRunAttempt:latest.run_attempt,
   provenanceAuthenticated:true,manualReviewRequired:true,
   autoApproveAllowed:false,autoMergeAllowed:false});
 }catch(_){return incomplete('UNVERIFIED_PROTECTED_EVIDENCE');}
}
const ALLOWED=new Set(['WORKFLOW_SEMANTIC_INCOMPLETE','SHELL_FUNCTION',
 'SENSITIVE_ACTION_ENV','OPAQUE_ARITHMETIC']);
function permitScopedOpaqueYaml(structural){
 if(!structural||structural.status!=='INCOMPLETE'||
    !Array.isArray(structural.findings)||
    structural.findings.some(x=>x?.severity==='HIGH'||x?.severity==='BLOCKER')||
    structural.coverage?.partial!==true||
    !Array.isArray(structural.coverage.reasons)||
    structural.coverage.reasons.length===0||structural.coverage.reasons.some(x=>!ALLOWED.has(x)))
  return false;
 const original=structural.coverage.requested===2&&structural.coverage.scanned===2;
 const pr221=structural.coverage.requested===1&&structural.coverage.scanned===1&&
  Array.isArray(structural.results)&&structural.results.length===1&&
  structural.results[0]?.path==='.github/workflows/android.yml'&&
  structural.results[0]?.status==='INCOMPLETE';
 return original||pr221;
}
module.exports={attestScopedPr172,permitScopedOpaqueYaml};
