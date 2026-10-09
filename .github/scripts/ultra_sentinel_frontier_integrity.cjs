'use strict';
/* Ultra Sentinel SSS phase 1: authenticated GitHub CI evidence + safe repair metadata.
 * Only pass GitHub API data obtained in a trusted read-only workflow.
 * This module NEVER approves, merges, commits or executes PR-supplied source. */
const {orchestrate}=require('./ultra_sentinel_orchestrator.cjs');
const SHA=/^[0-9a-f]{40}$/i;
const REPO='cardenaspiero255-lang/gamehub-ultra';
const REQUIRED=['Android build','Unit Test Coverage'];
const EVENTS=new Set(['pull_request']);
const PATHS=Object.freeze({'Android build':'.github/workflows/android.yml','Unit Test Coverage':'.github/workflows/coverage.yml'});
const FAILURES=new Set(['failure','cancelled','timed_out','action_required','startup_failure']);
function attestCi({sha,repo,runs,apiComplete=true,trustedWorkflows,changedFiles,changedFilesComplete}={}){
 const counts={success:0,pending:0,failed:0};
 const empty=()=>({
  schema:'ultra-sentinel-verified-ci/v1',sha:SHA.test(sha||'')?sha.toLowerCase():null,
  status:'UNKNOWN',counts:{...counts},workflows:[],autoApproveAllowed:false,autoMergeAllowed:false
 });
 if(typeof sha!=='string'||!SHA.test(sha)||repo!==REPO||!Array.isArray(runs)||
  apiComplete!==true||runs.length>100||
  changedFilesComplete!==true||!Array.isArray(changedFiles)||
  !trustedWorkflows||typeof trustedWorkflows!=='object')return empty();
 // A modified CI definition can emit success without doing the intended checks.
 // Check the WHOLE PR's changed file list, not the names of its run results.
 if(REQUIRED.some(name=>changedFiles.includes(PATHS[name])))
  return {...empty(),status:'UNTRUSTED'};
 if(REQUIRED.some(name=>!Number.isSafeInteger(trustedWorkflows[name]?.id)||
    trustedWorkflows[name].id<1||trustedWorkflows[name].path!==PATHS[name]))
  return empty();
 const chosen=[];
 for(const name of REQUIRED){
  const trusted=runs.filter(run=>run&&run.name===name&&
   typeof run.head_sha==='string'&&run.head_sha.toLowerCase()===sha.toLowerCase()&&
   run.repository?.full_name===repo&&run.head_repository?.full_name===repo&&
   EVENTS.has(run.event)&&run.workflow_id===trustedWorkflows[name].id&&
   run.path===trustedWorkflows[name].path&&Number.isSafeInteger(run.id)&&run.id>0&&
   Number.isSafeInteger(run.run_number)&&run.run_number>0&&
   Number.isSafeInteger(run.run_attempt)&&run.run_attempt>0&&
   (run.status==='completed'||['in_progress','queued','waiting','pending','requested'].includes(run.status))
  ).sort((a,b)=>(b.run_number-a.run_number)||(b.run_attempt-a.run_attempt)||(b.id-a.id));
  const latest=trusted[0];
  const state=!latest?'pending':latest.status!=='completed'?'pending':
   latest.conclusion==='success'?'success':FAILURES.has(latest.conclusion)?'failed':'pending';
  counts[state]++;
  chosen.push({name,state,runId:latest?.id??null,attempt:latest?.run_attempt??null});
 }
 return {schema:'ultra-sentinel-verified-ci/v1',sha:sha.toLowerCase(),
  status:counts.failed?'FAILED':counts.pending?'WAITING':'PASS',
  counts,workflows:chosen,autoApproveAllowed:false,autoMergeAllowed:false};
}
function summarizeDrafts({analysis,sources={},sha}={}){
 const valid=typeof sha==='string'&&SHA.test(sha);
 const needsInfo=!valid||analysis?.coverage?.partial===true||analysis?.verdict==='INCOMPLETE';
 const result=valid?orchestrate({analysis,sources,sha,checks:{}}):null;
 const candidates=(result?.fixer?.proposals||[]).slice(0,3).map(({proposal,judgement})=>({
  rule:proposal.rule,path:proposal.filename,linesChanged:proposal.linesChanged,
  status:judgement.status,pendingChecks:judgement.pendingChecks?.slice(0,5)||[]
 }));
 return {schema:'ultra-sentinel-safe-repair/v1',
  status:needsInfo?'INCOMPLETE':candidates.length?'HUMAN_REVIEW_REQUIRED':'NO_SAFE_TEMPLATE',
  generated:candidates.length,candidates,
  autoApplyAllowed:false,autoCommitAllowed:false,autoMergeAllowed:false};
}
module.exports={attestCi,summarizeDrafts};
