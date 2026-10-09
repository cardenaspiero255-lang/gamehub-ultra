'use strict';
/* Read-only full-source workflow audit. Inputs MUST come from GitHub's exact
 * PR HEAD SHA. Signals are review hypotheses, not proven exploits. */
const SHA=/^[a-f0-9]{40}$/i;
const WORKFLOW=/^\.github\/workflows\/[A-Za-z0-9_.-]+\.ya?ml$/;
const MAX_FILES=25,MAX_SOURCE=160000,MAX_ALERTS=40;
function reviewWorkflows({sha,expected,sources={}}={}){
 const findings=[],seen=new Set(),coverage={requested:0,scanned:0,partial:false};
 const output=status=>({schema:'ultra-sentinel-workflow-audit/v1',
  sha:SHA.test(sha||'')?sha.toLowerCase():null,status,coverage,findings:findings.slice(0,MAX_ALERTS),
  autoApproveAllowed:false,autoMergeAllowed:false});
 if(!SHA.test(sha||'')||!Array.isArray(expected)||!sources||
   typeof sources!=='object'||Array.isArray(sources)){
  coverage.partial=true;
  return output('INCOMPLETE');
 }
 const names=[...new Set(expected)];
 coverage.requested=names.length;
 if(names.length>MAX_FILES||names.some(n=>typeof n!=='string'||!WORKFLOW.test(n)||
   n.includes('..'))){coverage.partial=true;return output('INCOMPLETE');}
 if(!names.length)return output('NOT_APPLICABLE');
 const flag=(rule,severity,path,line)=>{
  const key=path+'#'+rule+'#'+line;
  if(seen.has(key)||findings.length>=MAX_ALERTS)return;
  seen.add(key);
  findings.push({rule,severity,path,line,confidence:'HEURISTIC',
   verification:'Inspect trusted full workflow and reproduce the path before concluding.',
   status:'NEEDS_HUMAN_VERIFICATION'});
 };
 for(const name of names){
  const code=sources[name];
  if(typeof code!=='string'||code.length>MAX_SOURCE||!code.trim()){
   coverage.partial=true;continue;
  }
  coverage.scanned++;
  const lines=code.split('\n');
  const hasPrivilegedTrigger=lines.some(l=>/^\s*(?:pull_request_target|workflow_run)\s*:/.test(l));
  const hasCheckout=lines.some(l=>/^\s*(?:-\s*)?uses:\s*actions\/checkout@/.test(l));
  for(let i=0;i<lines.length;i++){
   const line=lines[i],trim=line.trim();
   if(!trim||trim.startsWith('#'))continue;
   const uses=line.match(/^\s*(?:-\s*)?uses:\s*([^\s#]+)/);
   if(uses&&!uses[1].startsWith('./')){
    const action=uses[1];
    if(!/^[-A-Za-z0-9_.\/]+@[a-f0-9]{40}$/i.test(action))
      flag('UNPINNED_ACTION','HIGH',name,i+1);
   }
   if(/^\s*(?:contents|actions|issues|pull-requests|packages|deployments|id-token):\s*write(?:\s*(?:#.*)?)?$/.test(line))
     flag('PRIVILEGED_WRITE_TOKEN','HIGH',name,i+1);
   if(/\bcurl\b.{0,240}\|\s*(?:bash|sh)(?:\s|$)/.test(trim)||
      /\bwget\b.{0,240}\|\s*(?:bash|sh)(?:\s|$)/.test(trim))
     flag('REMOTE_SHELL_PIPELINE','HIGH',name,i+1);
   if(hasPrivilegedTrigger&&hasCheckout&&
     /^\s*ref:\s*\$\{\{\s*(?:github\.event\.pull_request\.head\.(?:sha|ref)|github\.head_ref)\s*\}\}/.test(line))
     flag('PRIVILEGED_PR_CODE_CHECKOUT','BLOCKER',name,i+1);
  }
 }
 if(findings.length>=MAX_ALERTS)coverage.partial=true;
 return output(coverage.partial?'INCOMPLETE':findings.length?'REVIEW_REQUIRED':'NO_RISK_PATTERN');
}
module.exports={reviewWorkflows};
