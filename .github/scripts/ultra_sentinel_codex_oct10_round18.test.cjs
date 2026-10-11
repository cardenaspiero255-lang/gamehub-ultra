'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {recordVerifiedRepair}=require('./ultra_sentinel_incidents.cjs');
const A='a'.repeat(40),B='b'.repeat(40),C='c'.repeat(40);
const valid=()=>({id:'sentinel-fix-2026',fixSha:A,redTestSha:B,greenTestSha:C,
 approvedBy:'reviewer',approval:'approved',verifiedAt:'2026-10-08T10:00:00Z',
 evidenceUrl:'https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/169',
 rule:'SPEECH_REENTRANT_RETRY',summary:'Fix verified recognition reentrancy lifecycle for CI',
 expiresAt:'2026-11-08T10:00:00Z'});
test('round18 normal verified provenance record remains a claim only',()=>{
 const verdict=recordVerifiedRepair(valid());
 assert.equal(verdict.status,'PROVENANCE_RECORDED');assert.equal(verdict.autoApply,false);
});
for(const [label,change] of [
 ['array id',{id:['sentinel-fix-2026']}],['object id',{id:{}}],
 ['array rule',{rule:['SPEECH_REENTRANT_RETRY']}],['object rule',{rule:{}}],
 ['array approver',{approvedBy:['reviewer']}],['object approver',{approvedBy:{}}],
 ['array evidence URL',{evidenceUrl:['https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/169']}],
 ['object evidence URL',{evidenceUrl:{}}],
 ['malformed approver',{approvedBy:'name with spaces'}],
 ['array immutable SHA',{fixSha:[A]}]
]){
 test('round18 reject malformed verified-repair '+label,()=>{
  let verdict;
  assert.doesNotThrow(()=>{verdict=recordVerifiedRepair({...valid(),...change});});
  assert.equal(verdict.status,'REJECTED',JSON.stringify(verdict));
 });
}
