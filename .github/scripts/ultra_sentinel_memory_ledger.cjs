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
const SCHEMA='ultra-sentinel-family-hunter-memory/v1';
const REPO='cardenaspiero255-lang/gamehub-ultra';
const MAX_RECORDS=200;
const MAX_FIELD=1500;
const isRecord=o=>o!==null&&typeof o==='object'&&!Array.isArray(o);
const hex=x=>typeof x==='string'&&/^[a-f0-9]{40}$/i.test(x);
const validUrl=x=>typeof x==='string'&&x.length<=230&&
 /^https:\/\/github\.com\/cardenaspiero255-lang\/gamehub-ultra\/(?:pull|commit)\/[a-z0-9]+\/?$/.test(x);
function boundedEntry(raw){
 // Bound decoded primitive types BEFORE memory.vetted() can coerce them.
 // Never stringify an untrusted object, array, getter or deep JSON value.
 return isRecord(raw)&&raw.type==='verified_fix'&&
  typeof raw.id==='string'&&/^[a-z0-9_-]{6,80}$/i.test(raw.id)&&
  typeof raw.rule==='string'&&/^[A-Z][A-Z_0-9]{2,75}$/.test(raw.rule)&&
  typeof raw.domain==='string'&&
   ['android','voice','network','ci','security','ai','performance','general'].includes(raw.domain)&&
  hex(raw.fixSha)&&hex(raw.testSha)&&validUrl(raw.evidenceUrl)&&
  typeof raw.summary==='string'&&raw.summary.length>=20&&raw.summary.length<=MAX_FIELD&&
  typeof raw.advice==='string'&&raw.advice.length>=20&&raw.advice.length<=MAX_FIELD&&
  Array.isArray(raw.tests)&&raw.tests.length>0&&raw.tests.length<=12&&
  raw.tests.every(t=>typeof t==='string'&&!!t.trim()&&t.length<=220);
}
function parseLedger(value){
 if(!isRecord(value)||value.schema!==SCHEMA||
    !Number.isSafeInteger(value.revision)||value.revision<0||
    !Array.isArray(value.entries)||value.entries.length>MAX_RECORDS)
  throw Error('INVALID_LEDGER_HEADER');
 const ids=new Set(),entries=[];
 for(const raw of value.entries){
  if(!boundedEntry(raw)||!memory.vetted(raw)||ids.has(raw.id))
   throw Error('INVALID_LEDGER_ENTRY');
  ids.add(raw.id);
  // Copy allowlisted primitives; ignore claimed verification flags and commands.
  entries.push(Object.freeze({id:raw.id,type:'verified_fix',rule:raw.rule,
   domain:raw.domain,fixSha:raw.fixSha.toLowerCase(),testSha:raw.testSha.toLowerCase(),
   evidenceUrl:raw.evidenceUrl,summary:raw.summary,advice:raw.advice,
   tests:Object.freeze([...raw.tests])}));
 }
 return Object.freeze(entries);
}
// Stable, length-preserving canonical content format. A SHA pair by itself
// never authenticates rule, domain, remediation advice or regression tests.
function entryDigest(e){
 const fields=[e.id,e.type,e.rule,e.domain,e.fixSha,e.testSha,
  e.evidenceUrl,e.summary,e.advice,e.tests];
 return createHash('sha256').update(JSON.stringify(fields),'utf8').digest('hex');
}
/**
 * verifier MUST be supplied by trusted-main infrastructure, not PR source.
 * It must derive entryDigest from immutable independently reviewed content,
 * attest testSha inclusion/ancestry in fixSha, and independently establish
 * the failing-before / passing-after regression. Echoing caller data is NOT
 * evidence. Without all facts, this reader always refuses promotion.
 */
async function verifiedEntries(ledger,verifier){
 if(typeof verifier!=='function')throw Error('TRUSTED_VERIFIER_REQUIRED');
 const entries=parseLedger(ledger),confirmed=[];
 for(const e of entries){
  const digest=entryDigest(e);
  const facts=await verifier({repository:REPO,fixSha:e.fixSha,testSha:e.testSha,
   evidenceUrl:e.evidenceUrl,entryDigest:digest});
  if(!isRecord(facts)||facts.repository!==REPO||
     facts.fixSha!==e.fixSha||facts.testSha!==e.testSha||
     facts.entryDigest!==digest||
     facts.mergedToProtectedMain!==true||facts.testsPassedAtFixSha!==true||
     facts.independentReviewPassed!==true||
     facts.testShaIncludedInFixHistory!==true||
     facts.redTestFailedBeforeFix!==true||
     facts.greenTestPassedAtFixSha!==true)
    continue;
  confirmed.push(e);
 }
 return Object.freeze(confirmed);
}
async function searchVerified(query,ledger,verifier,max=3){
 const approved=await verifiedEntries(ledger,verifier);
 return memory.search(query,approved,max);
}
module.exports={SCHEMA,REPO,MAX_RECORDS,parseLedger,entryDigest,verifiedEntries,searchVerified};
