'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {runReliability}=require('./ultra_sentinel_reliability.cjs');
test('reliability: 100 deterministic executions cover all 3 repository profiles',()=>{
 const r=runReliability({count:100,offset:0});
 assert.equal(r.executions,100);
 assert.deepEqual(r.profiles,['trusted','attacker','tampered']);
 assert.equal(r.checked,300);
 assert.equal(r.falsePositive,0);
 assert.equal(r.falseNegative,0);
 assert.equal(r.controlMisses,0);
 assert.equal(r.passed,true);
});
test('reliability: reproducible seeds do not alter security verdict',()=>{
 const a=runReliability({count:20,offset:40});
 const b=runReliability({count:20,offset:40});
 assert.deepEqual(a,b);
 assert.equal(a.executions,20);
 assert.equal(a.checked,60);
 assert.equal(a.passed,true);
});
test('reliability: invalid or excessive requested runs never certify success',()=>{
 for(const count of [-1,0,10001,'100',null]){
  const r=runReliability({count,offset:0});
  assert.equal(r.passed,false,JSON.stringify(r));
 }
});
