'use strict';
// Structural, read-only YAML review. Source is UNTRUSTED DATA: never execute
// parsed values, never interpolate them into a command, never load custom tags.
// Dependency: js-yaml 4.1.1, pinned with sha512 integrity in package-lock.json.
const yaml=require('js-yaml');
const MAX_SOURCE=160000,MAX_NODES=4096,MAX_DEPTH=35,MAX_FINDINGS=40;
const PINNED=/^[a-f0-9]{40}$/i, ACTION=/^[-A-Za-z0-9_.\/]+@([^\s]+)$/;
const REMOTE=/\b(?:curl|wget)\b[^\n]*\|&?\s*(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9]+(?:\.[0-9]+)?)?|pwsh|powershell|node|ruby|perl|php)(?:\b|$)/;
const DOWNLOAD_PIPE=/\b(?:curl|wget)\b[^\n]*\|&?\s*\S+/;
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
  if(!isMap(doc.jobs)||!Object.keys(doc.jobs).length)
   return {ok:false,reason:'INVALID_JOBS'};
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
 // These events use base/default-branch workflow definitions while PR-related
 // payloads or refs can be attacker-controlled. Treat checkouts of PR code
 // as privileged even when the workflow does not use pull_request_target.
 const privileged=new Set(['pull_request_target','workflow_run','issue_comment',
  'issues','discussion','discussion_comment','pull_request_review',
  'pull_request_review_comment','release','repository_dispatch',
  'workflow_dispatch','check_run','check_suite','deployment',
  'deployment_status','gollum','fork','watch','label','milestone','public']);
 return events.some(event=>privileged.has(event));
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
function unknownCheckoutExpression(value,field){
 if(typeof value!=='string'||!value.includes('${{'))return false;
 // Unknown dynamic inputs (e.g. inputs.ref) may be PR-controlled. Only
 // explicitly base-scoped expressions can be treated as trusted under
 // pull_request_target/workflow_run.
 const allowed=field==='repository'?
  /^\$\{\{\s*github\.repository\s*\}\}$/:
  /^\$\{\{\s*(?:github\.(?:sha|ref)|github\.event\.pull_request\.base\.sha|github\.event\.repository\.default_branch)\s*\}\}$/;
 return !allowed.test(value);
}
// Bash treats a newline following | or |& as part of the same pipeline,
// even without a backslash. Normalize only those lexical continuations.
// This remains a bounded, conservative heuristic (not a full shell parser).
function pipelineCommands(v){
 const withoutEscaped=String(v).replace(/\\\r?\n/g,'');
 // Shell concatenates adjacent quoted word fragments (c''url, c'u'rl).
 // This is a conservative classification canonicalization, not evaluation.
 const normalized=withoutEscaped.replace(/\\(?=[A-Za-z])/g,'')
  .replace(/\x24\x27([A-Za-z]*)\x27/g,'$1')
  .replace(/(['"])([A-Za-z]*)\1/g,'$2');
 return normalized.replace(/(\|&?)[ \t]*\r?\n[ \t]*/g,'$1 ').split(/\r?\n/);
}
function remotePipeline(v){
 return typeof v==='string'&&pipelineCommands(v).some(line=>REMOTE.test(line));
}
function unknownDownloadPipeline(v){
 return typeof v==='string'&&pipelineCommands(v).some(line=>DOWNLOAD_PIPE.test(line)&&!REMOTE.test(line));
}
// Direct interpolation substitutes attacker-controlled event text into a
// shell script before execution. Quoting cannot prevent command substitution.
// This catches high-confidence free-text expressions; review remains required
// for unmodeled sources.
// Executable Actions expressions are not a safe regular-language subset.
// Conservative trust boundary: permit ONLY a small set of self-contained
// GitHub-provided scalar values. Unknown or malformed expressions never yield
// a clean verdict; references to event/input text are explicitly blocked.
// This is not advertised as a complete parser for the Actions expression DSL.
const TRUSTED_EXECUTABLE_SCALAR=/^(?:github\.(?:sha|run_id|run_number|run_attempt|event_name)|github\.event\.(?:issue|pull_request|discussion)\.number|toJSON\s*\(\s*github\.event\.(?:issue|pull_request|discussion)\.number\s*\))$/i;
// GitHub expressions can contain '}}' inside quoted string literals.
// Do not mistake that literal for the expression terminator. GitHub's
// single-quoted string escaping uses doubled single quotes.
function expressionTerminator(script,start){
 let quote=null;
 for(let i=start+3;i<script.length-1;i++){
  const ch=script[i];
  if(quote!==null){
   if(quote==="'"&&ch==="'"&&script[i+1]==="'"){i++;continue;}
   if(quote==='"'&&ch==='\\'){i++;continue;}
   if(ch===quote)quote=null;
  }else if(ch==="'"||ch==='"')quote=ch;
  else if(ch==='}'&&script[i+1]==='}')return i;
 }
 return -1;
}
function auditExecutableExpressions(script){
 if(typeof script!=='string')return {unsafe:false,incomplete:true};
 let unsafe=false,incomplete=false,cursor=0;
 while(true){
  const start=script.indexOf('$'+'{{',cursor);
  if(start<0)break;
  const end=expressionTerminator(script,start);
  if(end<0){incomplete=true;break;}
  const raw=script.slice(start+3,end);
  if(raw.length>800){incomplete=true;cursor=end+2;continue;}
  const expr=raw.trim().replace(/\[\s*(['"])([A-Za-z_][A-Za-z0-9_]*|\*)\1\s*\]/g,'.$2');
  if(!TRUSTED_EXECUTABLE_SCALAR.test(expr)){
   // Treat every unknown expression as unverified. Event, head_ref and user
   // inputs are potentially arbitrary text even through format(), fromJSON(),
   // wildcard filters or computed property access.
   if(/\bgithub\s*\.\s*(?:event|head_ref)\b|\binputs\s*(?:\.|\[)/i.test(expr))unsafe=true;
   else incomplete=true;
  }
  cursor=end+2;
 }
 return {unsafe,incomplete};
}
function inspectWorkflow(source,{path='.github/workflows/workflow.yml',trustedRepository=null}={}){
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
 const inspectDynamicControl=(value,where)=>{
  if(typeof value!=='string'){coverage.partial=true;return;}
  const checked=auditExecutableExpressions(value);
  if(checked.unsafe)emit('PRIVILEGED_EVENT_SCRIPT_INJECTION','BLOCKER',where);
  // Unknown expressions and all dynamic execution infrastructure require
  // independent review. The caller may not infer safety from a clean script.
  coverage.partial=true;
 };
 const checkExecutableShell=(value,where)=>{
  if(value===undefined)return;
  if(typeof value!=='string'||!value.trim()){coverage.partial=true;return;}
  if(value.includes(String.fromCharCode(36,123,123))){
   inspectDynamicControl(value,where);
   return;
  }
  // Actions permits arbitrary custom command templates for shell; checking
  // only run: would miss malicious commands executed to launch that script.
  const supported=new Set(['bash','sh','pwsh','powershell','cmd','python']);
  if(!supported.has(value.trim())){
   if(remotePipeline(value))emit('REMOTE_SHELL_PIPELINE','HIGH',where);
   coverage.partial=true;
  }
 };
 // Only preapproved GitHub-hosted runner labels may yield a clean verdict.
 // Private runner groups/labels and arbitrary expression-based routing need
 // independent review even when the commands themselves look safe.
 const checkRunner=(value,where)=>{
  if(value===undefined)return;
  const hosted=v=>typeof v==='string'&&
   /^(?:ubuntu|windows|macos)-(?:latest|[0-9]{2}(?:\.[0-9]{2})?)$/.test(v);
  if(hosted(value))return;
  if(typeof value==='string'&&value.includes(String.fromCharCode(36,123,123)))
   inspectDynamicControl(value,where);
  else if(Array.isArray(value)){
   if(!value.length||!value.every(hosted))coverage.partial=true;
  }else coverage.partial=true;
 };
 // An unpinned/mutable image runs third-party code on the job machine.
 // Only fixed SHA-256 digests are recognized; all other image attributes,
 // credentials, mounts and service options remain explicitly inconclusive.
 const checkImage=(image,where)=>{
  if(typeof image!=='string'||!image.trim()){coverage.partial=true;return;}
  if(image.includes(String.fromCharCode(36,123,123))){
   inspectDynamicControl(image,where);return;
  }
  if(!/^[a-zA-Z0-9_.:/-]+@sha256:[a-fA-F0-9]{64}$/.test(image.trim()))
   coverage.partial=true;
 };
 const checkContainer=(container,where)=>{
  if(container===undefined)return;
  if(typeof container==='string'){checkImage(container,where);return;}
  if(!isMap(container)){coverage.partial=true;return;}
  checkImage(container.image,where);
  if(Object.keys(container).some(key=>key!=='image'))coverage.partial=true;
 };
 const checkServices=(services,where)=>{
  if(services===undefined)return;
  if(!isMap(services)){coverage.partial=true;return;}
  for(const service of Object.values(services)){
   if(!isMap(service)){coverage.partial=true;continue;}
   checkImage(service.image,where);
   if(Object.keys(service).some(key=>key!=='image'))coverage.partial=true;
  }
 };
 // Treat event-backed env as tainted DATA, not automatically as injected
 // code. A github-script that merely logs process.env is not a vulnerability.
 const collectEnvSources=(value)=>{
  const tainted=new Set();
  if(value===undefined)return tainted;
  if(!isMap(value)){coverage.partial=true;return tainted;}
  for(const [name,item] of Object.entries(value)){
   if(!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)){coverage.partial=true;continue;}
   if(typeof item!=='string'){
    if(item!==null&&typeof item!=='number'&&typeof item!=='boolean')coverage.partial=true;
    continue;
   }
   if(!item.includes(String.fromCharCode(36,123,123)))continue;
   const check=auditExecutableExpressions(item);
   if(check.unsafe||check.incomplete)tainted.add(name);
  }
  return tainted;
 };
 const checkReusableInputs=(value,where)=>{
  if(value===undefined)return;
  if(!isMap(value)){coverage.partial=true;return;}
  for(const item of Object.values(value)){
   if(typeof item!=='string'){coverage.partial=true;continue;}
   if(!item.includes(String.fromCharCode(36,123,123)))continue;
   const check=auditExecutableExpressions(item);
   if(check.unsafe)emit('PRIVILEGED_EVENT_SCRIPT_INJECTION','BLOCKER',where);
   if(check.incomplete)coverage.partial=true;
  }
 };
 const auditShellEnvUse=(script,tainted,where)=>{
  for(const key of tainted){
   const bare='\\$(?:\\{'+key+'\\}|'+key+'\\b)';
   // Bash parameter operators retain the tainted source. Unknown valid
   // parameter syntax must never be silently classified as clean.
   const parameter='\\$\\{!?'+key+'(?=[^A-Za-z0-9_])[^}]*\\}';
   const token='(?:'+bare+'|'+parameter+')';
   const ref=new RegExp(token);
   if(!ref.test(script))continue;
   // Bare or parametrized substitutions can choose a command or feed
   // eval/shell -c. Other usages remain INCOMPLETE, never clean.
   const commandPosition=new RegExp('(?:^|[;\\n]|&&|\\|\\|)\\s*'+token+'(?=\\s|$)');
   if(commandPosition.test(script)||/\\b(?:eval|source|bash\\s+-c|sh\\s+-c|python\\s+-c|node\\s+-e)\\b/.test(script))
    emit('PRIVILEGED_EVENT_SCRIPT_INJECTION','BLOCKER',where);
   else coverage.partial=true;
  }
 };
 const checkDefaults=(defaults,where)=>{
  if(defaults===undefined)return;
  if(!isMap(defaults)||!isMap(defaults.run)){coverage.partial=true;return;}
  checkExecutableShell(defaults.run.shell,where);
 };
 checkDefaults(document.defaults,path);
 const workflowTaint=collectEnvSources(document.env);
 checkPermissions(document.permissions,path);
 for(const [name,job] of Object.entries(document.jobs)){
  const where=path; // Deliberately no attempt to fabricate AST line locations.
  if(!isMap(job)){coverage.partial=true;continue;}
  checkPermissions(job.permissions,where);
  checkDefaults(job.defaults,where);
  const jobTaint=new Set([...workflowTaint,...collectEnvSources(job.env)]);
  if(privileged){
   checkRunner(job['runs-on'],where);
   checkContainer(job.container,where);
   checkServices(job.services,where);
  }
  if(job.steps===undefined){
   if(typeof job.uses==='string'){
    // Local same-repository reusable workflows follow this commit; only
    // third-party reusable workflows must be full SHA pinned.
    const local=/^\.\/\.github\/workflows\/[A-Za-z0-9_.-]+\.ya?ml$/.test(job.uses);
    if(!local){
     const external=job.uses.match(/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+\/\.github\/workflows\/[A-Za-z0-9_.-]+\.ya?ml@(.+)$/);
     if(!external||!PINNED.test(external[1]))
      emit('UNPINNED_REUSABLE_WORKFLOW','BLOCKER',where);
    }else if(privileged){
     // A caller cannot independently certify an uninspected callee graph.
     coverage.partial=true;
    }
    checkReusableInputs(job.with,where);
    if(privileged&&job.secrets!==undefined)coverage.partial=true;
    continue;
   }
   // Dynamic job.uses cannot be verified as a fixed action source.
   coverage.partial=true;continue;
  }
  if(job.uses!==undefined){coverage.partial=true;continue;}
  if(!Array.isArray(job.steps)||job.steps.length===0){coverage.partial=true;continue;}
  for(const step of job.steps){
   if(!isMap(step)){coverage.partial=true;continue;}
   const stepTaint=new Set([...jobTaint,...collectEnvSources(step.env)]);
   checkExecutableShell(step.shell,where);
   if(step.uses!==undefined){
    if(typeof step.uses!=='string'){coverage.partial=true;continue;}
    if(!step.uses.startsWith('./')){
     const action=ACTION.exec(step.uses);
     if(!action||!PINNED.test(action[1]))emit('UNPINNED_ACTION','HIGH',where);
    }
    if(/^actions\/github-script@/i.test(step.uses)){
     // github-script compiles its script input as JavaScript after Github
     // expressions are substituted. Use runner-equivalent input names.
     const inputs=Object.create(null);
     let ambiguous=step.with===undefined||!isMap(step.with);
     if(!ambiguous){
      for(const [key,value] of Object.entries(step.with)){
       const folded=key.toLowerCase();
       if(!/^[A-Za-z0-9_-]+$/.test(key)||
          Object.prototype.hasOwnProperty.call(inputs,folded)){
        ambiguous=true;break;
       }
       inputs[folded]=value;
      }
     }
     if(ambiguous||typeof inputs.script!=='string'||!inputs.script.trim())
      coverage.partial=true;
     else{
      const inspection=auditExecutableExpressions(inputs.script);
      if(inspection.incomplete)coverage.partial=true;
      if(inspection.unsafe)
       emit('PRIVILEGED_EVENT_SCRIPT_INJECTION','BLOCKER',where);
      // github-script runs JavaScript; tainted process.env is data until
      // passed to an executable code sink. Logging does not evaluate it.
      for(const name of stepTaint){
       const used=inputs.script.includes('process.env.'+name)||
        inputs.script.includes("process.env['"+name+"']")||
        inputs.script.includes('process.env["'+name+'"]');
       if(!used)continue;
       if(/\b(?:eval|Function|exec|execSync|spawn|spawnSync)\s*\(/.test(inputs.script))
        emit('PRIVILEGED_EVENT_SCRIPT_INJECTION','BLOCKER',where);
       else if(!/^\s*core\.info\(\s*process\.env\.[A-Za-z_][A-Za-z0-9_]*\s*\);?\s*$/.test(inputs.script))
        coverage.partial=true;
      }
     }
    }
    if(privileged&&/^actions\/checkout@/i.test(step.uses)){
     // Actions runner exposes every input as INPUT_<UPPERCASE_NAME>.
     // YAML preserves case, so Ref/ref and Repository/repository must share
     // the same effective slot; reject ambiguity or non-ASCII key tricks.
     const inputs=Object.create(null);
     let invalidInputs=step.with!==undefined&&!isMap(step.with);
     if(!invalidInputs&&isMap(step.with)){
      for(const [key,value] of Object.entries(step.with)){
       const k=key.toLowerCase();
       if(!/^[A-Za-z0-9_-]+$/.test(key)||
          Object.prototype.hasOwnProperty.call(inputs,k)){
        invalidInputs=true;break;
       }
       inputs[k]=value;
      }
     }
     if(invalidInputs)coverage.partial=true;
     else{
      const server=inputs['github-server-url'];
      if(server!==undefined){
       if(typeof server!=='string')coverage.partial=true;
       else if(/^\$\{\{\s*github\.server_url\s*\}\}$/.test(server.trim())||
               /^https:\/\/github\.com\/?$/.test(server.trim())){
        // Trusted GitHub instance, no attacker-controlled alternate origin.
       }else if(server.includes('$'+'{{'))coverage.partial=true;
       else emit('PRIVILEGED_ALTERNATE_GITHUB_SERVER','BLOCKER',where);
      }
      if((inputs.ref!==undefined&&typeof inputs.ref!=='string')||
         (inputs.repository!==undefined&&typeof inputs.repository!=='string')){
       coverage.partial=true;
      }else if(unsafePrRef(inputs.ref)||unsafePrRef(inputs.repository)){
       emit('PRIVILEGED_PR_CODE_CHECKOUT','BLOCKER',where);
      }else if(unknownCheckoutExpression(inputs.ref,'ref')||
                unknownCheckoutExpression(inputs.repository,'repository')){
       coverage.partial=true;
      }else if(typeof inputs.repository==='string'){
       const repository=inputs.repository.trim();
       const trusted=typeof trustedRepository==='string'&&
         /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(trustedRepository);
       if(/^\$\{\{\s*github\.repository\s*\}\}$/.test(repository)){
        // The exact GitHub-provided base repository identity is trusted.
       }else if(!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository)||!trusted){
        coverage.partial=true;
       }else if(repository.toLowerCase()!==trustedRepository.toLowerCase()&&
                !PINNED.test(inputs.ref||'')){
        emit('PRIVILEGED_EXTERNAL_MUTABLE_CHECKOUT','BLOCKER',where);
       }
      }
     }
    }
   }
   if(step.run!==undefined){
    if(typeof step.run!=='string'||!step.run.trim())coverage.partial=true;
    else{
     auditShellEnvUse(step.run,stepTaint,where);
     if(remotePipeline(step.run))emit('REMOTE_SHELL_PIPELINE','HIGH',where);
     else if(unknownDownloadPipeline(step.run))coverage.partial=true;
     {
      const inspection=auditExecutableExpressions(step.run);
      if(inspection.incomplete)coverage.partial=true;
      if(inspection.unsafe)
       emit('PRIVILEGED_EVENT_SCRIPT_INJECTION','BLOCKER',where);
     }
    }
   }
   if((step.run===undefined&&step.uses===undefined)||
      (step.run!==undefined&&step.uses!==undefined))coverage.partial=true;
  }
 }
 const status=coverage.partial?'INCOMPLETE':
  findings.length?'REVIEW_REQUIRED':'NO_RISK_PATTERN';
 return {status,findings,coverage,reason:null};
}
function reviewWorkflowSources({expected=[],sources={},trustedRepository=null}={}){
 const results=[],findings=[],coverage={partial:false,requested:expected.length,
  scanned:0,parser:'js-yaml@4.1.1'};
 if(!Array.isArray(expected)||expected.length>25)return {status:'INCOMPLETE',findings,
  coverage:{...coverage,partial:true},results};
 for(const path of expected){
  if(typeof path!=='string'||!/^\.github\/workflows\/[A-Za-z0-9_.-]+\.ya?ml$/.test(path)||
   !Object.prototype.hasOwnProperty.call(sources,path)){coverage.partial=true;continue;}
  const r=inspectWorkflow(sources[path],{path,trustedRepository});results.push({path,status:r.status,reason:r.reason});
  coverage.scanned++;
  if(r.coverage.partial)coverage.partial=true;
  findings.push(...r.findings);
 }
 return {status:coverage.partial?'INCOMPLETE':
  findings.length?'REVIEW_REQUIRED':'NO_RISK_PATTERN',findings,coverage,results};
}
module.exports={parseWorkflow,inspectWorkflow,reviewWorkflowSources};
