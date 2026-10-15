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

test('timeout skips a revision while genuine failing tests retain their exit status',()=>{
 const cp=require('node:child_process'),os=require('node:os');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'sentinel-bisect-'));
 try{
  const match=wf().match(/cat > "\$OUT\/run-test\.sh" <<'SCRIPT'\n([\s\S]*?)\n          SCRIPT/);
  assert.ok(match,'bisect script must be embedded');
  const script=path.join(dir,'run-test.sh');
  fs.writeFileSync(script,match[1].split('\n').map(x=>x.replace(/^          /,'')).join('\n')+'\n',{mode:0o755});
  fs.copyFileSync(path.join(__dirname,'ultra_sentinel_bisect_guard.cjs'),path.join(dir,'guard.cjs'));
  fs.writeFileSync(path.join(dir,'timeout'),"#!/bin/sh\nif [ \"$SIM_TIMEOUT_CODE\" -eq 1 ]; then\n  echo \"Execution failed for task ':app:testDebugUnitTest'.\"\n  echo '> There were failing tests.'\nfi\nexit \"$SIM_TIMEOUT_CODE\"\n",{mode:0o755});
  for(const suite of ['architecture','unit']){
   for(const [input,expected] of [[0,0],[1,1],[2,125],[124,125]]){
    const processResult=cp.spawnSync('sh',[script],{
     env:{...process.env,PATH:dir+path.delimiter+process.env.PATH,
      SIM_TIMEOUT_CODE:String(input),SUITE:suite,OUT:dir},encoding:'utf8'});
    assert.equal(processResult.status,expected,
     'suite='+suite+' simulated='+input+' stderr='+processResult.stderr);
   }
  }
 }finally{fs.rmSync(dir,{recursive:true,force:true})}
});
test('regression workflow tests BAD endpoint before beginning bisect',()=>{
 const text=wf();
 assert.match(text,/"\$OUT\/run-test\.sh" > "\$OUT\/bad-endpoint\.log"/);
 assert.match(text,/if \[ "\$bad_code" -ne 1 \]; then/);
 assert.match(text,/if \[ "\$good_code" -ne 0 \]; then/);
 assert.ok(text.indexOf('bad-endpoint.log')<text.indexOf('git bisect start "$BAD" "$GOOD"'));
 assert.match(text,/::error::Current main is not a confirmed test regression/);
 assert.ok(text.indexOf('good-endpoint.log')<text.indexOf('git bisect start "$BAD" "$GOOD"'));
 assert.match(text,/ultra_sentinel_bisect_guard\.cjs/);
});
