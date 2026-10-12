'use strict';
/**
 * Read-only bounded GitHub REST transport for trusted-main Ultra Sentinel.
 * Cache ONLY objects addressed by immutable commit/tree/blob SHA. Mutable
 * PR and Actions evidence is fetched fresh on EVERY security revalidation.
 * Serialize calls per runner to avoid secondary burst limits.
 * Never log credentials, use external hosts, or retry exhausted limits early.
 */
function immutablePath(path){
 if(/^git\/commits\/[a-f0-9]{40}$/i.test(path))return true;
 if(/^git\/trees\/[a-f0-9]{40}\?recursive=1$/i.test(path))return true;
 if(/^contents\/[A-Za-z0-9_.\/-]+\?ref=[a-f0-9]{40}$/i.test(path))return true;
 return false;
}
function createGitHubReader(repo,token,fetcher=globalThis.fetch,options={}){
 if(typeof repo!=='string'||!/^[-\w.]+\/[-\w.]+$/.test(repo)||
    repo.split('/').includes('..')||typeof token!=='string'||token.length<1||
    typeof fetcher!=='function')throw Error('INVALID_GITHUB_CLIENT');

 const MAX_COOLDOWN=90000;
 const cache=new Map();
 let tail=Promise.resolve(),blockedUntil=0;
 async function read(relative){
  if(blockedUntil>Date.now())throw Error('GITHUB_RATE_LIMITED');
  for(let attempt=0;attempt<2;attempt++){
   const response=await fetcher('https://api.github.com/repos/'+repo+'/'+relative,{
    method:'GET',redirect:'error',
    headers:{Authorization:'Bearer '+token,Accept:'application/vnd.github+json',
     'X-GitHub-Api-Version':'2022-11-28'},
    signal:AbortSignal.timeout(20000)
   });
   if(!response||typeof response.ok!=='boolean')throw Error('GITHUB_API_UNAVAILABLE');
   if(response.ok)return response.json();
   const status=response.status,header=key=>response.headers?.get?.(key)??null;
   const remaining=header('x-ratelimit-remaining'),retryAfter=header('retry-after');
   const limit=status===429||(status===403&&(remaining==='0'||retryAfter!==null));
   if(!limit)throw Error('GITHUB_API_UNAVAILABLE');
   let ms=60000;
   if(retryAfter!==null){
    const seconds=Number(retryAfter);
    if(!Number.isFinite(seconds)||seconds<0)throw Error('GITHUB_RATE_LIMITED');
    ms=Math.ceil(seconds*1000);
   }
   if(remaining==='0'){
    const reset=Number(header('x-ratelimit-reset'));
    if(!Number.isFinite(reset)||reset<=0)throw Error('GITHUB_RATE_LIMITED');
    ms=Math.max(ms===60000&&retryAfter===null?0:ms,
     Math.max(0,Math.ceil(reset*1000-Date.now()+1000)));
   }
   blockedUntil=Date.now()+ms;
   if(attempt===1||ms>MAX_COOLDOWN)throw Error('GITHUB_RATE_LIMITED');
   if(typeof options.pause==='function')await options.pause(ms);
   else await new Promise(resolve=>setTimeout(resolve,ms));
   blockedUntil=0;
  }
  throw Error('GITHUB_RATE_LIMITED');
 }
 async function get(relative){
  if(typeof relative!=='string'||!relative.length||
     !/^[A-Za-z0-9_.\/\?=&%-]+$/.test(relative)||relative.startsWith('/')||
     (relative.includes('..')&&!/^compare\/[a-f0-9]{40}\.\.\.[a-f0-9]{40}$/i.test(relative))||
     /%2e|%2f|%5c/i.test(relative))
   throw Error('INVALID_API_PATH');
  const task=tail.then(async()=>{
   const safe=immutablePath(relative);
   if(safe&&cache.has(relative))return structuredClone(cache.get(relative));
   const result=await read(relative);
   if(safe&&cache.size<32)cache.set(relative,structuredClone(result));
   return structuredClone(result);
  });
  tail=task.then(()=>undefined,()=>undefined);
  return task;
 }
 return get;
}
module.exports={createGitHubReader};
