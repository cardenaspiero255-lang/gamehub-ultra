'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {attestCi}=require('./ultra_sentinel_frontier_integrity.cjs');
const SHA='a'.repeat(40),REPO='cardenaspiero255-lang/gamehub-ultra';
const TRUST={'Android build':{id:11,path:'.github/workflows/android.yml'},
 'Unit Test Coverage':{id:12,path:'.github/workflows/coverage.yml'}};
function workflow(event,expr,githubScript=false){
 const head=['on: '+event,'jobs:','  verify:','    runs-on: ubuntu-latest','    steps:'];
 const script=githubScript?['      - uses: actions/github-script@'+SHA,'        with:',
  '          script: core.info("'+expr+'")']:['      - run: echo "'+expr+'"'];
 return [...head,...script].join('\n');
}
function run(name,overrides={}){
 const isAndroid=name==='Android build',id=isAndroid?101:201;
 return {id,run_number:7,run_attempt:1,name,head_sha:SHA,
  event:'pull_request',head_branch:'feature/test',status:'completed',conclusion:'success',
  workflow_id:isAndroid?11:12,
  path:isAndroid?'.github/workflows/android.yml':'.github/workflows/coverage.yml',
  repository:{full_name:REPO},head_repository:{full_name:REPO},...overrides};
}
function ci(runs){return attestCi({sha:SHA,repo:REPO,runs,apiComplete:true,
 trustedWorkflows:TRUST,changedFiles:[],changedFilesComplete:true})}
test('ROOT-1: every untrusted event data shape in privileged executable code is BLOCKER or INCOMPLETE',()=>{
 const expressions=[
  '${{ github.event.issue }}',
  '${{ github.event.issue.title }}',
  '${{ github.event.issue.user.login }}',
  '${{ github.event.issue.assignee.login }}',
  '${{ github.event.issue.labels.*.name }}',
  '${{ github.event.issue.* }}',
  '${{ github.event.issue["title"] }}',
  '${{ github.event.issue[format("body")] }}',
  '${{ toJSON(github.event.issue) }}',
  '${{ toJSON(github.event.issue.*) }}',
  '${{ fromJSON(toJSON(github.event.issue)).title }}',
  '${{ join(github.event.issue.labels.*.name, ",") }}',
  '${{ format("{0}",github.event.issue.title) }}',
  '${{ github.event.client_payload.deep.item.title }}',
  '${{ github.event.release.author.login }}',
  '${{ github.event.workflow_run.display_title }}',
  '${{ github.event.sender.login }}',
  '${{ github.head_ref }}',
  '${{ inputs.somevalue }}',
  '${{ github.ref }}',
  '${{ steps.read.outputs.issue_title }}'
 ];
 for(const executor of [false,true]){
  for(const expr of expressions){
   const w=workflow('issues',expr,executor);
   const result=inspectWorkflow(w);
   assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify({expr,executor,result}));
   assert.ok(result.status==='INCOMPLETE'||result.findings.some(f=>f.severity==='BLOCKER'),
    JSON.stringify({expr,executor,result}));
  }
 }
});
test('ROOT-1: safe fixed GitHub scalars remain clean on privileged scripts',()=>{
 const expressions=[
  '${{ github.sha }}','${{ github.run_id }}','${{ github.run_attempt }}',
  '${{ github.event.issue.number }}','${{ github.event.pull_request.number }}',
  '${{ github.event_name }}','${{ toJSON(github.event.issue.number) }}'
 ];
 for(const executor of [false,true])for(const expr of expressions){
  const result=inspectWorkflow(workflow('issues',expr,executor));
  assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify({expr,executor,result}));
 }
});
test('ROOT-1: complete expression coverage for long, malformed and nested strings',()=>{
 for(const expr of [
  '${{ format("'+'x'.repeat(1200)+'{0}", github.event.issue.title) }}',
  '${{ github.event.issue.title ',
  '${{ github.event.issue.title }} ${{',
  '${{ github.event.issue["title"] }}',
  '${{ format("{0}",github.event.issue.body) }}'
 ])for(const executor of [false,true]){
  const result=inspectWorkflow(workflow('issues',expr,executor));
  assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify({expr:expr.slice(0,80),executor,result}));
 }
});
test('ROOT-2: unorderable or untrusted records cannot let older Android green pass',()=>{
 const green=run('Android build',{id:100,run_number:7,run_attempt:1});
 const coverage=run('Unit Test Coverage');
 for(const bad of [
  {id:102,run_number:undefined,run_attempt:1},
  {id:102,run_number:8,run_attempt:undefined},
  {id:undefined,run_number:8,run_attempt:1},
  {id:102,run_number:8,run_attempt:1,workflow_id:99},
  {id:102,run_number:8,run_attempt:1,path:'.github/workflows/not-android.yml'},
  {id:102,run_number:8,run_attempt:1,event:'workflow_dispatch'},
  {id:102,run_number:8,run_attempt:1,repository:{full_name:'evil/fork'}},
  {id:102,run_number:8,run_attempt:1,head_repository:null},
  {id:102,run_number:8,run_attempt:1,status:'invalid'},
  {id:102,run_number:8,run_attempt:1,conclusion:'failure'}
 ]) {
  const result=ci([green,coverage,run('Android build',bad)]);
  assert.notEqual(result.status,'PASS',JSON.stringify({bad,result}));
 }
});
test('ROOT-2: invalid latest Coverage or contradictory duplicate metadata never passes',()=>{
 const android=run('Android build'),old=run('Unit Test Coverage',{id:200,run_number:7});
 for(const bad of [
  {id:202,run_number:8,run_attempt:undefined},
  {id:202,run_number:8,run_attempt:1,status:'in_progress',conclusion:null},
  {id:200,run_number:7,run_attempt:1,conclusion:'failure'},
  {id:202,run_number:8,run_attempt:1,workflow_id:999}
 ]){
  const result=ci([android,old,run('Unit Test Coverage',bad)]);
  assert.notEqual(result.status,'PASS',JSON.stringify({bad,result}));
 }
});
test('ROOT-2: out-of-order fresh green and stale red uses newest verified attempt',()=>{
 const android=run('Android build');
 const coverage=run('Unit Test Coverage');
 const result=ci([run('Android build',{id:99,run_number:6,conclusion:'failure'}),
   coverage,android]);
 assert.equal(result.status,'PASS',JSON.stringify(result));
});
