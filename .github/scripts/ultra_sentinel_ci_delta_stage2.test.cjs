'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const stage1=require('./ultra_sentinel_ci_delta.cjs');
const {evaluateRemote,HEAD,BASE}=require('./ultra_sentinel_ci_delta_stage2.cjs');
const REPO='cardenaspiero255-lang/gamehub-ultra',MAIN='5a363b3643d210df71b8235c949dd6f8063c4f8a';
const mapping={
 '.github/workflows/android.yml':['android',stage1.buildExpectedAndroid],
 '.github/workflows/coverage.yml':['coverage',stage1.buildExpectedCoverage],
 '.github/scripts/check-ci-safety-contract.py':['safetyContract',stage1.buildExpectedContract],
 '.github/scripts/test-ci-safety-contract-regressions.py':['regressions',stage1.buildExpectedRegressions],
 'docs/CI_ACCELERATION_172_V2.md':['documentation',stage1.buildExpectedDocs]
};
const blob=body=>crypto.createHash('sha1').update('blob '+Buffer.byteLength(body)+'\0'+body).digest('hex');
const baseline=Object.fromEntries(Object.entries(mapping).filter(([k])=>!k.startsWith('docs/')).map(([k])=>[k,fs.readFileSync(path.join(__dirname,k.split('/').at(-1).endsWith('.yml')?'../workflows/'+k.split('/').at(-1):k.split('/').at(-1)),'utf8')]));
const candidate=Object.fromEntries(Object.entries(mapping).map(([k,v])=>[k,v[1](baseline[k])]));
const paths=Object.keys(mapping);
const androidNames=['quality-lint','quality-contract-shard','quality-contracts','release-bundle','quality',
 'device-validation','build','device-validation-release-apk','device-validation-ui','device-validation-baseline',
 'device-validation-macro-startup','device-validation-macro-library','device-validation-macro-settings',
 'android-test-shards',...Array.from({length:8},(_,i)=>'android-test-shard-'+i)];
const coverageNames=['coverage',...Array.from({length:8},(_,i)=>'coverage-shard-'+i)];
function fixture(){
 const state={prHead:HEAD,mainSha:MAIN,mainScript:'',lastRunBad:false,
  fakeTree:false,dropFile:false,runAttempts:1,jobMissing:false,modifiedMain:false,mainMoves:false,mainReads:0,lateRunBad:false,runReads:0};
 const match=(relative)=>relative.replace(/^\/+/,''), repoPath='cardenaspiero255-lang/gamehub-ultra';
 const changeList=paths.map(path=>({filename:path,status:path.startsWith('docs/')?'added':'modified',sha:blob(candidate[path])}));
 const run=(name,id,workflowId)=>({id,name,workflow_id:workflowId,head_sha:HEAD,event:'pull_request',
  status:'completed',conclusion:'success',run_attempt:state.runAttempts,
  head_repository:{full_name:repoPath},pull_requests:[{number:172}]});
 const runs=[run('Android build',400,363810233),run('Unit Test Coverage',401,367848611)];
 async function get(relative){
  relative=match(relative);
  if(relative==='pulls/172')return {number:172,state:'open',changed_files:5,
   head:{sha:state.prHead,repo:{full_name:repoPath}},
   base:{sha:BASE,ref:'main',repo:{full_name:repoPath}}};
  if(relative==='git/ref/heads/main')return {object:{sha:state.mainMoves&&++state.mainReads>1?'e'.repeat(40):state.mainSha,type:'commit'}};
  if(relative==='git/commits/'+HEAD)return {sha:HEAD,tree:{sha:'a'.repeat(40)}};
  if(relative==='git/trees/'+('a'.repeat(40))+'?recursive=1')return {sha:'a'.repeat(40),truncated:false,
   tree:changeList.map(x=>({path:x.filename,mode:'100644',type:'blob',
    sha:state.fakeTree&&x.filename===paths[0]?'f'.repeat(40):x.sha}))};
  if(relative==='pulls/172/files?per_page=100&page=1')return state.dropFile?changeList.slice(1):changeList;
  if(relative==='pulls/172/files?per_page=100&page=2')return [];
  if(relative.startsWith('contents/')){
   const [encoded,ref]=relative.slice(9).split('?ref='),file=decodeURIComponent(encoded);
   if(!paths.includes(file))throw Error('unrecognized');
   const body=ref===HEAD?candidate[file]:baseline[file];
   if(typeof body!=='string')throw Error('not found');
   const actual=state.modifiedMain&&ref===MAIN&&file===paths[0]?body+'# tamper':body;
   return {type:'file',encoding:'base64',sha:blob(actual),size:Buffer.byteLength(actual),
    content:Buffer.from(actual).toString('base64').match(/.{1,64}/g).join('\n')+'\n'};
  }
  if(relative==='actions/workflows/android.yml')return {id:363810233,name:'Android build'};
  if(relative==='actions/workflows/coverage.yml')return {id:367848611,name:'Unit Test Coverage'};
  if(relative.startsWith('actions/workflows/')&&relative.includes('/runs?')){
   const id=Number(relative.split('/')[2]),rr=runs.filter(x=>x.workflow_id===id);
   state.runReads++;
   if(state.lateRunBad&&state.runReads>=3&&id===363810233)
    rr.push({...runs[0],id:901,conclusion:'failure'});
   if(state.lastRunBad&&id===363810233)rr.push({...runs[0],id:900,conclusion:'failure'});
   return {total_count:rr.length,workflow_runs:rr};
  }
  if(relative.startsWith('actions/runs/')&&relative.endsWith('/jobs?per_page=100&filter=latest')){
   const id=Number(relative.split('/')[2]);
   let names=id===400?androidNames:coverageNames;
   if(state.jobMissing&&id===400)names=names.slice(1);
   const jobs=names.map((name,i)=>({id:id*100+i,name,run_id:id,status:'completed',conclusion:'success'}));
   return {total_count:jobs.length,jobs};
  }
  throw Error('unrecognized API path: '+relative);
 }
 return {get,state};
}
test('trusted runner evidence is scoped and non-approving even with 31 jobs',async()=>{
 const out=await evaluateRemote(fixture().get);
 assert.equal(out.status,'SCOPED_CI_REVIEW_REQUIRED');
 assert.equal(out.provenanceAuthenticated,false);
 assert.equal(out.observedJobs,31);
 assert.equal(out.autoApproveAllowed,false);
 assert.equal(out.autoMergeAllowed,false);
});
test('malicious HEAD, mutated tree or missing changed file fail closed',async()=>{
 for(const adjust of [s=>s.prHead='f'.repeat(40),s=>s.fakeTree=true,s=>s.dropFile=true]){
  const f=fixture();adjust(f.state);
  assert.equal((await evaluateRemote(f.get)).status,'INCOMPLETE');
 }
});
test('older green cannot override newer failing workflow run',async()=>{
 const f=fixture();f.state.lastRunBad=true;
 assert.equal((await evaluateRemote(f.get)).status,'INCOMPLETE');
});
test('incomplete jobs, changed current main and inconsistent refs fail closed',async()=>{
 for(const adjust of [s=>s.jobMissing=true,s=>s.mainMoves=true,s=>s.modifiedMain=true]){
  const f=fixture();adjust(f.state);
  assert.equal((await evaluateRemote(f.get)).status,'INCOMPLETE');
 }
});

test('new CI failure arriving during verification invalidates previous green',async()=>{
 const f=fixture();f.state.lateRunBad=true;
 assert.equal((await evaluateRemote(f.get)).status,'INCOMPLETE');
});
