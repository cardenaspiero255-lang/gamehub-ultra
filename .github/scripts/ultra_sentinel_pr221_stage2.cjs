'use strict';
/**
 * Authenticated protected-job receipt for PR221. Executable only from trusted
 * default-branch reviewers. Untrusted PR source is never imported or executed.
 * Never a GitHub approval, CI certification or permission to merge.
 */
const {attestExactPr221}=require('./ultra_sentinel_pr221_scope.cjs');
const REPO='cardenaspiero255-lang/gamehub-ultra';
const WORKFLOW='ultra-sentinel-pr221-protected.yml';
const WORKFLOW_PATH='.github/workflows/'+WORKFLOW;
const WORKFLOW_NAME='Ultra Sentinel PR221 Protected Exact Delta';
const JOB_NAME='Trusted main PR221 exact blobs (manual review required)';
const SHA=/^[a-f0-9]{40}$/;
const incomplete=()=>Object.freeze({
 status:'INCOMPLETE',provenanceAuthenticated:false,manualReviewRequired:true,
 autoApproveAllowed:false,autoMergeAllowed:false
});
const notApplicable=()=>Object.freeze({
 status:'NOT_APPLICABLE',provenanceAuthenticated:false,manualReviewRequired:true,
 autoApproveAllowed:false,autoMergeAllowed:false
});
function startedAt(run){
 if(!Number.isSafeInteger(run?.id)||run.id<1||
    !Number.isSafeInteger(run?.run_attempt)||run.run_attempt<1||
    typeof run.run_started_at!=='string'||
    !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(run.run_started_at))
  throw Error('INVALID_RUN_TIMESTAMP');
 const time=Date.parse(run.run_started_at);
 if(!Number.isFinite(time))throw Error('INVALID_RUN_TIMESTAMP');
 return time;
}
function latestProtectedRun(result,workflowId,head,base,minRunStartedAt=null){
 if(!Number.isSafeInteger(result?.total_count)||result.total_count<1||
    result.total_count>100||!Array.isArray(result.workflow_runs)||
    result.workflow_runs.length!==result.total_count)
  throw Error('INCOMPLETE_PROTECTED_RUNS');
 const ids=new Set();
 let latest=null;
 let latestTime=-Infinity;
 for(const run of result.workflow_runs){
  const time=startedAt(run);
  if(ids.has(run.id))throw Error('DUPLICATE_RUN_ID');
  ids.add(run.id);
  if(run.workflow_id!==workflowId||run.name!==WORKFLOW_NAME||
     run.event!=='pull_request_target'||run.head_sha!==head||
     run.head_repository?.full_name!==REPO||
     !Array.isArray(run.pull_requests)||run.pull_requests.length!==1)
   throw Error('UNTRUSTED_RUN_IDENTITY');
  // The run's exact PR HEAD and BASE must be established by authenticated
  // GitHub metadata, not inferred from the job name or current branch state.
  // Missing linkage fails closed even if the protected job finished green.
  const link=run.pull_requests[0];
  if(link?.number!==221||link.head?.sha!==head||link.base?.sha!==base)
   throw Error('INCORRECT_PR_LINKAGE');
  if(time===latestTime&&latest&&run.id!==latest.id)
   throw Error('AMBIGUOUS_RUN_ORDER');
  if(time>latestTime||time===latestTime&&run.run_attempt>latest?.run_attempt){
   latest=run;
   latestTime=time;
  }
 }
 if(!latest||latest.status!=='completed'||latest.conclusion!=='success')
  throw Error('LATEST_PROTECTED_RUN_NOT_SUCCESS');
 // An authenticated historical run is still historical: it must not certify
 // reviewer code deployed only after trusted main advanced.
 if(minRunStartedAt!==null){
  if(typeof minRunStartedAt!=='string'||
     !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(minRunStartedAt)||
     !Number.isFinite(Date.parse(minRunStartedAt))||
     latestTime<=Date.parse(minRunStartedAt))
   throw Error('HISTORICAL_PROTECTED_RUN');
 }
 return latest;
}
function validatedJob(data,runId){
 if(data?.total_count!==1||!Array.isArray(data.jobs)||data.jobs.length!==1)
  throw Error('INCOMPLETE_PROTECTED_JOBS');
 const j=data.jobs[0];
 if(!Number.isSafeInteger(j.id)||j.id<1||j.run_id!==runId||
    j.name!==JOB_NAME||j.status!=='completed'||j.conclusion!=='success')
  throw Error('INVALID_PROTECTED_JOB');
}
async function attestScopedPr221({github,owner,repo,pr,files}={}){
 if(pr?.number!==221)return notApplicable();
 if(owner!=='cardenaspiero255-lang'||repo!=='gamehub-ultra'||!Array.isArray(files)||
    !github?.rest?.actions?.getWorkflow||!github.rest.actions.listWorkflowRuns||
    !github.rest.actions.listJobsForWorkflowRun||
    !github.rest.git?.getRef||!github.rest.pulls?.get)
  return incomplete();
 try{
  const exact=await attestExactPr221({github,owner,repo});
  if(exact.status!=='AUTHENTICATED_EXACT_DELTA_MANUAL_REVIEW_REQUIRED'||
     exact.provenanceAuthenticated!==true||
     exact.manualReviewRequired!==true||exact.autoApproveAllowed!==false||
     exact.autoMergeAllowed!==false||!SHA.test(exact.headSha||'')||
     !SHA.test(exact.mainSha||'')||
     !SHA.test(exact.recordedBaseSha||'')||
     pr.head?.sha!==exact.headSha||pr.base?.sha!==exact.recordedBaseSha||
     (exact.recordedBaseSha!==exact.mainSha&&
      (typeof exact.minRunStartedAt!=='string'||
       !Number.isFinite(Date.parse(exact.minRunStartedAt)))))
   return incomplete();
  const metadata=(await github.rest.actions.getWorkflow({
   owner,repo,workflow_id:WORKFLOW})).data;
  if(!Number.isSafeInteger(metadata?.id)||metadata.id<1||
     metadata.name!==WORKFLOW_NAME||metadata.path!==WORKFLOW_PATH||
     metadata.state!=='active')return incomplete();
  const fetchLatest=async()=>{
   const response=(await github.rest.actions.listWorkflowRuns({
    owner,repo,workflow_id:metadata.id,head_sha:exact.headSha,
    event:'pull_request_target',per_page:100,page:1
   })).data;
   return latestProtectedRun(response,metadata.id,exact.headSha,
    exact.recordedBaseSha,exact.minRunStartedAt);
  };
  const latest=await fetchLatest();
  const jobs=(await github.rest.actions.listJobsForWorkflowRun({
    owner,repo,run_id:latest.id,per_page:100,filter:'latest'
  })).data;
  validatedJob(jobs,latest.id);
  const after=(await github.rest.pulls.get({
   owner,repo,pull_number:221})).data;
  const base=(await github.rest.git.getRef({owner,repo,ref:'heads/main'})).data;
  if(after?.state!=='open'||after.head?.sha!==exact.headSha||
     after.base?.sha!==exact.recordedBaseSha||base?.object?.sha!==exact.mainSha)
   return incomplete();
  const terminal=await fetchLatest();
  if(terminal.id!==latest.id||terminal.run_attempt!==latest.run_attempt)
   return incomplete();
  return Object.freeze({
   status:'SCOPED_AUTHENTICATED_MANUAL_REVIEW',
   headSha:exact.headSha,mainSha:exact.mainSha,
   protectedRunId:latest.id,protectedRunAttempt:latest.run_attempt,
   provenanceAuthenticated:true,manualReviewRequired:true,
   autoApproveAllowed:false,autoMergeAllowed:false
  });
 }catch(_){return incomplete();}
}
module.exports={attestScopedPr221,latestProtectedRun,validatedJob};
