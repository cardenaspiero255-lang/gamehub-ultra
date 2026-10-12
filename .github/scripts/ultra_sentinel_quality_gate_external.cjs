'use strict';
/*
 * PR #187 — signed calibration input adapter (SHADOW ONLY).
 * A signature establishes integrity under caller-pinned keys, not truth of
 * labels, independence of humans, or production-grade detection accuracy.
 * Never load public keys from PR content or from the signed bundle itself.
 */
const {createHash,createPublicKey,verify}=require('node:crypto');
const {measureCalibration}=require('./ultra_sentinel_quality_gate.cjs');
const SCHEMA='ultra-sentinel-external-calibration/v1';
const SHA=/^[a-f0-9]{40}$/i,HEX=/^[a-f0-9]{64}$/i;
const SIG=/^[A-Za-z0-9+/]{86}==$/;
const ROLES=Object.freeze(['labeler','runner']);
const MAX_BYTES=1024*1024,MAX_DEPTH=24;

function stableJson(value){
 function encode(v,depth){
  if(depth>MAX_DEPTH)throw Error('UNBOUNDED_INPUT');
  if(v===null||typeof v==='string'||typeof v==='boolean')return JSON.stringify(v);
  if(typeof v==='number'&&Number.isFinite(v))return JSON.stringify(v);
  if(Array.isArray(v)){
   if(v.length>2500)throw Error('UNBOUNDED_INPUT');
   return '['+v.map(x=>encode(x,depth+1)).join(',')+']';
  }
  if(typeof v!=='object'||![Object.prototype,null].includes(Object.getPrototypeOf(v)))
   throw Error('INVALID_JSON_VALUE');
  const names=Object.keys(v).sort();
  if(names.length>100)throw Error('UNBOUNDED_INPUT');
  return '{'+names.map(k=>JSON.stringify(k)+':'+encode(v[k],depth+1)).join(',')+'}';
 }
 return encode(value,0);
}
function attestationMessage(role,headSha,digest,bytes,corpusDigest=null){
 if(!ROLES.includes(role)||!SHA.test(headSha||'')||!HEX.test(digest||'')||
    !Number.isSafeInteger(bytes)||bytes<1||bytes>MAX_BYTES||
    (role==='runner'&&!HEX.test(corpusDigest||'')))
  throw Error('INVALID_ATTESTATION');
 return [SCHEMA,role,headSha.toLowerCase(),
  ...(role==='runner'?[corpusDigest.toLowerCase()]:[]),
  digest.toLowerCase(),String(bytes)].join('\n');
}
function safeReport(status,calibration=null){
 const empty={schema:'ultra-sentinel-calibration/v1',mode:'shadow',status:'NOT_MEASURED',
  metrics:null,productionReady:false,enforcementEnabled:false,autoMergeAllowed:false};
 return {schema:SCHEMA,mode:'shadow',status,
  calibration:calibration||empty,signaturesVerified:status==='SIGNED_SHADOW_ONLY',
  // Signed data is NOT independent auditing or empirical accuracy certification.
  evidenceExternallyAudited:false,productionReady:false,
  productionBlockingEnabled:false,autoMergeAllowed:false,autoCommitAllowed:false,
  manualReviewRequired:true};
}
function verifyPart(role,data,attestation,trusted,headSha,corpusDigest=null){
 if(!attestation||typeof attestation!=='object'||!trusted||typeof trusted!=='object'||
    typeof attestation.keyId!=='string'||!/^[-A-Za-z0-9_]{4,80}$/.test(attestation.keyId)||
    attestation.keyId!==trusted.keyId||
    typeof attestation.sha256!=='string'||!HEX.test(attestation.sha256)||
    typeof attestation.signature!=='string'||!SIG.test(attestation.signature)||
    typeof trusted.publicKeySpki!=='string'||trusted.publicKeySpki.length>160||
    !/^[A-Za-z0-9+/]+={0,2}$/.test(trusted.publicKeySpki))
  return false;
 const serialized=stableJson(data),bytes=Buffer.byteLength(serialized);
 if(bytes>MAX_BYTES)return false;
 const digest=createHash('sha256').update(serialized).digest('hex');
 if(digest!==attestation.sha256.toLowerCase())return false;
 const pub=createPublicKey({key:Buffer.from(trusted.publicKeySpki,'base64'),format:'der',type:'spki'});
 if(pub.asymmetricKeyType!=='ed25519')return false;
 return verify(null,Buffer.from(attestationMessage(role,headSha,digest,bytes,corpusDigest)),
  pub,Buffer.from(attestation.signature,'base64'));
}
function evaluateSignedCalibration(bundle,trustedKeys){
 try{
  if(!bundle||typeof bundle!=='object'||Array.isArray(bundle)||
     Object.keys(bundle).sort().join(',')!=='corpus,headSha,reports,schema,signatures'||
     bundle.schema!==SCHEMA||!SHA.test(bundle.headSha||'')||
     !Array.isArray(bundle.corpus)||bundle.corpus.length<1||bundle.corpus.length>2000||
     !Array.isArray(bundle.reports)||bundle.reports.length<1||bundle.reports.length>2000||
     !bundle.signatures||!trustedKeys||!trustedKeys.labeler||!trustedKeys.runner)
   return safeReport('UNVERIFIED_EVIDENCE');
  const labeler=trustedKeys.labeler,runner=trustedKeys.runner;
  if(typeof labeler.keyId!=='string'||labeler.keyId===runner.keyId||
     !labeler.publicKeySpki||labeler.publicKeySpki===runner.publicKeySpki)
   return safeReport('UNVERIFIED_EVIDENCE');
  if(!verifyPart('labeler',bundle.corpus,bundle.signatures.labeler,labeler,bundle.headSha)||
     !verifyPart('runner',bundle.reports,bundle.signatures.runner,runner,bundle.headSha,
      bundle.signatures.labeler.sha256))
   return safeReport('UNVERIFIED_EVIDENCE');
  const calibration=measureCalibration({headSha:bundle.headSha,
   corpus:bundle.corpus,reports:bundle.reports});
  if(calibration.status==='INVALID_EVIDENCE'||calibration.status==='NOT_MEASURED')
   return safeReport('INVALID_CASES');
  return safeReport('SIGNED_SHADOW_ONLY',calibration);
 }catch(_error){return safeReport('UNVERIFIED_EVIDENCE');}
}
module.exports={evaluateSignedCalibration,attestationMessage,stableJson};
