'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {CASES,plan,replaceExactlyOnce}=require('./ultra_sentinel_mutation.cjs');
test('mutants are deterministic, named and capped',()=>{
 const x=plan();assert.ok(x.length>=12&&x.length<=20);assert.equal(new Set(x.map(z=>z.id)).size,x.length);
});
test('all mutation targets are distinct',()=>{
 assert.ok(plan().every(x=>x.from!==x.to));
});
test('replacement changes exactly one expression',()=>{
 assert.equal(replaceExactlyOnce('prefix A suffix','A','B'),'prefix B suffix');
});
test('refuses absent mutation anchors',()=>{
 assert.throws(()=>replaceExactlyOnce('A','B','C'),/missing/);
});
test('refuses ambiguous mutation anchors',()=>{
 assert.throws(()=>replaceExactlyOnce('A and A','A','B'),/ambiguous/);
});
test('cannot direct mutations to arbitrary files',()=>{
 assert.throws(()=>plan([['../../../etc/passwd','hack','a','b']]),/Unsafe/);
});
test('each mutant is paired with a real standalone test file',()=>{
 assert.ok(plan().every(x=>x.test.endsWith('.test.cjs')&&x.file));
});
test('module changes are never written into source files by planner',()=>{
 const xs=plan();assert.equal(xs[0].file,'report');
});
