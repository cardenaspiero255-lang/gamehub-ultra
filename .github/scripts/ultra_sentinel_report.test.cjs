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

test('never attaches a solution for a different rule or file',()=>{
 const x=sample();
 x.analysis.remediations.suggestions[0].rule='UNSCOPED_COROUTINE';
 const f=buildReport(x).findings[0];
 assert.equal(f.repair,null);
});
test('signals machine-report truncation separately from diff completeness',()=>{
 const x=sample();x.analysis.findings=Array(13).fill(x.analysis.findings[0]);
 const out=buildReport(x);
 assert.equal(out.reportTruncated,true);
 assert.equal(out.partial,false);
});

test('machine report labels heuristic playbooks rather than falsely verified fixes',()=>{
 const x=sample();
 x.analysis.remediations.suggestions[0].relatedEvidence=[
  {id:'pattern-voice-retry',type:'playbook',verification:'curated_playbook',advice:'Use retry gate'},
  {id:'fake-historic',type:'verified_fix',verification:'confirmed_tests',advice:'Based on a verified SHA',
    evidenceUrl:'https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/161'}
 ];
 const saved=parseComment(serialize(buildReport(x)),{pr:167,sha});
 const mem=saved.findings[0].repair.memory;
 assert.equal(mem[0].verification,'curated_playbook');
 assert.equal(mem[0].source,null);
 assert.equal(mem[1].type,'verified_fix');
 assert.ok(mem[1].source.startsWith('https://github.com/'));
});
