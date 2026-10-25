'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const workflow=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-pr221-protected.yml'),'utf8');

test('PR221 protected job is ownerless read-only and checks only PR221',()=>{
 assert.match(workflow,/pull_request_target:/);
 assert.match(workflow,/github\.event\.pull_request\.number == 221/);
 assert.match(workflow,/contents: read/);
 assert.match(workflow,/pull-requests: read/);
 assert.match(workflow,/actions: read/);
 assert.doesNotMatch(workflow,/(?:contents|pull-requests|actions): write/);
 assert.doesNotMatch(workflow,/\bwrite-all\b/);
});
test('never runs candidate PR source, never persists checkout GitHub token',()=>{
 assert.match(workflow,/ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
 assert.match(workflow,/persist-credentials: false/);
 assert.doesNotMatch(workflow,/\$\{\{ github\.event\.pull_request\.head/);
 assert.doesNotMatch(workflow,/git checkout .*pull/);
});
test('runs a main-deployed exact SHA verifier and refuses unverified proof',()=>{
 assert.match(workflow,/ultra_sentinel_pr221_scope\.cjs/);
 assert.match(workflow,/AUTHENTICATED_EXACT_DELTA_MANUAL_REVIEW_REQUIRED/);
 for(const needle of ['provenanceAuthenticated !== true','manualReviewRequired !== true',
                      'autoApproveAllowed !== false','autoMergeAllowed !== false','core.setFailed('])
  assert.ok(workflow.includes(needle),needle);
});
test('no Sentry, signing, arbitrary secrets or auto-approval operations',()=>{
 assert.doesNotMatch(workflow,/SENTRY_AUTH_TOKEN|SENTRY_DSN|secrets\./);
 assert.doesNotMatch(workflow,/pulls\.merge\s*\(|git\.create\w*\s*\(|repos\.create\w*\s*\(|setSecret\s*\(|enableAutoMerge\s*\(/i);
});
