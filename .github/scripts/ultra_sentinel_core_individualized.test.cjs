'use strict';
/**
 * Isolated, independently asserted red-team cases.
 *
 * The existing gauntlet scores its whole matrix in one aggregate test.
 * That hides the exact fixture when a family regresses. Here every selected
 * fixture is an independently reported Node.js test with a strict contract.
 *
 * Selection is deterministic and family-stratified to avoid rewarding a
 * single large family. No fixture invokes or downloads untrusted code.
 * Not all 1,000+ checks represent unique root vulnerability families.
 */
const test=require('node:test');
const assert=require('node:assert/strict');
const {variants,classify}=require('./ultra_sentinel_adversarial_gauntlet.cjs');
const CASE_COUNT=1100;
const all=variants();
const families=new Map();
for(const c of all){
 if(!families.has(c.family))families.set(c.family,[]);
 families.get(c.family).push(c);
}
const keys=[...families.keys()].sort(),cursor=new Map(keys.map(k=>[k,0]));
const chosen=[];
while(chosen.length<CASE_COUNT){
 let progress=false;
 for(const key of keys){
  const rows=families.get(key),at=cursor.get(key);
  if(at===rows.length)continue;
  chosen.push(rows[at]);cursor.set(key,at+1);progress=true;
  if(chosen.length===CASE_COUNT)break;
 }
 if(!progress)break;
}
function assertCase(c){
 const got=classify(c),message=JSON.stringify({case:c.id,family:c.family,kind:c.kind,
  result:got,source:typeof c.source==='string'?c.source.slice(0,800):'<structured>'});
 assert.notEqual(got.outcome,'CRASH',message);
 if(c.kind==='attack'){
  // Incomplete = refused certification, NOT a true positive.
  // The corpus's explicit rule remains mandatory when supplied.
  assert.ok(['DETECTED','INCOMPLETE','OTHER_FINDING'].includes(got.outcome),message);
  if(c.rule)assert.equal(got.outcome,'DETECTED',message);
 }else if(c.kind==='core-attack'){
  assert.equal(got.outcome,'DETECTED',message);
 }else if(c.kind==='judge-attack'){
  assert.equal(got.outcome,'REJECT',message);
 }else if(c.kind==='policy-attack'){
  assert.ok(['BLOCKED','REVIEW_REQUIRED'].includes(got.outcome),message);
 }else if(c.kind==='safe'||c.kind==='core-safe'){
  assert.equal(got.outcome,'CLEAN',message);
 }else if(c.kind==='judge-safe'){
  assert.equal(got.outcome,'ELIGIBLE_FOR_HUMAN_REVIEW',message);
 }else if(c.kind==='policy-safe'){
  assert.equal(got.outcome,'OK',message);
 }else if(c.kind==='uncertain'){
  assert.ok(['DETECTED','OTHER_FINDING','INCOMPLETE'].includes(got.outcome),message);
 }else{
  assert.fail('Unrecognized case label '+c.kind+': '+message);
 }
}
test('Corpus integrity: family diversity, unique labels and exactly 1100 independent checks',()=>{
 assert.ok(all.length>=CASE_COUNT,'Too few fixtures: '+all.length);
 assert.equal(chosen.length,CASE_COUNT);
 assert.equal(new Set(chosen.map(x=>x.id)).size,CASE_COUNT);
 assert.ok(new Set(chosen.map(x=>x.family)).size>=15);
 assert.ok(chosen.some(x=>x.kind==='safe'),'No negative controls');
 assert.ok(chosen.some(x=>x.kind==='attack'),'No positive controls');
 assert.ok(chosen.some(x=>x.kind==='uncertain'),'No fail-closed controls');
});
for(const c of chosen){
 test('sentinel scenario ['+c.family+'] '+c.id,()=>assertCase(c));
}
