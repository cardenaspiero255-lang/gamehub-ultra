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

test('Sentinel workflows pin Node 24 artifact uploads and GitHub API scripting',()=>{
 const workflows=[
  'ultra-sentinel-independent-review.yml',
  'ultra-sentinel-auto-review.yml',
  'ultra-sentinel-sentry-incidents.yml',
  'ultra-sentinel-regression-investigator.yml',
  'ultra-sentinel-mutation.yml',
  'ultra-sentinel-kotlin-mutation.yml'
 ];
 for(const name of workflows){
  const content=fs.readFileSync(path.resolve(__dirname,'../workflows',name),'utf8');
  assert.ok(content.includes('actions/upload-artifact@b7c566a772e6b6bfb58ed0dc250532a479d7789f'),
   name+' must use audited Node 24 artifact action');
  assert.ok(!content.includes('actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02'),
   name+' must not use retired Node 20 artifact action');
 }
 for(const name of workflows.slice(0,2)){
  const content=fs.readFileSync(path.resolve(__dirname,'../workflows',name),'utf8');
  assert.ok(content.includes('actions/github-script@ed597411d8f924073f98dfc5c65a23a2325f34cd'),
   name+' must use SHA-pinned Node 24 GitHub script');
 }
});
