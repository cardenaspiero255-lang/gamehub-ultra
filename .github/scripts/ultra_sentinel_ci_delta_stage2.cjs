'use strict';
/**
 * PR #172 protected read-only CI delta proof (stage 2).
 * `evaluateRemote(get)` consumes arbitrary caller-controlled data and can
 * NEVER claim authenticated evidence: its maximum status is SCOPED.
 * ONLY the non-exported main-branch CLI creates GitHub's HTTPS client from
 * the protected workflow token and may label validated results authenticated.
 * No GitHub writes, approval, bypass of AST/human review or automatic merge.
 */
const {classifyCiDelta}=require('./ultra_sentinel_ci_delta.cjs');
const crypto=require('node:crypto');
const OWNER='cardenaspiero255-lang',REPO_NAME='gamehub-ultra';
const REPO=OWNER+'/'+REPO_NAME;
const HEAD='bb08782350201f97576a974a4684a611c21701c0';
const BASE='7aa5df3e3c704965862ea728fb4c390d2c15a7f0';
const SHA=/^[a-f0-9]{40}$/;
const FILES=Object.freeze({
 '.github/workflows/android.yml':['android','0f78d28d5e8974b988490bd9facdf63cb6be6485'],
 '.github/workflows/coverage.yml':['coverage','e1042881b00273aec31c2a64131ff0df7042e4ef'],
 '.github/scripts/check-ci-safety-contract.py':['safetyContract','58357888ddd96d2980061293100ebc0795fdb800'],
 '.github/scripts/test-ci-safety-contract-regressions.py':['regressions','04ab3e26f8eb108323827df2a2e99c72c3216af8'],
 'docs/CI_ACCELERATION_172_V2.md':['documentation','786e327cdecc2b8985df1b08e1bb081d17d8e3b4']
});
const JOBS=Object.freeze({
 'Android build':['quality-lint','quality-contract-shard','quality-contracts',
  'release-bundle','quality','device-validation','build','device-validation-release-apk',
  'device-validation-ui','device-validation-baseline','device-validation-macro-startup',
  'device-validation-macro-library','device-validation-macro-settings','android-test-shards',
  ...Array.from({length:8},(_,i)=>'android-test-shard-'+i)],
 'Unit Test Coverage':['coverage',...Array.from({length:8},(_,i)=>'coverage-shard-'+i)]
});
const RUNS=Object.freeze({
 'Android build':['android.yml',363810233],
 'Unit Test Coverage':['coverage.yml',367848611]
});
function incomplete(reason){
 return Object.freeze({status:'INCOMPLETE',reason,provenanceAuthenticated:false,
  autoApproveAllowed:false,autoMergeAllowed:false,manualReviewRequired:true});
}
const gitBlobSha=s=>crypto.createHash('sha1').update('blob '+Buffer.byteLength(s)+'\0'+s).digest('hex');
function decode(record){
 if(record?.type!=='file'||record.encoding!=='base64'||
    !SHA.test(record.sha||'')||typeof record.content!=='string'||
    !Number.isSafeInteger(record.size)||record.size<0||record.size>160000)
  throw Error('INVALID_CONTENT');
 const data=record.content.replace(/\s/g,'');
 if(!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(data))
  throw Error('INVALID_BASE64');
 const bytes=Buffer.from(data,'base64');
 if(bytes.length!==record.size||bytes.toString('base64')!==data)throw Error('NON_CANONICAL_BASE64');
 const body=bytes.toString('utf8');
 if(!Buffer.from(body).equals(bytes)||gitBlobSha(body)!==record.sha)
  throw Error('INVALID_BLOB_HASH');
 return {body,sha:record.sha};
}
function validatePr(pr){
 if(pr?.number!==172||pr?.state!=='open'||pr.changed_files!==5||
  pr.head?.sha!==HEAD||pr.head?.repo?.full_name!==REPO||
  pr.base?.ref!=='main'||pr.base?.repo?.full_name!==REPO||
  !SHA.test(pr.base?.sha||''))throw Error('PR_IDENTITY_CHANGED');
}
function validateJobEvidence(runs,jobs,workflowName,workflowId){
 if(!runs||!Number.isSafeInteger(runs.total_count)||runs.total_count<1||
  runs.total_count>100||!Array.isArray(runs.workflow_runs)||
  runs.workflow_runs.length!==runs.total_count)throw Error('INCOMPLETE_RUN_HISTORY');
 for(const r of runs.workflow_runs){
  if(!r||r.workflow_id!==workflowId||r.name!==workflowName||
   r.head_sha!==HEAD||r.event!=='pull_request'||
   r.head_repository?.full_name!==REPO||!Number.isSafeInteger(r.id)||
   !Number.isSafeInteger(r.run_attempt)||r.run_attempt<1||
   !Array.isArray(r.pull_requests)||!r.pull_requests.some(x=>x?.number===172))
    throw Error('UNTRUSTED_RUN');
 }
 const latest=[...runs.workflow_runs].sort((a,b)=>b.id-a.id||b.run_attempt-a.run_attempt)[0];
 if(latest.status!=='completed'||latest.conclusion!=='success')
  throw Error('LATEST_RUN_NOT_GREEN');
 if(!jobs||!Number.isSafeInteger(jobs.total_count)||!Array.isArray(jobs.jobs)||
    jobs.total_count!==JOBS[workflowName].length||
    jobs.jobs.length!==jobs.total_count)throw Error('INCOMPLETE_CI_JOBS');
 const seen=new Set(),ids=new Set();
 for(const j of jobs.jobs){
  if(!j||!Number.isSafeInteger(j.id)||j.id<1||ids.has(j.id)||
     j.run_id!==latest.id||typeof j.name!=='string'||
     seen.has(j.name)||!JOBS[workflowName].includes(j.name)||
     j.status!=='completed'||j.conclusion!=='success')
   throw Error('BAD_CI_JOB');
  seen.add(j.name);ids.add(j.id);
 }
 if(seen.size!==JOBS[workflowName].length)throw Error('MISSING_CI_JOB');
 return latest.id;
}
async function evaluateRemote(get){
 try{
  if(typeof get!=='function')return incomplete('MISSING_READ_ONLY_CLIENT');
  const before=await get('pulls/172');
  validatePr(before);
  const mainBefore=(await get('git/ref/heads/main'))?.object?.sha;
  if(!SHA.test(mainBefore||''))return incomplete('MAIN_REF_MISSING');
  const commit=await get('git/commits/'+HEAD);
  if(commit?.sha!==HEAD||!SHA.test(commit.tree?.sha||''))return incomplete('HEAD_COMMIT_MISSING');
  const tree=await get('git/trees/'+commit.tree.sha+'?recursive=1');
  if(tree?.sha!==commit.tree.sha||tree?.truncated!==false||
     !Array.isArray(tree.tree))return incomplete('GIT_TREE_INCOMPLETE');
  const treeMap=new Map();
  for(const e of tree.tree){
   if(!Object.hasOwn(FILES,e?.path||''))continue;
   if(treeMap.has(e.path)||e.type!=='blob'||e.mode!=='100644'||
      e.sha!==FILES[e.path][1])return incomplete('GIT_TREE_TAMPERED');
   treeMap.set(e.path,e.sha);
  }
  if(treeMap.size!==5)return incomplete('GIT_TREE_MISSING_FILES');
  const changes=await get('pulls/172/files?per_page=100&page=1');
  if(!Array.isArray(changes)||changes.length!==5)
   return incomplete('FILE_LIST_MISSING');
  const candidateEntries=[],baseEntries=[];
  for(const pair of Object.entries(FILES)){
   const file=pair[0],key=pair[1][0],expectedSha=pair[1][1];
   const entry=changes.filter(f=>f?.filename===file);
   if(entry.length!==1||entry[0].sha!==expectedSha||
      entry[0].status!==(key==='documentation'?'added':'modified'))
    return incomplete('CHANGED_FILE_TAMPERED');
   const actual=decode(await get('contents/'+file+'?ref='+HEAD));
   if(actual.sha!==expectedSha||actual.sha!==treeMap.get(file))
    return incomplete('UNVERIFIED_HEAD_BLOB');
   candidateEntries.push([key,actual.body]);
   if(key!=='documentation'){
    const historical=decode(await get('contents/'+file+'?ref='+BASE));
    const mainCurrent=decode(await get('contents/'+file+'?ref='+mainBefore));
    if(historical.sha!==mainCurrent.sha||historical.body!==mainCurrent.body)
     return incomplete('MAIN_PROTECTED_SOURCES_CHANGED');
    baseEntries.push([key,historical.body]);
   }
  }
  const trusted=Object.fromEntries(baseEntries),candidate=Object.fromEntries(candidateEntries);
  const delta=classifyCiDelta({
   repo:REPO,baseSha:BASE,trustedBaseSha:BASE,headSha:HEAD,expectedHeadSha:HEAD,
   trusted,candidate,files:changes,changedFileCount:changes.length
  });
  if(delta.status!=='SCOPED_DELTA_REVIEW_REQUIRED')
   return incomplete('DELTA_POLICY_REJECTED');
  const runIds=[],runWitnesses=[];
  for(const row of Object.entries(RUNS)){
   const name=row[0],info=row[1];
   const workflow=await get('actions/workflows/'+info[0]);
   if(workflow?.id!==info[1]||workflow.name!==name)
    return incomplete('WORKFLOW_ID_CHANGED');
   const hist=await get('actions/workflows/'+workflow.id+
    '/runs?head_sha='+HEAD+'&event=pull_request&per_page=100');
   if(!hist||hist.total_count<1||hist.total_count>100||
      !Array.isArray(hist.workflow_runs)||hist.workflow_runs.length!==hist.total_count)
    return incomplete('RUN_HISTORY_INCOMPLETE');
   const latest=[...hist.workflow_runs].sort((a,b)=>b.id-a.id||
     b.run_attempt-a.run_attempt)[0];
   if(!latest||!Number.isSafeInteger(latest.id))
    return incomplete('LATEST_RUN_MISSING');
   const jobs=await get('actions/runs/'+latest.id+'/jobs?per_page=100&filter=latest');
   runIds.push(validateJobEvidence(hist,jobs,name,workflow.id));
   runWitnesses.push({name,workflowId:workflow.id,runId:latest.id,
    attempt:latest.run_attempt});
  }
  // TOCTOU: a later failing or pending run cannot hide behind old success.
  for(const witness of runWitnesses){
   const again=await get('actions/workflows/'+witness.workflowId+
    '/runs?head_sha='+HEAD+'&event=pull_request&per_page=100');
   if(!again||!Number.isSafeInteger(again.total_count)||
      again.total_count<1||again.total_count>100||
      !Array.isArray(again.workflow_runs)||
      again.workflow_runs.length!==again.total_count)
    return incomplete('RUN_HISTORY_CHANGED');
   const recent=[...again.workflow_runs].sort((a,b)=>b.id-a.id||
     b.run_attempt-a.run_attempt)[0];
   if(!recent||recent.id!==witness.runId||
      recent.run_attempt!==witness.attempt||
      recent.status!=='completed'||recent.conclusion!=='success')
    return incomplete('NEWER_OR_CHANGED_CI_RUN');
  }
  const after=await get('pulls/172');
  validatePr(after);
  const mainAfter=(await get('git/ref/heads/main'))?.object?.sha;
  if(after.base.sha!==before.base.sha||mainAfter!==mainBefore)
   return incomplete('MAIN_MOVED_DURING_REVIEW');
  // Final, near-simultaneous snapshots. Any later run invalidates earlier
  // witnesses; no API reads are performed after these attestations.
  if(runWitnesses.length!==2)return incomplete('CI_WITNESS_COUNT_CHANGED');
  const first=runWitnesses[0],second=runWitnesses[1];
  const histories=await Promise.all([
   get('actions/workflows/'+first.workflowId+
    '/runs?head_sha='+HEAD+'&event=pull_request&per_page=100'),
   get('actions/workflows/'+second.workflowId+
    '/runs?head_sha='+HEAD+'&event=pull_request&per_page=100')
  ]);
  for(let i=0;i<runWitnesses.length;i++){
   const witness=runWitnesses[i],history=histories[i];
   if(!history||!Number.isSafeInteger(history.total_count)||
      history.total_count<1||history.total_count>100||
      !Array.isArray(history.workflow_runs)||
      history.workflow_runs.length!==history.total_count)
    return incomplete('FINAL_HISTORY_INCOMPLETE');
   const newest=[...history.workflow_runs].sort((a,b)=>b.id-a.id||
    b.run_attempt-a.run_attempt)[0];
   if(!newest||newest.id!==witness.runId||
      newest.run_attempt!==witness.attempt||
      newest.status!=='completed'||newest.conclusion!=='success')
    return incomplete('LATEST_FINAL_RUN_NOT_GREEN');
  }
  return Object.freeze({status:'SCOPED_CI_REVIEW_REQUIRED',
   schema:'ultra-sentinel-ci-delta-protected/v2',headSha:HEAD,mainSha:mainBefore,
   observedJobs:31,observedRunIds:runIds,
   provenanceAuthenticated:false,manualReviewRequired:true,
   autoApproveAllowed:false,autoMergeAllowed:false});
 }catch(_){return incomplete('UNVERIFIED_EXTERNAL_EVIDENCE');}
}
// Private transport boundary: never export a credential-bearing client.
function protectedGitHubClient(token){
 if(typeof token!=='string'||token.length<10)throw Error('TOKEN_MISSING');
 return async function get(relative){
  if(typeof relative!=='string'||!/^([A-Za-z0-9_.\/\?=&%-]+)$/.test(relative)||
    relative.includes('..')||relative.startsWith('/'))throw Error('INVALID_API_PATH');
  const response=await fetch('https://api.github.com/repos/'+REPO+'/'+relative,{
   method:'GET',redirect:'error',headers:{Authorization:'Bearer '+token,
    Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'},
   signal:AbortSignal.timeout(20000)
  });
  if(!response.ok)throw Error('GITHUB_API_ERROR');
  return response.json();
 };
}
async function main(){
 const validEvent=['pull_request_target','push'];
 if(process.env.GITHUB_ACTIONS!=='true'||
    !validEvent.includes(process.env.GITHUB_EVENT_NAME)||
    process.env.GITHUB_REPOSITORY!==REPO||process.env.PR_NUMBER!=='172'||
    (process.env.GITHUB_EVENT_NAME==='push'&&process.env.GITHUB_REF!=='refs/heads/main'))
  throw Error('PROTECTED_RUNNER_REQUIRED');
 const result=await evaluateRemote(protectedGitHubClient(process.env.GITHUB_TOKEN));
 if(result.status!=='SCOPED_CI_REVIEW_REQUIRED'){
  process.stdout.write(JSON.stringify(result)+'\n');
  process.exitCode=1;
  return;
 }
 // Only a reviewed default-branch runner may label the collected evidence.
 // Even here, independent AST/human policy and explicit merge remain required.
 process.stdout.write(JSON.stringify({...result,
  status:'AUTHENTICATED_DELTA_REVIEW_REQUIRED',provenanceAuthenticated:true,
  manualReviewRequired:true,autoApproveAllowed:false,autoMergeAllowed:false})+'\n');
}
if(require.main===module){
 main().catch(()=>{process.stdout.write(JSON.stringify(incomplete('PROTECTED_REVIEW_FAILED'))+'\n');
  process.exitCode=1;});
}
module.exports={evaluateRemote};
