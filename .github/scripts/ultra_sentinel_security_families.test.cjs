'use strict';
/* Root-cause regression matrix: additions, moves, copies and missing CI
 * dependency protection must never silently certify a security change. */
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {evaluateProtectedChanges}=require('./ultra_sentinel_policy.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const entry=(filename,status='modified',previous_filename)=>({
 filename,status,...(previous_filename?{previous_filename}:{})
});
const check=(files)=>evaluateProtectedChanges(files,{expectedCount:files.length});
const guarded=[
 '.github/scripts/ultra_sentinel_report.cjs',
 '.github/scripts/ultra_sentinel_causal.cjs',
 '.github/scripts/ultra_sentinel_feedback.cjs',
 '.github/scripts/ultra_sentinel_remediation.cjs',
 '.github/scripts/ultra_sentinel_memory.cjs',
 '.github/scripts/ultra_sentinel_selfreview.cjs',
 '.github/scripts/ultra_sentinel_future_module.cjs',
 '.github/scripts/install_sentinel_hook.sh',
 '.github/actions/trusted/action.yml',
 '.github/workflows/never-before-seen.yml',
 '.github/workflows/new-gate.yaml',
 '.github/sentinel-contracts/contract.json'
];
for(const filename of guarded){
 test('Protect all critical GitHub automation surfaces: '+filename,()=>{
  assert.equal(check([entry(filename)]).status,'REVIEW_REQUIRED');
  assert.equal(check([entry(filename,'removed')]).status,'BLOCKED');
 });
}
for(const file of ['.github/scripts/helper.cjs','.github/workflows/new.yml',
 '.github/actions/another/action.yml','package-lock.json']){
 test('Non-critical filename renamed INTO protected destination requires approval: '+file,()=>{
  assert.notEqual(check([entry(file,'renamed','docs/normal.txt')]).status,'OK');
 });
}
test('Moved critical source away remains blocked',()=>{
 assert.equal(check([entry('docs/archive.txt','renamed','.github/scripts/ultra_sentinel_core.cjs')]).status,'BLOCKED');
});
test('A copied critical source still requires independent review',()=>{
 assert.notEqual(check([entry('docs/snapshot.txt','copied','.github/scripts/ultra_sentinel_core.cjs')]).status,'OK');
});
test('Duplicate GitHub file entries cannot pretend to represent an entire PR',()=>{
 const response=check([entry('README.md'),entry('README.md')]);
 assert.equal(response.status,'INCOMPLETE');
});
test('Fixed js-yaml 4.x version, manifest/lock parity, integrity-provenanced package',()=>{
 const pkg=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../package.json'),'utf8'));
 const lock=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../package-lock.json'),'utf8'));
 assert.equal(pkg.dependencies['js-yaml'],'4.3.2');
 assert.equal(lock.packages[''].dependencies['js-yaml'],'4.3.2');
 const dep=lock.packages['node_modules/js-yaml'];
 assert.equal(dep.version,'4.3.2');
 assert.equal(dep.resolved,'https://registry.npmjs.org/js-yaml/-/js-yaml-4.3.2.tgz');
 assert.equal(dep.integrity,'sha512-SFNOvSJ+Dgf/9An904Yx+CgSlIPCkIpao4qo51lpee25TIRejdH3rhR4EZMGoNx3/TP3O+wzWuiTFl4sqbltzA==');
});
test('Local actions used by a privileged job require review of action metadata and scripts',()=>{
 const src=['on: pull_request_target','permissions: {contents: read}',
 'jobs:','  review:','    runs-on: ubuntu-latest','    steps:',
 '      - uses: ./.github/actions/third-party-wrapper'].join('\n');
 assert.notEqual(inspectWorkflow(src).status,'NO_RISK_PATTERN');
});
test('Full nested local actions and traversal-like path are not clean',()=>{
 const src=['on: workflow_dispatch','permissions: {contents: read}','jobs:',
 '  review:','    runs-on: ubuntu-latest','    steps:',
 '      - uses: ./../untrusted/action'].join('\n');
 assert.notEqual(inspectWorkflow(src).status,'NO_RISK_PATTERN');
});

test('YAML merge explosion before post-parse graph limits is rejected by loader budget',()=>{
 const src=['on: workflow_dispatch','permissions: read-all',
 'jobs:','  audit:','    runs-on: ubuntu-latest','    steps:',
 '      - run: echo safe','base: &node { x: 1 }',
 'fused: { <<: ['+Array(25).fill('*node').join(', ')+'] }'].join('\n');
 const result=inspectWorkflow(src);
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
});
test('Uninspected local reusable workflow is never considered verified',()=>{
 const src=['on: push','permissions: read-all','jobs:',
 '  audit:','    uses: ./.github/workflows/local.yml'].join('\n');
 assert.notEqual(inspectWorkflow(src).status,'NO_RISK_PATTERN');
});
