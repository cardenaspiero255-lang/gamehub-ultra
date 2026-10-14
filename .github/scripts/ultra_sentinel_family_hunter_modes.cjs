'use strict';
// Presentation only: consume the existing Core analysis, never write to GitHub.
function resolveMode(requested){
 let mode='REVIEW_WITH_SOLUTIONS',status='READ_ONLY';
 if(requested==='REVIEW_ONLY')mode='REVIEW_ONLY';
 else if(requested==='REPAIR_COMMITS_AUTHORIZED')status='AUTHORIZATION_REQUIRED';
 else if(requested!==undefined&&requested!=='REVIEW_WITH_SOLUTIONS')status='INVALID_MODE';
 return {mode,status,repairCommits:'OFF',autoCommitAllowed:false,autoMergeAllowed:false};
}
// Historical repair evidence is NOT a verified attestation source here.
// Core 2.0.0 searches only built-in curated playbooks (extra=[]), never a
// user-contributed verified_fix. Match the actual curated source content to
// prevent a copied ID or fabricated "confirmed_tests" label becoming proof.
const {KNOWN:CURATED_MEMORY}=require('./ultra_sentinel_memory.cjs');
const CORE_DOMAIN_FILES=Object.freeze({
 voice:'speech/notes.md',ai:'frontier/notes.md',
 network:'dns/notes.md',performance:'thermal/notes.md',
 security:'auth/notes.md',ci:'.github/workflows/notes.md',
 android:'app/src/main/README.md'
});
const CORE_DOMAINS=new Set(Object.keys(CORE_DOMAIN_FILES));
function validValidationPlan(plan){
 if(!Array.isArray(plan.domains)||!Array.isArray(plan.checks)||
    plan.domains.length>CORE_DOMAINS.size||plan.checks.length>24||
    plan.domains.some(d=>typeof d!=='string'||!CORE_DOMAINS.has(d))||
    new Set(plan.domains).size!==plan.domains.length||
    plan.domains.some((d,i)=>i>0&&plan.domains[i-1]>d))return false;
 // Ask the actual pinned Core 2.0.0 producer for its canonical check list.
 // All filenames are our own inert documentation fixtures, never PR paths.
 // This avoids changing protected Core code or duplicating its rules.
 const {analyze}=require('./ultra_sentinel_core.cjs');
 const actual=analyze(plan.domains.map(d=>({
  filename:CORE_DOMAIN_FILES[d],status:'added',changes:0,patch:''
 })),{sha:'0'.repeat(40)}).validationPlan;
 return actual.domains.length===plan.domains.length&&
  actual.domains.every((d,i)=>d===plan.domains[i])&&
  plan.checks.length===actual.checks.length&&plan.checks.every((item,i)=>
   item&&typeof item==='object'&&!Array.isArray(item)&&
   item.domain===actual.checks[i].domain&&
   item.check===actual.checks[i].check);
}
function validRelatedEvidence(items){
 if(!Array.isArray(items)||items.length>2)return false;
 const seen=new Set();
 return items.every(item=>{
  if(!item||typeof item!=='object'||Array.isArray(item)||
     item.type!=='playbook'||item.verification!=='curated_playbook'||
     item.evidenceUrl!==null||
     typeof item.id!=='string'||seen.has(item.id)||
     typeof item.score!=='number'||!Number.isFinite(item.score)||item.score<=0||
     !Array.isArray(item.tests))return false;
  seen.add(item.id);
  const entry=CURATED_MEMORY.find(x=>x.id===item.id);
  if(!entry||item.domain!==entry.domain||item.rule!==entry.rule||
     item.advice!==entry.advice.slice(0,380))return false;
  const tests=entry.tests.slice(0,4).map(t=>String(t).slice(0,180));
  return item.tests.length===tests.length&&
   item.tests.every((t,i)=>t===tests[i]);
 });
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
    !Array.isArray(rem.suggestions)||!validValidationPlan(plan))return false;
 // Accept only the exact trusted Core/planner protocol. A plausible SHA and
 // correctly shaped containers do not prove an analysis came from Core.
 // Future protocol upgrades must explicitly update and retest this adapter.
 if(analysis.engine!=='Ultra Sentinel Core'||analysis.version!=='2.0.0'||
    rem.engine!=='Ultra Sentinel Repair Planner'||rem.version!=='2.0.0'||
    rem.scope!=='human-supervised'||
    typeof analysis.sha!=='string'||!/^[a-f0-9]{40}$/i.test(analysis.sha)||
    rem.sha!==analysis.sha||rem.verified!==false||rem.autoMerge!==false)return false;
 // Core scans the first 300 files but reports the original list.length.
 // More than 300 returned files is legitimate ONLY when coverage is partial,
 // the verdict is INCOMPLETE and no more than 300 files were analyzed.
 if(!Number.isSafeInteger(coverage.returned)||coverage.returned<0||
    !Number.isSafeInteger(coverage.analyzed)||coverage.analyzed<0||
    coverage.analyzed>coverage.returned||coverage.analyzed>300||
    typeof coverage.partial!=='boolean'||
    (coverage.returned>300&&!coverage.partial)||
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
    typeof f.rule!=='string'||!f.rule.trim()||
    !['BLOCKER','HIGH','MEDIUM','LOW'].includes(f.severity)||
    typeof f.path!=='string'||!f.path.trim()||!Number.isSafeInteger(f.line)||f.line<0||
    !['high','medium','low'].includes(f.confidence)||
    typeof f.evidence!=='string'||!f.evidence.trim()||
    typeof f.reason!=='string'||!f.reason.trim()||
    typeof f.verification!=='string'||!f.verification.trim()||
    !Array.isArray(f.domains)||
    f.domains.some(d=>typeof d!=='string'||!d.trim())||
    f.origin!=='Ultra Sentinel Core'||
    f.status!=='NEEDS_VERIFICATION'))
   return false;
 // A syntactically canonical checklist can still be truncated by removing
 // both a domain and its checks. Every Core finding must remain represented
 // in the plan's domain set or no review may be presented as complete.
 if(findings.some(f=>f.domains.some(d=>!plan.domains.includes(d))))
   return false;
 const hasCritical=findings.some(f=>f.severity==='BLOCKER'||f.severity==='HIGH');
 // Core can legitimately exceed its 40-finding display cap with only
 // MEDIUM/LOW alerts. Preserve that authentic verdict: the portable
 // report treats omitted>0 as incomplete, never a certified clean result.
 // Core only increases omitted after filling all 40 visible slots.
 // Reject malformed/truncated prefixes even if their severity seems benign.
 if(analysis.omitted>0&&findings.length!==40)return false;
 // Contradictory visible HIGH/BLOCKER findings must still fail closed.
 if(analysis.verdict==='NO_CRITICAL_PATTERN'&&hasCritical)
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
  if(i>=35)continue;
  const suggestion=rem.suggestions[i];
  if(!suggestion||typeof suggestion!=='object'||Array.isArray(suggestion)||
     suggestion.rule!==finding.rule||suggestion.path!==finding.path||
     suggestion.line!==finding.line||suggestion.severity!==finding.severity||
     suggestion.status!=='DRAFT_REQUIRES_VALIDATION'||suggestion.autofix!==false||
     typeof suggestion.title!=='string'||!suggestion.title.trim()||
     typeof suggestion.test!=='string'||!suggestion.test.trim()||
     typeof suggestion.handoff!=='string'||!suggestion.handoff.trim()||
     !validRelatedEvidence(suggestion.relatedEvidence)||
     typeof suggestion.caution!=='string'||!suggestion.caution.trim()||
     !Array.isArray(suggestion.steps)||suggestion.steps.length===0||
     suggestion.steps.some(step=>typeof step!=='string'||!step.trim()))
    return false;
 }
 return true;
}
function hasAmbiguousIdentities(findings){
 const known=new Set();
 for(const f of findings){
  const key=JSON.stringify([f.rule,f.path,f.line]);
  if(known.has(key))return true;
  known.add(key);
 }
 return false;
}
function present(analysis,requested){
 if(!completeShape(analysis))throw Error('INCOMPLETE_ANALYSIS');
 const collision=hasAmbiguousIdentities(analysis.findings);
 const familyHunter=resolveMode(requested);
 // Core 2.0.0 may truncate distinct long paths to the same visible ID.
 // Never claim "clean", offer repairs or authorize a write in that case.
 if(collision)familyHunter.status='INCOMPLETE_ANALYSIS';
 const only=familyHunter.mode==='REVIEW_ONLY'||collision;
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
  mode:analysis.mode,
  coverage:collision?{...analysis.coverage,partial:true}:analysis.coverage,
  validationPlan,remediations,findings,omitted:analysis.omitted,
  warnings:collision?[...analysis.warnings.slice(0,24),
   'Rutas sanitizadas ambiguas: revisar fuentes originales y pruebas completas.']:
   analysis.warnings,
  verdict:collision?'INCOMPLETE':analysis.verdict,
  note:analysis.note,familyHunter
 };
}
module.exports={resolveMode,present};
