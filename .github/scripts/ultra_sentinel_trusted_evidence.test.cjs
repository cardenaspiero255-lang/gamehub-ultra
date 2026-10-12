'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const fs=require('node:fs');
const path=require('node:path');
const yaml=require('js-yaml');
const target=path.resolve(__dirname,'../workflows/ultra-sentinel-self-review.yml');
const source=fs.readFileSync(target,'utf8');
const workflow=yaml.load(source);
const trusted=workflow.jobs['trusted-review'];
const candidate=workflow.jobs['candidate-tests'];
const step=(name)=>trusted.steps.find(x=>x.name===name);
test('workflow: both events are supported, but privileged logic only targets pull_request_target',()=>{
 assert.ok(workflow.on.pull_request);
 assert.ok(workflow.on.pull_request_target);
 assert.match(trusted.if,/github\.event_name == 'pull_request_target'/);
 assert.match(candidate.if,/github\.event_name == 'pull_request'/);
});
test('least privilege: workflow has read-only contents, PR and Actions permissions',()=>{
 assert.deepEqual(workflow.permissions,{'contents':'read','pull-requests':'read'});
 assert.deepEqual(candidate.permissions,{'contents':'read'});
 assert.deepEqual(trusted.permissions,{'contents':'read','pull-requests':'read','actions':'read'});
 assert.doesNotMatch(source,/(?:contents|pull-requests|actions):\s*write|write-all/);
});
test('trusted code: target runner checks out default branch, never PR head',()=>{
 const checkout=step('Checkout independent main source, NEVER the PR head');
 assert.ok(checkout);
 assert.match(checkout.with.ref,/github\.event\.repository\.default_branch/);
 assert.equal(checkout.with['persist-credentials'],false);
 assert.doesNotMatch(checkout.with.ref,/pull_request\.head|github\.head_ref/);
});
test('candidate job receives no token and uses credential-free checkout',()=>{
 assert.equal(candidate.steps[0].with['persist-credentials'],false);
 assert.equal(candidate.steps.some(s=>JSON.stringify(s.env||{}).includes('GITHUB_TOKEN')),false);
});
test('trusted dependencies only installed from main and lifecycle scripts disabled',()=>{
 const install=step('Install trusted parser from main lockfile');
 assert.ok(install);assert.match(install.run,/npm ci --ignore-scripts/);
});
test('scanner: independent main-diff review still runs before evidence collection',()=>{
 const scan=trusted.steps.findIndex(s=>s.name==='Scan immutable PR diff as untrusted data only');
 const evidence=trusted.steps.findIndex(s=>s.name==='Collect authenticated GitHub evidence in shadow mode');
 assert.ok(scan>=0&&evidence>scan);
 assert.match(trusted.steps[scan].run,/ultra_sentinel_selfreview\.cjs/);
});
test('trusted evidence uses github-token only in protected target runner',()=>{
 const s=step('Collect authenticated GitHub evidence in shadow mode');
 assert.ok(s);assert.equal(s['continue-on-error'],true);
 assert.match(s.run,/node \.github\/scripts\/ultra_sentinel_github_collector\.cjs/);
 assert.match(s.env.GITHUB_TOKEN,/github\.token/);
 assert.match(s.env.GITHUB_REPOSITORY,/github\.repository/);
 assert.match(s.env.PR_NUMBER,/github\.event\.pull_request\.number/);
 assert.match(s.env.PR_HEAD_SHA,/github\.event\.pull_request\.head\.sha/);
});
test('trusted runner does not execute, checkout or test candidate PR source',()=>{
 const txt=trusted.steps.map(s=>s.run||'').join('\n');
 assert.doesNotMatch(txt,/git\s+checkout|git\s+fetch|pull_request\.head|github\.head_ref|npm install/);
 assert.equal(trusted.steps.filter(s=>s.uses?.startsWith('actions/checkout@')).length,1);
});
test('evidence remains advisory, never a required merge or repair authority',()=>{
 const evidence=step('Collect authenticated GitHub evidence in shadow mode');
 assert.equal(evidence['continue-on-error'],true);
 assert.doesNotMatch(trusted.steps.map(s=>s.run||'').join('\n'),/git push|gh pr merge|merge_pull_request|repair_commits\s*=\s*ON/);
});
test('sanitized artifact is optional, short lived, pinned and contains no credentials',()=>{
 const a=step('Store sanitized, bounded shadow evidence');
 assert.ok(a);assert.equal(a['continue-on-error'],true);
 assert.match(a.uses,/^actions\/upload-artifact@[a-f0-9]{40}$/);
 assert.equal(a.with.path,'sentinel-github-evidence-shadow.json');
 assert.equal(a.with['retention-days'],7);
 assert.equal(a.with['if-no-files-found'],'ignore');
 assert.equal(a.env?.GITHUB_TOKEN,undefined);
});
test('unknown workflow or missing trusted scanner breaks the contract tests',()=>{
 assert.ok(trusted.steps.some(s=>s.name==='Validate trusted meta-review unit tests'));
 assert.equal(workflow.jobs['trusted-review']['timeout-minutes'],10);
 assert.equal(workflow.jobs['candidate-tests']['timeout-minutes'],4);
});
