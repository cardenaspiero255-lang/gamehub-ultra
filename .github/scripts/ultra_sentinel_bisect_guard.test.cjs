'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {classify}=require('./ultra_sentinel_bisect_guard.cjs');
test('bisect accepts genuinely passing tests',()=>{
 assert.equal(classify(0,'BUILD SUCCESSFUL'),0);
});
test('verified failing unit tests mark a bad revision',()=>{
 assert.equal(classify(1,"Execution failed for task ':app:testDebugUnitTest'.\n> There were failing tests. See the report"),1);
});
test('network errors, compile failures and Gradle configuration errors skip revisions',()=>{
 const errors=[
 'Could not find org.robolectric:robolectric:4.17',
 'Received status code 429 from server: Too Many Requests',
 "Execution failed for task ':app:compileDebugKotlin'.",
 "Could not resolve all files for configuration ':app:debugUnitTestRuntimeClasspath'.",
 'Gradle daemon disappeared unexpectedly',
 'BUILD FAILED'
 ];
 for(const log of errors)assert.equal(classify(1,log),125,log);
});
test('timeouts and killed runners never become confirmed regressions',()=>{
 for(const code of [124,125,126,127,137,143])assert.equal(classify(code,'There were failing tests'),125);
});
test('ambiguous or oversized logs must not establish cause',()=>{
 assert.equal(classify(2,'There were failing tests'),125);
 assert.equal(classify(1,"There were failing tests\n"+' '.repeat(200_000)),125);
});
