'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {createHash}=require('node:crypto');
const s=require('./ultra_sentinel_snapshot_scan.cjs');
const B='a'.repeat(40),H='b'.repeat(40),TB='c'.repeat(40),TH='d'.repeat(40);
const safe='name: Safe\non: pull_request\npermissions:\n  contents: read\njobs:\n  quality:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo ok\n';
function blob(src){const b=Buffer.from(src);return {sha:createHash('sha1').update('blob '+b.length+'\0').update(b).digest('hex'),size:b.length,encoding:'base64',content:b.toString('base64')};}
function fixture(baseFiles=[['.github/workflows/safe.yml',safe]],headFiles=baseFiles){
 const blobs=new Map(),counts=new Map();
 function inventory(items,sha,treeSha){
  return {commit:{sha,tree:{sha:treeSha}},tree:{sha:treeSha,truncated:false,
   tree:items.map(([path,src])=>{const x=blob(src);blobs.set(x.sha,x);
    return {path,sha:x.sha,size:x.size,type:'blob',mode:'100644'};})}};
 }
 const base=inventory(baseFiles,B,TB),head=inventory(headFiles,H,TH);
 const state={base,head,blobs,counts};
 async function get(url){
  counts.set(url,(counts.get(url)||0)+1);
  if(url==='git/commits/'+B)return structuredClone(state.base.commit);
  if(url==='git/commits/'+H)return structuredClone(state.head.commit);
  if(url==='git/trees/'+TB+'?recursive=1')return structuredClone(state.base.tree);
  if(url==='git/trees/'+TH+'?recursive=1')return structuredClone(state.head.tree);
  const b=blobs.get(url.replace(/^git\/blobs\//,''));
  if(url.startsWith('git/blobs/')&&b)return structuredClone(b);
  throw Error('UNEXPECTED_TEST_URL');
 }
 return {get,state,counts};
}
const scan=d=>s.scanSnapshots({get:d.get,baseSha:B,headSha:H});
test('scope includes all supported scanner targets',()=>{
 for(const p of ['.github/workflows/a.yml','.github/scripts/ultra_sentinel_a.cjs',
  'app/src/main/java/org/A.kt','app/src/main/java/org/A.java'])assert.equal(s.eligible(p),true,p);
});
test('scope transparently excludes test fixtures and unsupported files',()=>{
 for(const p of ['docs/a.md','.github/scripts/ultra_sentinel_a.test.cjs',
  'app/src/test/A.kt','control-center/a.ts','app/src/main/assets/a.png'])
  assert.equal(s.eligible(p),false,p);
});
test('immutable base and HEAD objects are verified separately',async()=>{
 const d=fixture(),r=await scan(d);
 assert.equal(r.base.sha,B);assert.equal(r.head.sha,H);assert.equal(r.scope,s.SCOPE);
 assert.equal(r.base.complete,true);assert.equal(r.head.complete,true);
 assert.equal(r.autoMergeAllowed,false);
});
test('shared Git blobs are fetched once across both commit snapshots',async()=>{
 const d=fixture();await scan(d);
 assert.equal(d.counts.get('git/blobs/'+blob(safe).sha),1);
});
test('spoofed commit identity fails closed',async()=>{
 const d=fixture();d.state.base.commit.sha=H;
 await assert.rejects(scan(d),/GIT_COMMIT_PROVENANCE_INCOMPLETE/);
});
test('missing valid commit tree SHA fails closed',async()=>{
 const d=fixture();d.state.base.commit.tree.sha='BAD';
 await assert.rejects(scan(d),/GIT_COMMIT_PROVENANCE_INCOMPLETE/);
});
test('a different tree SHA cannot masquerade as the authenticated commit tree',async()=>{
 const d=fixture();d.state.base.tree.sha=H;
 await assert.rejects(scan(d),/GIT_TREE_INCOMPLETE/);
});
test('truncated recursive tree is not complete',async()=>{
 const d=fixture();d.state.base.tree.truncated=true;
 await assert.rejects(scan(d),/GIT_TREE_INCOMPLETE/);
});
test('duplicate filenames are rejected before source inspection',async()=>{
 const d=fixture();d.state.base.tree.tree.push({...d.state.base.tree.tree[0]});
 await assert.rejects(scan(d),/GIT_TREE_INCOMPLETE/);
});
test('symlinks, submodules and wrong object types cannot pass validation',async()=>{
 for(const [mode,type] of [['120000','blob'],['160000','commit'],['100644','tree']]){
  const d=fixture();d.state.base.tree.tree[0].mode=mode;
  d.state.base.tree.tree[0].type=type;
  await assert.rejects(scan(d),/GIT_TREE_UNSAFE_ENTRY/);
 }
});
test('blob SHA and incorrect encoding fail closed',async()=>{
 for(const field of ['sha','encoding']){
  const d=fixture();d.state.blobs.get(blob(safe).sha)[field]=field==='sha'?H:'utf-8';
  await assert.rejects(scan(d),/GIT_BLOB_UNVERIFIED/);
 }
});
test('modified base64 payload cannot claim the original Git object SHA',async()=>{
 const d=fixture();d.state.blobs.get(blob(safe).sha).content=Buffer.from('evil').toString('base64');
 await assert.rejects(scan(d),/GIT_BLOB_UNVERIFIED/);
});
test('fake reported Git blob length fails closed',async()=>{
 const d=fixture();d.state.blobs.get(blob(safe).sha).size++;
 await assert.rejects(scan(d),/GIT_BLOB_UNVERIFIED/);
});
test('tree size mismatch cannot be authenticated by matching object bytes',async()=>{
 const d=fixture();d.state.base.tree.tree[0].size++;
 await assert.rejects(scan(d),/GIT_BLOB_SIZE_MISMATCH/);
});
test('reject invalid SHA and missing API function',async()=>{
 const d=fixture();
 for(const args of [{get:d.get,baseSha:'main',headSha:H},
  {get:d.get,baseSha:B,headSha:'bad'},{get:null,baseSha:B,headSha:H}])
  await assert.rejects(s.scanSnapshots(args),/INVALID_SNAPSHOT_REQUEST/);
});
test('path validation rejects dot segments and ambiguous separators',()=>{
 for(const path of ['../x.kt','app//src.kt','/root.kt','a/./x.kt'])
  assert.equal(s.safePath(path),false);
});
test('JavaScript source is parsed as data, not executed',async()=>{
 const src="'use strict'; const marker='NEVER_EXECUTE';";
 const r=await scan(fixture([['.github/scripts/ultra_sentinel_ok.cjs',src]]));
 assert.equal(r.base.complete,true);assert.equal(r.base.findings.length,0);
});
test('AST finds dynamic eval without running malicious candidate code',()=>{
 const r=s.inspectContent({path:'.github/scripts/ultra_sentinel_a.cjs'},"const e=eval; e('malware');");
 assert.equal(r.complete,true);assert.ok(r.findings.some(f=>f.rule==='DYNAMIC_CODE_EXECUTION'));
});
test('invalid JS grammar returns INCOMPLETE rather than clean',()=>{
 const r=s.inspectContent({path:'.github/scripts/ultra_sentinel_a.cjs'},'function {');
 assert.equal(r.complete,false);
});
test('unmodeled workflow syntax fails incomplete',()=>{
 const src='on: pull_request\njobs:\n  q:\n    runs-on: $'+'{{ github.event.inputs.runner }}\n    steps:\n      - run: echo hello';
 const r=s.inspectContent({path:'.github/workflows/complex.yml'},src);
 assert.equal(r.complete,false);
});
test('Kotlin scanner finds unscoped coroutine at exact source line',()=>{
 const r=s.inspectContent({path:'app/src/main/java/x/A.kt'},
  'package x\nfun a() { GlobalScope.launch { println("run") } }\n');
 assert.equal(r.complete,true);
 assert.ok(r.findings.some(f=>f.rule==='UNSCOPED_COROUTINE'&&f.line===2));
});
test('Kotlin scanner does not copy source text into findings',()=>{
 const r=s.inspectContent({path:'app/src/main/java/x/A.kt'},
  'fun a() { GlobalScope.launch { println("SENSITIVE_PAYLOAD") } }\n');
 assert.equal(JSON.stringify(r).includes('SENSITIVE_PAYLOAD'),false);
});
test('NUL byte source is never considered a completed scan',()=>{
 assert.equal(s.inspectContent({path:'.github/scripts/ultra_sentinel_a.cjs'},'a\0b').complete,false);
});
test('oversized file refuses a full scan before any blob download',async()=>{
 const d=fixture();d.state.base.tree.tree[0].size=s.MAX_FILE_BYTES+1;
 const r=await s.scanSnapshot({get:d.get,sha:B});
 assert.equal(r.complete,false);assert.ok(r.reasons.includes('SCAN_FILE_TOO_LARGE'));
 assert.equal(r.scannedFiles,0);
});
test('too many eligible files exceed bounded scan inventory',async()=>{
 const d=fixture();
 d.state.base.tree.tree=Array.from({length:s.MAX_FILES+1},(_,i)=>({
  path:'.github/workflows/w'+i+'.yml',sha:blob(safe).sha,size:blob(safe).size,
  type:'blob',mode:'100644'}));
 const r=await s.scanSnapshot({get:d.get,sha:B});
 assert.equal(r.complete,false);assert.ok(r.reasons.includes('SCAN_FILE_BUDGET_EXCEEDED'));
});
test('zero eligible source files are explicitly INCOMPLETE',async()=>{
 const r=await scan(fixture([],[]));
 assert.equal(r.base.complete,false);
 assert.ok(r.head.reasons.includes('EMPTY_SCANNABLE_SCOPE'));
});
test('invalid finding fields are rejected, not normalized into PASS',()=>{
 const basic={path:'a/b.kt',rule:'OK',severity:'HIGH',line:1};
 for(const change of [{path:'../x.kt'},{rule:'<x>'},{severity:'SUPER'},{line:0}])
  assert.throws(()=>s.checkedFinding({...basic,...change}),/INVALID_SCANNER_FINDING/);
});
test('results do not leak source strings, credentials or arbitrary code',async()=>{
 const src="const secret='ghp_FAKE_SECRET_MARKER_NEVER_LEAK';";
 const r=await scan(fixture([['.github/scripts/ultra_sentinel_ok.cjs',src]]));
 assert.equal(r.base.complete,true);
 assert.equal(JSON.stringify(r).includes('FAKE_SECRET_MARKER_NEVER_LEAK'),false);
});
