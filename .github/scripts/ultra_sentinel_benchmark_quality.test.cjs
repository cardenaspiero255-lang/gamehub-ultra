'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {evaluate}=require('./ultra_sentinel_comparative.cjs');
const SHA='a'.repeat(40);
const positive={rule:'UNSCOPED_COROUTINE',path:'app/src/main/java/Sample.kt'};
const issue={rule:'FORCED_GC',path:'app/src/main/java/Sample.kt'};
const items=[
 {id:'case-positive-a',sha:SHA,expected:[positive],humanReviewed:true,unseenAtTraining:true},
 {id:'case-positive-b',sha:SHA,expected:[issue],humanReviewed:true,unseenAtTraining:true},
 {id:'case-negative-a',sha:SHA,expected:[],humanReviewed:true,unseenAtTraining:true},
 {id:'case-negative-b',sha:SHA,expected:[],humanReviewed:true,unseenAtTraining:true}
];
const record=(caseId,findings,durationMs)=>({provider:'ultra-sentinel',caseId,sha:SHA,findings,durationMs,costUsd:0});
test('US-032: explicit negative-case false-positive rate and p95 latency are independently calculable',()=>{
 const reports=[
 record('case-positive-a',[positive],10),
 record('case-positive-b',[],20),
 record('case-negative-a',[issue],30),
 record('case-negative-b',[],40)
 ];
 const m=evaluate(items,reports,{expectedSha:SHA}).results['ultra-sentinel'];
 assert.equal(m.negativeCasesReviewed,2);
 assert.equal(m.negativeCasesFlagged,1);
 assert.equal(m.negativeCaseFalsePositiveRate,.5);
 assert.equal(m.latencyP95Ms,40);
 assert.equal(m.tp,1);assert.equal(m.fp,1);assert.equal(m.fn,1);
 assert.equal(require('./ultra_sentinel_comparative.cjs').metric(0,1,1).f1,0);
});
test('US-032: no negative-case or latency evidence is never represented as zero',()=>{
 const a=evaluate([items[0]],[record('case-positive-a',[positive],5)],{expectedSha:SHA}).results['ultra-sentinel'];
 assert.equal(a.negativeCasesReviewed,0);
 assert.equal(a.negativeCaseFalsePositiveRate,null);
 assert.equal(a.latencyP95Ms,5);
 const b=evaluate([items[2]],[{provider:'ultra-sentinel',caseId:items[2].id,sha:SHA,findings:[]}],{expectedSha:SHA}).results['ultra-sentinel'];
 assert.equal(b.negativeCaseFalsePositiveRate,0);
 assert.equal(b.latencyP95Ms,null);
});

// Differential benchmark improvement: compare actual Codex review evidence,
// never presume Codex outputs or rank unreviewed cases as successes.
test('US-032 supports independently supplied Codex review reports without inferred outcomes',()=>{
 const pos={provider:'codex',caseId:'case-positive-a',sha:SHA,findings:[positive],durationMs:10};
 const neg={provider:'codex',caseId:'case-negative-a',sha:SHA,findings:[],durationMs:12};
 const out=evaluate([items[0],items[2]],[pos,neg],{expectedSha:SHA});
 assert.equal(out.results.codex.tp,1);
 assert.equal(out.results.codex.fn,0);
 assert.equal(out.results.codex.fp,0);
 assert.equal(out.results.codex.complete,true);
 assert.equal(out.comparisonsJustified,false);
});
test('US-032 full head-to-head requires Codex and Sentinel reports for every same case',()=>{
 const selected=[items[0],items[2]];
 const reports=[
  {...record('case-positive-a',[positive],10)},
  {...record('case-negative-a',[],12)},
  {provider:'codex',caseId:'case-positive-a',sha:SHA,findings:[],durationMs:10},
  {provider:'codex',caseId:'case-negative-a',sha:SHA,findings:[],durationMs:12}
 ];
 const out=evaluate(selected,reports,{expectedSha:SHA});
 assert.equal(out.comparisonsJustified,true);
 assert.equal(out.results.codex.fn,1);
 assert.equal(out.results['ultra-sentinel'].tp,1);
 const partial=evaluate(selected,reports.slice(0,3),{expectedSha:SHA});
 assert.equal(partial.comparisonsJustified,false);
});
