'use strict';
/* Read-only, bounded workflow-source heuristics. Workflow and PR data are
 * untrusted. Findings require human verification; never run PR source. */
const SHA=/^[a-f0-9]{40}$/i;
const WORKFLOW=/^\.github\/workflows\/[A-Za-z0-9_.-]+\.ya?ml$/;
const MAX_FILES=25,MAX_SOURCE=160000,MAX_ALERTS=40;
const WRITABLE=new Set(['actions','attestations','checks','contents','deployments','discussions',
 'environments','id-token','issues','models','packages','pages','pull-requests',
 'security-events','statuses','repository-projects','members','administration','workflows']);
const scalar=v=>{
 let s=String(v??'').trim().replace(/\s+#.*$/,'').trim();
 if(s.length>=2&&((s[0]==='"'&&s.at(-1)==='"')||(s[0]==="'"&&s.at(-1)==="'")))s=s.slice(1,-1);
 return s.trim();
};
const keyValue=line=>{
 const m=String(line).match(/^\s*(?:"([^"]+)"|'([^']+)'|([a-zA-Z][\w-]*))\s*:\s*(.*)$/);
 return m?{key:m[1]||m[2]||m[3],value:m[4]}:null;
};
const privilegedEvent=/^(pull_request_target|workflow_run)$/;
function privilegedTrigger(lines){
 let start=-1,rest='';
 for(let i=0;i<lines.length;i++){
  const m=keyValue(lines[i]);
  if(m?.key==='on'&&!/^\s/.test(lines[i])){start=i;rest=m.value;break;}
 }
 if(start<0)return false;
 if(rest.trim()){
  const tokens=rest.replace(/[\[\]{},]/g,' ').trim().split(/\s+/).map(scalar);
  if(tokens.some(x=>privilegedEvent.test(x.replace(/:$/,''))))return true;
  // Inline YAML map includes quoted keys, e.g. on: {"pull_request_target": {}}
  return /(?:^|[\s,{])(?:"(?:pull_request_target|workflow_run)"|'(?:pull_request_target|workflow_run)'|(?:pull_request_target|workflow_run))\s*:/.test(rest);
 }
 for(let i=start+1;i<lines.length;i++){
  if(lines[i].trim()&&!/^\s/.test(lines[i]))break;
  const kv=keyValue(lines[i]);
  if(kv&&privilegedEvent.test(kv.key))return true;
 }
 return false;
}
function stepRange(lines,useIndex){
 const indent=lines[useIndex].match(/^\s*/)[0].length;
 let start=useIndex;
 for(let i=useIndex;i>=0;i--){
  const match=lines[i].match(/^(\s*)-\s+(?:name|uses|id|run)\s*:/);
  if(match&&match[1].length<=indent){start=i;break;}
 }
 let end=lines.length;
 const startIndent=lines[start].match(/^\s*/)[0].length;
 for(let i=useIndex+1;i<lines.length;i++){
  const match=lines[i].match(/^(\s*)-\s+(?:name|uses|id|run)\s*:/);
  if(match&&match[1].length<=startIndent){end=i;break;}
  if(lines[i].trim()&&lines[i].match(/^\s*/)[0].length<startIndent){end=i;break;}
 }
 return {start,end};
}
function isUnsafePrRef(input){
 const value=scalar(input);
 if(!value.startsWith('$'+'{{')||!value.endsWith('}}'))return false;
 const expression=value.slice(3,-2).trim();
 return /^(?:github\.event\.pull_request\.head\.(?:sha|ref)|github\.head_ref)$/.test(expression);
}
function dangerousCheckoutRefs(lines,useIndex){
 const {end}=stepRange(lines,useIndex);
 let withIndent=null;
 const out=[];
 for(let i=useIndex+1;i<end;i++){
  const line=lines[i],indent=line.match(/^\s*/)[0].length;
  const kv=keyValue(line);
  if(withIndent!==null&&line.trim()&&indent<=withIndent)withIndent=null;
  if(kv?.key==='with'){
   withIndent=indent;
   // YAML allows flow-style mappings (with: {ref: "expression"}).
   // Treat only the checkout step's own mapping as a source reference.
   const inline=String(kv.value||'').trim();
   const mapped=inline.match(/^\{\s*(?:"ref"|'ref'|ref)\s*:\s*(.*?)\s*\}$/);
   if(mapped&&isUnsafePrRef(mapped[1]))out.push(i+1);
   continue;
  }
  if(kv?.key==='ref'&&withIndent!==null&&indent>withIndent){
   let ref=String(kv.value||'').trim();
   // Folded/literal YAML scalars: the expression may be on the next line.
   // This is a conservative single-expression heuristic, not a YAML parser.
   if(/^[>|][+-]?$/.test(ref)){
    const parts=[];
    for(let j=i+1;j<end;j++){
     const row=lines[j],childIndent=row.match(/^\s*/)[0].length;
     if(row.trim()&&childIndent<=indent)break;
     if(row.trim()&&!row.trim().startsWith('#'))parts.push(row.trim());
    }
    ref=parts.join(' ');
   }
   if(isUnsafePrRef(ref))out.push(i+1);
  }
 }
 return out;
}
function writableLine(line){
 const kv=keyValue(line);
 if(kv?.key==='permissions'){
  if(scalar(kv.value)==='write-all')return true;
  if(/^\{.*\}$/.test(kv.value.trim())){
   const pairs=kv.value.trim().slice(1,-1).split(',');
   return pairs.some(p=>{
    const item=keyValue(p);
    return item&&WRITABLE.has(item.key)&&scalar(item.value)==='write';
   });
  }
 }
 return kv&&WRITABLE.has(kv.key)&&scalar(kv.value)==='write';
}
function reviewWorkflows({sha,expected,sources={}}={}){
 const findings=[],seen=new Set(),coverage={requested:0,scanned:0,partial:false};
 const output=status=>({schema:'ultra-sentinel-workflow-audit/v1',
  sha:SHA.test(sha||'')?sha.toLowerCase():null,status,coverage,findings:findings.slice(0,MAX_ALERTS),
  autoApproveAllowed:false,autoMergeAllowed:false});
 if(!SHA.test(sha||'')||!Array.isArray(expected)||!sources||
   typeof sources!=='object'||Array.isArray(sources)){
  coverage.partial=true;return output('INCOMPLETE');
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
  const lines=code.split('\n'),privileged=privilegedTrigger(lines);
  for(let i=0;i<lines.length;i++){
   const line=lines[i],trim=line.trim();
   if(!trim||trim.startsWith('#'))continue;
   const uses=line.match(/^\s*(?:-\s*)?uses:\s*(?:"([^"]+)"|'([^']+)'|([^\s#]+))/);
   if(uses){
    const action=uses[1]||uses[2]||uses[3];
    if(!action.startsWith('./')&&!/^[-A-Za-z0-9_.\/]+@[a-f0-9]{40}$/i.test(action))
      flag('UNPINNED_ACTION','HIGH',name,i+1);
    if(privileged&&/^actions\/checkout@/i.test(action)){
     for(const lineNumber of dangerousCheckoutRefs(lines,i))
      flag('PRIVILEGED_PR_CODE_CHECKOUT','BLOCKER',name,lineNumber);
    }
   }
   if(writableLine(line))flag('PRIVILEGED_WRITE_TOKEN','HIGH',name,i+1);
   if(/\b(?:curl|wget)\b.{0,240}\|\s*(?:bash|sh)(?:\s|$)/.test(trim))
    flag('REMOTE_SHELL_PIPELINE','HIGH',name,i+1);
  }
 }
 if(findings.length>=MAX_ALERTS)coverage.partial=true;
 return output(coverage.partial?'INCOMPLETE':findings.length?'REVIEW_REQUIRED':'NO_RISK_PATTERN');
}
module.exports={reviewWorkflows};
