'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const yaml=()=>fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-sss-post-ci.yml'),'utf8');
test('reassesses CI when either Android Build or Unit Test Coverage finishes',()=>{
 const wf=yaml();
 assert.match(wf,/workflow_run:/);
 assert.match(wf,/workflows: \['Android build', 'Unit Test Coverage'\]/);
 assert.match(wf,/types: \[completed\]/);
});
test('only trusted same-repository PR runs, no untrusted PR source executed',()=>{
 const wf=yaml();
 assert.match(wf,/github\.event\.workflow_run\.head_repository\.full_name == github\.repository/);
 assert.match(wf,/workflow_run\.event == 'pull_request'/);
 assert.match(wf,/ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
 assert.match(wf,/persist-credentials: false/);
 assert.doesNotMatch(wf,/pull_request_target:|ref: \$\{\{ github\.event\.pull_request\.head\./);
});
test('post-CI gate reads real workflow states and revalidates exact PR SHA',()=>{
 const wf=yaml();
 assert.match(wf,/listPullRequestsAssociatedWithCommit/);
 assert.match(wf,/listWorkflowRunsForRepo/);
 assert.match(wf,/attestCi/);
 assert.match(wf,/pr\.head\.sha\s*!==\s*run\.head_sha/);
 assert.match(wf,/apiComplete/);
 assert.match(wf,/CI evidence unavailable/);
});
test('post-CI action remains read-only and cannot merge or mutate an issue',()=>{
 const wf=yaml();
 assert.match(wf,/actions: read/);
 assert.match(wf,/pull-requests: read/);
 assert.match(wf,/contents: read/);
 assert.doesNotMatch(wf,/issues: write|pull-requests: write|contents: write|github\.rest\.pulls\.merge|github\.rest\.issues\.(?:createComment|updateComment)/);
 assert.match(wf,/actions\/github-script@ed597411d8f924073f98dfc5c65a23a2325f34cd/);
 assert.match(wf,/actions\/upload-artifact@b7c566a772e6b6bfb58ed0dc250532a479d7789f/);
});
test('post-CI never equates WAITING UNKNOWN or STALE with verified approval',()=>{
 const wf=yaml();
 const {postCiDisposition}=require('./ultra_sentinel_post_ci_state.cjs');
 const base={sourceSha:'a'.repeat(40),linkedCount:1,eligibleCount:1};
 assert.equal(postCiDisposition({...base,ciStatuses:['WAITING']}).action,'RETRY');
 assert.equal(postCiDisposition({...base,ciStatuses:['WAITING'],exhausted:true}).action,'BLOCK');
 assert.equal(postCiDisposition({...base,ciStatuses:['UNKNOWN']}).action,'BLOCK');
 assert.equal(postCiDisposition({...base,eligibleCount:0,ciStatuses:[]}).action,'ARCHIVE');
 assert.equal(postCiDisposition({...base,ciStatuses:['PASS']}).action,'VERIFIED');
 assert.match(wf,/if\(decision\.action==='BLOCK'\)\s*core\.setFailed/);
 assert.match(wf,/report\.attested=decision\.attested/);
 assert.match(wf,/Only PASS is verified evidence/);
 assert.match(wf,/if-no-files-found: error/);
});

test('ROOT: no current PR cannot receive PASS or approval; incomplete API still blocks',()=>{
 const wf=yaml();
 const {postCiDisposition}=require('./ultra_sentinel_post_ci_state.cjs');
 const base={sourceSha:'a'.repeat(40),linkedCount:0,eligibleCount:0,ciStatuses:[]};
 const stale=postCiDisposition(base);
 assert.equal(stale.status,'STALE');assert.equal(stale.attested,false);
 assert.equal(stale.autoMergeAllowed,false);assert.equal(stale.autoApproveAllowed,false);
 assert.equal(postCiDisposition({...base,apiComplete:false}).action,'BLOCK');
 assert.match(wf,/core\.notice\('STALE historical PR evidence archived/);
 assert.match(wf,/report\.status=decision\.status/);
});

test('post-CI checks the actual default branch and a bounded complete run list',()=>{
 const wf=yaml();
 assert.match(wf,/context\.payload\.repository\?\.default_branch/);
 assert.match(wf,/page=2/);
 assert.match(wf,/total<=1000/);
});
