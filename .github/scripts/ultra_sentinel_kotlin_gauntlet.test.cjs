'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {analyze}=require('./ultra_sentinel_core.cjs');
const SHA='a'.repeat(40);
const dir='app/src/main/java/com/cardenaspiero255/gamehubultra/stress';
const file=(line,k=0)=>({filename:dir+'/Gauntlet'+k+'.kt',status:'modified',changes:1,
 patch:'@@ -0,0 +1,1 @@\n+'+line+'\n'});
const check=line=>analyze([file(line)],{sha:SHA});
const has=(result,rule)=>result.findings.some(x=>x.rule===rule);
test('KOTLIN GAUNTLET 240 dangerous statements are surfaced for human investigation',()=>{
 const risky=[
 ['UNSCOPED_COROUTINE','fun start() { GlobalScope.launch { work() } }'],
 ['BLOCKING_ANDROID_CALL','fun a() { Thread.sleep(1000) }'],
 ['BLOCKING_ANDROID_CALL','fun b() { runBlocking { execute() } }'],
 ['FORCED_GC','fun c() { System.gc() }'],
 ['NON_CANCELLABLE_LOOP','fun d() { while (true) { execute() } }'],
 ['POTENTIAL_PRIVATE_LOG','fun e() { Log.e("gamehub", transcript) }']
 ];let cases=0;const misses=[];
 for(const [rule,statement] of risky)for(let k=0;k<40;k++){
  const out=analyze([file(statement,k)],{sha:SHA});cases++;
  if(!has(out,rule))misses.push({rule,k});
 }
 assert.equal(cases,240);assert.deepEqual(misses,[]);
});
test('KOTLIN GAUNTLET 160 quoted explanation strings do not create false code alerts',()=>{
 const quotes=[
 ['UNSCOPED_COROUTINE','val help = "Avoid GlobalScope.launch in Android"'],
 ['BLOCKING_ANDROID_CALL','val text = "Never use Thread.sleep(1000) in callbacks"'],
 ['FORCED_GC','val text = "Do not call System.gc() in a game"'],
 ['POTENTIAL_PRIVATE_LOG','val help = "Do not write Log.e(tag, transcript) to logcat"']
 ];let cases=0;const falseAlarms=[];
 for(const [rule,statement] of quotes)for(let k=0;k<40;k++){
  const out=analyze([file(statement,k)],{sha:SHA});cases++;
  if(has(out,rule))falseAlarms.push({rule,k});
 }
 assert.equal(cases,160);assert.deepEqual(falseAlarms,[]);
});
test('KOTLIN GAUNTLET 40 logging risks redact private data from exported findings',()=>{
 const leaked=[];
 for(let k=0;k<40;k++){
  const data='PRIVATE_'+k+'_DO_NOT_PRINT';
  const out=check('fun logIt() { Log.e("'+data+'", transcript) }');
  if(!has(out,'POTENTIAL_PRIVATE_LOG'))leaked.push('miss '+k);
  if(JSON.stringify(out.findings).includes(data))leaked.push('leak '+k);
 }
 assert.deepEqual(leaked,[]);
});
test('KOTLIN GAUNTLET 40 incomplete diffs never become clean evidence',()=>{
 for(let k=0;k<40;k++){
  const out=analyze([{filename:dir+'/Partial'+k+'.kt',changes:20,status:'modified',patch:null}],{sha:SHA});
  assert.equal(out.coverage.partial,true);assert.equal(out.verdict,'INCOMPLETE');
 }
});

test('KOTLIN GAUNTLET 120 inline comments must not appear as real unsafe calls',()=>{
 const rules=[
 ['UNSCOPED_COROUTINE','GlobalScope.launch { unexpected() }'],
 ['BLOCKING_ANDROID_CALL','runBlocking { slow() }'],
 ['FORCED_GC','System.gc()'],
 ['POTENTIAL_PRIVATE_LOG','Log.e("tag", transcript)']
 ];
 const misses=[];
 for(const [rule,fake] of rules)for(let k=0;k<30;k++){
  const out=analyze([file('val sample = '+k+' // avoid '+fake,k)],{sha:SHA});
  if(has(out,rule))misses.push({rule,k});
 }
 assert.deepEqual(misses,[]);
});
test('KOTLIN GAUNTLET 120 real calls with trailing comments are still detected',()=>{
 const rules=[
 ['UNSCOPED_COROUTINE','GlobalScope.launch { unexpected() }'],
 ['BLOCKING_ANDROID_CALL','runBlocking { slow() }'],
 ['FORCED_GC','System.gc()'],
 ['POTENTIAL_PRIVATE_LOG','Log.e("tag", transcript)']
 ];
 const misses=[];
 for(const [rule,real] of rules)for(let k=0;k<30;k++){
  const out=analyze([file('fun risky() { '+real+' } // test '+k,k)],{sha:SHA});
  if(!has(out,rule))misses.push({rule,k});
 }
 assert.deepEqual(misses,[]);
});
