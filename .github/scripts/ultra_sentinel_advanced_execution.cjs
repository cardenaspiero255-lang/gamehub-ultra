'use strict';
/**
 * Read-only rules for remote execution spanning commands or interpreters.
 * Never execute input, decode an attacker command, access network or files.
 * This is a bounded scanner, not a full shell interpreter.
 */
const MAX_SCRIPT=160000;
const posix=require('node:path').posix;
const normalizedFile=name=>posix.normalize(name.replace(/^(?:\.\/)+/,''));
// Concatenated quote spans form one literal shell path, never executable input.
function literalFileToken(token){
 let quote=null,decoded='';
 for(const ch of token){
  if(ch==='"'||ch==="'"){
   if(quote===ch){quote=null;continue}
   if(quote===null){quote=ch;continue}
  }
  if(ch==='\\'||ch==='$'||ch.charCodeAt(0)===96)return null;
  decoded+=ch;
 }
 if(quote!==null||!/^(?:\.\/)?[a-z0-9_.\/-]+$/i.test(decoded))return null;
 return normalizedFile(decoded);
}
// Bounded lexer: split on whitespace OUTSIDE shell quotes and preserve each
// complete word for literalFileToken(). Never interpolate or execute values.
function shellLiteralWords(raw){
 const words=[];let token='',quote=null;
 for(const ch of raw){
  if(ch==='"'||ch==="'"){
   if(quote===ch)quote=null;
   else if(!quote)quote=ch;
   token+=ch;
  }else if(/\s/.test(ch)&&!quote){
   if(token){words.push(token);token='';}
  }else token+=ch;
  if(token.length>256||words.length>64)return null;
 }
 if(quote)return null;
 if(token)words.push(token);
 return words;
}
function findingsForScript(source,{shell=''}={}){
 if(typeof source!=='string')return [];
 // Direct callers must never interpret an unscannable script as clean.
 // Workflow YAML parsing independently rejects oversized sources.
 if(source.length>MAX_SCRIPT)return ['REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'];
 const findings=new Set();
 const flag=rule=>findings.add(rule);
 const lines=source.split(/\r?\n/);
 // Literal echo/printf of attack documentation is not executable code.
 const active=lines.filter(line=>!(/^\s*(?:echo|printf)\s+(['"])[^$\x60]*\1\s*$/.test(line)||
  /^\s*#/.test(line))).join('\n');
 // Analyze filename aliases with command ordering: a link created *after*
 // an attempted execution must not turn an unrelated command into a finding.
 // Links created before a download are considered: curl can overwrite the
 // linked inode, so that alias can still execute the downloaded bytes.
 const download=/(?:^|[;&\n])\s*(?:curl|wget)\b([^\r\n;|&]{1,4096})/gi;
 // Only trust a directory target without '/' when this same script
 // explicitly created it before the linking operation.
 // mkdir accepts multiple directory operands. Preserve the command index
 // so only directories created before a copy/link can affect its destination.
 const createdDirs=[];
 for(const mkdir of active.matchAll(/(?:^|[;\n]|&&)\s*mkdir\b([^\r\n;&|]{1,2048})/gi)){
  const tokens=shellLiteralWords(mkdir[1]);
  if(!tokens){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;}
  let options=true,unknown=false;
  for(const token of tokens){
   if(options&&token==='--'){options=false;continue;}
   if(options&&/^(?:-p|--parents|-v|--verbose)$/.test(token))continue;
   if(options&&token.startsWith('-')){unknown=true;break;}
   options=false;
   const name=literalFileToken(token);
   if(!name){unknown=true;break;}
   createdDirs.push({at:mkdir.index,name});
  }
  // Unsupported/dynamic mkdir arguments must not be silently certified.
  if(unknown)flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
 }
 // Use the SAME literal shell-word tokenizer for all path-producing
 // commands: mkdir, ln, cp and mv. This closes the entire partial-quote
 // operand family instead of adding another special-case regular expression.
 const aliases=[];
 for(const m of active.matchAll(/(?:^|[;\n]|&&)\s*(ln|cp|mv)\b([^\r\n;&|]{1,2048})/gi)){
  const kind=m[1].toLowerCase(),tokens=shellLiteralWords(m[2]);
  if(!tokens){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;}
  const flags=[],operands=[],rawOperands=[];let afterDash=false,invalid=false;
  for(const word of tokens){
   if(!afterDash&&word==='--'){afterDash=true;continue;}
   if(!afterDash&&word.startsWith('-')){
    if(!/^(?:--[a-z-]+|-[a-zA-Z]+)$/.test(word)){invalid=true;break;}
    flags.push(word);continue;
   }
   const operand=literalFileToken(word);
   if(!operand){invalid=true;break;}
   operands.push(operand);
   rawOperands.push(word);
  }
  if(invalid||operands.length!==2){
   // Never report a complex alias command as analyzed-and-clean.
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
  }
  const [origin,destination]=operands;
  const destinationPath=normalizedFile(destination);
  // lexical normalization strips trailing '/', so preserve the original
  // complete shell word. Both dir/ and 'dir/' designate directories.
  const targetIsDirectory=/\/['"]*$/.test(rawOperands[1])||
   createdDirs.some(d=>d.name===destinationPath&&d.at<m.index);
  const to=normalizedFile(targetIsDirectory?
   posix.join(destination,posix.basename(origin)):destination);
  const symbolic=kind==='ln'&&flags.some(x=>
   x==='--symbolic'||/^-[a-zA-Z]*s[a-zA-Z]*$/.test(x));
  const from=normalizedFile(symbolic&&!origin.startsWith('/')?
   posix.join(posix.dirname(to),origin):origin);
  aliases.push({at:m.index,kind,from,to});
 }
 for(const d of active.matchAll(download)){
  const command=d[1];
  if(!/\bhttps?:\/\//i.test(command))continue;
  // Recognize bounded no-argument short-flag clusters before -o/-O.
  // An unrestricted greedy [A-Za-z]* would eat filename letters up to a
  // later 'o' (e.g. -fsSLopayload -> incorrectly parsed as file 'ad').
  const output=/(?:^|\s)(?:-[fsSLkvIqNn]*[oO]\s*|--output(?:-document)?(?:=|\s+))([a-z0-9_./'"-]+)(?=\s|$)/i.exec(command);
  if(!output)continue;
  const outputFile=literalFileToken(output[1]);
  if(!outputFile){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;}
  const outputDirs=[...command.matchAll(/(?:^|\s)--output-dir(?:=|\s+)([a-z0-9_./'"-]+)(?=\s|$)/gi)];
  // Apply the final --output-dir (earlier values are superseded).
  const rawOutputDir=outputDirs.at(-1);
  const outputDir=rawOutputDir?literalFileToken(rawOutputDir[1]):null;
  if(rawOutputDir&&!outputDir){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;}
  const file=normalizedFile(outputDir&&!outputFile.startsWith('/')?
   posix.join(outputDir,outputFile):outputFile);
  if(file==='-'||file==='.'||file==='..')continue;
  const after=active.slice(d.index+d[0].length);
  // Parse literal invocation words once; partial quote spans are supported.
  const invocations=/(?:^|[;\n]|&&|\|\|)\s*(?:(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9.]+)?|node|ruby|perl|php|source|\.)\s+(?:[-\w]+\s+)*|)([a-z0-9_.\/'"-]+)(?=\s|$|[;&])/gi;
  for(const match of after.matchAll(invocations)){
   const calledFile=literalFileToken(match[1]);
   if(!calledFile)continue;
   const executedAt=d.index+d[0].length+match.index;
   // A copy or move before the download contains old bytes; an ln may not.
   const visible=aliases.filter(a=>a.at<executedAt&&(a.at>=d.index||a.kind==='ln'));
   const tainted=new Set([file]);
   for(let i=0;i<visible.length;i++){
    let changed=false;
    for(const alias of visible){
     if(tainted.has(alias.from)&&!tainted.has(alias.to)){
      tainted.add(alias.to);changed=true;
     }
    }
    if(!changed)break;
   }
   if(tainted.has(calledFile)){
    flag('REMOTE_DOWNLOADED_FILE_EXECUTION');break;
   }
  }
 }
 // Scan whole interpreter HEREDOC bodies as a unit: URLs, fetches and eval
 // commonly appear on different lines. Use the declared delimiter rather
 // than mixing unrelated shell commands or interpreting any input as code.
 const evaluatesRemote=(lang,body)=>{
  // An opaque/dynamically concatenated URL does not make fetch+eval safe.
  // The network fetch and execution sink together are the trust violation.
  if(/^python/i.test(lang))return /\b(?:urllib(?:\.request)?|requests(?:\.get)?)\b/i.test(body)&&
    /\b(?:exec|eval)\s*\(/i.test(body);
  if(/^node/i.test(lang))return /\bfetch\s*\(/i.test(body)&&/\beval\s*\(/i.test(body);
  if(lang==='ruby')return /\b(?:URI\.open|open-uri)\b/i.test(body)&&/\beval\b/i.test(body);
  if(lang==='perl')return /\b(?:LWP::Simple|get\s*\()/i.test(body)&&/\beval\b/i.test(body);
  if(lang==='php')return /\bfile_get_contents\s*\(/i.test(body)&&/\beval\s*\(/i.test(body);
  return false;
 };
 for(const line of active.split('\n')){
  const current=line.trim();
  const interpreter=/^\s*(python(?:[0-9.]+)?|node(?:js)?|ruby|perl|php)\b/i.exec(current);
  if(interpreter&&evaluatesRemote(interpreter[1],current))
   flag('REMOTE_INTERPRETER_FETCH_EXECUTION');
 }
 // A quoted -c program may span physical lines; parsing by each line
 // separately would lose its network fetch or eval/exec sink.
 for(const cmd of active.matchAll(/(?:^|[;&\n])\s*(python(?:[0-9.]+)?)\s+(?:(?:-[A-Za-z]{1,5})\s+){0,8}-c\s+(['"])([\s\S]{0,8192}?)\2(?=\s|$|[;&])/gi)){
  if(evaluatesRemote(cmd[1],cmd[3]))flag('REMOTE_INTERPRETER_FETCH_EXECUTION');
 }
 const segments=active.split('\n');
 for(let i=0;i<segments.length;i++){
  const match=/^\s*(python(?:[0-9.]+)?|node(?:js)?|ruby|perl|php)\b[^\n]*?<<-?\s*['"]?([A-Za-z_][A-Za-z0-9_]*)['"]?\s*(?:&|;|&&|\|\|)?\s*(?:[012]?>{1,2}\s*[A-Za-z0-9_./-]+)?\s*$/.exec(segments[i]);
  if(!match)continue;
  const body=[],delimiter=match[2];let found=false;
  for(let j=i+1;j<segments.length;j++){
   if(segments[j].trim()===delimiter){i=j;found=true;break;}
   body.push(segments[j]);
  }
  // A missing terminator still feeds the remaining script to the interpreter.
  // Inspect body until EOF rather than silently skipping this program.
  if(evaluatesRemote(match[1],body.join('\n')))
   flag('REMOTE_INTERPRETER_FETCH_EXECUTION');
 }
 // A fetch piped into iex is a sink only when the downloaded response can
 // actually reach the pipeline. -OutFile without -PassThru suppresses output.
 const powershellScript=/^(?:pwsh|powershell)(?:\.exe)?(?:\s|$)/i.test(String(shell))||
  /(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b/i.test(active);
 const suppressesPowerShellOutput=script=>
  /(?:^|\s)-OutFile(?=\s|:|$)/i.test(script)&&
  !/(?:^|\s)-PassThru(?=\s|[)"']|$)/i.test(script);
 const powershellPipelines=/(?:^|[;\n])\s*(?:(?:pwsh|powershell)(?:\.exe)?\b[^\r\n;|]{0,300}?\s+-(?:Command|c)\s+["']?\s*)?((?:iwr|Invoke-WebRequest|irm|Invoke-RestMethod)\b[^\r\n;|]{0,4096})\|\s*&?\s*(?:iex|Invoke-Expression)\b/gi;
 if(powershellScript){
  for(const p of active.matchAll(powershellPipelines)){
   if(!suppressesPowerShellOutput(p[1])){flag('REMOTE_POWERSHELL_EXECUTION');break;}
  }
 }
 // Fetch expressions passed as arguments to iex (not necessarily pipelines).
 const endpoint=String.raw`(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s)\r\n]+)`;
 // The same bounded parameter grammar is applied both BEFORE and AFTER
 // -Uri. Values can be simple literals, quoted strings or @{...} hashtables.
 const psOptionValue=String.raw`(?:"[^"\r\n]*"|'[^'\r\n]*'|[a-zA-Z0-9._/-]+|@\{[^}\r\n]{1,300}\})`;
 const psNamedOption=String.raw`-[A-Za-z][A-Za-z0-9-]*(?:\s+`+psOptionValue+String.raw`)?`;
 const uriArgs=String.raw`(?:`+psNamedOption+String.raw`\s+){0,6}(?:-Uri(?:\s+|:))?`;
 const fetchWeb=String.raw`(?:iwr|Invoke-WebRequest)\b\s+`+uriArgs+endpoint;
 const fetchRest=String.raw`(?:irm|Invoke-RestMethod)\b\s+`+uriArgs+endpoint;
 const trailingSwitches=String.raw`(?:\s+`+psNamedOption+String.raw`){0,6}`;
 const argumentSource=String.raw`(?:iex|Invoke-Expression)\s+(?:-Command\s+)?\(*\s*(?:`+fetchWeb+trailingSwitches+String.raw`\s*\)\s*\.Content|`+fetchRest+trailingSwitches+String.raw`\s*\)(?:\s*\.Content)?)\s*\)*`;
 const argument=new RegExp(String.raw`(?:^|[;\n])\s*`+argumentSource,'i');
 const explicitArgument=new RegExp(String.raw`(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b[^\r\n]*?\s+-(?:Command|c)\s+["']?\s*`+argumentSource,'i');
 const matchesArgument=(match)=>!!match&&!suppressesPowerShellOutput(match[0]);
 if((powershellScript&&matchesArgument(argument.exec(active)))||
   matchesArgument(explicitArgument.exec(active)))
  flag('REMOTE_POWERSHELL_EXECUTION');
 // WebClient DownloadString can feed iex through the argument as well as a
 // pipeline. Avoid interpreting quoted Write-Host documentation as commands.
 const webclient=String.raw`(?:New-Object\s+Net\.WebClient|System\.Net\.WebClient)\b[\s\S]{0,200}\bDownloadString\s*\([\s\S]{0,200}?\)`;
 const webclientPipe=new RegExp(webclient+String.raw`\s*\)*\s*\|\s*&?\s*(?:iex|Invoke-Expression)\b`,'i');
 const webclientArgument=new RegExp(String.raw`(?:^|[;\n])\s*(?:iex|Invoke-Expression)\s+\(*\s*`+webclient+String.raw`\s*\)*`,'i');
 const explicitWebclient=new RegExp(String.raw`(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b[^\r\n]*?\s+-(?:Command|c)\s+["']?\s*(?:iex|Invoke-Expression)\s+\(*\s*`+webclient,'i');
 if(powershellScript&&(webclientPipe.test(active)||webclientArgument.test(active)||explicitWebclient.test(active)))
  flag('REMOTE_POWERSHELL_EXECUTION');
 // Shell continuations and lines following a trailing | form one pipeline.
 // Keep canonicalization bounded and never execute decoded content.
 const shellActive=active.replace(/\\\r?\n/g,'')
  .replace(/\|[ \t]*\n[ \t]*/g,'| ');
 // Encoded bytes routed into eval or interpreter code argument.
 if(/(?:^|[;&\n])\s*(?:eval|python(?:[0-9.]+)?\s+-c|bash\s+-c|node\s+-e)\b[^\r\n]*\$\([^\r\n]*\|\s*base64\s+(?:-d|--decode)\b/i.test(shellActive)||
  /\|\s*base64\s+(?:-d|--decode)\b[^\r\n]*?\|\s*(?:bash|sh|dash|zsh|ksh|python(?:[0-9.]+)?|node|ruby|perl|php)\b/i.test(shellActive))
  flag('REMOTE_ENCODED_EVAL');
 // Mutable package tags and container tags are not content-addressed.
 if(/(?:^|[;&\n])\s*(?:npx(?:\s+--yes)?|pnpm\s+dlx)\s+[\w@./-]+@(?:latest|next|canary|alpha|beta|dev|master|main)\b/i.test(active)||
  /(?:^|[;&\n])\s*(?:npx|npm\s+exec)\b[^\r\n;|&]{0,2000}(?:--package|-p)(?:=|\s+)[\w@./-]+@(?:latest|next|canary|alpha|beta|dev|master|main)\b/i.test(active))
  flag('MUTABLE_PACKAGE_EXECUTION');
 if(/(?:^|[;&\n])\s*docker\s+run\b[^\r\n]*\s+(?:[\w./-]+:)(?:latest|edge|nightly|dev|stable|main)\b/i.test(active))
  flag('MUTABLE_CONTAINER_EXECUTION');
 return [...findings];
}
module.exports={findingsForScript,MAX_SCRIPT};
