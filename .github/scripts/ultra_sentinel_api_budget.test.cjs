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
