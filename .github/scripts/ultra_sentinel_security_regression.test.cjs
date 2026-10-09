'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {attestCi}=require('./ultra_sentinel_frontier_integrity.cjs');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const SHA='a'.repeat(40),REPO='cardenaspiero255-lang/gamehub-ultra',WF='.github/workflows/worry.yml';
const trusted={
 'Android build':{id:101,path:'.github/workflows/android.yml'},
 'Unit Test Coverage':{id:202,path:'.github/workflows/coverage.yml'}
};
const run=(name,more={})=>({
 name,id:name==='Android build'?41:42,workflow_id:name==='Android build'?101:202,
 path:name==='Android build'?trusted['Android build'].path:trusted['Unit Test Coverage'].path,
 run_number:7,run_attempt:1,head_sha:SHA,
 event:'pull_request',status:'completed',conclusion:'success',
 repository:{full_name:REPO},head_repository:{full_name:REPO},...more
});
const both=[run('Android build'),run('Unit Test Coverage')];
const ci=(opts={})=>attestCi({sha:SHA,repo:REPO,runs:both,trustedWorkflows:trusted,
 changedFiles:[],changedFilesComplete:true,...opts});
const audit=s=>reviewWorkflows({sha:SHA,expected:[WF],sources:{[WF]:s}});
const rules=r=>r.findings.map(f=>f.rule);
test('CI verification requires trusted workflow identities, not just names',()=>{
 assert.notEqual(attestCi({sha:SHA,repo:REPO,runs:both}).status,'PASS');
 assert.equal(ci().status,'PASS');
});
test('CI rejects forged success from a different workflow ID and path',()=>{
 assert.notEqual(ci({runs:[run('Android build',{workflow_id:404}),both[1]]}).status,'PASS');
 assert.notEqual(ci({runs:[run('Android build',{path:'.github/workflows/forged.yml'}),both[1]]}).status,'PASS');
});
test('CI fails closed when PR changes trusted Android/Coverage workflow definitions',()=>{
 for(const name of ['.github/workflows/android.yml','.github/workflows/coverage.yml']){
  const r=ci({changedFiles:[name]});
  assert.notEqual(r.status,'PASS',name);
 }
});
test('CI fails closed if complete changed-file provenance is unavailable',()=>{
 assert.notEqual(ci({changedFilesComplete:false}).status,'PASS');
 assert.notEqual(ci({changedFiles:null}).status,'PASS');
});
test('CI ignores forged green event status while preserving genuine failed runs',()=>{
 assert.notEqual(ci({runs:[run('Android build',{event:'repository_dispatch'}),both[1]]}).status,'PASS');
 assert.equal(ci({runs:[run('Android build',{conclusion:'failure'}),both[1]]}).status,'FAILED');
});
test('privileged trigger inline scalar detects untrusted PR checkout',()=>{
 const yaml="on: pull_request_target\njobs:\n  dangerous:\n    steps:\n      - uses: actions/checkout@"+'f'.repeat(40)+
 "\n        with:\n          ref: \${{ github.event.pull_request.head.sha }}\n";
 assert.ok(rules(audit(yaml)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('privileged trigger inline list detects untrusted PR checkout',()=>{
 const yaml="on: [push, pull_request_target]\njobs:\n  dangerous:\n    steps:\n      - uses: actions/checkout@"+'f'.repeat(40)+
 "\n        with:\n          ref: \${{ github.event.pull_request.head.sha }}\n";
 assert.ok(rules(audit(yaml)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('privileged workflow_run inline trigger detects untrusted checkout',()=>{
 const yaml="on: [workflow_run]\njobs:\n  dangerous:\n    steps:\n      - uses: actions/checkout@"+'f'.repeat(40)+
 "\n        with:\n          ref: \${{ github.event.pull_request.head.sha }}\n";
 assert.ok(rules(audit(yaml)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('write-all is flagged as privileged',()=>{
 assert.ok(rules(audit('permissions: write-all\n')).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('checks/statuses/security-events/attestations writes are flagged',()=>{
 for(const key of ['checks','statuses','security-events','attestations','environments']){
  assert.ok(rules(audit('permissions:\n  '+key+': write\n')).includes('PRIVILEGED_WRITE_TOKEN'),key);
 }
});
test('inline permission mapping write is flagged',()=>{
 assert.ok(rules(audit('permissions: {contents: read, checks: write}\n')).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('quoted SHA action must not be misclassified as unpinned',()=>{
 for(const q of ['"',"'"]){
  const src='on: pull_request\njobs:\n  test:\n    steps:\n      - uses: '+q+'actions/checkout@'+'f'.repeat(40)+q+'\n';
  assert.ok(!rules(audit(src)).includes('UNPINNED_ACTION'),q);
 }
});
test('quoted non-SHA action stays unpinned',()=>{
 assert.ok(rules(audit('jobs:\n  test:\n    steps:\n      - uses: "actions/checkout@v6"\n')).includes('UNPINNED_ACTION'));
});
test('both review workflows lookup trusted main identities and changed files for SHA',()=>{
 for(const file of ['ultra-sentinel-auto-review.yml','ultra-sentinel-sss-post-ci.yml']){
  const txt=fs.readFileSync(path.resolve(__dirname,'../workflows',file),'utf8');
  assert.match(txt,/getWorkflow/);
  assert.match(txt,/changedFilesComplete/);
  assert.match(txt,/trustedWorkflows/);
  assert.match(txt,/attestCi/);
  assert.match(txt,/actions: read/);
 }
});
test('automatic and post-CI reviewers fail rather than report green on untrusted CI definitions',()=>{
 for(const file of ['ultra-sentinel-auto-review.yml','ultra-sentinel-sss-post-ci.yml']){
  const code=fs.readFileSync(path.resolve(__dirname,'../workflows',file),'utf8');
  assert.match(code,/if\s*\(\s*ci\.status===['"]UNTRUSTED['"]\s*\)\s*core\.setFailed/);
 }
});
