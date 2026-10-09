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
 // Decode YAML node *values* too: "\u0061ctions/checkout" is a checkout.
 // strip comments only when outside quotes; never interpret PR source code.
 const s=withoutLeadingAnchor(v);
 if(s.length>=2&&s[0]==='"'&&s.at(-1)==='"'){
  const decoded=decodeYamlKey(s.slice(1,-1));
  return decoded===null?s:decoded.trim();
 }
 if(s.length>=2&&s[0]==="'"&&s.at(-1)==="'")
  return s.slice(1,-1).replace(/''/g,"'").trim();
 return s;
};
// Only decode bounded YAML quoted-key escapes, not arbitrary expressions.
// Unknown escaped keys are marked unresolved so audit cannot report clean.
function decodeYamlKey(raw){
 const known={ '0':'\0',a:'\x07',b:'\b',t:'\t',n:'\n',v:'\v',
  f:'\f',r:'\r',e:'\x1b',' ':' ', '"':'"','/':'/','\\':'\\',
  N:'\u0085','_':'\u00a0',L:'\u2028',P:'\u2029' };
 let out='';
 for(let i=0;i<raw.length;i++){
  if(raw[i]!=='\\'){out+=raw[i];continue;}
  const escape=raw[++i];
  if(escape==='x'||escape==='u'||escape==='U'){
   const count=escape==='x'?2:escape==='u'?4:8;
   const hex=raw.slice(i+1,i+count+1);
   if(hex.length!==count||!/^[\da-fA-F]+$/.test(hex))return null;
   const code=parseInt(hex,16);
   if(code>0x10ffff||(code>=0xd800&&code<=0xdfff))return null;
   out+=String.fromCodePoint(code);
   i+=count;
  }else if(Object.prototype.hasOwnProperty.call(known,escape))out+=known[escape];
  else return null;
 }
 return out;
}
const keyValue=line=>{
 const m=String(line).match(/^\s*(?:"((?:\\.|[^"\\])*)"|'((?:''|[^'])*)'|([a-zA-Z][\w-]*))\s*:\s*([\s\S]*)$/);
 if(!m)return null;
 const key=m[1]!==undefined?decodeYamlKey(m[1]):
  m[2]!==undefined?m[2].replace(/''/g,"'"):m[3];
 return {key:key??'__UNKNOWN_QUOTED_KEY__',value:m[4],unresolved:key===null};
};

function withoutYamlComment(value){
 const s=String(value??'');
 let quote=null,escaped=false;
 for(let i=0;i<s.length;i++){
  const ch=s[i];
  if(quote){
   if(quote==='"'&&escaped){escaped=false;continue;}
   if(quote==='"'&&ch==='\\'){escaped=true;continue;}
   if(ch===quote){
    if(quote==="'"&&s[i+1]==="'"){i++;continue;}
    quote=null;
   }
  }else{
   if(ch==='"'||ch==="'"){quote=ch;continue;}
   if(ch==='#'&&(i===0||/\s/.test(s[i-1])))return s.slice(0,i);
  }
 }
 return s;
}
function withoutLeadingAnchor(value){
 return withoutYamlComment(String(value??'')
  .replace(/^\s*&[^\s[\]{},]+(?=\s|$)/,'')).trim();
}
function containsYamlAlias(value){
 const s=withoutYamlComment(value);
 let quote=null,escaped=false,code='';
 for(let i=0;i<s.length;i++){
  const ch=s[i];
  if(quote){
   code+=' ';
   if(quote==='"'&&escaped){escaped=false;continue;}
   if(quote==='"'&&ch==='\\'){escaped=true;continue;}
   if(ch===quote){
    if(quote==="'"&&s[i+1]==="'"){code+=' ';i++;continue;}
    quote=null;
   }
  }else if(ch==='"'||ch==="'"){quote=ch;code+=' ';}
  else code+=ch;
 }
 return /(?:^|[\s,[{,:])\*[-A-Za-z0-9_]+\b/.test(code);
}

function yamlRootIndent(lines){
 const mask=structuralYamlRowMask(lines);let min=Infinity;
 for(let i=0;i<lines.length;i++){
  if(!mask[i]||!keyValue(lines[i]))continue;
  min=Math.min(min,lines[i].match(/^\s*/)[0].length);
 }
 return Number.isFinite(min)?min:0;
}
const privilegedEvent=/^(pull_request_target|workflow_run)$/;
function privilegedTrigger(lines,audit={}){
 const rootIndent=yamlRootIndent(lines);
 let start=-1,rest='';
 for(let i=0;i<lines.length;i++){
  const m=keyValue(lines[i]);
  if(m?.key==='on'&&lines[i].match(/^\s*/)[0].length===rootIndent){
   start=i;rest=m.value;break;
  }
 }
 if(start<0)return false;
 rest=withoutLeadingAnchor(rest);
 if(rest.trim()){
  if(isBlockScalarHeader(rest)){
   const parts=[];
   let foldedCount=0;
   for(let j=start+1;j<lines.length;j++){
    if(lines[j].trim()&&lines[j].match(/^\s*/)[0].length<=rootIndent)break;
    if(++foldedCount>65){audit.partial=true;return false;}
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
  if(open==='['||open==='{'){
   // Exceeding the bounded flow collector is INCOMPLETE rather than safe.
   const mapping=collectFlowMap(lines,start,lines.length,rest);
   if(!mapping.closed){audit.partial=true;return false;}
   value=mapping.value;
   if(open==='{'){
    // Event names belong to the top-level map. Nested branch names and
    // event options must not be interpreted as privileged triggers.
    return splitFlowEntries(value.slice(1,-1)).some(entry=>{
     const kv=keyValue(entry.trim());
     return kv&&privilegedEvent.test(kv.key);
    });
   }
  }
  const tokens=value.replace(/[\[\]{},]/g,' ').trim().split(/\s+/).map(scalar);
  if(tokens.some(x=>privilegedEvent.test(x.replace(/:$/,''))))return true;
  return /(?:^|[\s,{])(?:"(?:pull_request_target|workflow_run)"|'(?:pull_request_target|workflow_run)'|(?:pull_request_target|workflow_run))\s*:/.test(value);
 }
 for(let i=start+1;i<lines.length;i++){
  if(lines[i].trim()&&lines[i].match(/^\s*/)[0].length<=rootIndent)break;
  const kv=keyValue(lines[i]);
  if(kv&&privilegedEvent.test(kv.key))return true;
  const entry=lines[i].match(/^\s*-\s+(.+?)\s*(?:#.*)?$/);
  if(entry&&privilegedEvent.test(scalar(entry[1])))return true;
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
 // pull_request_target must not checkout the PR head OR synthetic merge ref.
 // Both literal refs/pull/42/merge and interpolated PR numbers are untrusted.
 if(/^refs\/pull\/(?:\d+|\$\{\{[\s\S]*?\}\})\/(?:merge|head)$/i.test(value))
  return true;
 const expressions=[...value.matchAll(/\$\{\{\s*([\s\S]*?)\s*\}\}/g)];
 return expressions.some(m=>{
  const expression=m[1].replace(/\[\s*(['"])([A-Za-z_][A-Za-z0-9_]*)\1\s*\]/g,'.$2');
  return /\bgithub\.(?:event\.pull_request\.head\.(?:sha|ref|repo\.(?:full_name|name|clone_url))|event\.workflow_run\.(?:head_sha|head_branch|head_repository\.full_name)|head_ref)\b/.test(expression);
 });
}
function splitFlowEntries(content){
 const chunks=[];let quote=null,escaped=false,depth=0,begin=0;
 for(let i=0;i<content.length;i++){
  const c=content[i];
  if(quote){
   // YAML backslash escapes apply to double-quoted scalars only.
   // Single-quoted scalars escape an apostrophe by doubling it.
   if(quote==='"'&&escaped){escaped=false;continue;}
   if(quote==='"'&&c==='\\'){escaped=true;continue;}
   if(c===quote){
    if(quote==="'"&&content[i+1]==="'"){i++;continue;}
    quote=null;
   }
   continue;
  }
  if(c==='"'||c==="'"){quote=c;continue;}
  if(c==='{'||c==='[')depth++;
  else if(c==='}'||c===']')depth=Math.max(0,depth-1);
  else if(c===','&&depth===0){chunks.push(content.slice(begin,i));begin=i+1;}
 }
 chunks.push(content.slice(begin));
 return chunks;
}

// Collect bounded YAML flow maps without counting braces in comments or
// quoted strings. An unclosed map is incomplete evidence, not a clean scan.
function collectFlowMap(lines,start,end,firstValue){
 const stack=[];let opened=false,closed=false,quote=null,escaped=false;
 const parts=[];
 for(let i=start;i<Math.min(end,start+65);i++){
  const src=i===start?String(firstValue):lines[i].trim();
  let fragment='';
  for(let j=0;j<src.length;j++){
   const ch=src[j];
   if(quote){
    fragment+=ch;
    if(quote==='"'&&escaped){escaped=false;continue;}
    if(quote==='"'&&ch==='\\'){escaped=true;continue;}
    if(ch===quote){
     if(quote==="'"&&src[j+1]==="'"){fragment+=src[++j];continue;}
     quote=null;
    }
    continue;
   }
   if(ch==='"'||ch==="'"){quote=ch;fragment+=ch;continue;}
   if(ch==='#'&&(j===0||/\s/.test(src[j-1])))break;
   if(ch==='{'||ch==='['){stack.push(ch==='{'?'}':']');opened=true;}
   else if(ch==='}'||ch===']'){
    if(stack.pop()!==ch)return {value:parts.join(' '),closed:false};
   }
   fragment+=ch;
   if(opened&&stack.length===0){closed=true;break;}
  }
  if(fragment.trim())parts.push(fragment.trim());
  if(closed)break;
 }
 return {value:parts.join(' '),closed};
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
     if(['ref','repository'].includes(kvRef?.key)&&isUnsafePrRef(kvRef.value))out.push(i+1);
    }
   }
   continue;
  }
  if(['ref','repository'].includes(kv?.key)&&withIndent!==null&&indent>withIndent){
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
  const value=withoutLeadingAnchor(kv.value);
  if(scalar(value)==='write-all')return true;
  if(/^\{.*\}$/.test(value)){
   const pairs=value.slice(1,-1).split(',');
   return pairs.some(p=>{
    const item=keyValue(p);
    return item&&WRITABLE.has(item.key)&&scalar(item.value)==='write';
   });
  }
 }
 return kv&&WRITABLE.has(kv.key)&&scalar(withoutLeadingAnchor(String(kv.value).replace(/,\s*$/,'').trim()))==='write';
}
function inRealPermissionsMap(lines,index){
 const indentation=lines[index].match(/^\s*/)[0].length;
 const rootIndent=yamlRootIndent(lines);
 if(indentation===rootIndent)return true;
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
  if(prior<=rootIndent)break;
 }
 // Permissions are valid at the workflow root and inside a job, never
 // inside an action's with, environment variables, or workflow inputs.
 return parents.includes('jobs')&&
  !parents.some(p=>['steps','with','env','inputs','strategy','services','defaults'].includes(p));
}
// Read actions only from jobs.<job>.steps sequence items. Looking for "uses:"
 // anywhere in YAML also matches unrelated environment variables and run scripts.
function jobStepRanges(lines,audit={}){
 const ranges=[];
 for(let i=0;i<lines.length;i++){
  const kv=keyValue(lines[i]);
  if(kv?.key!=='steps'||!inRealPermissionsMap(lines,i))continue;
  const value=withoutLeadingAnchor(kv.value);
  if(value){
   // Aliases and unsupported inline representations cannot certify that
   // every step was inspected. [] is a known-empty sequence.
   if(value!=='[]')audit.partial=true;
   continue;
  }
  const parentIndent=lines[i].match(/^\s*/)[0].length,heads=[];
  let itemIndent=null;
  for(let j=i+1;j<lines.length;j++){
   const ln=lines[j],trim=ln.trim(),indent=ln.match(/^\s*/)[0].length;
   if(!trim||trim.startsWith('#'))continue;
   if(indent<=parentIndent)break;
   // A YAML sequence item may be a bare '-' on its own line.
   // Its 'uses' and 'with' mapping then appear on child lines.
   if(/^\s*-(?:\s+|$)/.test(ln)){
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
  if(['ref','repository'].includes(kv?.key)&&isUnsafePrRef(kv.value))return true;
 }
 return false;
}

function sensitiveFlowAlias(value){
 const source=String(value||'').trim();
 if(/^\*[-A-Za-z0-9_]+\b/.test(source))return true;
 if(!source.startsWith('{')||!source.endsWith('}'))return false;
 for(const entry of splitFlowEntries(source.slice(1,-1))){
  // YAML merge keys may inherit an untrusted action or ref at any position.
  if(/^<<\s*:/.test(entry.trim()))return true;
  const kv=keyValue(entry.trim());
  if(!kv||!['uses','ref','repository','with'].includes(kv.key))continue;
  if(sensitiveFlowAlias(kv.value))return true;
 }
 return false;
}


function isBlockScalarHeader(value){
 return /^[>|](?:(?:[+-][1-9]?)|(?:[1-9][+-]?)|[+-])?$/.test(withoutLeadingAnchor(value));
}
function readStepScalar(lines,index,end,raw,keyIndent){
 if(!isBlockScalarHeader(raw))return {value:scalar(raw),incomplete:false};
 const parts=[];let count=0;
 for(let i=index+1;i<end;i++){
  const line=lines[i],indent=line.match(/^\s*/)[0].length;
  if(line.trim()&&indent<=keyIndent)break;
  if(++count>65)return {value:null,incomplete:true};
  if(line.trim())parts.push(line.trim());
 }
 if(!parts.length)return {value:null,incomplete:true};
 return {value:parts.join(' '),incomplete:false};
}
const remoteShellPattern=/\b(?:curl|wget)\b.{0,240}\|\s*(?:bash|sh)(?:\s|["']|$)/;
function shellPipelinesInStep(lines,step){
 const {start,end}=step,itemIndent=lines[start].match(/^\s*/)[0].length;
 const original=withoutLeadingAnchor(lines[start].replace(/^\s*-\s*/,''));
 if(original.startsWith('{')){
  const map=collectFlowMap(lines,start,end,original);
  if(map.closed&&map.value.trim().endsWith('}')){
   return splitFlowEntries(map.value.trim().slice(1,-1)).some(entry=>{
    const kv=keyValue(entry.trim());
    return kv?.key==='run'&&remoteShellPattern.test(scalar(kv.value));
   })?[start+1]:[];
  }
 }
 const mask=structuralYamlRowMask(lines),matched=[];
 const childIndent=original?null:lines.slice(start+1,end)
  .find(line=>line.trim()&&!line.trim().startsWith('#')&&keyValue(line))
  ?.match(/^\s*/)[0].length;
 const direct=childIndent??itemIndent+2;
 for(let i=start;i<end;i++){
  if(!mask[i])continue;
  const isFirst=i===start;
  const indent=lines[i].match(/^\s*/)[0].length;
  if(!isFirst&&indent!==direct)continue;
  const kv=keyValue(isFirst?original:lines[i]);
  if(kv?.key!=='run')continue;
  if(isBlockScalarHeader(kv.value)){
   const folded=withoutLeadingAnchor(kv.value).startsWith('>'),script=[];
   for(let j=i+1;j<end;j++){
    const depth=lines[j].match(/^\s*/)[0].length;
    if(lines[j].trim()&&depth<=(isFirst?direct:indent))break;
    if(remoteShellPattern.test(lines[j]))matched.push(j+1);
    if(lines[j].trim())script.push({line:j+1,text:lines[j].trim()});
   }
   // YAML folded scalars join physical lines. Literal shell scripts only
   // join lines ending in a real unescaped backslash continuation.
   if(folded){
    if(remoteShellPattern.test(script.map(part=>part.text).join(' ')))
     matched.push(i+1);
   }else{
    let command='',startLine=i+1;
    for(const part of script){
     if(!command)startLine=part.line;
     command+=(command?' ':'')+part.text;
     const trailing=/\\+$/.exec(command)?.[0].length||0;
     if(trailing%2===1){
      command=command.slice(0,-1).trimEnd();
      continue;
     }
     if(remoteShellPattern.test(command))matched.push(startLine);
     command='';
    }
   }
  }else if(remoteShellPattern.test(scalar(kv.value)))matched.push(i+1);
 }
 return matched;
}

function stepAction(lines,step){
 const {start,end}=step,itemIndent=lines[start].match(/^\s*/)[0].length;
 const original=withoutLeadingAnchor(lines[start].replace(/^\s*-\s*/,''));
 let action=null,actionLine=null;
 const dangerous=[];let incomplete=false;
 // Flow-style steps can span several physical YAML lines. Build the exact
 // nested-brace mapping before parsing; quotes protect embedded expressions.
 let flowText=original.trim();
 if(flowText.startsWith('{')){
  const mapping=collectFlowMap(lines,start,end,flowText);
  if(!mapping.closed)return {action:null,actionLine:null,dangerous,incomplete:true};
  flowText=mapping.value;
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
 // A bare "-" permits any deeper indentation for the child's mapping.
 const childIndent=original.trim()===''?lines.slice(start+1,end)
  .find(ln=>ln.trim()&&!ln.trim().startsWith('#')&&
   ln.match(/^\s*/)[0].length>itemIndent&&keyValue(ln))
  ?.match(/^\s*/)[0].length:null;
 const direct=childIndent??itemIndent+2;
 let withStart=null,withEnd=null,withValue=null,withLine=null;
 const first=keyValue(original);
 if(first?.key==='uses'){
  const result=readStepScalar(lines,start,end,first.value,itemIndent+2);
  action=result.value;incomplete ||=result.incomplete;actionLine=start+1;
 }
 if(first?.key==='with'){withStart=start;withValue=first.value;withLine=start+1;}
 for(let i=start+1;i<end;i++){
  const line=lines[i],indent=line.match(/^\s*/)[0].length;
  if(indent!==direct)continue;
  const kv=keyValue(line);
  if(!kv)continue;
  if(kv.key==='uses'){
   const result=readStepScalar(lines,i,end,kv.value,indent);
   action=result.value;incomplete ||=result.incomplete;actionLine=i+1;
  }
  if(kv.key==='with'){withStart=i;withEnd=null;withValue=kv.value;withLine=i+1;}
  else if(withStart!==null&&withEnd===null)withEnd=i;
 }
 if(withStart===null)return {action,actionLine,dangerous,incomplete};
 if(refInFlowValue(withValue))dangerous.push(withLine);
 const stop=withEnd??end;
 for(let i=withStart+1;i<stop;i++){
  const line=lines[i],indent=line.match(/^\s*/)[0].length;
  if(indent<=direct)continue;
  const kv=keyValue(line);
  if(!['ref','repository'].includes(kv?.key))continue;
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
 return {action,actionLine,dangerous,incomplete};
}
// YAML aliases in security-sensitive values are not trustworthy without a
// complete YAML parser. Treat them as INCOMPLETE rather than silently clear.
// Only scope aliases inside on/permissions mappings or checkout inputs,
// never arbitrary labels, descriptions, comments or action names.
function hasSensitiveAliases(lines,stepRanges=jobStepRanges(lines)){
 let scope=null;
 const rootIndent=yamlRootIndent(lines);
 const structural=structuralYamlRowMask(lines);
 for(let i=0;i<lines.length;i++){
  if(!structural[i])continue;
  const row=lines[i],trim=row.trim();
  if(!trim||trim.startsWith('#'))continue;
  const indent=row.match(/^\s*/)[0].length,kv=keyValue(row);
  if(scope!==null&&indent<=scope.indent)scope=null;
  const rootEvent=indent===rootIndent&&kv?.key==='on';
  // Quoted "*name" is not a YAML alias, but is also not a supported Actions
  // trigger. Keep the existing fail-closed contract for ambiguous on scalars.
  if(rootEvent&&/^\s*["']\*[-A-Za-z0-9_]+["']\s*(?:#.*)?$/.test(kv.value))return true;
  const permissions=kv?.key==='permissions'&&inRealPermissionsMap(lines,i);
  const input=kv&&['uses','with','ref','repository'].includes(kv.key)&&
   stepRanges.some(s=>s.start<=i&&i<s.end);
  if(rootEvent||permissions)scope={indent};
  const examined=(rootEvent||permissions||input)?kv.value:
   scope&&indent>scope.indent?trim:'';
  // A leading *alias or an alias nested in a flow sequence/map is
  // intentionally unresolved and must fail closed, including quoted aliases.
  // A flow-step begins with "- {"; ref aliases there are nested in the
  // sequence item, so keyValue(row) cannot expose that syntax.
  // Check the *entire* compact flow step, including continued lines and
  // YAML-quoted mapping keys. A ref alias is not a verified checkout target.
  // Only inspect real job steps, never arbitrary env/run text named "uses".
  const flowStep=stepRanges.find(s=>s.start===i);
  const flowStepValue=flowStep && withoutLeadingAnchor(row.replace(/^\s*-\s*/,''));
  const flowStepAlias=flowStepValue?.startsWith('{') &&
   sensitiveFlowAlias(collectFlowMap(lines,flowStep.start,flowStep.end,
    flowStepValue).value);
  // A sequence item can itself be a YAML alias or use a merge-key alias.
  // Neither gives evidence about the real action or checkout ref: fail closed.
  const unresolvedStep=typeof flowStepValue==='string'&&
   (/^\*[-A-Za-z0-9_.]+(?:\s|$)/.test(flowStepValue)||
    /^<<\s*:/.test(flowStepValue));
  // Inspect structural mapping entries throughout every step (not only
  // the first key). Unresolved merges must not certify hidden uses/with.
  const stepMerge=stepRanges.some(s=>s.start<=i&&i<s.end)&&/^<<\s*:/.test(trim);
  if(unresolvedStep||stepMerge||flowStepAlias||containsYamlAlias(examined))return true;
 }
 return false;
}

// One structural mask for every security-sensitive YAML scan.
// Content inside | and > block scalars is script/text data, not YAML nodes.
function structuralYamlRowMask(lines){
 const mask=[];let blockIndent=null;
 for(const line of lines){
  const trimmed=line.trim(),indent=line.match(/^\s*/)[0].length;
  if(blockIndent!==null){
   if(!trimmed||indent>blockIndent){mask.push(false);continue;}
   blockIndent=null;
  }
  mask.push(true);
  if(!trimmed||trimmed.startsWith('#'))continue;
  const kv=keyValue(line)||keyValue(line.replace(/^\s*-\s*/,''));
  if(kv&&/^[>|](?:(?:[+-][1-9]?)|(?:[1-9][+-]?)|[+-])?$/.test(
    withoutYamlComment(kv.value).trim())){
   // Sequence mapping keys start after "- "; literal content has deeper
   // indentation, but subsequent uses/with siblings are at key indentation.
   const sequencePrefix=line.match(/^(\s*)-\s+/);
   blockIndent=sequencePrefix?sequencePrefix[0].length:indent;
  }
 }
 return mask;
}
function hasUnknownStructuralYamlKeys(lines){
 const mask=structuralYamlRowMask(lines);
 return lines.some((line,i)=>mask[i]&&
  (keyValue(line)||keyValue(line.replace(/^\s*-\s*/,'')))?.unresolved);
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
  const lines=code.split('\n'),audit={partial:false};
  // Unknown YAML key escapes must not silently certify a workflow clean.
  if(hasUnknownStructuralYamlKeys(lines))coverage.partial=true;
  const privileged=privilegedTrigger(lines,audit);
  const steps=jobStepRanges(lines,audit);
  if(audit.partial||hasSensitiveAliases(lines,steps))coverage.partial=true;
  for(const step of steps){
   for(const line of shellPipelinesInStep(lines,step))
    flag('REMOTE_SHELL_PIPELINE','HIGH',name,line);
   const details=stepAction(lines,step);
   if(details.incomplete)coverage.partial=true;
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
    // Flow permission maps may continue on following lines and contain
    // several comma-delimited entries on the same physical line. Parse the
    // collected flow *entries*, not each physical line as a single scalar.
    const permissionValue=withoutLeadingAnchor(permissionKey.value);
    let flowClosed=false;
    if(permissionValue.startsWith('{')){
     const mapping=collectFlowMap(lines,i,lines.length,permissionValue);
     flowClosed=mapping.closed;
     if(!flowClosed)coverage.partial=true;
     else if(splitFlowEntries(mapping.value.slice(1,-1))
      .some(part=>writableLine(part.trim())))
      flag('PRIVILEGED_WRITE_TOKEN','HIGH',name,i+1);
    }
    if(/^[>|][+-]?$/.test(permissionValue)){
     const items=[];
     for(let k=i+1;k<Math.min(lines.length,i+65);k++){
      const child=lines[k],childIndent=child.match(/^\s*/)[0].length;
      if(child.trim()&&childIndent<=indent)break;
      if(child.trim()&&!child.trim().startsWith('#'))items.push(child.trim());
     }
     if(items.join(' ').trim()==='write-all')
      flag('PRIVILEGED_WRITE_TOKEN','HIGH',name,i+1);
    }
    if(!permissionValue||(permissionValue.startsWith('{')&&!flowClosed))
      permissionsIndent=indent;
   }else if(permissionsIndent!==null&&indent>permissionsIndent&&writableLine(line)){
    flag('PRIVILEGED_WRITE_TOKEN','HIGH',name,i+1);
   }
  }
 }
 if(findings.length>=MAX_ALERTS)coverage.partial=true;
 return output(coverage.partial?'INCOMPLETE':findings.length?'REVIEW_REQUIRED':'NO_RISK_PATTERN');
}
module.exports={reviewWorkflows};
