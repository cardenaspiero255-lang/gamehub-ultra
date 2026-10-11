'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {buildSanitizedReport,renderActionsSummary}=require('./ultra_sentinel_sentry_ingest.cjs');
const wf=()=>fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-sentry-incidents.yml'),'utf8');
test('Sentry supports explicit manual consent and scheduled monitoring behind TWO disabled-by-default flags',()=>{
 const src=wf();
 assert.match(src,/schedule:/);
 assert.match(src,/cron:/);
 assert.match(src,/ULTRA_SENTINEL_SENTRY_MONITORING_ENABLED/);
 assert.match(src,/ULTRA_SENTINEL_SENTRY_CONSENT/);
 assert.match(src,/github\.event_name == 'schedule'/);
 assert.match(src,/github\.event_name == 'workflow_dispatch'/);
 assert.match(src,/inputs\.consent == true/);
});
test('Sentry integration is strictly read only and outputs aggregated data only',()=>{
 const src=wf();
 assert.match(src,/contents: read/);
 assert.match(src,/actions: read/);
 assert.doesNotMatch(src,/contents: write|issues: write|pull-requests: write/);
 assert.match(src,/SENTRY_AUTH_TOKEN: \$\{\{ secrets\.SENTRY_AUTH_TOKEN \}\}/);
 assert.match(src,/ultra_sentinel_sentry_ingest\.cjs/);
 assert.match(src,/if-no-files-found: error/);
 assert.doesNotMatch(src,/curl.*SENTRY_AUTH_TOKEN|echo.*SENTRY_AUTH_TOKEN/);
});
test('Actions summary never publishes PII, passwords, raw titles or stack traces',()=>{
 const data=[{id:'123',project:{slug:'gamehub-ultra'},level:'error',count:'5',
  firstSeen:'2026-10-08T09:00:00Z',lastSeen:'2026-10-08T10:00:00Z',
  title:'user@mail.com password=SuperSecret',stacktrace:'STACK_PRIVATE',audio:'WAV_PRIVATE'}];
 const report=buildSanitizedReport(data,{consent:true});
 const md=renderActionsSummary(report);
 assert.match(md,/Ultra Sentinel.*Sentry/);
 assert.match(md,/5/);
 for(const marker of ['SuperSecret','user@mail.com','STACK_PRIVATE','WAV_PRIVATE'])
  assert.ok(!md.includes(marker),marker);
 assert.doesNotMatch(md,/\|.*https:\/\/sentry\.io/);
});
test('Sentry actions summary strictly reports unverified releases as unknown',()=>{
 const report=buildSanitizedReport([], {consent:true});
 const md=renderActionsSummary(report);
 assert.match(md,/INSUFFICIENT_EVIDENCE/);
 assert.match(md,/No automatic rollback/);
});
test('Sentry rejects arbitrary rich report values and errors rather than serializing secrets',()=>{
 assert.throws(()=>renderActionsSummary({snapshotCount:1,assessment:{decision:'something malicious'}}),/invalid/i);
});
