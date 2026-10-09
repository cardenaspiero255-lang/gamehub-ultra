'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {candidate,judge,orchestrate}=require('./ultra_sentinel_orchestrator.cjs');
const SHA='a'.repeat(40),FILE='app/src/main/java/com/cardenaspiero255/gamehubultra/voice/VoiceController.kt';
const text='fun log() {\n  Log.e(TAG, transcript)\n  println("safe")\n}\n';
const finding={rule:'POTENTIAL_PRIVATE_LOG',path:FILE,line:2,severity:'HIGH'};
test('US-026: a standalone private Logcat call gets one review-only removal proposal',()=>{
 const r=candidate({filename:FILE,content:text,sha:SHA,findings:[finding]});
 assert.equal(r.status,'DRAFT_PATCH');
 assert.equal(r.rule,'POTENTIAL_PRIVATE_LOG');
 assert.equal(r.linesChanged,1);
 assert.match(r.patch,/-  Log\.e\(TAG, transcript\)/);
 assert.doesNotMatch(r.patch,/^\+.*Log\.e/m);
 const j=judge(r,{sha:SHA,checks:{'sentinel-core-tests':'success','android-build':'success',
  'unit-test-coverage':'success','architecture-boundary':'success'}});
 assert.equal(j.status,'ELIGIBLE_FOR_HUMAN_REVIEW');
 assert.equal(j.autoMergeAllowed,false);
 assert.equal(j.autoCommitAllowed,false);
});
test('US-026: refuse potentially side-effecting, sensitive or inline Logcat replacements',()=>{
 for(const source of [
  'fun a() {\n  Log.e(TAG, fetchSecret())\n}\n',
  'fun a() {\n  Log.e(TAG, "password="+password)\n}\n',
  'fun a() {\n  if (secret) Log.d(TAG, transcript)\n}\n',
  'fun a() {\n  Log.e("private-auth-token-here", authToken)\n}\n',
  'fun a() {\n  Timber.d("private="+password)\n}\n'
 ]){
  const r=candidate({filename:FILE,content:source,sha:SHA,findings:[finding]});
  assert.equal(r.status,'NO_SAFE_TEMPLATE',JSON.stringify(r));
 }
});
test('US-026: private Logcat proposal remains valid git diff without modifying other lines',()=>{
 const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'us026-private-log-'));
 try{
  const target=path.join(dir,FILE);fs.mkdirSync(path.dirname(target),{recursive:true});
  fs.writeFileSync(target,text);
  const p=candidate({filename:FILE,content:text,sha:SHA,findings:[finding]});
  const patch=path.join(dir,'fix.patch');fs.writeFileSync(patch,p.patch);
  cp.execFileSync('git',['apply','--check',patch],{cwd:dir,timeout:2500});
  cp.execFileSync('git',['apply',patch],{cwd:dir,timeout:2500});
  const edited=fs.readFileSync(target,'utf8');
  assert.doesNotMatch(edited,/Log.e/);assert.match(edited,/println\("safe"\)/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('US-027: the orchestrator never applies or merges a log deletion proposal',()=>{
 const r=orchestrate({analysis:{coverage:{partial:false},findings:[finding]},sources:{[FILE]:text},sha:SHA});
 assert.equal(r.fixer.generated,1);
 assert.equal(r.fixer.proposals[0].judgement.autoMergeAllowed,false);
 assert.equal(r.fixer.proposals[0].judgement.autoCommitAllowed,false);
});
