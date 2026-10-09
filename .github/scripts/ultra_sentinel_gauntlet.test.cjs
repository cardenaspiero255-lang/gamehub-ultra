'use strict';
// ULTRA SENTINEL GAUNTLET: 954 deterministic adversarial scenarios.
// No network, repository mutation, secrets, execution of PR code or LLM judge.
const test=require('node:test'),assert=require('node:assert/strict');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const {attestCi}=require('./ultra_sentinel_frontier_integrity.cjs');
const {buildSanitizedReport,renderActionsSummary}=require('./ultra_sentinel_sentry_ingest.cjs');
const SHA='a'.repeat(40),SHA2='b'.repeat(40),REPO='cardenaspiero255-lang/gamehub-ultra';
const FILE='.github/workflows/gauntlet.yml',CHECKOUT='actions/checkout@'+'f'.repeat(40),OTHER='owner/action@'+'e'.repeat(40);
const TRUST={'Android build':{id:101,path:'.github/workflows/android.yml'},'Unit Test Coverage':{id:202,path:'.github/workflows/coverage.yml'}};
const analyze=body=>reviewWorkflows({sha:SHA,expected:[FILE],sources:{[FILE]:body}});
const includes=(out,rule)=>out.findings.some(f=>f.rule===rule);
const eventForms=[
 'on: pull_request_target\n','on: [pull_request_target, push]\n',
 'on: ["pull_request_target", push]\n',"on: ['pull_request_target', push]\n",
 'on:\n  pull_request_target:\n    types: [opened]\n',
 'on:\n  "pull_request_target":\n    types: [opened]\n',
 "on:\n  'pull_request_target':\n    types: [opened]\n",
 '"on": [workflow_run]\n'
];
const refExpressions=[
 '$'+'{{ github.event.pull_request.head.sha }}',
 '$'+'{{ github.event.pull_request.head.ref }}',
 '$'+'{{ github.head_ref }}',
 '$'+'{{  github.event.pull_request.head.sha  }}'
];
const refForms=expr=>[
 '          ref: '+expr+'\n',
 '          ref: "'+expr+'"\n',
 "          ref: '"+expr+"'\n",
 '          ref: >-\n            '+expr+'\n',
 '        with: {ref: "'+expr+'"}\n'
];
const prefixForms=[
 '',
 '      - uses: '+CHECKOUT+'\n        with:\n          ref: main\n',
 '      - name: run trusted action\n        uses: '+OTHER+'\n        with:\n          ref: main\n'
];
const dangerousStep=(expr,refIndex)=>'      - name: source checkout\n        uses: '+CHECKOUT+'\n'+
 (refIndex===4?'':'        with:\n')+refForms(expr)[refIndex];
const benignStep=expr=>'      - name: another action\n        uses: '+OTHER+'\n        with:\n          ref: "'+expr+'"\n';
test('GAUNTLET detects all 480 forged privileged checkout scenarios',()=>{
 const fails=[];let cases=0;
 for(const ev of eventForms)for(const expr of refExpressions)for(let rf=0;rf<5;rf++)
 for(const before of prefixForms){
  const body=ev+'jobs:\n  audit:\n    steps:\n'+before+dangerousStep(expr,rf);
  const out=analyze(body);cases++;
  if(!includes(out,'PRIVILEGED_PR_CODE_CHECKOUT'))
   fails.push({event:ev.trim(),expression:expr,refForm:rf,prefix:before?'present':'none',status:out.status});
 }
 assert.equal(cases,480);
 assert.deepEqual(fails,[],JSON.stringify({cases,missed:fails.length,samples:fails.slice(0,10)}));
});
test('GAUNTLET 96 benign scenarios do not report a false privileged checkout',()=>{
 const fails=[];let cases=0;
 for(const ev of eventForms)for(const expr of refExpressions)for(let n=0;n<3;n++){
  const safe='      - name: safe checkout\n        uses: '+CHECKOUT+
   '\n        with:\n          ref: '+(['main','refs/heads/main',SHA][n])+'\n';
  const code=ev+'jobs:\n  test:\n    steps:\n'+safe+benignStep(expr);
  const out=analyze(code);cases++;
  if(includes(out,'PRIVILEGED_PR_CODE_CHECKOUT'))fails.push({event:ev.trim(),expr,n});
 }
 assert.equal(cases,96);assert.deepEqual(fails,[]);
});
test('GAUNTLET 50 YAML write-permission variants never produce a clean verdict',()=>{
 const scopes=['contents','actions','attestations','checks','issues','pull-requests','security-events','statuses','packages','deployments'];
 const forms=scope=>[
  'permissions:\n  '+scope+': write\n','permissions:\n  "'+scope+'": write\n',
  "permissions:\n  '"+scope+"': 'write'\n",'permissions: {"'+scope+'": write}\n',
  "permissions: {'"+scope+"': write}\n"
 ];let cases=0;const misses=[];
 for(const scope of scopes)for(const code of forms(scope)){
  cases++;const r=analyze('on: push\n'+code);
  if(!includes(r,'PRIVILEGED_WRITE_TOKEN'))misses.push({scope,code});
 }
 assert.equal(cases,50);assert.deepEqual(misses,[]);
});
const run=(name,extra={})=>({name,head_sha:SHA,workflow_id:TRUST[name].id,path:TRUST[name].path,
 id:TRUST[name].id*100,run_number:1,run_attempt:1,event:'pull_request',status:'completed',
 conclusion:'success',repository:{full_name:REPO},head_repository:{full_name:REPO},...extra});
test('GAUNTLET 48 modified or forged CI runs are never PASS',()=>{
 const attacks=[
 ['sha',{head_sha:SHA2}],['workflow ID',{workflow_id:404}],['workflow path',{path:'.github/workflows/evil.yml'}],
 ['repo',{repository:{full_name:'other/fork'}}],['head repo',{head_repository:{full_name:'other/fork'}}],
 ['event',{event:'repository_dispatch'}],['status',{status:'queued',conclusion:null}],
 ['failure',{conclusion:'failure'}],['cancel',{conclusion:'cancelled'}],['run id',{id:-4}],
 ['attempt',{run_attempt:0}],['number',{run_number:0}]
 ];let cases=0;const failures=[];
 for(const [label,override] of attacks)for(const targeted of ['Android build','Unit Test Coverage'])
 for(const changedFiles of [[],['.github/workflows/other.yml']]){
  cases++;
  const rs=['Android build','Unit Test Coverage'].map(name=>run(name,name===targeted?override:{}));
  const v=attestCi({sha:SHA,repo:REPO,runs:rs,
   apiComplete:true,trustedWorkflows:TRUST,changedFiles,changedFilesComplete:true});
  if(v.status==='PASS')failures.push({label,targeted,changedFiles});
 }
 assert.equal(cases,48);assert.deepEqual(failures,[]);
});
test('GAUNTLET 160 incomplete provenance permutations never authorize a merge',()=>{
 const variants=[
 {changedFiles:['.github/workflows/android.yml']},{changedFiles:['.github/workflows/coverage.yml']},
 {changedFilesComplete:false},{apiComplete:false},{trustedWorkflows:{}},{runs:[]},
 {sha:SHA2},{repo:'other/repo'}
 ];let cases=0;
 for(const override of variants)for(let n=0;n<20;n++){
  const verdict=attestCi({sha:SHA,repo:REPO,runs:[run('Android build'),run('Unit Test Coverage')],
   trustedWorkflows:TRUST,changedFiles:[],changedFilesComplete:true,apiComplete:true,...override});
  cases++;
  assert.notEqual(verdict.status,'PASS',JSON.stringify({override,n}));
  assert.equal(verdict.autoMergeAllowed,false);
 }
 assert.equal(cases,160);
});
test('GAUNTLET 120 synthetic Sentry incidents cannot leak titles, PII or secrets',()=>{
 let cases=0;const misses=[];
 for(let k=1;k<=120;k++){
  const secret='SECRET'+k+'-TOKEN-user'+k+'@sample.test';
  const input=[{id:String(k),project:{slug:'gamehub-ultra'},level:'error',count:'2',
   firstSeen:'2026-10-08T09:00:00Z',lastSeen:'2026-10-08T10:00:00Z',
   title:secret,stacktrace:secret,audio:secret,user:{email:secret},
   tags:[{key:'password',value:secret}]}];
  const report=buildSanitizedReport(input,{consent:true});
  const markdown=renderActionsSummary(report);cases++;
  if(JSON.stringify(report).includes(secret)||markdown.includes(secret))misses.push(k);
  assert.equal(report.assessment.automaticRollback,false);
 }
 assert.equal(cases,120);assert.deepEqual(misses,[]);
});

const unsafeRef='$'+'{{ github.event.pull_request.head.sha }}';
const oneDangerousCheckout= 'jobs:\n  test:\n    steps:\n      - name: checkout\n        uses: '+CHECKOUT+
 '\n        with:\n          ref: '+unsafeRef+'\n';
test('GAUNTLET new: multiline flow list events are privileged (Codex P1)',()=>{
 for(const prefix of [
  'on: [\n  pull_request_target,\n  push\n]\n',
  'on: [\n  "pull_request_target",\n  push\n]\n',
  "on: [\n  'workflow_run'\n]\n",
  'on: {\n  "workflow_run": {}\n}\n'
 ]){
  const out=analyze(prefix+oneDangerousCheckout);
  assert.ok(includes(out,'PRIVILEGED_PR_CODE_CHECKOUT'),prefix+': '+out.status);
 }
});
test('GAUNTLET new: checkout ref before uses cannot hide privilege escalation (Codex P1)',()=>{
 const variants=[
  '      - name: reverse order\n        with:\n          ref: "'+unsafeRef+'"\n        uses: '+CHECKOUT+'\n',
  "      - name: reverse order\n        with:\n          ref: '"+unsafeRef+"'\n        uses: "+CHECKOUT+'\n',
  '      - name: reverse flow mapping\n        with: {ref: "'+unsafeRef+'"}\n        uses: '+CHECKOUT+'\n'
 ];
 for(const code of variants){
  const out=analyze('on: pull_request_target\njobs:\n  test:\n    steps:\n'+code);
  assert.ok(includes(out,'PRIVILEGED_PR_CODE_CHECKOUT'),out.status+': '+code);
 }
});
test('GAUNTLET new: action inputs and env vars do not falsely become GitHub permissions (Codex P2)',()=>{
 const variants=[
  'on: push\njobs:\n  build:\n    env:\n      contents: write\n',
  'on: push\njobs:\n  build:\n    env:\n      "security-events": write\n',
  'on: push\njobs:\n  build:\n    steps:\n      - uses: '+OTHER+'\n        with:\n          permissions: write-all\n',
  'on: push\njobs:\n  build:\n    steps:\n      - uses: '+OTHER+'\n        with:\n          contents: write\n'
 ];
 for(const code of variants){
  const out=analyze(code);
  assert.ok(!includes(out,'PRIVILEGED_WRITE_TOKEN'),JSON.stringify(out));
 }
});
test('GAUNTLET new: actual job-level permissions still generate HIGH review (Codex P2)',()=>{
 for(const permissions of ['contents: write','"checks": write',"'security-events': write"]){
  const code='on: push\njobs:\n  test:\n    permissions:\n      '+permissions+'\n    runs-on: ubuntu-latest\n';
  assert.ok(includes(analyze(code),'PRIVILEGED_WRITE_TOKEN'),permissions);
 }
});
