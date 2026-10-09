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
