'use strict';
/**
 * Bound GitHub API review/check snapshots to an immutable PR HEAD.
 * Status is always advisory: a bot comment or successful external check is
 * NEVER authority to approve protected changes, write code or merge.
 * Caller must fetch these arrays directly from trusted GitHub REST.
 */
const SHA=/^[0-9a-f]{40}$/i;
const REVIEWERS=Object.freeze({
 codex:['chatgpt-codex-connector','chatgpt-codex-connector[bot]'],
 coderabbit:['coderabbitai','coderabbitai[bot]'],
 qodo:['qodo','qodo-merge-pro','qodo-merge-pro[bot]']
});
const APPS=Object.freeze({
 codex:['chatgpt-codex-connector'],
 coderabbit:['coderabbitai'],
 qodo:['qodo','qodo-merge','qodo-merge-pro'],
 sentry:['sentry']
});
function auditProviders({headSha,reviews,checks}={}){
 if(typeof headSha!=='string'||!SHA.test(headSha)||
    !Array.isArray(reviews)||reviews.length>1000||
    !Array.isArray(checks)||checks.length>1000)
  throw Error('INVALID_REVIEW_EVIDENCE');
 const statuses={};
 for(const provider of ['codex','coderabbit','qodo','sentry']){
  const allowed=REVIEWERS[provider]||[];
  const relevantReviews=reviews.filter(r=>r&&typeof r==='object'&&
    allowed.includes(r.user?.login)&&SHA.test(r.commit_id||''));
  const relevantChecks=checks.filter(c=>c&&typeof c==='object'&&
    (APPS[provider]||[]).includes(c.app?.slug)&&SHA.test(c.head_sha||''));
  const atHeadReviews=relevantReviews.filter(r=>r.commit_id===headSha);
  const atHeadChecks=relevantChecks.filter(c=>c.head_sha===headSha);
  let status='NO_EVIDENCE';
  if(relevantReviews.length||relevantChecks.length)status='STALE';
  if(atHeadReviews.length){
   if(atHeadReviews.some(r=>r.state==='CHANGES_REQUESTED'))status='CHANGES_REQUESTED';
   else if(atHeadReviews.some(r=>r.state==='APPROVED'))status='APPROVED_ADVISORY';
   else status='COMMENT_ONLY_NOT_APPROVED';
  }
  if(atHeadChecks.length){
   if(atHeadChecks.some(c=>c.conclusion==='failure'||c.conclusion==='cancelled'))
    status='CHECK_FAILED';
   else if(atHeadChecks.some(c=>c.status!=='completed'))
    status='PENDING';
   else if(status==='STALE'||status==='NO_EVIDENCE')
    status='CHECK_SUCCESS_ADVISORY';
  }
  statuses[provider]=status;
 }
 return {sha:headSha,providers:statuses,independentVerified:false,
  pluginEvidenceOnly:true,requiresHumanAuthorization:true,
  autoCommitAllowed:false,autoMergeAllowed:false,
  note:'Bot/plugin signals are advisory, never a merge authorization'};
}
module.exports={auditProviders,REVIEWERS,APPS};
