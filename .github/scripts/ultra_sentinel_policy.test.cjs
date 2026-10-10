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

const {hasIndependentHumanApproval}=require('./ultra_sentinel_policy.cjs');
const CURRENT_SHA='a'.repeat(40);
const approval=(id,override={})=>({id,state:'APPROVED',commit_id:CURRENT_SHA,
 user:{login:'independent-reviewer',type:'User'},author_association:'COLLABORATOR',...override});
test('Protected workflow changes require a trusted non-author human review of same head SHA',()=>{
 assert.equal(hasIndependentHumanApproval([approval(1)],{sha:CURRENT_SHA,author:'pr-author'}),true);
 for(const invalid of [
  approval(1,{commit_id:'b'.repeat(40)}),
  approval(1,{user:{login:'pr-author',type:'User'}}),
  approval(1,{user:{login:'bot[bot]',type:'Bot'}}),
  approval(1,{author_association:'NONE'}),
  approval(1,{state:'COMMENTED'})
 ])assert.equal(hasIndependentHumanApproval([invalid],{sha:CURRENT_SHA,author:'pr-author'}),false);
 assert.equal(hasIndependentHumanApproval([approval(1),approval(2,{state:'CHANGES_REQUESTED'})],{sha:CURRENT_SHA,author:'pr-author'}),false);
});


const {isAuthorizedSoloMaintainerPR}=require('./ultra_sentinel_policy.cjs');
const MAIN_REPO='cardenaspiero255-lang/gamehub-ultra';
const mainSha='a'.repeat(40);
const ownerPr={
 state:'open',number:169,user:{login:'cardenaspiero255-lang',type:'User'},
 author_association:'OWNER',
 head:{sha:mainSha,repo:{full_name:MAIN_REPO}},
 base:{ref:'main',repo:{full_name:MAIN_REPO}}
};
const authOpts={repository:MAIN_REPO,expectedSha:mainSha};
test('trusted main: sole owner of exact repository does not need a second human approver',()=>{
 assert.equal(isAuthorizedSoloMaintainerPR(ownerPr,authOpts),true);
});
test('solo-owner exception fails closed for forks, bots, external authors and stale SHA',()=>{
 const cases=[
  {...ownerPr,state:'closed'},
  {...ownerPr,user:{login:'random-contributor',type:'User'}},
  {...ownerPr,user:{login:'cardenaspiero255-lang',type:'Bot'}},
  {...ownerPr,author_association:'COLLABORATOR'},
  {...ownerPr,head:{...ownerPr.head,repo:{full_name:'external/fork'}}},
  {...ownerPr,base:{...ownerPr.base,ref:'develop'}},
  {...ownerPr,head:{...ownerPr.head,sha:'b'.repeat(40)}},
  {...ownerPr,user:null},
  {...ownerPr,head:{sha:mainSha}},
  {...ownerPr,base:{ref:'main'}},
 ];
 for(const entry of cases)assert.equal(isAuthorizedSoloMaintainerPR(entry,authOpts),false);
 assert.equal(isAuthorizedSoloMaintainerPR(ownerPr,{...authOpts,repository:'someone/other'}),false);
 assert.equal(isAuthorizedSoloMaintainerPR(ownerPr,{...authOpts,expectedSha:'invalid'}),false);
});
