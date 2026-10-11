'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');

test('Post-CI certification rejects unknown or waiting CI evidence',()=>{
 const script=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-sss-post-ci.yml'),'utf8');
 assert.match(script,/if\s*\(\s*ci\.status\s*!==\s*['"]PASS['"]\s*\)\s*core\.setFailed\s*\(/);
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
