'use strict';
/**
 * Family Hunter presentation policy, backed by the existing Ultra Sentinel
 * detector and remediation planner. No GitHub writes, commits or privilege.
 * A future Mode 3 needs a separate authenticated revocable authorization.
 */
function resolveMode(requested){
 let mode='REVIEW_WITH_SOLUTIONS',status='READ_ONLY';
 if(requested==='REVIEW_ONLY')mode='REVIEW_ONLY';
 else if(requested==='REPAIR_COMMITS_AUTHORIZED')status='AUTHORIZATION_REQUIRED';
 else if(requested!==undefined&&requested!=='REVIEW_WITH_SOLUTIONS')status='INVALID_MODE';
 return {mode,status,repairCommits:'OFF',autoCommitAllowed:false,autoMergeAllowed:false};
}
function present(analysis,requested){
 const familyHunter=resolveMode(requested),only=familyHunter.mode==='REVIEW_ONLY';
 // Malformed engine evidence is incomplete; it never yields CLEAN.
 const input=analysis&&typeof analysis==='object'?analysis:null;
 const valid=!!(input&&Array.isArray(input.findings)&&
  input.coverage&&typeof input.coverage==='object'&&
  typeof input.verdict==='string');
 const source=valid?input:{findings:[],coverage:{partial:true},verdict:'INCOMPLETE'};
 const findings=source.findings.slice(0,40).map(f=>({
  rule:f.rule,severity:f.severity,confidence:f.confidence,path:f.path,
  line:f.line,evidence:f.evidence,reason:f.reason,
  verification:only?'':f.verification,domains:f.domains,
  origin:f.origin,status:f.status
 }));
 const originRem=source.remediations||{};
 const remediations={
  engine:originRem.engine,version:originRem.version,sha:originRem.sha,
  verified:false,autoMerge:false,scope:originRem.scope,
  suggestions:only?[]:Array.isArray(originRem.suggestions)?originRem.suggestions:[]
 };
 const originPlan=source.validationPlan||{};
 const validationPlan={
  domains:Array.isArray(originPlan.domains)?originPlan.domains:[],
  checks:only?[]:Array.isArray(originPlan.checks)?originPlan.checks:[]
 };
 return {
  engine:source.engine,version:source.version,sha:source.sha,
  mode:source.mode,coverage:source.coverage,validationPlan,
  remediations,findings,omitted:source.omitted,
  warnings:source.warnings,verdict:source.verdict,note:source.note,
  familyHunter
 };
}
function portableReport({pr,sha,analysis,mode}){
 const view=present(analysis,mode);
 const report=require('./ultra_sentinel_report.cjs').buildReport({pr,sha,analysis:view});
 return {...report,familyHunter:view.familyHunter};
}
module.exports={resolveMode,present,portableReport};
