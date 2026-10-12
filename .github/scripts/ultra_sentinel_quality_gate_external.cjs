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
 // Never allocate an encoded string before verifying its *serialized* size.
 // JSON escaping can expand control characters up to sixfold.
 let remaining=MAX_BYTES;
 function spend(bytes){
  if(!Number.isSafeInteger(bytes)||bytes<0||bytes>remaining)throw Error('UNBOUNDED_INPUT');
  remaining-=bytes;
 }
 function quotedBytes(str){
  if(typeof str!=='string'||str.length>MAX_BYTES)throw Error('UNBOUNDED_INPUT');
  let bytes=2;
  for(let i=0;i<str.length;i++){
   const c=str.charCodeAt(i);
   if(c===34||c===92||c===8||c===9||c===10||c===12||c===13)bytes+=2;
   else if(c<32)bytes+=6;
   else if(c<128)bytes+=1;
   else if(c<2048)bytes+=2;
   else if(c>=0xd800&&c<=0xdbff){
    const next=str.charCodeAt(i+1);
    if(next>=0xdc00&&next<=0xdfff){bytes+=4;i++;}
    else bytes+=6;
   }else if(c>=0xdc00&&c<=0xdfff)bytes+=6;
   else bytes+=3;
   if(bytes>MAX_BYTES)throw Error('UNBOUNDED_INPUT');
  }
  return bytes;
 }
 function encode(v,depth){
  if(depth>MAX_DEPTH)throw Error('UNBOUNDED_INPUT');
  if(typeof v==='string'){spend(quotedBytes(v));return JSON.stringify(v);}
  if(v===null||typeof v==='boolean'){
   const serialized=JSON.stringify(v);spend(serialized.length);return serialized;
  }
  if(typeof v==='number'&&Number.isFinite(v)){
   const serialized=JSON.stringify(v);spend(serialized.length);return serialized;
  }
  if(Array.isArray(v)){
   if(v.length>2500)throw Error('UNBOUNDED_INPUT');
   if(Object.keys(v).length!==v.length)throw Error('INVALID_JSON_VALUE');
   spend(2+Math.max(0,v.length-1));
   const parts=[];
   for(let i=0;i<v.length;i++){
    const descriptor=Object.getOwnPropertyDescriptor(v,String(i));
    if(!descriptor||!Object.hasOwn(descriptor,'value'))throw Error('INVALID_JSON_VALUE');
    parts.push(encode(descriptor.value,depth+1));
   }
   return '['+parts.join(',')+']';
  }
  if(typeof v!=='object'||![Object.prototype,null].includes(Object.getPrototypeOf(v)))
   throw Error('INVALID_JSON_VALUE');
  const names=Object.keys(v).sort();
  if(names.length>100)throw Error('UNBOUNDED_INPUT');
  spend(2+Math.max(0,names.length-1)+names.length);
  const parts=[];
  for(const name of names){
   spend(quotedBytes(name));
   const descriptor=Object.getOwnPropertyDescriptor(v,name);
   if(!descriptor||!Object.hasOwn(descriptor,'value'))throw Error('INVALID_JSON_VALUE');
   parts.push(JSON.stringify(name)+':'+encode(descriptor.value,depth+1));
  }
  return '{'+parts.join(',')+'}';
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
function parsedTrustedKey(record){
 if(!record||typeof record!=='object'||
    typeof record.publicKeySpki!=='string'||record.publicKeySpki.length>160||
    !/^[A-Za-z0-9+/]+={0,2}$/.test(record.publicKeySpki))throw Error('INVALID_TRUST_ROOT');
 const raw=Buffer.from(record.publicKeySpki,'base64');
 if(raw.length>128||raw.toString('base64')!==record.publicKeySpki)
  throw Error('INVALID_TRUST_ROOT');
 const publicKey=createPublicKey({key:raw,format:'der',type:'spki'});
 if(publicKey.asymmetricKeyType!=='ed25519')throw Error('INVALID_TRUST_ROOT');
 const canonical=publicKey.export({format:'der',type:'spki'});
 // Reject parser-ignored DER suffixes, BER aliases and noncanonical encodings.
 if(!raw.equals(canonical))throw Error('INVALID_TRUST_ROOT');
 return {publicKey,fingerprint:createHash('sha256').update(canonical).digest('hex')};
}

function verifyPart(role,data,attestation,trusted,headSha,corpusDigest=null,parsedKey){
 if(!attestation||typeof attestation!=='object'||!trusted||typeof trusted!=='object'||
    typeof attestation.keyId!=='string'||!/^[-A-Za-z0-9_]{4,80}$/.test(attestation.keyId)||
    attestation.keyId!==trusted.keyId||
    typeof attestation.sha256!=='string'||!HEX.test(attestation.sha256)||
    typeof attestation.signature!=='string'||!SIG.test(attestation.signature)||
    !parsedKey||parsedKey.publicKey?.asymmetricKeyType!=='ed25519')
  return false;
 const serialized=stableJson(data),bytes=Buffer.byteLength(serialized);
 const digest=createHash('sha256').update(serialized).digest('hex');
 if(digest!==attestation.sha256.toLowerCase())return false;
 return verify(null,Buffer.from(attestationMessage(role,headSha,digest,bytes,corpusDigest)),
  parsedKey.publicKey,Buffer.from(attestation.signature,'base64'));
}
function evaluateSignedCalibration(bundle,trustedKeys,{expectedHeadSha}={}){
 try{
  if(!bundle||typeof bundle!=='object'||Array.isArray(bundle)||
     Object.keys(bundle).sort().join(',')!=='corpus,headSha,reports,schema,signatures'||
     bundle.schema!==SCHEMA||!SHA.test(bundle.headSha||'')||
     !SHA.test(expectedHeadSha||'')||
     bundle.headSha.toLowerCase()!==expectedHeadSha.toLowerCase()||
     !Array.isArray(bundle.corpus)||bundle.corpus.length<1||bundle.corpus.length>2000||
     !Array.isArray(bundle.reports)||bundle.reports.length<1||bundle.reports.length>2000||
     !bundle.signatures||!trustedKeys||!trustedKeys.labeler||!trustedKeys.runner)
   return safeReport('UNVERIFIED_EVIDENCE');
  const labeler=trustedKeys.labeler,runner=trustedKeys.runner;
  if(typeof labeler.keyId!=='string'||labeler.keyId===runner.keyId)
   return safeReport('UNVERIFIED_EVIDENCE');
  const labelerKey=parsedTrustedKey(labeler),runnerKey=parsedTrustedKey(runner);
  if(labelerKey.fingerprint===runnerKey.fingerprint)
   return safeReport('UNVERIFIED_EVIDENCE');
  if(!verifyPart('labeler',bundle.corpus,bundle.signatures.labeler,labeler,bundle.headSha,null,labelerKey)||
     !verifyPart('runner',bundle.reports,bundle.signatures.runner,runner,bundle.headSha,
      bundle.signatures.labeler.sha256,runnerKey))
   return safeReport('UNVERIFIED_EVIDENCE');
  const calibration=measureCalibration({headSha:bundle.headSha,
   corpus:bundle.corpus,reports:bundle.reports});
  if(calibration.status==='INVALID_EVIDENCE'||calibration.status==='NOT_MEASURED')
   return safeReport('INVALID_CASES');
  return safeReport('SIGNED_SHADOW_ONLY',calibration);
 }catch(_error){return safeReport('UNVERIFIED_EVIDENCE');}
}
module.exports={evaluateSignedCalibration,attestationMessage,stableJson};
