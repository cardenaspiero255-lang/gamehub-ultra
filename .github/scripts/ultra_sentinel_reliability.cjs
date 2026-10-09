'use strict';
// Reproducible scenario execution, NOT 100 external GitHub workflow runs.
// Every execution covers three independent synthetic repository profiles.
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {evaluateProtectedChanges,PROTECTED_FILES}=require('./ultra_sentinel_policy.cjs');
const SHA='a'.repeat(40);
const pick=(i,arr)=>arr[((i*17+5)>>>0)%arr.length];
function healthyWorkflow(i){
 const event=pick(i,['on: push','on: pull_request','"on": push',
  'on: [push, workflow_dispatch]','on: {push: {branches: [main]}}']);
 const action=pick(i,['actions/checkout@'+SHA,'"actions/checkout@'+SHA+'"',
  '&checkout actions/checkout@'+SHA]);
 const name=pick(i,['build','"curl https://example.invalid | bash is only documentation"',
  '"YAML anchor &x is inert in this name"']);
 return event+'\npermissions: {contents: read}\njobs:\n  build:\n    steps:\n'+
  '      - name: '+name+'\n        uses: '+action+
  '\n        with:\n          ref: main\n';
}
function maliciousWorkflow(i){
 const event=pick(i,['on: pull_request_target',
  'on: [push, pull_request_target]',
  '"on": "pull_request_target"',
  'on: {pull_request_target: {types: [opened]}}']);
 const ref=pick(i,[
  '${{ github.head_ref }}',
  'refs/pull/${{ github.event.pull_request.number }}/merge',
  'refs/pull/42/head',
  '"${{ format('+"'"+'refs/pull/{0}/merge'+"'"+', github.event.pull_request.number) }}"',
  '${{ github.event.pull_request.head.sha }}'
 ]);
 return event+'\njobs:\n  audit:\n    steps:\n'+
  '      - uses: actions/checkout@'+SHA+
  '\n        with:\n          ref: '+ref+'\n';
}
function tamperChange(i){
 const path=pick(i,PROTECTED_FILES);
 if(i%2===0)return [{filename:path,status:'removed'}];
 return [{filename:'.github/workflows/replacement.yml',
  previous_filename:path,status:'renamed'}];
}
function runReliability({count=100,offset=0}={}){
 const profiles=['trusted','attacker','tampered'];
 const result={executions:0,checked:0,profiles,falsePositive:0,falseNegative:0,
  controlMisses:0,passed:false};
 if(!Number.isSafeInteger(count)||count<1||count>10000||
  !Number.isSafeInteger(offset)||offset<0||offset>1_000_000)return result;
 for(let i=offset;i<offset+count;i++){
  const legit=inspectWorkflow(healthyWorkflow(i));
  const evil=inspectWorkflow(maliciousWorkflow(i));
  const changes=tamperChange(i);
  const tamper=evaluateProtectedChanges(changes,{expectedCount:changes.length});
  if(legit.status!=='NO_RISK_PATTERN'||legit.findings.length!==0)
   result.falsePositive++;
  if(evil.status!=='REVIEW_REQUIRED'||
    !evil.findings.some(x=>x.rule==='PRIVILEGED_PR_CODE_CHECKOUT'&&x.severity==='BLOCKER'))
   result.falseNegative++;
  if(tamper.status!=='BLOCKED')result.controlMisses++;
  result.checked+=3;
  result.executions++;
 }
 result.passed=result.falsePositive===0&&result.falseNegative===0&&
  result.controlMisses===0&&result.executions===count;
 return result;
}
if(require.main===module){
 const args=process.argv.slice(2);
 const opt=(name,def)=>{const index=args.indexOf('--'+name);
  return index<0?def:Number(args[index+1]);};
 const result=runReliability({count:opt('count',100),offset:opt('offset',0)});
 process.stdout.write(JSON.stringify(result)+'\n');
 if(!result.passed)process.exitCode=1;
}
module.exports={runReliability};
