'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const fs=require('node:fs');
const path=require('node:path');
const yaml=require('js-yaml');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const workflowPath=path.resolve(__dirname,'../workflows/ultra-sentinel-github-trusted-evidence.yml');
const source=fs.readFileSync(workflowPath,'utf8');
const config=yaml.load(source);
const job=config.jobs['trusted-main-evidence'];
const step=(name)=>job.steps.find(x=>x.name===name);
const original=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-self-review.yml'),'utf8');
test('trusted evidence is triggered only from pull_request_target, never pull_request source',()=>{
 assert.ok(config.on.pull_request_target);
 assert.equal(config.on.pull_request,undefined);
 assert.deepEqual(config.on.pull_request_target.branches,['main']);
});
test('diagnostic workflow uses least-privilege read-only token',()=>{
 assert.deepEqual(config.permissions,{'contents':'read','pull-requests':'read','actions':'read'});
 assert.doesNotMatch(source,/(?:contents|pull-requests|actions):\s*write|write-all/);
});
test('trusted default branch checkout never selects attacker HEAD or retains credentials',()=>{
 const checkout=step('Checkout trusted default branch only');
 assert.match(checkout.uses,/^actions\/checkout@[a-f0-9]{40}$/);
 assert.match(checkout.with.ref,/github\.event\.repository\.default_branch/);
 assert.doesNotMatch(checkout.with.ref,/pull_request\.head|github\.head_ref/);
 assert.equal(checkout.with['persist-credentials'],false);
});
test('trusted collector dependencies come from main lockfile, with lifecycle scripts disabled',()=>{
 assert.match(step('Install main-branch locked parser without scripts').run,/npm ci --ignore-scripts/);
 assert.match(step('Test trusted collector contract with no API credentials').run,/node --test/);
});
test('GitHub token is passed only to the trusted read-only collector step',()=>{
 const s=step('Collect SHA-scoped GitHub evidence as data');
 assert.match(s.env.GITHUB_TOKEN,/github\.token/);
 assert.match(s.env.GITHUB_REPOSITORY,/github\.repository/);
 assert.match(s.env.PR_NUMBER,/github\.event\.pull_request\.number/);
 assert.match(s.env.PR_HEAD_SHA,/github\.event\.pull_request\.head\.sha/);
 for(const candidate of job.steps)if(candidate!==s)
  assert.equal(JSON.stringify(candidate.env||{}).includes('GITHUB_TOKEN'),false);
});
test('the untrusted candidate branch is never installed, built, executed or checked out',()=>{
 assert.equal(job.steps.filter(s=>s.uses?.startsWith('actions/checkout@')).length,1);
 const body=job.steps.map(s=>s.run||'').join('\n');
 assert.doesNotMatch(body,/git checkout|git fetch|pull_request\.head|github\.head_ref|npm install/);
});
test('no repair, repository write or merge command exists in the trusted workflow',()=>{
 const body=job.steps.map(s=>s.run||'').join('\n');
 assert.doesNotMatch(body,/git push|gh pr merge|merge_pull_request|update_ref|repair_commits/);
});
test('original required Self-Review remains a separate untouched protection',()=>{
 const old=yaml.load(original);
 assert.ok(old.jobs['trusted-review']);assert.ok(old.jobs['candidate-tests']);
 assert.ok(old.jobs['trusted-review'].steps.some(s=>s.run?.includes('ultra_sentinel_selfreview.cjs')));
 assert.doesNotMatch(original,/ultra_sentinel_github_collector/);
});
test('shadow evidence cannot mask errors in mandatory review with continue-on-error',()=>{
 assert.doesNotMatch(source,/continue-on-error:\s*true/);
 assert.equal(job.steps.some(s=>s['continue-on-error']===true),false);
});
test('untrusted text never interpolates directly into a shell command',()=>{
 for(const s of job.steps.filter(s=>s.run)){
  assert.doesNotMatch(s.run,/\$\{\{/);
 }
});
test('artifact is pinned and bounded; its absence is not an approval',()=>{
 const a=step('Upload bounded sanitized shadow report');
 assert.match(a.uses,/^actions\/upload-artifact@[a-f0-9]{40}$/);
 assert.equal(a.with.path,'sentinel-github-evidence-shadow.json');
 assert.equal(a.with['retention-days'],7);
 assert.equal(a.with['if-no-files-found'],'ignore');
});
test('trusted Workflow AST analysis is complete, not bypassed for convenience',()=>{
 const report=inspectWorkflow(source,{path:'.github/workflows/ultra-sentinel-github-trusted-evidence.yml'});
 assert.notEqual(report.status,'INCOMPLETE',JSON.stringify(report));
 assert.equal(report.coverage.partial,false);
});
