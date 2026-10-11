'use strict';
/**
 * Ultra Sentinel Self-Review Gate
 * Run independent trusted main-branch code against UNTRUSTED PR diff data.
 * Advisory is NOT approval. Never merge or run PR code in a privileged job.
 */
const SCOPE=/^\.github\/(?:scripts\/ultra_sentinel_[\w.-]+\.(?:cjs|py)|workflows\/ultra-sentinel-[\w.-]+\.ya?ml|sentinel-contracts\/[\w./-]+)$/;
const REQUIRED=[
 '.github/scripts/ultra_sentinel_core.cjs',
 '.github/scripts/ultra_sentinel_selfreview.cjs',
 '.github/workflows/ultra-sentinel-self-review.yml'
];
function parseAdded(patch){
 if(typeof patch!=='string'||patch.length>150000)return null;
 let line=0,active=false;const added=[];
 for(const row of patch.split('\n')){
  const h=row.match(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/);
  if(h){line=+h[1];active=true;continue}
  if(!active||row.startsWith('+++')||row.startsWith('---')||row.startsWith('\\'))continue;
  if(row.startsWith('+')){added.push({line:line++,text:row.slice(1)})}
  else if(row.startsWith(' '))line++;
 }
 return added;
}

// Real JavaScript grammar, not an ad-hoc regex/division/comment lexer.
// Acorn is installed from a SHA512-locked npm artifact, with no lifecycle scripts.
// The fetched candidate source is parsed as DATA and is never imported or run.
const acorn=require('acorn');
const AST_NODE_LIMIT=50000;
const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
function jsDiffSource(patch){
 let started=false,last=0,first=true,lines=[];
 for(const row of patch.split('\n')){
  const m=row.match(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/);
  if(m){
   const start=Number(m[1]);
   if(first&&start!==1)return null;
   if(!first&&start!==last+1)return null;
   first=false;started=true;last=start-1;continue;
  }
  if(!started||row.startsWith('+++')||row.startsWith('---')||row.startsWith('\\'))continue;
  if(row[0]==='+'||row[0]===' '){lines.push(row.slice(1));last++;}
 }
 return first?null:lines.join('\n');
}
function jsAstByLine(patch,fullSource){
 const additions=parseAdded(patch),map=new Map();
 if(!additions?.length)return map;
 const fail=(rule)=>{map.set(additions[0].line,{unknown:true,reason:rule});return map;};
 const exact=typeof fullSource==='string';
 if(exact&&Buffer.byteLength(fullSource,'utf8')>160000)return fail('SOURCE_TOO_LARGE');
 const text=exact?fullSource:jsDiffSource(patch);
 if(text===null||Buffer.byteLength(text,'utf8')>160000)return fail('JS_CONTEXT_UNAVAILABLE');
 let ast;
 try{
  ast=acorn.parse(text,{ecmaVersion:'latest',sourceType:'script',
   allowHashBang:true,allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,
   locations:true,ranges:true});
 }catch(_){return fail('JS_PARSE_ERROR');}
 let sinks;
 try{sinks=findCapabilities(ast);}catch(_){return fail('SCOPE_OR_CAPABILITY_INCOMPLETE');}
 if(sinks.length){
  // With exact source, also catch added syntax that exposes an old sink.
  // Reviewers are security-critical: a pre-existing sink still blocks edits.
  const first=additions[0].line;
  for(const sink of sinks){
   const line=sink.loc?.start?.line||first;
   const added=additions.find(a=>a.line===line)||additions.find(a=>
    a.line>=(sink.loc?.start?.line||first)&&a.line<=(sink.loc?.end?.line||first));
   const slot=added?.line||first;
   map.set(slot,{sink:true});
  }
 }
 return map;
}

function scan(files,sha){
 const findings=[],changed=[];
 function flag(rule,severity,file,line,reason){findings.push({rule,severity,file,line,reason})}
 if(!/^[a-f0-9]{40}$/i.test(String(sha||''))||!Array.isArray(files)||files.length>250)
  return {status:'BLOCKED',findings:[{rule:'INVALID_OR_UNBOUNDED_INPUT',severity:'BLOCKER'}],filesReviewed:0,requiresHuman:true,autoMergeAllowed:false};
 for(const f of files){
  const file=String(f?.filename||'');
  const previous=String(f?.previous_filename||'');
  if(f?.status==='renamed' && previous!==file && REQUIRED.includes(previous)){
   changed.push(previous);
   flag('REMOVED_GATE','BLOCKER',previous,0,'Critical reviewer component renamed away');
  }
  if(f?.status==='renamed' && SCOPE.test(previous) && !SCOPE.test(file)){
   changed.push(previous);
   flag('REVIEWER_SCOPE_ESCAPED','BLOCKER',previous,0,'Sentinel source moved outside review scope');
  }
  if(!SCOPE.test(file))continue;
  changed.push(file);
  if(f.status==='removed'){
   if(REQUIRED.includes(file))flag('REMOVED_GATE','BLOCKER',file,0,'Critical reviewer component removed');
   continue;
  }
  const additions=parseAdded(f.patch);
  if(!additions||((Number(f.changes)||0)>0&&!additions.length)){
   flag('INCOMPLETE_DIFF','BLOCKER',file,0,'Diff truncated or missing');continue;
  }
  const inspectJs=file.endsWith('.cjs')&&!file.endsWith('.test.cjs');
  const executableByLine=inspectJs?jsAstByLine(f.patch,f.fullSource):null;
  for(const a of additions){
   // Normalize YAML list prefixes, quotes and trailing comments before rules.
   const line=a.text.trim().replace(/\s+#.*$/,'').trim()
    .replace(/^-\s+/,'').trim()
    .replace(/^([\w-]+:\s*)["']([^"']+)["']\s*$/,'$1$2');
   if(file.includes('/workflows/')&&/^ref:\s*/i.test(line)&&
    /\$\{\{\s*(?:github\.event\.pull_request\.head\.|github\.head_ref|github\.event\.workflow_run\.head_sha)/i.test(line))
    flag('UNTRUSTED_CHECKOUT','BLOCKER',file,a.line,'Unsafe head checkout may execute PR code');
   if(file.includes('/workflows/')&&/^(?:permissions:\s*write-all|contents:\s*write|actions:\s*write)$/i.test(line))
    flag('WORKFLOW_PRIVILEGE','BLOCKER',file,a.line,'Unexpected new workflow write permission');
   if(file.includes('/workflows/')&&/^uses:\s*/i.test(line)){
    const action=line.replace(/^uses:\s*/i,'').trim();
    // Disallow every unpinned/dynamic remote action, not only known tag names.
    // Local repository and docker:// references are distinct action types.
    if(!action.startsWith('./')&&!action.startsWith('docker://')&&
      !/^[\w.-]+\/[\w./-]+@[a-f0-9]{40}$/i.test(action))
      flag('MUTABLE_ACTION','BLOCKER',file,a.line,'New action is not pinned to a commit');
   }
   const js=executableByLine?.get(a.line);
   if(inspectJs&&js?.unknown)
    flag('JS_CONTEXT_INCOMPLETE','BLOCKER',file,a.line,'Cannot attest JavaScript grammar or complete context');
   else if(inspectJs&&js?.sink)
    flag('DYNAMIC_EVAL','BLOCKER',file,a.line,'Executable dynamic code in reviewer');
   if(file.endsWith('.cjs')&&!file.endsWith('.test.cjs')&&!file.endsWith('ultra_sentinel_mutation.cjs')&&/\bauto(?:Merge|Commit)Allowed:\s*true\b/.test(line))
    flag('UNREVIEWED_AUTOMATION','BLOCKER',file,a.line,'Automated commit or merge enabled');
  }
 }
 if(changed.some(p=>REQUIRED.includes(p)))
  flag('REVIEWER_CHANGED','WARNING','self-review',0,'Always require human review for modifications to self-review components');
 return {status:findings.some(f=>f.severity==='BLOCKER')?'BLOCKED':'ADVISORY',
  sha:sha.toLowerCase(),filesReviewed:changed.length,findings,requiresHuman:true,
  autoMergeAllowed:false,autoCommitAllowed:false,
  note:'Diff-only security checks by trusted main; other bugs may remain'};
}
async function get(url,token){
 const response=await fetch(url,{headers:{Authorization:'Bearer '+token,
  Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'},
  signal:AbortSignal.timeout(20000)});
 if(!response.ok)throw Error('GitHub API '+response.status);
 return response.json();
}
async function reviewRemote(env=process.env){
 const repo=String(env.GITHUB_REPOSITORY||''),pr=Number(env.PR_NUMBER),
  sha=String(env.PR_HEAD_SHA||''),token=String(env.GITHUB_TOKEN||'');
 if(!/^[\w.-]+\/[\w.-]+$/.test(repo)||!Number.isSafeInteger(pr)||pr<=0||!/^[a-f0-9]{40}$/i.test(sha)||!token)
  throw Error('Invalid immutable PR request');
 const base='https://api.github.com/repos/'+repo+'/pulls/'+pr;
 const metadata=await get(base,token);
 if(metadata.state!=='open'||metadata.head?.sha!==sha||metadata.changed_files>250)
  throw Error('PR changed, closed, or too large');
 let files=[];
 for(let page=1;page<=3;page++){
  const group=await get(base+'/files?per_page=100&page='+page,token);
  if(!Array.isArray(group))throw Error('Invalid file metadata');
  files.push(...group);if(group.length<100)break;
 }
 if(files.length!==metadata.changed_files)throw Error('Truncated pull request');
 // Exact-SHA read-only source context is needed for hunks beginning inside
 // existing JS comments. Never execute, import, or evaluate PR source.
 const javascriptFiles=files.filter(f=>f.status!=='removed'&&
  SCOPE.test(f.filename)&&f.filename.endsWith('.cjs')&&
  !f.filename.endsWith('.test.cjs')&&typeof f.patch==='string');
 for(let i=0;i<javascriptFiles.length;i+=8){
  await Promise.all(javascriptFiles.slice(i,i+8).map(async f=>{
   try{
    const safePath=f.filename.split('/').map(encodeURIComponent).join('/');
    const payload=await get('https://api.github.com/repos/'+repo+'/contents/'+
     safePath+'?ref='+sha,token);
    if(payload.type==='file'&&payload.encoding==='base64'&&
       Number.isSafeInteger(payload.size)&&payload.size<=160000&&
       typeof payload.content==='string'){
     const data=Buffer.from(payload.content,'base64');
     if(data.length===payload.size)f.fullSource=data.toString('utf8');
    }
   }catch(error){ /* Missing exact-SHA source fails closed in scan(). */ }
  }));
 }
 const result=scan(files,sha);
 const after=await get(base,token);
 if(after.state!=='open'||after.head?.sha!==sha)throw Error('Stale PR analysis');
 return {repo,pr,...result};
}
if(require.main===module){
 reviewRemote().then(report=>{
  console.log(JSON.stringify(report,null,2));
  if(report.status==='BLOCKED')process.exitCode=1;
 }).catch(error=>{console.error('Sentinel Self-Review: '+String(error.message).slice(0,220));process.exitCode=2});
}
module.exports={SCOPE,REQUIRED,parseAdded,scan,reviewRemote};
