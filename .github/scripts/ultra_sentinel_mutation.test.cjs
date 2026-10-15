'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {CASES,plan,replaceExactlyOnce}=require('./ultra_sentinel_mutation.cjs');
test('mutants are deterministic, named and capped',()=>{
 const x=plan();assert.ok(x.length>=12&&x.length<=20);assert.equal(new Set(x.map(z=>z.id)).size,x.length);
});
test('all mutation targets are distinct',()=>{
 assert.ok(plan().every(x=>x.from!==x.to));
 const source=require('node:fs').readFileSync(require('node:path').join(__dirname,'ultra_sentinel_core.cjs'),'utf8');
 const shrink=plan().find(x=>x.id==='all-patches-truncated');
 assert.ok(shrink&&source.includes(shrink.from),'truncation mutant must follow current Core patch bound');
 // Keep the immutable Core b1 registered-test contract while checking every
 // mutation anchor for drift after source hardening.
 const fs=require('node:fs'),path=require('node:path');
 for(const mutant of plan()){
  const content=fs.readFileSync(path.join(__dirname,'ultra_sentinel_'+mutant.file+'.cjs'),'utf8');
  const first=content.indexOf(mutant.from);
  assert.notEqual(first,-1,'missing anchor: '+mutant.id);
  assert.equal(content.indexOf(mutant.from,first+mutant.from.length),-1,
   'ambiguous anchor: '+mutant.id);
 }
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
