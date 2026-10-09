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
 // Preserve blocking on HIGH and BLOCKER without tying test to whitespace.
 assert.match(x,/f\.severity==='HIGH'\|\|f\.severity==='BLOCKER'/);
 assert.match(x,/core\.setFailed/);
});

test('every Sentinel workflow uses Node 24 SHA-pinned upload and GitHub API actions',()=>{
 // Discover the directory, rather than maintain an incomplete manual allowlist.
 // Newly added workflows must automatically enter this safety check.
 const directory=path.resolve(__dirname,'../workflows');
 const workflows=fs.readdirSync(directory).filter(name=>
  /^ultra-sentinel-.*\.yml$/.test(name)
 );
 assert.ok(workflows.length>=9,'expected at least nine Sentinel workflows');
 const allowed={
  'actions/upload-artifact':'b7c566a772e6b6bfb58ed0dc250532a479d7789f',
  'actions/github-script':'ed597411d8f924073f98dfc5c65a23a2325f34cd'
 };
 const seen={'actions/upload-artifact':0,'actions/github-script':0};
 for(const name of workflows){
  const source=fs.readFileSync(path.join(directory,name),'utf8');
  for(const [action,sha] of Object.entries(allowed)){
   const uses=new RegExp('^\\s*(?:-\\s*)?uses:\\s*'+action.replace('/','\\/')+'@([^\\s#]+)','gm');
   for(const match of source.matchAll(uses)){
    seen[action]++;
    assert.equal(match[1],sha,name+' must use SHA-pinned Node 24 '+action);
   }
  }
 }
 assert.ok(seen['actions/upload-artifact']>=9,'expected all nine Sentinel uploads');
 assert.ok(seen['actions/github-script']>=4,'expected all four Sentinel API actions');
});
