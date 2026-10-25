'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const workflows=[
 {name:'Ultra Sentinel - Independent Read-only Review',
  file:'ultra-sentinel-independent-review.yml',
  accepted:'report.scopedAccepted===true'},
 {name:'Ultra Sentinel - Automatic CAR Review',
  file:'ultra-sentinel-auto-review.yml',
  accepted:'report.frontier?.scopedAccepted===true'}
];
for(const spec of workflows){
 test(spec.name+' post-upload reauth permits only PR172 and PR221 with current identity',()=>{
  const yml=fs.readFileSync(path.join(__dirname,'../.github/workflows',spec.file),'utf8');
  const sections=yml.split('      - name: Reauthenticate scoped evidence after artifact upload');
  assert.equal(sections.length,2,'protected post-upload step must exist exactly once');
  const step=sections[1];
  assert.ok(step.includes(spec.accepted),'must require pre-upload attestation');
  assert.ok(step.includes('if(![172,221].includes(report.pr)||!fs.existsSync(scopePath)){'),
   'must authorize exactly these two PRs and require main trusted module');
  assert.equal((step.match(/pull_number:report\.pr/g)||[]).length,2,
   'both GitHub API calls must reauthenticate the same allowlisted PR');
  assert.ok(step.includes("const {attestScopedPr172}=require(scopePath);"),
    'must invoke verified trusted scoped reviewer');
  assert.ok(step.includes("if(pr.head.sha!==report.sha)"),
    'must reject stale original PR SHA');
  assert.ok(step.includes("current.status!=='SCOPED_AUTHENTICATED_MANUAL_REVIEW'"),
    'must require authenticated receipt');
  assert.ok(step.includes("stored?.status!=='SCOPED_AUTHENTICATED_MANUAL_REVIEW'"));
  for(const k of ['headSha','mainSha','protectedRunId','protectedRunAttempt']){
   assert.ok(step.includes("'"+k+"'"), 'missing receipt binding '+k);
  }
  assert.ok(step.includes('current[field]!==stored[field]'),
    'must reject superseded protected SHA / attempt');
  assert.ok(step.includes("core.setFailed('Post-upload attestation changed:"),
    'fail closed when receipt changes');
  assert.equal((step.match(/pull_number:172/g)||[]).length,0,
    'must never refetch PR172 while checking PR221');
 });
}
