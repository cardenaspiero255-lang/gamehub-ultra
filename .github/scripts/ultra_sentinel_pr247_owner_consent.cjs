'use strict';
/**
 * Explicit owner authorization is an authenticated GitHub Actions workflow_dispatch,
 * NOT a GitHub PR review, CI approval, waiver, or self-review by this agent.
 * Every consumer must execute from the trusted default branch and still run
 * its own protected security analysis.
 */
const REPO='cardenaspiero255-lang/gamehub-ultra';
const OWNER='cardenaspiero255-lang';
const OWNER_ID=322222186;
const WORKFLOW='ultra-sentinel-pr247-owner-consent.yml';
const PATH='.github/workflows/'+WORKFLOW;
const NAME='CAR29 PR247 Sole-Maintainer Consent';
const JOB='Authenticated owner consent for PR247 exact SHA';
const SHA=/^[a-f0-9]{40}$/;
const BASE_BLOB='f2831ae24e9687c8a0472c9a42df05f708815be5';
const HEAD_BLOB='5d74541253fa867a7dbea9370be5facfc1be7bf8';
const incomplete=()=>Object.freeze({
 status:'INCOMPLETE',provenanceAuthenticated:false,manualReviewRequired:true,
 independentReviewVerified:false,autoApproveAllowed:false,autoMergeAllowed:false
});
function startTime(r){
 if(!Number.isSafeInteger(r?.id)||r.id<1||
    !Number.isSafeInteger(r.run_attempt)||r.run_attempt<1||
    typeof r.run_started_at!=='string'||!/^20\d\d-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(r.run_started_at))
  return null;
 const t=Date.parse(r.run_started_at);
 return Number.isFinite(t)?t:null;
}
function latestRun(runs){
 if(!runs||!Number.isSafeInteger(runs.total_count)||
    runs.total_count<1||runs.total_count>100||
    !Array.isArray(runs.workflow_runs)||
    runs.workflow_runs.length!==runs.total_count)return null;
 const seen=new Set();
 let latest=null,latestTime=-Infinity;
 for(const run of runs.workflow_runs){
  const t=startTime(run);
  if(t===null||seen.has(run.id))return null;
  seen.add(run.id);
  if(t===latestTime)return null; // Unordered equal-time attempts are ambiguous.
  if(t>latestTime){latestTime=t;latest=run;}
 }
 return latest;
}
function selectAuthenticatedOwnerRun({runs,workflow,mainSha,headSha,jobs}={}){
 const incompleteReceipt=incomplete();
 if(!SHA.test(mainSha||'')||!SHA.test(headSha||'')||
    !Number.isSafeInteger(workflow?.id)||workflow.id<1||
    workflow.name!==NAME||workflow.path!==PATH||workflow.state!=='active')
  return incompleteReceipt;
 const latest=latestRun(runs);
 if(!latest||latest.run_attempt!==1||
    latest.workflow_id!==workflow.id||
    latest.name!=='CAR29-PR247-owner-'+headSha||
    latest.path!==PATH||
    latest.display_title!=='CAR29-PR247-owner-'+headSha||
    latest.event!=='workflow_dispatch'||latest.head_branch!=='main'||
    latest.head_sha!==mainSha||
    latest.repository?.full_name!==REPO||
    latest.head_repository?.full_name!==REPO||
    latest.actor?.id!==OWNER_ID||latest.actor.login!==OWNER||
    latest.actor.type!=='User'||
    latest.triggering_actor?.id!==OWNER_ID||
    latest.triggering_actor.login!==OWNER||
    latest.triggering_actor.type!=='User'||
    latest.status!=='completed'||latest.conclusion!=='success')return incompleteReceipt;
 if(jobs?.total_count!==1||!Array.isArray(jobs.jobs)||jobs.jobs.length!==1)
  return incompleteReceipt;
 const job=jobs.jobs[0];
 if(!Number.isSafeInteger(job?.id)||job.id<1||job.run_id!==latest.id||
    job.name!==JOB||job.status!=='completed'||job.conclusion!=='success')
  return incompleteReceipt;
 return Object.freeze({
  status:'AUTHENTICATED_OWNER_CONSENT_PR247',
  provenanceAuthenticated:true,independentReviewVerified:false,
  manualReviewRequired:true,autoApproveAllowed:false,autoMergeAllowed:false,
  headSha,mainSha,workflowBlobSha:'5d74541253fa867a7dbea9370be5facfc1be7bf8',
  ownerConsentRunId:latest.id,ownerConsentAttempt:latest.run_attempt,
  ownerLogin:OWNER
 });
}
async function attestOwnerConsentPr247({github,owner,repo,pr,files}={}){
 if(pr?.number!==247||owner!==OWNER||repo!=='gamehub-ultra'||
    !github?.rest?.actions?.getWorkflow||
    !github.rest.actions.listWorkflowRuns||
    !github.rest.actions.listJobsForWorkflowRun||
    !github.rest.git?.getRef||!github.rest.pulls?.get||
    !github.rest.pulls.listFiles||
    !github.rest.repos?.getContent||
    typeof github.paginate!=='function')return incomplete();
 try{
  // Shared immutable blob contract; never check out or run candidate PR code.
  const scope=require('./ultra_sentinel_pr247_auth.cjs');
  const currentMain=async()=>((await github.rest.git.getRef({
   owner,repo,ref:'heads/main'})).data?.object?.sha);
  const mainSha=await currentMain();
  const args={owner,repo,pull_number:247,per_page:100};
  if(!scope.isExactPr(pr,mainSha)||!scope.exactFiles(files))return incomplete();
  const wf=(await github.rest.actions.getWorkflow({
   owner,repo,workflow_id:WORKFLOW})).data;
  if(!Number.isSafeInteger(wf?.id)||wf.id<1||
     wf.name!==NAME||wf.path!==PATH||wf.state!=='active')return incomplete();
  const evidence=await Promise.all([
   github.rest.repos.getContent({owner,repo,path:'.github/workflows/sonar.yml',ref:mainSha}),
   github.rest.repos.getContent({owner,repo,path:'.github/workflows/sonar.yml',ref:pr.head.sha})
  ]);
  if(!scope.verifiedBlob(evidence[0].data,BASE_BLOB)||
     !scope.verifiedBlob(evidence[1].data,HEAD_BLOB))return incomplete();
  const getRuns=async()=>((await github.rest.actions.listWorkflowRuns({
   owner,repo,workflow_id:wf.id,event:'workflow_dispatch',
   per_page:100,page:1})).data);
  const runs=await getRuns();
  const run=latestRun(runs);
  if(!run)return incomplete();
  const jobs=(await github.rest.actions.listJobsForWorkflowRun({
   owner,repo,run_id:run.id,per_page:100,filter:'latest'})).data;
  const proof=selectAuthenticatedOwnerRun({
   runs,workflow:wf,headSha:pr.head.sha,mainSha,jobs});
  if(proof.status!=='AUTHENTICATED_OWNER_CONSENT_PR247')return incomplete();
  // Full second read after async work: stale consent cannot certify new HEAD,
  // altered main, a newer owner dispatch attempt, or replaced PR blobs.
  const after=(await github.rest.pulls.get({owner,repo,pull_number:247})).data;
  const afterFiles=await github.paginate(github.rest.pulls.listFiles,args);
  if(!scope.isExactPr(after,mainSha)||!scope.exactFiles(afterFiles)||
     await currentMain()!==mainSha)return incomplete();
  const newRuns=await getRuns();
  const terminal=selectAuthenticatedOwnerRun({
   runs:newRuns,workflow:wf,headSha:after.head.sha,mainSha,jobs});
  if(terminal.status!=='AUTHENTICATED_OWNER_CONSENT_PR247'||
     terminal.ownerConsentRunId!==proof.ownerConsentRunId||
     terminal.ownerConsentAttempt!==proof.ownerConsentAttempt)return incomplete();
  return terminal;
 }catch(_){return incomplete();}
}
module.exports={selectAuthenticatedOwnerRun,attestOwnerConsentPr247};
