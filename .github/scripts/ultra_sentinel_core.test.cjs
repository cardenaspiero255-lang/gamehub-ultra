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

test('Large added PR file without GitHub patch is fully attested and analyzed',()=>{
 const crypto=require('node:crypto');
 const {recoverAddedPatch}=require('./ultra_sentinel_core.cjs');
 const body=('// '+'a'.repeat(128)+'\n').repeat(970)+
  'SENTRY_AUTH_TOKEN="abcdefghijklmnop"\n';
 const bytes=Buffer.from(body,'utf8');
 const lines=body.split('\n').length-1;
 const sha=crypto.createHash('sha1').update('blob '+bytes.length+'\0').update(bytes).digest('hex');
 const item={filename:'.github/scripts/giant_added.cjs',status:'added',
  additions:lines,deletions:0,changes:lines,sha,patch:null};
 const entry={type:'file',sha,size:bytes.length,encoding:'base64',content:bytes.toString('base64')};
 const patch=recoverAddedPatch(item,entry);
 assert.ok(typeof patch==='string'&&patch.length>100000&&patch.length<=320000);
 const result=analyze([{...item,patch}],{sha:'a'.repeat(40)});
 assert.equal(result.coverage.partial,false,JSON.stringify(result.warnings));
 assert.ok(rules(result).includes('POTENTIAL_HARDCODED_SECRET'),JSON.stringify(result.findings));
 assert.equal(result.findings.find(f=>f.rule==='POTENTIAL_HARDCODED_SECRET').line,lines);
});

test('Missing or tampered sources retain fail-closed incomplete coverage',()=>{
 const crypto=require('node:crypto');
 const {recoverAddedPatch}=require('./ultra_sentinel_core.cjs');
 const text='SENTRY_AUTH_TOKEN="abcdefghijklmnop"\n';
 const bytes=Buffer.from(text,'utf8');
 const sha=crypto.createHash('sha1').update('blob '+bytes.length+'\0').update(bytes).digest('hex');
 const item={filename:'.github/scripts/new_core.cjs',status:'added',sha,
  changes:1,additions:1,deletions:0,patch:null};
 const entry={type:'file',sha,size:bytes.length,encoding:'base64',content:bytes.toString('base64')};
 const variations=[
  [{...item,status:'modified'},entry],
  [{...item,sha:'0'.repeat(40)},entry],
  [{...item,additions:2,changes:2},entry],
  [item,{...entry,sha:'0'.repeat(40)}],
  [item,{...entry,content:Buffer.from('different\n').toString('base64')}],
  [item,{...entry,size:bytes.length+1}],
  [item,{...entry,encoding:'utf8'}],
  [item,null]
 ];
 for(const [file,data] of variations){
  assert.equal(recoverAddedPatch(file,data),null,
   JSON.stringify({status:file.status,sha:file.sha,entrySha:data?.sha}));
 }
 const out=analyze([item],{sha:'a'.repeat(40)});
 assert.equal(out.verdict,'INCOMPLETE');
 assert.equal(out.coverage.partial,true);
});
