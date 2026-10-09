'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {candidate,judge}=require('./ultra_sentinel_orchestrator.cjs');
const SHA='a'.repeat(40),REPO='cardenaspiero255-lang/gamehub-ultra';
const FILE='app/src/main/java/com/cardenaspiero255/gamehubultra/Engine.kt';
const CONTENT='fun render() {\n  System.gc()\n  println("ok")\n}\n';
const FINDING={rule:'FORCED_GC',path:FILE,line:2,severity:'MEDIUM'};
const checks={'sentinel-core-tests':'success','android-build':'success',
 'unit-test-coverage':'success','architecture-boundary':'success'};
function workflow(trigger,run){
 return ['on: '+trigger,'jobs:','  audit:','    runs-on: ubuntu-latest',
  '    steps:','      - run: |','          '+run].join('\n');
}
test('P1 privileged issue_comment script rejects direct comment.body interpolation',()=>{
 const actual=inspectWorkflow(workflow('issue_comment','echo "${{ github.event.comment.body }}"'));
 assert.ok(actual.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify(actual));
});
test('P1 privileged PR review script rejects direct pull_request.title interpolation',()=>{
 const actual=inspectWorkflow(workflow('pull_request_target','echo "${{ github.event.pull_request.title }}"'));
 assert.ok(actual.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify(actual));
});
test('P1 privileged review script rejects direct untrusted github.head_ref interpolation',()=>{
 const actual=inspectWorkflow(workflow('pull_request_target','echo "${{ github.head_ref }}"'));
 assert.ok(actual.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify(actual));
});
test('negative safe numeric event and trusted SHA expressions remain permitted',()=>{
 const actual=inspectWorkflow(workflow('issue_comment','echo "${{ github.event.issue.number }}-${{ github.sha }}"'));
 assert.ok(!actual.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'),JSON.stringify(actual));
});
test('P1 Judge refuses tampering with valid deletion diff context',()=>{
 const proposal=candidate({filename:FILE,content:CONTENT,sha:SHA,findings:[FINDING]});
 assert.equal(proposal.status,'DRAFT_PATCH');
 const tampered={...proposal,patch:proposal.patch.replace(' println("ok")',' println("other")')};
 const got=judge(tampered,{sha:SHA,checks,source:CONTENT,findings:[FINDING]});
 assert.equal(got.status,'REJECT',JSON.stringify(got));
});
test('P1 Judge requires original source and finding evidence for exact-patch approval',()=>{
 const proposal=candidate({filename:FILE,content:CONTENT,sha:SHA,findings:[FINDING]});
 assert.equal(judge(proposal,{sha:SHA,checks}).status,'REJECT');
 const okay=judge(proposal,{sha:SHA,checks,source:CONTENT,findings:[FINDING]});
 assert.equal(okay.status,'ELIGIBLE_FOR_HUMAN_REVIEW',JSON.stringify(okay));
});
test('P1 Judge rejects different finding line even when proposal diff is syntactically valid',()=>{
 const proposal=candidate({filename:FILE,content:CONTENT,sha:SHA,findings:[FINDING]});
 const got=judge(proposal,{sha:SHA,checks,source:CONTENT,findings:[{...FINDING,line:3}]});
 assert.equal(got.status,'REJECT',JSON.stringify(got));
});

test('P2 AST rejects a job with an empty steps sequence as incomplete evidence',()=>{
 const src=['on: pull_request_target','jobs:','  audit:','    steps: []'].join('\n');
 const result=inspectWorkflow(src);
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
});
test('P2 AST rejects an empty run command as incomplete evidence',()=>{
 const src=['on: issue_comment','jobs:','  audit:','    steps:','      - run: ""'].join('\n');
 const result=inspectWorkflow(src);
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
});
test('P1 Judge requires explicit immutable expected SHA even when CI and source match',()=>{
 const proposal=candidate({filename:FILE,content:CONTENT,sha:SHA,findings:[FINDING]});
 const result=judge(proposal,{checks,source:CONTENT,findings:[FINDING]});
 assert.equal(result.status,'REJECT',JSON.stringify(result));
});
