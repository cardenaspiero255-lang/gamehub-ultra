'use strict';
/**
 * Ultra Sentinel Core: deterministic file-disjoint 15-job execution plan.
 * 13 x 10,000 generated matrix cases; 2 jobs cover every other Core file once.
 * Does not execute attacker fixture strings or use shell interpretation.
 */
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');

const ROOT=__dirname;
const MATRIX='ultra_sentinel_150k_unique_matrix.test.cjs';
const EXTRA='ultra_sentinel_500k_advanced_matrix.test.cjs';
const BASE_A=new Set([
 'ultra_sentinel_review_authority_matrix.test.cjs',
 'ultra_sentinel_patch_attestation_matrix.test.cjs'
]);
const SHARD_COUNT=15;
const MATRIX_SHARDS=13;
const MATRIX_CASES=500500;
const CASES_PER_SHARD=10000;
const EXTRA_CASES_PER_SHARD=28500;
const MINIMUM=500000;
// Reviewed expected registration totals; any new baseline test requires an intentional update.
// A PR must never attest fewer real baseline tests just because 500k matrix cases pass.
const BASELINE_TEST_COUNTS=Object.freeze({b0:10242,b1:10943});

function testFiles(root=ROOT){
 return fs.readdirSync(root).filter(f=>/^ultra_sentinel_.*\.test\.cjs$/.test(f)).sort();
}
function plan(id,root=ROOT){
 if(!/^m(?:0[0-9]|1[0-2])$|^b[01]$/.test(id))throw Error('Invalid shard id');
 const files=testFiles(root);
 if(!files.includes(MATRIX)||!files.includes(EXTRA)||![...BASE_A].every(f=>files.includes(f)))
  throw Error('Missing core test file; refuse incomplete shard coverage');
 const isMatrix=id.startsWith('m');
 const index=Number(id.slice(1));
 const selected=isMatrix?[MATRIX,EXTRA]:files.filter(f=>f!==MATRIX&&f!==EXTRA&&
  (id==='b0'?BASE_A.has(f):!BASE_A.has(f)));
 if(selected.length===0)throw Error('Empty shard '+id);
 return {id,kind:isMatrix?'matrix':'baseline',index,files:selected,
  start:isMatrix?index*CASES_PER_SHARD:null,
  end:isMatrix?(index+1)*CASES_PER_SHARD:null,
  extraStart:isMatrix?index*EXTRA_CASES_PER_SHARD:null,
  extraEnd:isMatrix?(index+1)*EXTRA_CASES_PER_SHARD:null};
}
function parseTap(content){
 const fields={tests:null,pass:null,fail:null,skipped:null,todo:null,cancelled:null};
 for(const match of content.matchAll(/^# (tests|pass|fail|skipped|todo|cancelled) (\d+)\s*$/gm)){
  fields[match[1]]=Number(match[2]);
 }
 return fields;
}
function validateReport(report,expected,sha){
 if(!report||report.schema!=='sentinel-core-shard/v1'||report.id!==expected.id||
    report.kind!==expected.kind||report.index!==expected.index||
    report.start!==expected.start||report.end!==expected.end||
    report.extraStart!==expected.extraStart||report.extraEnd!==expected.extraEnd||
    JSON.stringify(report.files)!==JSON.stringify(expected.files)||
    report.sha!==sha||report.exitCode!==0||report.signal!==null)
  throw Error('Invalid or stale report '+expected.id);
 const c=report.counters;
 if(!c||!Number.isSafeInteger(c.tests)||c.tests<=0||
    c.fail!==0||c.skipped!==0||c.todo!==0||c.cancelled!==0||
    c.pass!==c.tests||report.passed!==true)
  throw Error('Failed or incomplete cases in '+expected.id);
 if(expected.kind==='baseline'&&c.tests!==BASELINE_TEST_COUNTS[expected.id])
  throw Error('Baseline shard count mismatch '+expected.id);
 if(expected.kind==='matrix'&&c.tests!==CASES_PER_SHARD+EXTRA_CASES_PER_SHARD+(expected.index===0?2:0))
  throw Error('Missing or duplicated matrix cases in '+expected.id);
 if(!/^[a-f0-9]{64}$/.test(report.tapSha256||''))
  throw Error('Missing execution trace checksum '+expected.id);
 return c.tests;
}
function aggregate(dir,sha,root=ROOT){
 if(typeof sha!=='string'||!sha)throw Error('Missing immutable workflow SHA');
 const ids=[...Array.from({length:MATRIX_SHARDS},(_,i)=>'m'+String(i).padStart(2,'0')),'b0','b1'];
 const actual=fs.readdirSync(dir).filter(f=>f.endsWith('.json')).sort();
 const expected=ids.map(x=>x+'.json').sort();
 if(JSON.stringify(actual)!==JSON.stringify(expected))
  throw Error('Incomplete/extra reports: expected '+expected.join(',')+'; got '+actual.join(','));
 const seenFiles=new Set(),details=[];
 let tests=0;
 for(const id of ids){
  const target=plan(id,root);
  const report=JSON.parse(fs.readFileSync(path.join(dir,id+'.json'),'utf8'));
  const count=validateReport(report,target,sha);
  tests+=count;
  for(const f of report.files){
   if(target.kind==='matrix')continue; // Matrix file deliberately split by disjoint ranges.
   if(seenFiles.has(f))throw Error('Duplicate baseline file '+f);
   seenFiles.add(f);
  }
  details.push({id,count});
 }
 const other=testFiles(root).filter(f=>f!==MATRIX&&f!==EXTRA);
 if(JSON.stringify([...seenFiles].sort())!==JSON.stringify(other))
  throw Error('Core test files missing from baseline plan');
 if(tests<MINIMUM)throw Error('Core floor not met: '+tests+' < '+MINIMUM);
 return {schema:'sentinel-core-aggregate/v1',sha,jobs:SHARD_COUNT,
  matrix: MATRIX_CASES,executed:tests,minimum:MINIMUM,passed:true,details};
}
function runShard(id,outDir){
 const expected=plan(id);
 const sha=process.env.GITHUB_SHA||'LOCAL';
 const childEnv={...process.env};
 if(expected.kind==='matrix')childEnv.SENTINEL_MATRIX_SHARD=String(expected.index);
 else delete childEnv.SENTINEL_MATRIX_SHARD;
 fs.mkdirSync(outDir,{recursive:true});
 const tapFile=path.join(outDir,id+'.tap');
 const fd=fs.openSync(tapFile,'w');
 const start=Date.now();
 let run;
 try{
  run=spawnSync(process.execPath,['--test','--test-reporter=tap',...expected.files.map(f=>path.join(ROOT,f))],
   {cwd:path.resolve(ROOT,'../..'),env:childEnv,stdio:['ignore',fd,fd],timeout:22*60*1000});
 }finally{fs.closeSync(fd);}
 const tap=fs.readFileSync(tapFile,'utf8');
 const counters=parseTap(tap);
 const report={schema:'sentinel-core-shard/v1',...expected,sha,
  counters,exitCode:run.status,signal:run.signal,
  tapSha256:crypto.createHash('sha256').update(tap).digest('hex'),
  elapsedMs:Date.now()-start,passed:run.status===0&&run.signal===null&&
   counters.tests>0&&counters.fail===0&&counters.skipped===0&&
   counters.todo===0&&counters.cancelled===0&&
   counters.pass===counters.tests};
 fs.writeFileSync(path.join(outDir,id+'.json'),JSON.stringify(report)+'\n');
 try{validateReport(report,expected,sha);}
 catch(error){
  const failures=tap.split('\n').filter(x=>/^not ok\b/.test(x)).slice(0,20);
  console.error('Sentinel shard FAILED:',error.message,'Failed test IDs:',failures);
  process.exitCode=1;return;
 }
 console.log('Sentinel shard '+id+': '+counters.tests+' executed; 0 fail, 0 skip, 0 todo, 0 cancelled');
 fs.unlinkSync(tapFile); // Keep only small evidence JSON; no TAP flood/artifact.
}
if(require.main===module){
 try{
  const [mode,arg,dir]=process.argv.slice(2);
  if(mode==='run')runShard(arg,dir||process.cwd());
  else if(mode==='run-env')runShard(process.env.SENTINEL_SHARD_ID,arg||process.cwd());
  else if(mode==='aggregate'){
   if(process.env.SENTINEL_MATRIX_RESULT!=='success')
    throw Error('Matrix job not successful; reject core attestation');
   const result=aggregate(arg,process.env.GITHUB_SHA);
   console.log(JSON.stringify(result));
   const summary=process.env.GITHUB_STEP_SUMMARY;
   if(summary)fs.appendFileSync(summary,
    '## Ultra Sentinel Core — validated\n\n'+
    result.executed+' executed, 0 failures/skips, '+result.jobs+
    ' disjoint shards, GitHub SHA '+result.sha+'\n');
  }else throw Error('Usage: node ultra_sentinel_core_shards.cjs run-env DIR | aggregate DIR');
 }catch(error){console.error(error.message);process.exitCode=1;}
}
module.exports={plan,testFiles,parseTap,validateReport,aggregate,
 SHARD_COUNT,MATRIX_SHARDS,MATRIX_CASES,CASES_PER_SHARD,EXTRA_CASES_PER_SHARD,
 BASELINE_TEST_COUNTS,MINIMUM};
