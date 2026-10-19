'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {postCiDisposition}=require('./ultra_sentinel_post_ci_state.cjs');
const SHA='a'.repeat(40);
const evidence=(overrides={})=>({sourceSha:SHA,linkedCount:1,eligibleCount:1,ciStatuses:['PASS'],apiComplete:true,...overrides});
test('PASS requires trusted completed Android and Coverage without authorizing merges',()=>{
 const d=postCiDisposition(evidence());
 assert.equal(d.status,'PASS');assert.equal(d.action,'VERIFIED');
 assert.equal(d.autoMergeAllowed,false);assert.equal(d.autoApproveAllowed,false);
});
test('first CI completion WAITING is retried, not failed immediately',()=>{
 const d=postCiDisposition(evidence({ciStatuses:['WAITING']}));
 assert.equal(d.status,'WAITING');assert.equal(d.action,'RETRY');assert.equal(d.attested,false);
});
test('unresolved WAITING after bounded retries blocks certification',()=>{
 const d=postCiDisposition(evidence({ciStatuses:['WAITING'],exhausted:true}));
 assert.equal(d.status,'WAITING');assert.equal(d.action,'BLOCK');assert.equal(d.attested,false);
});
test('closed or merged PR is STALE archival evidence not certification',()=>{
 const d=postCiDisposition(evidence({eligibleCount:0,ciStatuses:[]}));
 assert.equal(d.status,'STALE');assert.equal(d.action,'ARCHIVE');assert.equal(d.attested,false);
});
test('PR HEAD changed during evaluation is STALE and old SHA never certified',()=>{
 const d=postCiDisposition(evidence({eligibleCount:0,ciStatuses:[],snapshotStatus:'STALE'}));
 assert.equal(d.status,'STALE');assert.equal(d.action,'ARCHIVE');assert.equal(d.attested,false);
});
test('no associated PR is archival, not a successful attestation',()=>{
 const d=postCiDisposition(evidence({linkedCount:0,eligibleCount:0,ciStatuses:[]}));
 assert.equal(d.status,'STALE');assert.equal(d.action,'ARCHIVE');assert.equal(d.attested,false);
});
test('real CI failure or untrusted source always blocks',()=>{
 for(const status of ['FAILED','UNTRUSTED']){
  const d=postCiDisposition(evidence({ciStatuses:[status]}));
  assert.equal(d.action,'BLOCK');assert.equal(d.attested,false);
 }
});
test('API failure, incomplete coverage, malformed SHA or linkage never pass',()=>{
 for(const malformed of [{apiComplete:false},{ciStatuses:['UNKNOWN']},
  {eligibleCount:2,ciStatuses:['PASS']},{sourceSha:'invalid'},{linkedCount:0,eligibleCount:1}]){
  const d=postCiDisposition(evidence(malformed));
  assert.equal(d.status,'UNKNOWN',JSON.stringify(malformed));
  assert.equal(d.action,'BLOCK',JSON.stringify(malformed));
 }
});
test('all current linked PRs must pass on the identical source SHA',()=>{
 assert.equal(postCiDisposition(evidence({linkedCount:2,eligibleCount:2,ciStatuses:['PASS','WAITING']})).action,'RETRY');
 assert.equal(postCiDisposition(evidence({linkedCount:2,eligibleCount:2,ciStatuses:['PASS','FAILED']})).action,'BLOCK');
 assert.equal(postCiDisposition(evidence({linkedCount:2,eligibleCount:2,ciStatuses:['PASS','PASS']})).action,'VERIFIED');
});
test('unknown states do not become approvals',()=>{
 const d=postCiDisposition(evidence({ciStatuses:['green']}));
 assert.equal(d.status,'UNKNOWN');assert.equal(d.action,'BLOCK');
});
test('workflow waits boundedly and emits explicit status, never trusts PR source',()=>{
 const y=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-sss-post-ci.yml'),'utf8');
 assert.match(y,/postCiDisposition/);
 assert.match(y,/MAX_WAIT_ATTEMPTS\s*=\s*1[0-9]/);
 assert.match(y,/await\s+new\s+Promise/);
 assert.match(y,/decision\.action==='BLOCK'/);
 assert.match(y,/report\.status=decision\.status/);
 assert.doesNotMatch(y,/if\(ci\.status!=='PASS'\)core\.setFailed/);
 assert.doesNotMatch(y,/if\(!report\.prs\.length\)core\.setFailed/);
 assert.match(y,/persist-credentials: false/);
 assert.doesNotMatch(y,/\b(?:contents|actions|pull-requests|issues): write\b/);
});
