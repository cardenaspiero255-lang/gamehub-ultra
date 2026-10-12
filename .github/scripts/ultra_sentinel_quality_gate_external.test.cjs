'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {generateKeyPairSync,createHash,sign}=require('node:crypto');
const {evaluateSignedCalibration,attestationMessage,stableJson}=require('./ultra_sentinel_quality_gate_external.cjs');
const H='b'.repeat(40),OTHER='a'.repeat(40);
const key=()=>generateKeyPairSync('ed25519');
const pair=()=>({labeler:key(),runner:key()});
const keys=(p)=>({
 labeler:{keyId:'independent-labeler',publicKeySpki:p.labeler.publicKey.export({format:'der',type:'spki'}).toString('base64')},
 runner:{keyId:'independent-runner',publicKeySpki:p.runner.publicKey.export({format:'der',type:'spki'}).toString('base64')}
});
const corpus=(np=100,nn=100)=>[
 ...Array.from({length:np},(_,i)=>({id:'positive-'+i,sha:H,humanReviewed:true,unseenAtTraining:true,
  expected:[{rule:'FAMILY_'+(i%6),path:'app/src/main/java/Positive'+i+'.kt'}]})),
 ...Array.from({length:nn},(_,i)=>({id:'negative-'+i,sha:H,humanReviewed:true,unseenAtTraining:true,expected:[]}))
];
const reports=(c)=>c.map(x=>({provider:'ultra-sentinel',caseId:x.id,sha:H,
 findings:x.expected.map(f=>({...f})),durationMs:3}));
function envelope(role,payload,p,headSha=H,corpusDigest=null){
 const json=stableJson(payload);
 const digest=createHash('sha256').update(json).digest('hex');
 const privateKey=p[role].privateKey;
 return {keyId:role==='labeler'?'independent-labeler':'independent-runner',
  sha256:digest,signature:sign(null,Buffer.from(attestationMessage(role,headSha,digest,Buffer.byteLength(json),corpusDigest)),privateKey).toString('base64')};
}
function fixture(c=corpus(),r=reports(c)){
 const p=pair();
 const labeler=envelope('labeler',c,p);
 return {trusted:keys(p),bundle:{schema:'ultra-sentinel-external-calibration/v1',headSha:H,
  corpus:c,reports:r,
  signatures:{labeler,runner:envelope('runner',r,p,H,labeler.sha256)}}};
}
const run=(f)=>evaluateSignedCalibration(f.bundle,f.trusted);
test('EXT-187: absent evidence remains unmeasured and never enables enforcement',()=>{
 const r=evaluateSignedCalibration(null,null);
 assert.equal(r.status,'UNVERIFIED_EVIDENCE');
 assert.equal(r.calibration.metrics,null);
 assert.equal(r.autoMergeAllowed,false);
 assert.equal(r.productionBlockingEnabled,false);
 assert.equal(r.autoCommitAllowed,false);
});
test('EXT-187: separately signed corpus and scanner reports calculate reproducible shadow metrics',()=>{
 const f=fixture(),r=run(f);
 assert.equal(r.status,'SIGNED_SHADOW_ONLY');
 assert.equal(r.calibration.status,'TARGETS_MET_SHADOW_ONLY');
 assert.deepEqual([r.calibration.metrics.tp,r.calibration.metrics.fp,r.calibration.metrics.fn],[100,0,0]);
 assert.equal(r.signaturesVerified,true);
 assert.equal(r.evidenceExternallyAudited,false);
 assert.equal(r.autoMergeAllowed,false);
});
test('EXT-187: missing true positive is measured as a false negative',()=>{
 const c=corpus(),p=reports(c);p[0].findings=[];
 const r=run(fixture(c,p));
 assert.equal(r.status,'SIGNED_SHADOW_ONLY');
 assert.equal(r.calibration.metrics.fn,1);
 assert.equal(r.calibration.status,'TARGETS_NOT_MET');
});
test('EXT-187: tampering with a signed label corpus is rejected without metrics',()=>{
 const f=fixture();f.bundle.corpus[0].expected=[];
 const r=run(f);
 assert.equal(r.status,'UNVERIFIED_EVIDENCE');
 assert.equal(r.calibration.metrics,null);
});
test('EXT-187: tampering with signed report findings is rejected without metrics',()=>{
 const f=fixture();f.bundle.reports[0].findings=[];
 assert.equal(run(f).status,'UNVERIFIED_EVIDENCE');
});
test('EXT-187: role-swapped authority and reused public keys cannot establish provenance',()=>{
 const f=fixture(),swapped={labeler:f.trusted.runner,runner:f.trusted.labeler};
 assert.equal(evaluateSignedCalibration(f.bundle,swapped).status,'UNVERIFIED_EVIDENCE');
 f.trusted.runner={...f.trusted.runner,publicKeySpki:f.trusted.labeler.publicKeySpki};
 assert.equal(run(f).status,'UNVERIFIED_EVIDENCE');
});
test('EXT-187: payload-declared attacker keys have no authority over trusted configuration',()=>{
 const f=fixture();f.bundle.trustedKeys={...f.trusted};
 assert.equal(run(f).status,'UNVERIFIED_EVIDENCE');
});
test('EXT-187: stale SHA cannot reuse signed reports for another commit',()=>{
 const f=fixture();f.bundle.headSha=OTHER;
 assert.equal(run(f).status,'UNVERIFIED_EVIDENCE');
});
test('EXT-187: signed but internally inconsistent per-case SHA is rejected',()=>{
 const c=corpus();c[0].sha=OTHER;
 const f=fixture(c);
 assert.equal(run(f).status,'INVALID_CASES');
 assert.equal(run(f).calibration.metrics,null);
});
test('EXT-187: signed duplicate and missing reports cannot certify coverage',()=>{
 const c=corpus(),r=reports(c);
 assert.equal(run(fixture(c,[...r.slice(0,-1),r[0]])).status,'INVALID_CASES');
 assert.equal(run(fixture(c,r.slice(0,-1))).status,'INVALID_CASES');
});
test('EXT-187: below-minimum externally signed samples remain shadow insufficient',()=>{
 const c=corpus(10,10),r=run(fixture(c));
 assert.equal(r.status,'SIGNED_SHADOW_ONLY');
 assert.equal(r.calibration.status,'INSUFFICIENT_SAMPLE');
 assert.equal(r.calibration.productionReady,false);
});
test('EXT-187: mixing separately authentic but unpaired corpus and reports fails closed',()=>{
 const p=pair(),a=corpus(),b=corpus();
 b[0].expected=[{rule:'DIFFERENT',path:'app/src/main/java/Other.kt'}];
 const labelerA=envelope('labeler',a,p),labelerB=envelope('labeler',b,p);
 const runnerA=envelope('runner',reports(a),p,H,labelerA.sha256);
 const malicious={schema:'ultra-sentinel-external-calibration/v1',headSha:H,
  corpus:b,reports:reports(a),signatures:{labeler:labelerB,runner:runnerA}};
 const result=evaluateSignedCalibration(malicious,keys(p));
 assert.equal(result.status,'UNVERIFIED_EVIDENCE');
 assert.equal(result.calibration.metrics,null);
});
test('EXT-187: secrets in malformed inputs never get copied to sanitized output',()=>{
 const f=fixture();f.bundle.reports[0].findings=[{rule:'ghp_PRIVATE_TOKEN',path:'../private'}];
 const r=run(f);
 assert.equal(r.status,'UNVERIFIED_EVIDENCE');
 assert.doesNotMatch(JSON.stringify(r),/PRIVATE_TOKEN|private/);
});
