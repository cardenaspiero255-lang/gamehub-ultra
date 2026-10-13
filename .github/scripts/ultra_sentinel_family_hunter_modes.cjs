'use strict';
// Presentation only: consume the existing Core analysis, never write to GitHub.
function resolveMode(requested){
 let mode='REVIEW_WITH_SOLUTIONS',status='READ_ONLY';
 if(requested==='REVIEW_ONLY')mode='REVIEW_ONLY';
 else if(requested==='REPAIR_COMMITS_AUTHORIZED')status='AUTHORIZATION_REQUIRED';
 else if(requested!==undefined&&requested!=='REVIEW_WITH_SOLUTIONS')status='INVALID_MODE';
 return {mode,status,repairCommits:'OFF',autoCommitAllowed:false,autoMergeAllowed:false};
}
function present(analysis,requested){
 if(!analysis||!Array.isArray(analysis.findings)||
    !analysis.remediations||!Array.isArray(analysis.remediations.suggestions)||
    !analysis.validationPlan||!Array.isArray(analysis.validationPlan.checks)||
    !analysis.coverage||typeof analysis.verdict!=='string')
   throw Error('INCOMPLETE_ANALYSIS');
 const familyHunter=resolveMode(requested);
 const only=familyHunter.mode==='REVIEW_ONLY';
 const findings=analysis.findings.slice(0,40).map(f=>({
  rule:f.rule,severity:f.severity,confidence:f.confidence,path:f.path,
  line:f.line,evidence:f.evidence,reason:f.reason,
  verification:only?'':f.verification,domains:f.domains,origin:f.origin,status:f.status
 }));
 const remediations={
  engine:analysis.remediations.engine,version:analysis.remediations.version,
  sha:analysis.remediations.sha,verified:false,autoMerge:false,
  scope:analysis.remediations.scope,
  suggestions:only?[]:analysis.remediations.suggestions
 };
 const validationPlan={
  domains:analysis.validationPlan.domains,
  checks:only?[]:analysis.validationPlan.checks
 };
 return {
  engine:analysis.engine,version:analysis.version,sha:analysis.sha,
  mode:analysis.mode,coverage:analysis.coverage,validationPlan,
  remediations,findings,omitted:analysis.omitted,warnings:analysis.warnings,
  verdict:analysis.verdict,note:analysis.note,familyHunter
 };
}
module.exports={resolveMode,present};
