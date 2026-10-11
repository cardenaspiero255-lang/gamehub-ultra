'use strict';
/**
 * 8192 explicit cross-product tests for privileged file-change metadata.
 * Adversarial GitHub API records: duplicate entries, invalid change statuses,
 * count truncation, protected renames and copies, missing source names,
 * and unexpected mutations in a mixed file batch.
 * All records are inert JSON and cannot modify any repository files.
 */
const test=require('node:test');
const assert=require('node:assert/strict');
const {evaluateProtectedChanges}=require('./ultra_sentinel_policy.cjs');
const COUNT=8192;
const names=[
 '.github/workflows/security.yml',
 '.github/workflows/ultra-sentinel-core-check.yml',
 '.github/scripts/reviewer.cjs',
 '.github/actions/local/action.yml',
 '.github/DO-NOT-EDIT',
 '.github/sentinel-contracts/contract.json',
 'package.json',
 'package-lock.json',
 'docs/README.md',
 'docs/ultra-sentinel.md',
 'app/src/main/java/com/example/Feature.kt',
 'app/src/test/java/com/example/FeatureTest.kt',
 'README.md',
 '.gitignore',
 'gradle/libs.versions.toml',
 'settings.gradle.kts'
];
const states=['added','modified','removed','renamed','copied','unchanged',
 'replaced','reverted'];
const legacy=[
 'docs/previous.md',
 '.github/workflows/old-security.yml',
 '.github/scripts/old-guard.cjs',
 null
];
function input(i){
 const name=i%16,operation=Math.floor(i/16)%8,
  previous=Math.floor(i/128)%4,
  fault=Math.floor(i/512)%4,
  secondary=Math.floor(i/2048)%4;
 const head={filename:names[name],status:states[operation]};
 if(['renamed','copied'].includes(head.status)&&legacy[previous]!==null)
  head.previous_filename=legacy[previous];
 const files=[head];
 if(secondary===1)files.push({filename:'docs/unrelated-'+i+'.md',status:'modified'});
 if(secondary===2)files.push({filename:'.github/workflows/removed-'+i+'.yml',status:'removed'});
 if(secondary===3)files.push({filename:'.github/scripts/added-'+i+'.cjs',status:'added'});
 if(fault===2)files.push({...head});
 if(fault===3)files.push(null);
 const expectedCount=files.length+(fault===1?1:0);
 return {files,expectedCount,name,operation,previous,fault,secondary};
}
function protectedPath(p){
 if(typeof p!=='string')return false;
 return p.startsWith('.github/')||p==='package.json'||p==='package-lock.json';
}
function contract({files,expectedCount}){
 const validOps=new Set(['added','modified','removed','renamed','copied','unchanged']);
 if(files.length!==expectedCount||files.some(f=>!f||typeof f.filename!=='string'||
  !validOps.has(f.status)||(['renamed','copied'].includes(f.status)&&
  typeof f.previous_filename!=='string'))||
  new Set(files.filter(Boolean).map(f=>f.filename)).size!==files.length){
  return {status:'INCOMPLETE',removed:[],modified:[],partial:true};
 }
 const removed=[],modified=[];
 for(const f of files){
  const current=protectedPath(f.filename);
  const former=protectedPath(['renamed','copied'].includes(f.status)?
   f.previous_filename:f.filename);
  if(f.status==='removed'&&current)removed.push(f.filename);
  else if(f.status==='renamed'&&former)removed.push(f.previous_filename);
  else if(f.status==='renamed'&&current)modified.push(f.filename);
  else if(f.status==='copied'&&(current||former))modified.push(f.filename);
  else if((f.status==='modified'||f.status==='added')&&current)
   modified.push(f.filename);
 }
 return {status:removed.length?'BLOCKED':modified.length?'REVIEW_REQUIRED':'OK',
  removed,modified,partial:false};
}
test('Protected changes scenarios cover 8192 unique combinations, not copies',()=>{
 const seen=new Set();
 for(let i=0;i<COUNT;i++){
  const c=input(i);
  seen.add([c.name,c.operation,c.previous,c.fault,c.secondary].join('-'));
 }
 assert.equal(seen.size,COUNT);
 assert.equal(names.length,16);
 assert.ok(contract(input(0)).status==='REVIEW_REQUIRED');
 assert.equal(contract(input(8)).status,'OK');
});
for(let i=0;i<COUNT;i++){
 test('protected file metadata attack '+i.toString(16).padStart(4,'0'),()=>{
  const args=input(i),expected=contract(args);
  const got=evaluateProtectedChanges(args.files,{expectedCount:args.expectedCount});
  assert.deepEqual(got,expected,JSON.stringify({
   case:i,name:args.name,operation:args.operation,previous:args.previous,
   fault:args.fault,secondary:args.secondary,files:args.files
  }));
 });
}
