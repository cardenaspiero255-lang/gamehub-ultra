'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {createGitHubReader}=require('./ultra_sentinel_api_budget.cjs');
const repo='cardenaspiero255-lang/gamehub-ultra',sha='a'.repeat(40);
function reply(status,body={},headers={}){
 return {ok:status>=200&&status<300,status,
  headers:{get:key=>headers[key.toLowerCase()]??null},json:async()=>body};
}
test('immutable Git blobs are cached with defensive copies; mutable PR/runs never cached',async()=>{
 let count=0;
 const get=createGitHubReader(repo,'test-token-value',async()=>{count++;return reply(200,{value:1});});
 const blob='contents/.github/scripts/safe.cjs?ref='+sha;
 const one=await get(blob);one.value=999;
 assert.deepEqual(await get(blob),{value:1});
 assert.equal(count,1);
 await get('pulls/172');await get('pulls/172');
 await get('actions/runs?event=pull_request');await get('actions/runs?event=pull_request');
 assert.equal(count,5);
});
test('requests are serialized to protect the shared installation from API bursts',async()=>{
 let active=0,max=0;
 const get=createGitHubReader(repo,'test-token-value',async()=>{
  active++;max=Math.max(max,active);await Promise.resolve();active--;
  return reply(200,{ok:true});
 });
 await Promise.all([get('pulls/172'),get('pulls/172'),get('pulls/172')]);
 assert.equal(max,1);
});
test('429 retry-after is honored only with bounded retries',async()=>{
 let attempts=0;
 const pauses=[];
 const get=createGitHubReader(repo,'test-token-value',async()=>{
  attempts++;return attempts===1?reply(429,{}, {'retry-after':'2'}):reply(200,{ok:true});
 },{pause:async ms=>pauses.push(ms)});
 assert.deepEqual(await get('pulls/172'),{ok:true});
 assert.equal(attempts,2);assert.deepEqual(pauses,[2000]);
});
test('primary quota exhausted for an hour fails closed, without hammering or sleeping',async()=>{
 let calls=0;
 const get=createGitHubReader(repo,'test-token-value',async()=>{
  calls++;return reply(403,{}, {'x-ratelimit-remaining':'0',
   'x-ratelimit-reset':String(Math.floor(Date.now()/1000)+3600)});
 });
 await assert.rejects(()=>get('pulls/172'),/GITHUB_RATE_LIMITED/);
 assert.equal(calls,1);
 await assert.rejects(()=>get('pulls/172'),/GITHUB_RATE_LIMITED/);
 assert.equal(calls,1);
});

test('secondary limit without headers honors a full minute before one retry',async()=>{
 let calls=0;const pauses=[];
 const get=createGitHubReader(repo,'test-token-value',async()=>{
  calls++;return calls===1?reply(429,{}):reply(200,{ok:true});
 },{pause:async ms=>pauses.push(ms)});
 assert.deepEqual(await get('pulls/172'),{ok:true});
 assert.equal(calls,2);assert.deepEqual(pauses,[60000]);
});
test('trusted main AST can parse and certify API budget source context',()=>{
 const fs=require('node:fs'),path=require('node:path'),acorn=require('acorn');
 const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
 const src=fs.readFileSync(path.join(__dirname,'ultra_sentinel_api_budget.cjs'),'utf8');
 const ast=acorn.parse(src,{ecmaVersion:'latest',sourceType:'script',
  allowHashBang:true,allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,
  locations:true,ranges:true});
 assert.deepEqual(findCapabilities(ast),[]);
});

test('GitHub compare accepts exact SHA...SHA while blocking traversal',async()=>{
 let requests=0;
 const get=createGitHubReader(repo,'test-token-value',async()=>{
  requests++;
  return reply(200,{merge_base_commit:{sha:'a'.repeat(40)}});
 });
 const safe='compare/'+ 'a'.repeat(40)+'...'+ 'b'.repeat(40);
 const output=await get(safe);
 assert.equal(output.merge_base_commit.sha,'a'.repeat(40));
 assert.equal(requests,1);
 await assert.rejects(get('../secrets'),/INVALID_API_PATH/);
 await assert.rejects(get('compare/../../../secrets'),/INVALID_API_PATH/);
});

test('GitHub secondary 403 with no rate headers retries after 60 seconds, once only',async()=>{
 const pauses=[];let calls=0;
 const get=createGitHubReader(repo,'test-token-value',async()=>{
  calls++;
  if(calls===1)return reply(403,{message:'You have exceeded a secondary rate limit. Please wait a few minutes.'});
  return reply(200,{ok:true});
 },{pause:async ms=>pauses.push(ms)});
 assert.deepEqual(await get('pulls/172'),{ok:true});
 assert.equal(calls,2);
 assert.deepEqual(pauses,[60000]);
});
test('plain GitHub forbidden 403 never retries or weakens permissions',async()=>{
 // Fail closed on persistent gateway failures but retry a transient 5xx
 // exactly once. Never treat them as verified evidence or an approval.
 for(const status of [500,502,503,504]){
  const delays=[];let calls=0;
  const get=createGitHubReader(repo,'test-token-value',async()=>{
   calls++;return calls===1?reply(status,{message:'temporary'}):
    reply(200,{files:2});
  },{pause:async ms=>delays.push(ms)});
  assert.deepEqual(await get('pulls/194/files?per_page=100'),{files:2});
  assert.equal(calls,2);
  assert.deepEqual(delays,[1000]);
 }
 let failures=0;
 const failed=createGitHubReader(repo,'test-token-value',async()=>{
  failures++;return reply(503,{message:'unavailable'});
 },{pause:async()=>undefined});
 await assert.rejects(()=>failed('pulls/194/files?per_page=100'),
  /GITHUB_API_UNAVAILABLE/);
 assert.equal(failures,2,'persistent 5xx must still block merge and reviews');

 let calls=0;
 const get=createGitHubReader(repo,'test-token-value',async()=>{
  calls++;return reply(403,{message:'Resource not accessible by integration'});
 },{pause:async()=>assert.fail('unexpected backoff')});
 await assert.rejects(get('pulls/172'),/GITHUB_API_UNAVAILABLE/);
 assert.equal(calls,1);
});
