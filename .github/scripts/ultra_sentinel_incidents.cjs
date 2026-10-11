'use strict';
/*
 * Ultra Sentinel incident intelligence (offline, bounded, privacy first).
 * Untrusted Sentry responses are data, not instructions. Never export raw titles,
 * stacks, device identifiers, networks, users, audio, access tokens or prompts.
 * Correlations and rollback suggestions are ADVISORY, never confirmed causes.
 */
const crypto=require('node:crypto');
const SHA=/^[a-f0-9]{40}$/i;
const validSha=value=>typeof value==='string'&&SHA.test(value);
const validLogin=value=>typeof value==='string'&&
 /^[a-z0-9](?:[a-z0-9-]{0,37}[a-z0-9])?$/i.test(value);
const ISO=/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/;
const ID=/^[0-9]{1,18}$/;
const PROJECT=/^[a-z0-9][a-z0-9-]{0,49}$/;
const REPO='cardenaspiero255-lang/gamehub-ultra';
const policy=Object.freeze({
 minIncidents:3,minCount:50,minBaseline:1,minRatio:3,maxIssues:100,
 maxBreadcrumbs:32,maxCountPerIssue:100000,maxRetainedDays:30
});
function validTime(value){
 return typeof value==='string'&&ISO.test(value)&&Number.isFinite(Date.parse(value))&&
  new Date(value).toISOString()===new Date(Date.parse(value)).toISOString()?
  Date.parse(value):null;
}
function digest(project,id){
 return crypto.createHash('sha256').update(project+'\0'+id).digest('hex');
}
function normalizeIssues(records,settings={}){
 const limit=Number.isSafeInteger(settings.maxIssues)&&settings.maxIssues>=1?
  Math.min(policy.maxIssues,settings.maxIssues):policy.maxIssues;
 if(!Array.isArray(records))return [];
 const seen=new Set(),out=[];
 for(const raw of records.slice(0,Math.min(1000,limit*10))){
  if(!raw||typeof raw!=='object'||Array.isArray(raw))continue;
  const project=raw.project?.slug;
  if(!PROJECT.test(project||'')||!ID.test(String(raw.id??'')))continue;
  const num=Number(raw.count);
  if(!/^\d{1,7}$/.test(String(raw.count??''))||!Number.isSafeInteger(num)||
     num<0||num>policy.maxCountPerIssue)continue;
  const t=validTime(raw.lastSeen);
  if(t===null||validTime(raw.firstSeen)===null||validTime(raw.firstSeen)>t)continue;
  const severity=['fatal','error','warning','info'].includes(raw.level)?raw.level:'unknown';
  const fingerprint=digest(project,String(raw.id));
  if(seen.has(fingerprint))continue;
  seen.add(fingerprint);
  out.push(Object.freeze({
   fingerprint,project,severity,count:num,
   lastSeen:new Date(t).toISOString(),bucket:new Date(t).toISOString().slice(0,10)
  }));
  if(out.length>=limit)break;
 }
 return out;
}
function verifiedBuild(build){
 return !!build&&validSha(build.sha)&&build.source==='github-actions'&&
  build.status==='success'&&build.workflow==='Android build'&&
  (!build.repo||build.repo===REPO);
}
function correlateBuilds(incidents,links,builds){
 if(!Array.isArray(incidents)||!Array.isArray(links)||!Array.isArray(builds))return [];
 const accepted=new Set(builds.slice(0,100).filter(verifiedBuild).map(b=>b.sha.toLowerCase()));
 const fingerprinted=new Set(incidents.slice(0,policy.maxIssues).map(x=>x.fingerprint));
 const out=[],seen=new Set();
 for(const row of links.slice(0,200)){
  if(!row||!ID.test(String(row.issueId??''))||!validSha(row.sha))continue;
  const sha=row.sha.toLowerCase();
  // Associate only with issues that actually appeared in the allowed input.
  // Project must be explicit in link if caller uses anything other than default.
  const project=row.project||'gamehub-ultra';
  if(!PROJECT.test(project))continue;
  const fp=digest(project,String(row.issueId));
  if(!fingerprinted.has(fp)||!accepted.has(sha)||seen.has(fp))continue;
  seen.add(fp);
  // This validates caller-provided CI metadata, NOT a live GitHub attestation.
  out.push({fingerprint:fp,sha,verification:'ci-input-matched'});
 }
 return out;
}
function checkedPolicy(value){
 if(value===undefined)return policy;
 if(!value||typeof value!=='object'||Array.isArray(value))throw Error('Invalid incident policy');
 const candidate={...policy};
 for(const k of ['minIncidents','minCount','minBaseline','minRatio']){
  if(Object.hasOwn(value,k)){
   if(!Number.isSafeInteger(value[k])||value[k]<1||value[k]>100000)throw Error('Invalid incident policy: '+k);
   candidate[k]=value[k];
  }
 }
 if(candidate.minIncidents<2||candidate.minCount<10||candidate.minRatio<2)
  throw Error('Incident policy must remain conservative');
 return candidate;
}
function analyzeIncidents(incidents,opts={}){
 const limits=checkedPolicy(opts.policy),safe=(Array.isArray(incidents)?incidents:[])
  .filter(x=>x&&PROJECT.test(x.project||'')&&/^[a-f0-9]{64}$/.test(x.fingerprint||'')&&
   Number.isSafeInteger(x.count)&&x.count>=0&&x.count<=policy.maxCountPerIssue)
  .slice(0,policy.maxIssues);
 const unique=new Set(safe.map(x=>x.fingerprint));
 const count=safe.reduce((a,x)=>a+x.count,0);
 const baseline=opts.previousWindowCount;
 const candidate=typeof opts.verifiedReleaseSha==='string'?opts.verifiedReleaseSha:'';
 const trusted=Array.isArray(opts.builds)&&opts.builds.some(b=>verifiedBuild(b)&&
  b.sha.toLowerCase()===candidate.toLowerCase());
 const enough=unique.size>=limits.minIncidents&&count>=limits.minCount;
 const hasBaseline=Number.isSafeInteger(baseline)&&baseline>=limits.minBaseline;
 const spike=hasBaseline&&count>=baseline*limits.minRatio;
 const canAdvise=enough&&spike&&trusted&&opts.consent===true;
 const decision=canAdvise?'INVESTIGATE_ROLLBACK':enough&&hasBaseline?'WATCH':'INSUFFICIENT_EVIDENCE';
 return {
  schema:'ultra-sentinel-incidents/v1',decision,
  incidents:unique.size,observations:count,
  releaseSha:canAdvise?candidate.toLowerCase():null,
  confidence:'HEURISTIC_NOT_CALIBRATED',rootCauseConfirmed:false,automaticRollback:false,
  evidence:hasBaseline?[{
   kind:'aggregate_window_comparison',current:count,baseline,minimumRatio:limits.minRatio,
   spike:!!spike
  }]:[],
  recommendation:canAdvise?
   'Solicitar revisión humana de canary/rollback, confirmar impacto y señales independientes antes de actuar.':
   'Continuar observación sin atribuir causa ni recomendar rollback automático.',
  provenance:'Caller-supplied CI metadata; verify run SHA using GitHub before operational decisions.'
 };
}
const ALLOWED_EVENTS=Object.freeze({
 voice:new Set(['retry','start','stop','cancel','permission_denied','timeout']),
 network:new Set(['connect','disconnect','timeout','retry','unavailable']),
 thermal:new Set(['warm','cool','limit']),
 app:new Set(['foreground','background','crash'])
});
function sanitizeBreadcrumbs(records,{consent=false}={}){
 if(consent!==true||!Array.isArray(records))return [];
 const out=[];
 for(const record of records.slice(0,policy.maxBreadcrumbs)){
  if(!record||typeof record!=='object')continue;
  if(!Object.hasOwn(ALLOWED_EVENTS,record.kind)||
   !ALLOWED_EVENTS[record.kind].has(record.event))continue;
  out.push({kind:record.kind,event:record.event});
 }
 return out;
}
function recordVerifiedRepair(record,now=Date.now()){
 const rejected=reason=>({status:'REJECTED',reason});
 if(!record||typeof record!=='object')return rejected('invalid_record');
 if(typeof record.id!=='string'||!/^[a-z0-9-]{6,80}$/.test(record.id)||
  ![record.fixSha,record.redTestSha,record.greenTestSha].every(validSha))
  return rejected('missing_immutable_provenance');
 if(typeof record.rule!=='string'||!/^[A-Z][A-Z0-9_]{2,75}$/.test(record.rule)||
  record.approval!=='approved'||!validLogin(record.approvedBy)||
  record.approvedBy.length<3)
  return rejected('missing_human_review');
 if(typeof record.evidenceUrl!=='string'||
  !/^https:\/\/github\.com\/cardenaspiero255-lang\/gamehub-ultra\/(?:pull|commit)\/[0-9a-f]+\/?$/i.test(record.evidenceUrl))
  return rejected('untrusted_evidence_url');
 const verified=validTime(record.verifiedAt),expire=validTime(record.expiresAt);
 if(verified===null||expire===null||expire<=verified||expire<=now||
    expire-verified>policy.maxRetainedDays*86400000*2)return rejected('expired_or_invalid_retention');
 if(typeof record.summary!=='string'||record.summary.length<20||record.summary.length>300)
  return rejected('missing_summary');
 // This is a provenance-validated human CLAIM, not external verification of CI/test outcomes.
 return {status:'PROVENANCE_RECORDED',id:record.id,rule:record.rule,
  fixSha:record.fixSha.toLowerCase(),redTestSha:record.redTestSha.toLowerCase(),
  greenTestSha:record.greenTestSha.toLowerCase(),expiresAt:record.expiresAt,
  evidenceUrl:record.evidenceUrl,
  verification:'human-claimed-tests-not-externally-attested',autoApply:false};
}
// Regla estricta CAR/FAMILY-001: no cerrar un error aislado mientras
// existan variantes CONFIRMADAS dentro de su familia causal.
// This validates caller-supplied evidence SHAPE only; independent CI and
// human review must verify that referenced tests genuinely ran on this SHA.
const FAMILY_CATEGORIES=Object.freeze(['original','alternate','boundary','benign_control']);
const MAX_FAMILIES=32,MAX_FAMILY_VARIANTS=200;
function evaluateFamilyResolution(families,{sha}={}){
 const reasons=[];
 const reject=reason=>{if(!reasons.includes(reason))reasons.push(reason);};
 if(!validSha(sha)||!Array.isArray(families)||
   families.length<1||families.length>MAX_FAMILIES){
  reject('error_family_missing_evidence');
  return {status:'BLOCKED',reasons,verified:false};
 }
 const seenFamilies=new Set();
 for(const family of families){
  if(!family||typeof family!=='object'||Array.isArray(family)){
   reject('error_family_invalid_record');continue;
  }
  if(!/^[A-Z][A-Z0-9_]{2,79}$/.test(family.id||'')||
    seenFamilies.has(family.id))reject('error_family_invalid_identity');
  seenFamilies.add(family.id);
  if(typeof family.sha!=='string'||family.sha.toLowerCase()!==sha.toLowerCase())
   reject('error_family_stale_sha');
  if(typeof family.rootCause!=='string'||family.rootCause.trim().length<25||
    family.rootCause.length>600||typeof family.scope!=='string'||
    family.scope.trim().length<20||family.scope.length>500)
   reject('error_family_missing_root_cause_or_scope');
  if(family.unresolvedConfirmed!==0||!Array.isArray(family.knownGaps)||
    family.knownGaps.length!==0||family.unknownSyntax!=='fail_closed')
   reject('error_family_variant_gaps');
  const variants=family.variants;
  if(!Array.isArray(variants)||variants.length<4||
    variants.length>MAX_FAMILY_VARIANTS){
   reject('error_family_missing_variant_coverage');continue;
  }
  const types=new Set(),ids=new Set();
  for(const variant of variants){
   if(!variant||typeof variant!=='object'||Array.isArray(variant)){
    reject('error_family_invalid_variant');continue;
   }
   if(typeof variant.id!=='string'||!/^[a-z0-9][a-z0-9_-]{3,99}$/.test(variant.id)||
     ids.has(variant.id))reject('error_family_duplicate_or_invalid_variant');
   ids.add(variant.id);
   if(!FAMILY_CATEGORIES.includes(variant.kind))reject('error_family_invalid_variant');
   else types.add(variant.kind);
   // Do not accept arbitrary paths, parent-directory hops or claim test runs
   // from non-test source files. Tests may be JavaScript, Python, or Android.
   const path=variant.testFile;
   if(typeof path!=='string'||path.length>240||path.includes('..')||
      path.includes('//')||path.startsWith('/')||
      !/^(?:\.github\/scripts\/|app\/src\/(?:test|androidTest)\/|tests?\/)[A-Za-z0-9_./-]+\.(?:test\.(?:cjs|js|ts|py)|spec\.(?:cjs|js|ts|py)|kt|java)$/.test(path))
    reject('error_family_invalid_test_file');
   if(variant.status!=='passed_after_fix')reject('error_family_unverified_variant');
   if(variant.kind==='original'&&variant.red!=='failed_before_fix')
    reject('error_family_original_red_missing');
  }
  if(FAMILY_CATEGORIES.some(kind=>!types.has(kind)))
   reject('error_family_missing_variant_coverage');
 }
 return {status:reasons.length?'BLOCKED':'VERIFIED_CLAIM',reasons,
  verified:false,familyCount:families.length,
  provenance:'caller-provided structured claim; confirm actual distinct cases, red/green tests, CI SHA and independent reviewer before merge',
  autoMerge:false};
}
function evaluateRepairGate(input){
 const failures=[];
 if(!input||typeof input!=='object')return {status:'BLOCKED',reasons:['invalid_input'],autoMerge:false};
 if(!validSha(input.sha)||!validSha(input.currentSha)||
    input.sha.toLowerCase()!==input.currentSha.toLowerCase())
  failures.push('stale_or_invalid_sha');
 if(!validLogin(input.proposedBy)||!validLogin(input.approvedBy)||
    input.approvedBy.toLowerCase()===input.proposedBy.toLowerCase()||
  input.approval!=='approved')failures.push('no_independent_human_approval');
 if(input.tests?.red!=='failed_before_fix'||input.tests?.green!=='passed_after_fix')
  failures.push('red_green_missing');
 for(const name of ['Android build','Unit Test Coverage','Ultra Sentinel Core Tests'])
  if(input.checks?.[name]!=='success')failures.push('required_check_'+name);
 if(input.independentReview!=='approved')failures.push('independent_review_missing');
 failures.push(...evaluateFamilyResolution(input.familyEvidence,{sha:input.sha}).reasons);
 return {status:failures.length?'BLOCKED':'READY_FOR_HUMAN_MERGE',
  reasons:failures,autoMerge:false,autoDeploy:false,approvedBy:failures.length?null:input.approvedBy};
}
function safeSummary(records){
 const counts={fatal:0,error:0,warning:0,info:0,unknown:0};
 for(const row of (Array.isArray(records)?records:[]).slice(0,100)){
  const severity=Object.hasOwn(counts,row?.severity)?row.severity:'unknown';
  if(Number.isSafeInteger(row?.count)&&row.count>=0&&row.count<=policy.maxCountPerIssue)
   counts[severity]+=row.count;
 }
 return {schema:'ultra-sentinel-summary/v1',counts,rawDataIncluded:false};
}
module.exports={
 policy,normalizeIssues,correlateBuilds,analyzeIncidents,sanitizeBreadcrumbs,
 recordVerifiedRepair,evaluateRepairGate,evaluateFamilyResolution,safeSummary,verifiedBuild
};
