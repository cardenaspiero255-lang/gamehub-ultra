'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const SHA='a'.repeat(40),FILE='.github/workflows/omega-security.yml';
const PIN='actions/checkout@'+'f'.repeat(40),OTHER='org/other@'+'d'.repeat(40);
const X='$'+'{{ github.head_ref }}',INDEX="$"+"{{ github['head_ref'] }}";
const INDEX_DEEP='$'+"{{ github.event.pull_request.head['sha'] }}";
const check=source=>reviewWorkflows({sha:SHA,expected:[FILE],sources:{[FILE]:source}});
const has=(report,rule)=>report.findings.some(f=>f.rule===rule);
const start='on: pull_request_target\njobs:\n  audit:\n    runs-on: ubuntu-latest\n    steps:\n';
test('OMEGA P1: bracket-index GitHub expressions must not bypass privileged checkout alert',()=>{
 for(const ref of [INDEX,INDEX_DEEP,'refs/heads/'+INDEX,'$'+"{{ github['event']['pull_request']['head']['sha'] }}"]){
  const report=check(start+'      - uses: '+PIN+'\n        with:\n          ref: "'+ref+'"\n');
  assert.ok(has(report,'PRIVILEGED_PR_CODE_CHECKOUT'),ref+' => '+report.status);
 }
});
test('OMEGA P1: flow-map steps detect unpinned actions and untrusted ref',()=>{
 const variants=[
 '      - {uses: actions/checkout@v6, with: {ref: "'+X+'"}}\n',
 '      - {"uses": '+PIN+', "with": {"fetch-depth": 0, "ref": "'+INDEX+'"}}\n',
 '      - {name: read-only, with: {ref: "'+INDEX_DEEP+'"}, uses: '+PIN+'}\n'
 ];
 for(const step of variants){
  const report=check(start+step);
  assert.ok(has(report,'PRIVILEGED_PR_CODE_CHECKOUT'),step+' => '+report.status);
  if(step.includes('@v6'))assert.ok(has(report,'UNPINNED_ACTION'));
 }
});
test('OMEGA P2: artifact-metadata/code-quality privileges produce warnings',()=>{
 for(const scope of ['artifact-metadata','code-quality']){
  for(const stmt of ['permissions:\n  '+scope+': write\n','permissions: {"'+scope+'": write}\n']){
   assert.ok(has(check('on: push\n'+stmt),'PRIVILEGED_WRITE_TOKEN'),stmt);
  }
 }
});
test('OMEGA P2: never interpret env, inputs or run-block uses as action',()=>{
 const source='on: pull_request_target\njobs:\n  build:\n    env:\n      uses: harmless\n'+
 '    steps:\n      - name: safe checkout\n        uses: '+PIN+'\n        with:\n          ref: main\n'+
 '      - name: normal user input\n        uses: '+OTHER+'\n        with:\n          uses: attacker/unpinned@v1\n'+
 '      - name: harmless script\n        run: |\n          uses: attacker/unpinned@v1\n          echo hi\n';
 const report=check(source);
 assert.ok(!has(report,'UNPINNED_ACTION'),JSON.stringify(report));
 assert.ok(!has(report,'PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(report));
});
const triggers=[
 'on: pull_request_target\n','on: [push, "pull_request_target"]\n',
 'on:\n  pull_request_target:\n    types: [opened]\n','on: >-\n  pull_request_target\n'
];
const refs=[X,INDEX,INDEX_DEEP,'refs/heads/'+INDEX];
const sameRef=['main','refs/heads/main',SHA];
function rand(state){return (Math.imul(state,1664525)+1013904223)>>>0;}
test('OMEGA 4096 seeded malicious combinations retain security detection',()=>{
 let seed=0x5eedbabe,miss=0;
 for(let i=0;i<4096;i++){
  seed=rand(seed);const event=triggers[seed%4];
  seed=rand(seed);const ref=refs[seed%4];
  seed=rand(seed);const variant=seed%4;
  const step=[
   '      - uses: '+PIN+'\n        with:\n          ref: "'+ref+'"\n',
   '      - "uses": '+PIN+'\n        with:\n          ref: "'+ref+'"\n',
   '      - {uses: '+PIN+', with: {ref: "'+ref+'"}}\n',
   '      - {name: safety-test, with: {fetch-depth: 0, ref: "'+ref+'"}, uses: '+PIN+'}\n'
  ][variant];
  const result=check(event+'jobs:\n  a:\n    steps:\n'+step);
  if(!has(result,'PRIVILEGED_PR_CODE_CHECKOUT'))miss++;
 }
 assert.equal(miss,0,'missed dangerous checkout: '+miss);
});
test('OMEGA 4096 seeded benign combinations avoid false blocker and false unpinned action',()=>{
 let seed=0x42c0ffee,errors=0;
 for(let i=0;i<4096;i++){
  seed=rand(seed);const event=triggers[seed%4];
  seed=rand(seed);const ref=sameRef[seed%3];
  seed=rand(seed);const variant=seed%4;
  const safe=['      - uses: '+PIN+'\n        with:\n          ref: '+ref+'\n',
   '      - {"uses": '+PIN+', "with": {"ref": "'+ref+'"}}\n',
   '      - {name: safe, with: {ref: "'+ref+'"}, uses: '+PIN+'}\n',
   '      - name: safe\n        uses: '+PIN+'\n        with:\n          ref: '+ref+'\n'][variant];
  const fake='      - name: innocent\n        uses: '+OTHER+'\n        with:\n          uses: danger/unpinned@v3\n          ref: "'+INDEX+'"\n';
  const result=check(event+'jobs:\n  a:\n    steps:\n'+safe+fake);
  if(has(result,'PRIVILEGED_PR_CODE_CHECKOUT')||has(result,'UNPINNED_ACTION'))errors++;
 }
 assert.equal(errors,0,'incorrectly blocked benign workflow: '+errors);
});
test('OMEGA 2048 permission and misleading-input mutations are scoped',()=>{
 let seed=0xc1a055ed,errors=0;
 const perms=['artifact-metadata','code-quality','contents','checks'];
 for(let i=0;i<2048;i++){
  seed=rand(seed);const scope=perms[seed%4];
  seed=rand(seed);const isReal=seed%2===0;
  seed=rand(seed);const quoted=seed%2===0;
  const key=quoted?'"'+scope+'"':scope;
  const body=isReal?'permissions:\n  '+key+': write\n':
   'on: push\njobs:\n  a:\n    env:\n      '+key+': write\n';
  const result=check(body);
  if(has(result,'PRIVILEGED_WRITE_TOKEN')!==isReal)errors++;
 }
 assert.equal(errors,0,'misclassified permission: '+errors);
});
