'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const collector=require('./ultra_sentinel_github_collector.cjs');
const H='b'.repeat(40),B='a'.repeat(40),M='c'.repeat(40),REPO='cardenaspiero255-lang/gamehub-ultra',PR=181;
const metadata=()=>({number:PR,state:'open',head:{sha:H},base:{sha:B,repo:{full_name:REPO}},changed_files:1});
const runs=()=>collector.REQUIRED.map((name,i)=>({name,workflow_id:collector.TRUSTED_WORKFLOW_IDS[name],event:'pull_request',head_sha:H,
 pull_requests:[{number:PR}],id:100+i,run_attempt:1,status:'completed',conclusion:'success'}));
const audit=()=>({sha:H,repo:REPO,pr:PR,status:'BLOCKED',requiresHuman:true,autoMergeAllowed:false,
 findings:[{rule:'UNTRUSTED_CHECKOUT',severity:'BLOCKER',file:'.github/workflows/unsafe.yml',line:5,
  reason:'do not expose raw attacker content'}]});
const request=()=>({repo:REPO,pr:PR,headSha:H,token:'test-secret-token'});
function fixture(over={}){
 const state={meta:metadata(),final:null,compare:{base_commit:{sha:B},merge_base_commit:{sha:M}},
  files:[{filename:'.github/workflows/unsafe.yml',status:'modified'}],runs:runs(),audit:audit(),...over};
 const urls=[];
 const get=async relative=>{
  urls.push(relative);
  if(relative==='pulls/'+PR)return structuredClone(state.final||state.meta);
  if(relative.startsWith('compare/'))return structuredClone(state.compare);
  if(relative.startsWith('pulls/'+PR+'/files?'))return structuredClone(state.files);
  if(relative.startsWith('actions/runs?'))return {total_count:state.runs.length,workflow_runs:structuredClone(state.runs)};
  throw Error('BAD_TEST_ENDPOINT');
 };
 const analyzer=async()=>structuredClone(state.audit);
 const snapshots=async()=>({autoMergeAllowed:false,scope:'TEST_SCOPED_SNAPSHOT',
  base:{sha:B,complete:false,findings:[],reasons:['UNMODELED_SOURCE']},
  head:{sha:H,complete:false,findings:[],reasons:['UNMODELED_SOURCE']}});
 return {get,analyzer,snapshots,urls,state};
}
test('schema: authenticated real-PR metadata feeds shadow without ever approving merges',async()=>{
 const deps=fixture(),r=await collector.collect(request(),deps);
 assert.equal(r.schema,'ultra-sentinel-github-evidence/v1');
 assert.equal(r.headSha,H);assert.equal(r.baseSha,B);assert.equal(r.mergeBaseSha,M);
 assert.equal(r.status,'INCOMPLETE');assert.equal(r.ciStatus,'PASS');
 assert.equal(r.scannerStatus,'BLOCKED');assert.equal(r.reviewRequired,true);
 assert.equal(r.scannerFindingsTotal,1);assert.equal(r.hasAuthenticatedBaseline,false);
 assert.equal(r.autoMergeAllowed,false);assert.equal(r.autoCommitAllowed,false);
 assert.equal(r.checks.length,3);assert.ok(r.checks.every(c=>c.sha===H));
 assert.equal(JSON.stringify(r).includes('do not expose raw attacker content'),false);
 assert.equal(JSON.stringify(r).includes('test-secret-token'),false);
});
test('provenance: incorrect PR, wrong immutable HEAD, wrong repository or stale baseline fail closed',async()=>{
 for(const meta of [
  {...metadata(),number:180},
  {...metadata(),head:{sha:B}},
  {...metadata(),base:{sha:B,repo:{full_name:'attacker/repo'}}},
  {...metadata(),base:{sha:'wrong',repo:{full_name:REPO}}},
  {...metadata(),state:'closed'},
 ]){
  await assert.rejects(collector.collect(request(),fixture({meta})),/PR_PROVENANCE_INCOMPLETE/);
 }
});
test('provenance: merge-base evidence must match the immutable GitHub comparison',async()=>{
 for(const compare of [{base_commit:{sha:B},merge_base_commit:{sha:'no'}},
  {base_commit:{sha:M},merge_base_commit:{sha:M}},{}])
  await assert.rejects(collector.collect(request(),fixture({compare})),/MERGE_BASE_UNVERIFIED/);
});
test('provenance: a changed PR during collection is never certified',async()=>{
 const deps=fixture(),get=deps.get;let metadataReads=0;
 deps.get=async relative=>{
  if(relative==='pulls/'+PR&&++metadataReads===2)return {...metadata(),head:{sha:M}};
  return get(relative);
 };
 await assert.rejects(collector.collect(request(),deps),/PR_PROVENANCE_INCOMPLETE|PR_CHANGED/);
});
test('input: invalid SHA, injection, missing token and invalid PR are refused',async()=>{
 for(const change of [{headSha:'main'},{repo:'../etc/passwd'},{repo:'a/b/c'},
  {pr:0},{pr:'x'},{token:''},{headSha:H+'?ref=main'}])
  await assert.rejects(collector.collect({...request(),...change},fixture()),/INVALID_REQUEST/);
});
test('files: omitted, excessive or inconsistent file count fails closed',async()=>{
 for(const change of [{files:[]},{files:[{filename:'x'}],meta:{...metadata(),changed_files:2}},
  {files:[{filename:'x'.repeat(241)}]}])
  await assert.rejects(collector.collect(request(),fixture(change)),/FILES_INCOMPLETE/);
});
test('CI: missing required check is INCOMPLETE, never PASS',async()=>{
 const deps=fixture({runs:runs().slice(1)}),r=await collector.collect(request(),deps);
 assert.equal(r.ciStatus,'INCOMPLETE');assert.equal(r.status,'INCOMPLETE');
});
test('CI: latest failed run supersedes earlier successful evidence',async()=>{
 const old=runs(),late={...old[0],id:999,conclusion:'failure'};
 const r=await collector.collect(request(),fixture({runs:[...old,late]}));
 assert.equal(r.ciStatus,'BLOCKED');assert.equal(r.checks[0].conclusion,'failure');
});
test('CI: reruns with same ID use the latest attempt, never stale success',async()=>{
 const old=runs(),retry={...old[0],run_attempt:2,status:'in_progress',conclusion:null};
 const r=await collector.collect(request(),fixture({runs:[...old,retry]}));
 assert.equal(r.ciStatus,'INCOMPLETE');assert.equal(r.checks[0].status,'in_progress');
});
test('CI: incorrect SHA, event or PR association cannot certify checks',async()=>{
 for(const change of [{head_sha:B},{event:'push'},{pull_requests:[{number:180}]},
  {pull_requests:[]},{run_attempt:null}]){
  const sample=runs();sample[0]={...sample[0],...change};
  const r=await collector.collect(request(),fixture({runs:sample}));
  assert.equal(r.ciStatus,'INCOMPLETE');
 }
});
test('CI: skipped, neutral, pending and cancelled cannot be counted as successful',async()=>{
 for(const item of [{status:'completed',conclusion:'skipped'},{status:'completed',conclusion:'neutral'},
  {status:'in_progress',conclusion:null},{status:'completed',conclusion:'cancelled'}]){
  const sample=runs();sample[1]={...sample[1],...item};
  const r=await collector.collect(request(),fixture({runs:sample}));
  assert.notEqual(r.ciStatus,'PASS');
 }
});
test('CI: unbounded workflow history and malformed JSON cannot attest',()=>{
 assert.throws(()=>collector.checkEvidence({total_count:101,workflow_runs:runs()},H,PR),/WORKFLOW_EVIDENCE_INCOMPLETE/);
 assert.throws(()=>collector.checkEvidence({total_count:3,workflow_runs:[]},H,PR),/WORKFLOW_EVIDENCE_INCOMPLETE/);
});
test('trusted analyzer: missing SHA, spoofed output or false merge authority is rejected',async()=>{
 for(const change of [{sha:B},{repo:'attacker/repo'},{pr:180},
  {autoMergeAllowed:true},{requiresHuman:false},{status:'PASS'}]){
  const deps=fixture({audit:{...audit(),...change}});
  await assert.rejects(collector.collect(request(),deps),/ANALYZER_EVIDENCE_INCOMPLETE/);
 }
});
test('trusted analyzer: secrets in reasons never reach reports',async()=>{
 const r=await collector.collect(request(),fixture());
 assert.equal(JSON.stringify(r).includes('do not expose raw attacker content'),false);
 assert.deepEqual(r.scannerFindings[0],
  {rule:'UNTRUSTED_CHECKOUT',severity:'BLOCKER',path:'.github/workflows/unsafe.yml',line:5});
});
test('trusted analyzer: unknown severity and invalid path must fail closed',async()=>{
 for(const item of [{severity:'UNKNOWN'},{file:'../hidden'},{line:-1},{rule:'<script>'}]){
  const x=audit();x.findings=[{...x.findings[0],...item}];
  await assert.rejects(collector.collect(request(),fixture({audit:x})),/ANALYZER_EVIDENCE_INCOMPLETE/);
 }
});
test('GitHub client: read-only GET on pinned API host; authorization never put in URL',async()=>{
 let called;
 const get=collector.apiClient(REPO,'opaque-secret',async(url,args)=>{
  called={url,args};return {ok:true,json:async()=>({ok:true})};
 });
 assert.deepEqual(await get('pulls/181'),{ok:true});
 assert.match(called.url,/^https:\/\/api.github.com\/repos\/cardenaspiero255-lang\/gamehub-ultra\/pulls\/181$/);
 assert.equal(called.args.method,'GET');assert.equal(called.args.redirect,'error');
 assert.equal(called.args.headers.Authorization,'Bearer opaque-secret');
 assert.equal(called.url.includes('opaque-secret'),false);
 await assert.rejects(get('../secrets'),/INVALID_API_PATH/);
});
test('GitHub client: no redirect, fail on non-2xx instead of treating errors as empty',async()=>{
 const get=collector.apiClient(REPO,'secret',async()=>({ok:false,status:403}));
 await assert.rejects(get('pulls/181'),/GITHUB_API_UNAVAILABLE/);
});
test('workflow: unprivileged contract tests never inject API tokens into PR code',()=>{
 const workflow=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-github-evidence-shadow.yml'),'utf8');
 assert.match(workflow,/pull_request:/);
 assert.doesNotMatch(workflow,/pull_request_target:/);
 assert.match(workflow,/persist-credentials: false/);
 assert.match(workflow,/contents: read/);
 assert.doesNotMatch(workflow,/contents: write|write-all|pull-requests: write|actions: write/);
 assert.doesNotMatch(workflow,/GITHUB_TOKEN|github\.token|secrets\./);
 assert.match(workflow,/node --test .*ultra_sentinel_github_collector\.test\.cjs/);
 assert.doesNotMatch(workflow,/merge_pull_request|auto-merge|git push/i);
});



test('trusted-main evidence job includes the workflow directory required by contract tests',()=>{
 const workflow=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-github-trusted-evidence.yml'),'utf8');
 const sparse=workflow.match(/sparse-checkout:\s*\|\n([\s\S]*?)\n\s*persist-credentials:/)?.[1]||'';
 assert.match(sparse,/^\s*\.github\/workflows\s*$/m);
 assert.match(sparse,/^\s*\.github\/scripts\s*$/m);
 assert.match(workflow,/github\.event\.repository\.default_branch/);
 assert.match(workflow,/persist-credentials:\s*false/);
});

test('failed trusted collection produces a bounded sanitized fail-closed report',()=>{
 const input={repo:REPO,pr:PR,headSha:H};
 const report=collector.failureReport(new Error('GIT_TREE_INCOMPLETE'),input);
 assert.equal(report.schema,'ultra-sentinel-github-evidence-failure/v1');
 assert.equal(report.status,'INCOMPLETE');
 assert.equal(report.reason,'GIT_TREE_INCOMPLETE');
 assert.equal(report.mode,'shadow');
 assert.equal(report.headSha,H);
 assert.equal(report.reviewRequired,true);
 assert.equal(report.autoMergeAllowed,false);
 assert.equal(report.autoCommitAllowed,false);
 assert.equal(report.hasAuthenticatedBaseline,false);
 assert.ok(Buffer.byteLength(JSON.stringify(report))<2048);
 const leaked=collector.failureReport(new Error('secret ghp_NEVER_EXPOSE_TOKEN or JWT'),input);
 assert.equal(leaked.reason,'COLLECTION_FAILED');
 assert.equal(JSON.stringify(leaked).includes('NEVER_EXPOSE_TOKEN'),false);
 assert.equal(JSON.stringify(leaked).includes('JWT'),false);
 const malformed=collector.failureReport(new Error('INVALID_REQUEST'),{
   repo:'attacker\nfake',pr:-1,headSha:'bad'});
 assert.equal(Object.hasOwn(malformed,'repo'),false);
 assert.equal(Object.hasOwn(malformed,'headSha'),false);
 assert.equal(Object.hasOwn(malformed,'pr'),false);
});

test('CI: a confirmed failed gate stays BLOCKED even while another required gate is pending',async()=>{
 const set=runs().slice(0,2);
 set[0]={...set[0],conclusion:'failure'};
 const r=await collector.collect(request(),fixture({runs:set}));
 assert.equal(r.ciStatus,'BLOCKED');
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.autoMergeAllowed,false);
});
test('repository identity: dot-segment owner or repo and API-client ambiguity are rejected',async()=>{
 for(const repo of ['../gamehub-ultra','owner/..','./gamehub-ultra']){
  await assert.rejects(collector.collect({...request(),repo},fixture()),/INVALID_REQUEST/);
  assert.throws(()=>collector.apiClient(repo,'token',async()=>({ok:true})),/INVALID_REQUEST/);
 }
});


test('CI: a forged workflow name with the wrong GitHub workflow ID cannot claim success',async()=>{
 const sample=runs();sample[0].workflow_id=123456;
 const r=await collector.collect(request(),fixture({runs:sample}));
 assert.equal(r.ciStatus,'INCOMPLETE');
 assert.equal(r.autoMergeAllowed,false);
});

test('integration: complete authenticated base and HEAD snapshots produce scoped comparison',async()=>{
 const deps=fixture();
 deps.snapshots=async()=>({autoMergeAllowed:false,scope:'TEST_SCOPED_SNAPSHOT',
  base:{sha:B,complete:true,reasons:[],findings:[
   {path:'app/src/main/java/x/A.kt',line:3,rule:'UNSCOPED_COROUTINE',severity:'HIGH'}]},
  head:{sha:H,complete:true,reasons:[],findings:[
   {path:'app/src/main/java/x/A.kt',line:3,rule:'UNSCOPED_COROUTINE',severity:'HIGH'},
   {path:'app/src/main/java/x/B.kt',line:4,rule:'BLOCKING_ANDROID_CALL',severity:'HIGH'}]}});
 const report=await collector.collect(request(),deps);
 assert.equal(report.hasAuthenticatedBaseline,true);
 assert.equal(report.hasAuthenticatedFullRepositoryScan,false);
 assert.equal(report.scopedEvaluator.status,'REVIEW_REQUIRED');
 assert.equal(report.scopedEvaluator.findingsTotal,2);
 assert.equal(report.scopedEvaluator.findings.find(f=>f.rule==='BLOCKING_ANDROID_CALL').classification,'NEW');
 assert.equal(report.status,'INCOMPLETE');assert.equal(report.autoMergeAllowed,false);
});
test('integration: snapshot commit identity mismatch is rejected, never scored',async()=>{
 const deps=fixture();
 deps.snapshots=async()=>({autoMergeAllowed:false,
  base:{sha:H,complete:true,reasons:[],findings:[]},
  head:{sha:H,complete:true,reasons:[],findings:[]}});
 await assert.rejects(collector.collect(request(),deps),/SNAPSHOT_ATTESTATION_MISMATCH/);
});
test('integration: untrusted scanner with alleged auto-merge authority is rejected',async()=>{
 const deps=fixture();
 deps.snapshots=async()=>({autoMergeAllowed:true,
  base:{sha:B,complete:false,reasons:[],findings:[]},
  head:{sha:H,complete:false,reasons:[],findings:[]}});
 await assert.rejects(collector.collect(request(),deps),/SNAPSHOT_ATTESTATION_MISMATCH/);
});
test('integration: partial source coverage stays INCOMPLETE without hiding counts',async()=>{
 const report=await collector.collect(request(),fixture());
 assert.equal(report.hasAuthenticatedBaseline,false);
 assert.equal(report.scopedEvaluator.status,'INCOMPLETE');
 assert.equal(report.baseSnapshot.complete,false);
 assert.equal(report.headSnapshot.complete,false);
 assert.equal(report.status,'INCOMPLETE');
});
test('integration: a PR changed after full snapshot collection is refused',async()=>{
 const deps=fixture();let reads=0,original=deps.get;
 deps.get=async url=>{
  if(url==='pulls/'+PR&&++reads===2)return {...metadata(),head:{sha:B}};
  return original(url);
 };
 await assert.rejects(collector.collect(request(),deps),/PR_PROVENANCE_INCOMPLETE/);
});
test('integration: invalid snapshot finding must not become a pass verdict',async()=>{
 const deps=fixture();
 deps.snapshots=async()=>({autoMergeAllowed:false,
  base:{sha:B,complete:true,reasons:[],findings:[]},
  head:{sha:H,complete:true,reasons:[],findings:[
   {rule:'INVALID<>',path:'app/src/main/x.kt',line:1,severity:'HIGH'}]}});
 await assert.rejects(collector.collect(request(),deps),/SHADOW_EVIDENCE_INCOMPLETE/);
});

test('incomplete scans still preserve a confirmed critical finding review veto',async()=>{
 const deps=fixture({audit:{...audit(),status:'ADVISORY',findings:[]}});
 deps.snapshots=async()=>({autoMergeAllowed:false,
  base:{sha:B,complete:false,reasons:['SCAN_FILE_TOO_LARGE'],findings:[]},
  head:{sha:H,complete:false,reasons:['SCAN_FILE_TOO_LARGE'],
   findings:[{path:'app/src/main/java/x/A.kt',line:5,
    rule:'UNSCOPED_COROUTINE',severity:'HIGH'}]}});
 const r=await collector.collect(request(),deps);
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.scopedEvaluator.status,'INCOMPLETE');
 assert.equal(r.reviewRequired,true);
 assert.equal(r.autoMergeAllowed,false);
});
test('confirmed CI failure cannot be masked by an incomplete snapshot',async()=>{
 const attempted=runs();attempted[0]={...attempted[0],conclusion:'failure'};
 const deps=fixture({runs:attempted,audit:{...audit(),status:'ADVISORY',findings:[]}});
 const r=await collector.collect(request(),deps);
 assert.equal(r.ciStatus,'BLOCKED');
 assert.equal(r.reviewRequired,true);
 assert.equal(r.status,'INCOMPLETE');
});
