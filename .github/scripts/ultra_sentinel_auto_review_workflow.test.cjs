'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const workflow = () => fs.readFileSync(path.resolve(__dirname, '../workflows/ultra-sentinel-auto-review.yml'), 'utf8');

test('Automatic CAR review runs without paid Claude, Grok, Groq or DeepSeek integrations', () => {
  const wf = workflow();
  assert.match(wf, /name: Ultra Sentinel - Automatic CAR Review/);
  assert.match(wf, /pull_request_target:/);
  assert.match(wf, /workflow_dispatch:/);
  assert.doesNotMatch(wf, /run_external_providers|matrix\.provider|\n  providers:\n|\n  consensus:\n/);
  assert.doesNotMatch(wf, /ANTHROPIC_API_KEY|XAI_API_KEY|DEEPSEEK_API_KEY|GROQ_API_KEY/);
  assert.doesNotMatch(wf, /claude_pr_review|grok_pr_review|compatible_ai_pr_review|api\.groq\.com|api\.deepseek\.com/);
});

test('automatic reviewer requires read permissions only and does not try forbidden comments', () => {
  const wf = workflow();
  assert.match(wf, /permissions:\s*\n\s*contents: read\s*\n\s*pull-requests: read/);
  assert.doesNotMatch(wf, /issues: write|pull-requests: write|contents: write/);
  assert.doesNotMatch(wf, /issues\.(createComment|updateComment)|pulls\.createReview|secrets\.[A-Z_]+/);
});

test('analyzes PR with trusted main engine and verifies current immutable SHA', () => {
  const wf = workflow();
  // Protected controls still fail closed; sole-owner exemption is checked using
  // the same trusted SHA- and identity-bound function as the independent gate.
  assert.match(wf,/evaluateProtectedChanges,hasIndependentHumanApproval,isAuthorizedSoloMaintainerPR/);
  assert.match(wf,/isAuthorizedSoloMaintainerPR\(pr,\{\s*repository:owner\+'\/'\+repo,\s*expectedSha:pr\.head\.sha\s*\}\)/);
  assert.match(wf,/if\(!independentlyApproved\)\s*\{\s*try\s*\{/);
  assert.match(wf,/if\(!independentlyApproved\)\s*core\.setFailed\('Critical controls changed:/);
  assert.match(wf,/if\(protectedChanges\.status==='BLOCKED'\|\|protectedChanges\.status==='INCOMPLETE'\)/);
  // A protected SHA-bound exception is never a general-purpose CI bypass.
  assert.match(wf,/const scopedAccepted=scopedProtected\.status==='SCOPED_AUTHENTICATED_MANUAL_REVIEW'&&/);
  assert.match(wf,/permitScopedOpaqueYaml\(structuralYaml\)/);
  assert.match(wf,/if\(ci\.status==='UNTRUSTED'&&!scopedAccepted\)\s*core\.setFailed/);
  assert.match(wf,/if\s*\(structuralYaml\.coverage\.partial&&!scopedAccepted\)\s*core\.setFailed/);
  assert.match(wf, /ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
  assert.match(wf, /persist-credentials: false/);
  assert.match(wf, /github\.rest\.pulls\.listFiles/);
  assert.match(wf, /after\.head\.sha !== pr\.head\.sha/);
  assert.match(wf, /evidence\.coverage\.partial/);
  // All three finding channels must fail closed, not merely produce warnings.
  for(const source of ['structuralYaml','supplyChain','evidence']){
    const pattern=new RegExp(source+
      String.raw`\.findings\.some\(f=>f\.severity==='HIGH'\|\|f\.severity==='BLOCKER'\)\)\s*core\.setFailed\(`);
    assert.match(wf,pattern,source+' must fail on HIGH and BLOCKER');
  }
  // The trusted repair workflow must fetch exact-SHA Kotlin context BEFORE
  // deciding which defects qualify for proposals; post-analysis is too late.
  const repair=fs.readFileSync(path.resolve(__dirname,
    '../workflows/ultra-sentinel-repair-proposals.yml'),'utf8');
  const prepare=repair.indexOf('const evidenceFiles=files.map(');
  const read=repair.indexOf('github.rest.repos.getContent(',prepare);
  const analyze=repair.indexOf('const analysis=analyze(evidenceFiles',read);
  const select=repair.indexOf('selectRepairTargets(analysis.findings',analyze);
  assert.ok(prepare>=0&&read>prepare&&analyze>read&&select>analyze);
  assert.match(repair,/ref:pr\.head\.sha/);
  assert.match(repair,/entry\.size>160000/);
  assert.match(repair,/item\.fullSource=bytes\.toString\('utf8'\)/);
  assert.match(repair,/\.\(\?:kt\|java\)/);
  assert.match(repair,/current\.head\.sha!==pr\.head\.sha/);
  assert.doesNotMatch(wf, /ref: \$\{\{ github\.event\.pull_request\.head\.sha \}\}|auto-merge\s*:\s*true/);
});

test('always writes SHA-pinned artifact and Actions summary without GitHub comment access', () => {
  const wf = workflow();
  assert.match(wf, /ultra-sentinel-core-report/);
  assert.match(wf, /writeFileSync\(/);
  assert.match(wf, /core\.summary\.addHeading\(/);
  assert.match(wf, /actions\/upload-artifact@[a-f0-9]{40}/);
  assert.match(wf, /if: always\(\)/);
  assert.match(wf, /if-no-files-found: error/);
  assert.match(wf, /retention-days: 7/);
});

test('self tests remain mandatory and potential critical defects still fail the gate', () => {
  const wf = workflow();
  // The trusted engine runs YAML tests which open workflow fixtures on disk.
  const sparse=wf.match(/sparse-checkout:\s*\|([^]*?)sparse-checkout-cone-mode:/);
  assert.ok(sparse,'explicit sparse checkout must remain enabled');
  assert.match(sparse[1],/\.github\/scripts\//);
  assert.match(sparse[1],/\.github\/workflows\//);
  assert.match(wf, /node --test .*ultra_sentinel_core\.test\.cjs/);
  assert.match(wf, /node .*ultra_sentinel_benchmark\.cjs/);
  assert.match(wf, /Incomplete patch coverage/);
  assert.match(wf, /Engine HIGH\/BLOCKER must be reproduced and resolved before merge/);
  assert.match(wf, /No external provider approval is required/);
});

test('CI evidence collects bounded pages instead of silently ignoring 101st record',()=>{
 const wf=workflow();assert.match(wf,/page=2/);
 assert.match(wf,/total<=1000/);
 assert.match(wf,/total_count!==total/);
});

test('trusted automatic reviewer fetches bounded immutable Kotlin and Java sources before analysis',()=>{
 const wf=workflow();
 const start=wf.indexOf('const evidenceFiles=files.map(');
 const target=wf.indexOf('const languageTargets=evidenceFiles.filter(',start);
 const read=wf.indexOf('github.rest.repos.getContent(',target);
 const analyze=wf.indexOf('const evidence = analyze(evidenceFiles',read);
 assert.ok(start>=0&&target>start&&read>target&&analyze>read,
  'full source must be fetched before the analyzer inspects PR diff hunks');
 const excerpt=wf.slice(target,analyze);
 assert.ok(excerpt.includes('(?:kt|java)'),
  'Kotlin and Java production files must both be included');
 assert.ok(excerpt.includes('ref:pr.head.sha'),
  'only fetch source at the immutable PR HEAD');
 assert.ok(excerpt.includes('size>160000'),
  'preserve the source-size bound');
 assert.ok(excerpt.includes('item.fullSource=bytes.toString("utf8")'));
 assert.ok(excerpt.includes('item.status!=="removed"'));
});

test('trusted auto-review attests omitted added blobs before Core without relaxing failures',()=>{
 const wf=workflow();
 const start=wf.indexOf('const evidenceFiles=files.map(');
 const recover=wf.indexOf('recoverAddedPatch(item,body)',start);
 const analyze=wf.indexOf('const evidence = analyze(evidenceFiles',start);
 assert.ok(start>=0&&recover>start&&analyze>recover,
  'evidence must be reconstructed before Core');
 const section=wf.slice(start,analyze);
 assert.match(section,/item.status==='added'/);
 assert.match(section,/ref:pr\.head\.sha/);
 assert.match(section,/missingAddedPatch\(item\)/);
 assert.match(wf,/if \(evidence\.coverage\.partial\) core\.setFailed/);
});
