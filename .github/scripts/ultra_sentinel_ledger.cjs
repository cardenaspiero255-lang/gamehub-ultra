'use strict';
/* Immutable, review-gated repair evidence ledger. This module does not write GitHub
 * or commit itself. Only trusted reviewers may commit JSON via normal PR controls.
 * CI claims from untrusted inputs never become VERIFIED automatically. */
const {recordVerifiedRepair}=require('./ultra_sentinel_incidents.cjs');
const SCHEMA='ultra-sentinel-repair-ledger/v1';
const SHA=/^[a-f0-9]{40}$/i;
function empty(){return {schema:SCHEMA,revision:0,entries:[]}}
function validate(ledger){
 if(!ledger||ledger.schema!==SCHEMA||!Number.isSafeInteger(ledger.revision)||ledger.revision<0||
  !Array.isArray(ledger.entries)||ledger.entries.length>500)throw Error('Invalid repair ledger');
 const ids=new Set();
 for(const e of ledger.entries){
  if(!e||typeof e.id!=='string'||ids.has(e.id)||!SHA.test(e.fixSha||'')||
   !['staged','reviewed','revoked'].includes(e.state)||
   !Number.isSafeInteger(e.sequence)||e.sequence<1||
   !Number.isFinite(Date.parse(e.expiresAt)))
   throw Error('Repair ledger entry malformed or duplicated');
  ids.add(e.id);
 }
 return true;
}
function stage(ledger,record,now=Date.now()){
 validate(ledger);
 if(ledger.entries.length>=500)throw Error('Repair ledger is full');
 const claim=recordVerifiedRepair(record,now);
 if(claim.status!=='PROVENANCE_RECORDED')return {accepted:false,reason:claim.reason,ledger};
 if(ledger.entries.some(x=>x.id===claim.id))return {accepted:false,reason:'duplicate_id',ledger};
 const entry={id:claim.id,rule:claim.rule,fixSha:claim.fixSha,
  redTestSha:claim.redTestSha,greenTestSha:claim.greenTestSha,
  evidenceUrl:claim.evidenceUrl,expiresAt:claim.expiresAt,
  state:'staged',sequence:ledger.revision+1,reviewedBy:null};
 const next={schema:SCHEMA,revision:ledger.revision+1,entries:[...ledger.entries,entry]};
 validate(next);return {accepted:true,ledger:next,entry};
}
function review(ledger,{id,decision,reviewer,currentHeadSha}={}){
 validate(ledger);
 if(typeof reviewer!=='string'||!/^[a-z0-9-]{3,70}$/i.test(reviewer)||
  !['reviewed','revoked'].includes(decision)||!SHA.test(currentHeadSha||''))
  throw Error('Human review decision requires valid reviewer and SHA');
 const existing=ledger.entries.find(x=>x.id===id);
 if(!existing||existing.fixSha!==currentHeadSha.toLowerCase())throw Error('Stale or missing evidence SHA');
 if(existing.state!=='staged'&&decision==='reviewed')throw Error('Only staged evidence can be promoted');
 const entries=ledger.entries.map(e=>e.id===id?{...e,state:decision,reviewedBy:reviewer}:e);
 return {schema:SCHEMA,revision:ledger.revision+1,entries};
}
function active(ledger,now=Date.now()){
 validate(ledger);
 return ledger.entries.filter(e=>e.state==='reviewed'&&Date.parse(e.expiresAt)>now)
  .map(e=>({id:e.id,rule:e.rule,fixSha:e.fixSha,evidenceUrl:e.evidenceUrl,
   verification:'human-reviewed-repository-record-not-CI-attestation'}));
}
module.exports={SCHEMA,empty,validate,stage,review,active};
