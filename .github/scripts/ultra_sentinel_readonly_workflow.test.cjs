'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const yaml=()=>fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-independent-review.yml'),'utf8');
test('read-only reviewer runs for PRs, including drafts, without issue write privileges',()=>{
 const x=yaml();
 assert.match(x,/pull_request:\s*\n\s*types:\s*\[opened, reopened, synchronize, ready_for_review\]/);
 assert.match(x,/contents: read/);
 assert.match(x,/pull-requests: read/);
 assert.doesNotMatch(x,/issues:\s*write|pull-requests:\s*write|pull_request_target:/);
});
test('never executes PR source or accesses third-party secrets',()=>{
 const x=yaml();
 assert.match(x,/ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
 assert.match(x,/persist-credentials: false/);
 assert.match(x,/ultra_sentinel_core\.cjs/);
 assert.doesNotMatch(x,/ref: \$\{\{ github\.event\.pull_request\.head\.sha \}\}/);
 assert.doesNotMatch(x,/secrets\.[A-Z_]+|ANTHROPIC_API_KEY|XAI_API_KEY|DEEPSEEK_API_KEY|GROQ_API_KEY/);
});
test('uses full immutable SHA and always uploads a report without posting comments',()=>{
 const x=yaml();
 assert.match(x,/after\.head\.sha !== pr\.head\.sha/);
 assert.match(x,/fs\.writeFileSync\(/);
 assert.match(x,/upload-artifact@[a-f0-9]{40}/);
 assert.match(x,/if-no-files-found: error/);
 assert.match(x,/retention-days: 7/);
 assert.doesNotMatch(x,/issues\.(?:createComment|updateComment)/);
});
test('partial patch evidence or BLOCKER does not silently produce a green gate',()=>{
 const x=yaml();
 assert.match(x,/coverage\.partial\)/);
 assert.match(x,/severity === "BLOCKER"/);
 assert.match(x,/core\.setFailed/);
});
