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
function scan(files,sha){
 const findings=[],changed=[];
 function flag(rule,severity,file,line,reason){findings.push({rule,severity,file,line,reason})}
 if(!/^[a-f0-9]{40}$/i.test(String(sha||''))||!Array.isArray(files)||files.length>250)
  return {status:'BLOCKED',findings:[{rule:'INVALID_OR_UNBOUNDED_INPUT',severity:'BLOCKER'}],filesReviewed:0,requiresHuman:true,autoMergeAllowed:false};
 for(const f of files){
  const file=String(f?.filename||'');
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
  for(const a of additions){
   const line=a.text.trim();
   if(file.includes('/workflows/')&&/^ref:\s*/i.test(line)&&
    /\$\{\{\s*(?:github\.event\.pull_request\.head\.|github\.head_ref|github\.event\.workflow_run\.head_sha)/i.test(line))
    flag('UNTRUSTED_CHECKOUT','BLOCKER',file,a.line,'Unsafe head checkout may execute PR code');
   if(file.includes('/workflows/')&&/^(?:permissions:\s*write-all|contents:\s*write|actions:\s*write)$/i.test(line))
    flag('WORKFLOW_PRIVILEGE','BLOCKER',file,a.line,'Unexpected new workflow write permission');
   if(file.includes('/workflows/')&&/^uses:\s*[\w.-]+\/[\w.-]+@(?:main|master|v\d+(?:\.\d+)*|latest)\s*$/i.test(line))
    flag('MUTABLE_ACTION','BLOCKER',file,a.line,'New action is not pinned to a commit');
   if(file.endsWith('.cjs')&&/(?:\beval\s*\(|\bnew\s+Function\s*\(|\bvm\.runIn(?:This|New)Context\s*\()/.test(line))
    flag('DYNAMIC_EVAL','BLOCKER',file,a.line,'Dynamic execution added to reviewer');
   if(file.endsWith('.cjs')&&/\bauto(?:Merge|Commit)Allowed:\s*true\b/.test(line))
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
