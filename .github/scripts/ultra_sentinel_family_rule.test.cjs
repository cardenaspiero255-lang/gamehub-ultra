'use strict';
// Contract for strict error-family closure; claims are not independent CI attestations.
const test=require('node:test'),assert=require('node:assert/strict');
const {evaluateFamilyResolution,evaluateRepairGate}=require('./ultra_sentinel_incidents.cjs');
const A='a'.repeat(40),B='b'.repeat(40);
const variant=(id,kind,overrides={})=>({
 id,kind,testFile:'.github/scripts/ultra_sentinel_family_rule.test.cjs',
 status:'passed_after_fix',...overrides
});
const family=(overrides={})=>({
 id:'BASH_WRAPPER_BYPASS',
 sha:A,
 rootCause:'The bounded shell command analyzer misclassified nested command wrappers.',
 scope:'All known literal command wrappers and quoted option variants in GitHub workflows.',
 variants:[
  variant('original-repro','original',{red:'failed_before_fix'}),
  variant('nested-alternate','alternate'),
  variant('upper-boundary','boundary'),
  variant('harmless-control','benign_control')
 ],
 unresolvedConfirmed:0,
 knownGaps:[],
 unknownSyntax:'fail_closed',
 ...overrides
});
const approved=()=>({
 sha:A,currentSha:A,proposedBy:'bot',approvedBy:'human-reviewer',
 approval:'approved',tests:{red:'failed_before_fix',green:'passed_after_fix'},
 checks:{'Android build':'success','Unit Test Coverage':'success','Ultra Sentinel Core Tests':'success'},
 independentReview:'approved',familyEvidence:[family()]
});
test('strict family rule rejects green CI and human sign-off without family coverage',()=>{
 const request=approved();delete request.familyEvidence;
 const verdict=evaluateRepairGate(request);
 assert.equal(verdict.status,'BLOCKED');
 assert.ok(verdict.reasons.some(x=>x.startsWith('error_family_')));
});
test('complete bounded family evidence permits human review, never automatic merge',()=>{
 const result=evaluateRepairGate(approved());
 assert.equal(result.status,'READY_FOR_HUMAN_MERGE');
 assert.equal(result.autoMerge,false);
 assert.equal(evaluateFamilyResolution([family()],{sha:A}).status,'VERIFIED_CLAIM');
});
for(const [name,change] of [
 ['stale family SHA',{sha:B}],
 ['missing causal analysis',{rootCause:'fixed'}],
 ['missing precise scope',{scope:''}],
 ['unresolved variant',{unresolvedConfirmed:1}],
 ['unknown gap',{knownGaps:['alternate shell syntax remains unhandled']}],
 ['unsupported syntax accepted',{unknownSyntax:'assume_safe'}],
 ['missing original RED',{variants:[
  variant('original-repro','original'),variant('nested-alternate','alternate'),
  variant('upper-boundary','boundary'),variant('harmless-control','benign_control')]}],
 ['no benign negative control',{variants:[
  variant('original-repro','original',{red:'failed_before_fix'}),
  variant('nested-alternate','alternate'),variant('upper-boundary','boundary')]}],
 ['same regression repeated',{variants:Array.from({length:4},()=>variant('same-id','alternate'))}],
 ['unverified variant',{variants:[
  variant('original-repro','original',{red:'failed_before_fix'}),
  variant('nested-alternate','alternate',{status:'pending'}),
  variant('upper-boundary','boundary'),variant('harmless-control','benign_control')]}],
 ['invalid test-file path',{variants:[
  variant('original-repro','original',{red:'failed_before_fix'}),
  variant('nested-alternate','alternate',{testFile:'../untrusted.js'}),
  variant('upper-boundary','boundary'),variant('harmless-control','benign_control')]}],
]){
 test('error-family gate refuses '+name,()=>{
  const evidence=[family(change)];
  assert.equal(evaluateFamilyResolution(evidence,{sha:A}).status,'BLOCKED');
  const verdict=evaluateRepairGate({...approved(),familyEvidence:evidence});
  assert.equal(verdict.status,'BLOCKED');
  assert.ok(verdict.reasons.some(x=>x.startsWith('error_family_')));
 });
}
test('every reported family must close, not just the first',()=>{
 assert.equal(evaluateFamilyResolution([family(),family({
  id:'BASH_REDIRECT_TAINT',unresolvedConfirmed:1
 })],{sha:A}).status,'BLOCKED');
});
