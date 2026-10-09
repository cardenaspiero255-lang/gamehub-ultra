'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {evaluate}=require('./ultra_sentinel_comparative.cjs');
const SHA='a'.repeat(40),rule={rule:'UNSCOPED_COROUTINE',path:'app/src/main/java/A.kt'};
const corpus=[
 {id:'case-001',sha:SHA,humanReviewed:true,unseenAtTraining:true,expected:[rule]},
 {id:'case-002',sha:SHA,humanReviewed:true,unseenAtTraining:true,expected:[]}
];
const r=(provider,caseId,findings,durationMs)=>({provider,caseId,sha:SHA,findings,durationMs,costUsd:0});
test('precision and recall account for negative and positive human-reviewed cases',()=>{
 const out=evaluate(corpus,[r('ultra-sentinel','case-001',[rule],10),
  r('ultra-sentinel','case-002',[rule],20)],{expectedSha:SHA});
 const s=out.results['ultra-sentinel'];
 assert.equal(s.tp,1);assert.equal(s.fp,1);assert.equal(s.fn,0);
 assert.equal(s.precision,.5);assert.equal(s.recall,1);
 assert.equal(out.comparisonsJustified,false);
});
test('does not invent CodeRabbit or Qodo results if absent',()=>{
 const out=evaluate(corpus,[r('baseline','case-001',[],1)],{expectedSha:SHA});
 assert.equal(out.results.coderabbit,undefined);
 assert.equal(out.results.qodo,undefined);
 assert.equal(out.results.baseline.complete,false);
 assert.equal(out.results.baseline.latencyMedianMs,1);
 assert.equal(out.comparisonsJustified,false);
});
test('compares only when all evaluated providers cover same complete corpus',()=>{
 const reports=[
  r('baseline','case-001',[],10),r('baseline','case-002',[],20),
  r('ultra-sentinel','case-001',[rule],5),r('ultra-sentinel','case-002',[],7)
 ];
 const out=evaluate(corpus,reports,{expectedSha:SHA});
 assert.equal(out.comparisonsJustified,true);
 assert.equal(out.results['ultra-sentinel'].tp,1);
});
test('rejects unverified labels, duplicate cases, stale results, unknown providers',()=>{
 assert.throws(()=>evaluate([{...corpus[0],humanReviewed:false}],[],{expectedSha:SHA}));
 assert.throws(()=>evaluate([corpus[0],corpus[0]],[],{expectedSha:SHA}));
 assert.throws(()=>evaluate(corpus,[r('random-llm','case-001',[])],{expectedSha:SHA}));
 assert.throws(()=>evaluate(corpus,[r('baseline','case-001',[]),r('baseline','case-001',[])],{expectedSha:SHA}));
 assert.throws(()=>evaluate(corpus,[{...r('baseline','case-001',[]),sha:'b'.repeat(40)}],{expectedSha:SHA}));
});
test('rejects potentially hostile paths, executable prompts or unbounded output',()=>{
 assert.throws(()=>evaluate(corpus,[r('baseline','case-001',[{rule:'UNSCOPED_COROUTINE',path:'../../etc/passwd'}])],{expectedSha:SHA}));
});
