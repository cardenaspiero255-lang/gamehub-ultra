'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const gate=require('./ultra_sentinel_intelligent_gate.cjs');
const B='a'.repeat(40),H='b'.repeat(40),M='c'.repeat(40);
const finding=(extra={})=>({rule:'JS_PROVENANCE',severity:'HIGH',path:'.github/scripts/engine.cjs',line:10,astKind:'CallExpression',symbol:'validateSource',context:'validate(raw)',cause:'untrusted-call',...extra});
const checks=()=>['Android build','Unit Test Coverage','Ultra Sentinel Core Tests','Trusted main Sentinel inspects Sentinel changes','read-only-review'].map(name=>({name,sha:H,source:'trusted',status:'completed',conclusion:'success'}));
const request=(over={})=>({headSha:H,baseSha:B,mergeBaseSha:M,headComplete:true,baseComplete:true,baseFindings:[],headFindings:[finding()],renames:[],changedPaths:['.github/scripts/engine.cjs'],requiredChecks:checks().map(x=>x.name),checks:checks(),...over});
test('1: requires attested immutable base, merge-base and head SHA',()=>{for(const extra of [{headSha:'head'},{baseSha:''},{mergeBaseSha:null}])assert.equal(gate.review(request(extra)).status,'INCOMPLETE');});
test('1: stable AST identity survives line movement within the same symbol',()=>{const a=gate.fingerprint(finding({line:2}));const b=gate.fingerprint(finding({line:99}));assert.equal(a,b);});
test('1: absent AST symbol keeps locations distinct instead of hiding duplicates',()=>{assert.notEqual(gate.fingerprint(finding({symbol:'',line:2})),gate.fingerprint(finding({symbol:'',line:99})))});
test('1: safe verified rename preserves base fingerprint',()=>{const old=finding({path:'.github/scripts/old.cjs'}),fresh=finding({path:'.github/scripts/new.cjs',line:32});const r=gate.review(request({baseFindings:[old],headFindings:[fresh],renames:[{status:'renamed',previous_filename:old.path,filename:fresh.path}]}));assert.equal(r.findings[0].classification,'MOVED');assert.equal(r.counts.new,0);});
test('1: changed dependencies reactivate inherited critical finding',()=>{const old=finding({path:'app/src/main/java/Legacy.kt'});const r=gate.review(request({baseFindings:[old],headFindings:[old],affectedPaths:[old.path]}));assert.equal(r.findings[0].classification,'REACTIVATED');assert.equal(r.status,'REVIEW_REQUIRED');});
test('1: historical finding without change remains visible, never silently deleted',()=>{const f=finding();const r=gate.review(request({baseFindings:[f],headFindings:[f],changedPaths:[],affectedPaths:[]}));assert.equal(r.findings[0].classification,'EXISTING');assert.equal(r.findings.length,1);});
test('1: increased severity is exacerbated, not baseline-muted',()=>{const base=finding({severity:'LOW'});const r=gate.review(request({baseFindings:[base],headFindings:[finding()]}));assert.equal(r.findings[0].classification,'EXACERBATED');assert.equal(r.status,'REVIEW_REQUIRED');});
test('1: missing baseline evidence is fail closed, even with zero findings',()=>{const r=gate.review(request({baseComplete:false,headFindings:[]}));assert.equal(r.status,'INCOMPLETE');});
test('2: critical, BLOCKER, HIGH never downgraded',()=>{for(const name of ['CRITICAL','BLOCKER','HIGH'])assert.equal(gate.classifySeverity(name),'BLOCK');});
test('2: major and warning advisory, info and minor telemetry',()=>{for(const name of ['MAJOR','WARNING','MEDIUM'])assert.equal(gate.classifySeverity(name),'ADVISORY');for(const name of ['INFO','MINOR','LOW'])assert.equal(gate.classifySeverity(name),'INFO');});
test('2: unknown severity cannot become a clean result',()=>{const r=gate.review(request({headFindings:[finding({severity:'HUGELY_BAD'})]}));assert.equal(r.status,'INCOMPLETE');});
test('2: incomplete evidence takes precedence over empty and advisory results',()=>{const r=gate.review(request({headComplete:false,headFindings:[finding({severity:'INFO'})]}));assert.equal(r.status,'INCOMPLETE');});
test('2: severity must be an exact primitive string and cannot be spoofed by coercion',()=>{const spoof={toString:()=>'HIGH'};assert.equal(gate.classifySeverity(spoof),'UNKNOWN');assert.equal(gate.review(request({headFindings:[finding({severity:spoof})]})).status,'INCOMPLETE');assert.equal(gate.review(request({headFindings:[finding({severity:new String('HIGH')})]})).status,'INCOMPLETE');});
test('2: duplicate identity keeps the highest severity regardless of input order',()=>{for(const pair of [[finding({severity:'INFO'}),finding({severity:'HIGH'})],[finding({severity:'HIGH'}),finding({severity:'INFO'})]]){const r=gate.review(request({headFindings:pair}));assert.equal(r.status,'REVIEW_REQUIRED');assert.equal(r.findings.length,1);assert.equal(r.findings[0].severity,'HIGH');assert.equal(r.findings[0].severityClass,'BLOCK');}});
test('3: distinct causal families with same rule must remain distinct',()=>{const r=gate.review(request({headFindings:[finding({cause:'alpha'}),finding({cause:'beta',line:50})]}));assert.equal(r.families.length,2);});
test('3: same causal family groups variants but retains findings',()=>{const r=gate.review(request({headFindings:[finding({symbol:'methodA'}),finding({symbol:'methodB',line:40})]}));assert.equal(r.families.length,1);assert.equal(r.families[0].variants,2);assert.equal(r.findings.length,2);});
test('3: identical source expressions in two distinct locations are both reported',()=>{const r=gate.review(request({headFindings:[finding({line:10}),finding({line:11})]}));assert.equal(r.findings.length,2);assert.equal(r.families[0].variants,2);});
test('3: repeated identical alert deduplicates without multiplying annotations',()=>{const r=gate.review(request({headFindings:[finding(),finding(),finding()]}));assert.equal(r.findings.length,1);assert.equal(r.counts.duplicates,2);});
test('3: source snippets and secrets are not copied into annotation or SARIF text',()=>{const r=gate.review(request({headFindings:[finding({reason:'Bearer ghp_abcd1234567890 TOKEN=my-password secret exposed',context:'xoxb-1234567890',cause:'unsafe-auth'})]}));const dump=JSON.stringify({annotations:r.annotations,sarif:r.sarif});assert.doesNotMatch(dump,/my-password|ghp_abcd1234567890|xoxb-1234567890/);});
test('3: bounded annotations and SARIF partialFingerprints',()=>{const arr=Array.from({length:80},(_,i)=>finding({symbol:'fn'+i,line:i+1}));const r=gate.review(request({headFindings:arr}));assert.equal(r.annotations.length,50);assert.equal(r.sarif.runs[0].results.length,50);assert.equal(r.reportTruncated,true);assert.match(r.sarif.runs[0].results[0].partialFingerprints['ultraSentinel/v1'],/^[0-9a-f]{64}$/);});
test('3: invalid path and path traversal fail closed',()=>{for(const p of ['../secrets.txt','/etc/passwd','docs/../../x','a\b'])assert.equal(gate.review(request({headFindings:[finding({path:p})]})).status,'INCOMPLETE');});
test('3: input caps fail closed, not truncate findings to get PASS',()=>{const r=gate.review(request({headFindings:Array.from({length:5001},(_,i)=>finding({line:i+1}))}));assert.equal(r.status,'INCOMPLETE');});
test('4: all expected same-SHA trusted CI permits advisory outcome, not auto-merge',()=>{const r=gate.review(request({headFindings:[finding({severity:'WARNING'})]}));assert.equal(r.status,'ADVISORY');assert.equal(r.autoMergeAllowed,false);assert.equal(r.mode,'shadow');});
test('4: zero findings and all evidence can be candidate PASS but never auto-merge',()=>{const r=gate.review(request({headFindings:[]}));assert.equal(r.status,'PASS');assert.equal(r.autoMergeAllowed,false);});
test('4: wrong SHA for one CI gate is incomplete, not pass',()=>{const c=checks();c[0].sha=B;assert.equal(gate.review(request({checks:c,headFindings:[]})).status,'INCOMPLETE');});
test('4: skipped, pending, API failure or missing required gate cannot yield PASS',()=>{for(const c of [{status:'completed',conclusion:'skipped'},{status:'in_progress',conclusion:null},{status:'completed',conclusion:'success',source:'untrusted'}]){const x=checks();x[1]={...x[1],...c};assert.equal(gate.review(request({checks:x,headFindings:[]})).status,'INCOMPLETE');}assert.equal(gate.review(request({checks:checks().slice(1),headFindings:[]})).status,'INCOMPLETE');});
test('4: verified failing required CI blocks',()=>{const c=checks();c[1].conclusion='failure';assert.equal(gate.review(request({checks:c,headFindings:[]})).status,'BLOCKED');});
test('4: an empty required check inventory cannot certify code',()=>{assert.equal(gate.review(request({requiredChecks:[],checks:[],headFindings:[]})).status,'INCOMPLETE');});
test('5: altered or conflicting rename metadata fails closed',()=>{const renames=[{status:'renamed',previous_filename:'app/a.kt',filename:'app/b.kt'},{status:'renamed',previous_filename:'app/c.kt',filename:'app/b.kt'}];assert.equal(gate.review(request({renames})).status,'INCOMPLETE');});
test('5: hostile enormous finding strings fail closed without leaks',()=>{const r=gate.review(request({headFindings:[finding({context:'A'.repeat(100000)})]}));assert.equal(r.status,'INCOMPLETE');assert.equal(JSON.stringify(r).includes('A'.repeat(100)),false);});
test('5: reordering inputs does not change family IDs or overall verdict',()=>{const a=finding({cause:'one'}),b=finding({cause:'two'});const r1=gate.review(request({headFindings:[a,b]})),r2=gate.review(request({headFindings:[b,a]}));assert.deepEqual(r1.families.map(x=>x.id),r2.families.map(x=>x.id));assert.equal(r1.status,r2.status);});
test('5: duplicate external checks cannot create false evidence',()=>{const c=checks();c.push({...c[0]});assert.equal(gate.review(request({checks:c,headFindings:[]})).status,'INCOMPLETE');});
test('5: shadow comparison notes divergences without granting merge authority',()=>{const r=gate.review(request({headFindings:[],legacyVerdict:'BLOCKED'}));assert.equal(r.shadowComparison,'DIVERGENCE');assert.equal(r.autoMergeAllowed,false);});

test('trusted AST self-review can attest whole intelligent gate module (not only synthetic unit cases)',()=>{
 const fs=require('node:fs');
 const path=require('node:path');
 const acorn=require('acorn');
 const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
 const source=fs.readFileSync(path.join(__dirname,'ultra_sentinel_intelligent_gate.cjs'),'utf8');
 const ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:'script',locations:true,ranges:true});
 assert.deepEqual(findCapabilities(ast),[]);
});

test('3: family severity retains strongest variant regardless of fingerprint order',()=>{
 const variants=[finding({symbol:'weak',severity:'INFO'}),finding({symbol:'warn',line:20,severity:'WARNING'}),finding({symbol:'critical',line:30,severity:'HIGH'})];
 for(const ordered of [variants,[...variants].reverse()]){
  const r=gate.review(request({headFindings:ordered}));
  assert.equal(r.status,'REVIEW_REQUIRED');
  assert.equal(r.families.length,1);
  assert.equal(r.families[0].variants,3);
  assert.equal(r.families[0].severityClass,'BLOCK');
 }
});
test('3: advisory family cannot be downgraded by informational variant',()=>{
 const variants=[finding({symbol:'warn',severity:'WARNING'}),finding({symbol:'info',line:20,severity:'INFO'})];
 for(const ordered of [variants,[...variants].reverse()]){
  const r=gate.review(request({headFindings:ordered}));
  assert.equal(r.families.length,1);
  assert.equal(r.families[0].severityClass,'ADVISORY');
 }
});

test('3: identical-location severity deduplication retains HIGH across all input permutations',()=>{
 const levels=['INFO','WARNING','HIGH'];
 for(const a of levels)for(const b of levels)for(const c of levels){
  const r=gate.review(request({headFindings:[finding({severity:a}),finding({severity:b}),finding({severity:c})]}));
  const highest=levels.filter(x=>[a,b,c].includes(x)).reverse()[0];
  assert.equal(r.findings.length,1);
  assert.equal(r.counts.duplicates,2);
  assert.equal(r.findings[0].severity,highest);
  assert.equal(r.families[0].severityClass,gate.classifySeverity(highest));
  assert.equal(r.status,highest==='HIGH'?'REVIEW_REQUIRED':highest==='WARNING'?'ADVISORY':'PASS');
 }
});

test('4: all required checks must match immutable HEAD, including case differences',()=>{
 const c=checks();c[2].sha=H.toUpperCase();
 assert.equal(gate.review(request({checks:c,headFindings:[]})).status,'PASS');
 c[2].sha=B;
 assert.equal(gate.review(request({checks:c,headFindings:[]})).status,'INCOMPLETE');
});
test('4: CI cannot certify an unknown conclusion, missing source, or forged success',()=>{
 for(const patch of [{conclusion:'unexpected'},{source:'untrusted'},{source:undefined},{sha:'not-a-sha'},{status:'pending',conclusion:'success'}]){
  const c=checks();c[0]={...c[0],...patch};
  assert.notEqual(gate.review(request({checks:c,headFindings:[]})).status,'PASS');
 }
});
test('4: duplicate required gate names or duplicate external evidence fail closed',()=>{
 const c=checks();
 assert.equal(gate.review(request({requiredChecks:[c[0].name,c[0].name],headFindings:[]})).status,'INCOMPLETE');
 assert.equal(gate.review(request({checks:[...c,{...c[0],source:'untrusted'}],headFindings:[]})).status,'INCOMPLETE');
});
test('4: a verified failed required check blocks even if no findings exist',()=>{
 for(const conclusion of ['failure','timed_out','cancelled','action_required']){
  const c=checks();c[3].conclusion=conclusion;
  assert.equal(gate.review(request({checks:c,headFindings:[]})).status,'BLOCKED');
 }
});
test('4: CI decisions never grant automatic merge, even for a passing HEAD',()=>{
 for(const headFindings of [[],[finding({severity:'INFO'})],[finding({severity:'HIGH'})]]){
  const r=gate.review(request({headFindings}));
  assert.equal(r.mode,'shadow');
  assert.equal(r.autoMergeAllowed,false);
 }
});

test('5: deterministic adversarial permutations preserve status, families, and severity',()=>{
 const variants=[finding({symbol:'critical',line:10,severity:'HIGH'}),finding({symbol:'warning',line:20,severity:'WARNING'}),finding({symbol:'info',line:30,severity:'INFO'})];
 const orders=[[0,1,2],[0,2,1],[1,0,2],[1,2,0],[2,0,1],[2,1,0]];
 const reference=gate.review(request({headFindings:variants}));
 for(const order of orders){
  const r=gate.review(request({headFindings:order.map(i=>variants[i])}));
  assert.equal(r.status,'REVIEW_REQUIRED');
  assert.deepEqual(r.families,reference.families);
  assert.deepEqual(r.findings,reference.findings);
  assert.deepEqual(r.sarif,reference.sarif);
  assert.equal(r.autoMergeAllowed,false);
 }
});
test('5: missing or stale CI never passes across advisory, critical, and empty findings',()=>{
 for(const headFindings of [[],[finding({severity:'WARNING'})],[finding({severity:'HIGH'})]]){
  for(const checksVariant of [[],checks().map((c,i)=>i===0?{...c,sha:B}:c)]){
   const r=gate.review(request({headFindings,checks:checksVariant}));
   assert.equal(r.status,'INCOMPLETE');
   assert.equal(r.autoMergeAllowed,false);
  }
 }
});
test('5: shadow comparison reports agreement and divergence without changing verdict',()=>{
 for(const headFindings of [[],[finding({severity:'WARNING'})],[finding({severity:'HIGH'})]]){
  const original=gate.review(request({headFindings}));
  const agreed=gate.review(request({headFindings,legacyVerdict:original.status}));
  const divergent=gate.review(request({headFindings,legacyVerdict:'BLOCKED'}));
  assert.equal(agreed.shadowComparison,'AGREES');
  assert.equal(divergent.shadowComparison,'DIVERGENCE');
  assert.equal(agreed.status,original.status);
  assert.equal(divergent.status,original.status);
  assert.equal(divergent.autoMergeAllowed,false);
 }
});
test('5: adversarial report caps do not erase critical findings or merge protections',()=>{
 const arr=Array.from({length:120},(_,i)=>finding({symbol:'variant'+i,line:i+1,severity:i===119?'HIGH':'INFO'}));
 const r=gate.review(request({headFindings:arr}));
 assert.equal(r.status,'REVIEW_REQUIRED');
 assert.equal(r.findings.length,120);
 assert.equal(r.annotations.length,50);
 assert.equal(r.sarif.runs[0].results.length,50);
 assert.equal(r.reportTruncated,true);
 assert.equal(r.families[0].severityClass,'BLOCK');
 assert.equal(r.autoMergeAllowed,false);
});
