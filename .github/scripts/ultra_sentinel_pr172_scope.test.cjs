'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {attestScopedPr172,permitScopedOpaqueYaml}=require('./ultra_sentinel_pr172_scope.cjs');
const REPO='cardenaspiero255-lang/gamehub-ultra';
const HEAD='d3f4c245ca32f7033e5e20095ecd30fab7666a63';
const MAIN='5d81c62663170b2c353152aea79c640d8b724cab';
const protectedFiles=[
 ['.github/workflows/android.yml','0f78d28d5e8974b988490bd9facdf63cb6be6485','modified'],
 ['.github/workflows/coverage.yml','e1042881b00273aec31c2a64131ff0df7042e4ef','modified'],
 ['.github/scripts/check-ci-safety-contract.py','58357888ddd96d2980061293100ebc0795fdb800','modified'],
 ['.github/scripts/test-ci-safety-contract-regressions.py','04ab3e26f8eb108323827df2a2e99c72c3216af8','modified'],
 ['docs/CI_ACCELERATION_172_V2.md','786e327cdecc2b8985df1b08e1bb081d17d8e3b4','added']
].map(([filename,sha,status])=>({filename,sha,status}));
function fixture(){
 const state={main:MAIN,head:HEAD,runStatus:'completed',runConclusion:'success',
  badLink:false,badLinkHead:false,missingLink:false,badRunSha:false,unrelatedRun:false,
  badJob:false,mainMoves:false,prMoves:false,rerunPending:false,missingRuns:false,
  lateRun:false,unlinkedNewer:false,listReads:0,listHeadSha:null};
 const pr={number:172,state:'open',changed_files:5,author_association:'OWNER',
  user:{login:'cardenaspiero255-lang',id:322222186,type:'User'},
  head:{sha:HEAD,repo:{full_name:REPO}},
  base:{sha:MAIN,ref:'main',repo:{full_name:REPO}}};
 const run=(id,attempt,started,conclusion='success',status='completed')=>({
  id,workflow_id:382593687,name:'Ultra Sentinel PR172 Protected Delta Review',
  event:'pull_request_target',status,conclusion,head_sha:HEAD,run_attempt:attempt,
  run_started_at:started,head_repository:{full_name:REPO},
  pull_requests:[{number:172,base:{sha:MAIN},head:{sha:HEAD}}]
 });
 const original=run(900,1,'2026-10-13T02:00:00Z');
 let prReads=0,mainReads=0;
 const github={rest:{
  git:{getRef:async()=>({data:{object:{sha:state.mainMoves&&++mainReads>1?'e'.repeat(40):state.main}}})},
  // The caller-supplied PR was the first snapshot: this mock is the final reread.
  pulls:{get:async()=>{prReads++;return{data:{...pr,head:{...pr.head,sha:state.prMoves&&prReads>=1?'f'.repeat(40):state.head}}};}},
  actions:{
   getWorkflow:async()=>({data:{id:382593687,name:'Ultra Sentinel PR172 Protected Delta Review',
    path:'.github/workflows/ultra-sentinel-ci-delta-protected.yml',state:'active'}}),
   listWorkflowRuns:async args=>{state.listHeadSha=args.head_sha;state.listReads++;
    let runs=state.missingRuns?[]:[original];
    if(state.unrelatedRun)runs.push({...run(901,1,'2026-10-13T03:30:00Z'),
      pull_requests:[{number:191,base:{sha:MAIN},head:{sha:'a'.repeat(40)}}]});
    if(state.rerunPending)runs.push(run(899,2,'2026-10-13T03:00:00Z',null,'in_progress'));
    if(state.lateRun&&state.listReads>1)
      runs.push(run(902,1,'2026-10-13T04:00:00Z',null,'in_progress'));
    if(state.unlinkedNewer)runs.push({...run(903,1,'2026-10-13T04:01:00Z',null,'in_progress'),pull_requests:[]});
    runs=runs.map(v=>({...v,
     status:state.runStatus==='completed'?v.status:state.runStatus,
     head_sha:state.badRunSha?MAIN:v.head_sha,
     conclusion:state.runConclusion==='success'?v.conclusion:state.runConclusion,
     pull_requests:state.missingLink?[]:state.badLink?
       [{number:172,head:{sha:HEAD},base:{sha:'e'.repeat(40)}}]:state.badLinkHead?
       [{number:172,head:{sha:'f'.repeat(40)},base:{sha:MAIN}}]:v.pull_requests
    }));
    return{data:{total_count:runs.length,workflow_runs:runs}};},
   listJobsForWorkflowRun:async()=>({data:{total_count:2,jobs:[
    {id:11,run_id:900,name:'Trusted main PR172 SHA/tree/31 jobs (manual review required)',
     status:'completed',conclusion:state.badJob?'failure':'success'},
    {id:12,run_id:900,name:'Unprivileged protected-delta regression tests',
     status:'completed',conclusion:'skipped'}
   ]}})
  }
 }};
 return {github,pr,files:protectedFiles.map(x=>({...x})),state};
}
const run=async f=>attestScopedPr172({github:f.github,owner:'cardenaspiero255-lang',
 repo:'gamehub-ultra',pr:f.pr,files:f.files});
test('exact GitHub-origin scoped stage2 evidence stays manual and non-approving',async()=>{
 const f=fixture();
 const x=await run(f);
 assert.equal(f.state.listHeadSha,HEAD,
  'Actions REST workflow_runs.head_sha is the PR head, unlike the runner GITHUB_SHA base');
 assert.equal(x.status,'SCOPED_AUTHENTICATED_MANUAL_REVIEW');
 assert.equal(x.provenanceAuthenticated,true);
 assert.equal(x.manualReviewRequired,true);
 assert.equal(x.autoApproveAllowed,false);
 assert.equal(x.autoMergeAllowed,false);
 assert.equal(x.protectedRunAttempt,1);
});
test('unrelated PR and foreign author never inherit PR172 attestation',async()=>{
 const a=fixture();a.pr.number=173;assert.notEqual((await run(a)).status,'SCOPED_AUTHENTICATED_MANUAL_REVIEW');
 const b=fixture();b.pr.user.login='attacker';assert.notEqual((await run(b)).status,'SCOPED_AUTHENTICATED_MANUAL_REVIEW');
});
test('missing, changed, reordered IDs and duplicate files fail closed',async()=>{
 for(const change of [x=>x.files.pop(),x=>x.files[0].sha='f'.repeat(40),
  x=>x.files.push({...x.files[0]}),x=>x.files[1].status='renamed']){
  const f=fixture();change(f);
  assert.notEqual((await run(f)).status,'SCOPED_AUTHENTICATED_MANUAL_REVIEW');
 }
});
test('stale failed, newer pending rerun and missing protected run fail closed',async()=>{
 for(const change of [x=>x.state.runConclusion='failure',
  x=>x.state.rerunPending=true,x=>x.state.missingRuns=true]){
  const f=fixture();change(f);
  assert.notEqual((await run(f)).status,'SCOPED_AUTHENTICATED_MANUAL_REVIEW');
 }
});
test('unrelated PR target runs with the same head commit cannot impersonate PR172',async()=>{
 const f=fixture();f.state.unrelatedRun=true;
 const x=await run(f);
 assert.equal(x.status,'SCOPED_AUTHENTICATED_MANUAL_REVIEW');
 assert.equal(x.protectedRunId,900);
});
test('forged workflow target base, missing PR linkage and job failures fail closed',async()=>{
 for(const change of [x=>x.state.badLink=true,x=>x.state.badLinkHead=true,
  x=>x.state.missingLink=true,x=>x.state.badRunSha=true,x=>x.state.badJob=true]){
  const f=fixture();change(f);
  assert.notEqual((await run(f)).status,'SCOPED_AUTHENTICATED_MANUAL_REVIEW');
 }
});
test('mixed linked-green and newer unlinked runs cannot certify a stale success',async()=>{
 const f=fixture();f.state.unlinkedNewer=true;
 assert.equal((await run(f)).status,'INCOMPLETE');
});
test('a new protected run during terminal verification blocks stale success',async()=>{
 const f=fixture();f.state.lateRun=true;
 const x=await run(f);
 assert.equal(x.status,'INCOMPLETE');
 assert.equal(f.state.listReads,2);
});
test('HEAD or main moving during verification fails closed',async()=>{
 for(const change of [x=>x.state.prMoves=true,x=>x.state.mainMoves=true]){
  const f=fixture();change(f);
  assert.notEqual((await run(f)).status,'SCOPED_AUTHENTICATED_MANUAL_REVIEW');
 }
});
const opaque={status:'INCOMPLETE',findings:[],coverage:{partial:true,requested:2,scanned:2,
 reasons:['WORKFLOW_SEMANTIC_INCOMPLETE','SHELL_FUNCTION','SENSITIVE_ACTION_ENV','OPAQUE_ARITHMETIC']}};
test('only bounded known opaque YAML families can use scoped verified manual review',()=>{
 assert.equal(permitScopedOpaqueYaml(opaque),true);
 for(const wrong of [
  {...opaque,coverage:{...opaque.coverage,reasons:['UNMODELED_WORKFLOW_FEATURE']}},
  {...opaque,coverage:{...opaque.coverage,scanned:1}},
  {...opaque,findings:[{severity:'HIGH'}]},
  {...opaque,coverage:{...opaque.coverage,reasons:['SHELL_ALIAS']}}
 ])assert.equal(permitScopedOpaqueYaml(wrong),false);
});
test('reviewer workflows consume trusted scoped evidence without weakening high blockers',()=>{
 for(const file of ['ultra-sentinel-auto-review.yml','ultra-sentinel-independent-review.yml']){
  const text=fs.readFileSync(path.resolve(__dirname,'../workflows',file),'utf8');
  assert.match(text,/ultra_sentinel_pr172_scope\.cjs/);
  assert.match(text,/SCOPED_AUTHENTICATED_MANUAL_REVIEW/);
  assert.match(text,/HIGH.*BLOCKER/);
 }
});
test('both reviewers reattest the same run immediately before making an exception',()=>{
 for(const file of ['ultra-sentinel-auto-review.yml','ultra-sentinel-independent-review.yml']){
  const text=fs.readFileSync(path.resolve(__dirname,'../workflows',file),'utf8');
  const terminal=text.lastIndexOf('await attestScopedPr172({github,owner,repo,pr,files})');
  const summary=text.lastIndexOf('await core.summary.addHeading');
  const decision=text.lastIndexOf('core.setFailed(');
  assert.ok(terminal>summary,'must reattest after async summary work: '+file);
  assert.ok(decision>terminal,'decision must follow terminal attestation: '+file);
  assert.match(text,/protectedRunAttempt/);
  const upload=text.lastIndexOf('uses: actions/upload-artifact@');
  const post=text.lastIndexOf('- name: Reauthenticate scoped evidence after artifact upload');
  assert.ok(upload>=0&&post>upload,'post-upload check must be the last job step: '+file);
  const afterUpload=text.slice(post);
  assert.match(afterUpload,/const current=await attestScopedPr172\(\{github,owner,repo,pr,files\}\)/);
  assert.match(afterUpload,/if\(pr\.head\.sha!==report\.sha\)/);
  assert.match(afterUpload,/if\(current\.status!=='SCOPED_AUTHENTICATED_MANUAL_REVIEW'/);
  assert.match(afterUpload,/if\(current\[field\]!==stored\[field\]\)/);
  assert.match(afterUpload,/core\.setFailed\('Post-upload attestation changed/);
 }
});
test('trusted-main reviewers tolerate absent not-yet-merged scope module and fail closed',()=>{
 for(const file of ['ultra-sentinel-auto-review.yml','ultra-sentinel-independent-review.yml']){
  const text=fs.readFileSync(path.resolve(__dirname,'../workflows',file),'utf8');
  assert.match(text,/fs\.existsSync\(scopePath\)/);
  assert.match(text,/permitScopedOpaqueYaml:\(\)=>false/);
  assert.match(text,/status:'INCOMPLETE'/);
  assert.match(text,/require\(scopePath\)/);
  assert.doesNotMatch(text,/require\(path\.resolve\("\.github\/scripts\/ultra_sentinel_pr172_scope\.cjs"\)\)/);
 }
});
test('scoped authenticator never uses GitHub writes or dynamically executes PR code',()=>{
 const source=fs.readFileSync(path.resolve(__dirname,'ultra_sentinel_pr172_scope.cjs'),'utf8');
 assert.doesNotMatch(source,/createOrUpdate|mergePull|createReview|workflow_dispatch|child_process|eval\s*\(/i);
});
