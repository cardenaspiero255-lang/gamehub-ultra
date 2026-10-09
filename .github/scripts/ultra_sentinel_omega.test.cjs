'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const omega=require('./ultra_sentinel_omega.cjs');
const sample=()=>JSON.parse(fs.readFileSync(path.resolve(__dirname,'../sentinel-contracts/voice-retry.omega.json'),'utf8'));
test('shipped OMEGA voice contract is total and deterministic',()=>{
 const result=omega.check(sample());assert.equal(result.valid,true,JSON.stringify(result.errors));
 assert.equal(result.transitionsChecked,12);assert.deepEqual(result.reachableStates,['CLOSED','IDLE','RETRY_PENDING']);
});
test('rejects repeated schedule from pending',()=>{
 const p=sample();p.transitions.find(t=>t.from==='RETRY_PENDING'&&t.event==='SCHEDULE').accepted=true;
 assert.equal(omega.check(p).valid,false);
});
test('rejects wrong first schedule result',()=>{
 const p=sample();p.transitions.find(t=>t.from==='IDLE'&&t.event==='SCHEDULE').accepted=false;
 assert.equal(omega.check(p).valid,false);
});
test('rejects reset leaving pending',()=>{
 const p=sample();p.transitions.find(t=>t.from==='RETRY_PENDING'&&t.event==='RESET').to='RETRY_PENDING';
 assert.equal(omega.check(p).valid,false);
});
test('rejects dispatch leaving pending',()=>{
 const p=sample();p.transitions.find(t=>t.from==='RETRY_PENDING'&&t.event==='DISPATCH').to='RETRY_PENDING';
 assert.equal(omega.check(p).valid,false);
});
test('rejects missing transitions',()=>{
 const p=sample();p.transitions.pop();assert.equal(omega.check(p).valid,false);
});
test('rejects conflicting duplicate transition',()=>{
 const p=sample();p.transitions[2]=p.transitions[1];assert.equal(omega.check(p).valid,false);
});
test('rejects dropped security invariant',()=>{
 const p=sample();p.properties.pop();assert.equal(omega.check(p).valid,false);
});
test('rejects spec retargeting to arbitrary source',()=>{
 const p=sample();p.implementation='../../secret.kt';assert.equal(omega.check(p).valid,false);
});
test('rejects arbitrary added states',()=>{
 const p=sample();p.states.push('COMPROMISED');assert.equal(omega.check(p).valid,false);
});
test('rejects nonobject payload',()=>assert.equal(omega.check(null).valid,false));
test('does not report Android runtime proof',()=>{
 assert.match(omega.check(sample()).guarantee,/Abstract/);
});

test('CLOSED is terminal even after reset and dispatch',()=>{
 const p=sample();
 for(const event of ['SCHEDULE','RESET','DISPATCH','CLOSE']){
  const tr=p.transitions.find(t=>t.from==='CLOSED'&&t.event===event);
  assert.equal(tr.to,'CLOSED');
 }
 const broken=sample();broken.transitions.find(t=>t.from==='CLOSED'&&t.event==='RESET').to='IDLE';
 assert.equal(omega.check(broken).valid,false);
});
test('cannot schedule retry after close',()=>{
 const p=sample();const tr=p.transitions.find(t=>t.from==='CLOSED'&&t.event==='SCHEDULE');
 tr.accepted=true;assert.equal(omega.check(p).valid,false);
});
