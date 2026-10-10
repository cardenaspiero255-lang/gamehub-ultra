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

// Read-only bounded JS lexer. Preserve comment state across added/context
// lines inside each diff hunk; strings are data, not executable statements.
// Multiline templates are conservatively treated as unknown.
const JS_BACKTICK=String.fromCharCode(96);

// Treat regex character classes as regex data, not line comments. Nested
// templates get inspected only inside executable interpolation expressions.

// A regex can occur after an operator or a control-flow keyword. Read the
// entire literal (including bracket classes) before interpreting // or }.
// Unknown/unclosed constructs are not certified as safe.
function expectsJsRegex(prefix){
 const p=prefix.trimEnd();
 return p===''||/[=({[:,!?;+\-*%&|^~<>/]$/.test(p)||
  /\b(?:return|throw|case|yield|await|void|delete|typeof|instanceof|in)$/.test(p);
}
function readJsRegexEnd(source,start){
 let inClass=false,i=start+1;
 for(;i<source.length;i++){
  const ch=source[i];
  if(ch==='\\'){i++;continue;}
  if(ch==='['){inClass=true;continue;}
  if(ch===']'){inClass=false;continue;}
  if(ch==='/'&&!inClass){
   i++;
   while(i<source.length&&/[a-z]/i.test(source[i]))i++;
   return i;
  }
 }
 return -1;
}
function findTemplateExpressionEnd(body,start,state){
 let i=start+2,nesting=1,code='';
 while(i<body.length){
  const ch=body[i];
  if(body.startsWith('/*',i)){
   const end=body.indexOf('*/',i+2);
   if(end<0){state.unknown=true;return -1;}
   code+=' ';i=end+2;continue;
  }
  if(body.startsWith('//',i)){
   const end=body.indexOf('\n',i+2);
   if(end<0){state.unknown=true;return -1;}
   code+=' ';i=end+1;continue;
  }
  if(ch==='/'&&expectsJsRegex(code)){
   const end=readJsRegexEnd(body,i);
   if(end<0){state.unknown=true;return -1;}
   code+=' ';i=end;continue;
  }
  if(ch==="'"||ch==='"'){
   const quote=ch;let end=i+1,closed=false;
   while(end<body.length){
    if(body[end]==='\\'){end+=2;continue;}
    if(body[end++]===quote){closed=true;break;}
   }
   if(!closed){state.unknown=true;return -1;}
   code+=' ';i=end;continue;
  }
  if(ch===JS_BACKTICK){
   // A nested template requires a full JS grammar to disambiguate safely.
   state.unknown=true;return -1;
  }
  if(ch==='{')nesting++;
  if(ch==='}'&&--nesting===0)return i;
  code+=ch;i++;
 }
 state.unknown=true;return -1;
}
function templateExpressionsHaveSink(body,state,depth){
 if(depth>4){state.unknown=true;return;}
 let cursor=0;
 while(cursor<body.length){
  const start=body.indexOf('$'+'{',cursor);
  if(start<0)return;
  const end=findTemplateExpressionEnd(body,start,state);
  if(end<0)return;
  const expression=body.slice(start+2,end);
  const nested={block:false,unknown:false,danger:false};
  const code=jsExecutableLine(expression,nested,depth+1);
  if(nested.unknown||nested.block)state.unknown=true;
  if(nested.danger||DYNAMIC_JS_SINK.test(code))state.danger=true;
  cursor=end+1;
 }
}
function jsExecutableLine(line,state,depth=0){
 let code='',i=0;
 while(i<line.length){
  if(state.block){
   const end=line.indexOf('*/',i);
   if(end<0)return code;
   state.block=false;code+=' ';i=end+2;continue;
  }
  if(line[i]==='/'&&line[i+1]!=='/'&&line[i+1]!=='*'&&
     expectsJsRegex(code)){
   const end=readJsRegexEnd(line,i);
   if(end<0){state.unknown=true;break;}
   i=end;code+=' ';continue;
  }
  if(line.startsWith('//',i))break;
  if(line.startsWith('/*',i)){
   state.block=true;code+=' ';i+=2;continue;
  }
  const quote=line[i];
  if(quote==="'"||quote==='"'||quote===JS_BACKTICK){
   i++;let body='',closed=false;
   while(i<line.length){
    const ch=line[i++];
    if(ch==='\\'){i++;continue;}
    if(ch===quote){closed=true;break;}
    body+=ch;
   }
   if(!closed)state.unknown=true;
   if(quote===JS_BACKTICK&&body.includes('$'+'{')){
    code+=' __SENTINEL_DYNAMIC_TEMPLATE__ ';
    templateExpressionsHaveSink(body,state,depth);
   }else code+=' ';
   continue;
  }
  code+=line[i++];
 }
 return code;
}
function jsAddedExecutableByLine(patch,fullSource){
 const found=new Map();
 const source=typeof fullSource==='string'&&Buffer.byteLength(fullSource,'utf8')<=160000?
  fullSource.split(/\r?\n/):null;
 let line=0,active=false,state=null,prior='';
 for(const row of patch.split('\n')){
  const h=row.match(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/);
  if(h){
   line=+h[1];active=true;prior='';
   state={block:false,unknown:false,danger:false,contextUnknown:false};
   if(source){
    for(let n=0;n<line-1;n++){
     const scanned=jsExecutableLine(source[n]||'',state);
     if(scanned.trim())prior=(prior+' '+scanned).slice(-384);
    }
    state.danger=false;
   }else if(line>1)state.contextUnknown=true;
   continue;
  }
  if(!active||row.startsWith('+++')||row.startsWith('---')||row.startsWith('\\'))continue;
  if(row.startsWith('+')||row.startsWith(' ')){
   const added=row[0]==='+';
   const before=state.danger,previousSink=DYNAMIC_JS_SINK.test(prior);
   const code=jsExecutableLine(row.slice(1),state);
   const together=code.trim()?(prior+' '+code).slice(-768):prior;
   if(added)found.set(line,{
    sink:DYNAMIC_JS_SINK.test(together)&&(!previousSink||DYNAMIC_JS_SINK.test(code)),
    danger:state.danger&&!before,
    unknown:state.unknown||state.contextUnknown
   });
   prior=together;
   line++;
  }
 }
 return found;
}
const DYNAMIC_JS_SINK=/(?:\beval\s*\(|\bnew\s+Function\s*\(|\bvm\s*\.\s*runIn(?:This|New)Context\s*\(|\b(?:globalThis|global)\s*\[\s*__SENTINEL_DYNAMIC_TEMPLATE__\s*\]\s*\()/;
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
  const executableByLine=inspectJs?jsAddedExecutableByLine(f.patch,f.fullSource):null;
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
    flag('JS_CONTEXT_INCOMPLETE','BLOCKER',file,a.line,'Cannot attest JavaScript lexical context');
   else if(inspectJs&&(js?.danger||js?.sink))
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
