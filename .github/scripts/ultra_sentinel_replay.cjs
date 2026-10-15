'use strict';
/**
 * Reproducibility manifest, NOT a paused emulator, filesystem snapshot, or time machine.
 * Metadata only: avoid uploading raw crash logs, secrets or protected environment.
 */
const REPO='cardenaspiero255-lang/gamehub-ultra';
const SHA=/^[a-f0-9]{40}$/i;
const WORKFLOWS=new Map([
 ['Android build',{run:'./gradlew --no-daemon :app:assembleDebug',reason:'Verify actual Gradle task used by the failing job before reproduction.'}],
 ['Unit Test Coverage',{run:'./gradlew --no-daemon :app:testDebugUnitTest',reason:'Coverage invocation may differ; inspect the workflow definition.'}],
 ['Ultra Sentinel Core Tests',{run:'node --test .github/scripts/ultra_sentinel_*.test.cjs',reason:'Runs only reviewer JavaScript tests.'}]
]);
function cleaned(v,n=130){return String(v??'').replace(/[\x00-\x1f<>]/g,' ').slice(0,n)}
function approvedURL(url,runId){
 try{const u=new URL(url);return u.protocol==='https:'&&u.hostname==='github.com'&&
  u.pathname==='/'+REPO+'/actions/runs/'+runId?u.href:null}
 catch{return null}
}
function manifest(x){
 if(x?.repo!==REPO||!Number.isSafeInteger(x.runId)||x.runId<1||!SHA.test(x.sha||''))
  throw Error('Invalid repository, run ID or commit SHA');
 if(!['failure','timed_out'].includes(x.conclusion))throw Error('Only failed/timed-out jobs are investigated');
 if(!approvedURL(x.url,x.runId))throw Error('Untrusted CI URL');
 const workflow=cleaned(x.workflow,80);
 const program=WORKFLOWS.get(workflow);
 const jobs=(Array.isArray(x.jobs)?x.jobs:[]).slice(0,30).map(j=>({
   name:cleaned(j.name,100),conclusion:['failure','timed_out','success','skipped','cancelled'].includes(j.conclusion)?j.conclusion:'unknown',
   stepNames:(Array.isArray(j.failedSteps)?j.failedSteps:[]).slice(0,8).map(n=>cleaned(n,80))
 }));
 return {
  schema:'ultra-sentinel-reproducibility/v1',repo:REPO,sha:x.sha.toLowerCase(),
  runId:x.runId,workflow,conclusion:x.conclusion,runUrl:approvedURL(x.url,x.runId),
  environment:{os:'GitHub-hosted runner metadata unavailable unless explicitly recorded',
   java:'Read exact Java/Gradle versions from this workflow run; not inferred here',
   emulator:'No emulator snapshot captured'},
  jobs,recipe:program?{
   suggestedCommand:program.run,
   caveat:program.reason,
   inspectRun:'gh run view '+x.runId+' --repo '+REPO+' --log-failed'
  }:{suggestedCommand:null,caveat:'Unknown job command; read workflow source before attempting reproduction.',inspectRun:'gh run view '+x.runId+' --repo '+REPO},
  limits:'Not a replay of process state. No breakpoints, APK snapshot, container image, private data, or logs are embedded.',
  createdAt:null
 };
}
module.exports={manifest,approvedURL};
