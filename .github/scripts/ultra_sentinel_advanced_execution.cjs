'use strict';
/**
 * Read-only rules for remote execution spanning commands or interpreters.
 * Never execute input, decode an attacker command, access network or files.
 * This is a bounded scanner, not a full shell interpreter.
 */
const MAX_SCRIPT=160000;
function findingsForScript(source){
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
  const output=/(?:^|\s)-(?:o|O)\s+([a-z0-9_./-]+)(?=\s|$)/i.exec(command);
  if(!output)continue;
  const file=output[1];
  if(!file.includes('/')||file==='-')continue;
  const escape=file.replace(/[.*+?^$()|[\]{}\\]/g,'\\$&');
  const after=active.slice(d.index+d[0].length);
  const invocation=new RegExp('(?:^|[;\\n]|&&|\\|\\|)\\s*(?:(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9.]+)?|node|ruby|perl|php|source|\\.)\\s+(?:[-\\w]+\\s+)*|)(?:'+escape+')(?=\\s|$|[;&])','i');
  if(invocation.test(after))flag('REMOTE_DOWNLOADED_FILE_EXECUTION');
 }
 // Native interpreter network fetch paired with code evaluation.
 for(const line of active.split('\n')){
  const current=line.trim();
  if(!/\bhttps?:\/\//i.test(current))continue;
  if(/\bpython(?:[0-9.]+)?\b/i.test(current)&&
   /\b(?:urllib(?:\.request)?|requests(?:\.get)?)\b/i.test(current)&&
   /\bexec\s*\(/i.test(current))flag('REMOTE_INTERPRETER_FETCH_EXECUTION');
  if(/\bnode(?:js)?\b/i.test(current)&&
   /\bfetch\s*\(/i.test(current)&&/\beval\s*\(/i.test(current))
   flag('REMOTE_INTERPRETER_FETCH_EXECUTION');
  if(/\bruby\b/i.test(current)&&
   /\b(?:URI\.open|open-uri)\b/i.test(current)&&/\beval\b/i.test(current))
   flag('REMOTE_INTERPRETER_FETCH_EXECUTION');
  if(/\bperl\b/i.test(current)&&
   /\b(?:LWP::Simple|get\s*\()/i.test(current)&&/\beval\b/i.test(current))
   flag('REMOTE_INTERPRETER_FETCH_EXECUTION');
 }
 if(/(?:^|[;\n])\s*(?:pwsh|powershell)(?:\.exe)?\b[^\r\n]*\b(?:irm|Invoke-RestMethod)\b[^\r\n]*\|\s*(?:iex|Invoke-Expression)\b/i.test(active))
  flag('REMOTE_POWERSHELL_EXECUTION');
 // Encoded bytes routed into eval or interpreter code argument.
 if(/(?:^|[;&\n])\s*(?:eval|python(?:[0-9.]+)?\s+-c|bash\s+-c|node\s+-e)\b[^\r\n]*\$\([^\r\n]*\|\s*base64\s+(?:-d|--decode)\b/i.test(active))
  flag('REMOTE_ENCODED_EVAL');
 // Mutable package tags and container tags are not content-addressed.
 if(/(?:^|[;&\n])\s*(?:npx(?:\s+--yes)?|pnpm\s+dlx)\s+[\w@./-]+@(?:latest|next|canary|alpha|beta|dev|master|main)\b/i.test(active))
  flag('MUTABLE_PACKAGE_EXECUTION');
 if(/(?:^|[;&\n])\s*docker\s+run\b[^\r\n]*\s+(?:[\w./-]+:)(?:latest|edge|nightly|dev|stable|main)\b/i.test(active))
  flag('MUTABLE_CONTAINER_EXECUTION');
 return [...findings];
}
module.exports={findingsForScript,MAX_SCRIPT};
