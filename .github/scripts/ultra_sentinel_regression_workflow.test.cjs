'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const wf=()=>fs.readFileSync(path.join(__dirname,'../workflows/ultra-sentinel-regression-investigator.yml'),'utf8');
test('bisect installs pinned Gradle and Android SDK, no absent wrapper',()=>{
 const text=wf();
 assert.match(text,/gradle\/actions\/setup-gradle@[0-9a-f]{40}/);
 assert.match(text,/gradle-version: '8\.13'/);
 assert.match(text,/android-actions\/setup-android@[0-9a-f]{40}/);
 assert.match(text,/timeout 420 gradle --no-daemon :app:testDebugUnitTest/);
 assert.doesNotMatch(text,/\.\/gradlew/);
});
test('bisect takes candidate from verified first bad ref and fails on errors',()=>{
 const text=wf();
 assert.match(text,/git rev-parse --verify -q refs\/bisect\/bad/);
 assert.match(text,/::error::Bisect failed/);
 assert.doesNotMatch(text,/candidate="\$\(git rev-parse HEAD\)"/);
});
