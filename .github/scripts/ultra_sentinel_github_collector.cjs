'use strict';
/*
 * GitHub evidence bridge for Ultra Sentinel (PR #181).
 * Trusted-main runner only. Read-only shadow diagnostics; never approval/merge.
 * PR content, API payloads and analyzer output are untrusted data, not code.
 */
const fs=require('node:fs');
const {createGitHubReader}=require('./ultra_sentinel_api_budget.cjs');
const {reviewRemote}=require('./ultra_sentinel_selfreview.cjs');
const {review}=require('./ultra_sentinel_intelligent_gate.cjs');
const {assess}=require('./ultra_sentinel_quality_gate.cjs');
const {scanSnapshots}=require('./ultra_sentinel_snapshot_scan.cjs');

const SHA=/^[a-f0-9]{40}$/i;
const REPO=/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;
const REQUIRED=Object.freeze(['Android build','Unit Test Coverage','Ultra Sentinel Core Tests']);
// Stable workflow IDs obtained from GitHub, not names supplied by a PR.
const TRUSTED_WORKFLOW_IDS=Object.freeze({'Android build':363810233,
 'Unit Test Coverage':367848611,'Ultra Sentinel Core Tests':379109738});
const MAX_FILES=250,MAX_WORKFLOWS=100,MAX_FINDINGS=5000;

function requireInput(options){
 const repo=options?.repo,pr=Number(options?.pr),headSha=options?.headSha,token=options?.token;
 if(!REPO.test(repo||'')||repo.split('/').some(part=>part==='.'||part==='..')||
    !Number.isSafeInteger(pr)||pr<1||pr>100000000||
    !SHA.test(headSha||'')||typeof token!=='string'||!token.length)
  throw Error('INVALID_REQUEST');
 return {repo,pr,headSha:headSha.toLowerCase(),token};
}
function apiClient(repo,token,fetcher=globalThis.fetch){
 return createGitHubReader(repo,token,fetcher);
}
function validateMetadata(info,repo,pr,headSha){
 if(!info||info.number!==pr||info.state!=='open'||info.head?.sha?.toLowerCase()!==headSha||
    info.base?.repo?.full_name!==repo||!SHA.test(info.base?.sha||'')||
    !Number.isSafeInteger(info.changed_files)||info.changed_files<0||info.changed_files>MAX_FILES)
  throw Error('PR_PROVENANCE_INCOMPLETE');
 return info.base.sha.toLowerCase();
}
function checkEvidence(runs,headSha,pr){
 if(!runs||!Array.isArray(runs.workflow_runs)||!Number.isSafeInteger(runs.total_count)||
    runs.total_count>MAX_WORKFLOWS||runs.workflow_runs.length!==runs.total_count)
  throw Error('WORKFLOW_EVIDENCE_INCOMPLETE');
 const selected=new Map();
 for(const run of runs.workflow_runs){
  if(!run||!REQUIRED.includes(run.name)||run.workflow_id!==TRUSTED_WORKFLOW_IDS[run.name]||
     run.event!=='pull_request'||
     typeof run.head_sha!=='string'||run.head_sha.toLowerCase()!==headSha||
     !Array.isArray(run.pull_requests)||!run.pull_requests.some(p=>p?.number===pr)||
     !Number.isSafeInteger(run.id)||!Number.isSafeInteger(run.run_attempt))continue;
  const old=selected.get(run.name);
  if(!old||run.id>old.id||(run.id===old.id&&run.run_attempt>old.run_attempt))
   selected.set(run.name,run);
 }
 const checks=REQUIRED.map(name=>{
  const r=selected.get(name);
  return {name,sha:headSha,source:'trusted',status:r?.status||'pending',
   conclusion:r?.conclusion||null,runId:r?.id||null};
 });
 const failed=checks.some(c=>c.status==='completed'&&c.conclusion&&
  !['success','neutral','skipped'].includes(c.conclusion));
 const incomplete=checks.some(c=>c.status!=='completed'||!c.conclusion||
  ['neutral','skipped'].includes(c.conclusion));
 return {checks,status:failed?'BLOCKED':incomplete?'INCOMPLETE':'PASS'};
}
function cleanFindings(findings){
 if(!Array.isArray(findings)||findings.length>MAX_FINDINGS)throw Error('ANALYZER_EVIDENCE_INCOMPLETE');
 return findings.map(item=>{
  if(!item||typeof item.rule!=='string'||!/^[a-zA-Z0-9_.:-]{1,100}$/.test(item.rule)||
     !['BLOCKER','CRITICAL','HIGH','MAJOR','WARNING','MEDIUM','LOW','MINOR','INFO'].includes(item.severity)||
     typeof item.file!=='string'||item.file.length>240||item.file.startsWith('/')||
     !/^[A-Za-z0-9_.\/-]+$/.test(item.file)||
     item.file.split('/').some(segment=>!segment||segment==='.'||segment==='..')||
     !Number.isSafeInteger(item.line)||item.line<0||item.line>10000000)
   throw Error('ANALYZER_EVIDENCE_INCOMPLETE');
  return {rule:item.rule,severity:item.severity,path:item.file,line:item.line};
 });
}
async function collect(options,dependencies={}){
 const {repo,pr,headSha,token}=requireInput(options);
 // Keep the callable identity statically known for trusted AST provenance.
 // Dependency injection is resolved at call time, with no PR code executed.
 const get=async relative=>{
  if(dependencies.get)return dependencies.get(relative);
  const remote=apiClient(repo,token,dependencies.fetcher);
  return remote(relative);
 };
 const analyzer=dependencies.analyzer||reviewRemote;
 const snapshotScanner=dependencies.snapshots||scanSnapshots;
 const prefix='pulls/'+pr;
 const initial=await get(prefix);
 const baseSha=validateMetadata(initial,repo,pr,headSha);
 const comparison=await get('compare/'+baseSha+'...'+headSha);
 if(!SHA.test(comparison?.merge_base_commit?.sha||'')||
    comparison.base_commit?.sha?.toLowerCase()!==baseSha)
  throw Error('MERGE_BASE_UNVERIFIED');
 const mergeBaseSha=comparison.merge_base_commit.sha.toLowerCase();
 const files=[];
 for(let page=1;page<=3;page++){
  const entries=await get(prefix+'/files?per_page=100&page='+page);
  if(!Array.isArray(entries)||entries.length>100)throw Error('FILES_INCOMPLETE');
  files.push(...entries);
  if(files.length>MAX_FILES)throw Error('FILES_INCOMPLETE');
  if(entries.length<100)break;
 }
 if(files.length!==initial.changed_files)throw Error('FILES_INCOMPLETE');
 const changedPaths=[],renames=[];
 for(const item of files){
  if(!item||typeof item.filename!=='string'||item.filename.length>240)
   throw Error('FILES_INCOMPLETE');
  if(item.status!=='removed')changedPaths.push(item.filename);
  if(item.status==='renamed')
   renames.push({status:'renamed',previous_filename:item.previous_filename,filename:item.filename});
 }
 const runEvidence=await get('actions/runs?event=pull_request&head_sha='+headSha+'&per_page=100');
 const ci=checkEvidence(runEvidence,headSha,pr);
 // Existing trusted-main scanner fetches exact-SHA source and analyzes diff as
 // DATA, using the trusted parser; it does not run candidate source code.
 const audit=await analyzer({GITHUB_REPOSITORY:repo,PR_NUMBER:String(pr),
  PR_HEAD_SHA:headSha,GITHUB_TOKEN:token});
 if(audit?.sha!==headSha||audit?.repo!==repo||audit?.pr!==pr||
    audit.autoMergeAllowed!==false||audit.requiresHuman!==true||
    !['ADVISORY','BLOCKED'].includes(audit.status))
  throw Error('ANALYZER_EVIDENCE_INCOMPLETE');
 const detected=cleanFindings(audit.findings);
 // Independently enumerate and attest both immutable commit trees. Every
 // analyzed Git blob is rehashed; no candidate file is imported or executed.
 const snapshots=await snapshotScanner({get,baseSha,headSha});
 const base=snapshots?.base,head=snapshots?.head;
 if(snapshots?.autoMergeAllowed!==false||base?.sha!==baseSha||head?.sha!==headSha||
    !Array.isArray(base?.findings)||!Array.isArray(head?.findings)||
    !Array.isArray(base?.reasons)||!Array.isArray(head?.reasons)||
    typeof base?.complete!=='boolean'||typeof head?.complete!=='boolean')
  throw Error('SNAPSHOT_ATTESTATION_MISMATCH');
 const final=await get(prefix);
 if(validateMetadata(final,repo,pr,headSha)!==baseSha)
  throw Error('PR_CHANGED_DURING_COLLECTION');
 const candidate=review({headSha,baseSha,mergeBaseSha,
  headComplete:head.complete,baseComplete:base.complete,
  headFindings:head.findings,baseFindings:base.findings,
  requiredChecks:REQUIRED,checks:ci.checks,renames,changedPaths,
  affectedPaths:changedPaths});
 if(candidate.autoMergeAllowed!==false)throw Error('SHADOW_INVARIANT_VIOLATION');
 if(base.complete&&head.complete&&candidate.status==='INCOMPLETE'&&
    candidate.reason!=='CI_EVIDENCE_INCOMPLETE')
  throw Error('SHADOW_EVIDENCE_INCOMPLETE');
 // Scope-complete is NOT synonymous with complete repository semantic
 // understanding. The quality gate stays advisory regardless of its verdict.
 const verified=base.complete&&head.complete;
 const quality=assess({headSha,ciStatus:ci.status,scannerStatus:audit.status,
  hasAuthenticatedBaseline:verified,hasAuthenticatedFullRepositoryScan:false,
  findings:candidate.findings});
 if(quality.productionBlockingEnabled!==false||quality.autoMergeAllowed!==false||
    quality.autoCommitAllowed!==false)throw Error('SHADOW_INVARIANT_VIOLATION');
 const summary=sample=>({sha:sample.sha,complete:sample.complete,
  targetFiles:sample.targetFiles||0,scannedFiles:sample.scannedFiles||0,
  treeEntries:sample.treeEntries||0,bytes:sample.bytes||0,
  findingsTotal:sample.findings.length,reasons:sample.reasons.slice(0,20)});
 return {schema:'ultra-sentinel-github-evidence/v1',mode:'shadow',
  status:'INCOMPLETE',reason:'PRODUCTION_QUALITY_GATE_NOT_AUTHORIZED',
  analysisScope:snapshots.scope||'SCOPED_AUTHENTICATED_SOURCE_SCAN_V1',
  repo,pr,headSha,baseSha,mergeBaseSha,changedFiles:files.length,
  scannerStatus:audit.status,scannerFindingsTotal:detected.length,
  scannerFindings:detected.slice(0,50),scannerReportTruncated:detected.length>50,
  ciStatus:ci.status,checks:ci.checks.map(c=>({name:c.name,status:c.status,
   conclusion:c.conclusion,sha:c.sha,runId:c.runId})),
  baseSnapshot:summary(base),headSnapshot:summary(head),
  qualityGate:quality,
  scopedEvaluator:{status:candidate.status,reason:candidate.reason||null,
   findingsTotal:candidate.findings.length,
   findings:candidate.findings.slice(0,50).map(f=>({path:f.path,line:f.line,
    rule:f.rule,severity:f.severity,classification:f.classification,
    familyId:f.familyId})),
   truncated:candidate.findings.length>50,shadowComparison:candidate.shadowComparison},
  reviewRequired:audit.status==='BLOCKED'||ci.status==='BLOCKED'||
   head.findings.some(f=>['BLOCKER','CRITICAL','HIGH'].includes(f.severity))||
   candidate.status==='BLOCKED'||candidate.status==='REVIEW_REQUIRED',
  autoMergeAllowed:false,autoCommitAllowed:false,
  hasAuthenticatedBaseline:verified,hasAuthenticatedFullRepositoryScan:false};
}

const FAILURE_CODES=new Set([
 'INVALID_REQUEST','INVALID_API_PATH','GITHUB_API_UNAVAILABLE',
 'PR_PROVENANCE_INCOMPLETE','PR_CHANGED_DURING_COLLECTION',
 'MERGE_BASE_UNVERIFIED','FILES_INCOMPLETE','WORKFLOW_EVIDENCE_INCOMPLETE',
 'ANALYZER_EVIDENCE_INCOMPLETE','SNAPSHOT_ATTESTATION_MISMATCH',
 'SHADOW_EVIDENCE_INCOMPLETE','SHADOW_INVARIANT_VIOLATION',
 'INVALID_SNAPSHOT_REQUEST','GIT_COMMIT_PROVENANCE_INCOMPLETE',
 'GIT_TREE_INCOMPLETE','GIT_TREE_UNSAFE_ENTRY','GIT_BLOB_UNVERIFIED',
 'GIT_BLOB_SIZE_MISMATCH','SCANNER_FINDINGS_OVERFLOW'
]);
function failureReport(error,options={}){
 // Error messages and exception objects are untrusted. Only preapproved
 // machine codes are emitted, never raw API responses, paths or credentials.
 const reason=FAILURE_CODES.has(error?.message)?error.message:'COLLECTION_FAILED';
 const report={schema:'ultra-sentinel-github-evidence-failure/v1',
  mode:'shadow',status:'INCOMPLETE',reason,reviewRequired:true,
  autoMergeAllowed:false,autoCommitAllowed:false,
  hasAuthenticatedBaseline:false,hasAuthenticatedFullRepositoryScan:false};
 if(typeof options.repo==='string'&&REPO.test(options.repo)&&
  options.repo.split('/').every(p=>p!=='.'&&p!=='..'))
  report.repo=options.repo;
 if(Number.isSafeInteger(options.pr)&&options.pr>0&&options.pr<=100000000)
  report.pr=options.pr;
 if(typeof options.headSha==='string'&&SHA.test(options.headSha))
  report.headSha=options.headSha.toLowerCase();
 return report;
}

async function main(){
 try{
  const report=await collect({repo:process.env.GITHUB_REPOSITORY,
   pr:process.env.PR_NUMBER,headSha:process.env.PR_HEAD_SHA,token:process.env.GITHUB_TOKEN});
  const serialized=JSON.stringify(report,null,2)+'\n';
  fs.writeFileSync('sentinel-github-evidence-shadow.json',serialized,{mode:0o600});
  console.log(serialized);
  if(process.env.GITHUB_STEP_SUMMARY){
   fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
    '## Ultra Sentinel GitHub evidence (shadow only)\n\n'+
    'HEAD: '+report.headSha+'\n\n'+
    'CI evidence: **'+report.ciStatus+'**; verified scoped base/HEAD: **'+
    report.hasAuthenticatedBaseline+'**; evaluator: **'+report.scopedEvaluator.status+
    '**; Quality Gate (shadow): **'+report.qualityGate.status+
    '**; production status: **INCOMPLETE**. Never merge authority.\n');
  }
 }catch(error){
  const failed=failureReport(error,{
   repo:process.env.GITHUB_REPOSITORY,
   pr:Number(process.env.PR_NUMBER),
   headSha:process.env.PR_HEAD_SHA});
  try{
   fs.writeFileSync('sentinel-github-evidence-shadow.json',
    JSON.stringify(failed,null,2)+'\n',{mode:0o600});
   if(process.env.GITHUB_STEP_SUMMARY)
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
     '## Ultra Sentinel GitHub evidence (shadow only)\n\n'+
     'Collection incomplete: **'+failed.reason+
     '**. Manual review required; no merge authority.\n');
  }catch(_writeError){
   console.error('Ultra Sentinel trusted evidence: REPORT_WRITE_FAILED');
  }
  console.error('Ultra Sentinel trusted evidence: '+failed.reason);
  process.exitCode=1;
 }
}
if(require.main===module)main();
module.exports={REQUIRED,TRUSTED_WORKFLOW_IDS,requireInput,apiClient,validateMetadata,checkEvidence,
 cleanFindings,collect,failureReport};
