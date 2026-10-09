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

test('Codex P1 mixed-case checkout inputs cannot hide mutable foreign repository',()=>{
 const src=['on: issue_comment','jobs:','  audit:','    steps:',
  '      - uses: actions/checkout@'+SHA,
  '        with:',
  '          Repository: attacker/evil',
  '          Ref: main'].join('\n');
 const r=inspectWorkflow(src,{trustedRepository:REPO});
 assert.ok(r.findings.some(f=>f.rule==='PRIVILEGED_EXTERNAL_MUTABLE_CHECKOUT'&&f.severity==='BLOCKER'),JSON.stringify(r));
});
test('Codex P1 colliding case-insensitive checkout input names fail closed',()=>{
 const src=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - uses: actions/checkout@'+SHA,
  '        with:',
  '          repository: '+REPO,
  '          Repository: attacker/evil',
  '          ref: main'].join('\n');
 const r=inspectWorkflow(src,{trustedRepository:REPO});
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
});
test('Codex P1 alternate github-server-url cannot impersonate the trusted same-name repo',()=>{
 const src=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - uses: actions/checkout@'+SHA,
  '        with:',
  '          repository: '+REPO,
  '          ref: main',
  '          github-server-url: https://attacker.example'].join('\n');
 const r=inspectWorkflow(src,{trustedRepository:REPO});
 assert.ok(r.findings.some(f=>f.rule==='PRIVILEGED_ALTERNATE_GITHUB_SERVER'&&f.severity==='BLOCKER'),JSON.stringify(r));
});
test('Codex P1 dynamic alternate GitHub server must fail closed',()=>{
 const src=['on: issue_comment','jobs:','  audit:','    steps:',
  '      - uses: actions/checkout@'+SHA,
  '        with:',
  '          github-server-url: ${{ inputs.server }}'].join('\n');
 const r=inspectWorkflow(src,{trustedRepository:REPO});
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
});
test('secure github-server-url variants cannot produce bogus blockers',()=>{
 for(const server of ['https://github.com','${{ github.server_url }}']){
  const src=['on: issue_comment','jobs:','  audit:','    steps:',
   '      - uses: actions/checkout@'+SHA,
   '        with:',
   '          repository: '+REPO,
   '          ref: main',
   '          github-server-url: '+server].join('\n');
  const r=inspectWorkflow(src,{trustedRepository:REPO});
  assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
 }
});

test('Codex P1 default branch issue and discussion events block script injection',()=>{
 for(const [event,field] of [['issues','issue.title'],['discussion','discussion.body']]){
  for(const trigger of [
    'on: '+event,
    'on: [push, '+event+']',
    'on: {'+event+': {types: [opened]}}'
  ]){
   const wf=[trigger,'jobs:','  audit:','    steps:',
    '      - run: echo "\${{ github.event.'+field+' }}"'].join('\n');
   const result=inspectWorkflow(wf);
   assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify({trigger,result}));
  }
 }
});
test('Codex P1 pinned github-script interpolated comment body is unsafe JavaScript',()=>{
 const src=['on: issue_comment','jobs:','  audit:','    steps:',
 '      - uses: actions/github-script@'+SHA,
 '        with:',
 '          script: |',
 '            const comment = "\${{ github.event.comment.body }}";',
 '            core.info(comment);'].join('\n');
 const result=inspectWorkflow(src);
 assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify(result));
});
test('Codex P1 github-script Script key with mixed case must be treated as executable',()=>{
 const src=['on: pull_request_target','jobs:','  audit:','    steps:',
 '      - uses: actions/github-script@'+SHA,
 '        with:',
 '          Script: console.log("\${{ github.event.pull_request.title }}")'].join('\n');
 const result=inspectWorkflow(src);
 assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify(result));
});
test('Codex P1 github-script duplicate Script input case variants are INCOMPLETE',()=>{
 const src=['on: issue_comment','jobs:','  audit:','    steps:',
 '      - uses: actions/github-script@'+SHA,
 '        with:',
 '          script: console.log("safe")',
 '          SCRIPT: console.log("not verified")'].join('\n');
 const result=inspectWorkflow(src);
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
});
test('Codex P1 github-script safe env interpolation is not false-positive',()=>{
 const src=['on: issue_comment','jobs:','  audit:','    steps:',
 '      - uses: actions/github-script@'+SHA,
 '        env:',
 '          MESSAGE: \${{ github.event.comment.body }}',
 '        with:',
 '          script: core.info(process.env.MESSAGE);'].join('\n');
 const result=inspectWorkflow(src);
 assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
});
test('privileged release body must not be interpolated into JavaScript',()=>{
 const src=['on: release','jobs:','  audit:','    steps:',
 '      - uses: actions/github-script@'+SHA,
 '        with:',
 '          script: console.log("\${{ github.event.release.body }}")'].join('\n');
 const result=inspectWorkflow(src);
 assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'),JSON.stringify(result));
});

test('Codex P1: serialized issue event object in privileged shell is injection',()=>{
 const expr='$'+'{{ toJSON(github.event.issue) }}';
 const result=inspectWorkflow(workflow('issues',"echo '"+expr+"'"));
 assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify(result));
});
test('Codex P1: serialized event objects cannot enter github-script',()=>{
 for(const expr of ['$'+'{{ toJson(github.event.comment) }}','$'+'{{ toJSON(github.event) }}']){
  const src=['on: issue_comment','jobs:','  audit:','    steps:',
   '      - uses: actions/github-script@'+SHA,'        with:',
   '          script: core.info("'+expr+'")'].join('\n');
  const result=inspectWorkflow(src);
  assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'),JSON.stringify(result));
 }
});
test('Codex P1: bracket notation for serialized issue and release objects fails closed',()=>{
 for(const expr of ['$'+'{{ toJSON(github.event["issue"]) }}','$'+'{{ toJSON(github.event.release) }}']){
  const result=inspectWorkflow(workflow('issues',"echo '"+expr+"'"));
  assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'),JSON.stringify(result));
 }
});
test('Codex P1: serializing trusted numeric fields is not a code-injection finding',()=>{
 const result=inspectWorkflow(workflow('issues',"echo '"+'$'+'{{ toJSON(github.event.issue.number) }}'+"'"));
 assert.ok(!result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'),JSON.stringify(result));
});
