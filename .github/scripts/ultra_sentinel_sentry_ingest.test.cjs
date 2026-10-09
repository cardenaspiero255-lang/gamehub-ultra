'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {EventEmitter}=require('node:events');
const {apiPath,fetchIssues,buildSanitizedReport,HOST,MAX_BODY,main}=require('./ultra_sentinel_sentry_ingest.cjs');
test('API path stays on hardcoded Sentry host and project scope',()=>{
 assert.equal(HOST,'sentry.io');
 assert.ok(apiPath('gamehub-org','gamehub-ultra').startsWith('/api/0/projects/gamehub-org/gamehub-ultra/issues/'));
 for(const unsafe of ['https://attacker.test','../secret','a/b','org?token=x','FOO','']){
  assert.throws(()=>apiPath(unsafe,'gamehub-ultra'),/Invalid/);
 }
});
function fakeRequest(code,body){
 let captured;
 return {handler(opts,callback){
  captured=opts;
  const request=new EventEmitter();
  request.end=()=>{
   const res=new EventEmitter();res.statusCode=code;res.resume=()=>{};
   callback(res);process.nextTick(()=>{res.emit('data',Buffer.from(body));res.emit('end')});
  };
  request.destroy=(e)=>request.emit('error',e);
  return request;
 },get options(){return captured;}};
}
test('Sentry request sends auth in headers only and returns array',async()=>{
 const fake=fakeRequest(200,JSON.stringify([{id:'1'}]));
 const result=await fetchIssues({org:'example',project:'gamehub-ultra',token:'x'.repeat(16),request:fake.handler});
 assert.equal(result.length,1);
 assert.equal(fake.options.hostname,'sentry.io');
 assert.equal(fake.options.protocol,'https:');
 assert.equal(fake.options.method,'GET');
 assert.ok(!fake.options.path.includes('xxxx'));
 assert.equal(fake.options.headers.Authorization,'Bearer '+'x'.repeat(16));
});
test('non-200 including redirect is rejected without following it',async()=>{
 for(const status of [301,401,403,429,500]){
  const fake=fakeRequest(status,'[]');
  await assert.rejects(fetchIssues({org:'example',project:'gamehub-ultra',token:'x'.repeat(16),request:fake.handler}),/Sentry request unsuccessful/);
 }
});
test('oversized and malformed issue response cannot be exported',async()=>{
 const huge=fakeRequest(200,'['+' '.repeat(MAX_BODY+1)+']');
 await assert.rejects(fetchIssues({org:'example',project:'gamehub-ultra',token:'x'.repeat(16),request:huge.handler}));
 const malformed=fakeRequest(200,'{"token":"secret"}');
 await assert.rejects(fetchIssues({org:'example',project:'gamehub-ultra',token:'x'.repeat(16),request:malformed.handler}));
});
test('intake produces only sanitized artifact and never raw title/audio/PII',()=>{
 const report=buildSanitizedReport([{
  id:'18',project:{slug:'gamehub-ultra'},level:'error',count:'4',
  firstSeen:'2026-10-08T09:00:00Z',lastSeen:'2026-10-08T10:00:00Z',
  title:'password=hunter2 user@mail.com',stacktrace:'PRIVATE_STACK',audio:'PRIVATE_WAV'
 }],{consent:true});
 const out=JSON.stringify(report);
 assert.equal(report.snapshotCount,1);
 assert.ok(!out.includes('hunter2'));
 assert.ok(!out.includes('user@mail.com'));
 assert.ok(!out.includes('PRIVATE_STACK'));
 assert.ok(!out.includes('PRIVATE_WAV'));
});
test('missing consent fails before network requests',async()=>{
 await assert.rejects(main({SENTRY_AUTH_TOKEN:'x'.repeat(16),SENTRY_ORG_SLUG:'org',SENTRY_PROJECT_SLUG:'app'}),/consent/);
});


test('Sentry releases are attached only after live GitHub CI verification',()=>{
 const SHA='a'.repeat(40),RUN=99;
 const raw=[{id:'23',project:{slug:'gamehub-ultra'},level:'error',count:'10',
  firstSeen:'2026-10-08T09:00:00Z',lastSeen:'2026-10-08T10:00:00Z',
  firstRelease:{version:SHA},title:'Bearer secret123 user@private.test'}];
 const run={sha:SHA,workflow:'Android build',runId:RUN,
  verification:'verified-github-api-run'};
 const report=buildSanitizedReport(raw,{consent:true,attestedRuns:[run]});
 assert.equal(report.verifiedReleases.length,1);
 assert.equal(report.verifiedReleases[0].sha,SHA);
 assert.equal(report.releaseHealth[SHA].status,'INCOMPLETE');
 assert.ok(!JSON.stringify(report).includes('private.test'));
 const complete=buildSanitizedReport(raw,{consent:true,attestedRuns:[run,
  {...run,workflow:'Unit Test Coverage',runId:RUN+1}]});
 assert.equal(complete.releaseHealth[SHA].status,'CI_VERIFIED');
});

test('GitHub CI outage preserves sanitized Sentry incidents but never claims verified releases',async()=>{
 const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ultra-sentinel-intake-'));
 const release='a'.repeat(40),raw=[{
  id:'23',project:{slug:'gamehub-ultra'},level:'error',count:'3',
  firstSeen:'2026-10-09T01:00:00Z',lastSeen:'2026-10-09T02:00:00Z',
  firstRelease:{version:'gamehub-ultra@'+release},title:'password=PRIVATE-RAW-TITLE'
 }];
 try{
  const env={RUNNER_TEMP:dir,ULTRA_SENTINEL_INCIDENTS_CONSENT:'true',
   SENTRY_ORG_SLUG:'demo',SENTRY_PROJECT_SLUG:'gamehub-ultra',
   SENTRY_AUTH_TOKEN:'s'.repeat(20),GITHUB_TOKEN:'g'.repeat(20)};
  const report=await main(env,{
   fetchIssues:async()=>raw,
   fetchVerifiedRuns:async()=>{throw Error('GitHub CI returned HTTP 403: SECRET-IN-ERROR')}
  });
  assert.equal(report.snapshotCount,1);
  assert.equal(report.verifiedReleases.length,0);
  assert.equal(report.attestation.status,'PARTIAL');
  assert.equal(report.attestation.failedLookups,1);
  assert.ok(report.cautions.some(x=>x.includes('unavailable')));
  const exported=require('node:fs').readFileSync(path.join(dir,'ultra-sentinel-incident-summary.json'),'utf8');
  assert.ok(!exported.includes('PRIVATE-RAW-TITLE'));
  assert.ok(!exported.includes('SECRET-IN-ERROR'));
  assert.ok(!exported.includes('s'.repeat(20)));
 }finally{fs.rmSync(dir,{recursive:true,force:true})}
});

test('partial GitHub CI outage retains only independently verified releases',async()=>{
 const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ultra-sentinel-partial-'));
 const good='a'.repeat(40),bad='b'.repeat(40);
 const raw=[good,bad].map((sha,i)=>({
  id:String(i+1),project:{slug:'gamehub-ultra'},level:'error',count:'1',
  firstSeen:'2026-10-09T01:00:00Z',lastSeen:'2026-10-09T02:00:00Z',
  firstRelease:{version:'gamehub-ultra@'+sha}
 }));
 try{
  const env={RUNNER_TEMP:dir,ULTRA_SENTINEL_INCIDENTS_CONSENT:'true',
   SENTRY_ORG_SLUG:'demo',SENTRY_PROJECT_SLUG:'gamehub-ultra',
   SENTRY_AUTH_TOKEN:'s'.repeat(20),GITHUB_TOKEN:'g'.repeat(20)};
  const report=await main(env,{
   fetchIssues:async()=>raw,
   fetchVerifiedRuns:async({sha})=>{
    if(sha===bad)throw Error('rate limited');
    return ['Android build','Unit Test Coverage'].map((workflow,i)=>({
     sha,workflow,runId:i+1,verification:'verified-github-api-run'
    }));
   }
  });
  assert.equal(report.verifiedReleases.length,1);
  assert.equal(report.verifiedReleases[0].sha,good);
  assert.equal(report.releaseHealth[good].status,'CI_VERIFIED');
  assert.equal(report.releaseHealth[bad],undefined);
  assert.equal(report.attestation.failedLookups,1);
  assert.equal(report.attestation.status,'PARTIAL');
 }finally{fs.rmSync(dir,{recursive:true,force:true})}
});

test('malformed Sentry incident is not correlated with a valid release SHA',()=>{
 const sha='a'.repeat(40);
 const broken={id:'303',project:{slug:'gamehub-ultra'},level:'error',
  count:'invalid',firstSeen:'2026-10-09T01:00:00Z',
  lastSeen:'2026-10-09T02:00:00Z',firstRelease:{version:'gamehub-ultra@'+sha}};
 const attestedRuns=['Android build','Unit Test Coverage'].map((workflow,i)=>({
  sha,workflow,runId:i+1,verification:'verified-github-api-run'
 }));
 const report=buildSanitizedReport([broken],{consent:true,attestedRuns});
 assert.equal(report.snapshotCount,0);
 assert.equal(report.verifiedReleases.length,0);
 assert.deepEqual(report.releaseHealth,{});
});

test('invalid Sentry incidents never consume SHA attestation lookup budget',async()=>{
 const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ultra-sentinel-filtered-'));
 const good='f'.repeat(40);
 const invalid=Array.from({length:4},(_,i)=>({
  id:String(i+1),project:{slug:'gamehub-ultra'},level:'error',count:'invalid',
  firstSeen:'2026-10-09T01:00:00Z',lastSeen:'2026-10-09T02:00:00Z',
  firstRelease:{version:'gamehub-ultra@'+String(i+1).repeat(40)}
 }));
 const valid={id:'5',project:{slug:'gamehub-ultra'},level:'error',count:'2',
  firstSeen:'2026-10-09T01:00:00Z',lastSeen:'2026-10-09T02:00:00Z',
  firstRelease:{version:'gamehub-ultra@'+good}};
 const seen=[];
 try{
  const env={RUNNER_TEMP:dir,ULTRA_SENTINEL_INCIDENTS_CONSENT:'true',
   SENTRY_ORG_SLUG:'demo',SENTRY_PROJECT_SLUG:'gamehub-ultra',
   SENTRY_AUTH_TOKEN:'s'.repeat(20),GITHUB_TOKEN:'g'.repeat(20)};
  const report=await main(env,{
   fetchIssues:async()=>[...invalid,valid],
   fetchVerifiedRuns:async({sha})=>{
    seen.push(sha);
    return [{sha,workflow:'Android build',runId:1,
     verification:'verified-github-api-run'}];
   }
  });
  assert.deepEqual(seen,[good]);
  assert.equal(report.snapshotCount,1);
  assert.equal(report.verifiedReleases.length,1);
  assert.equal(report.verifiedReleases[0].sha,good);
 }finally{fs.rmSync(dir,{recursive:true,force:true})}
});
