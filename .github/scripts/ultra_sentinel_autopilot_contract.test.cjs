'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const yaml=require('js-yaml');
const acorn=require('acorn');

function workflow(path){
  return yaml.load(fs.readFileSync(path,'utf8'));
}
function scripts(w){
  return Object.values(w.jobs).flatMap(j=>j.steps||[])
    .filter(s=>s.uses?.startsWith('actions/github-script@'))
    .map(s=>s.with.script);
}
const mergePath='.github/workflows/ultra-sentinel-pr-autopilot.yml';
const carPath='.github/workflows/ultra-sentinel-car-agent-dispatch.yml';

test('event-driven only: no scheduled task or untrusted pull request checkout',()=>{
  for(const path of [mergePath,carPath]){
    const text=fs.readFileSync(path,'utf8');
    const w=workflow(path);
    assert.equal(Object.hasOwn(w.on,'schedule'),false);
    assert.equal(Object.hasOwn(w.on,'pull_request'),false);
    assert.ok(!text.includes('actions/checkout@'));
    assert.match(text,/actions\/github-script@[a-f0-9]{40}/);
    assert.ok(scripts(w).length>0);
  }
});

test('embedded JavaScript parses as code without running untrusted data',()=>{
  for(const path of [mergePath,carPath]){
    for(const code of scripts(workflow(path))){
      assert.doesNotThrow(()=>acorn.parse(code,{
        ecmaVersion:'latest',sourceType:'script',
        allowAwaitOutsideFunction:true,allowReturnOutsideFunction:true
      }));
      assert.doesNotMatch(code,/eval\s*\(|new\s+Function\s*\(/);
    }
  }
});

test('PR gate refuses unprotected main, draft, missing owner approval and missing exact SHA checks',()=>{
  const w=workflow(mergePath);
  assert.ok(w.on.pull_request_target && w.on.workflow_run);
  assert.equal(w.jobs['guarded-merge'].permissions.contents,'write');
  const code=scripts(w)[0];
  for(const required of [
    "p.base.ref==='main'","!p.draft","p.user?.login===owner",
    "automerge:approved","branch.protected","AUTOMERGE_OK ","listComments",
    "rc?.enforcement_level!=='everyone'","'build','coverage'",
    "r.head_sha===pr.head.sha","pr.changed_files","^\\.github\\/",
    "listReviews","reviewThreads(first:100)","t.pageInfo.hasNextPage",
    "after.head.sha!==pr.head.sha","after.base.sha!==pr.base.sha",
    "merge_method:'squash'","sha:pr.head.sha"
  ]) assert.ok(code.includes(required),required);
});

test('CAR dispatch is issue-label/event driven and requires original owner',()=>{
  const w=workflow(carPath);
  assert.ok(w.on.issues && w.on.pull_request_target);
  const code=scripts(w)[0];
  for(const required of [
    "context.actor!==owner","i.user?.login!==owner",
    "CAR_AGENT_LOGIN","github.rest.issues.addAssignees",
    "car:ready","car:active","'car:ready'",
    "TDD RED","no declarar el CAR terminado"
  ]) assert.ok(code.includes(required),required);
});
