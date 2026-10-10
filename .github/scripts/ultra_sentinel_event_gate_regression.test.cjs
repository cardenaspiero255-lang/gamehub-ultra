'use strict';
/* TDD regression family: a security gate must not attest work that is
 * impossible to run on any event configured by its workflow.
 * We never eval GitHub expressions, only compare simple literal event tests. */
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const clean=condition=>[
 'on: pull_request_target',
 'permissions: read-all',
 'jobs:',
 '  gate:',
 '    runs-on: ubuntu-latest',
 '    if: '+condition,
 '    steps:',
 '      - run: echo safe'
].join('\n');
const check=(trigger,condition,placement='job')=>{
 const lines=[
   'on: '+trigger,
   'permissions: read-all',
   'jobs:','  audit:','    runs-on: ubuntu-latest'
 ];
 if(placement==='job')lines.push('    if: '+condition);
 lines.push('    steps:');
 if(placement==='step')lines.push('      - if: '+condition);
 else lines.push('      - name: Run independent security gate');
 if(placement==='step')lines.push('        run: echo safe');
 else lines.push('        run: echo safe');
 return inspectWorkflow(lines.join('\n'));
};
for(const [label,condition] of [
 ['mismatched bare event', "github.event_name == 'push'"],
 ['mismatched expression', "${{ github.event_name == 'push' }}"],
 ['mismatched flipped operands', "'push' == github.event_name"],
 ['mismatched bracket property', "github['event_name'] == 'push'"],
 ['mismatched double quotes', 'github.event_name == "push"'],
 ['mismatched non-equality', "github.event_name != 'pull_request_target'"],
 ['mismatched flipped inequality', "'pull_request_target' != github.event_name"],
 ['mismatched disjunction', "github.event_name == 'push' || github.event_name == 'issues'"],
 ['mismatched conjunction', "github.event_name == 'push' && always()"],
 ['mismatched event with negated equality', "!(github.event_name == 'pull_request_target')"]
]){
 test('Inert critical job must not be clean: '+label,()=>{
  const verdict=check('pull_request_target',condition);
  assert.equal(verdict.status,'INCOMPLETE',JSON.stringify(verdict));
 });
}
for(const [label,condition] of [
 ['mismatched step gate', "github.event_name == 'push'"],
 ['mismatched step bracket event', "${{ github['event_name'] != 'pull_request_target' }}"]
]){
 test('Critical step gated away is not clean: '+label,()=>{
  const verdict=check('pull_request_target',condition,'step');
  assert.equal(verdict.status,'INCOMPLETE',JSON.stringify(verdict));
 });
}
test('Event mismatch also applies to a multi-event workflow',()=>{
 const verdict=check('[pull_request_target, issues]',"github.event_name == 'push'");
 assert.equal(verdict.status,'INCOMPLETE',JSON.stringify(verdict));
});
test('Event condition matching declared event is not a false positive',()=>{
 for(const condition of ["github.event_name == 'pull_request_target'",
  "github.event_name != 'push'",
  "${{ github.event_name == 'pull_request_target' }}"]){
  const verdict=check('pull_request_target',condition);
  assert.equal(verdict.status,'NO_RISK_PATTERN',JSON.stringify({condition,verdict}));
 }
});
test('Full base-trusted independent review is re-triggered on submitted and dismissed human reviews',()=>{
 const raw=fs.readFileSync(path.join(__dirname,'../workflows/ultra-sentinel-independent-review.yml'),'utf8');
 assert.match(raw,/^  pull_request_target:/m,'trusted main-source PR event must stay');
 assert.match(raw,/^  pull_request_review:\s*\n\s*types:\s*\[submitted, dismissed\]/m,
   'approval arriving after a failed missing-approval verdict must re-run the trusted gate');
 assert.match(raw,/ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
 assert.match(raw,/persist-credentials: false/);
 assert.doesNotMatch(raw,/^\s+pull-requests:\s+write\s*$/m);
});

for(const [label,source] of [
 ['empty pull_request_target event types',
  'on: {pull_request_target: {types: []}}'],
 ['empty pull_request_target branch filters',
  'on: {pull_request_target: {branches: []}}'],
 ['empty pull_request_target path filters',
  'on: {pull_request_target: {paths: []}}'],
 ['empty push branches',
  'on: {push: {branches: []}}'],
 ['cron with missing cron string',
  'on: {schedule: [{}]}'],
 ['cron with blank cron string',
  'on: {schedule: [{cron: "  "}]}'],
 ['event spec scalar impossible to schedule',
  'on: {pull_request_target: "random"}']
]){
 test('Invalid or inert trigger shape fails closed: '+label,()=>{
  const src=[source,'permissions: read-all','jobs:','  audit:',
   '    runs-on: ubuntu-latest','    steps:','      - run: echo safe'].join('\n');
  const verdict=inspectWorkflow(src);
  assert.equal(verdict.status,'INCOMPLETE',JSON.stringify(verdict));
 });
}
for(const [label,condition] of [
 ['uppercase expression property', "GITHUB.EVENT_NAME == 'push'"],
 ['unknown event dependent function', "contains(fromJSON('[\"push\"]'), github.event_name)"],
 ['dynamic event concatenation', "format('{0}', github.event_name) == 'push'"]
]){
 test('Unknown event-gate expression is never silently clean: '+label,()=>{
  const verdict=check('pull_request_target',condition);
  assert.equal(verdict.status,'INCOMPLETE',JSON.stringify(verdict));
 });
}
