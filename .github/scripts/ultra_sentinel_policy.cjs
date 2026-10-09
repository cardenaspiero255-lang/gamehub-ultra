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
 'package.json','package-lock.json'
]);
const critical=new Set(PROTECTED_FILES);
function evaluateProtectedChanges(files,{expectedCount}={}){
 if(!Array.isArray(files)||!Number.isSafeInteger(expectedCount)||expectedCount<0||
  files.length!==expectedCount||files.some(f=>!f||typeof f.filename!=='string'||
   typeof f.status!=='string')){
  return {status:'INCOMPLETE',removed:[],partial:true};
 }
 const removed=[];
 for(const f of files){
  const old=f.status==='renamed'?f.previous_filename:f.filename;
  if((f.status==='removed'||f.status==='renamed')&&critical.has(old))
   removed.push(old);
 }
 return {status:removed.length?'BLOCKED':'OK',removed,partial:false};
}
module.exports={evaluateProtectedChanges,PROTECTED_FILES};
