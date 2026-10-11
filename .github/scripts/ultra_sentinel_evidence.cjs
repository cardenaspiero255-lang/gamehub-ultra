'use strict';
/*
 * GH CI evidence: GitHub API, not caller-invented checks. Never execute PR data.
 * Token is confined to a fixed GET request to api.github.com.
 */
const https=require('node:https');
const crypto=require('node:crypto');
const REPO='cardenaspiero255-lang/gamehub-ultra';
const SHA=/^[a-f0-9]{40}$/i;
const NAMES=new Set(['Android build','Unit Test Coverage']);
const API='api.github.com';
const MAX=1024*1024;
function apiPath(sha){
 if(typeof sha!=='string'||!SHA.test(sha))throw Error('Full immutable SHA required');
 return '/repos/'+REPO+'/actions/runs?head_sha='+sha.toLowerCase()+'&per_page=100';
}
const TRUSTED_PATHS=Object.freeze({'Android build':'.github/workflows/android.yml',
 'Unit Test Coverage':'.github/workflows/coverage.yml'});
const TRUSTED_BLOBS=Object.freeze({
 'Android build':'418287ceb11221f1879ee81cbef40c4b008c4f04',
 'Unit Test Coverage':'5ccf1c5de322a29c6e5b1d884ea659d310efa83c'
});
function validateRun(run,sha,trustedWorkflows){
 if(!run||typeof run!=='object'||!SHA.test(sha||'')||
  run.head_sha?.toLowerCase()!==sha.toLowerCase()||
  run.status!=='completed'||run.conclusion!=='success'||
  !NAMES.has(run.name)||!trustedWorkflows||
  !Number.isSafeInteger(trustedWorkflows[run.name]?.id)||
  trustedWorkflows[run.name].id<1||
  trustedWorkflows[run.name].path!==TRUSTED_PATHS[run.name]||
  trustedWorkflows[run.name].blobSha!==TRUSTED_BLOBS[run.name]||
  run.workflow_id!==trustedWorkflows[run.name].id||
  run.path!==trustedWorkflows[run.name].path||
  !['push','workflow_dispatch'].includes(run.event)||
  run.head_branch!=='main'||
  run.repository?.full_name!==REPO||
  (run.head_repository?.full_name && run.head_repository.full_name!==REPO)||
  !Number.isSafeInteger(run.id)||run.id<=0||
  !Number.isSafeInteger(run.run_number)||run.run_number<1||
  !Number.isSafeInteger(run.run_attempt)||run.run_attempt<1||
  run.html_url!=='https://github.com/'+REPO+'/actions/runs/'+run.id)return null;
 return Object.freeze({sha:sha.toLowerCase(),workflow:run.name,runId:run.id,
  runAttempt:run.run_attempt,verification:'verified-github-api-run'});
}
function releaseSha(issue){
 const version=issue?.firstRelease?.version;
 if(typeof version!=='string'||version.length>180)return null;
 const value=version.trim();
 if(SHA.test(value))return value.toLowerCase();
 // Android CI names releases gamehub-ultra@<SHA>, not app@version+SHA.
 const current=value.match(/^gamehub-ultra@([a-f0-9]{40})$/i);
 if(current)return current[1].toLowerCase();
 const legacy=value.match(/^[a-zA-Z0-9._-]{1,100}@[a-zA-Z0-9._-]{1,30}\+([a-f0-9]{40})$/i);
 return legacy?legacy[1].toLowerCase():null;
}
function fingerprint(project,id){
 return crypto.createHash('sha256').update(project+'\0'+id).digest('hex');
}
function attachVerifiedReleases(rawIssues,validatedRuns){
 if(!Array.isArray(rawIssues)||!Array.isArray(validatedRuns))return [];
 const ok=new Set(validatedRuns.filter(r=>r?.verification==='verified-github-api-run'&&
  r.workflow==='Android build'&&SHA.test(r.sha||'')).map(r=>r.sha));
 const seen=new Set(),out=[];
 for(const issue of rawIssues.slice(0,100)){
  const sha=releaseSha(issue),project=issue?.project?.slug,id=String(issue?.id??'');
  if(!sha||!ok.has(sha)||!(/^[a-z0-9][a-z0-9-]{0,49}$/).test(project||'')||
   !(/^[0-9]{1,18}$/).test(id))continue;
  const fp=fingerprint(project,id);
  if(seen.has(fp))continue;
  seen.add(fp);
  out.push({fingerprint:fp,sha,verification:'verified-github-api-run'});
 }
 return out;
}
function assessRelease(sha,runs,consent){
 if(consent!==true)return {status:'CONSENT_REQUIRED',safeToRollback:false};
 if(!SHA.test(sha||'')||!Array.isArray(runs))return {status:'INCOMPLETE',safeToRollback:false};
 const names=new Set(runs.filter(r=>r?.sha===sha.toLowerCase()&&
  r.verification==='verified-github-api-run').map(r=>r.workflow));
 const ready=[...NAMES].every(x=>names.has(x));
 return {status:ready?'CI_VERIFIED':'INCOMPLETE',safeToRollback:false,
  verifiedWorkflows:[...names].sort()};
}

function readGithubJSON({uri,token,request=https.request}){
 return new Promise((resolve,reject)=>{
  let settled=false;
  const fail=reason=>{if(!settled){settled=true;reject(Error(reason))}};
  const req=request({
   protocol:'https:',hostname:API,port:443,path:uri,method:'GET',timeout:15000,
   headers:{Authorization:'Bearer '+token,Accept:'application/vnd.github+json',
    'X-GitHub-Api-Version':'2022-11-28','User-Agent':'Ultra-Sentinel-ReadOnly'}
  },res=>{
   if(res.statusCode!==200){res.resume();fail('GitHub CI API returned non-success');return}
   let size=0;const chunks=[];
   res.on('data',chunk=>{
    if(settled)return;
    size+=chunk.length;
    if(size>MAX){settled=true;reject(Error('CI API response exceeded limit'));req.destroy();return;}
    chunks.push(chunk);
   });
   res.on('end',()=>{
    if(settled)return;
    try{
     const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
     settled=true;resolve(body);
    }catch{fail('GitHub CI response invalid')}
   });
   res.on('error',()=>fail('GitHub CI response failed'));
  });
  req.on('timeout',()=>req.destroy(Error('timeout')));
  req.on('error',()=>fail('GitHub CI network request failed'));
  req.end();
 });
}
async function fetchVerifiedRuns({sha,token,request=https.request}){
 const uri=apiPath(sha);
 if(typeof token!=='string'||token.length<12||/[\r\n]/.test(token))
  throw Error('Read-only GitHub token is required');
 const trustedWorkflows={};
 for(const [name,expected] of Object.entries(TRUSTED_PATHS)){
  const short=expected.slice('.github/workflows/'.length);
  const api='/repos/'+REPO+'/actions/workflows/'+short;
  const response=await readGithubJSON({uri:api,token,request});
  if(!Number.isSafeInteger(response?.id)||response.id<1||response.path!==expected)
   throw Error('GitHub CI workflow identity unverified');
  const blob=await readGithubJSON({
   uri:'/repos/'+REPO+'/contents/'+expected+'?ref='+sha.toLowerCase(),
   token,request
  });
  if(blob?.type!=='file'||blob.sha!==TRUSTED_BLOBS[name]||blob.path!==expected)
   throw Error('GitHub workflow content differs from trusted reviewed baseline');
  trustedWorkflows[name]={id:response.id,path:response.path,blobSha:blob.sha};
 }
 const body=await readGithubJSON({uri,token,request});
 if(!Array.isArray(body?.workflow_runs)||!Number.isSafeInteger(body.total_count)||
  body.total_count!==body.workflow_runs.length||body.workflow_runs.length>100)
  throw Error('GitHub CI run pagination incomplete');
 // Validate run recency BEFORE filtering success. Otherwise a prior green
 // run could mask a newer red/in-progress execution of the exact same SHA.
 // If any matching run lacks trustworthy ordering metadata, fail closed for
 // that workflow rather than retaining a possibly stale green run.
 const newest=new Map(),unorderable=new Set();
 for(const candidate of body.workflow_runs){
  if(!NAMES.has(candidate?.name)||typeof candidate.head_sha!=='string'||
     candidate.head_sha.toLowerCase()!==sha.toLowerCase())continue;
  const name=candidate.name;
  if(!Number.isSafeInteger(candidate.run_number)||candidate.run_number<1||
     !Number.isSafeInteger(candidate.run_attempt)||candidate.run_attempt<1||
     !Number.isSafeInteger(candidate.id)||candidate.id<1){
   unorderable.add(name);continue;
  }
  const old=newest.get(name);
  if(!old||candidate.run_number>old.run_number||
     (candidate.run_number===old.run_number&&candidate.run_attempt>old.run_attempt)||
     (candidate.run_number===old.run_number&&candidate.run_attempt===old.run_attempt&&
      candidate.id>old.id))newest.set(name,candidate);
 }
 return [...NAMES].filter(name=>!unorderable.has(name))
  .map(name=>validateRun(newest.get(name),sha,trustedWorkflows)).filter(Boolean);
}

module.exports={apiPath,validateRun,releaseSha,attachVerifiedReleases,assessRelease,fetchVerifiedRuns,REPO,TRUSTED_BLOBS};
