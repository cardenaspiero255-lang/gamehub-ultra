'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const workflows=[
 'ultra-sentinel-auto-review.yml',
 'ultra-sentinel-independent-review.yml'
];
for(const workflow of workflows){
 test(workflow+' requires exact post-upload reauthentication for only 172/221',()=>{
  const source=fs.readFileSync(path.resolve(__dirname,'../workflows/'+workflow),'utf8');
  const anchor=source.indexOf('- name: Reauthenticate scoped evidence after artifact upload')>=0?
   '- name: Reauthenticate scoped evidence after artifact upload':
   '- name: Reauthenticate';
  const index=source.lastIndexOf(anchor);
  assert.ok(index>=0,'post-upload step missing');
  const post=source.slice(index);
  assert.match(post,/const scopedReviewPr=report\.pr;/);
  assert.match(post,/!\[172,221\]\.includes\(scopedReviewPr\)/);
  assert.doesNotMatch(post,/report\.pr!==172/);
  assert.equal((post.match(/pull_number:scopedReviewPr/g)||[]).length,2);
  assert.match(post,/pr\.head\.sha!==report\.sha/);
  assert.match(post,/const current=await attestScopedPr172/);
  assert.match(post,/current\[field\]!==stored\[field\]/);
  assert.match(post,/core\.setFailed/);
  assert.doesNotMatch(post,/autoMergeAllowed:\s*true|autoCommitAllowed:\s*true/);
 });
}
test('the trust bridge remains exact-identity and non-approving',()=>{
 const s=fs.readFileSync(path.resolve(__dirname,'ultra_sentinel_pr172_scope.cjs'),'utf8');
 assert.match(s,/if\(pr\?\.number===221\)/);
 assert.match(s,/if\(pr\?\.number!==172\)return notApplicable/);
 const p=fs.readFileSync(path.resolve(__dirname,'ultra_sentinel_pr221_stage2.cjs'),'utf8');
 assert.match(p,/link\?\.number!==221\|\|link\.head\?\.sha!==head\|\|link\.base\?\.sha!==base/);
 assert.match(p,/autoApproveAllowed:false,autoMergeAllowed:false/);
});
