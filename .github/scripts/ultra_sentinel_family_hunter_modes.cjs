'use strict';
// Presentation only: consume the existing Core analysis, never write to GitHub.
function resolveMode(requested){
 let mode='REVIEW_WITH_SOLUTIONS',status='READ_ONLY';
 if(requested==='REVIEW_ONLY')mode='REVIEW_ONLY';
 else if(requested==='REPAIR_COMMITS_AUTHORIZED')status='AUTHORIZATION_REQUIRED';
 else if(requested!==undefined&&requested!=='REVIEW_WITH_SOLUTIONS')status='INVALID_MODE';
 return {mode,status,repairCommits:'OFF',autoCommitAllowed:false,autoMergeAllowed:false};
}
// The adapter is not an attestation authority. It rejects structurally
// unverifiable inputs rather than converting them into a clean-looking report.
// Real Core analysis may legitimately be INCOMPLETE; retain that verdict.
function completeShape(analysis){
 if(!analysis||typeof analysis!=='object'||Array.isArray(analysis))return false;
 const coverage=analysis.coverage,rem=analysis.remediations;
 const plan=analysis.validationPlan,findings=analysis.findings;
 if(!coverage||typeof coverage!=='object'||Array.isArray(coverage)||
    !rem||typeof rem!=='object'||Array.isArray(rem)||
    !plan||typeof plan!=='object'||Array.isArray(plan)||
    !Array.isArray(findings)||findings.length>40||
    !Array.isArray(analysis.warnings)||
    !Array.isArray(rem.suggestions)||!Array.isArray(plan.domains)||
    !Array.isArray(plan.checks))return false;
 if(typeof analysis.engine!=='string'||!analysis.engine.trim()||
    typeof analysis.version!=='string'||!analysis.version.trim()||
    typeof analysis.sha!=='string'||!/^[a-f0-9]{40}$/i.test(analysis.sha)||
    rem.sha!==analysis.sha||rem.verified!==false||rem.autoMerge!==false)return false;
 if(!Number.isSafeInteger(coverage.returned)||coverage.returned<0||
    !Number.isSafeInteger(coverage.analyzed)||coverage.analyzed<0||
    coverage.analyzed>coverage.returned||typeof coverage.partial!=='boolean'||
    !Number.isSafeInteger(analysis.omitted)||analysis.omitted<0)return false;
 if(!['INCOMPLETE','REVIEW_REQUIRED','NO_CRITICAL_PATTERN'].includes(analysis.verdict)||
    coverage.partial!==(analysis.verdict==='INCOMPLETE'))return false;
 // Core can legitimately skip documentation/test-only/deletion-only files.
 // Keep nonempty PR coverage, but never equate zero production scans with
 // malformed analysis when Core explicitly reports a complete verdict.
 if(!coverage.partial&&coverage.returned===0)return false;
 // Core evidence fields are part of the security contract. The read-only
 // adapter must never certify a finding whose evidence/provenance was dropped
 // while moving between report formats or truncated by another component.
 if(findings.some(f=>!f||typeof f!=='object'||Array.isArray(f)||
    typeof f.rule!=='string'||!f.rule||
    !['BLOCKER','HIGH','MEDIUM','LOW'].includes(f.severity)||
    typeof f.path!=='string'||!Number.isSafeInteger(f.line)||f.line<0||
    !['high','medium','low'].includes(f.confidence)||
    typeof f.evidence!=='string'||!f.evidence.trim()||
    typeof f.reason!=='string'||!f.reason.trim()||
    typeof f.verification!=='string'||!f.verification.trim()||
    !Array.isArray(f.domains)||
    f.domains.some(d=>typeof d!=='string'||!d.trim())||
    f.origin!=='Ultra Sentinel Core'||
    f.status!=='NEEDS_VERIFICATION'))
   return false;
 const hasCritical=findings.some(f=>f.severity==='BLOCKER'||f.severity==='HIGH');
 if(analysis.verdict==='NO_CRITICAL_PATTERN'&&(hasCritical||analysis.omitted>0))
   return false;
 if(analysis.verdict==='REVIEW_REQUIRED'&&!hasCritical)return false;
 // The existing planner yields one draft per finding, capped at 35.
 // A missing, extra or misplaced proposal could mask a real risk or
 // associate another file's repair with an unrelated finding.
 if(rem.suggestions.length!==Math.min(findings.length,35))return false;
 for(let i=0;i<findings.length;i++){
  const finding=findings[i];
  // Core orders *all* findings by descending severity, then filename,
  // then ascending line. The portable report exposes only ten entries;
  // accepting permutations can hide a HIGH/BLOCKER in its omitted suffix.
  if(i>0){
   const previous=findings[i-1];
   const left=previous.severity==='BLOCKER'?4:
    previous.severity==='HIGH'?3:previous.severity==='MEDIUM'?2:1;
   const right=finding.severity==='BLOCKER'?4:
    finding.severity==='HIGH'?3:finding.severity==='MEDIUM'?2:1;
   if(left<right||(left===right&&(
      previous.path.localeCompare(finding.path)>0||
      (previous.path===finding.path&&previous.line>finding.line))))
    return false;
  }
  for(let j=0;j<i;j++){
   const previous=findings[j];
   if(finding.rule===previous.rule&&finding.path===previous.path&&
      finding.line===previous.line)return false;
  }
  if(i>=35)continue;
  const suggestion=rem.suggestions[i];
  if(!suggestion||typeof suggestion!=='object'||Array.isArray(suggestion)||
     suggestion.rule!==finding.rule||suggestion.path!==finding.path||
     suggestion.line!==finding.line||suggestion.severity!==finding.severity||
     suggestion.status!=='DRAFT_REQUIRES_VALIDATION'||suggestion.autofix!==false||
     typeof suggestion.title!=='string'||!suggestion.title.trim()||
     typeof suggestion.test!=='string'||!suggestion.test.trim()||
     typeof suggestion.caution!=='string'||!suggestion.caution.trim()||
     !Array.isArray(suggestion.steps)||suggestion.steps.length===0||
     suggestion.steps.some(step=>typeof step!=='string'||!step.trim()))
    return false;
 }
 return true;
}
function present(analysis,requested){
 if(!completeShape(analysis))throw Error('INCOMPLETE_ANALYSIS');
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
