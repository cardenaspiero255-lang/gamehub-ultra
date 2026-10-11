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
