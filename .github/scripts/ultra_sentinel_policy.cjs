'use strict';
/* Security-critical definitions must remain present and independent from PR
 * changes. A PR can alter its *own* YAML; trusted main review checks deletions.
 * A GitHub branch ruleset requiring the trusted review check is still needed. */
const PROTECTED_FILES=Object.freeze([
 '.github/workflows/ultra-sentinel-auto-review.yml',
 '.github/workflows/ultra-sentinel-core-check.yml',
 '.github/workflows/ultra-sentinel-independent-review.yml',
 '.github/workflows/ultra-sentinel-self-review.yml',
 '.github/workflows/ultra-sentinel-mutation.yml',
 '.github/workflows/ultra-sentinel-sss-post-ci.yml',
 '.github/workflows/ultra-sentinel-reliability-100.yml',
 '.github/workflows/android.yml',
 '.github/workflows/coverage.yml',
 '.github/scripts/ultra_sentinel_yaml_ast.cjs',
 '.github/scripts/ultra_sentinel_policy.cjs',
 'package.json','package-lock.json',
  '.github/workflows/ultra-sentinel-repair-proposals.yml',
  '.github/workflows/ultra-sentinel-sentry-incidents.yml',
  '.github/scripts/ultra_sentinel_core.cjs',
  '.github/scripts/ultra_sentinel_frontier_integrity.cjs',
  '.github/scripts/ultra_sentinel_supply_chain.cjs',
  '.github/scripts/ultra_sentinel_orchestrator.cjs',
  '.github/scripts/ultra_sentinel_evidence.cjs',
  '.github/scripts/ultra_sentinel_sentry_ingest.cjs'
]);
const critical=new Set(PROTECTED_FILES);
function evaluateProtectedChanges(files,{expectedCount}={}){
 // A green gate must NOT certify a PR that modifies its own enforcer.
 // There is no safe self-approval in a candidate branch: a trusted reviewer
 // and explicit maintainer approval are required for any critical edit.
 const incomplete={status:'INCOMPLETE',removed:[],modified:[],partial:true};
 const allowed=new Set(['added','modified','removed','renamed','copied','unchanged']);
 if(!Array.isArray(files)||!Number.isSafeInteger(expectedCount)||expectedCount<0||
   files.length!==expectedCount||files.some(f=>!f||typeof f.filename!=='string'||
    typeof f.status!=='string'||!allowed.has(f.status)||
    f.status==='renamed'&&typeof f.previous_filename!=='string')){
  return incomplete;
 }
 const removed=[],modified=[];
 for(const f of files){
  const old=f.status==='renamed'?f.previous_filename:f.filename;
  if((f.status==='removed'||f.status==='renamed')&&critical.has(old))
   removed.push(old);
  else if(['modified','added','copied'].includes(f.status)&&
          (critical.has(old)||critical.has(f.filename)))
   modified.push(f.filename);
 }
 return {status:removed.length?'BLOCKED':modified.length?'REVIEW_REQUIRED':'OK',
   removed,modified,partial:false};
}
module.exports={evaluateProtectedChanges,PROTECTED_FILES};
