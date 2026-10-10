'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {scan,parseAdded}=require('./ultra_sentinel_selfreview.cjs');
const SHA='a'.repeat(40);
const wf='.github/workflows/ultra-sentinel-security.yml';
const src='.github/scripts/ultra_sentinel_core.cjs';
const patch=(...text)=>'@@ -1,0 +1,'+text.length+' @@\n'+text.map(line=>'+'+line).join('\n');
const file=(name,...lines)=>({filename:name,patch:patch(...lines),changes:lines.length,status:'modified'});
test('read-only benign diff remains advisory, never an approval',()=>{
 const result=scan([file(wf,'name: safe','contents: read')],SHA);
 assert.equal(result.status,'ADVISORY');assert.equal(result.autoMergeAllowed,false);assert.equal(result.requiresHuman,true);
});
test('privileged checkout of attacker PR head is blocked',()=>{
 for(const line of ['ref: $'+'{{ github.event.pull_request.head.sha }}','ref: $'+'{{ github.head_ref }}','ref: $'+'{{ github.event.workflow_run.head_sha }}'])
  assert.equal(scan([file(wf,line)],SHA).status,'BLOCKED');
});
test('new write capability in reviewer workflow is blocked',()=>{
 for(const line of ['permissions: write-all','contents: write','actions: write'])
  assert.equal(scan([file(wf,line)],SHA).status,'BLOCKED');
});
test('unpinned action is blocked, SHA-pinned action is permitted',()=>{
 assert.equal(scan([file(wf,'uses: actions/checkout@v6')],SHA).status,'BLOCKED');
 assert.equal(scan([file(wf,'uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803')],SHA).status,'ADVISORY');
});
test('dynamic JS execution is blocked',()=>{
 for(const line of [
  'eval(userPatch)','new Function(userPatch)','vm.runInNewContext(userPatch)',
  'const doc="hello"; eval(userPatch)',
  'const doc="safe"; new Function(userPatch)'
 ])assert.equal(scan([file(src,line)],SHA).status,'BLOCKED',line);
 // The red-team fixtures are inert strings: only executable JS is dangerous.
 // Inert fixtures live in the unprivileged test-only module, not reviewer production code.
 const fixture='.github/scripts/ultra_sentinel_gauntlet_extreme_data.test.cjs';
 for(const line of [
  'const text="eval(userPatch)"',
  "const text='new Function(userPatch)'",
  'const text="done"; // eval(userPatch)',
  'const text="done"; /* eval(userPatch) */ const ok=1'
 ])assert.equal(scan([file(fixture,line)],SHA).status,'ADVISORY',line);
 // Codex: an executable computed globalThis call cannot be certified safe
 // merely because template interpolation splits the dangerous identifier.
 for(const line of [
  "globalThis[`ev${'al'}`](userPatch)",
  "new globalThis[`Fun${'ction'}`](userPatch)",
  'vm /* trivia */ . runInThisContext(userPatch)',
  'vm /* trivia */ . runInNewContext(userPatch)'
 ])assert.equal(scan([file(src,line)],SHA).status,'BLOCKED',line);
 // Comment contents and ordinary string literals are not executable sinks.
 for(const lines of [
  ['/*','eval(userPatch)','*/','const a=1;'],
  ['const a=1; /*','new Function(userPatch)','*/ const b=2;'],
  ['const description="eval(userPatch)";'],
  ['// vm.runInThisContext(userPatch)'],
  ['const x="safe"; /* eval(userPatch) */']
 ])assert.equal(scan([file(src,...lines)],SHA).status,'ADVISORY',JSON.stringify(lines));
 // Closing a multiline comment must not mask subsequent active JavaScript.
 assert.equal(scan([file(src,'/*','inert description','*/','eval(userPatch)')],SHA).status,'BLOCKED');
 // A slash in a regex character class is not a JS line comment.
 assert.equal(scan([file(src,'const slash=/[//]/; eval(userPatch)')],SHA).status,'BLOCKED');
 // JavaScript tokens remain adjacent across multiline block-comment trivia.
 assert.equal(scan([file(src,'vm /* trivia','*/ . runInThisContext(userPatch)')],SHA).status,'BLOCKED');
 for(const line of [
  'const s=`eval(userPatch) '+ '$' + '{name}`;',
  'const s=`'+ '$' + '{"eval(userPatch)"}`;',
  'const s=`text '+ '$' + '{ordinaryIdentifier}`;'
 ])assert.equal(scan([file(src,line)],SHA).status,'ADVISORY',line);
 // Hunk starts inside a previously existing comment: full-source context
 // must suppress false confirmed DYNAMIC_EVAL. Missing context fails closed.
 const earlier=['/*',...Array.from({length:98},()=>'* inert')];
 const fullSource=[...earlier,'eval(userPatch)','*/','const ok=true;'].join('\n');
 const midHunk={filename:src,status:'modified',changes:1,
  patch:'@@ -100,0 +100,1 @@\n+eval(userPatch)',fullSource};
 assert.equal(scan([midHunk],SHA).status,'ADVISORY');
 const missing=scan([{...midHunk,fullSource:undefined}],SHA);
 assert.equal(missing.status,'BLOCKED');
 assert.ok(missing.findings.some(x=>x.rule==='JS_CONTEXT_INCOMPLETE'));
 assert.ok(!missing.findings.some(x=>x.rule==='DYNAMIC_EVAL'));
});
test('autocommit and automerge authorization in changed reviewer code are blocked',()=>{
 for(const line of ['autoMergeAllowed:true','autoCommitAllowed:true'])
  assert.equal(scan([file(src,line)],SHA).status,'BLOCKED');
});
test('incomplete diff always blocks instead of approving',()=>{
 assert.equal(scan([{filename:src,changes:2,status:'modified'}],SHA).status,'BLOCKED');
 assert.equal(scan([file(src,'x'.repeat(150100))],SHA).status,'BLOCKED');
});
test('deleting reviewer guard fails and changes require human validation',()=>{
 const removed=scan([{filename:src,status:'removed'}],SHA);
 assert.equal(removed.status,'BLOCKED');
 assert.ok(removed.findings.some(f=>f.rule==='REVIEWER_CHANGED'));
 const changed=scan([file('.github/scripts/ultra_sentinel_selfreview.cjs','const improvement=1;')],SHA);
 assert.equal(changed.status,'ADVISORY');
 assert.ok(changed.findings.some(f=>f.rule==='REVIEWER_CHANGED'));
});
test('app changes outside self-review scope are not falsely certified',()=>{
 const r=scan([file('app/src/main/java/Foo.kt','eval("x")')],SHA);
 assert.equal(r.status,'ADVISORY');assert.equal(r.filesReviewed,0);
});
test('invalid head and oversized PR fail closed',()=>{
 assert.equal(scan([],null).status,'BLOCKED');
 assert.equal(scan(Array.from({length:251},()=>file(src,'ok')),SHA).status,'BLOCKED');
});
test('parser treats shell and HTML in patch as inert text',()=>{
 const entries=parseAdded(patch('$(touch /tmp/unsafe)', '<script>alert(1)</script>'));
 assert.equal(entries.length,2);
 assert.equal(entries[0].text,'$(touch /tmp/unsafe)');
});

test('red team fixtures are not treated as live auto-merge permissions',()=>{
 const mutation=file('.github/scripts/ultra_sentinel_mutation.cjs',"['orchestrator','automerge','autoMergeAllowed:false','autoMergeAllowed:true']");
 assert.equal(scan([mutation],SHA).status,'ADVISORY');
 const fixture=file('.github/scripts/ultra_sentinel_selfreview.test.cjs',"assert.equal(scan([file(src,'new Function(userPatch)')],SHA).status,'BLOCKED')");
 assert.equal(scan([fixture],SHA).status,'ADVISORY');
});

test('required guard renamed outside scope must be blocked',()=>{
 const r=scan([{filename:'docs/renamed.txt',previous_filename:'.github/scripts/ultra_sentinel_selfreview.cjs',
  status:'renamed',changes:1,patch:patch('hello')}],SHA);
 assert.equal(r.status,'BLOCKED');
 assert.ok(r.findings.some(x=>x.rule==='REMOVED_GATE'));
});
test('other Sentinel source cannot escape scope via rename',()=>{
 const r=scan([{filename:'docs/escaped.txt',previous_filename:'.github/scripts/ultra_sentinel_memory.cjs',
  status:'renamed',changes:1,patch:patch('hello')}],SHA);
 assert.equal(r.status,'BLOCKED');
 assert.ok(r.findings.some(x=>x.rule==='REVIEWER_SCOPE_ESCAPED'));
});
test('YAML list syntax quotes and comments cannot bypass security rules',()=>{
 for(const line of ['- uses: actions/checkout@v4','- uses: "actions/checkout@v4" # mutable',
  "uses: 'actions/checkout@v4'",'contents: write # elevated',
  "contents: 'write' # elevated",'actions: "write" # elevated']){
  assert.equal(scan([file(wf,line)],SHA).status,'BLOCKED',line);
 }
 assert.equal(scan([file(wf,'- uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # pinned')],SHA).status,'ADVISORY');
});

test('rejects nested action refs and every non-SHA remote uses reference',()=>{
 for(const action of [
  'github/codeql-action/init@v3','actions/checkout@dev',
  'actions/checkout@release','actions/checkout@v4-beta',
  'vendor/action/subpath@branch','actions/checkout','$'+'{{ inputs.action }}'
 ]){
  for(const line of ['uses: '+action,'- uses: "'+action+'" # mutable']){
   const result=scan([file(wf,line)],SHA);
   assert.equal(result.status,'BLOCKED',line);
   assert.ok(result.findings.some(x=>x.rule==='MUTABLE_ACTION'),line);
  }
 }
});
test('accepts SHA-pinned remote action subpaths and local/docker uses',()=>{
 for(const action of [
  'actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803',
  'github/codeql-action/init@d23441a48e516b6c34aea4fa41551a30e30af803',
  './.github/actions/custom','docker://alpine:3.20'
 ]){
  assert.equal(scan([file(wf,'- uses: "'+action+'" # safe')],SHA).status,'ADVISORY',action);
 }
});
