'use strict';
/* Fast opt-in staged-code guard. Pure parser also used for tests.
 * 0-network; does not execute code under review; defaults to warn on heuristic risks.
 */
const {spawnSync}=require('node:child_process');
const core=require('./ultra_sentinel_core.cjs');
const MAX_BYTES=260000,MAX_FILES=80;
function parseGitDiff(raw){
 if(typeof raw!=='string'||raw.length>MAX_BYTES)throw Error('Staged diff too large; run GitHub CI.');
 const groups=[];let current=null,body=[];
 function finish(){
  if(!current)return;
  groups.push({filename:current,patch:body.join('\n'),changes:body.filter(x=>x.startsWith('+')&&!x.startsWith('+++')).length});
 }
 for(const line of raw.split('\n')){
  if(line.startsWith('diff --git ')){finish();current=null;body=[];continue;}
  if(line.startsWith('+++ b/')){current=line.slice(6);continue;}
  if(current&&(line.startsWith('@@')||body.length))body.push(line);
 }
 finish();
 if(groups.length>MAX_FILES)throw Error('Too many staged files; run CI.');
 return groups;
}
function inspect(diff){
 const files=parseGitDiff(diff);
 const result=core.analyze(files);
 const fatal=result.findings.filter(f=>f.severity==='BLOCKER'&&f.rule!=='REGRESSION_TEST_COVERAGE');
 return {files:files.length,alerts:result.findings,blockers:fatal,
  provisional:result.coverage.partial,note:'Fast static staged diff; no AST, Kotlin compilation or runtime checks.'};
}
function main(env=process.env,runGit=spawnSync){
 const strict=env.SENTINEL_PRECOMMIT_STRICT==='1';
 const args=['diff','--cached','--no-ext-diff','--no-color','--unified=3','--','app/src/main/','.github/workflows/','.github/scripts/'];
 const output=runGit('git',args,{encoding:'utf8',maxBuffer:MAX_BYTES+30000,timeout:8000,windowsHide:true});
 if(output.error||output.status!==0){console.error('Sentinel: git diff unavailable; review manually and run CI.');return strict?2:0}
 let report;try{report=inspect(output.stdout)}catch(e){console.error('Sentinel: '+e.message);return strict?2:0}
 if(!report.files){console.log('Sentinel fast path: no staged production changes.');return 0}
 const n=report.alerts.length;console.log('Ultra Sentinel pre-commit: '+report.files+' changed files, '+n+' hypotheses.');
 for(const f of report.alerts.slice(0,12)){
   console.log('['+f.severity+'] '+f.rule+' '+f.path+':'+f.line+' — '+f.reason);
 }
 if(report.provisional)console.warn('Sentinel coverage incomplete: full CI required.');
 if(report.blockers.length&&strict){console.error('Strict mode: possible blocker; inspect before committing.');return 1}
 if(report.blockers.length)console.warn('Potential BLOCKER: review before push (use strict mode to block commits).');
 return 0;
}
if(require.main===module)process.exitCode=main();
module.exports={parseGitDiff,inspect,main};
