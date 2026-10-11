'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const evidence=require('./ultra_sentinel_evidence.cjs');
const SHA='a'.repeat(40),F='.github/workflows/stress.yml',CO='actions/checkout@'+'f'.repeat(40),PREF='$'+'{{ github.head_ref }}';
const scan=src=>reviewWorkflows({sha:SHA,expected:[F],sources:{[F]:src}});
const warns=(x,rule)=>x.findings.some(f=>f.rule===rule);
const kotlin=lines=>analyze([{filename:'app/src/main/java/com/cardenaspiero255/gamehubultra/Probe.kt',
 status:'modified',changes:lines.length,patch:'@@ -0,0 +1,'+lines.length+' @@\n'+
 lines.map(x=>'+'+x).join('\n')+'\n'}],{sha:SHA});
test('Codex P1 folded scalar privileged trigger detects risky checkout',()=>{
 const body='on: >-\n  pull_request_target\njobs:\n  x:\n    steps:\n      - uses: '+CO+
  '\n        with:\n          ref: '+PREF+'\n';
 assert.ok(warns(scan(body),'PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P2 folded scalar write-all is never treated as a normal permission',()=>{
 assert.ok(warns(scan('permissions: >-\n  write-all\n'),'PRIVILEGED_WRITE_TOKEN'));
});
test('Codex P2 Kotlin interpolated URL cannot swallow later runBlocking',()=>{
 const src='val url = "https://'+'$'+'{host}"; runBlocking { work() }';
 assert.ok(warns(kotlin([src]),'BLOCKING_ANDROID_CALL'));
});
test('Codex P2 multiline Kotlin raw strings remain data, not executable statements',()=>{
 const out=kotlin(['val documentation = """','runBlocking { work() }','"""','println("done")']);
 assert.ok(!warns(out,'BLOCKING_ANDROID_CALL'));
});
test('Codex P2 multiline Kotlin block comments do not fake blocking calls',()=>{
 const out=kotlin(['/* docs','Thread.sleep(600)','*/','println("safe")']);
 assert.ok(!warns(out,'BLOCKING_ANDROID_CALL'));
});
test('Codex P1 release evidence requires pinned trusted workflow file blobs',()=>{
 const file='.github/workflows/android.yml';
 const run={name:'Android build',workflow_id:131,path:file,id:99,head_sha:SHA,
 status:'completed',conclusion:'success',event:'push',head_branch:'main',run_attempt:1,
 repository:{full_name:'cardenaspiero255-lang/gamehub-ultra'},
 html_url:'https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/99'};
 assert.equal(evidence.validateRun(run,SHA,{'Android build':{id:131,path:file,blobSha:'f'.repeat(40)}}),null);
 assert.ok(evidence.TRUSTED_BLOBS?.['Android build']);
});
