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
 const aliases=[...active.matchAll(/(?:^|[;\n]|&&)\s*(ln|cp|mv)\s+((?:(?:--[a-z-]+|-[a-zA-Z]+|--)\s+){0,4})(?:"([a-z0-9_./-]+)"|'([a-z0-9_./-]+)'|([a-z0-9_./-]+))\s+(?:"([a-z0-9_./-]+)"|'([a-z0-9_./-]+)'|([a-z0-9_./-]+))(?=\s|$|[;&])/gi)]
  .map(m=>{
   const kind=m[1].toLowerCase(),origin=m[3]||m[4]||m[5];
   const destination=m[6]||m[7]||m[8];
   // Directory destinations create basename(source) at that location.
   const to=normalizedFile(destination.endsWith('/')?
    posix.join(destination,posix.basename(origin)):destination);
   // ln -s resolves its relative target from the link's own directory.
   // cp/mv and hard links resolve their source from the working directory.
   const symbolic=kind==='ln'&&/(?:--symbolic|-[A-Za-z]*s[A-Za-z]*)/.test(m[2]);
   const from=normalizedFile(symbolic&&!origin.startsWith('/')?
    posix.join(posix.dirname(to),origin):origin);
   return {at:m.index,kind,from,to};
  });
 for(const d of active.matchAll(download)){
  const command=d[1];
  if(!/\bhttps?:\/\//i.test(command))continue;
  // Recognize bounded no-argument short-flag clusters before -o/-O.
  // An unrestricted greedy [A-Za-z]* would eat filename letters up to a
  // later 'o' (e.g. -fsSLopayload -> incorrectly parsed as file 'ad').
  const output=/(?:^|\s)(?:-[fsSLkvIqNn]*[oO]\s*|--output(?:-document)?(?:=|\s+))(['"]?)([a-z0-9_./-]+)\1(?=\s|$)/i.exec(command);
  if(!output)continue;
  const outputDirs=[...command.matchAll(/(?:^|\s)--output-dir(?:=|\s+)(['"]?)([a-z0-9_./-]+)\1(?=\s|$)/gi)];
  // Repeated flags: curl uses the last --output-dir value.
  const outputDir=outputDirs.at(-1);
  // A constant --output-dir changes where the downloaded bytes land.
  const file=normalizedFile(outputDir&&!output[2].startsWith('/')?
   posix.join(outputDir[2],output[2]):output[2]);
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
 if(/(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b[^\r\n]*?\s+-(?:Command|c)\s+["']?\s*(?:iwr|Invoke-WebRequest|irm|Invoke-RestMethod)\b[^\r\n]*\|\s*&?\s*(?:iex|Invoke-Expression)\b/i.test(active)||
  (/^(?:pwsh|powershell)(?:\.exe)?(?:\s|$)/i.test(String(shell))&&
   /(?:^|[;\n])\s*(?:iwr|Invoke-WebRequest|irm|Invoke-RestMethod)\b[^\r\n]*\|\s*&?\s*(?:iex|Invoke-Expression)\b/i.test(active)))
  flag('REMOTE_POWERSHELL_EXECUTION');
 // Argument evaluation is execution even without a pipeline. Accept quoted
 // endpoints and runtime variables, as well as -Command and nested grouping.
 // Require an actual fetch expression consumed by the sink; iwr alone is safe.
 const powershellScript=/^(?:pwsh|powershell)(?:\.exe)?(?:\s|$)/i.test(String(shell))||
  /(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b/i.test(active);
 const endpoint=String.raw`(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s)\r\n]+)`;
 // Bounded PowerShell argument forms, including -Uri and non-executing
 // switches that may precede the endpoint.
 const uriArgs=String.raw`(?:(?:-(?:Verbose|Debug|UseBasicParsing)\s+){0,3})?(?:-Uri(?:\s+|:))?`;
 const fetchWeb=String.raw`(?:iwr|Invoke-WebRequest)\b\s+`+uriArgs+endpoint;
 const fetchRest=String.raw`(?:irm|Invoke-RestMethod)\b\s+`+uriArgs+endpoint;
 // WebRequest's response object needs .Content; RestMethod can return the
 // response body directly as a string, which is executable by iex.
 const trailingSwitches=String.raw`(?:\s+-(?:UseBasicParsing|Verbose|Debug)){0,4}`;
 const argumentSource=String.raw`(?:iex|Invoke-Expression)\s+(?:-Command\s+)?\(*\s*(?:`+fetchWeb+trailingSwitches+String.raw`\s*\)\s*\.Content|`+fetchRest+trailingSwitches+String.raw`\s*\)(?:\s*\.Content)?)\s*\)*`;
 const argument=new RegExp(String.raw`(?:^|[;\n])\s*`+argumentSource,'i');
 const explicitArgument=new RegExp(String.raw`(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b[^\r\n]*?\s+-(?:Command|c)\s+["']?\s*`+argumentSource,'i');
 if((powershellScript&&argument.test(active))||explicitArgument.test(active))
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
