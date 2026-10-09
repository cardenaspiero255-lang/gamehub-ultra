'use strict';
/* Read-only, bounded workflow-source heuristics. Workflow and PR data are
 * untrusted. Findings require human verification; never run PR source. */
const SHA=/^[a-f0-9]{40}$/i;
const WORKFLOW=/^\.github\/workflows\/[A-Za-z0-9_.-]+\.ya?ml$/;
const MAX_FILES=25,MAX_SOURCE=160000,MAX_ALERTS=40;
const WRITABLE=new Set(['actions','attestations','checks','contents','deployments','discussions',
 'environments','id-token','issues','models','packages','pages','pull-requests',
 'security-events','statuses','artifact-metadata','code-quality',
 'repository-projects','members','administration','workflows']);
const scalar=v=>{
 let s=String(v??'').trim().replace(/\s+#.*$/,'').trim();
 if(s.length>=2&&((s[0]==='"'&&s.at(-1)==='"')||(s[0]==="'"&&s.at(-1)==="'")))s=s.slice(1,-1);
 return s.trim();
};
const keyValue=line=>{
 const m=String(line).match(/^\s*(?:"([^"]+)"|'([^']+)'|([a-zA-Z][\w-]*))\s*:\s*([\s\S]*)$/);
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
  if(/^[>|][+-]?$/.test(rest.trim())){
   const parts=[];
   for(let j=start+1;j<Math.min(lines.length,start+65);j++){
    if(lines[j].trim()&&!/^\s/.test(lines[j]))break;
    if(lines[j].trim()&&!lines[j].trim().startsWith('#'))parts.push(lines[j].trim());
   }
   const folded=parts.join(' ');
   return /(?:^|[\s,])(?:pull_request_target|workflow_run)(?:\s|$)/.test(folded);
  }
  // GitHub Actions accepts block-flow sequences and mappings:
  // on: [<newline> pull_request_target, <newline> push].
  // Read only this bounded YAML value; never interpret arbitrary job text as an event.
  let value=rest;
  const open=rest.trim()[0];
  const close=open==='['?']':open==='{'?'}':null;
  if(close&&!rest.includes(close)){
   for(let j=start+1;j<Math.min(lines.length,start+65);j++){
    if(lines[j].trim()&&!/^\s/.test(lines[j])&&lines[j].trim()!==close)break;
    value+=' '+lines[j].trim();
    if(lines[j].includes(close))break;
   }
  }
  const tokens=value.replace(/[\[\]{},]/g,' ').trim().split(/\s+/).map(scalar);
  if(tokens.some(x=>privilegedEvent.test(x.replace(/:$/,''))))return true;
  return /(?:^|[\s,{])(?:"(?:pull_request_target|workflow_run)"|'(?:pull_request_target|workflow_run)'|(?:pull_request_target|workflow_run))\s*:/.test(value);
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
  const match=lines[i].match(/^(\s*)-\s+(?:"[^"]+"|'[^']+'|[A-Za-z][\w-]*)\s*:/);
  if(match&&match[1].length<=indent){start=i;break;}
 }
 let end=lines.length;
 const startIndent=lines[start].match(/^\s*/)[0].length;
 for(let i=useIndex+1;i<lines.length;i++){
  const match=lines[i].match(/^(\s*)-\s+(?:"[^"]+"|'[^']+'|[A-Za-z][\w-]*)\s*:/);
  if(match&&match[1].length<=startIndent){end=i;break;}
  if(lines[i].trim()&&lines[i].match(/^\s*/)[0].length<startIndent){end=i;break;}
 }
 return {start,end};
}
function isUnsafePrRef(input){
 const value=scalar(input);
 const expressions=[...value.matchAll(/\$\{\{\s*([\s\S]*?)\s*\}\}/g)];
 return expressions.some(m=>{
  const expression=m[1].replace(/\[\s*(['"])([A-Za-z_][A-Za-z0-9_]*)\1\s*\]/g,'.$2');
  return /\bgithub\.(?:event\.pull_request\.head\.(?:sha|ref)|head_ref)\b/.test(expression);
 });
}
function splitFlowEntries(content){
 const chunks=[];let quote=null,escaped=false,depth=0,begin=0;
 for(let i=0;i<content.length;i++){
  const c=content[i];
  if(quote){
   if(escaped){escaped=false;continue;}
   if(c==='\\'){escaped=true;continue;}
   if(c===quote)quote=null;
   continue;
  }
  if(c==='"'||c==="'"){quote=c;continue;}
  if(c==='{')depth++;
  else if(c==='}')depth=Math.max(0,depth-1);
  else if(c===','&&depth===0){chunks.push(content.slice(begin,i));begin=i+1;}
 }
 chunks.push(content.slice(begin));
 return chunks;
}
function dangerousCheckoutRefs(lines,useIndex){
 const {start,end}=stepRange(lines,useIndex);
 let withIndent=null;
 const out=[];
 for(let i=start+1;i<end;i++){
  const line=lines[i],indent=line.match(/^\s*/)[0].length;
  const kv=keyValue(line);
  if(withIndent!==null&&line.trim()&&indent<=withIndent)withIndent=null;
  if(kv?.key==='with'){
   withIndent=indent;
   // YAML allows flow-style mappings (with: {ref: "expression"}).
   // Treat only the checkout step's own mapping as a source reference.
   const inline=String(kv.value||'').trim();
   const flow=inline.match(/^\{([\s\S]*)\}$/);
   if(flow){
    for(const entry of splitFlowEntries(flow[1])){
     const kvRef=keyValue(entry);
     if(kvRef?.key==='ref'&&isUnsafePrRef(kvRef.value))out.push(i+1);
    }
   }
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
 return kv&&WRITABLE.has(kv.key)&&scalar(String(kv.value).replace(/,\s*$/,'').trim())==='write';
}
function inRealPermissionsMap(lines,index){
 const indentation=lines[index].match(/^\s*/)[0].length;
 if(indentation===0)return true;
 let current=indentation;
 const parents=[];
 for(let j=index-1;j>=0;j--){
  const line=lines[j],trim=line.trim();
  if(!trim||trim.startsWith('#'))continue;
  const prior=line.match(/^\s*/)[0].length;
  if(prior>=current)continue;
  current=prior;
  const kv=keyValue(line);
  if(kv)parents.push(kv.key);
  if(prior===0)break;
 }
 // Permissions are valid at the workflow root and inside a job, never
 // inside an action's with, environment variables, or workflow inputs.
 return parents.includes('jobs')&&
  !parents.some(p=>['steps','with','env','inputs','strategy','services','defaults'].includes(p));
}
// Read actions only from jobs.<job>.steps sequence items. Looking for "uses:"
 // anywhere in YAML also matches unrelated environment variables and run scripts.
function jobStepRanges(lines){
 const ranges=[];
 for(let i=0;i<lines.length;i++){
  const kv=keyValue(lines[i]);
  if(kv?.key!=='steps'||kv.value.trim()||!inRealPermissionsMap(lines,i))continue;
  const parentIndent=lines[i].match(/^\s*/)[0].length,heads=[];
  let itemIndent=null;
  for(let j=i+1;j<lines.length;j++){
   const ln=lines[j],trim=ln.trim(),indent=ln.match(/^\s*/)[0].length;
   if(!trim||trim.startsWith('#'))continue;
   if(indent<=parentIndent)break;
   if(/^\s*-\s+/.test(ln)){
    if(itemIndent===null)itemIndent=indent;
    if(indent===itemIndent)heads.push(j);
   }
  }
  for(let j=0;j<heads.length;j++){
   let end=lines.length;
   if(j+1<heads.length)end=heads[j+1];
   else for(let k=heads[j]+1;k<lines.length;k++){
    if(lines[k].trim()&&lines[k].match(/^\s*/)[0].length<=parentIndent){end=k;break;}
   }
   ranges.push({start:heads[j],end});
  }
 }
 return ranges;
}
function refInFlowValue(value){
 const mapped=String(value||'').trim().match(/^\{([\s\S]*)\}$/);
 if(!mapped)return false;
 for(const entry of splitFlowEntries(mapped[1])){
  const kv=keyValue(entry.trim());
  if(kv?.key==='ref'&&isUnsafePrRef(kv.value))return true;
 }
 return false;
}
function stepAction(lines,step){
 const {start,end}=step,itemIndent=lines[start].match(/^\s*/)[0].length;
 const original=lines[start].replace(/^\s*-\s*/,'');
 let action=null,actionLine=null;
 const dangerous=[];
 // Flow-style steps can span several physical YAML lines. Build the exact
 // nested-brace mapping before parsing; quotes protect embedded expressions.
 let flowText=original.trim();
 if(flowText.startsWith('{')){
  let quote=null,escape=false,depth=0,closed=false,parts=[];
  for(const character of lines.slice(start,end).map((line,k)=>
    (k===0?line.replace(/^\s*-\s*/,''):line.trim())).join('\n')){
   parts.push(character);
   if(quote){
    if(escape)escape=false;
    else if(character==='\\')escape=true;
    else if(character===quote)quote=null;
    continue;
   }
   if(character==='"'||character==="'"){quote=character;continue;}
   if(character==='{')depth++;
   else if(character==='}'){
    depth--;
    if(depth===0){closed=true;break;}
   }
  }
  flowText=closed?parts.join(''):'';
 }
 const flow=flowText.match(/^\{([\s\S]*)\}\s*(?:#.*)?$/);
 if(flow){
  for(const entry of splitFlowEntries(flow[1])){
   const kv=keyValue(entry.trim());
   if(kv?.key==='uses'){action=scalar(kv.value);actionLine=start+1;}
   if(kv?.key==='with'&&refInFlowValue(kv.value))dangerous.push(start+1);
  }
  return {action,actionLine,dangerous};
 }
 const direct=itemIndent+2;
 let withStart=null,withEnd=null,withValue=null,withLine=null;
 const first=keyValue(original);
 if(first?.key==='uses'){action=scalar(first.value);actionLine=start+1;}
 if(first?.key==='with'){withStart=start;withValue=first.value;withLine=start+1;}
 for(let i=start+1;i<end;i++){
  const line=lines[i],indent=line.match(/^\s*/)[0].length;
  if(indent!==direct)continue;
  const kv=keyValue(line);
  if(!kv)continue;
  if(kv.key==='uses'){action=scalar(kv.value);actionLine=i+1;}
  if(kv.key==='with'){withStart=i;withEnd=null;withValue=kv.value;withLine=i+1;}
  else if(withStart!==null&&withEnd===null)withEnd=i;
 }
 if(withStart===null)return {action,actionLine,dangerous};
 if(refInFlowValue(withValue))dangerous.push(withLine);
 const stop=withEnd??end;
 for(let i=withStart+1;i<stop;i++){
  const line=lines[i],indent=line.match(/^\s*/)[0].length;
  if(indent<=direct)continue;
  const kv=keyValue(line);
  if(kv?.key!=='ref')continue;
  let value=String(kv.value||'').trim();
  if(/^[>|][+-]?$/.test(value)){
   const sub=[];
   for(let k=i+1;k<stop;k++){
    const r=lines[k],dep=r.match(/^\s*/)[0].length;
    if(r.trim()&&dep<=indent)break;
    if(r.trim()&&!r.trim().startsWith('#'))sub.push(r.trim());
   }
   value=sub.join(' ');
  }
  if(isUnsafePrRef(value))dangerous.push(i+1);
 }
 return {action,actionLine,dangerous};
}
// YAML aliases in security-sensitive values are not trustworthy without a
// complete YAML parser. Treat them as INCOMPLETE rather than silently clear.
// Only scope aliases inside on/permissions mappings or checkout inputs,
// never arbitrary labels, descriptions, comments or action names.
function hasSensitiveAliases(lines){
 let scope=null;
 for(let i=0;i<lines.length;i++){
  const row=lines[i],trim=row.trim();
  if(!trim||trim.startsWith('#'))continue;
  const indent=row.match(/^\s*/)[0].length,kv=keyValue(row);
  if(scope!==null&&indent<=scope.indent)scope=null;
  const rootEvent=indent===0&&kv?.key==='on';
  const permissions=kv?.key==='permissions'&&inRealPermissionsMap(lines,i);
  const input=kv&&['uses','with','ref'].includes(kv.key)&&
   jobStepRanges(lines).some(s=>s.start<=i&&i<s.end);
  if(rootEvent||permissions)scope={indent};
  const examined=(rootEvent||permissions||input)?kv.value:
   scope&&indent>scope.indent?trim:'';
  // A leading *alias or an alias nested in a flow sequence/map is
  // intentionally unresolved and must fail closed, including quoted aliases.
  if(/(?:^|[\s,[{,:])\*[-A-Za-z0-9_]+(?:\b|$)/.test(examined))return true;
 }
 return false;
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
  if(hasSensitiveAliases(lines))coverage.partial=true;
  for(const step of jobStepRanges(lines)){
   const details=stepAction(lines,step);
   const action=details.action;
   if(!action)continue;
   if(!action.startsWith('./')&&!/^[-A-Za-z0-9_.\/]+@[a-f0-9]{40}$/i.test(action))
    flag('UNPINNED_ACTION','HIGH',name,details.actionLine);
   if(privileged&&/^actions\/checkout@/i.test(action))
    for(const lineNumber of details.dangerous)
     flag('PRIVILEGED_PR_CODE_CHECKOUT','BLOCKER',name,lineNumber);
  }
  let permissionsIndent=null;
  for(let i=0;i<lines.length;i++){
   const line=lines[i],trim=line.trim();
   if(!trim||trim.startsWith('#'))continue;
   const indent=line.match(/^\s*/)[0].length;
   if(permissionsIndent!==null&&indent<=permissionsIndent)permissionsIndent=null;
   const permissionKey=keyValue(line);
   if(permissionKey?.key==='permissions'&&inRealPermissionsMap(lines,i)){
    if(writableLine(line))flag('PRIVILEGED_WRITE_TOKEN','HIGH',name,i+1);
    if(/^[>|][+-]?$/.test(permissionKey.value.trim())){
     const items=[];
     for(let k=i+1;k<Math.min(lines.length,i+65);k++){
      const child=lines[k],childIndent=child.match(/^\s*/)[0].length;
      if(child.trim()&&childIndent<=indent)break;
      if(child.trim()&&!child.trim().startsWith('#'))items.push(child.trim());
     }
     if(items.join(' ').trim()==='write-all')
      flag('PRIVILEGED_WRITE_TOKEN','HIGH',name,i+1);
    }
    if(!permissionKey.value.trim()||
       (permissionKey.value.trim().startsWith('{')&&!permissionKey.value.includes('}')))
      permissionsIndent=indent;
   }else if(permissionsIndent!==null&&indent>permissionsIndent&&writableLine(line)){
    flag('PRIVILEGED_WRITE_TOKEN','HIGH',name,i+1);
   }
   if(/\b(?:curl|wget)\b.{0,240}\|\s*(?:bash|sh)(?:\s|$)/.test(trim))
    flag('REMOTE_SHELL_PIPELINE','HIGH',name,i+1);
  }
 }
 if(findings.length>=MAX_ALERTS)coverage.partial=true;
 return output(coverage.partial?'INCOMPLETE':findings.length?'REVIEW_REQUIRED':'NO_RISK_PATTERN');
}
module.exports={reviewWorkflows};
