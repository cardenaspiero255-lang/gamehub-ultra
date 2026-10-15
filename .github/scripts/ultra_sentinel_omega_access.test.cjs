'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const m=require('./ultra_sentinel_omega_access.cjs');
test('all 20 state/event pairs remain total and deterministic',()=>{
 const seen=new Set();
 for(const s of m.STATES)for(const e of m.EVENTS){
  const result=m.step(s,e);
  assert.ok(m.STATES.includes(result.to));
  seen.add(s+'#'+e);
 }
 assert.equal(seen.size,20);
});
test('resource access only granted following an explicit grant observation',()=>{
 for(const s of m.STATES){
  assert.equal(m.step(s,'USE_RESOURCE').allowed,s==='GRANTED');
  assert.equal(m.step(s,'REVOKE').to,'REVOKED');
 }
 assert.equal(m.step('REVOKED','CHECK_GRANTED').to,'GRANTED');
 assert.equal(m.step('GRANTED','RESET').to,'UNKNOWN');
});
test('revoke blocks access even after repeated late use callbacks',()=>{
 let state='GRANTED';state=m.step(state,'REVOKE').to;
 for(let i=0;i<1000;i++){
  const x=m.step(state,'USE_RESOURCE');
  assert.equal(x.allowed,false);state=x.to;
 }
});
test('exhaustive bounded traces preserve revoked and denied access invariants',()=>{
 const report=m.verify({maxTrace:7});
 assert.equal(report.valid,true);assert.equal(report.linkedRuntimeTests,false);
 assert.equal(report.states,4);assert.equal(report.events,5);
 assert.equal(report.transitions,97655);
});
test('rejects malformed state and runaway verification budgets',()=>{
 assert.throws(()=>m.step('ADMIN','USE_RESOURCE'));
 assert.throws(()=>m.step('GRANTED','ELEVATE_PRIVILEGES'));
 assert.throws(()=>m.verify({maxTrace:10}));
});
