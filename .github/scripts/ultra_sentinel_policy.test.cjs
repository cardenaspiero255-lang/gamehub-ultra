'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {evaluateProtectedChanges}=require('./ultra_sentinel_policy.cjs');
const cases=[
 {name:'deleted auto-review workflow',files:[{filename:'.github/workflows/ultra-sentinel-auto-review.yml',status:'removed'}],status:'BLOCKED'},
 {name:'renamed auto-review workflow',files:[{filename:'.github/workflows/archive.yml',previous_filename:'.github/workflows/ultra-sentinel-auto-review.yml',status:'renamed'}],status:'BLOCKED'},
 {name:'deleted Core Tests workflow',files:[{filename:'.github/workflows/ultra-sentinel-core-check.yml',status:'removed'}],status:'BLOCKED'},
 {name:'deleted mutation lab',files:[{filename:'.github/workflows/ultra-sentinel-mutation.yml',status:'removed'}],status:'BLOCKED'},
 {name:'deleted independent reviewer',files:[{filename:'.github/workflows/ultra-sentinel-independent-review.yml',status:'removed'}],status:'BLOCKED'},
 {name:'deleted baseline Android',files:[{filename:'.github/workflows/android.yml',status:'removed'}],status:'BLOCKED'},
 {name:'deleted Coverage',files:[{filename:'.github/workflows/coverage.yml',status:'removed'}],status:'BLOCKED'},
 {name:'deleted structural parser',files:[{filename:'.github/scripts/ultra_sentinel_yaml_ast.cjs',status:'removed'}],status:'BLOCKED'},
 {name:'deleted npm integrity lockfile',files:[{filename:'package-lock.json',status:'removed'}],status:'BLOCKED'},
 {name:'malformed changed-files list',files:null,status:'INCOMPLETE'},
 {name:'truncated changed-files list',files:[{filename:'README.md',status:'modified'}],status:'INCOMPLETE',expected:2},
 {name:'legitimate README edit',files:[{filename:'README.md',status:'modified'}],status:'OK'},
 {name:'modified trusted review requires manual approval',files:[{filename:'.github/workflows/ultra-sentinel-auto-review.yml',status:'modified'}],status:'REVIEW_REQUIRED'},
 {name:'added trusted parser requires manual approval',files:[{filename:'.github/scripts/ultra_sentinel_yaml_ast.cjs',status:'added'}],status:'REVIEW_REQUIRED'},
 {name:'unsupported change status is not trusted',files:[{filename:'.github/workflows/ultra-sentinel-auto-review.yml',status:'mystery'}],status:'INCOMPLETE'},
];
for(const c of cases){
 test('protected controls: '+c.name,()=>{
  const r=evaluateProtectedChanges(c.files,{expectedCount:c.expected??(c.files?.length??1)});
  assert.equal(r.status,c.status,JSON.stringify(r));
  if(c.status==='BLOCKED')assert.ok(r.removed.length>0);
  if(c.status==='INCOMPLETE')assert.equal(r.partial,true);
 });
}
