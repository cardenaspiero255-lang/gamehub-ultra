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
 assert.match(wf,/pr\.head\.sha !== run\.head_sha/);
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
test('post-CI reports never equate a pending/unknown result with approval',()=>{
 const wf=yaml();
 assert.match(wf,/if\s*\(\s*ci\.status\s*!==\s*['"]PASS['"]\s*\)\s*core\.setFailed/);
 assert.match(wf,/No automated merge or approval/);
 assert.match(wf,/if-no-files-found: error/);
});

test('ROOT: post-CI must not claim a green run when no current PR received attestation',()=>{
 const src=yaml();
 assert.match(src,/if\s*\(!report\.prs\.length\)core\.setFailed\(/);
});

test('post-CI checks the actual default branch and a bounded complete run list',()=>{
 const wf=yaml();
 assert.match(wf,/context\.payload\.repository\?\.default_branch/);
 assert.match(wf,/page=2/);
 assert.match(wf,/total<=1000/);
});
