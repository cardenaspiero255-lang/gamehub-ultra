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
// No historical fixes have an independently accredited content digest and
// correlated red/green provenance yet. Do not install an approval mechanism
// until it is reviewed on protected main with authentic evidence. In
// particular, callers cannot supply an attestor or affect authorization.
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
 return Object.freeze([]);
}
// Only verifiedEntries may contribute historical fixes; untrusted callers
// cannot make direct memory.search mark arbitrary extra records as confirmed.
function rankApproved(query,entries){
 let rule='',domain='',parts=[];
 if(typeof query==='string')parts=[query];
 else if(isRecord(query)){
  if(typeof query.rule==='string'){rule=query.rule;parts.push(rule);}
  if(typeof query.path==='string')parts.push(query.path);
  if(typeof query.reason==='string')parts.push(query.reason);
  if(typeof query.text==='string')parts.push(query.text);
  if(typeof query.domain==='string')domain=query.domain;
 }
 const q=memory.terms(parts.join(' '));
 if(!q.length)return [];
 const out=[];
 for(const entry of entries){
  const tokens=memory.terms(entry.summary+' '+entry.rule+' '+entry.domain);
  let score=0;
  for(const token of q){if(tokens.includes(token))score+=1;}
  if(rule&&rule===entry.rule)score+=7;
  if(domain&&domain===entry.domain)score+=2;
  if(score<=0)continue;
  out.push({id:entry.id,type:'verified_fix',domain:entry.domain,rule:entry.rule,
   score:Number(score.toFixed(3)),advice:entry.advice.slice(0,380),
   tests:entry.tests.slice(0,4).map(x=>x.slice(0,180)),
   evidenceUrl:entry.evidenceUrl,verification:'confirmed_tests'});
 }
 return out;
}
async function searchVerified(query,ledger,verifier,max=3){
 const approved=await verifiedEntries(ledger); // ignore untrusted legacy callback
 const limit=Math.max(0,Math.min(5,Number(max)||3));
 const curated=memory.search(query,[],5);
 return [...curated,...rankApproved(query,approved)].sort((a,b)=>
  b.score-a.score||a.id.localeCompare(b.id)).slice(0,limit);
}
// Export inert primitive literals directly so the trusted main provenance
// analyzer can certify CommonJS exports without guessing a string alias.
module.exports={SCHEMA:'ultra-sentinel-family-hunter-memory/v1',
 REPO:'cardenaspiero255-lang/gamehub-ultra',MAX_RECORDS:200,
 parseLedger,entryDigest,verifiedEntries,searchVerified};
