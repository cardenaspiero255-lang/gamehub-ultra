'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const SHA='a'.repeat(40);
function workflow({trigger='pull_request_target',permissions='permissions: read-all',ref='main',body='echo safe'}={}){
 return [
  'on: '+trigger,
  ...(permissions?[permissions]:[]),
  'jobs:','  audit:','    runs-on: ubuntu-latest',
  '    steps:',
  '      - uses: actions/checkout@'+SHA,
  '        with:','          ref: '+ref,
  '      - run: '+body
 ].join('\n');
}
test('P1: privileged mutable feature branch cannot be certified clean',()=>{
 const result=inspectWorkflow(workflow({ref:'refs/heads/feature-untrusted',body:'bash scripts/build.sh'}));
 assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
});
test('P1: privileged mutable tag cannot be certified clean',()=>{
 const result=inspectWorkflow(workflow({ref:'refs/tags/release-candidate'}));
 assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
});
test('P1: trusted main remains certifiable when permissions are explicit',()=>{
 const result=inspectWorkflow(workflow({ref:'refs/heads/main'}));
 assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
});
test('P1/P2: omitted default token permissions require review in privileged workflow',()=>{
 const result=inspectWorkflow(workflow({trigger:'issues',permissions:null}));
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
});
test('P1/P2: per-job explicit token permissions suffice if workflow-level permissions omitted',()=>{
 const result=inspectWorkflow([
 'on: issues','jobs:','  audit:','    permissions: read-all',
 '    runs-on: ubuntu-latest','    steps:','      - run: echo safe'
 ].join('\n'));
 assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
});
test('P2: post-CI never marks UNKNOWN/WAITING as green certification',()=>{
 const script=fs.readFileSync(path.join(__dirname,'../workflows/ultra-sentinel-sss-post-ci.yml'),'utf8');
 assert.match(script,/if\s*\(\s*ci\.status\s*!==\s*['"]PASS['"]\s*\)\s*core\.setFailed\s*\(/);
});

test('Root cause: pinned third-party action receiving secret input is not clean',()=>{
 const value='${{ secrets.PROD_TOKEN }}';
 const source=['on: workflow_dispatch','permissions: {contents: read}','jobs:','  audit:','    steps:',
 '      - uses: vendor/repo@'+SHA,'        with:','          token: '+value].join('\n');
 const result=inspectWorkflow(source);
 assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
});
test('Root cause: pinned third-party action receiving secret environment is not clean',()=>{
 const source=['on: workflow_dispatch','permissions: {contents: read}','jobs:','  audit:','    steps:',
 '      - uses: vendor/repo@'+SHA,'        env:','          TOKEN: ${{ secrets.PROD_TOKEN }}'].join('\n');
 const result=inspectWorkflow(source);
 assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
});
test('Root cause: privileged disabled job cannot be certified clean',()=>{
 const source=['on: workflow_dispatch','permissions: {contents: read}','jobs:','  audit:',
 '    if: false','    steps:','      - run: echo hello'].join('\n');
 assert.notEqual(inspectWorkflow(source).status,'NO_RISK_PATTERN');
});
test('Root cause: privileged ignored failed step cannot be certified clean',()=>{
 const source=['on: workflow_dispatch','permissions: {contents: read}','jobs:','  audit:',
 '    steps:','      - run: echo hello','        continue-on-error: true'].join('\n');
 assert.notEqual(inspectWorkflow(source).status,'NO_RISK_PATTERN');
});

test('Root cause: inherited job secret is forwarded to every uses step',()=>{
 const source=['on: workflow_dispatch','permissions: {contents: read}','jobs:',
  '  audit:','    env:','      SHARED_TOKEN: ${{ secrets.SERVICE_CREDENTIAL }}',
  '    steps:','      - uses: vendor/repo@'+SHA].join('\n');
 assert.notEqual(inspectWorkflow(source).status,'NO_RISK_PATTERN');
});
test('Root cause: inherited workflow secret reaches the third-party action',()=>{
 const source=['on: workflow_dispatch','permissions: {contents: read}',
  'env:','  SHARED_TOKEN: ${{ secrets.SERVICE_CREDENTIAL }}',
  'jobs:','  audit:','    steps:','      - uses: vendor/repo@'+SHA].join('\n');
 assert.notEqual(inspectWorkflow(source).status,'NO_RISK_PATTERN');
});
test('Root cause: derived secret expressions remain tainted in action inputs',()=>{
 const source=['on: workflow_dispatch','permissions: {contents: read}',
 'jobs:','  audit:','    steps:','      - uses: vendor/repo@'+SHA,
 '        with:',"          token: ${{ format('{0}', secrets.SERVICE_CREDENTIAL) }}"].join('\n');
 assert.notEqual(inspectWorkflow(source).status,'NO_RISK_PATTERN');
});
test('Regression: first-party pinned github-script uses normal scoped GitHub token',()=>{
 const source=['on: workflow_dispatch','permissions: {contents: read}',
 'jobs:','  audit:','    steps:','      - uses: actions/github-script@'+SHA,
 '        with:','          github-token: ${{ github.token }}',
 '          script: core.info("safe")'].join('\n');
 assert.equal(inspectWorkflow(source).status,'NO_RISK_PATTERN');
});

// RED: serialized secret objects require a non-clean structural verdict.
test('Sensitive GitHub context object is not clean for third party',()=>{
 const s=['on: workflow_dispatch','permissions: read-all','jobs:','  audit:',
  '    runs-on: ubuntu-latest','    steps:',
  '      - uses: vendor/collector@'+SHA,'        with:',
  '          metadata: ${{ toJSON(secrets) }}'].join('\n');
 assert.notEqual(inspectWorkflow(s).status,'NO_RISK_PATTERN');
});

const makeExpr=s=>String.fromCharCode(36,123,123)+' '+s+' }}';
const adversarial=(lines,jobExtra=[])=>[
 'on: workflow_dispatch','permissions: read-all','jobs:','  audit:',
 '    runs-on: ubuntu-latest',...jobExtra,'    steps:',...lines
].join('\n');
for(const expression of ['toJSON(secrets)','toJSON(github)',"format('{0}', toJSON(secrets))"]){
 test('Context object disclosure must be incomplete: '+expression,()=>{
  const w=adversarial(['      - uses: vendor/collector@'+SHA,'        with:','          data: '+makeExpr(expression)]);
  assert.notEqual(inspectWorkflow(w).status,'NO_RISK_PATTERN');
 });
}
for(const expression of ['false && true','true && false','!true']){
 test('Disabled job condition cannot be trusted: '+expression,()=>{
  const w=adversarial(['      - run: echo hello'],['    if: '+makeExpr(expression)]);
  assert.notEqual(inspectWorkflow(w).status,'NO_RISK_PATTERN');
 });
}
for(const trigger of ['[]','{}']){
 test('No enabled event cannot pass: '+trigger,()=>{
  const w=['on: '+trigger,'permissions: read-all','jobs:','  audit:','    runs-on: ubuntu-latest','    steps:','      - run: echo hello'].join('\n');
  assert.equal(inspectWorkflow(w).status,'INCOMPLETE');
 });
}
for(const runner of ['windows-2022','windows-2025','ubuntu-24.04','macos-15']){
 test('Official GitHub hosted runner remains supported: '+runner,()=>{
  const w=['on: workflow_dispatch','permissions: read-all','jobs:','  audit:','    runs-on: '+runner,'    steps:','      - run: echo hello'].join('\n');
  assert.equal(inspectWorkflow(w).status,'NO_RISK_PATTERN');
 });
}
