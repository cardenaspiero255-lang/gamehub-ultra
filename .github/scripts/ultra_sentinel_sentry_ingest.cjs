'use strict';
/* Read-only Sentry API intake for authorized manual Actions runs.
   SENTRY_AUTH_TOKEN never appears in the URL, output, logs, or artifacts.
   No request to a custom host and no HTTP redirect is followed. */
const https=require('node:https');
const fs=require('node:fs');
const path=require('node:path');
const {normalizeIssues,analyzeIncidents,safeSummary}=require('./ultra_sentinel_incidents.cjs');
const {releaseSha,attachVerifiedReleases,assessRelease,fetchVerifiedRuns}=require('./ultra_sentinel_evidence.cjs');
const IDENTIFIER=/^[a-z0-9][a-z0-9-]{0,49}$/;
const HOST='sentry.io';
const MAX_BODY=1024*1024;
function apiPath(org,project){
 if(!IDENTIFIER.test(org||'')||!IDENTIFIER.test(project||''))throw Error('Invalid Sentry org/project slug');
 return '/api/0/projects/'+org+'/'+project+'/issues/?statsPeriod=24h&query=is%3Aunresolved&limit=100';
}
function fetchIssues({org,project,token,request=https.request}){
 const route=apiPath(org,project);
 if(typeof token!=='string'||token.length<12||/[\r\n]/.test(token))throw Error('Missing Sentry read-only token');
 return new Promise((resolve,reject)=>{
  const req=request({
   protocol:'https:',hostname:HOST,port:443,path:route,method:'GET',timeout:15000,
   headers:{Authorization:'Bearer '+token,Accept:'application/json','User-Agent':'ultra-sentinel/incident-readonly'}
  },res=>{
   if(res.statusCode!==200){res.resume();reject(Error('Sentry request unsuccessful: HTTP '+Number(res.statusCode)));return;}
   let bytes=0,chunks=[];
   res.on('data',c=>{
    bytes+=c.length;
    if(bytes>MAX_BODY){req.destroy(Error('Sentry response exceeded size budget'));return;}
    chunks.push(c);
   });
   res.on('end',()=>{
    try{
     const decoded=JSON.parse(Buffer.concat(chunks).toString('utf8'));
     if(!Array.isArray(decoded))throw Error('Sentry returned an unexpected response');
     resolve(decoded.slice(0,100));
    }catch{reject(Error('Sentry response malformed or too large'))}
   });
   res.on('error',()=>reject(Error('Sentry response transport failed')));
  });
  req.on('timeout',()=>req.destroy(Error('Sentry request timed out')));
  req.on('error',()=>reject(Error('Sentry request failed')));
  req.end();
 });
}
function buildSanitizedReport(raw,options={}){
 const issues=normalizeIssues(raw);
 const analysis=analyzeIncidents(issues,{...options,consent:options.consent===true});
 const attestedRuns=options.consent===true&&Array.isArray(options.attestedRuns)?options.attestedRuns:[];
 const verifiedReleases=attachVerifiedReleases(raw,attestedRuns);
 const releaseHealth={};
 for(const linked of verifiedReleases)releaseHealth[linked.sha]=assessRelease(linked.sha,attestedRuns,options.consent);
 return {
  schema:'ultra-sentinel-sentry-summary/v1',
  source:'sentry-issues-read-only',
  window:'24h',snapshotCount:issues.length,
  summary:safeSummary(issues),assessment:analysis,
  verifiedReleases,releaseHealth,
  privacy:{
   rawTitles:false,rawStackTraces:false,rawDeviceIds:false,rawUsers:false,
   rawBreadcrumbs:false,rawAudio:false,tokens:false,
   retentionDays:7
  },
  cautions:['Incident metadata only, not a reproduced Android crash',
   'No root-cause confirmation, automatic patching, or rollback',
   'CI provenance for a release must be independently verified before association']
 };
}
async function main(env=process.env){
 const org=env.SENTRY_ORG_SLUG,project=env.SENTRY_PROJECT_SLUG,token=env.SENTRY_AUTH_TOKEN;
 if(env.ULTRA_SENTINEL_INCIDENTS_CONSENT!=='true')throw Error('Incident monitoring requires explicit consent');
 if(!env.RUNNER_TEMP||!path.isAbsolute(env.RUNNER_TEMP))throw Error('Runner temporary output directory missing');
 const raw=await fetchIssues({org,project,token});
 // Only GitHub's own API can attest the latest CI results; no client-supplied
 // status can authorize a release correlation or automated rollback.
 const releaseShas=[...new Set(raw.map(releaseSha).filter(Boolean))].slice(0,4);
 const attestedRuns=[];
 for(const sha of releaseShas){
  const validated=await fetchVerifiedRuns({sha,token:env.GITHUB_TOKEN});
  attestedRuns.push(...validated);
 }
 const report=buildSanitizedReport(raw,{consent:true,attestedRuns});
 const destination=path.join(env.RUNNER_TEMP,'ultra-sentinel-incident-summary.json');
 fs.writeFileSync(destination,JSON.stringify(report,null,2)+'\n',{encoding:'utf8',mode:0o600,flag:'wx'});
 console.log('Ultra Sentinel: '+report.snapshotCount+' sanitized issues assessed; decision='+report.assessment.decision);
 console.log('Privacy: raw Sentry issue content was not persisted.');
 return report;
}
if(require.main===module){
 main().catch(e=>{
  console.error('Ultra Sentinel intake stopped: '+String(e.message).replace(/Bearer\s+[A-Za-z0-9._-]+/ig,'[REDACTED]').slice(0,200));
  process.exitCode=1;
 });
}
module.exports={HOST,MAX_BODY,apiPath,fetchIssues,buildSanitizedReport,main};
