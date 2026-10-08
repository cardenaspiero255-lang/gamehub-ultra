'use strict';
const test=require('node:test'), assert=require('node:assert/strict');
const {buildReport,serialize,parseComment}=require('./ultra_sentinel_report.cjs');
const sha='a'.repeat(40);
const sample=()=>({pr:167,sha,analysis:{
 sha,engine:'Ultra Sentinel Core',version:'2.0.0',
 coverage:{returned:1,analyzed:1,partial:false},
 findings:[{rule:'SPEECH_REENTRANT_RETRY',severity:'HIGH',confidence:'medium',
 path:'app/src/main/java/Voice.kt',line:12,reason:'callback restarts listener',verification:'test 100 callbacks'}],
 remediations:{suggestions:[{rule:'SPEECH_REENTRANT_RETRY',path:'app/src/main/java/Voice.kt',line:12,
 title:'Defer callback',steps:['Use retry gate'],test:'100 callbacks',caution:'Verify lifecycle',handoff:'Inspect source first'}]}
}});
test('round-trip machine report',()=>{
 const report=buildReport(sample());
 assert.deepEqual(parseComment(serialize(report),{pr:167,sha}),report);
});
test('stale commit rejected',()=>{
 assert.equal(parseComment(serialize(buildReport(sample())),{pr:167,sha:'b'.repeat(40)}),null);
});
test('wrong PR rejected',()=>{
 assert.equal(parseComment(serialize(buildReport(sample())),{pr:168,sha}),null);
});
test('report tied to analysis SHA',()=>{
 const x=sample();x.sha='b'.repeat(40);assert.throws(()=>buildReport(x));
});
test('machine repair preserves line and reproducibility',()=>{
 const r=buildReport(sample());assert.equal(r.findings[0].location.line,12);
 assert.equal(r.findings[0].repair.test,'100 callbacks');
});
test('partial scan cannot be called clean',()=>{
 const x=sample();x.analysis.coverage.partial=true;x.analysis.findings=[];
 assert.equal(buildReport(x).status,'incomplete');
});
test('missing marker does not produce report',()=>{
 assert.equal(parseComment('No machine report',{pr:167,sha}),null);
});
test('comment parser finds embedded report',()=>{
 const block=serialize(buildReport(sample()));
 assert.equal(parseComment('Human review\n'+block,{pr:167,sha}).pr,167);
});
test('findings are bounded in reports',()=>{
 const x=sample();x.analysis.findings=Array(19).fill(x.analysis.findings[0]);
 const r=buildReport(x);assert.equal(r.findings.length,10);assert.equal(r.count,19);
});
test('requires valid report shape',()=>{
 assert.throws(()=>buildReport({pr:0,sha,analysis:sample().analysis}));
});
