'use strict';
/**
 * Read-only rules for remote execution spanning commands or interpreters.
 * Never execute input, decode an attacker command, access network or files.
 * This is a bounded scanner, not a full shell interpreter.
 */
const MAX_SCRIPT=160000;
const posix=require('node:path').posix;
const normalizedFile=name=>posix.normalize(name.replace(/^(?:\.\/)+/,''));
function findingsForScript(source,{shell=''}={}){
 if(typeof source!=='string'||source.length>MAX_SCRIPT)return [];
 const findings=new Set();
 const flag=rule=>findings.add(rule);
 const lines=source.split(/\r?\n/);
 // Literal echo/printf of attack documentation is not executable code.
 const active=lines.filter(line=>!(/^\s*(?:echo|printf)\s+(['"])[^$\x60]*\1\s*$/.test(line)||
  /^\s*#/.test(line))).join('\n');
 // Download then execute same local filename (multi-line, && and ;).
 const download=/(?:^|[;&\n])\s*(?:curl|wget)\b([^\r\n;|&]{1,4096})/gi;
 for(const d of active.matchAll(download)){
  const command=d[1];
  if(!/\bhttps?:\/\//i.test(command))continue;
  const output=/(?:^|\s)(?:-(?:o|O)\s*|--output(?:=|\s+))(['"]?)([a-z0-9_./-]+)\1(?=\s|$)/i.exec(command);
  if(!output)continue;
  const file=normalizedFile(output[2]);
  if(file==='-'||file==='.'||file==='..')continue;
  const after=active.slice(d.index+d[0].length);
  // Track simple local file aliases created after the download. Paths are
  // normalized lexically, without touching the filesystem or executing code.
  const targets=new Set([file]);
  for(const alias of active.matchAll(/(?:^|[;\n]|&&)\s*(?:ln(?:\s+-s)?|cp|mv)\s+([a-z0-9_./-]+)\s+([a-z0-9_./-]+)(?=\s|$|[;&])/gi)){
   if(targets.has(normalizedFile(alias[1])))targets.add(normalizedFile(alias[2]));
  }
  for(const candidate of targets){
   const escape=candidate.replace(/[.*+?^$()|[\]{}\\]/g,'\\$&');
   const invocation=new RegExp('(?:^|[;\\n]|&&|\\|\\|)\\s*(?:(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9.]+)?|node|ruby|perl|php|source|\\.)\\s+(?:[-\\w]+\\s+)*|)(?:\\.\\/)?(?:'+escape+')(?=\\s|$|[;&])','i');
   if(invocation.test(after)){flag('REMOTE_DOWNLOADED_FILE_EXECUTION');break;}
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
 // PowerShell also evaluates a fetched response through an argument:
 // iex (iwr URL).Content or Invoke-Expression (Invoke-WebRequest URL).Content.
 // Accept direct script under pwsh shell, or an explicit pwsh -Command call.
 const pwshArgument=/(?:^|[;\n])\s*(?:iex|Invoke-Expression)\s*\(\s*(?:iwr|Invoke-WebRequest|irm|Invoke-RestMethod)\s+https?:\/\/[^\r\n)"']+\s*\)\s*\.Content\b/i;
 const explicitPwshArgument=/(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b[^\r\n]*?\s+-(?:Command|c)\s+["']?\s*(?:iex|Invoke-Expression)\s*\(\s*(?:iwr|Invoke-WebRequest|irm|Invoke-RestMethod)\s+https?:\/\/[^\r\n)"']+\s*\)\s*\.Content\b/i;
 if((/^(?:pwsh|powershell)(?:\.exe)?(?:\s|$)/i.test(String(shell))&&
     pwshArgument.test(active))||explicitPwshArgument.test(active))
  flag('REMOTE_POWERSHELL_EXECUTION');
 // PowerShell's native WebClient is an alternative fetch-and-eval path,
 // including steps already running under shell: pwsh (no pwsh prefix).
 const powershellScript=/^(?:pwsh|powershell)(?:\.exe)?(?:\s|$)/i.test(String(shell))||
  /(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b/i.test(active);
 if(powershellScript&&
   /\b(?:New-Object\s+Net\.WebClient|System\.Net\.WebClient)\b[\s\S]{0,300}\bDownloadString\s*\([\s\S]{0,200}\)\s*\|\s*&?\s*(?:iex|Invoke-Expression)\b/i.test(active))
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
