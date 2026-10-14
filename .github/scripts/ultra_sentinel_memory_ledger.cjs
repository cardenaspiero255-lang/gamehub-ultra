'use strict';
/**
 * Family Hunter memory evidence v1 is intentionally separate from the
 * existing staged-repair ledger (ultra-sentinel-repair-ledger/v1).
 * PR-authored records are UNTRUSTED; only a separately installed trusted-main
 * attestor may promote content tied to real commit and red/green test proof.
 * No network requests, writes, workflow dispatch, approvals or merges here.
 */
const {createHash}=require('node:crypto');
const memory=require('./ultra_sentinel_memory.cjs');
 // Capture the trusted curated entrypoints and inert result before any
 // consumer can tamper with public exports or JavaScript intrinsics.
const TRUSTED_EMPTY=Object.freeze([]);
const TRUSTED_ARRAY_FROM=Array.from;
const TRUSTED_FREEZE=Object.freeze;
const trustedCuratedSearch=memory.search;
const trustedCuratedById=memory.getCuratedPlaybookById;
const SCHEMA='ultra-sentinel-family-hunter-memory/v1';
const REPO='cardenaspiero255-lang/gamehub-ultra';
const MAX_RECORDS=200;
const MAX_FIELD=1500;
// No historical fixes have an independently accredited content digest and
// correlated red/green provenance yet. Do not install an approval mechanism
// until it is reviewed on protected main with authentic evidence. In
// particular, callers cannot supply an attestor or affect authorization.
const isRecord=o=>o!==null&&typeof o==='object'&&!Array.isArray(o);
const lower=c=>c>='a'&&c<='z';
const upper=c=>c>='A'&&c<='Z';
const digit=c=>c>='0'&&c<='9';
function validId(x){
 if(typeof x!=='string'||x.length<6||x.length>80)return false;
 for(let i=0;i<x.length;i++){
  const c=x[i];
  if(!lower(c)&&!upper(c)&&!digit(c)&&c!=='_'&&c!=='-')return false;
 }
 return true;
}
function validRule(x){
 if(typeof x!=='string'||x.length<3||x.length>76||!upper(x[0]))return false;
 for(let i=1;i<x.length;i++){
  const c=x[i];
  if(!upper(c)&&!digit(c)&&c!=='_')return false;
 }
 return true;
}
function hex(x){
 if(typeof x!=='string'||x.length!==40)return false;
 for(let i=0;i<x.length;i++){
  const c=x[i];
  if(!digit(c)&&!(c>='a'&&c<='f')&&!(c>='A'&&c<='F'))return false;
 }
 return true;
}
function hasPrefix(s,prefix){
 if(s.length<prefix.length)return false;
 for(let i=0;i<prefix.length;i++){
  if(s[i]!==prefix[i])return false;
 }
 return true;
}
function validUrl(x){
 if(typeof x!=='string'||x.length>230)return false;
 const p1='https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/';
 const p2='https://github.com/cardenaspiero255-lang/gamehub-ultra/commit/';
 const start=hasPrefix(x,p1)?p1.length:hasPrefix(x,p2)?p2.length:-1;
 if(start<0)return false;
 const end=x[x.length-1]==='/'?x.length-1:x.length;
 if(end<=start)return false;
 for(let i=start;i<end;i++){
  const c=x[i];
  if(!lower(c)&&!digit(c))return false;
 }
 return true;
}
function boundedTests(tests){
 if(!Array.isArray(tests))return false;
 const count=tests.length;
 if(!Number.isSafeInteger(count)||count<1||count>12)return false;
 for(let i=0;i<count;i++){
  const t=tests[i];
  if(typeof t!=='string'||t.length>220||!t.trim())return false;
 }
 return true;
}
function boundedEntry(raw){
 // Bound decoded primitive types BEFORE memory.vetted() can coerce them.
 // Never stringify an untrusted object, array, getter or deep JSON value.
 return isRecord(raw)&&raw.type==='verified_fix'&&
  validId(raw.id)&&validRule(raw.rule)&&
  typeof raw.domain==='string'&&
   (raw.domain==='android'||raw.domain==='voice'||raw.domain==='network'||
    raw.domain==='ci'||raw.domain==='security'||raw.domain==='ai'||
    raw.domain==='performance'||raw.domain==='general')&&
  hex(raw.fixSha)&&hex(raw.testSha)&&validUrl(raw.evidenceUrl)&&
  typeof raw.summary==='string'&&raw.summary.length>=20&&raw.summary.length<=MAX_FIELD&&
  typeof raw.advice==='string'&&raw.advice.length>=20&&raw.advice.length<=MAX_FIELD&&
  boundedTests(raw.tests);
}
function parseLedger(value){
 // Snapshot every untrusted header field once. Re-reading a getter or proxy
 // after checking MAX_RECORDS would permit an oversized second value.
 if(!isRecord(value))throw Error('INVALID_LEDGER_HEADER');
 let schema,revision,rawEntries,entryCount;
 try{
  schema=value.schema;
  revision=value.revision;
  rawEntries=value.entries;
  if(!Array.isArray(rawEntries))throw Error('INVALID_LEDGER_HEADER');
  entryCount=rawEntries.length;
 }catch{throw Error('INVALID_LEDGER_HEADER');}
 if(schema!==SCHEMA||!Number.isSafeInteger(revision)||revision<0||
    !Number.isSafeInteger(entryCount)||entryCount<0||entryCount>MAX_RECORDS)
  throw Error('INVALID_LEDGER_HEADER');
 const ids=new Set();
 // Captured native Array.from maps exactly the validated count, without
 // mutable Array.prototype.push or a hostile source iterator.
 const entries=TRUSTED_ARRAY_FROM({length:entryCount},(_,index)=>{
  let raw;
  try{raw=rawEntries[index];}catch{throw Error('INVALID_LEDGER_ENTRY');}
  if(!isRecord(raw))throw Error('INVALID_LEDGER_ENTRY');
  // One read of each untrusted property. Validate and persist this exact
  // snapshot: accessors cannot swap a valid primitive for an unsafe value.
  let snap;
  try{
   const id=raw.id,type=raw.type,rule=raw.rule,domain=raw.domain;
   const fixSha=raw.fixSha,testSha=raw.testSha,evidenceUrl=raw.evidenceUrl;
   const summary=raw.summary,advice=raw.advice,tests=raw.tests;
   if(!Array.isArray(tests))throw Error('INVALID_LEDGER_ENTRY');
   const count=tests.length;
   if(!Number.isSafeInteger(count)||count<1||count>12)throw Error('INVALID_LEDGER_ENTRY');
   const copy=TRUSTED_ARRAY_FROM({length:count},(_,i)=>tests[i]);
   snap={id,type,rule,domain,fixSha,testSha,evidenceUrl,summary,advice,tests:copy};
  }catch{throw Error('INVALID_LEDGER_ENTRY');}
  if(!boundedEntry(snap)||!memory.vetted(snap)||ids.has(snap.id))
   throw Error('INVALID_LEDGER_ENTRY');
  ids.add(snap.id);
  return TRUSTED_FREEZE({id:snap.id,type:'verified_fix',rule:snap.rule,
   domain:snap.domain,fixSha:snap.fixSha.toLowerCase(),testSha:snap.testSha.toLowerCase(),
   evidenceUrl:snap.evidenceUrl,summary:snap.summary,advice:snap.advice,
   tests:TRUSTED_FREEZE(snap.tests)});
 });
 return TRUSTED_FREEZE(entries);
}
// Stable, length-preserving canonical content format. A SHA pair by itself
// never authenticates rule, domain, remediation advice or regression tests.
function entryDigest(e){
 const fields=[e.id,e.type,e.rule,e.domain,e.fixSha,e.testSha,
  e.evidenceUrl,e.summary,e.advice,e.tests];
 return createHash('sha256').update(JSON.stringify(fields),'utf8').digest('hex');
}
/**
 * Historical fixes require a digest committed following independent
 * red/green attestation in trusted main. Until that proof exists, the
 * private approved-digest allowlist remains empty and all records quarantine.
 * Legacy caller-supplied callbacks are NEVER executed or trusted.
 */
async function verifiedEntries(ledger,_ignoredVerifier){
 // Always parse and validate the complete untrusted ledger, then fail closed:
 // zero independently approved historical entries means zero promoted fixes.
 // This does not invoke caller code, Reflect.apply or mutable prototype
 // operations on the security authorization path.
 parseLedger(ledger);
 return TRUSTED_EMPTY;
}
// The reviewed historical allowlist is empty: return ONLY curated playbooks
// until a separately attested record can be installed by trusted-main.
// A tampered Array.prototype.sort/slice can fabricate the result of search(),
// so rebuild every returned row from frozen, private source material.
function certifyCurated(row){
 if(!row||row.type!=='playbook'||row.verification!=='curated_playbook'||
    typeof row.id!=='string'||typeof row.score!=='number'||
    !Number.isFinite(row.score)||row.score<0)
  throw Error('CURATED_MEMORY_UNVERIFIED');
 const original=trustedCuratedById(row.id);
 if(!original||row.domain!==original.domain||row.rule!==original.rule||
    row.advice!==original.advice||row.evidenceUrl!==null||
    !Array.isArray(row.tests))
  throw Error('CURATED_MEMORY_UNVERIFIED');
 const n=original.tests.length>4?4:original.tests.length;
 if(row.tests.length!==n)throw Error('CURATED_MEMORY_UNVERIFIED');
 for(let i=0;i<n;i++){
  if(row.tests[i]!==original.tests[i])throw Error('CURATED_MEMORY_UNVERIFIED');
 }
 return {id:original.id,type:'playbook',domain:original.domain,
  rule:original.rule,score:row.score,advice:original.advice,
  tests:original.tests,evidenceUrl:null,verification:'curated_playbook'};
}
async function searchVerified(query,ledger,_ignoredVerifier,max=3){
 await verifiedEntries(ledger);
 const limit=Math.max(0,Math.min(5,Number(max)||3));
 // Results from the mutable Array sort/slice stack are UNTRUSTED even when
 // generated by our own search. Rebuild from the private curated authority.
 const candidate=trustedCuratedSearch(query,[],limit);
 if(!Array.isArray(candidate)||candidate.length>limit)return TRUSTED_EMPTY;
 if(candidate.length===0)return TRUSTED_EMPTY;
 // One complete direct-literal return per supported cardinality; never
 // delegate authority to replaceable sort, slice, push or Object.freeze.
 if(candidate.length===1)return [certifyCurated(candidate[0])];
 if(candidate.length===2)return [certifyCurated(candidate[0]),certifyCurated(candidate[1])];
 if(candidate.length===3)return [certifyCurated(candidate[0]),certifyCurated(candidate[1]),certifyCurated(candidate[2])];
 if(candidate.length===4)return [certifyCurated(candidate[0]),certifyCurated(candidate[1]),certifyCurated(candidate[2]),certifyCurated(candidate[3])];
 return [certifyCurated(candidate[0]),certifyCurated(candidate[1]),certifyCurated(candidate[2]),certifyCurated(candidate[3]),certifyCurated(candidate[4])];
}
// Export inert primitive literals directly so the trusted main provenance
// analyzer can certify CommonJS exports without guessing a string alias.
module.exports={SCHEMA:'ultra-sentinel-family-hunter-memory/v1',
 REPO:'cardenaspiero255-lang/gamehub-ultra',MAX_RECORDS:200,
 parseLedger,entryDigest,verifiedEntries,searchVerified};
Object.freeze(module.exports);
