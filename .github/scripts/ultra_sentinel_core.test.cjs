'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {analyze,parsePatch,markdown,VERSION}=require('./ultra_sentinel_core.cjs');
const p=(xs)=>'@@ -1,1 +1,'+xs.length+' @@\n'+xs.map(x=>'+'+x).join('\n');
const file=(path,lines)=>({filename:path,patch:p(lines),changes:lines.length});
const app='app/src/main/java/com/cardenaspiero255/gamehubultra/';
function rules(out){return out.findings.map(f=>f.rule)}
test('added line numbers follow GitHub diff ranges',()=>{
 const z=parsePatch('@@ -7,2 +43,3 @@\n old\n+GlobalScope.launch { }\n old\n+runBlocking { }\n');
 assert.deepEqual(z.added.map(x=>x.line),[44,46]);
});
test('no external API/model dependency for independent engine',()=>{
 const z=analyze([file(app+'Engine.kt',['fun safe() = 42'])],{sha:'a'.repeat(40)});
 assert.equal(z.engine,'Ultra Sentinel Core');
 assert.equal(z.sha,'a'.repeat(40));assert.equal(VERSION,'2.0.0');
});
test('recognizes unsupervised Android coroutine',()=>{
 assert.ok(rules(analyze([file(app+'Service.kt',['GlobalScope.launch { work() }'])])).includes('UNSCOPED_COROUTINE'));
});
test('recognizes main thread blocking candidate',()=>{
 assert.ok(rules(analyze([file(app+'MainActivity.kt',['Thread.sleep(1000)'])])).includes('BLOCKING_ANDROID_CALL'));
});
test('detects possible onError synchronous recursion',()=>{
 const result=analyze([file(app+'voice/Recognizer.kt',['override fun onError(error: Int) {','startListening(intent)','}'])]);
 assert.ok(rules(result).includes('SPEECH_REENTRANT_RETRY'));
 assert.equal(result.findings.find(x=>x.rule==='SPEECH_REENTRANT_RETRY').status,'NEEDS_VERIFICATION');
});
test('does not flag posted speech restart',()=>{
 const result=analyze([file(app+'voice/Recognizer.kt',['override fun onError(error: Int) {','mainHandler.post { startListening(intent) }','}'])]);
 assert.ok(!rules(result).includes('SPEECH_REENTRANT_RETRY'));
});
test('detects recursive self-call candidate',()=>{
 const result=analyze([file(app+'X.kt',['fun recursive() {','recursive()','}'])]);
 assert.ok(rules(result).includes('UNBOUNDED_RECURSION'));
});
test('does not flag guarded bounded recursion',()=>{
 const result=analyze([file(app+'X.kt',['fun recursive(depth: Int) {','if (depth > 100) return','recursive(depth + 1)','}'])]);
 assert.ok(!rules(result).includes('UNBOUNDED_RECURSION'));
});
test('detects dangerous privileged PR checkout',()=>{
 const result=analyze([file('.github/workflows/danger.yml',['pull_request_target:','  ref: ${{ github.event.pull_request.head.sha }}'])]);
 assert.ok(rules(result).includes('PRIVILEGED_UNTRUSTED_CHECKOUT'));
 assert.equal(result.findings[0].severity,'BLOCKER');
});
test('does not leak a credential in JSON or Markdown',()=>{
 const secret='abcdefghijklmnop';
 const result=analyze([file(app+'X.kt',['SENTRY_AUTH_TOKEN="'+secret+'"'])]);
 assert.ok(rules(result).includes('POTENTIAL_HARDCODED_SECRET'));
 assert.ok(!JSON.stringify(result).includes(secret));
 assert.ok(!markdown(result).includes(secret));
});
test('comments are not treated as executable code',()=>{
 const result=analyze([file(app+'X.kt',['// GlobalScope.launch { }'])]);
 assert.ok(!rules(result).includes('UNSCOPED_COROUTINE'));
});
test('documentation-only changes do not trigger Android rules',()=>{
 const result=analyze([file('README.md',['GlobalScope.launch { }'])]);
 assert.equal(result.findings.length,0);
});
test('missing PR patch fails open-data coverage',()=>{
 const result=analyze([{filename:app+'X.kt',changes:23}]);
 assert.equal(result.coverage.partial,true);assert.equal(result.verdict,'INCOMPLETE');
});
test('test gap is a low-confidence finding, not a confirmed bug',()=>{
 const result=analyze([file(app+'Example.kt',['fun foo() = 3'])]);
 assert.equal(result.findings.find(x=>x.rule==='REGRESSION_TEST_COVERAGE').confidence,'low');
});
test('modified tests avoid no-tests signal',()=>{
 const result=analyze([file(app+'Example.kt',['fun foo() = 3']),file('app/src/test/java/ExampleTest.kt',['testFoo()'])]);
 assert.ok(!rules(result).includes('REGRESSION_TEST_COVERAGE'));
});
test('output honestly states a clean-looking patch is not certified',()=>{
 const text=markdown(analyze([file('README.md',['Hello'])]));
 assert.match(text,/NO certifica/);
});

test('does not confuse another method call with direct self-recursion (CAR-51)',()=>{
 const changed=file('app/src/main/java/com/cardenaspiero255/gamehubultra/platform/RuntimeDiagnostics.kt',[
   'fun connectivity(context: Context): ConnectivityTelemetry =',
   '  runCatching { readConnectivity(context.applicationContext) }',
   '      .getOrDefault(ConnectivityTelemetry(null,false,false,true,null,null,null))',
   'fun get(context: Context): RuntimeDiagnostics {',
   '  return RuntimeDiagnostics(connectivity = connectivity(context))',
   '}'
 ]);
 const result=analyze([changed]);
 assert.ok(!rules(result).includes('UNBOUNDED_RECURSION'));
});

test('does not flag calls in a second diff hunk as self-recursion in the previous method',()=>{
 const patch=[
   '@@ -98,6 +98,9 @@',
   '+fun connectivity(context: Context): ConnectivityTelemetry =',
   '+  runCatching { readConnectivity(context.applicationContext) }',
   '+    .getOrDefault(ConnectivityTelemetry(null,false,false,true,null,null,null))',
   ' fun get(context: Context): RuntimeDiagnostics {',
   '@@ -107,8 +110,7 @@',
   ' refresh = runCatching { readRefresh(appContext) }',
   '- connectivity = readConnectivity(appContext),',
   '+ connectivity = connectivity(appContext),',
   ' storage = readStorage()'
 ].join('\n');
 const result=analyze([{filename:'app/src/main/java/com/cardenaspiero255/gamehubultra/platform/RuntimeDiagnostics.kt',patch}]);
 assert.ok(!rules(result).includes('UNBOUNDED_RECURSION'));
});

test('does not scan synthetic credentials inside .test.cjs fixtures as production',()=>{
 const z=analyze([file('.github/scripts/sample.test.cjs',['GITHUB_TOKEN="abcdefghijklmnop"'])]);
 assert.ok(!rules(z).includes('POTENTIAL_HARDCODED_SECRET'));
});

test('ignores the labeled benchmark fixture as executable production secrets',()=>{
 const z=analyze([file('.github/scripts/ultra_sentinel_benchmark.cjs',['SENTRY_AUTH_TOKEN="abcdefghijklmnop"'])]);
 assert.ok(!rules(z).includes('POTENTIAL_HARDCODED_SECRET'));
});

test('empty changed-file list must mark audit incomplete',()=>{
 const result=analyze([]);
 assert.equal(result.coverage.partial,true);
 assert.equal(result.verdict,'INCOMPLETE');
});

test('P2: an ambiguous raw-string closer before a diff addition cannot hide executable Kotlin',()=>{
 // The opening triple quote may be before this hunk. Its first visible
 // delimiter can be a closer; accepting a clean verdict is unsafe.
 const patch=['@@ -40,3 +40,4 @@','     """','     old text','+    runBlocking { work() }','     after()'].join('\n');
 const result=analyze([{filename:app+'MainActivity.kt',patch,changes:1}]);
 assert.equal(result.coverage.partial,true);
 assert.equal(result.verdict,'INCOMPLETE');
});
test('a raw Kotlin string opened and closed within trustworthy file-origin context does not hide later code',()=>{
 const patch=['@@ -1,4 +1,5 @@','val help = """','  sample','"""','+runBlocking { work() }','val end = 42'].join('\n');
 const result=analyze([{filename:app+'MainActivity.kt',patch,changes:1}]);
 assert.ok(rules(result).includes('BLOCKING_ANDROID_CALL'));
 assert.equal(result.coverage.partial,false);
});

test('Codex P1: added triple-quote delimiter with unknown hunk state fails closed',()=>{
 const patch=[
  '@@ -40,3 +40,4 @@',
  '-    """.trimIndent()',
  '+    """',
  '+    runBlocking { work() }',
  '     after()'
 ].join('\n');
 const result=analyze([{filename:app+'MainActivity.kt',patch,changes:3}]);
 assert.equal(result.coverage.partial,true);
 assert.equal(result.verdict,'INCOMPLETE');
});
test('added raw opening with known beginning of Kotlin file remains safely distinguishable',()=>{
 const patch=[
  '@@ -1,4 +1,6 @@',
  'fun sample() {',
  '+val description = """',
  '+runBlocking { textOnly() }',
  '+"""',
  '+runBlocking { executable() }',
  '}' 
 ].join('\n');
 const result=analyze([{filename:app+'MainActivity.kt',patch,changes:4}]);
 assert.equal(result.coverage.partial,false);
 assert.equal(result.findings.filter(f=>f.rule==='BLOCKING_ANDROID_CALL').length,1);
});

test('Codex P2: nested Kotlin block comment does not trigger a blocking-call finding',()=>{
 const result=analyze([file(app+'Nested.kt',[
 '/* outer documentation',
 '/* nested inner */',
 'runBlocking { example() }',
 '*/',
 'val safe = 1'
 ])]);
 assert.ok(!rules(result).includes('BLOCKING_ANDROID_CALL'));
});
test('nested Kotlin comments close only at outer end and then executable calls are visible',()=>{
 const result=analyze([file(app+'Nested.kt',[
 '/* outer */',
 '/* second outer',
 '/* nested */',
 'runBlocking { commentOnly() }',
 '*/',
 'runBlocking { actualCall() }'
 ])]);
 const blocking=result.findings.filter(x=>x.rule==='BLOCKING_ANDROID_CALL');
 assert.equal(blocking.length,1);
 assert.equal(blocking[0].line,6);
});
test('single-line nested Kotlin block comments are not executable',()=>{
 const result=analyze([file(app+'Nested.kt',[
 '/* outer /* nested */ runBlocking { hidden() } */',
 'val safe = 1'
 ])]);
 assert.ok(!rules(result).includes('BLOCKING_ANDROID_CALL'));
});

test('Java block comments do not nest: executable call after first closer stays visible',()=>{
 const java=analyze([file(app+'Legacy.java',[
  '/* outer /* inner */',
  'Thread.sleep(1000);'
 ])]);
 assert.ok(rules(java).includes('BLOCKING_ANDROID_CALL'));
 const kotlin=analyze([file(app+'Modern.kt',[
  '/* outer /* inner */',
  'Thread.sleep(1000);',
  '*/'
 ])]);
 assert.ok(!rules(kotlin).includes('BLOCKING_ANDROID_CALL'));
});

test('ADVERSARIAL P2: Java ordinary strings containing Kotlin-looking template expressions are inert',()=>{
 const out=analyze([file(app+'Literal.java',[
  'String example = "${runBlocking { work() }}";',
  'String another = "${Thread.sleep(99)}";',
  'String tertiary = "${System.gc()}";'
 ])]);
 assert.ok(!rules(out).includes('BLOCKING_ANDROID_CALL'),JSON.stringify(out.findings));
 assert.ok(!rules(out).includes('FORCED_GC'));
});
test('ADVERSARIAL P2: Java multiline text blocks are not Kotlin interpolation',()=>{
 const out=analyze([file(app+'Literal.java',[
  'String example = """',
  '  ${runBlocking { work() }}',
  '  ${Thread.sleep(1000)}',
  '""";',
  'int answer = 42;'
 ])]);
 assert.ok(!rules(out).includes('BLOCKING_ANDROID_CALL'),JSON.stringify(out.findings));
 assert.equal(out.coverage.partial,false);
});
test('ADVERSARIAL: Kotlin regular string interpolation retains dangerous call detection',()=>{
 const out=analyze([file(app+'Literal.kt',[
  'val result = "${runBlocking { work() }}"'
 ])]);
 assert.ok(rules(out).includes('BLOCKING_ANDROID_CALL'));
});
test('ADVERSARIAL: Kotlin raw interpolation retains executable call detection',()=>{
 const out=analyze([file(app+'Literal.kt',[
  'val result = """${runBlocking { work() }}"""'
 ])]);
 assert.ok(rules(out).includes('BLOCKING_ANDROID_CALL'));
});
test('ADVERSARIAL: Java executable call outside a closing multiline text block is still flagged',()=>{
 const out=analyze([file(app+'Literal.java',[
  'String example = """',
  '  ${runBlocking { notCode() }}',
  '""";',
  'Thread.sleep(1500);'
 ])]);
 const f=out.findings.filter(x=>x.rule==='BLOCKING_ANDROID_CALL');
 assert.equal(f.length,1,JSON.stringify(f));
 assert.equal(f[0].line,4);
});
test('ADVERSARIAL: Java comment and text-block context cannot manufacture Kotlin templates',()=>{
 const out=analyze([file(app+'Literal.java',[
  '/* hidden ${Thread.sleep(200)} */',
  'String example = "${runBlocking { notCode() }}";',
  'int value = 2;'
 ])]);
 assert.ok(!rules(out).includes('BLOCKING_ANDROID_CALL'));
});

// Codex P2: no lexical certainty when a changed hunk begins in the middle
// of an existing file. An unchanged comment/raw-string opener may be missing.
test('Codex P2: a mid-file hunk inside a Kotlin comment cannot create an actionable finding',()=>{
 for(const statement of ['runBlocking { example() }','System.gc()','Log.i("private", transcript)']){
  const patch=['@@ -60,3 +60,4 @@',' * explanation',
   '+ '+statement,' * more explanation'].join('\n');
  const result=analyze([{filename:app+'MainActivity.kt',patch,changes:1}]);
  assert.equal(result.verdict,'INCOMPLETE',JSON.stringify(result));
  assert.equal(result.coverage.partial,true);
  assert.ok(!result.findings.some(f=>['BLOCKING_ANDROID_CALL','FORCED_GC','POTENTIAL_PRIVATE_LOG'].includes(f.rule)),
   JSON.stringify({statement,findings:result.findings}));
  assert.ok(!result.remediations?.suggestions?.some(x=>x.rule==='FORCED_GC'),JSON.stringify(result.remediations));
 }
});
test('Codex P2: disconnected Kotlin hunk cannot inherit falsely trusted lexer context',()=>{
 const patch=['@@ -1,1 +1,1 @@','val count = 1',
  '@@ -80,1 +80,2 @@',' val explanation = 1',
  '+runBlocking { example() }'].join('\n');
 const result=analyze([{filename:app+'MainActivity.kt',patch,changes:1}]);
 assert.equal(result.verdict,'INCOMPLETE',JSON.stringify(result));
 assert.ok(!result.findings.some(f=>f.rule==='BLOCKING_ANDROID_CALL'),JSON.stringify(result.findings));
});

test('P2 full source: aligned immutable Kotlin source restores exact executable detection',()=>{
 const patch=['@@ -5,2 +5,3 @@',' val message = "hello"',
  '+runBlocking { realWork() }',' val end = true'].join('\n');
 const fullSource=['val a = 1','val b = 2','val c = 3','val d = 4',
  'val message = "hello"','runBlocking { realWork() }','val end = true'].join('\n');
 const result=analyze([{filename:app+'Service.kt',patch,changes:1,fullSource}]);
 assert.equal(result.coverage.partial,false,JSON.stringify(result.warnings));
 assert.ok(rules(result).includes('BLOCKING_ANDROID_CALL'),JSON.stringify(result.findings));
});
test('P2 full source: matching patch inside multiline comment stays inert and complete',()=>{
 const patch=['@@ -5,2 +5,3 @@',' * docs',
  '+System.gc()',' * docs'].join('\n');
 const fullSource=['/*',' * heading',' * background',' * guidance',' * docs',
  'System.gc()',' * docs',' */'].join('\n');
 const result=analyze([{filename:app+'Service.kt',patch,changes:1,fullSource}]);
 assert.equal(result.coverage.partial,false,JSON.stringify(result.warnings));
 assert.ok(!rules(result).includes('FORCED_GC'),JSON.stringify(result.findings));
});
test('P2 full source: mismatch to immutable patch must be INCOMPLETE and never propose fixes',()=>{
 const patch=['@@ -1,1 +1,2 @@',' val x = 1','+System.gc()'].join('\n');
 const result=analyze([{filename:app+'Service.kt',patch,changes:1,
  fullSource:'val x = 1\nfun safe() {}'}]);
 assert.equal(result.verdict,'INCOMPLETE');
 assert.ok(!rules(result).includes('FORCED_GC'),JSON.stringify(result.findings));
 assert.ok(!result.remediations?.suggestions?.some(s=>s.rule==='FORCED_GC'));
});
