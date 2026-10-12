'use strict';
/*
 * PR #183: authenticated immutable Git object scanner for base and HEAD.
 * The GitHub API is the only source of Git objects. Never execute PR source.
 * Scope is explicit and bounded, not a claim to reason about every language.
 */
const {createHash}=require('node:crypto');
const acorn=require('acorn');
const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
const {inspectWorkflow,parseWorkflow}=require('./ultra_sentinel_yaml_ast.cjs');
const {analyze}=require('./ultra_sentinel_core.cjs');
const SHA=/^[a-f0-9]{40}$/i;
const PATH=/^[A-Za-z0-9_.\/-]{1,240}$/;
const MAX_TREE=20000,MAX_FILES=400,MAX_FILE_BYTES=150000;
const MAX_ALL_BYTES=16000000,MAX_FINDINGS=5000,MAX_LINES=10000;
const SCOPE='WORKFLOWS_SENTINEL_CJS_ANDROID_KOTLIN_JAVA_V1';
function eligible(path){
 return /^\.github\/workflows\/[A-Za-z0-9_.-]+\.ya?ml$/.test(path)||
  /^\.github\/scripts\/ultra_sentinel_[A-Za-z0-9_.-]+\.cjs$/.test(path)&&
   !path.endsWith('.test.cjs')||
  /^app\/src\/main\/[A-Za-z0-9_.\/-]+\.(?:kt|java)$/.test(path);
}
function safePath(value){
 return typeof value==='string'&&PATH.test(value)&&!value.startsWith('/')&&
  value.split('/').every(x=>x!==''&&x!=='.'&&x!=='..');
}
function reasonList(reasons){
 return [...new Set(reasons)].slice(0,20).sort();
}
function checkedFinding(f){
 if(!f||!safePath(f.path)||!(/^[A-Za-z0-9_.:-]{1,100}$/.test(f.rule||''))||
    !['BLOCKER','CRITICAL','HIGH','MAJOR','WARNING','MEDIUM','LOW','MINOR','INFO'].includes(f.severity)||
    !Number.isSafeInteger(f.line)||f.line<1||f.line>10000000)throw Error('INVALID_SCANNER_FINDING');
 return {path:f.path,rule:f.rule,severity:f.severity,line:f.line};
}
function inspectContent(file,source){
 const path=file.path;
 if(typeof source!=='string'||source.includes('\0'))return {findings:[],complete:false};
 if(/^\.github\/workflows\//.test(path)){
  const x=inspectWorkflow(source,{path});
  // The existing AST checks custom runners mainly for privileged triggers.
  // Full snapshots must refuse unmodeled dynamic runner routing on ANY event.
  const parsed=parseWorkflow(source);
  const opaqueRunner=parsed.ok&&Object.values(parsed.workflow.jobs).some(job=>{
   if(!job||typeof job!=='object'||!Object.hasOwn(job,'runs-on'))return false;
   const runner=job['runs-on'];
   if(typeof runner==='string')return runner.includes('
 }
 if(path.endsWith('.cjs')){
  try{
   const ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:'script',
    allowHashBang:true,allowReturnOutsideFunction:true,
    allowAwaitOutsideFunction:true,locations:true,ranges:true});
   const seen=new Set(),findings=[];
   for(const node of findCapabilities(ast)){
    const line=node.loc?.start?.line;
    if(!Number.isSafeInteger(line)||line<1)throw Error('INVALID_AST_LOCATION');
    if(seen.has(line))continue;
    seen.add(line);
    findings.push(checkedFinding({path,rule:'DYNAMIC_CODE_EXECUTION',severity:'HIGH',line}));
   }
   return {findings,complete:true};
  }catch(error){return {findings:[],complete:false};}
 }
 if(path.endsWith('.kt')||path.endsWith('.java')){
  const rows=source.split(/\r?\n/);
  if(rows.at(-1)==='')rows.pop();
  if(rows.length<1||rows.length>MAX_LINES)return {findings:[],complete:false};
  const patch='@@ -0,0 +1,'+rows.length+' @@\n'+rows.map(row=>'+'+row).join('\n')+'\n';
  if(Buffer.byteLength(patch,'utf8')>320000)return {findings:[],complete:false};
  const result=analyze([{filename:path,status:'added',changes:rows.length,
   patch,fullSource:source}],{sha:'0'.repeat(40)});
  const findings=result.findings.filter(f=>f.rule!=='REGRESSION_TEST_COVERAGE'&&f.line>0)
   .map(f=>checkedFinding({path,rule:f.rule,severity:f.severity,line:f.line}));
  return {findings,complete:result.coverage?.partial===false&&result.omitted===0};
 }
 return {findings:[],complete:false};
}
function validateInventory(commit,tree,sha){
 if(!commit||commit.sha?.toLowerCase()!==sha||!SHA.test(commit.tree?.sha||''))
  throw Error('GIT_COMMIT_PROVENANCE_INCOMPLETE');
 if(!tree||tree.sha?.toLowerCase()!==commit.tree.sha.toLowerCase()||
    tree.truncated!==false||!Array.isArray(tree.tree)||tree.tree.length>MAX_TREE)
  throw Error('GIT_TREE_INCOMPLETE');
 const names=new Set(),selected=[];
 for(const item of tree.tree){
  if(!item||typeof item.path!=='string'||names.has(item.path))
   throw Error('GIT_TREE_INCOMPLETE');
  names.add(item.path);
  if(!eligible(item.path))continue;
  if(!safePath(item.path)||item.type!=='blob'||item.mode!=='100644'&&item.mode!=='100755'||
     !SHA.test(item.sha||'')||!Number.isSafeInteger(item.size)||item.size<0)
   throw Error('GIT_TREE_UNSAFE_ENTRY');
  selected.push({path:item.path,sha:item.sha.toLowerCase(),size:item.size});
 }
 selected.sort((a,b)=>a.path.localeCompare(b.path));
 return selected;
}
async function verifiedBlob(get,entry,cache){
 if(!cache.has(entry.sha)){
  const pending=(async()=>{
   const object=await get('git/blobs/'+entry.sha);
   if(!object||object.sha?.toLowerCase()!==entry.sha||object.encoding!=='base64'||
      typeof object.content!=='string'||!Number.isSafeInteger(object.size)||
      object.size<0||object.size>MAX_FILE_BYTES)throw Error('GIT_BLOB_UNVERIFIED');
   const encoded=object.content.replace(/\s/g,'');
   const bytes=Buffer.from(encoded,'base64');
   if(bytes.length!==object.size||bytes.toString('base64')!==encoded||
      createHash('sha1').update('blob '+bytes.length+'\0').update(bytes).digest('hex')!==entry.sha)
    throw Error('GIT_BLOB_UNVERIFIED');
   const source=bytes.toString('utf8');
   if(!Buffer.from(source,'utf8').equals(bytes)||source.includes('\0'))throw Error('GIT_BLOB_UNVERIFIED');
   return {source,size:bytes.length};
  })();
  cache.set(entry.sha,pending);
 }
 const blob=await cache.get(entry.sha);
 if(blob.size!==entry.size)throw Error('GIT_BLOB_SIZE_MISMATCH');
 return blob.source;
}
async function scanSnapshot({get,sha,cache=new Map()}){
 if(typeof get!=='function'||!SHA.test(sha||''))throw Error('INVALID_SNAPSHOT_REQUEST');
 const normalized=sha.toLowerCase();
 const commit=await get('git/commits/'+normalized);
 if(!SHA.test(commit?.tree?.sha||'')||commit?.sha?.toLowerCase()!==normalized)
  throw Error('GIT_COMMIT_PROVENANCE_INCOMPLETE');
 const tree=await get('git/trees/'+commit.tree.sha+'?recursive=1');
 const entries=validateInventory(commit,tree,normalized);
 const reasons=[];
 let scanned=0,totalBytes=0;
 const findings=[];
 if(entries.length===0)reasons.push('EMPTY_SCANNABLE_SCOPE');
 if(entries.length>MAX_FILES)reasons.push('SCAN_FILE_BUDGET_EXCEEDED');
 for(const entry of entries){
  totalBytes+=entry.size;
  if(entry.size>MAX_FILE_BYTES)reasons.push('SCAN_FILE_TOO_LARGE');
 }
 if(totalBytes>MAX_ALL_BYTES)reasons.push('SCAN_BYTE_BUDGET_EXCEEDED');
 if(reasons.length)return {sha:normalized,scope:SCOPE,complete:false,
  reasons:reasonList(reasons),treeEntries:tree.tree.length,
  targetFiles:entries.length,scannedFiles:0,bytes:totalBytes,findings:[]};
 // The same immutable blob SHA is cached across both commits.
 for(let i=0;i<entries.length;i+=8){
  const group=entries.slice(i,i+8);
  const read=await Promise.all(group.map(async entry=>{
   const source=await verifiedBlob(get,entry,cache);
   return inspectContent(entry,source);
  }));
  for(const item of read){
   scanned++;
   if(!item.complete)reasons.push('UNMODELED_SCANNER_CONTEXT');
   findings.push(...item.findings);
   if(findings.length>MAX_FINDINGS)throw Error('SCANNER_FINDINGS_OVERFLOW');
  }
 }
 findings.sort((a,b)=>a.path.localeCompare(b.path)||a.line-b.line||a.rule.localeCompare(b.rule));
 return {sha:normalized,scope:SCOPE,complete:reasons.length===0,
  reasons:reasonList(reasons),treeEntries:tree.tree.length,targetFiles:entries.length,
  scannedFiles:scanned,bytes:totalBytes,findings};
}
async function scanSnapshots({get,baseSha,headSha}){
 if(typeof get!=='function'||!SHA.test(baseSha||'')||!SHA.test(headSha||''))
  throw Error('INVALID_SNAPSHOT_REQUEST');
 const cache=new Map();
 const base=await scanSnapshot({get,sha:baseSha,cache});
 const head=await scanSnapshot({get,sha:headSha,cache});
 return {schema:'ultra-sentinel-authenticated-snapshots/v1',
  scope:SCOPE,base,head,verifiedBy:'github-git-object-sha1',autoMergeAllowed:false};
}
module.exports={SCOPE,eligible,safePath,checkedFinding,inspectContent,
 validateInventory,verifiedBlob,scanSnapshot,scanSnapshots,
 MAX_FILES,MAX_ALL_BYTES,MAX_FILE_BYTES};
+'{{');
   if(Array.isArray(runner))return runner.some(item=>
    typeof item!=='string'||item.includes('
 }
 if(path.endsWith('.cjs')){
  try{
   const ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:'script',
    allowHashBang:true,allowReturnOutsideFunction:true,
    allowAwaitOutsideFunction:true,locations:true,ranges:true});
   const seen=new Set(),findings=[];
   for(const node of findCapabilities(ast)){
    const line=node.loc?.start?.line;
    if(!Number.isSafeInteger(line)||line<1)throw Error('INVALID_AST_LOCATION');
    if(seen.has(line))continue;
    seen.add(line);
    findings.push(checkedFinding({path,rule:'DYNAMIC_CODE_EXECUTION',severity:'HIGH',line}));
   }
   return {findings,complete:true};
  }catch(error){return {findings:[],complete:false};}
 }
 if(path.endsWith('.kt')||path.endsWith('.java')){
  const rows=source.split(/\r?\n/);
  if(rows.at(-1)==='')rows.pop();
  if(rows.length<1||rows.length>MAX_LINES)return {findings:[],complete:false};
  const patch='@@ -0,0 +1,'+rows.length+' @@\n'+rows.map(row=>'+'+row).join('\n')+'\n';
  if(Buffer.byteLength(patch,'utf8')>320000)return {findings:[],complete:false};
  const result=analyze([{filename:path,status:'added',changes:rows.length,
   patch,fullSource:source}],{sha:'0'.repeat(40)});
  const findings=result.findings.filter(f=>f.rule!=='REGRESSION_TEST_COVERAGE'&&f.line>0)
   .map(f=>checkedFinding({path,rule:f.rule,severity:f.severity,line:f.line}));
  return {findings,complete:result.coverage?.partial===false&&result.omitted===0};
 }
 return {findings:[],complete:false};
}
function validateInventory(commit,tree,sha){
 if(!commit||commit.sha?.toLowerCase()!==sha||!SHA.test(commit.tree?.sha||''))
  throw Error('GIT_COMMIT_PROVENANCE_INCOMPLETE');
 if(!tree||tree.sha?.toLowerCase()!==commit.tree.sha.toLowerCase()||
    tree.truncated!==false||!Array.isArray(tree.tree)||tree.tree.length>MAX_TREE)
  throw Error('GIT_TREE_INCOMPLETE');
 const names=new Set(),selected=[];
 for(const item of tree.tree){
  if(!item||typeof item.path!=='string'||names.has(item.path))
   throw Error('GIT_TREE_INCOMPLETE');
  names.add(item.path);
  if(!eligible(item.path))continue;
  if(!safePath(item.path)||item.type!=='blob'||item.mode!=='100644'&&item.mode!=='100755'||
     !SHA.test(item.sha||'')||!Number.isSafeInteger(item.size)||item.size<0)
   throw Error('GIT_TREE_UNSAFE_ENTRY');
  selected.push({path:item.path,sha:item.sha.toLowerCase(),size:item.size});
 }
 selected.sort((a,b)=>a.path.localeCompare(b.path));
 return selected;
}
async function verifiedBlob(get,entry,cache){
 if(!cache.has(entry.sha)){
  const pending=(async()=>{
   const object=await get('git/blobs/'+entry.sha);
   if(!object||object.sha?.toLowerCase()!==entry.sha||object.encoding!=='base64'||
      typeof object.content!=='string'||!Number.isSafeInteger(object.size)||
      object.size<0||object.size>MAX_FILE_BYTES)throw Error('GIT_BLOB_UNVERIFIED');
   const encoded=object.content.replace(/\s/g,'');
   const bytes=Buffer.from(encoded,'base64');
   if(bytes.length!==object.size||bytes.toString('base64')!==encoded||
      createHash('sha1').update('blob '+bytes.length+'\0').update(bytes).digest('hex')!==entry.sha)
    throw Error('GIT_BLOB_UNVERIFIED');
   const source=bytes.toString('utf8');
   if(!Buffer.from(source,'utf8').equals(bytes)||source.includes('\0'))throw Error('GIT_BLOB_UNVERIFIED');
   return {source,size:bytes.length};
  })();
  cache.set(entry.sha,pending);
 }
 const blob=await cache.get(entry.sha);
 if(blob.size!==entry.size)throw Error('GIT_BLOB_SIZE_MISMATCH');
 return blob.source;
}
async function scanSnapshot({get,sha,cache=new Map()}){
 if(typeof get!=='function'||!SHA.test(sha||''))throw Error('INVALID_SNAPSHOT_REQUEST');
 const normalized=sha.toLowerCase();
 const commit=await get('git/commits/'+normalized);
 if(!SHA.test(commit?.tree?.sha||'')||commit?.sha?.toLowerCase()!==normalized)
  throw Error('GIT_COMMIT_PROVENANCE_INCOMPLETE');
 const tree=await get('git/trees/'+commit.tree.sha+'?recursive=1');
 const entries=validateInventory(commit,tree,normalized);
 const reasons=[];
 let scanned=0,totalBytes=0;
 const findings=[];
 if(entries.length===0)reasons.push('EMPTY_SCANNABLE_SCOPE');
 if(entries.length>MAX_FILES)reasons.push('SCAN_FILE_BUDGET_EXCEEDED');
 for(const entry of entries){
  totalBytes+=entry.size;
  if(entry.size>MAX_FILE_BYTES)reasons.push('SCAN_FILE_TOO_LARGE');
 }
 if(totalBytes>MAX_ALL_BYTES)reasons.push('SCAN_BYTE_BUDGET_EXCEEDED');
 if(reasons.length)return {sha:normalized,scope:SCOPE,complete:false,
  reasons:reasonList(reasons),treeEntries:tree.tree.length,
  targetFiles:entries.length,scannedFiles:0,bytes:totalBytes,findings:[]};
 // The same immutable blob SHA is cached across both commits.
 for(let i=0;i<entries.length;i+=8){
  const group=entries.slice(i,i+8);
  const read=await Promise.all(group.map(async entry=>{
   const source=await verifiedBlob(get,entry,cache);
   return inspectContent(entry,source);
  }));
  for(const item of read){
   scanned++;
   if(!item.complete)reasons.push('UNMODELED_SCANNER_CONTEXT');
   findings.push(...item.findings);
   if(findings.length>MAX_FINDINGS)throw Error('SCANNER_FINDINGS_OVERFLOW');
  }
 }
 findings.sort((a,b)=>a.path.localeCompare(b.path)||a.line-b.line||a.rule.localeCompare(b.rule));
 return {sha:normalized,scope:SCOPE,complete:reasons.length===0,
  reasons:reasonList(reasons),treeEntries:tree.tree.length,targetFiles:entries.length,
  scannedFiles:scanned,bytes:totalBytes,findings};
}
async function scanSnapshots({get,baseSha,headSha}){
 if(typeof get!=='function'||!SHA.test(baseSha||'')||!SHA.test(headSha||''))
  throw Error('INVALID_SNAPSHOT_REQUEST');
 const cache=new Map();
 const base=await scanSnapshot({get,sha:baseSha,cache});
 const head=await scanSnapshot({get,sha:headSha,cache});
 return {schema:'ultra-sentinel-authenticated-snapshots/v1',
  scope:SCOPE,base,head,verifiedBy:'github-git-object-sha1',autoMergeAllowed:false};
}
module.exports={SCOPE,eligible,safePath,checkedFinding,inspectContent,
 validateInventory,verifiedBlob,scanSnapshot,scanSnapshots,
 MAX_FILES,MAX_ALL_BYTES,MAX_FILE_BYTES};
+'{{'));
   return true;
  });
  const findings=x.findings.map(f=>checkedFinding({path,rule:f.rule,severity:f.severity,line:f.line||1}));
  return {findings,complete:x.coverage?.partial===false&&parsed.ok&&!opaqueRunner};
 }
 if(path.endsWith('.cjs')){
  try{
   const ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:'script',
    allowHashBang:true,allowReturnOutsideFunction:true,
    allowAwaitOutsideFunction:true,locations:true,ranges:true});
   const seen=new Set(),findings=[];
   for(const node of findCapabilities(ast)){
    const line=node.loc?.start?.line;
    if(!Number.isSafeInteger(line)||line<1)throw Error('INVALID_AST_LOCATION');
    if(seen.has(line))continue;
    seen.add(line);
    findings.push(checkedFinding({path,rule:'DYNAMIC_CODE_EXECUTION',severity:'HIGH',line}));
   }
   return {findings,complete:true};
  }catch(error){return {findings:[],complete:false};}
 }
 if(path.endsWith('.kt')||path.endsWith('.java')){
  const rows=source.split(/\r?\n/);
  if(rows.at(-1)==='')rows.pop();
  if(rows.length<1||rows.length>MAX_LINES)return {findings:[],complete:false};
  const patch='@@ -0,0 +1,'+rows.length+' @@\n'+rows.map(row=>'+'+row).join('\n')+'\n';
  if(Buffer.byteLength(patch,'utf8')>320000)return {findings:[],complete:false};
  const result=analyze([{filename:path,status:'added',changes:rows.length,
   patch,fullSource:source}],{sha:'0'.repeat(40)});
  const findings=result.findings.filter(f=>f.rule!=='REGRESSION_TEST_COVERAGE'&&f.line>0)
   .map(f=>checkedFinding({path,rule:f.rule,severity:f.severity,line:f.line}));
  return {findings,complete:result.coverage?.partial===false&&result.omitted===0};
 }
 return {findings:[],complete:false};
}
function validateInventory(commit,tree,sha){
 if(!commit||commit.sha?.toLowerCase()!==sha||!SHA.test(commit.tree?.sha||''))
  throw Error('GIT_COMMIT_PROVENANCE_INCOMPLETE');
 if(!tree||tree.sha?.toLowerCase()!==commit.tree.sha.toLowerCase()||
    tree.truncated!==false||!Array.isArray(tree.tree)||tree.tree.length>MAX_TREE)
  throw Error('GIT_TREE_INCOMPLETE');
 const names=new Set(),selected=[];
 for(const item of tree.tree){
  if(!item||typeof item.path!=='string'||names.has(item.path))
   throw Error('GIT_TREE_INCOMPLETE');
  names.add(item.path);
  if(!eligible(item.path))continue;
  if(!safePath(item.path)||item.type!=='blob'||item.mode!=='100644'&&item.mode!=='100755'||
     !SHA.test(item.sha||'')||!Number.isSafeInteger(item.size)||item.size<0)
   throw Error('GIT_TREE_UNSAFE_ENTRY');
  selected.push({path:item.path,sha:item.sha.toLowerCase(),size:item.size});
 }
 selected.sort((a,b)=>a.path.localeCompare(b.path));
 return selected;
}
async function verifiedBlob(get,entry,cache){
 if(!cache.has(entry.sha)){
  const pending=(async()=>{
   const object=await get('git/blobs/'+entry.sha);
   if(!object||object.sha?.toLowerCase()!==entry.sha||object.encoding!=='base64'||
      typeof object.content!=='string'||!Number.isSafeInteger(object.size)||
      object.size<0||object.size>MAX_FILE_BYTES)throw Error('GIT_BLOB_UNVERIFIED');
   const encoded=object.content.replace(/\s/g,'');
   const bytes=Buffer.from(encoded,'base64');
   if(bytes.length!==object.size||bytes.toString('base64')!==encoded||
      createHash('sha1').update('blob '+bytes.length+'\0').update(bytes).digest('hex')!==entry.sha)
    throw Error('GIT_BLOB_UNVERIFIED');
   const source=bytes.toString('utf8');
   if(!Buffer.from(source,'utf8').equals(bytes)||source.includes('\0'))throw Error('GIT_BLOB_UNVERIFIED');
   return {source,size:bytes.length};
  })();
  cache.set(entry.sha,pending);
 }
 const blob=await cache.get(entry.sha);
 if(blob.size!==entry.size)throw Error('GIT_BLOB_SIZE_MISMATCH');
 return blob.source;
}
async function scanSnapshot({get,sha,cache=new Map()}){
 if(typeof get!=='function'||!SHA.test(sha||''))throw Error('INVALID_SNAPSHOT_REQUEST');
 const normalized=sha.toLowerCase();
 const commit=await get('git/commits/'+normalized);
 if(!SHA.test(commit?.tree?.sha||'')||commit?.sha?.toLowerCase()!==normalized)
  throw Error('GIT_COMMIT_PROVENANCE_INCOMPLETE');
 const tree=await get('git/trees/'+commit.tree.sha+'?recursive=1');
 const entries=validateInventory(commit,tree,normalized);
 const reasons=[];
 let scanned=0,totalBytes=0;
 const findings=[];
 if(entries.length===0)reasons.push('EMPTY_SCANNABLE_SCOPE');
 if(entries.length>MAX_FILES)reasons.push('SCAN_FILE_BUDGET_EXCEEDED');
 for(const entry of entries){
  totalBytes+=entry.size;
  if(entry.size>MAX_FILE_BYTES)reasons.push('SCAN_FILE_TOO_LARGE');
 }
 if(totalBytes>MAX_ALL_BYTES)reasons.push('SCAN_BYTE_BUDGET_EXCEEDED');
 if(reasons.length)return {sha:normalized,scope:SCOPE,complete:false,
  reasons:reasonList(reasons),treeEntries:tree.tree.length,
  targetFiles:entries.length,scannedFiles:0,bytes:totalBytes,findings:[]};
 // The same immutable blob SHA is cached across both commits.
 for(let i=0;i<entries.length;i+=8){
  const group=entries.slice(i,i+8);
  const read=await Promise.all(group.map(async entry=>{
   const source=await verifiedBlob(get,entry,cache);
   return inspectContent(entry,source);
  }));
  for(const item of read){
   scanned++;
   if(!item.complete)reasons.push('UNMODELED_SCANNER_CONTEXT');
   findings.push(...item.findings);
   if(findings.length>MAX_FINDINGS)throw Error('SCANNER_FINDINGS_OVERFLOW');
  }
 }
 findings.sort((a,b)=>a.path.localeCompare(b.path)||a.line-b.line||a.rule.localeCompare(b.rule));
 return {sha:normalized,scope:SCOPE,complete:reasons.length===0,
  reasons:reasonList(reasons),treeEntries:tree.tree.length,targetFiles:entries.length,
  scannedFiles:scanned,bytes:totalBytes,findings};
}
async function scanSnapshots({get,baseSha,headSha}){
 if(typeof get!=='function'||!SHA.test(baseSha||'')||!SHA.test(headSha||''))
  throw Error('INVALID_SNAPSHOT_REQUEST');
 const cache=new Map();
 const base=await scanSnapshot({get,sha:baseSha,cache});
 const head=await scanSnapshot({get,sha:headSha,cache});
 return {schema:'ultra-sentinel-authenticated-snapshots/v1',
  scope:SCOPE,base,head,verifiedBy:'github-git-object-sha1',autoMergeAllowed:false};
}
module.exports={SCOPE,eligible,safePath,checkedFinding,inspectContent,
 validateInventory,verifiedBlob,scanSnapshot,scanSnapshots,
 MAX_FILES,MAX_ALL_BYTES,MAX_FILE_BYTES};
