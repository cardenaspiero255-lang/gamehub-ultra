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
