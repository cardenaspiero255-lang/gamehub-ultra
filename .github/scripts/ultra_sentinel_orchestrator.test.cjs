'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const p=require('./ultra_sentinel_orchestrator.cjs');
const sha='a'.repeat(40),filename='app/src/main/java/com/cardenaspiero255/gamehubultra/App.kt';
const content='fun a() {\n  System.gc()\n  println("ok")\n}\n';
const f={rule:'FORCED_GC',path:filename,line:2,severity:'MEDIUM'};
test('hunter fixer judge produces limited optional exact patch',()=>{
 const r=p.candidate({filename,content,findings:[f],sha});
 assert.equal(r.status,'DRAFT_PATCH');assert.equal(r.linesChanged,1);
 assert.match(r.patch,/-  System\.gc\(\)/);assert.match(r.patch,/^\+\+\+ b\//m);
});
test('refuse ambiguous Java or Kotlin without line evidence',()=>{
 assert.equal(p.candidate({filename,content,findings:[{...f,line:9}],sha}).status,'NO_SAFE_TEMPLATE');
});
test('refuse any provider suggested generic patch',()=>{
 assert.equal(p.candidate({filename,content,findings:[{...f,rule:'UNBOUNDED_RECURSION'}],sha}).status,'NO_SAFE_TEMPLATE');
});
test('refuse traversal paths',()=>{
 assert.equal(p.candidate({filename:'app/src/main/../secrets.kt',content,findings:[f],sha}).status,'NO_SAFE_TEMPLATE');
});
test('refuse string/comment containing System.gc()',()=>{
 assert.equal(p.candidate({filename,content:'fun a() {\n  println("System.gc()")\n}\n',
 findings:[f],sha}).status,'NO_SAFE_TEMPLATE');
});
test('reject stale SHA',()=>{
 const r=p.candidate({filename,content,findings:[f],sha});
 assert.equal(p.judge(r,{sha:'b'.repeat(40)}).status,'REJECT');
});
test('never auto merge even with all checks success',()=>{
 const r=p.candidate({filename,content,findings:[f],sha});
 const j=p.judge(r,{sha,checks:{'sentinel-core-tests':'success','android-build':'success',
  'unit-test-coverage':'success','architecture-boundary':'success'}});
 assert.equal(j.status,'ELIGIBLE_FOR_HUMAN_REVIEW');assert.equal(j.autoMergeAllowed,false);
});
test('pending build is not a verification',()=>{
 const r=p.candidate({filename,content,findings:[f],sha});
 const j=p.judge(r,{sha});assert.equal(j.status,'REVIEW_PENDING');
 assert.ok(j.pendingChecks.includes('android-build'));
});
test('orchestrator produces no imaginary patch for unsupported rules',()=>{
 const r=p.orchestrate({analysis:{findings:[{...f,rule:'SPEECH_REENTRANT_RETRY'}]},sources:{[filename]:content},sha});
 assert.equal(r.fixer.generated,0);
});
test('partial diff cannot authorize check-ready proposal',()=>{
 const r=p.orchestrate({analysis:{coverage:{partial:true},findings:[f]},sources:{[filename]:content},sha});
 assert.equal(r.judge.verdict,'INSUFFICIENT_EVIDENCE');
});
test('bound sources and edits',()=>{
 const files=Object.fromEntries(Array(120).fill(0).map((_,i)=>['app/src/main/java/X'+i+'.kt',content]));
 const r=p.orchestrate({analysis:{findings:[]},sources:files,sha});
 assert.equal(r.fixer.generated,0);assert.equal(p.judge(null).status,'REJECT');
});

test('generated hunk passes git apply --check in temporary Kotlin tree',()=>{
 const fs=require('node:fs'),os=require('node:os'),cp=require('node:child_process'),path=require('node:path');
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'sentinel-patch-'));
 try{
  const target=path.join(tmp,filename);
  fs.mkdirSync(path.dirname(target),{recursive:true});
  fs.writeFileSync(target,content);
  const proposal=p.candidate({filename,content,findings:[f],sha});
  const patchfile=path.join(tmp,'fix.patch');
  fs.writeFileSync(patchfile,proposal.patch);
  cp.execFileSync('git',['apply','--check',patchfile],{cwd:tmp,timeout:2500});
  cp.execFileSync('git',['apply',patchfile],{cwd:tmp,timeout:2500});
  const changed=fs.readFileSync(target,'utf8');
  assert.doesNotMatch(changed,/System\.gc\(\)/);
  assert.match(changed,/println\("ok"\)/);
 }finally{fs.rmSync(tmp,{recursive:true,force:true})}
});
