'use strict';
/**
 * Family Hunter durable error memory. Ledger records are untrusted until a
 * separately privileged verifier checks the actual merge and CI at each SHA.
 * This module performs NO network requests, writes, workflow dispatches or merges.
 */
const memory=require('./ultra_sentinel_memory.cjs');
const SCHEMA='ultra-sentinel-repair-ledger/v1';
const REPO='cardenaspiero255-lang/gamehub-ultra';
const MAX_RECORDS=200;
const MAX_FIELD=1500;
const isRecord=o=>o!==null&&typeof o==='object'&&!Array.isArray(o);
function parseLedger(value){
 if(!isRecord(value)||value.schema!==SCHEMA||
    !Number.isSafeInteger(value.revision)||value.revision<0||
    !Array.isArray(value.entries)||value.entries.length>MAX_RECORDS)
  throw Error('INVALID_LEDGER_HEADER');
 const ids=new Set(),entries=[];
 for(const raw of value.entries){
  if(!isRecord(raw)||!memory.vetted(raw)||raw.type!=='verified_fix'||
     !['android','voice','network','ci','security','general'].includes(raw.domain)||
     typeof raw.rule!=='string'||!/^[A-Z][A-Z_0-9]{2,75}$/.test(raw.rule)||
     typeof raw.summary!=='string'||raw.summary.length>MAX_FIELD||
     typeof raw.advice!=='string'||raw.advice.length>MAX_FIELD||
     raw.tests.length>12||raw.tests.some(t=>typeof t!=='string'||!t.trim()||t.length>220)||
     ids.has(raw.id))
   throw Error('INVALID_LEDGER_ENTRY');
  ids.add(raw.id);
  // Copy only allowlisted fields: never consume a record's commands, verdicts,
  // badges or self-declared verification flags.
  entries.push(Object.freeze({id:raw.id,type:'verified_fix',rule:raw.rule,
   domain:raw.domain,fixSha:raw.fixSha.toLowerCase(),testSha:raw.testSha.toLowerCase(),
   evidenceUrl:raw.evidenceUrl,summary:raw.summary,advice:raw.advice,
   tests:Object.freeze([...raw.tests])}));
 }
 return Object.freeze(entries);
}
/**
 * verifier is supplied ONLY by trusted-main review infrastructure.
 * The success signal must be bound to the same repository, both immutable
 * commits, merge ancestry, completed checks and independent review.
 * GitHub plugin output or a PR-supplied JSON attestation is not a verifier.
 */
async function verifiedEntries(ledger,verifier){
 if(typeof verifier!=='function')throw Error('TRUSTED_VERIFIER_REQUIRED');
 const entries=parseLedger(ledger),confirmed=[];
 for(const e of entries){
  const facts=await verifier({repository:REPO,fixSha:e.fixSha,testSha:e.testSha,
   evidenceUrl:e.evidenceUrl});
  if(!isRecord(facts)||facts.repository!==REPO||
     facts.fixSha!==e.fixSha||facts.testSha!==e.testSha||
     facts.mergedToProtectedMain!==true||facts.testsPassedAtFixSha!==true||
     facts.independentReviewPassed!==true)
    continue;
  // Never let a user-provided field become "verified" without GitHub evidence.
  confirmed.push(e);
 }
 return Object.freeze(confirmed);
}
async function searchVerified(query,ledger,verifier,max=3){
 const approved=await verifiedEntries(ledger,verifier);
 return memory.search(query,approved,max);
}
module.exports={SCHEMA,REPO,MAX_RECORDS,parseLedger,verifiedEntries,searchVerified};
