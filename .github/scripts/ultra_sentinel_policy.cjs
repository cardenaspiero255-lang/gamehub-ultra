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
// Protect the entire executable GitHub automation trust domain, including
// future helpers, local actions, contracts and new workflow names.
function isProtectedPath(path){
 return typeof path==='string'&&
  (critical.has(path)||path.startsWith('.github/'));
}
function evaluateProtectedChanges(files,{expectedCount}={}){
 // A green gate must NOT certify a PR that modifies its own enforcer.
 // There is no safe self-approval in a candidate branch: a trusted reviewer
 // and explicit maintainer approval are required for any critical edit.
 const incomplete={status:'INCOMPLETE',removed:[],modified:[],partial:true};
 const allowed=new Set(['added','modified','removed','renamed','copied','unchanged']);
 if(!Array.isArray(files)||!Number.isSafeInteger(expectedCount)||expectedCount<0||
   files.length!==expectedCount||files.some(f=>!f||typeof f.filename!=='string'||
    typeof f.status!=='string'||!allowed.has(f.status)||
    ['renamed','copied'].includes(f.status)&&typeof f.previous_filename!=='string')){
  return incomplete;
 }
 // Duplicates can hide omitted files in an otherwise complete API response.
 if(new Set(files.map(f=>f.filename)).size!==files.length)return incomplete;
 const removed=[],modified=[];
 for(const f of files){
  const old=['renamed','copied'].includes(f.status)?f.previous_filename:f.filename;
  const oldProtected=isProtectedPath(old),newProtected=isProtectedPath(f.filename);
  if(f.status==='removed'&&newProtected)removed.push(f.filename);
  else if(f.status==='renamed'&&oldProtected)removed.push(old);
  else if(f.status==='renamed'&&newProtected)modified.push(f.filename);
  else if(f.status==='copied'&&(oldProtected||newProtected))modified.push(f.filename);
  else if(['modified','added'].includes(f.status)&&newProtected)modified.push(f.filename);
 }
 return {status:removed.length?'BLOCKED':modified.length?'REVIEW_REQUIRED':'OK',
   removed,modified,partial:false};
}

/* Reviews are read-only GitHub API records fetched by a workflow checked out
 * from trusted main. A candidate PR cannot provide its own review claims.
 * This is an evidence prerequisite, not a substitute for branch rulesets.
 */
function hasIndependentHumanApproval(reviews,{sha,author}={}){
 if(!Array.isArray(reviews)||!/^[a-f0-9]{40}$/i.test(sha||'')||
  typeof author!=='string'||!author.trim())return false;
 const latest=new Map();
 for(const review of reviews){
  const user=review?.user;
  if(!user||user.type!=='User'||typeof user.login!=='string'||
   user.login.toLowerCase()===author.toLowerCase()||
   !['OWNER','MEMBER','COLLABORATOR'].includes(review.author_association)||
   !['APPROVED','CHANGES_REQUESTED','DISMISSED'].includes(review.state)||
   !Number.isSafeInteger(review.id)||review.id<=0)continue;
  const login=user.login.toLowerCase(),prev=latest.get(login);
  if(!prev||review.id>prev.id)latest.set(login,review);
 }
 const current=[...latest.values()];
 // Any outstanding request for changes invalidates prior approvals.
 if(current.some(review=>review.state==='CHANGES_REQUESTED'))return false;
 return current.some(review=>review.state==='APPROVED'&&
  typeof review.commit_id==='string'&&review.commit_id.toLowerCase()===sha.toLowerCase());
}

/**
 * Explicit SHA-scoped owner acknowledgement for a verified solo-maintainer
 * repository. It does NOT authorize merging or replace a trusted-main scan.
 * Only independent trusted-main workflows may consume this GitHub API evidence.
 */
const SOLO_REPO='cardenaspiero255-lang/gamehub-ultra';
function hasSoloOwnerAcknowledgement(comments,{repo,prNumber,sha,author}={}){
 if(repo!==SOLO_REPO||author?.toLowerCase()!==SOLO_REPO.split('/')[0]||
    !Number.isSafeInteger(prNumber)||prNumber<=0||
    !/^[a-f0-9]{40}$/i.test(sha||'')||!Array.isArray(comments)||
    comments.length>1000)return false;
 const expected='ULTRA-SENTINEL-SOLO-ACK PR#'+prNumber+' SHA='+sha.toLowerCase();
 return comments.some(c=>c&&Number.isSafeInteger(c.id)&&c.id>0&&
  c.user?.type==='User'&&
  c.user.login?.toLowerCase()===author.toLowerCase()&&
  c.author_association==='OWNER'&&
  typeof c.body==='string'&&c.body.trim()===expected);
}


/** Check GitHub's most recent result for each required trusted check.
 * A prior green check must never override a subsequent failure/cancellation.
 * Exact enumeration is required: no partial/paginated evidence is accepted.
 */
function hasAllLatestTrustedChecks(response,required){
 const items=response?.check_runs;
 if(!Array.isArray(items)||!Number.isSafeInteger(response.total_count)||
    response.total_count!==items.length||items.length>100||
    !Array.isArray(required)||required.length===0||
    required.some(name=>typeof name!=='string'||!name))return false;
 for(const name of required){
  const matching=items.filter(x=>x?.name===name&&Number.isSafeInteger(x.id)&&x.id>0)
   .sort((a,b)=>b.id-a.id);
  if(matching.length===0||matching[0].status!=='completed'||
     matching[0].conclusion!=='success')return false;
 }
 return true;
}

module.exports={evaluateProtectedChanges,PROTECTED_FILES,isProtectedPath,hasIndependentHumanApproval,hasSoloOwnerAcknowledgement,hasAllLatestTrustedChecks};
