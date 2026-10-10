'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const m=require('./ultra_sentinel_core_shards.cjs');

function fakeRoot(t){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'sentinel-shards-'));
 t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 for(const name of [
  'ultra_sentinel_150k_unique_matrix.test.cjs',
  'ultra_sentinel_500k_advanced_matrix.test.cjs',
  'ultra_sentinel_review_authority_matrix.test.cjs',
  'ultra_sentinel_patch_attestation_matrix.test.cjs',
  'ultra_sentinel_protected_file_matrix.test.cjs',
  'ultra_sentinel_extra.test.cjs'
 ])fs.writeFileSync(path.join(root,name),'// inert test stub\n');
 return root;
}
function makeReports(root,out,sha='f'.repeat(40)){
 const ids=[...Array.from({length:13},(_,i)=>'m'+String(i).padStart(2,'0')),'b0','b1'];
 for(const id of ids){
  const planned=m.plan(id,root),count=planned.kind==='matrix'?
   38500+(planned.index===0?2:0):m.BASELINE_TEST_COUNTS[id];
  fs.writeFileSync(path.join(out,id+'.json'),JSON.stringify({
   schema:'sentinel-core-shard/v1',...planned,sha,exitCode:0,signal:null,
   counters:{tests:count,pass:count,fail:0,skipped:0,todo:0,cancelled:0},
   tapSha256:'a'.repeat(64),passed:true
  }));
 }
 return ids;
}
test('15 shards deterministically cover 130k matrix indexes and every baseline file exactly once',t=>{
 const root=fakeRoot(t),all=[];
 for(let i=0;i<13;i++){
  const s=m.plan('m'+String(i).padStart(2,'0'),root);
  assert.equal(s.start,i*10000);assert.equal(s.end,s.start+10000);
  assert.deepEqual(s.files,['ultra_sentinel_150k_unique_matrix.test.cjs','ultra_sentinel_500k_advanced_matrix.test.cjs']);
  assert.equal(s.extraStart,i*28500);assert.equal(s.extraEnd,(i+1)*28500);
 }
 for(const id of ['b0','b1'])all.push(...m.plan(id,root).files);
 assert.equal(new Set(all).size,all.length);
 assert.deepEqual([...all].sort(),m.testFiles(root).filter(x=>!x.includes('150k_unique_matrix')&&!x.includes('500k_advanced_matrix')));
});
test('reject invalid shard selectors and missing core files',t=>{
 const root=fakeRoot(t);
 for(const x of ['m13','m-1','b2','b00','x00','m00;echo pwn'])
  assert.throws(()=>m.plan(x,root));
 fs.unlinkSync(path.join(root,'ultra_sentinel_patch_attestation_matrix.test.cjs'));
 assert.throws(()=>m.plan('m00',root),/Missing/);
});
test('TAP parser rejects missing counters and counts actual executions',()=>{
 const tap='TAP version 13\n# tests 10000\n# pass 10000\n# fail 0\n# skipped 0\n# todo 0\n# cancelled 0\n';
 const counters=m.parseTap(tap);
 assert.deepEqual(counters,{tests:10000,pass:10000,fail:0,skipped:0,todo:0,cancelled:0});
});
test('aggregator demands all 15 reports, exact filenames and minimum 150k',t=>{
 const root=fakeRoot(t),out=fs.mkdtempSync(path.join(os.tmpdir(),'sentinel-reports-'));
 t.after(()=>fs.rmSync(out,{recursive:true,force:true}));
 const sha='f'.repeat(40),ids=makeReports(root,out,sha);
 const result=m.aggregate(out,sha,root);
 assert.equal(result.executed,521686);
 assert.equal(result.jobs,15);
 assert.equal(result.passed,true);
 fs.unlinkSync(path.join(out,ids[2]+'.json'));
 assert.throws(()=>m.aggregate(out,sha,root),/Incomplete/);
});
test('aggregator fails closed on stale SHA, inflated counters, skips and tampered shard range',t=>{
 const root=fakeRoot(t),out=fs.mkdtempSync(path.join(os.tmpdir(),'sentinel-tamper-'));
 t.after(()=>fs.rmSync(out,{recursive:true,force:true}));
 const sha='f'.repeat(40);makeReports(root,out,sha);
 const filename=path.join(out,'m01.json');
 const original=JSON.parse(fs.readFileSync(filename,'utf8'));
 const mutate=patch=>{
  fs.writeFileSync(filename,JSON.stringify({...original,...patch}));
  assert.throws(()=>m.aggregate(out,sha,root));
 };
 mutate({sha:'e'.repeat(40)});
 mutate({counters:{...original.counters,tests:20000,pass:20000}});
 mutate({counters:{...original.counters,skipped:1}});
 mutate({start:999});
 fs.writeFileSync(filename,JSON.stringify(original));
 const baseline=path.join(out,'b1.json');
 const ok=JSON.parse(fs.readFileSync(baseline,'utf8'));
 fs.writeFileSync(baseline,JSON.stringify({...ok,counters:{...ok.counters,tests:1,pass:1}}));
 assert.throws(()=>m.aggregate(out,sha,root),/Baseline shard count mismatch/);
 fs.writeFileSync(baseline,JSON.stringify({...ok,counters:{...ok.counters,tests:1000,pass:1000}}));
 assert.throws(()=>m.aggregate(out,sha,root),/Baseline shard count mismatch/);
 fs.writeFileSync(baseline,JSON.stringify(ok));
 const matrix=path.join(out,'m01.json');
 const originalMatrix=JSON.parse(fs.readFileSync(matrix,'utf8'));
 for(const value of [1,null,undefined]){
  const counters={...originalMatrix.counters,cancelled:value};
  if(value===undefined)delete counters.cancelled;
  fs.writeFileSync(matrix,JSON.stringify({...originalMatrix,counters}));
  assert.throws(()=>m.aggregate(out,sha,root),/Failed or incomplete/);
 }
 fs.writeFileSync(matrix,JSON.stringify(originalMatrix));
 for(const value of [1,null,undefined]){
  const counters={...ok.counters,cancelled:value};
  if(value===undefined)delete counters.cancelled;
  fs.writeFileSync(baseline,JSON.stringify({...ok,counters}));
  assert.throws(()=>m.aggregate(out,sha,root),/Failed or incomplete/);
 }
});
