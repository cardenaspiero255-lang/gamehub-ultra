'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {buildGraph,words}=require('./ultra_sentinel_causal.cjs');
const A='a'.repeat(40),B='b'.repeat(40),C='c'.repeat(40);
const f={path:'app/src/main/java/com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt',rule:'SPEECH_REENTRANT_RETRY',
 line:42,reason:'SpeechRecognizer callback restart stack overflow'};
function historic(sha,path,message){return {sha,files:[path],message}}
test('same-file ancestor is a hypothesis, not proven root cause',()=>{
 const r=buildGraph({headSha:A,findings:[f],history:[historic(B,f.path,'Change callback restart')]});
 assert.equal(r.candidates.length,1);
 assert.equal(r.candidates[0].historyCandidates[0].causality,'NOT_ESTABLISHED');
 assert.equal(r.rootCauseConfirmed,false);
});
test('unrelated files do not create causal edges',()=>{
 const r=buildGraph({headSha:A,findings:[f],history:[historic(B,'README.md','docs')]});
 assert.equal(r.candidates.length,0);
});
test('current commit excluded',()=>{
 const r=buildGraph({headSha:A,findings:[f],history:[historic(A,f.path,'new change')]});
 assert.equal(r.candidates.length,0);
});
test('reject non full commit SHA and malicious filenames',()=>{
 assert.throws(()=>buildGraph({headSha:'abc'}));
 const r=buildGraph({headSha:A,findings:[{...f,path:'../../etc/passwd'}],history:[historic(B,'../../etc/passwd','x')]});
 assert.equal(r.candidates.length,0);
});
test('rank is ordering, never a fault probability',()=>{
 const r=buildGraph({headSha:A,findings:[f],history:[
  historic(B,f.path,'Unknown unrelated'),
  historic(C,f.path,'SpeechRecognizer callback restart')
 ]});
 const hits=r.candidates[0].historyCandidates;
 assert.equal(hits[0].sha,C);
 assert.ok(!('probability' in hits[0]));
});
test('no fabricated history when inputs are absent',()=>{
 const r=buildGraph({headSha:A,findings:[f],history:[]});
 assert.equal(r.inspectedHistory,0);assert.equal(r.candidates.length,0);
});
test('results bounded and escaped in descriptive strings',()=>{
 const many=Array(120).fill(0).map((_,i)=>historic(i.toString(16).padStart(40,'0'),f.path,'title <script>'));
 const r=buildGraph({headSha:A,findings:[f],history:many});
 assert.ok(r.candidates[0].historyCandidates.length<=5);
 assert.ok(!JSON.stringify(r).includes('<script>'));
});
test('duplicate commits per finding deduplicated',()=>{
 const c=historic(B,f.path,'callback');
 const r=buildGraph({headSha:A,findings:[f],history:[c,c]});
 assert.equal(r.candidates[0].historyCandidates.length,1);
});
test('token analysis is bounded',()=>assert.ok(words('word '.repeat(2000)).length<=70));
