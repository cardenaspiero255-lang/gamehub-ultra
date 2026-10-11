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
 // Codex P1: arrow function can precede a regex containing two slashes.
 assert.equal(scan([file(src,'const slash = () => /[//]/; eval(userPatch)')],SHA).status,'BLOCKED','arrow regex bypass');
 // Regex braces inside template interpolation do not close the ${expression}.
 const interpolation='const s = `'+'$'+'{x / /}/.test(y) ? 1 : eval(userPatch)}`;';
 assert.equal(scan([file(src,interpolation)],SHA).status,'BLOCKED','interpolation regex brace bypass');
 // 760 comment-only lines must not erase the significant vm token.
 const longComment=['vm /*',...Array.from({length:760},(_,i)=>' harmless comment '+i),'*/ . runInThisContext(userPatch)'];
 const prolonged=scan([file(src,...longComment)],SHA);
 assert.equal(prolonged.status,'BLOCKED','very long multiline comment bypass');
 assert.ok(prolonged.findings.some(x=>x.rule==='DYNAMIC_EVAL'));
 // Codex next P1 round: return used as a property is not a keyword.
 for(const script of [
  'obj.return / eval(userPatch) / divisor;',
  'const s=`'+'$'+'{obj.return / eval(userPatch) / divisor}`;'
 ])assert.equal(scan([file(src,script)],SHA).status,'BLOCKED',script);
 // A regex can also be the immediate body of a control statement.
 for(const script of [
  'if (enabled) /[//]/; eval(userPatch)',
  'while (enabled) /[//]/; eval(userPatch)',
  'for (; enabled ;) /[//]/; eval(userPatch)',
  'with (obj) /[//]/; eval(userPatch)',
  'do /[//]/; while (false); eval(userPatch)'
 ])assert.equal(scan([file(src,script)],SHA).status,'BLOCKED',script);
 assert.equal(scan([file(src,'const q=obj.return / size / divisor')],SHA).status,'ADVISORY');
 assert.equal(scan([file(src,'if (enabled) /[//]/; const ok=true;')],SHA).status,'ADVISORY');
 // Equivalent inoperative comments, string literals and regex expressions stay advisory.
 assert.equal(scan([file(src,'const slash = () => /[//]/; const ok = true')],SHA).status,'ADVISORY');
 assert.equal(scan([file(src,'const s = `'+'$'+'{x / /}/.test(y) ? 1 : 2}`;')],SHA).status,'ADVISORY');
 // Structural regression family (RED): JavaScript must be parsed, not guessed.
 for(const code of [
  'const result=numerator\n/ eval(userPatch) / divisor;',
  'let x=4; x++ / eval(userPatch) / divisor;',
  'let x=4; x-- / eval(userPatch) / divisor;',
  'class C { #return=2; m(){ return this.#return / eval(userPatch) / divisor; }} new C().m();',
  'async function f(xs){ for await (const x of xs) /[//]/; eval(userPatch) }',
  "globalThis['eval'](userPatch)",
  "global['eval'](userPatch)",
  "new globalThis['Function'](userPatch)",
  "globalThis['ev'+'al'](userPatch)",
  "globalThis['ev\\x61l'](userPatch)",
  "const hidden=globalThis['eval']; hidden(userPatch)",
  "eval?.(userPatch)",
  "(0,eval)(userPatch)",
  "const renderer=\`Hello \${eval(userPatch)}\`;",
  "require('node:vm').runInThisContext(userPatch)"
 ]) {
  const result=scan([file(src,...code.split('\n'))],SHA);
  assert.equal(result.status,'BLOCKED',code);
  assert.ok(result.findings.some(f=>f.rule==='DYNAMIC_EVAL'),code);
 }
 for(const code of [
  "const x=numerator / divisor;",
  "let x=4; x++ / size / divisor;",
  "class C { #return=2; m(){ return this.#return / factor; }}",
  "async function f(xs){ for await (const x of xs) /[//]/; }",
  "const text=\`eval(userPatch)\`;",
  "// globalThis['eval'](userPatch)",
  "const description=\"globalThis['eval'](userPatch)\";",
  "const rx=/[//]/; const x='eval(userPatch)';",
  "const value={eval:()=>123}; value.eval()"
 ]) assert.equal(scan([file(src,...code.split('\n'))],SHA).status,'ADVISORY',code);
 const malformed=scan([file(src,'const x = (eval(userPatch)')],SHA);
 assert.equal(malformed.status,'BLOCKED','partial syntax must never be certified safe');
 assert.ok(malformed.findings.some(f=>f.rule==='JS_CONTEXT_INCOMPLETE'));
 // Codex family: aliases preserve dynamic execution capabilities.
 for(const code of [
  'const {eval:e}=globalThis; e(userPatch)',
  'const g=globalThis; g.eval(userPatch)',
  "const g=global; g['eval'](userPatch)",
  "const {Function: F}=globalThis; new F(userPatch)",
  "const {runInThisContext:r}=require('node:vm'); r(userPatch)",
  "const {runInNewContext:r}=require('vm'); r(userPatch)",
  "const {compileFunction:c}=require('node:vm'); c(userPatch)",
  "require('node:vm').compileFunction(userPatch)()",
  "vm.compileFunction(userPatch)()",
  "vm['compileFunction'](userPatch)()",
  "const v=require('node:vm'); const exec=v['compileFunction']; exec(userPatch)",
  "const first=globalThis; const second=first; second['eval'](userPatch)",
  "let take; take=globalThis['eval']; take(userPatch)",
  "Reflect.get(globalThis,'eval')(userPatch)"
 ]) {
  const r=scan([file(src,...code.split('\n'))],SHA);
  assert.equal(r.status,'BLOCKED',code);
  assert.ok(r.findings.some(f=>f.rule==='DYNAMIC_EVAL'),code);
 }
 for(const code of [
  'const g=globalThis; g.console.log(42)',
  "const {eval: e}={eval:(x)=>x}; e(42)",
  "const {compileFunction:c}={compileFunction:(x)=>x}; c(42)",
  "const tool={compileFunction:x=>x}; tool.compileFunction(42)"
 ]) assert.equal(scan([file(src,code)],SHA).status,'ADVISORY',code);
 // Codex RED: capability provenance through arguments, iterables and loaders.
 for(const code of [
  'function invoke({eval:e}) { e(userPatch) } invoke(globalThis)',
  "function invoke({runInThisContext:r}) { r(userPatch) } invoke(require('node:vm'))",
  'for (const {eval:e} of [globalThis]) e(userPatch)',
  "for (const {runInThisContext:r} of [require('vm')]) r(userPatch)",
  "module.require('node:vm').compileFunction(userPatch)()",
  "module['require']('vm')['runInThisContext'](userPatch)",
  "const req=require; const {runInThisContext:r}=req('vm'); r(userPatch)",
  "const R=Reflect; R.get(globalThis,'eval')(userPatch)",
  "const {get}=Reflect; get(globalThis,'eval')(userPatch)",
  "const R=Reflect; const Next=R; Next.get(globalThis,'eval')(userPatch)",
  "const req=module.require; req('vm').compileFunction(userPatch)()"
 ]) {
  const result=scan([file(src,...code.split('\n'))],SHA);
  assert.equal(result.status,'BLOCKED',code);
  assert.ok(result.findings.some(x=>x.rule==='DYNAMIC_EVAL'),code);
 }
 // Scope-engine diagnostics must never silently turn a known sink into INCOMPLETE.
 const scopeProbe=require('acorn').parse(
   'function invoke({eval:e}) { e(userPatch) } invoke(globalThis)',
   {ecmaVersion:'latest',sourceType:'script',locations:true,ranges:true});
 const capabilityNodes=require('./ultra_sentinel_capabilities.cjs').findCapabilities(scopeProbe);
 assert.ok(capabilityNodes.some(n=>n.type==='Identifier'&&n.name==='e'));
 // Stabilization RED: VM factories, object property flow and true lexical scope.
 // Each pair is evaluated as a complete source file, never executed.
 const executableVariants=[
  "require('node:vm').createScript(userPatch).runInThisContext()",
  "vm.createScript(userPatch).runInNewContext({})",
  "const {createScript:c}=require('vm'); c(userPatch).runInNewContext({})",
  "const v=require('node:vm'); const create=v['create'+'Script']; create(userPatch)",
  "const {x:g}={x:globalThis}; g.eval(userPatch)",
  "const {x:v}={x:require('vm')}; v.compileFunction(userPatch)()",
  "const {x:r}={x:Reflect}; r.get(globalThis,'eval')(userPatch)",
  "const box={x:globalThis}; const {x:g}=box; g.eval(userPatch)",
  "const box={x:require('vm')}; const {x:v}=box; v.compileFunction(userPatch)()",
  "const box={x:Reflect}; const {x:r}=box; r.get(globalThis,'eval')(userPatch)",
  "const box={x:globalThis}; const next=box; const {x:g}=next; g.eval(userPatch)"
 ];
 for(const code of executableVariants){
  const result=scan([file(src,...code.split('\n'))],SHA);
  assert.equal(result.status,'BLOCKED',code);
  assert.ok(result.findings.some(f=>f.rule==='DYNAMIC_EVAL'),code);
 }
 const safeShadowing=[
  "function safe(require){ return require('vm').compileFunction(userPatch) } safe(()=>({compileFunction:x=>x}))",
  "function safe(globalThis){ return globalThis.eval(userPatch) } safe({eval:x=>x})",
  "function safe(vm){ return vm.createScript(userPatch) } safe({createScript:x=>x})",
  "function safe(Reflect){ return Reflect.get(globalThis,'eval') } safe({get:()=>42})",
  "const safe={x:(n)=>n}; const {x:f}=safe; f(userPatch)",
  "const safe={x:42}; const {x:x}=safe; const y=x + 2",
  "function f(){const require=(s)=>({compileFunction:x=>x});require('vm').compileFunction(userPatch)}",
  "const vm={createScript:x=>x}; vm.createScript(userPatch)",
  "function safe(global){ return global.eval(userPatch) } safe({eval:x=>x})"
 ];
 for(const code of safeShadowing)
  assert.equal(scan([file(src,...code.split('\n'))],SHA).status,'ADVISORY',code);
 // The same dangerous operation remains detectable after harmless formatting.
 for(const gap of ['',' ','/* trivia */']){
  const code="const {x:g}={x:globalThis}; g"+gap+"['eval'](userPatch)";
  const result=scan([file(src,code)],SHA);
  assert.equal(result.status,'BLOCKED',code);
  assert.ok(result.findings.some(f=>f.rule==='DYNAMIC_EVAL'));
 }
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

test('metamorphic AST gate detects equivalent dynamic execution forms',()=>{
 const variants=[
  "const g=globalThis; g.eval(userPatch)",
  "const g=globalThis; g['eval'](userPatch)",
  "const {eval:e}=globalThis; e(userPatch)",
  "const {x:g}={x:globalThis}; g['ev'+'al'](userPatch)",
  "const box={x:globalThis}; const {x:g}=box; g.eval(userPatch)",
  "vm.createScript(userPatch).runInThisContext()",
  "const {createScript:c}=require('vm'); c(userPatch)",
  "const v=require('node:vm'); v['createScript'](userPatch)",
  "const {runInThisContext:r}=require('node:vm'); r(userPatch)",
  "const {x:v}={x:require('node:vm')}; v.compileFunction(userPatch)()",
  "const {x:r}={x:Reflect}; r.get(globalThis,'eval')(userPatch)"
 ];
 for(const body of variants)for(const [before,after] of [
  ['',''],['; ',' /* end */'],['/* pre */ ',' /* post */']
 ]){
  const script=before+body+after;
  const out=scan([file(src,script)],SHA);
  assert.equal(out.status,'BLOCKED',script);
  assert.ok(out.findings.some(f=>f.rule==='DYNAMIC_EVAL'),script);
 }
});
test('lexical shadowing does not invent executable built-in capabilities',()=>{
 const benign=[
  "function f(require){return require('vm').compileFunction(userPatch)} f(()=>({compileFunction:x=>x}))",
  "function f(globalThis){return globalThis.eval(userPatch)} f({eval:x=>x})",
  "function f(vm){return vm.createScript(userPatch)} f({createScript:x=>x})",
  "const vm={createScript:x=>x}; vm.createScript(userPatch)",
  "const box={x:globalThis}; const {x:local}=({x:{eval:x=>x}});local.eval(userPatch)",
  "const safe={x:(n)=>n};const {x:f}=safe;f(userPatch)"
 ];
 for(const script of benign){
  const result=scan([file(src,script)],SHA);
  assert.equal(result.status,'ADVISORY',script);
  assert.ok(!result.findings.some(f=>f.rule==='DYNAMIC_EVAL'),script);
 }
});
test('ambiguous JavaScript context and malformed source fail closed without fake certainty',()=>{
 const cases=[
  {filename:src,status:'modified',changes:1,patch:'@@ -92,0 +92,1 @@\n+const ok = 1;'},
  file(src,'const missing = ('),
  file(src,"const unclosed = 'bad"),
  {...file(src,'const ok = 1;'),fullSource:'q'.repeat(160001)}
 ];
 for(const item of cases){
  const result=scan([item],SHA);
  assert.equal(result.status,'BLOCKED');
  assert.ok(result.findings.some(f=>f.rule==='JS_CONTEXT_INCOMPLETE'));
  assert.ok(!result.findings.some(f=>f.rule==='DYNAMIC_EVAL'));
 }
});
test('scope analysis never turns unknown or invalid exact-SHA evidence into an approval',()=>{
 const bad=scan([{filename:src,status:'modified',changes:1,
  patch:'@@ -60,0 +60,1 @@\n+const ok = 1;',fullSource:undefined}],SHA);
 assert.equal(bad.status,'BLOCKED');
 assert.equal(bad.requiresHuman,true);
 assert.equal(bad.autoMergeAllowed,false);
 assert.ok(bad.findings.some(f=>f.rule==='JS_CONTEXT_INCOMPLETE'));
});
