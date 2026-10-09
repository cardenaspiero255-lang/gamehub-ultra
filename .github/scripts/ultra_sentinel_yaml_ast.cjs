'use strict';
// Structural, read-only YAML review. Source is UNTRUSTED DATA: never execute
// parsed values, never interpolate them into a command, never load custom tags.
// Dependency: js-yaml 4.1.1, pinned with sha512 integrity in package-lock.json.
const yaml=require('js-yaml');
const MAX_SOURCE=160000,MAX_NODES=4096,MAX_DEPTH=35,MAX_FINDINGS=40;
const PINNED=/^[a-f0-9]{40}$/i, ACTION=/^[-A-Za-z0-9_.\/]+@([^\s]+)$/;
const REMOTE=/\b(?:curl|wget)\b[^\n]{0,240}\|&?\s*(?:bash|sh)(?:\b|$)/;
const WRITE_CAPABILITIES=new Set(['actions','attestations','checks','contents',
 'deployments','discussions','environments','id-token','issues','models','packages',
 'pages','pull-requests','security-events','statuses','artifact-metadata',
 'code-quality','repository-projects','members','administration','workflows']);
const isMap=v=>v!==null&&typeof v==='object'&&!Array.isArray(v)&&
  (Object.getPrototypeOf(v)===Object.prototype||Object.getPrototypeOf(v)===null);
function boundedGraph(root){
 const visited=new WeakSet(),ancestors=new WeakSet();let count=0;
 function visit(v,depth){
  if(depth>MAX_DEPTH||++count>MAX_NODES)throw Error('yaml-graph-limit');
  if(v===null||typeof v!=='object')return;
  if(!Array.isArray(v)&&!isMap(v))throw Error('yaml-type');
  if(ancestors.has(v))throw Error('yaml-cycle');
  if(visited.has(v))return;
  visited.add(v);ancestors.add(v);
  const children=Array.isArray(v)?v:Object.values(v);
  if(children.length>MAX_NODES)throw Error('yaml-width-limit');
  for(const child of children)visit(child,depth+1);
  ancestors.delete(v);
 }
 visit(root,0);
}
function parseWorkflow(source){
 if(typeof source!=='string'||!source.trim()||Buffer.byteLength(source,'utf8')>MAX_SOURCE)
  return {ok:false,reason:'INVALID_SOURCE_SIZE'};
 try{
  const input=source.charCodeAt(0)===0xfeff?source.slice(1):source;
  // DEFAULT_SCHEMA is YAML 1.2-compatible for GitHub's 'on' key; unlike
  // PyYAML's unmodified YAML 1.1 loader, 'on' stays a string.
  const doc=yaml.load(input,{schema:yaml.DEFAULT_SCHEMA,json:false,
   onWarning:()=>{throw Error('yaml-warning');}});
  if(!isMap(doc))return {ok:false,reason:'INVALID_ROOT'};
  boundedGraph(doc);
  if(!Object.prototype.hasOwnProperty.call(doc,'on'))
   return {ok:false,reason:'MISSING_TRIGGER'};
  if(!isMap(doc.jobs))return {ok:false,reason:'INVALID_JOBS'};
  const trigger=doc.on;
  if(typeof trigger!=='string'&&!Array.isArray(trigger)&&!isMap(trigger))
   return {ok:false,reason:'INVALID_TRIGGER'};
  if(Array.isArray(trigger)&&!trigger.every(v=>typeof v==='string'))
   return {ok:false,reason:'INVALID_TRIGGER_LIST'};
  if(isMap(trigger)&&Object.keys(trigger).some(k=>typeof k!=='string'))
   return {ok:false,reason:'INVALID_TRIGGER_MAP'};
  return {ok:true,workflow:doc};
 }catch(e){
  // Do not emit raw attacker-controlled snippets or throw from CI gating.
  return {ok:false,reason:'YAML_PARSE_OR_LIMIT_ERROR'};
 }
}
function privilegedTrigger(value){
 const events=typeof value==='string'?[value]:
  Array.isArray(value)?value:Object.keys(value);
 return events.some(event=>event==='pull_request_target'||event==='workflow_run');
}
function unsafePrRef(v){
 if(typeof v!=='string')return false;
 if(/^refs\/pull\/(?:\d+|\$\{\{[\s\S]*?\}\})\/(?:merge|head)$/i.test(v))return true;
 if(/\$\{\{\s*format\(/i.test(v)&&/refs\/pull\//i.test(v)&&
   /github\.event\.pull_request\.number\b/.test(v)&&/(?:merge|head)/i.test(v))return true;
 const matches=[...v.matchAll(/\$\{\{\s*([\s\S]*?)\s*\}\}/g)];
 return matches.some(m=>{
  const expr=m[1].replace(/\[\s*(['"])([A-Za-z_][A-Za-z0-9_]*)\1\s*\]/g,'.$2');
  return /\bgithub\.(?:head_ref|event\.pull_request\.head\.(?:sha|ref|repo\.(?:full_name|name|clone_url))|event\.workflow_run\.(?:head_sha|head_branch|head_repository\.full_name))\b/.test(expr);
 });
}
function remotePipeline(v){
 if(typeof v!=='string')return false;
 // For literal scripts, Bash removes a backslash + newline WITHOUT spaces.
 // Folded YAML is already folded by js-yaml; do not join unrelated commands.
 return v.replace(/\\\r?\n/g,'').split(/\r?\n/).some(line=>REMOTE.test(line));
}
function inspectWorkflow(source,{path='.github/workflows/workflow.yml'}={}){
 const findings=[],coverage={partial:false,parser:'js-yaml@4.1.1'};
 const emit=(rule,severity,where)=>{
  if(findings.length>=MAX_FINDINGS){coverage.partial=true;return;}
  if(!findings.some(x=>x.rule===rule&&x.path===where))
   findings.push({rule,severity,path:where,line:1,confidence:'STRUCTURAL',
     verification:'Verify the complete trusted GitHub Actions workflow.'});
 };
 const parsed=parseWorkflow(source);
 if(!parsed.ok)return {status:'INCOMPLETE',findings,coverage:{...coverage,partial:true},
  reason:parsed.reason};
 const document=parsed.workflow;
 const privileged=privilegedTrigger(document.on);
 const checkPermissions=(value,where)=>{
  if(value===undefined||value===null)return;
  if(value==='write-all'){emit('PRIVILEGED_WRITE_TOKEN','HIGH',where);return;}
  if(value==='read-all')return;
  if(!isMap(value)){coverage.partial=true;return;}
  for(const [capability,level] of Object.entries(value)){
   if(!WRITE_CAPABILITIES.has(capability)){coverage.partial=true;continue;}
   if(level==='write')emit('PRIVILEGED_WRITE_TOKEN','HIGH',where);
   else if(level!=='read'&&level!=='none')coverage.partial=true;
  }
 };
 checkPermissions(document.permissions,path);
 for(const [name,job] of Object.entries(document.jobs)){
  const where=path; // Deliberately no attempt to fabricate AST line locations.
  if(!isMap(job)){coverage.partial=true;continue;}
  checkPermissions(job.permissions,where);
  if(job.steps===undefined){
   if(typeof job.uses==='string')continue; // Reusable workflow job.
   // Purely declarative/non-step jobs are not evaluated as successful evidence.
   coverage.partial=true;continue;
  }
  if(!Array.isArray(job.steps)){coverage.partial=true;continue;}
  for(const step of job.steps){
   if(!isMap(step)){coverage.partial=true;continue;}
   if(step.uses!==undefined){
    if(typeof step.uses!=='string'){coverage.partial=true;continue;}
    if(!step.uses.startsWith('./')){
     const action=ACTION.exec(step.uses);
     if(!action||!PINNED.test(action[1]))emit('UNPINNED_ACTION','HIGH',where);
    }
    if(privileged&&/^actions\/checkout@/i.test(step.uses)){
     if(step.with!==undefined&&!isMap(step.with))coverage.partial=true;
     else if(isMap(step.with)){
      if(step.with.ref!==undefined&&typeof step.with.ref!=='string'){
       coverage.partial=true;
      }else if(unsafePrRef(step.with.ref))
       emit('PRIVILEGED_PR_CODE_CHECKOUT','BLOCKER',where);
     }
    }
   }
   if(step.run!==undefined){
    if(typeof step.run!=='string')coverage.partial=true;
    else if(remotePipeline(step.run))emit('REMOTE_SHELL_PIPELINE','HIGH',where);
   }
  }
 }
 const status=coverage.partial?'INCOMPLETE':
  findings.length?'REVIEW_REQUIRED':'NO_RISK_PATTERN';
 return {status,findings,coverage,reason:null};
}
function reviewWorkflowSources({expected=[],sources={}}={}){
 const results=[],findings=[],coverage={partial:false,requested:expected.length,
  scanned:0,parser:'js-yaml@4.1.1'};
 if(!Array.isArray(expected)||expected.length>25)return {status:'INCOMPLETE',findings,
  coverage:{...coverage,partial:true},results};
 for(const path of expected){
  if(typeof path!=='string'||!/^\.github\/workflows\/[A-Za-z0-9_.-]+\.ya?ml$/.test(path)||
   !Object.prototype.hasOwnProperty.call(sources,path)){coverage.partial=true;continue;}
  const r=inspectWorkflow(sources[path],{path});results.push({path,status:r.status,reason:r.reason});
  coverage.scanned++;
  if(r.coverage.partial)coverage.partial=true;
  findings.push(...r.findings);
 }
 return {status:coverage.partial?'INCOMPLETE':
  findings.length?'REVIEW_REQUIRED':'NO_RISK_PATTERN',findings,coverage,results};
}
module.exports={parseWorkflow,inspectWorkflow,reviewWorkflowSources};
