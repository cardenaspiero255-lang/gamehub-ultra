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
