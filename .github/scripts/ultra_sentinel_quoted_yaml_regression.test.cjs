'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const sha='a'.repeat(40),name='.github/workflows/complex.yml',checkout='actions/checkout@'+'f'.repeat(40);
const scan=body=>reviewWorkflows({sha,expected:[name],sources:{[name]:body}});
const alerts=x=>x.findings.map(f=>f.rule);
const start='jobs:\n  test:\n    steps:\n      - uses: '+checkout+'\n        with:\n';
const dangerous='          ref: \${{ github.event.pull_request.head.sha }}\n';
for(const kind of [
 'on: ["pull_request_target"]\n',
 "on: ['pull_request_target']\n",
 '"on": ["workflow_run"]\n',
 'on:\n  "pull_request_target":\n    types: [opened]\n',
 "on:\n  'workflow_run':\n    types: [completed]\n"
]){
 test('P1 quoted privileged YAML trigger '+JSON.stringify(kind.trim()),()=>{
  const r=scan(kind+start+dangerous);
  assert.ok(alerts(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
 });
}
for(const ref of [
 '          ref: "\${{ github.event.pull_request.head.sha }}"\n',
 "          ref: '\${{ github.head_ref }}'\n",
 '          ref: \${{ github.event.pull_request.head.ref }}\n'
]){
 test('P1 quoted or variant untrusted checkout ref '+ref.trim(),()=>{
  const r=scan('on: pull_request_target\n'+start+ref);
  assert.ok(alerts(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
 });
}
for(const permission of [
 'permissions: {"contents": write}\n',
 "permissions: {'checks': write}\n",
 'permissions:\n  "security-events": write\n',
 "permissions:\n  'pull-requests': write\n"
]){
 test('P2 quoted write scope '+permission.trim(),()=>{
  const r=scan('on: pull_request\n'+permission);
  assert.ok(alerts(r).includes('PRIVILEGED_WRITE_TOKEN'),JSON.stringify(r));
 });
}
test('P2 unrelated later action ref does not contaminate a safe checkout',()=>{
 const wf='on: pull_request_target\njobs:\n  test:\n    steps:\n'+
 '      - name: Trusted checkout\n        uses: '+checkout+'\n        with:\n          ref: main\n'+
 "      - name: A different action\n        uses: org/special@"+'b'.repeat(40)+"\n        with:\n          ref: \${{ github.head_ref }}\n";
 const r=scan(wf);
 assert.ok(!alerts(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('P2 second checkout dangerous ref still triggers blocker',()=>{
 const wf='on: [pull_request_target]\njobs:\n  test:\n    steps:\n'+
 '      - uses: '+checkout+'\n        with:\n          ref: main\n'+
 '      - uses: '+checkout+'\n        with:\n'+dangerous;
 const r=scan(wf);
 assert.ok(alerts(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
