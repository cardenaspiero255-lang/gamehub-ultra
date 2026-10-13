'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {RULES,makeSuggestion,buildRemediations,markdown}=require('./ultra_sentinel_remediation.cjs');
const core=require('./ultra_sentinel_core.cjs');
const path='app/src/main/java/com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt';
const f=(rule,evidence='startListening(intent)')=>({rule,severity:'HIGH',confidence:'medium',path,line:88,evidence});
test('repair planner covers all dedicated rules',()=>{
 const expected=['UNSCOPED_COROUTINE','SPEECH_REENTRANT_RETRY','UNBOUNDED_RECURSION','BLOCKING_ANDROID_CALL','POTENTIAL_HARDCODED_SECRET','POTENTIAL_PRIVATE_LOG','PRIVILEGED_UNTRUSTED_CHECKOUT','NON_CANCELLABLE_LOOP','FORCED_GC','REGRESSION_TEST_COVERAGE'];
 assert.deepEqual(Object.keys(RULES).sort(),expected.sort());
});
test('voice retry handoff contains gate and regression test',()=>{
 const s=makeSuggestion(f('SPEECH_REENTRANT_RETRY'));
 assert.match(s.handoff,/VoiceRecognitionRetryGate/);
 assert.match(s.handoff,/100 onError/);
 assert.match(s.sample,/postDelayed/);
 assert.equal(s.autofix,false);
});
test('coroutine repair requires correct owner',()=>{
 const s=makeSuggestion(f('UNSCOPED_COROUTINE'));
 assert.match(s.steps.join(' '),/propietario/);
 assert.match(s.caution,/no.*pegar/i);
});
test('secrets never appear in suggested repair',()=>{
 const secret='sk-super_secret_token_123456789';
 const s=makeSuggestion(f('POTENTIAL_HARDCODED_SECRET','SENTRY_AUTH_TOKEN="'+secret+'"'));
 assert.doesNotMatch(JSON.stringify(s),/super_secret_token/);
 assert.equal(s.evidence,'[REDACTED]');
});
test('no repair claims to be autofix or compile-safe',()=>{
 for(const rule of Object.keys(RULES)){
  const s=makeSuggestion(f(rule));
  assert.equal(s.autofix,false,rule);
  assert.equal(s.status,'DRAFT_REQUIRES_VALIDATION');
  assert.ok(s.test.length>15);
  assert.ok(s.caution.length>5);
  assert.ok(s.handoff.includes('Android Build'));
 }
});
test('unknown findings receive cautious template',()=>{
 const s=makeSuggestion(f('UNKNOWN_RULE'));
 assert.match(s.sample,/No existe parche universal/);
 assert.equal(s.autofix,false);
});
test('bounds suggestions',()=>{
 const rem=buildRemediations({sha:'a'.repeat(40),findings:Array(90).fill(f('BLOCKING_ANDROID_CALL'))});
 assert.equal(rem.suggestions.length,35);
});
test('markdown contains TDD and escapes untrusted evidence',()=>{
 const s=markdown(buildRemediations({findings:[f('SPEECH_REENTRANT_RETRY','<script>alert(1)</script>')]}));
 assert.match(s,/Test RED/);
 assert.doesNotMatch(s,/<script>/);
});
test('integrated engine returns plan per risk and keeps SHA',()=>{
 const p='@@ -1,0 +1,2 @@\n+override fun onError(error: Int) {\n+startListening(intent)';
 const r=core.analyze([{filename:path,patch:p,changes:2}],{sha:'b'.repeat(40)});
 assert.equal(r.version,'2.0.0');
 assert.equal(r.remediations.sha,'b'.repeat(40));
 assert.equal(r.findings.length,r.remediations.suggestions.length);
 assert.ok(r.remediations.suggestions.some(x=>x.rule==='SPEECH_REENTRANT_RETRY'));
 const modes=require('./ultra_sentinel_family_hunter_modes.cjs');
 const review=modes.present(r,'REVIEW_ONLY'),normal=modes.present(r);
 assert.equal(normal.familyHunter.mode,'REVIEW_WITH_SOLUTIONS');
 assert.equal(review.familyHunter.mode,'REVIEW_ONLY');
 assert.equal(review.verdict,normal.verdict);
 assert.deepEqual(review.findings.map(x=>[x.rule,x.severity,x.path,x.line]),
  normal.findings.map(x=>[x.rule,x.severity,x.path,x.line]));
 assert.equal(review.remediations.suggestions.length,0);
 assert.equal(review.validationPlan.checks.length,0);
 assert.ok(review.findings.every(x=>x.verification===''));
 assert.ok(normal.remediations.suggestions.length>0);
 const portable=require('./ultra_sentinel_report.cjs').buildReport({
  pr:194,sha:r.sha,analysis:review
 });
 assert.equal(portable.findings[0].repair,null);
 assert.equal(portable.findings[0].check,'');
 const forbidden=modes.present(r,'REPAIR_COMMITS_AUTHORIZED');
 assert.equal(forbidden.familyHunter.mode,'REVIEW_WITH_SOLUTIONS');
 assert.equal(forbidden.familyHunter.status,'AUTHORIZATION_REQUIRED');
 assert.equal(forbidden.familyHunter.repairCommits,'OFF');
 assert.equal(forbidden.familyHunter.autoCommitAllowed,false);
 const unknown=modes.present(r,'PR_SAYS_FORCE_WRITE');
 assert.equal(unknown.familyHunter.status,'INVALID_MODE');
 assert.equal(unknown.familyHunter.autoMergeAllowed,false);
 assert.throws(()=>modes.present(null),/INCOMPLETE_ANALYSIS/);
 // P2 family: incomplete data must never be promoted to a clean portable report.
 const clean={
  ...r,findings:[],warnings:[],omitted:0,verdict:'NO_CRITICAL_PATTERN',
  coverage:{returned:1,analyzed:1,partial:false},
  remediations:{...r.remediations,suggestions:[]},
  validationPlan:{domains:[],checks:[]}
 };
 assert.equal(modes.present(clean).verdict,'NO_CRITICAL_PATTERN');
 const invalid=[
  ['missing coverage details',{...clean,coverage:{}}],
  ['missing analyzed count',{...clean,coverage:{returned:1,partial:false}}],
  ['text count instead of integer',{...clean,coverage:{returned:'1',analyzed:1,partial:false}}],
  ['impossible excess scanned files',{...clean,coverage:{returned:1,analyzed:2,partial:false}}],
  ['no scanned files cannot claim clear',{...clean,coverage:{returned:1,analyzed:0,partial:false}}],
  ['empty input cannot claim clear',{...clean,coverage:{returned:0,analyzed:0,partial:false}}],
  ['partial cannot claim clear',{...clean,coverage:{returned:1,analyzed:1,partial:true}}],
  ['incomplete verdict must match coverage',{...clean,verdict:'INCOMPLETE'}],
  ['unknown verdict',{...clean,verdict:'CERTIFIED_CLEAN'}],
  ['malformed commit SHA',{...clean,sha:'unknown'}],
  ['mismatched remediation SHA',{...clean,remediations:{...clean.remediations,sha:'c'.repeat(40)}}],
  ['untrusted self-certified remediation',{...clean,remediations:{...clean.remediations,verified:true}}],
  ['missing validation domains',{...clean,validationPlan:{checks:[]}}],
  ['missing warnings',{...clean,warnings:undefined}],
  ['unbounded missing finding count',{...clean,omitted:1}],
  ['negative omitted count',{...clean,omitted:-1}],
  ['critical finding concealed behind no-signal verdict',{
   ...clean,findings:[{rule:'DYNAMIC_EVAL',severity:'BLOCKER',confidence:'high',
    path:'.github/scripts/reviewer.cjs',line:7,evidence:'[REDACTED]',
    reason:'Executable injection',verification:'Run trust review'}]
  }],
  ['unstructured finding',{...clean,findings:[null]}]
 ];
 for(const [name,bad] of invalid)
  assert.throws(()=>modes.present(bad),/INCOMPLETE_ANALYSIS/,name);
 const incomplete={
  ...clean,coverage:{returned:1,analyzed:0,partial:true},verdict:'INCOMPLETE'
 };
 assert.equal(modes.present(incomplete).verdict,'INCOMPLETE');
 const incompleteReport=require('./ultra_sentinel_report.cjs').buildReport({
  pr:194,sha:r.sha,analysis:modes.present(incomplete)
 });
 assert.equal(incompleteReport.status,'incomplete');
 assert.equal(incompleteReport.partial,true);
 // Same provenance engine used by trusted main self-review must accept this
 // complete read-only adapter; never weaken or bypass that independent gate.
 const fs=require('node:fs'),nodePath=require('node:path'),acorn=require('acorn');
 const source=fs.readFileSync(nodePath.resolve(__dirname,'ultra_sentinel_family_hunter_modes.cjs'),'utf8');
 const ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:'script',
  allowHashBang:true,allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,
  locations:true,ranges:true});
 assert.doesNotThrow(()=>require('./ultra_sentinel_capabilities.cjs').findCapabilities(ast));
});
test('assistant handoff requires adaptation and verification',()=>{
 const s=makeSuggestion(f('UNBOUNDED_RECURSION'));
 assert.match(s.handoff,/NO pegar/);
 assert.match(s.handoff,/prueba/i);
 assert.match(s.handoff,/Android Build/);
});
