'use strict';
/*
 * Ultra Sentinel Quality Gate: bounded, read-only shadow evaluation.
 * Self-reported human labels are NOT independent authorization evidence.
 * Never promote these results to merge, commit, or production blocking rights.
 */
const {evaluate}=require('./ultra_sentinel_comparative.cjs');
const SHA=/^[a-f0-9]{40}$/i,HEX=/^[a-f0-9]{64}$/i;
const RULE=/^[A-Za-z0-9_.:-]{1,100}$/,PATH=/^[A-Za-z0-9_.\/-]{1,240}$/;
const SEVERITIES=new Set(['BLOCKER','CRITICAL','HIGH','MAJOR','WARNING','MEDIUM','LOW','MINOR','INFO']);
const CLASSES=new Set(['NEW','EXACERBATED','REACTIVATED','EXISTING','MOVED']);
const MIN_POSITIVE=100,MIN_NEGATIVE=100,MIN_FAMILIES=5;

function emptyCalibration(status){
 return {schema:'ultra-sentinel-calibration/v1',mode:'shadow',status,
  metrics:null,positiveCases:0,negativeCases:0,distinctPositiveRules:0,
  evidenceExternallyAttested:false,productionReady:false,
  enforcementEnabled:false,autoMergeAllowed:false};
}
function measureCalibration(input){
 if(!input||typeof input!=='object'||!SHA.test(input.headSha||''))
  return emptyCalibration('INVALID_EVIDENCE');
 if(input.corpus===undefined&&input.reports===undefined)
  return emptyCalibration('NOT_MEASURED');
 if(!Array.isArray(input.corpus)||!Array.isArray(input.reports)||
    input.reports.some(item=>item?.provider!=='ultra-sentinel'))
  return emptyCalibration('INVALID_EVIDENCE');
 try{
  // The existing comparative harness validates exact-SHA case IDs, report
  // completeness, human-review metadata, duplicate entries and safe paths.
  // The metadata is not independently authenticated; this is shadow-only.
  const raw=evaluate(input.corpus,input.reports,{expectedSha:input.headSha});
  const m=raw.results['ultra-sentinel'];
  if(!m||m.complete!==true||m.casesReviewed!==input.corpus.length||
     m.negativeCasesReviewed>m.casesReviewed||m.tp<0||m.fp<0||m.fn<0)
   return emptyCalibration('INVALID_EVIDENCE');
  let positiveCases=0,negativeCases=0;
  const rules=new Set();
  for(const sample of input.corpus){
   if(sample.expected.length){
    positiveCases++;
    for(const expected of sample.expected)rules.add(expected.rule);
   }else negativeCases++;
  }
  const metrics={tp:m.tp,fp:m.fp,fn:m.fn,precision:m.precision,
   recall:m.recall,negativeCaseFalsePositiveRate:m.negativeCaseFalsePositiveRate,
   casesReviewed:m.casesReviewed,negativeCasesReviewed:m.negativeCasesReviewed,
   latencyP95Ms:m.latencyP95Ms};
  const sufficient=positiveCases>=MIN_POSITIVE&&negativeCases>=MIN_NEGATIVE&&
   rules.size>=MIN_FAMILIES&&m.negativeCasesReviewed===negativeCases;
  const targets=m.fn===0&&m.precision!==null&&m.precision>=.99&&
   m.recall!==null&&m.recall>=.99&&m.negativeCaseFalsePositiveRate!==null&&
   m.negativeCaseFalsePositiveRate<=.01;
  const status=!sufficient?'INSUFFICIENT_SAMPLE':
   targets?'TARGETS_MET_SHADOW_ONLY':'TARGETS_NOT_MET';
  return {schema:'ultra-sentinel-calibration/v1',mode:'shadow',status,
   metrics,positiveCases,negativeCases,distinctPositiveRules:rules.size,
   evidenceExternallyAttested:false,productionReady:false,
   enforcementEnabled:false,autoMergeAllowed:false};
 }catch(_error){return emptyCalibration('INVALID_EVIDENCE');}
}
function classification(severity){
 if(['BLOCKER','CRITICAL','HIGH'].includes(severity))return 'BLOCK';
 if(['MAJOR','WARNING','MEDIUM'].includes(severity))return 'ADVISORY';
 return 'INFO';
}
function validPath(path){
 return typeof path==='string'&&PATH.test(path)&&!path.startsWith('/')&&
  path.split('/').every(s=>s!==''&&s!=='.'&&s!=='..');
}
function validFinding(f){
 if(!f||typeof f!=='object'||Array.isArray(f)||!validPath(f.path)||
    !RULE.test(f.rule||'')||!SEVERITIES.has(f.severity)||
    !CLASSES.has(f.classification)||!HEX.test(f.fingerprint||'')||
    (f.familyId!==undefined&&!HEX.test(f.familyId||''))||
    !Number.isSafeInteger(f.line)||f.line<1||f.line>10000000)
  return false;
 return f.severityClass===classification(f.severity);
}
function emptyCounts(){return {new:0,exacerbated:0,reactivated:0,existing:0,total:0};}
function assess(input){
 const invalid={
  schema:'ultra-sentinel-quality-gate/v1',mode:'shadow',
  status:'INCOMPLETE',reason:'INVALID_QUALITY_EVIDENCE',
  ciStatus:'INCOMPLETE',hasAuthenticatedBaseline:false,
  hasAuthenticatedFullRepositoryScan:false,
  criticalFindings:emptyCounts(),calibration:emptyCalibration('NOT_MEASURED'),
  manualReviewRequired:true,productionBlockingEnabled:false,
  autoMergeAllowed:false,autoCommitAllowed:false
 };
 try{
  if(!input||typeof input!=='object'||!SHA.test(input.headSha||'')||
    !['PASS','BLOCKED','INCOMPLETE'].includes(input.ciStatus)||
    !['ADVISORY','BLOCKED'].includes(input.scannerStatus)||
    typeof input.hasAuthenticatedBaseline!=='boolean'||
    typeof input.hasAuthenticatedFullRepositoryScan!=='boolean'||
    !Array.isArray(input.findings)||input.findings.length>5000||
    input.findings.some(f=>!validFinding(f)))
   return invalid;
  // Decision and calibration must be independently attested. A caller-supplied
  // benchmark is not evidence of provenance and cannot enter this decision.
  if(input.calibration!==undefined)return invalid;
  const calibration=emptyCalibration('NOT_MEASURED');
  const counts=emptyCounts(),seen=new Map();
  let advisory=false;
  for(const f of input.findings){
   const old=seen.get(f.fingerprint);
   if(old){
    if(old.path!==f.path||old.line!==f.line||
       old.severity!==f.severity||old.classification!==f.classification)
     return invalid;
    continue;
   }
   seen.set(f.fingerprint,f);
   const cls=classification(f.severity);
   if(cls==='ADVISORY')advisory=true;
   if(cls!=='BLOCK')continue;
   counts.total++;
   if(f.classification==='NEW')counts.new++;
   else if(f.classification==='EXACERBATED')counts.exacerbated++;
   else if(f.classification==='REACTIVATED')counts.reactivated++;
   else counts.existing++;
  }
  let status='SHADOW_PASS',reason=null;
  if(input.ciStatus==='BLOCKED'){
   status='BLOCKED_CI';reason='CONFIRMED_CI_FAILURE';
  }else if(input.scannerStatus==='BLOCKED'||counts.total>0){
   status='REVIEW_REQUIRED';reason='CRITICAL_REVIEW_REQUIRED';
  }else if(input.ciStatus!=='PASS'){
   status='INCOMPLETE';reason='CI_EVIDENCE_PENDING';
  }else if(!input.hasAuthenticatedBaseline){
   status='INCOMPLETE';reason='SCOPED_BASELINE_MISSING';
  }else if(!input.hasAuthenticatedFullRepositoryScan){
   status='INCOMPLETE';reason='FULL_REPOSITORY_EVIDENCE_MISSING';
  }else if(advisory)status='ADVISORY';
  return {schema:'ultra-sentinel-quality-gate/v1',mode:'shadow',
   status,reason,headSha:input.headSha.toLowerCase(),ciStatus:input.ciStatus,
   hasAuthenticatedBaseline:input.hasAuthenticatedBaseline,
   hasAuthenticatedFullRepositoryScan:input.hasAuthenticatedFullRepositoryScan,
   criticalFindings:counts,calibration,
   manualReviewRequired:status!=='SHADOW_PASS'&&status!=='ADVISORY',
   productionBlockingEnabled:false,autoMergeAllowed:false,autoCommitAllowed:false};
 }catch(_error){return invalid;}
}
module.exports={measureCalibration,assess};
