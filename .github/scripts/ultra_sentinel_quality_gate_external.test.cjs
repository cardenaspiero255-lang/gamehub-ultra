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
 const json=stableJson(JSON.stringify(payload));
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
const run=(f)=>evaluateSignedCalibration(JSON.stringify(f.bundle),f.trusted,{expectedHeadSha:H});
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
 assert.equal(evaluateSignedCalibration(JSON.stringify(f.bundle),swapped,{expectedHeadSha:H}).status,'UNVERIFIED_EVIDENCE');
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
 const result=evaluateSignedCalibration(JSON.stringify(malicious),keys(p),{expectedHeadSha:H});
 assert.equal(result.status,'UNVERIFIED_EVIDENCE');
 assert.equal(result.calibration.metrics,null);
});
test('EXT-187: secrets in malformed inputs never get copied to sanitized output',()=>{
 const f=fixture();f.bundle.reports[0].findings=[{rule:'ghp_PRIVATE_TOKEN',path:'../private'}];
 const r=run(f);
 assert.equal(r.status,'UNVERIFIED_EVIDENCE');
 assert.doesNotMatch(JSON.stringify(r),/PRIVATE_TOKEN|private/);
});

test('EXT-187 P1: DER trailing bytes cannot impersonate a second independent role',()=>{
 const p=pair();p.runner=p.labeler;
 const c=corpus(),r=reports(c),labeler=envelope('labeler',c,p);
 const signed={schema:'ultra-sentinel-external-calibration/v1',headSha:H,
  corpus:c,reports:r,signatures:{labeler,runner:envelope('runner',r,p,H,labeler.sha256)}};
 const trusted=keys(p);
 trusted.runner.publicKeySpki=Buffer.concat([Buffer.from(trusted.runner.publicKeySpki,'base64'),
  Buffer.from('deadbeef','hex')]).toString('base64');
 const result=evaluateSignedCalibration(JSON.stringify(signed),trusted,{expectedHeadSha:H});
 assert.equal(result.status,'UNVERIFIED_EVIDENCE');
 assert.equal(result.signaturesVerified,false);
});
test('EXT-187 P1: noncanonical trusted SPKI with trailing DER bytes is rejected',()=>{
 const f=fixture();
 f.trusted.labeler.publicKeySpki=Buffer.concat([Buffer.from(f.trusted.labeler.publicKeySpki,'base64'),
  Buffer.from('01020304','hex')]).toString('base64');
 assert.equal(run(f).status,'UNVERIFIED_EVIDENCE');
});
test('EXT-187 P1: a signed bundle without trusted caller-pinned HEAD is unverified',()=>{
 const f=fixture();
 assert.equal(evaluateSignedCalibration(JSON.stringify(f.bundle),f.trusted).status,'UNVERIFIED_EVIDENCE');
});
test('EXT-187 P1: unchanged signed old bundle cannot be replayed at a new trusted HEAD',()=>{
 const f=fixture();
 const result=evaluateSignedCalibration(JSON.stringify(f.bundle),f.trusted,{expectedHeadSha:OTHER});
 assert.equal(result.status,'UNVERIFIED_EVIDENCE');
 assert.equal(result.calibration.metrics,null);
});
test('EXT-187 P1: untrusted or invalid caller HEAD fails closed',()=>{
 const f=fixture();
 for(const expectedHeadSha of ['main','',null,'G'.repeat(40),23]){
  assert.equal(evaluateSignedCalibration(JSON.stringify(f.bundle),f.trusted,{expectedHeadSha}).status,'UNVERIFIED_EVIDENCE');
 }
});
test('EXT-187 P2: oversized string is rejected before canonical JSON allocation',()=>{
 assert.throws(()=>stableJson(JSON.stringify({data:'X'.repeat(2*1024*1024)})),/UNBOUNDED_INPUT/);
});
test('EXT-187 P2: escaped control characters are bounded by serialized UTF8 bytes',()=>{
 assert.throws(()=>stableJson(JSON.stringify({data:'\u0000'.repeat(300000)})),/UNBOUNDED_INPUT/);
});
test('EXT-187 P2: huge property name is rejected before canonical JSON allocation',()=>{
 const name='k'.repeat(2*1024*1024);
 const obj=Object.create(null);obj[name]='v';
 assert.throws(()=>stableJson(JSON.stringify(obj)),/UNBOUNDED_INPUT/);
});
test('EXT-187 P2: untrusted accessor is never invoked while canonicalizing',()=>{
 let called=0;
 const obj={};Object.defineProperty(obj,'data',{enumerable:true,get(){called++;return 'injected';}});
 assert.throws(()=>stableJson(obj),/UNBOUNDED_INPUT/);
 assert.equal(called,0);
});
test('EXT-187 P2: sparse arrays cannot produce non-JSON canonical structures',()=>{
 const a=['a',,'c'];
 assert.throws(()=>stableJson(a),/UNBOUNDED_INPUT/);
});
test('EXT-187 P2: canonicalizer accepts exact serialized byte budget without weakening bounds',()=>{
 const v='z'.repeat(1024*1024-2);
 assert.equal(Buffer.byteLength(stableJson(JSON.stringify(v))),1024*1024);
 assert.throws(()=>stableJson(JSON.stringify(v+'q')),/UNBOUNDED_INPUT/);
});


test('EXT-187 P1: forged Ed25519 identity and sign-bit alias cannot impersonate two signing roles',()=>{
 // The two encodings are distinct SPKI bytes yet refer to the same identity point.
 // S=1, R=basepoint verifies for every message under an identity public key.
 const c=corpus(),r=reports(c);
 const identity=Buffer.alloc(32);identity[0]=1;
 const alias=Buffer.from(identity);alias[31]=0x80;
 const spki=(point)=>Buffer.concat([Buffer.from('302a300506032b6570032100','hex'),point]).toString('base64');
 const forged=Buffer.concat([Buffer.from('58'+'66'.repeat(31),'hex'),
  Buffer.concat([Buffer.from([1]),Buffer.alloc(31)])]).toString('base64');
 const signed=(role,payload)=>({keyId:role==='labeler'?'independent-labeler':'independent-runner',
  sha256:createHash('sha256').update(stableJson(JSON.stringify(payload))).digest('hex'),signature:forged});
 const bundle={schema:'ultra-sentinel-external-calibration/v1',headSha:H,corpus:c,reports:r,
  signatures:{labeler:signed('labeler',c),runner:signed('runner',r)}};
 const trusted={labeler:{keyId:'independent-labeler',publicKeySpki:spki(identity)},
  runner:{keyId:'independent-runner',publicKeySpki:spki(alias)}};
 const result=evaluateSignedCalibration(JSON.stringify(bundle),trusted,{expectedHeadSha:H});
 assert.equal(result.status,'UNVERIFIED_EVIDENCE');
 assert.equal(result.signaturesVerified,false);
 assert.equal(result.calibration.metrics,null);
});
test('EXT-187 P2: null options fail closed without throwing outside the error boundary',()=>{
 const f=fixture();
 const result=evaluateSignedCalibration(JSON.stringify(f.bundle),f.trusted,null);
 assert.equal(result.status,'UNVERIFIED_EVIDENCE');
 assert.equal(result.signaturesVerified,false);
 assert.equal(result.calibration.metrics,null);
 assert.equal(result.autoMergeAllowed,false);
});
test('EXT-187 P1: order-two and noncanonical Ed25519 trusted points fail closed',()=>{
 const f=fixture();
 const identity=Buffer.alloc(32);identity[0]=1;
 const orderTwo=Buffer.from('ec'+'ff'.repeat(30)+'7f','hex'); // y = p - 1
 const noncanonical=Buffer.from('ed'+'ff'.repeat(30)+'7f','hex'); // y = p
 for(const point of [identity,orderTwo,noncanonical]){
  f.trusted.labeler.publicKeySpki=Buffer.concat([
   Buffer.from('302a300506032b6570032100','hex'),point]).toString('base64');
  assert.equal(run(f).status,'UNVERIFIED_EVIDENCE');
 }
});
test('EXT-187 P2: object key cap applies before sorting untrusted keys',()=>{
 const huge=Object.create(null);
 for(let i=0;i<101;i++)huge['key'+i]='ok';
 const sort=Array.prototype.sort;
 let sortCalled=false;
 try{
  Array.prototype.sort=function(){sortCalled=true;throw Error('PREMATURE_SORT');};
  assert.throws(()=>stableJson(huge),/UNBOUNDED_INPUT/);
  assert.equal(sortCalled,false);
 }finally{
  Array.prototype.sort=sort;
 }
});
test('EXT-187 P2: at-limit objects retain deterministic canonicalization',()=>{
 const data=Object.create(null);
 for(let i=99;i>=0;i--)data['k'+String(i).padStart(3,'0')]=i;
 const json=stableJson(JSON.stringify(data)),keys=Object.keys(JSON.parse(json));
 assert.equal(keys.length,100);
 assert.equal(keys[0],'k000');
 assert.equal(keys[99],'k099');
});

test('EXT-187: full external-calibration source is attestable by the trusted JavaScript AST engine',()=>{
 const fs=require('node:fs'),path=require('node:path'),acorn=require('acorn');
 const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
 const source=fs.readFileSync(path.join(__dirname,'ultra_sentinel_quality_gate_external.cjs'),'utf8');
 const ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:'script',allowHashBang:true,
  allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,locations:true,ranges:true});
 assert.deepEqual(findCapabilities(ast),[]);
});


test('EXT-187 P2: array extras are rejected before unbounded Object.keys allocation',()=>{
 const malicious=[];
 for(let i=0;i<300;i++)malicious['injected_'+i]=i;
 const original=Object.keys;
 try{
  Object.keys=function(value){
   if(value===malicious)throw Error('PREMATURE_ARRAY_KEY_ALLOCATION');
   return original(value);
  };
  assert.throws(()=>stableJson(malicious),/UNBOUNDED_INPUT/);
 }finally{Object.keys=original;}
});
test('EXT-187 P2: a short array with a named own property fails the bounded shape check',()=>{
 const malicious=['valid'];
 Object.defineProperty(malicious,'metadata',{value:'untrusted',enumerable:true});
 assert.throws(()=>stableJson(malicious),/UNBOUNDED_INPUT/);
});
test('EXT-187 P2: normal dense arrays at the 2500-element limit remain valid',()=>{
 const safe=Array.from({length:2500},(_,index)=>index%17);
 assert.deepEqual(JSON.parse(stableJson(JSON.stringify(safe))),safe);
 const sparse=new Array(2);sparse[1]='value';
 assert.throws(()=>stableJson(sparse),/UNBOUNDED_INPUT/);
});


test('EXT-187 P2: external calibration only accepts prebounded serialized JSON, never arbitrary JS objects',()=>{
 const f=fixture();
 // Valid genuine evidence must work as a bounded raw JSON message.
 const valid=evaluateSignedCalibration(JSON.stringify(f.bundle),f.trusted,{expectedHeadSha:H});
 assert.equal(valid.status,'SIGNED_SHADOW_ONLY');
 assert.equal(valid.signaturesVerified,true);
 const tooLarge='{"filler":"'+'x'.repeat(1024*1024)+'"}';
 assert.equal(evaluateSignedCalibration(tooLarge,f.trusted,{expectedHeadSha:H}).status,'UNVERIFIED_EVIDENCE');
 // A programmatic object has no bounded input size and must never reach key enumeration.
 const unbounded=evaluateSignedCalibration(f.bundle,f.trusted,{expectedHeadSha:H});
 assert.equal(unbounded.status,'UNVERIFIED_EVIDENCE');
 assert.equal(unbounded.calibration.metrics,null);
});


test('EXT-187 P2: reject overlong raw strings without traversing UTF-8 bytes',()=>{
 // Four-byte scalar, but already over the code-unit limit; byteLength must not run.
 const overlong='\u{1F9EA}'.repeat(524289);
 const byteLength=Buffer.byteLength;
 let excessiveScan=false;
 try{
  Buffer.byteLength=function(value,...args){
   if(value===overlong){excessiveScan=true;throw Error('PREMATURE_UTF8_SCAN');}
   return byteLength(value,...args);
  };
  assert.equal(evaluateSignedCalibration(overlong,null,{expectedHeadSha:H}).status,'UNVERIFIED_EVIDENCE');
  assert.equal(excessiveScan,false);
  assert.throws(()=>stableJson(overlong),/UNBOUNDED_INPUT/);
 }finally{Buffer.byteLength=byteLength;}
});
