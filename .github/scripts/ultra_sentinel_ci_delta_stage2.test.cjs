'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const stage1=require('./ultra_sentinel_ci_delta.cjs');
const {evaluateRemote}=require('./ultra_sentinel_ci_delta_stage2.cjs');
const HEAD='bb08782350201f97576a974a4684a611c21701c0';
const BASE='7aa5df3e3c704965862ea728fb4c390d2c15a7f0';
const REPO='cardenaspiero255-lang/gamehub-ultra',MAIN=BASE;
const mapping={
 '.github/workflows/android.yml':['android',stage1.buildExpectedAndroid],
 '.github/workflows/coverage.yml':['coverage',stage1.buildExpectedCoverage],
 '.github/scripts/check-ci-safety-contract.py':['safetyContract',stage1.buildExpectedContract],
 '.github/scripts/test-ci-safety-contract-regressions.py':['regressions',stage1.buildExpectedRegressions],
 'docs/CI_ACCELERATION_172_V2.md':['documentation',stage1.buildExpectedDocs]
};
const blob=body=>crypto.createHash('sha1').update('blob '+Buffer.byteLength(body)+'\0'+body).digest('hex');
// Do not read mutable main here: after PR172 merge, it contains optimized YAML.
// Historical original blobs live in a SHA-verified immutable fixture directory.
const historical=path.resolve(__dirname,'../sentinel-fixtures/pr172-ci-baseline');
const baseline=Object.fromEntries(Object.entries(mapping).filter(([k])=>!k.startsWith('docs/')).map(([k])=>[k,fs.readFileSync(path.join(historical,path.basename(k)),'utf8')]));
const candidate=Object.fromEntries(Object.entries(mapping).map(([k,v])=>[k,v[1](baseline[k])]));
const paths=Object.keys(mapping);
const androidNames=['quality-lint','quality-contract-shard','quality-contracts','release-bundle','quality',
 'device-validation','build','device-validation-release-apk','device-validation-ui','device-validation-baseline',
 'device-validation-macro-startup','device-validation-macro-library','device-validation-macro-settings',
 'android-test-shards',...Array.from({length:8},(_,i)=>'android-test-shard-'+i)];
const coverageNames=['coverage',...Array.from({length:8},(_,i)=>'coverage-shard-'+i)];
function fixture(){
 const state={prHead:HEAD,mainSha:MAIN,mainScript:'',lastRunBad:false,olderIdRerunStatus:null,
  fakeTree:false,dropFile:false,runAttempts:1,jobMissing:false,modifiedMain:false,mainMoves:false,mainMovesAfterJoint:false,mainReads:0,lateRunBad:false,lateRunBadAfterFirstCheck:false,runReads:0,badRunBase:false,lateAndroidAfterCoverage:false,missingJointAndroid:false};
 const match=(relative)=>relative.replace(/^\/+/,''), repoPath='cardenaspiero255-lang/gamehub-ultra';
 const changeList=paths.map(path=>({filename:path,status:path.startsWith('docs/')?'added':'modified',sha:blob(candidate[path])}));
 const run=(name,id,workflowId)=>({id,name,workflow_id:workflowId,head_sha:HEAD,event:'pull_request',
  status:'completed',conclusion:'success',run_attempt:state.runAttempts,
  run_started_at:'2026-10-12T21:00:00Z',
  head_repository:{full_name:repoPath},pull_requests:[{number:172,head:{sha:HEAD},base:{sha:BASE}}]});
 const runs=[run('Android build',400,363810233),run('Unit Test Coverage',401,367848611)];
 const olderRerun=()=>state.olderIdRerunStatus?{
  ...runs[0],id:399,run_attempt:2,run_started_at:'2026-10-12T22:00:00Z',
  status:state.olderIdRerunStatus==='success'?'completed':state.olderIdRerunStatus,
  conclusion:state.olderIdRerunStatus==='success'?'success':
   state.olderIdRerunStatus==='completed'?'failure':null
 }:null;
 async function get(relative){
  relative=match(relative);
  if(relative==='pulls/172')return {number:172,state:'open',changed_files:5,
   head:{sha:state.prHead,repo:{full_name:repoPath}},
   base:{sha:BASE,ref:'main',repo:{full_name:repoPath}}};
  if(relative==='git/ref/heads/main'){
   state.mainReads++;
   const moved=(state.mainMoves&&state.mainReads>1)||
    (state.mainMovesAfterJoint&&state.mainReads>2);
   return {object:{sha:moved?'e'.repeat(40):state.mainSha,type:'commit'}};
  }
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
   const rerun=olderRerun();
   if(rerun&&id===363810233)rr.push(rerun);
   state.runReads++;
   if(((state.lateRunBad&&state.runReads>=3)||
       (state.lateRunBadAfterFirstCheck&&state.runReads>=5))&&id===363810233)
    rr.push({...runs[0],id:901,conclusion:'failure'});
   if(state.lastRunBad&&id===363810233)rr.push({...runs[0],id:900,conclusion:'failure'});
   if(state.badRunBase)for(const item of rr)item.pull_requests[0].base.sha='f'.repeat(40);
   return {total_count:rr.length,workflow_runs:rr};
  }
  if(relative==='actions/runs?head_sha='+HEAD+'&event=pull_request&per_page=100'){
   const items=state.missingJointAndroid?runs.slice(1):[...runs];
   const rerun=olderRerun();if(rerun)items.push(rerun);
   if(state.lateAndroidAfterCoverage||state.lateRunBadAfterFirstCheck)
    items.push({...runs[0],id:910,status:'in_progress',conclusion:null});
   return {total_count:items.length,workflow_runs:items};
  }
  if(relative.startsWith('actions/runs/')&&relative.endsWith('/jobs?per_page=100&filter=latest')){
   const id=Number(relative.split('/')[2]);
   let names=(id===400||id===399)?androidNames:coverageNames;
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

test('trusted main capability AST accepts stage2 protected reviewer without opaque semantics',()=>{
 const acorn=require('acorn'),{findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
 const source=fs.readFileSync(path.join(__dirname,'ultra_sentinel_ci_delta_stage2.cjs'),'utf8');
 const ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:'script',
  allowHashBang:true,allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,
  locations:true,ranges:true});
 const sinks=findCapabilities(ast);
 assert.deepEqual(sinks,[]);
});

test('late Android failure after the first recheck still prevents certification',async()=>{
 const f=fixture();f.state.lateRunBadAfterFirstCheck=true;
 assert.equal((await evaluateRemote(f.get)).status,'INCOMPLETE');
});
test('protected CI workflow has no dispatch entrypoint selectable from PR branches',()=>{
 const workflow=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-ci-delta-protected.yml'),'utf8');
 assert.equal(workflow.includes('workflow_dispatch'),false);
 assert.match(workflow,/ref:.*github\.event\.repository\.default_branch/);
});

test('PR #172 current base must equal main and the recorded run base',async()=>{
 const older=fixture();older.state.mainSha='5a363b3643d210df71b8235c949dd6f8063c4f8a';
 assert.equal((await evaluateRemote(older.get)).status,'INCOMPLETE');
 const wrongRun=fixture();wrongRun.state.badRunBase=true;
 assert.equal((await evaluateRemote(wrongRun.get)).status,'INCOMPLETE');
});

test('stage2 trusted main runs after push touching only the shared API reader',()=>{
 const yaml=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-ci-delta-protected.yml'),'utf8');
 const push=yaml.split(/\n  push:\s*\n/)[1]?.split(/\npermissions:/)[0];
 assert.ok(push,'missing push trigger');
 assert.match(push,/ultra_sentinel_api_budget\*\.cjs/);
 assert.match(push,/ultra_sentinel_ci_delta_stage2\*\.cjs/);
});

test('atomic repository-wide CI snapshot rejects late Android rerun after Coverage read',async()=>{
 const f=fixture();f.state.lateAndroidAfterCoverage=true;
 assert.equal((await evaluateRemote(f.get)).status,'INCOMPLETE');
});
test('missing Android workflow in final repository-wide snapshot fails closed',async()=>{
 const f=fixture();f.state.missingJointAndroid=true;
 assert.equal((await evaluateRemote(f.get)).status,'INCOMPLETE');
});

test('main moving after joint CI snapshot cannot authorize old base',async()=>{
 const f=fixture();f.state.mainMovesAfterJoint=true;
 assert.equal((await evaluateRemote(f.get)).status,'INCOMPLETE');
});

test('P2: rerun of a lower-ID Android workflow must invalidate a newer-ID green run',async()=>{
 for(const status of ['in_progress','completed']){
  const f=fixture();
  f.state.olderIdRerunStatus=status;
  // GitHub keeps the original run ID on re-run, but starts attempt 2 later.
  // Green run #400 cannot hide the later pending/failed attempt on run #399.
  const result=await evaluateRemote(f.get);
  assert.equal(result.status,'INCOMPLETE','stale green accepted despite '+status+' rerun');
  assert.equal(result.autoMergeAllowed,false);
 }
});

test('P2: successful newer rerun with lower ID becomes the verified witness',async()=>{
 const f=fixture();
 f.state.olderIdRerunStatus='success';
 const out=await evaluateRemote(f.get);
 assert.equal(out.status,'SCOPED_CI_REVIEW_REQUIRED');
 assert.deepEqual(out.observedRunIds,[399,401]);
});


// PR172 must remain verifiable after merging new trusted main into its branch.
// The five immutable reviewed blobs, branch identity, base and CI runs are still pinned.
function updatedHeadFixture(nextHead){
 const f=fixture();
 f.state.prHead=nextHead;
 const get=async relative=>{
  if(relative.includes(HEAD))throw Error('STALE_PINNED_HEAD');
  const forwarded=relative.replaceAll(nextHead,HEAD);
  const response=await f.get(forwarded);
  return JSON.parse(JSON.stringify(response).replaceAll(HEAD,nextHead));
 };
 return {get,state:f.state};
}
test('Stage 2 accepts a rebased PR172 HEAD when exact reviewed blobs and CI evidence match',async()=>{
 const nextHead='d'.repeat(40),f=updatedHeadFixture(nextHead);
 const out=await evaluateRemote(f.get);
 assert.equal(out.status,'SCOPED_CI_REVIEW_REQUIRED');
 assert.equal(out.headSha,nextHead);
 assert.equal(out.provenanceAuthenticated,false);
 assert.equal(out.autoMergeAllowed,false);
 assert.equal(out.manualReviewRequired,true);
});
test('Stage 2 rejects altered Git tree even with a consistently rebased HEAD',async()=>{
 const f=updatedHeadFixture('d'.repeat(40));
 f.state.fakeTree=true;
 const out=await evaluateRemote(f.get);
 assert.equal(out.status,'INCOMPLETE');
 assert.equal(out.autoApproveAllowed,false);
});
test('Stage 2 fails closed if PR172 HEAD changes during rebased verification',async()=>{
 const f=updatedHeadFixture('d'.repeat(40));
 let observed=0;
 const get=async relative=>{
  if(relative==='pulls/172'&&++observed===2)f.state.prHead='e'.repeat(40);
  return f.get(relative);
 };
 const out=await evaluateRemote(get);
 assert.equal(out.status,'INCOMPLETE');
 assert.equal(out.autoMergeAllowed,false);
});


test('P2: historical PR172 fixtures exist and match immutable Git blob hashes',()=>{
 const folder=path.resolve(__dirname,'../sentinel-fixtures/pr172-ci-baseline');
 const expected={
  'android.yml':'418287ceb11221f1879ee81cbef40c4b008c4f04',
  'coverage.yml':'5ccf1c5de322a29c6e5b1d884ea659d310efa83c',
  'check-ci-safety-contract.py':'e297ca93aface84cda6a7769cd21da6e47e0e7af',
  'test-ci-safety-contract-regressions.py':'f3c8fa8c0f0c2a9361a33a12fe7386729404e5df'
 };
 for(const [filename,sha] of Object.entries(expected)){
  const bytes=fs.readFileSync(path.join(folder,filename));
  assert.equal(blob(bytes),sha,filename+' historical Git blob mismatch');
 }
});
