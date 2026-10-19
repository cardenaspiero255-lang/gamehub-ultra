'use strict';
// A successful reporter job is not authorization to merge.
// PASS is based only on authenticated, same-SHA Android Build and Coverage evidence.
const SHA=/^[0-9a-f]{40}$/i;
function postCiDisposition({sourceSha,linkedCount,eligibleCount,ciStatuses,apiComplete=true,
 snapshotStatus='CURRENT',exhausted=false}={}){
 const base={status:'UNKNOWN',action:'BLOCK',attested:false,
  autoApproveAllowed:false,autoMergeAllowed:false};
 if(typeof sourceSha!=='string'||!SHA.test(sourceSha)||apiComplete!==true||
  !Number.isSafeInteger(linkedCount)||linkedCount<0||
  !Number.isSafeInteger(eligibleCount)||eligibleCount<0||eligibleCount>linkedCount||
  !Array.isArray(ciStatuses)||ciStatuses.length!==eligibleCount||
  !['CURRENT','STALE'].includes(snapshotStatus))return base;
 if(snapshotStatus==='STALE'||eligibleCount===0)
  return {...base,status:'STALE',action:'ARCHIVE'};
 if(ciStatuses.some(s=>!['PASS','WAITING','FAILED','UNTRUSTED','UNKNOWN'].includes(s)))return base;
 if(ciStatuses.some(s=>s==='FAILED'||s==='UNTRUSTED'))
  return {...base,status:'FAILED'};
 if(ciStatuses.includes('UNKNOWN'))return base;
 if(ciStatuses.includes('WAITING'))return {...base,status:'WAITING',action:exhausted?'BLOCK':'RETRY'};
 return {...base,status:'PASS',action:'VERIFIED',attested:true};
}
module.exports={postCiDisposition};
