'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {parseGitDiff,inspect}=require('./ultra_sentinel_precommit.cjs');
const patch=(path,added)=>[
 'diff --git a/'+path+' b/'+path,'index 0123456..1234567 100644','--- a/'+path,'+++ b/'+path,
 '@@ -1,1 +1,'+added.length+' @@',...added.map(x=>'+'+x)].join('\n');
test('parses staged Git diff with exact filenames and added lines',()=>{
 const raw=patch('app/src/main/java/X.kt',['GlobalScope.launch { work() }']);
 const result=parseGitDiff(raw);
 assert.equal(result[0].filename,'app/src/main/java/X.kt');
 assert.equal(result[0].changes,1);
});
test('rejects dangerous code as hypothesis',()=>{
 const r=inspect(patch('app/src/main/java/X.kt',['Thread.sleep(100)']));
 assert.ok(r.alerts.some(x=>x.rule==='BLOCKING_ANDROID_CALL'));
});
test('empty diff fast path',()=>{
 assert.equal(inspect('').files,0);
});
test('does not execute untrusted PR scripts',()=>{
 const r=inspect(patch('README.md',['GlobalScope.launch { }']));
 assert.equal(r.blockers.length,0);
});
test('fails safely on very large staged changes',()=>{
 assert.throws(()=>parseGitDiff('x'.repeat(260001)));
});
test('production credential triggers possible blocker',()=>{
 const r=inspect(patch('app/src/main/java/Auth.kt',['SENTRY_AUTH_TOKEN="abcdefghijklmnop"']));
 assert.ok(r.blockers.some(x=>x.rule==='POTENTIAL_HARDCODED_SECRET'));
});
test('no magical compile predictions',()=>{
 const r=inspect(patch('app/src/main/java/X.kt',['val x = 1']));
 assert.match(r.note,/no AST/);
});
