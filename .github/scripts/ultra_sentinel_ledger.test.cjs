'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const led=require('./ultra_sentinel_ledger.cjs');
const A='a'.repeat(40),B='b'.repeat(40),C='c'.repeat(40);
const example={
 id:'verified-fixture',fixSha:A,redTestSha:B,greenTestSha:C,
 approvedBy:'reviewer',approval:'approved',
 verifiedAt:'2026-10-08T10:00:00Z',expiresAt:'2026-11-08T10:00:00Z',
 evidenceUrl:'https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/167',
 rule:'SPEECH_REENTRANT_RETRY',summary:'Confirmed callback reentrancy change as reviewed by human'
};
test('new records remain staged and invisible to trusted search',()=>{
 const x=led.stage(led.empty(),example,Date.parse('2026-10-09T00:00:00Z'));
 assert.equal(x.accepted,true);
 assert.equal(x.ledger.entries[0].state,'staged');
 assert.deepEqual(led.active(x.ledger),[]);
});
test('human review and matching SHA required to promote',()=>{
 const x=led.stage(led.empty(),example,Date.parse('2026-10-09T00:00:00Z')).ledger;
 assert.throws(()=>led.review(x,{id:example.id,decision:'reviewed',reviewer:'human',currentHeadSha:C}),/SHA/);
 const approved=led.review(x,{id:example.id,decision:'reviewed',reviewer:'human',currentHeadSha:A});
 assert.equal(approved.entries[0].state,'reviewed');
 assert.equal(led.active(approved,Date.parse('2026-10-10T00:00:00Z')).length,1);
 assert.equal(led.active(approved,Date.parse('2027-01-01T00:00:00Z')).length,0);
});
test('revocation hides previous evidence and leaves audit state',()=>{
 const x=led.stage(led.empty(),example,Date.parse('2026-10-09T00:00:00Z')).ledger;
 const y=led.review(x,{id:example.id,decision:'revoked',reviewer:'human',currentHeadSha:A});
 assert.deepEqual(led.active(y),[]);
 assert.equal(y.entries[0].state,'revoked');
});
test('duplicate entries and unverified records never enter ledger',()=>{
 const x=led.stage(led.empty(),example,Date.parse('2026-10-09T00:00:00Z'));
 assert.equal(led.stage(x.ledger,example,Date.parse('2026-10-09T00:00:00Z')).accepted,false);
 assert.equal(led.stage(led.empty(),{...example,redTestSha:null}).accepted,false);
 assert.throws(()=>led.validate({schema:led.SCHEMA,revision:0,entries:[x.entry,x.entry]}),/duplicated/);
});
