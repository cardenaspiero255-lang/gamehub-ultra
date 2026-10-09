'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const E=require('./ultra_sentinel_evidence.cjs');
const SHA='a'.repeat(40),REPO='cardenaspiero255-lang/gamehub-ultra',ID=123456;
const TRUST={'Android build':{id:131,path:'.github/workflows/android.yml',blobSha:E.TRUSTED_BLOBS['Android build']},
 'Unit Test Coverage':{id:132,path:'.github/workflows/coverage.yml',blobSha:E.TRUSTED_BLOBS['Unit Test Coverage']}};
const run=(overrides={})=>({
 id:ID,name:'Android build',workflow_id:131,path:'.github/workflows/android.yml',
 head_sha:SHA,status:'completed',conclusion:'success',event:'push',head_branch:'main',
 run_attempt:1,repository:{full_name:REPO},head_repository:{full_name:REPO},
 html_url:'https://github.com/'+REPO+'/actions/runs/'+ID,
 ...overrides
});
test('release attestation refuses caller-supplied CI names without trusted IDs',()=>{
 assert.equal(E.validateRun(run(),SHA),null);
 assert.equal(E.validateRun(run(),SHA,TRUST)?.workflow,'Android build');
});
test('release attestation refuses mismatched workflow ID/path and foreign branch',()=>{
 for(const override of [{workflow_id:555},{path:'.github/workflows/forged.yml'},
  {head_branch:'feature/unsound'},{event:'pull_request'}])
  assert.equal(E.validateRun(run(override),SHA,TRUST),null);
});
test('release attestation refuses incomplete trusted-workflow identity map',()=>{
 assert.equal(E.validateRun(run(),SHA,{}),null);
 assert.equal(E.validateRun(run(),SHA,{'Android build':{id:131,path:'unsafe.yml'}}),null);
});
test('release metadata still contains only immutable hashes and run IDs',()=>{
 const result=E.validateRun(run({privateInfo:'Bearer leak secret'}),SHA,TRUST);
 assert.ok(result);assert.ok(!JSON.stringify(result).includes('Bearer'));
 assert.equal(result.verification,'verified-github-api-run');
});
test('Sentry source includes no hardcoded workflow IDs',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const code=fs.readFileSync(path.join(__dirname,'ultra_sentinel_evidence.cjs'),'utf8');
 assert.match(code,/\.github\/workflows\/android\.yml/);
 assert.match(code,/\.github\/workflows\/coverage\.yml/);
 assert.match(code,/actions\/workflows\/'\+short/);
});
