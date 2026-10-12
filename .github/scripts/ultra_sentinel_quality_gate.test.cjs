'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const quality=require('./ultra_sentinel_quality_gate.cjs');
const H='b'.repeat(40),B='a'.repeat(40);
const positive=(id)=>({rule:'FAMILY_'+String(id%6),path:'app/src/main/java/Case'+id+'.kt'});
const corpus=(nPos=100,nNeg=100)=>[
 ...Array.from({length:nPos},(_,i)=>({id:'positive-'+i,sha:H,expected:[positive(i)],
  humanReviewed:true,unseenAtTraining:true})),
 ...Array.from({length:nNeg},(_,i)=>({id:'negative-'+i,sha:H,expected:[],
  humanReviewed:true,unseenAtTraining:true}))
];
const reports=(cases)=>cases.map(c=>({provider:'ultra-sentinel',caseId:c.id,sha:H,
 findings:c.expected.map(x=>({...x})),durationMs:10,costUsd:0}));
const calibration=(cs=corpus(),rs=reports(cs))=>({headSha:H,corpus:cs,reports:rs});
const finding=(over={})=>({
 path:'app/src/main/java/Renderer.kt',line:22,rule:'DANGEROUS_EXEC',
 severity:'CRITICAL',severityClass:'BLOCK',classification:'NEW',
 fingerprint:'a'.repeat(64),familyId:'b'.repeat(64),...over
});
const evidence=(over={})=>({headSha:H,ciStatus:'PASS',
 hasAuthenticatedBaseline:true,hasAuthenticatedFullRepositoryScan:false,
 scannerStatus:'ADVISORY',findings:[],...over});

test('QG-186: calibration without external labeled cases is unmeasured, not 100% precision',()=>{
 const r=quality.measureCalibration({headSha:H});
 assert.equal(r.status,'NOT_MEASURED');
 assert.equal(r.metrics,null);
 assert.equal(r.productionReady,false);
 assert.equal(r.autoMergeAllowed,false);
});

test('QG-186: confusion matrix counts FP and FN using existing independently labeled harness',()=>{
 const cs=corpus(2,2),rs=reports(cs);
 rs[1].findings=[];
 rs[2].findings=[positive(7)];
 const r=quality.measureCalibration(calibration(cs,rs));
 assert.deepEqual([r.metrics.tp,r.metrics.fp,r.metrics.fn],[1,1,1]);
 assert.equal(r.metrics.precision,.5);
 assert.equal(r.metrics.recall,.5);
 assert.equal(r.metrics.negativeCaseFalsePositiveRate,.5);
 assert.equal(r.status,'INSUFFICIENT_SAMPLE');
});

test('QG-186: no negative cases or missing detections never invents a zero false-positive rate',()=>{
 const cs=corpus(3,0),rs=reports(cs);
 const r=quality.measureCalibration(calibration(cs,rs));
 assert.equal(r.metrics.negativeCaseFalsePositiveRate,null);
 assert.equal(r.status,'INSUFFICIENT_SAMPLE');
 assert.equal(r.productionReady,false);
});

test('QG-186: sufficient perfect labeled fixture meets *shadow* target only',()=>{
 const cs=corpus(),r=quality.measureCalibration(calibration(cs));
 assert.equal(r.status,'TARGETS_MET_SHADOW_ONLY');
 assert.equal(r.metrics.tp,100);
 assert.equal(r.metrics.fp,0);
 assert.equal(r.metrics.fn,0);
 assert.equal(r.metrics.precision,1);
 assert.equal(r.metrics.recall,1);
 assert.equal(r.metrics.negativeCaseFalsePositiveRate,0);
 assert.equal(r.positiveCases,100);assert.equal(r.negativeCases,100);
 assert.equal(r.distinctPositiveRules,6);
 assert.equal(r.productionReady,false);
 assert.equal(r.enforcementEnabled,false);
});

test('QG-186: one missed critical case defeats calibration target even with 199 green fixtures',()=>{
 const cs=corpus(),rs=reports(cs);rs[0].findings=[];
 const r=quality.measureCalibration(calibration(cs,rs));
 assert.equal(r.status,'TARGETS_NOT_MET');
 assert.equal(r.metrics.fn,1);
 assert.equal(r.productionReady,false);
});

test('QG-186: false alarms on clean cases defeat precision and negative-case false-positive limits',()=>{
 const cs=corpus(),rs=reports(cs);
 rs[100].findings=[positive(109)];
 rs[101].findings=[positive(110)];
 const r=quality.measureCalibration(calibration(cs,rs));
 assert.equal(r.status,'TARGETS_NOT_MET');
 assert.equal(r.metrics.fp,2);
 assert.equal(r.metrics.negativeCaseFalsePositiveRate,.02);
 assert.equal(r.productionReady,false);
});

test('QG-186: incomplete provider coverage cannot be presented as a successful precision sample',()=>{
 const cs=corpus(),rs=reports(cs).slice(1);
 const r=quality.measureCalibration(calibration(cs,rs));
 assert.equal(r.status,'INVALID_EVIDENCE');
 assert.equal(r.metrics,null);
});

test('QG-186: stale SHA, duplicate reports and forged providers are rejected',()=>{
 const cs=corpus(2,2),rs=reports(cs);
 for(const broken of [
  {headSha:B,corpus:cs,reports:rs},
  calibration(cs,[...rs,rs[0]]),
  calibration(cs,[...rs.slice(0,3),{...rs[3],provider:'untrusted-provider'}]),
  calibration([...cs.slice(0,3),{...cs[3],id:cs[0].id}],rs)
 ]){
  const r=quality.measureCalibration(broken);
  assert.equal(r.status,'INVALID_EVIDENCE');assert.equal(r.productionReady,false);
 }
});

test('QG-186: self-labeled, training-exposed and malformed ground truth never certify quality',()=>{
 const cs=corpus(2,2),rs=reports(cs);
 for(const broken of [
  {...cs[0],humanReviewed:false},
  {...cs[0],unseenAtTraining:false},
  {...cs[0],expected:[{rule:'UNSAFE',path:'../secret'}]},
  {...cs[0],sha:B}
 ]){
  const r=quality.measureCalibration(calibration([broken,...cs.slice(1)],rs));
  assert.equal(r.status,'INVALID_EVIDENCE');
  assert.equal(r.metrics,null);
 }
});

test('QG-186: sensitive raw content from corrupt labels and reports is not echoed',()=>{
 const cs=corpus(2,2),rs=reports(cs);
 rs[0].findings=[{rule:'ghp_NEVER_LOG_SECRET',path:'../private'}];
 const r=quality.measureCalibration(calibration(cs,rs));
 assert.equal(r.status,'INVALID_EVIDENCE');
 assert.doesNotMatch(JSON.stringify(r),/NEVER_LOG_SECRET|private/);
});

test('QG-186: invalid immutable HEAD always fails closed and has no authority',()=>{
 const r=quality.assess({...evidence(),headSha:'main'});
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.reason,'INVALID_QUALITY_EVIDENCE');
 assert.equal(r.productionBlockingEnabled,false);
 assert.equal(r.autoCommitAllowed,false);
 assert.equal(r.autoMergeAllowed,false);
});

test('QG-186: scoped snapshot is not a full-repository security claim',()=>{
 const r=quality.assess(evidence());
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.reason,'FULL_REPOSITORY_EVIDENCE_MISSING');
 assert.equal(r.calibration.status,'NOT_MEASURED');
 assert.equal(r.hasAuthenticatedFullRepositoryScan,false);
 assert.equal(r.productionBlockingEnabled,false);
});

test('QG-186: new critical HIGH/BLOCKER findings require review but cannot activate auto-blocking',()=>{
 for(const severity of ['BLOCKER','CRITICAL','HIGH']){
  const r=quality.assess(evidence({findings:[finding({severity})]}));
  assert.equal(r.status,'REVIEW_REQUIRED');
  assert.equal(r.criticalFindings.new,1);
  assert.equal(r.manualReviewRequired,true);
  assert.equal(r.productionBlockingEnabled,false);
  assert.equal(r.autoMergeAllowed,false);
 }
});

test('QG-186: exacerbated/reactivated criticals are not hidden by an existing baseline',()=>{
 const r=quality.assess(evidence({findings:[
  finding({classification:'EXISTING',fingerprint:'a'.repeat(64)}),
  finding({classification:'EXACERBATED',fingerprint:'c'.repeat(64),line:23}),
  finding({classification:'REACTIVATED',fingerprint:'d'.repeat(64),line:24})
 ]}));
 assert.equal(r.status,'REVIEW_REQUIRED');
 assert.deepEqual(r.criticalFindings,{new:0,exacerbated:1,reactivated:1,existing:1,total:3});
});

test('QG-186: confirmed CI failure is distinguished from suspected code findings',()=>{
 const r=quality.assess(evidence({ciStatus:'BLOCKED',findings:[finding()]}));
 assert.equal(r.status,'BLOCKED_CI');
 assert.equal(r.manualReviewRequired,true);
 assert.equal(r.productionBlockingEnabled,false);
});

test('QG-186: pending CI and incomplete scanner cannot pass even when there are no findings',()=>{
 for(const delta of [{ciStatus:'INCOMPLETE'},{hasAuthenticatedBaseline:false},
  {scannerStatus:'BLOCKED'}]){
  const r=quality.assess(evidence(delta));
  assert.notEqual(r.status,'SHADOW_PASS');
  assert.equal(r.autoMergeAllowed,false);
 }
});

test('QG-186: invalid findings, oversized arrays and malformed severity cannot become PASS',()=>{
 for(const bad of [finding({path:'../unsafe'}),finding({severity:'SUPERLOW'}),
  finding({classification:'WHATEVER'}),finding({line:-1})]){
  const r=quality.assess(evidence({hasAuthenticatedFullRepositoryScan:true,findings:[bad]}));
  assert.equal(r.status,'INCOMPLETE');
  assert.equal(r.reason,'INVALID_QUALITY_EVIDENCE');
 }
 const r=quality.assess(evidence({findings:Array.from({length:5001},()=>finding())}));
 assert.equal(r.status,'INCOMPLETE');
});

test('QG-186: duplicate fingerprints cannot inflate the critical risk count',()=>{
 const r=quality.assess(evidence({findings:[finding(),finding()]}));
 assert.equal(r.criticalFindings.total,1);
 assert.equal(r.criticalFindings.new,1);
});

test('QG-186: even complete, clean source evidence grants only a SHADOW_PASS',()=>{
 const r=quality.assess(evidence({hasAuthenticatedFullRepositoryScan:true}));
 assert.equal(r.status,'SHADOW_PASS');
 assert.equal(r.productionBlockingEnabled,false);
 assert.equal(r.autoMergeAllowed,false);
 assert.equal(r.autoCommitAllowed,false);
 assert.equal(r.calibration.status,'NOT_MEASURED');
});

test('QG-186: advisory severities stay advisory and cannot suppress incomplete coverage',()=>{
 const r=quality.assess(evidence({hasAuthenticatedFullRepositoryScan:true,
  findings:[finding({severity:'WARNING',severityClass:'ADVISORY'})]}));
 assert.equal(r.status,'ADVISORY');
 assert.equal(r.criticalFindings.total,0);
 assert.equal(r.autoMergeAllowed,false);
});

test('QG-186: untrusted reasons, secrets and user-supplied authority flags never reach sanitized output',()=>{
 const r=quality.assess({...evidence({hasAuthenticatedFullRepositoryScan:true}),
  autoMergeAllowed:true,enforcementEnabled:true,secret:'ghp_DONT_ECHO'});
 assert.equal(r.autoMergeAllowed,false);
 assert.equal(r.productionBlockingEnabled,false);
 assert.doesNotMatch(JSON.stringify(r),/DONT_ECHO/);
});

test('QG-186: unprivileged workflow contract runs the gate but does not obtain tokens or write permissions',()=>{
 const workflow=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-github-evidence-shadow.yml'),'utf8');
 assert.match(workflow,/ultra_sentinel_quality_gate\.test\.cjs/);
 assert.match(workflow,/pull_request:/);
 assert.doesNotMatch(workflow,/pull_request_target:|secrets\.|github\.token|contents: write|pull-requests: write/);
 assert.match(workflow,/persist-credentials: false/);
 // The independent trusted reviewer reads this immutable candidate as data.
 // Regression must fail on any AST/context ambiguity, not silence the blocker.
 const acorn=require('acorn');
 const complete=fs.readFileSync(path.resolve(__dirname,'ultra_sentinel_quality_gate.cjs'),'utf8');
 const ast=acorn.parse(complete,{ecmaVersion:'latest',sourceType:'script',
  allowHashBang:true,allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,
  locations:true,ranges:true});
 assert.doesNotThrow(()=>require('./ultra_sentinel_capabilities.cjs').findCapabilities(ast),
  'complete Quality Gate AST must be independently analyzable');
});