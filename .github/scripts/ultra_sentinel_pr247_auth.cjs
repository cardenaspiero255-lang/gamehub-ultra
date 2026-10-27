'use strict';
/**
 * CAR-29: exact, read-only PR #247 authorization. This module MUST execute
 * from trusted main, never from PR code. No approval may be generated here.
 * GitHub-authenticated, independent human PR review is mandatory.
 */
const {createHash}=require('node:crypto');
const REPO='cardenaspiero255-lang/gamehub-ultra';
const OWNER='cardenaspiero255-lang';
const PR=247;
const HEAD='8d53e229a9108869a665d35ce1836ecff8b37f3c';
const SHA=/^[a-f0-9]{40}$/;
const SOURCE='.github/workflows/sonar.yml';
const BASE_BLOB='f2831ae24e9687c8a0472c9a42df05f708815be5';
const HEAD_BLOB='5d74541253fa867a7dbea9370be5facfc1be7bf8';
const FILES=Object.freeze(new Map([
 ['.github/scripts/find_sonar_coverage_run.py',['added','bfa756c0441a66dbe00ee943f8928537249533c2']],
 ['.github/scripts/test_find_sonar_coverage_run.py',['added','d5cdd9357207cbe1747e05b037c117aa85c4e104']],
 ['.github/scripts/test_verify_sonar_jacoco.py',['added','14f414e87d05385782b6493aab6c522c1ed0e81c']],
 ['.github/scripts/verify_sonar_jacoco.py',['added','e60ace269e32fe738f05f8eefa4ccd3327054739']],
 [SOURCE,['modified',HEAD_BLOB]]
]));
function incomplete(){return Object.freeze({
 status:'INCOMPLETE',provenanceAuthenticated:false,manualReviewRequired:true,
 independentReviewVerified:false,autoApproveAllowed:false,autoMergeAllowed:false
});}
function isExactPr(pr,mainSha){
 return pr?.number===PR&&pr.state==='open'&&pr.draft===false&&
  pr.changed_files===FILES.size&&pr.author_association==='OWNER'&&
  pr.user?.login===OWNER&&pr.user.id===322222186&&pr.user.type==='User'&&
  pr.head?.repo?.full_name===REPO&&pr.base?.repo?.full_name===REPO&&
  pr.base.ref==='main'&&pr.head.sha===HEAD&&SHA.test(mainSha||'')&&
  pr.base.sha===mainSha;
}
function exactFiles(files){
 if(!Array.isArray(files)||files.length!==FILES.size)return false;
 const seen=new Set();
 for(const item of files){
  const wanted=FILES.get(item?.filename);
  if(!wanted||seen.has(item.filename)||item.status!==wanted[0]||
     item.sha!==wanted[1])return false;
  seen.add(item.filename);
 }
 return seen.size===FILES.size;
}
function independentApproval(reviews){
 if(!Array.isArray(reviews)||reviews.length>1000)return null;
 const latest=new Map();
 for(const r of reviews){
  if(!r||!Number.isSafeInteger(r.id)||r.id<1||
     !['APPROVED','CHANGES_REQUESTED','DISMISSED'].includes(r.state))continue;
  if(r.user?.type!=='User'||typeof r.user.login!=='string')continue;
  const key=r.user.login.toLowerCase();
  if(key===OWNER.toLowerCase())continue;
  const prev=latest.get(key);
  if(!prev||prev.id<r.id)latest.set(key,r);
 }
 const decisions=[...latest.values()];
 if(decisions.some(r=>r.state==='CHANGES_REQUESTED'))return null;
 // GitHub author association is API-generated, not read from a PR comment.
 const signed=decisions.filter(r=>r.state==='APPROVED'&&
  ['OWNER','MEMBER','COLLABORATOR'].includes(r.author_association)&&
  r.commit_id===HEAD);
 if(signed.length<1)return null;
 signed.sort((a,b)=>b.id-a.id);
 return signed[0];
}
function evaluatePr247({pr,files,reviews,mainSha,baseWorkflowSha,headWorkflowSha}={}){
 if(!isExactPr(pr,mainSha)||!exactFiles(files)||
    baseWorkflowSha!==BASE_BLOB||headWorkflowSha!==HEAD_BLOB)return incomplete();
 const review=independentApproval(reviews);
 if(!review)return incomplete();
 return Object.freeze({
  status:'AUTHENTICATED_MANUAL_PR247',headSha:HEAD,mainSha,
  workflowBlobSha:HEAD_BLOB,approvalId:review.id,
  reviewerLogin:review.user.login,provenanceAuthenticated:true,
  independentReviewVerified:true,manualReviewRequired:true,
  autoApproveAllowed:false,autoMergeAllowed:false
 });
}
function allowPr247ManualStructural(r){
 if(r?.status!=='INCOMPLETE'||!Array.isArray(r.findings)||
  r.findings.some(x=>x?.severity==='HIGH'||x?.severity==='BLOCKER')||
  r.coverage?.partial!==true||r.coverage.requested!==1||
  r.coverage.scanned!==1||!Array.isArray(r.coverage.reasons)||
  !r.coverage.reasons.includes('SENSITIVE_ACTION_ENV')||
  r.coverage.reasons.some(x=>!['SENSITIVE_ACTION_ENV','WORKFLOW_SEMANTIC_INCOMPLETE'].includes(x))||
  !Array.isArray(r.results)||r.results.length!==1||
  r.results[0].path!==SOURCE||r.results[0].status!=='INCOMPLETE')return false;
 return true;
}
function verifiedBlob(response,expected){
 if(response?.type!=='file'||response.encoding!=='base64'||
  response.sha!==expected||!Number.isSafeInteger(response.size)||
  response.size<1||response.size>160000||typeof response.content!=='string')return false;
 const encoded=response.content.replace(/\s/g,'');
 if(!/^[A-Za-z0-9+/]*={0,2}$/.test(encoded))return false;
 const bytes=Buffer.from(encoded,'base64');
 return bytes.length===response.size&&
  createHash('sha1').update('blob '+bytes.length+'\0').update(bytes).digest('hex')===expected;
}
async function attestScopedPr247({github,owner,repo,pr,files}={}){
 if(pr?.number!==PR)return incomplete();
 if(owner!==OWNER||repo!=='gamehub-ultra'||!github?.rest?.git?.getRef||
  !github.rest.pulls?.get||!github.rest.pulls.listReviews||
  !github.rest.pulls.listFiles||!github.rest.repos?.getContent||
  typeof github.paginate!=='function')return incomplete();
 try{
  const getMain=async()=>((await github.rest.git.getRef({
   owner,repo,ref:'heads/main'})).data?.object?.sha);
  const mainSha=await getMain();
  if(!isExactPr(pr,mainSha)||!exactFiles(files))return incomplete();
  const reviewArgs={owner,repo,pull_number:PR,per_page:100};
  const authenticatedEvidence=await Promise.all([
   github.rest.repos.getContent({owner,repo,path:SOURCE,ref:mainSha}),
   github.rest.repos.getContent({owner,repo,path:SOURCE,ref:HEAD}),
   github.paginate(github.rest.pulls.listReviews,reviewArgs)
  ]);
  const mainBlob=authenticatedEvidence[0];
  const headBlob=authenticatedEvidence[1];
  const reviews=authenticatedEvidence[2];
  if(!verifiedBlob(mainBlob.data,BASE_BLOB)||
     !verifiedBlob(headBlob.data,HEAD_BLOB))return incomplete();
  const proof=evaluatePr247({pr,files,reviews,mainSha,
   baseWorkflowSha:mainBlob.data.sha,headWorkflowSha:headBlob.data.sha});
  if(proof.status!=='AUTHENTICATED_MANUAL_PR247')return incomplete();
  // Check the live PR, file list, human reviews and main HEAD again.
  // Any SHA movement or review dismissal fails closed, never reuse a prior approval.
  const fresh=(await github.rest.pulls.get({owner,repo,pull_number:PR})).data;
  const freshFiles=await github.paginate(github.rest.pulls.listFiles,reviewArgs);
  const freshReviews=await github.paginate(github.rest.pulls.listReviews,reviewArgs);
  const final=evaluatePr247({pr:fresh,files:freshFiles,reviews:freshReviews,
    mainSha:await getMain(),baseWorkflowSha:mainBlob.data.sha,
    headWorkflowSha:headBlob.data.sha});
  if(final.status!=='AUTHENTICATED_MANUAL_PR247'||final.approvalId!==proof.approvalId||
     final.reviewerLogin!==proof.reviewerLogin||final.mainSha!==proof.mainSha)return incomplete();
  return final;
 }catch(_){return incomplete();}
}
module.exports={evaluatePr247,allowPr247ManualStructural,attestScopedPr247};
