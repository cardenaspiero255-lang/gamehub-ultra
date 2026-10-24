'use strict';
/** Phase-8 PR221 exact-delta intake. Not a green gate or a review waiver.
 * Must execute only from trusted main against authenticated GitHub API.
 * PR source never executes as part of its own security attestation.
 */
const {createHash}=require('node:crypto');
const REPO='cardenaspiero255-lang/gamehub-ultra';
const OWNER='cardenaspiero255-lang';
const BASELINE_MAIN='e86ac7df81ee55139babba862fc538de630943f6';
const HEAD_SHA_PATTERN=/^[a-f0-9]{40}$/;
const SOURCE='.github/workflows/android.yml';
const TEST='.github/scripts/test-sentry-release-auth-fallback.py';
const PINNED=new Map([
 [SOURCE,{base:'0f78d28d5e8974b988490bd9facdf63cb6be6485',head:'6dd175a322ac5ebe03d88c8609a1e0a92d103068'}],
 [TEST,{base:'ed497909e5cb16088f432ca91c572d716326065f',head:'7676ab22a62bc4834d45602ccee6ec81a99589f6'}],
]);
const MASK='          echo "::add-mask::$PASSWORD"\n';
const ASSIGN='          PASSWORD="$(openssl rand -hex 24)"\n';
const incomplete=()=>Object.freeze({status:'INCOMPLETE',manualReviewRequired:true,
  autoApproveAllowed:false,autoMergeAllowed:false});
const notApplicable=()=>Object.freeze({status:'NOT_APPLICABLE',manualReviewRequired:true,
  autoApproveAllowed:false,autoMergeAllowed:false});
function exactMaskDiff(before,after){
 if(typeof before!=='string'||typeof after!=='string')return false;
 if(before.includes(MASK))return false;
 if(before.split(ASSIGN).length!==3)return false;
 if(after.split(MASK).length!==3)return false;
 if(after.split(ASSIGN+MASK).length!==3)return false;
 return after.replaceAll(MASK,'')===before;
}
function verifyPr(pr, trustedMain){
 return pr?.number===221&&pr.state==='open'&&pr.changed_files===2&&
 pr.user?.login===OWNER&&pr.user?.id===322222186&&pr.user?.type==='User'&&
 pr.author_association==='OWNER'&&HEAD_SHA_PATTERN.test(pr.head?.sha||'')&&
 pr.base?.sha===trustedMain&&
 pr.head?.repo?.full_name===REPO&&pr.base?.repo?.full_name===REPO&&
 pr.base?.ref==='main';
}
function verifyFiles(files){
 if(!Array.isArray(files)||files.length!==2)return false;
 const seen=new Set();
 for(const f of files){
  if(!f||f.status!=='modified'||typeof f.filename!=='string'||seen.has(f.filename)||
      f.sha!==PINNED.get(f.filename)?.head)return false;
  seen.add(f.filename);
 }
 return seen.size===PINNED.size;
}
function verifiedBlob(body,expected){
 if(body?.type!=='file'||body.encoding!=='base64'||
   !Number.isSafeInteger(body.size)||body.size<0||body.size>128000||
   typeof body.content!=='string'||body.sha!==expected)return null;
 const b64=body.content.replace(/\s/g,'');
 if(!/^[A-Za-z0-9+/]*={0,2}$/.test(b64))return null;
 const bytes=Buffer.from(b64,'base64');
 if(bytes.length!==body.size)return null;
 const gitSha=createHash('sha1').update('blob '+bytes.length+'\0').update(bytes).digest('hex');
 if(gitSha!==expected)return null;
 return bytes.toString('utf8');
}
async function attestExactPr221({github,owner,repo}={}){
 if(owner!==OWNER||repo!=='gamehub-ultra'||!github?.rest?.pulls?.get||
    !github.rest.pulls.listFiles||!github.rest.git?.getRef||
    !github.rest.repos?.getContent)return incomplete();
 try{
  const getMain=async()=>{
   const r=(await github.rest.git.getRef({owner,repo,ref:'heads/main'})).data;
   return r?.object?.sha;
  };
  const original=(await github.rest.pulls.get({owner,repo,pull_number:221})).data;
  if(original?.number!==221)return notApplicable();
  const mainSha=await getMain();
  const headSha=original?.head?.sha;
  if(!/^[a-f0-9]{40}$/.test(mainSha||'')||!verifyPr(original,mainSha))return incomplete();
  const files=(await github.rest.pulls.listFiles({owner,repo,pull_number:221,per_page:100,page:1})).data;
  if(!verifyFiles(files))return incomplete();
  const sources=new Map();
  for(const path of [SOURCE,TEST]){
   const sha=PINNED.get(path);
   for(const label of ['base','head']){
    const commit=label==='base'?mainSha:headSha;
    const response=(await github.rest.repos.getContent({owner,repo,path,ref:commit})).data;
    const bytes=verifiedBlob(response,sha[label]);
    if(bytes===null)return incomplete();
    sources.set(label+':'+path,bytes);
   }
  }
  if(!exactMaskDiff(sources.get('base:'+SOURCE),sources.get('head:'+SOURCE)))return incomplete();
  const final=(await github.rest.pulls.get({owner,repo,pull_number:221})).data;
  if(!verifyPr(final,mainSha)||await getMain()!==mainSha)return incomplete();
  return Object.freeze({status:'AUTHENTICATED_EXACT_DELTA_MANUAL_REVIEW_REQUIRED',
   headSha,mainSha,baselineMainSha:BASELINE_MAIN,changedFiles:2,maskInsertions:2,
   provenanceAuthenticated:true,manualReviewRequired:true,
   autoApproveAllowed:false,autoMergeAllowed:false});
 }catch(_){return incomplete();}
}
module.exports={attestExactPr221,exactMaskDiff,verifiedBlob,verifyPr};
