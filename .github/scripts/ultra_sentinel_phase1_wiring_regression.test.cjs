'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');

test('Post-CI never certifies UNKNOWN or WAITING and still blocks after retry exhaustion',()=>{
 const script=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-sss-post-ci.yml'),'utf8');
 const {postCiDisposition}=require('./ultra_sentinel_post_ci_state.cjs');
 const base={sourceSha:'a'.repeat(40),linkedCount:1,eligibleCount:1};
 assert.equal(postCiDisposition({...base,ciStatuses:['UNKNOWN']}).action,'BLOCK');
 assert.equal(postCiDisposition({...base,ciStatuses:['WAITING']}).action,'RETRY');
 assert.equal(postCiDisposition({...base,ciStatuses:['WAITING'],exhausted:true}).action,'BLOCK');
 assert.match(script,/postCiDisposition\(args\)/);
 assert.match(script,/if\(decision\.action==='BLOCK'\)\s*core\.setFailed/);
 assert.match(script,/MAX_WAIT_ATTEMPTS=15/);
});

test('Phase-1 Sentinel workflows parse and can be structurally attested',()=>{
 for(const filename of [
  'ultra-sentinel-auto-review.yml',
  'ultra-sentinel-core-check.yml',
  'ultra-sentinel-independent-review.yml',
  'ultra-sentinel-self-review.yml',
  'ultra-sentinel-mutation.yml',
  'ultra-sentinel-reliability-100.yml',
  'ultra-sentinel-sss-post-ci.yml'
 ]){
  const source=fs.readFileSync(path.resolve(__dirname,'../workflows',filename),'utf8');
  const result=inspectWorkflow(source,{path:'.github/workflows/'+filename});
  assert.notEqual(result.status,'INCOMPLETE',filename+': '+JSON.stringify(result));
 }
});
