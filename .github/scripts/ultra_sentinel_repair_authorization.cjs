'use strict';
/**
 * Authorization PREFLIGHT, not a code writer. A repair commit can be proposed
 * only after trusted server-side verification of a scoped, short-lived user
 * authorization, immutable HEAD, fresh tests, and an independent human review.
 * Neither this module nor its return value grants a GitHub write permission.
 */
const SHA=/^[a-f0-9]{40}$/i;
const REPO='cardenaspiero255-lang/gamehub-ultra';
const OWNER='cardenaspiero255-lang';
const AGE_MS=15*60*1000;
const blocked=reason=>Object.freeze({status:'BLOCKED',reason,
  autoCommitAllowed:false,autoMergeAllowed:false,manualCommitRequired:true});
function approvedPath(file){
 if(typeof file!=='string'||file.length>240||file.includes('..')||
    file.includes('\\')||file.startsWith('/'))return false;
 // Explicitly allow only ordinary Android code and tests, never manifest,
 // Gradle, scripts, workflows, Supabase, policies or deployment controls.
 return /^app\/src\/(?:main\/java|test|androidTest)\/[A-Za-z0-9_./-]+\.(?:kt|java)$/.test(file);
}
async function preflightRepair(ctx,verifyAuthorization){
 if(!ctx||typeof ctx!=='object'||ctx.repository!==REPO||
    ctx.headRepo!==REPO||!Number.isSafeInteger(ctx.number)||ctx.number<1||
    typeof ctx.headSha!=='string'||!SHA.test(ctx.headSha)||
    ctx.draft!==false||
    !Number.isSafeInteger(ctx.changedFiles)||ctx.changedFiles<1||
    ctx.changedFiles>50||!Array.isArray(ctx.files)||
    ctx.files.length!==ctx.changedFiles||
    !ctx.files.every(approvedPath)||new Set(ctx.files).size!==ctx.files.length)
   return blocked('UNTRUSTED_OR_PRIVILEGED_CHANGE_SET');
 if(ctx.checkInventoryComplete!==true||
    !Array.isArray(ctx.requiredChecks)||!ctx.requiredChecks.length||
    ctx.requiredChecks.length>100||
    !Array.isArray(ctx.checks)||!ctx.checks.length||ctx.checks.length>500||
    !Number.isSafeInteger(ctx.now)||ctx.now<=0||
    typeof verifyAuthorization!=='function')return blocked('MISSING_VERIFIABLE_GATES');
 const names=new Set();
 for(const item of ctx.checks){
  if(!item||typeof item.name!=='string'||!item.name.trim()||
     names.has(item.name)||item.sha!==ctx.headSha||
     item.status!=='completed'||item.conclusion!=='success')
   return blocked('CHECK_PENDING_FAILED_OR_STALE');
  names.add(item.name);
 }
 if(ctx.requiredChecks.some(name=>typeof name!=='string'||!names.has(name)))
  return blocked('REQUIRED_CHECK_MISSING');
 let att;
 try{
  att=await verifyAuthorization({repository:REPO,prNumber:ctx.number,
   sha:ctx.headSha,scope:'repair:commit-to-pr-branch'});
 }catch(_){return blocked('AUTHORITY_UNAVAILABLE');}
 if(!att||typeof att!=='object'||att.repository!==REPO||
    att.prNumber!==ctx.number||att.sha!==ctx.headSha||
    att.actor!==OWNER||att.scope!=='repair:commit-to-pr-branch'||
    att.signedApprovalVerified!==true||att.independentReviewVerified!==true||
    att.nonceUnused!==true||
    !Number.isSafeInteger(att.issuedAt)||att.issuedAt>ctx.now||
    ctx.now-att.issuedAt>AGE_MS)return blocked('MISSING_OR_STALE_AUTHORIZATION');
 return Object.freeze({status:'ELIGIBLE_FOR_HUMAN_COMMIT',
  sha:ctx.headSha,repository:REPO,pr:ctx.number,
  autoCommitAllowed:false,autoMergeAllowed:false,manualCommitRequired:true,
  note:'No writes. Human must reverify HEAD, checks, nonce and scope at commit time.'});
}
module.exports={REPO,AGE_MS,approvedPath,preflightRepair};
