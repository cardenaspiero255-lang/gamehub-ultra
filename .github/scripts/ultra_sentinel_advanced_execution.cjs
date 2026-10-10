'use strict';
/**
 * Read-only rules for remote execution spanning commands or interpreters.
 * Never execute input, decode an attacker command, access network or files.
 * This is a bounded scanner, not a full shell interpreter.
 */
const MAX_SCRIPT=160000;
const posix=require('node:path').posix;
const normalizedFile=name=>posix.normalize(name.replace(/^(?:\.\/)+/,''));
const {parseShellCommands,literalFileToken,literalCommandName}=require('./ultra_sentinel_command_ir.cjs');
// Consume arguments of interpreter flags before identifying the script file.
// Unknown options fail closed instead of treating their values as executables.
function interpreterFileOperand(command,flag){
 const language=command.name.toLowerCase(),argv=command.words.slice(1);
 const python=/^python(?:[0-9.]+)?$/.test(language);
 const node=language==='node';
 const valueOptions=python?new Set(['-W','-X','--check-hash-based-pycs']):
  node?new Set(['-r','--require','--import','--loader',
    '--experimental-loader','--conditions','-C']):new Set();
 const evalOptions=python?new Set(['-c','-m']):node?new Set(['-e','--eval','-p','--print']):
  new Set(['-c','-e','-r']);
 const plainFlags=python?
  /^-(?:B|E|I|O|OO|P|q|s|S|u|v|V|x)$/:
  node?/^-(?:v|V|h|i)$/:
  /^-(?:e|f|i|l|n|r|s|u|v|x|p)$/;
 for(let i=0;i<argv.length;i++){
  const a=argv[i];
  if(a==='--')return argv[i+1]||null;
  if(evalOptions.has(a))return null; // Inline code/module is not a script path.
  if(valueOptions.has(a)){
   if(++i>=argv.length)flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
   continue;
  }
  if(python&&/^-(?:W|X).+/.test(a))continue;
  if(node&&/^(?:--require=|--import=|--loader=|--conditions=|-r.).+/.test(a))continue;
  if(a.startsWith('-')||a.startsWith('+')){
   if(plainFlags.test(a)||/^[+-][abefhkmnptuvxBCDEHPT]+$/.test(a)||
      (python&&/^-[BEOIPqSsuvx]+$/.test(a)))continue;
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return null;
  }
  return a;
 }
 return null;
}
/**
 * Remove transparent Bash command prefixes before classification. Unknown
 * wrapper options remain explicitly INCOMPLETE, never silently trusted.
 */
function normalizeInvocation(command,flag){
 const args=command.words;let i=0;
 for(let depth=0;depth<5;depth++){
  const name=literalCommandName(args[i]||'');
  if(name==='command'){
   i++;
   while(i<args.length&&/^-[A-Za-z-]+$/.test(args[i])){
    if(args[i]==='-v'||args[i]==='-V')return null;
    if(args[i]==='-p'){i++;continue;}
    if(args[i]==='--'){i++;break;}
    flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return null;
   }
  }else if(name==='env'){
   i++;
   while(i<args.length){
    const w=args[i];
    if(w==='--'){i++;break;}
    if(/^[A-Za-z_][\w]*=/.test(w)){i++;continue;}
    if(/^(?:-i|--ignore-environment|-0|--null)$/.test(w)){i++;continue;}
    if(/^(?:-u|--unset|-C|--chdir)$/.test(w)){
     if(++i>=args.length){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return null;}
     i++;continue;
    }
    if(/^(?:--unset=|--chdir=)/.test(w)){i++;continue;}
    if(w.startsWith('-')){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return null;}
    break;
   }
  }else if(/^(?:sudo|nohup|nice|timeout|stdbuf|setsid|time)$/.test(name||'')){
   // Further wrappers have option parsers of their own. Do not certify them.
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return null;
  }else{
   if(!name){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return null;}
   return {...command,name,words:args.slice(i),raw:args.slice(i).join(' ')};
  }
  if(i>=args.length)return null;
 }
 flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
 return null;
}
/**
 * Infer only filenames that receive remote RESPONSE bytes, not logs and not
 * intermediate shell redirection targets. Descriptors evolve left-to-right.
 * Redirects are distinct from curl -o and wget -O output-document options.
 */
function downloadedFiles(command,flag){
 const raw=command.words.slice(1).join(' '),wget=command.name==='wget';
 const outputPattern=wget?
  /(?:^|\s)(?:-[a-zA-Z]*O\s*|--output-document(?:=|\s+))([a-z0-9_./'"-]+)(?=\s|$)/g:
  /(?:^|\s)(?:-[fsSLkvIqNn]*o\s*|--output(?:=|\s+))([a-z0-9_./'"-]+)(?=\s|$)/g;
 const direct=[...raw.matchAll(outputPattern)];
 const outputDirs=[...raw.matchAll(/(?:^|\s)--output-dir(?:=|\s+)([a-z0-9_./'"-]+)(?=\s|$)/gi)];
 const outDir=outputDirs.at(-1);
 const dir=outDir?literalFileToken(outDir[1]):null;
 if(outDir&&!dir){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return [];}
 const files=new Set();
 for(const d of direct){
  const file=literalFileToken(d[1]);
  if(!file){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;}
  if(file==='-'||file==='.'||file==='..')continue;
  files.add(normalizedFile(dir&&!file.startsWith('/')?
   posix.join(dir,file):file));
 }
 // Track fd 1 and fd 2, including duplication/closing. A prior ">temp"
 // only truncates temp; if stdout is redirected again, temp is not tainted.
 const fds={1:null,2:null};
 const redirects=/(?:^|\s)([12]?>{1,2}[&|]?|&>{1,2})\s*([a-z0-9_./'"-]+)(?=\s|$)/g;
 for(const match of raw.matchAll(redirects)){
  const op=match[1],argument=match[2];
  const isDup=op.endsWith('&');
  const destFd=op.startsWith('2')?2:1;
  if(isDup&&(/^[012]$/.test(argument)||argument==='-')){
   fds[destFd]=argument==='-'?null:fds[argument]||null;
   continue;
  }
  const dest=literalFileToken(argument);
  if(!dest){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;}
  if(isDup&&op!=='>&'){
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
  }
  if(op.startsWith('&')||op==='>&'){
   fds[1]=dest;fds[2]=dest;
  }else fds[destFd]=dest;
 }
 const urlCount=command.words.slice(1).filter(w=>/^['"]?https?:\/\//i.test(w)).length;
 // GNU Wget normally emits the document into a file, not stdout. Curl
 // emits response bytes to stdout only for URLs lacking -o destinations.
 const finalOutput=direct.at(-1);
 const wgetStdout=!!finalOutput&&literalFileToken(finalOutput[1])==='-';
 const mayEmitRemoteStdout=wget?wgetStdout:urlCount>direct.length;
 if(mayEmitRemoteStdout&&fds[1]&&fds[1]!=='.'&&fds[1]!=='..')
  files.add(normalizedFile(fds[1]));
 return [...files];
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
 const parsed=parseShellCommands(active);
 const commands=parsed.commands.map(c=>normalizeInvocation(c,flag)).filter(Boolean);
 // No source containing an uncertain path command is certified as clean.
 if(parsed.incomplete&&/\b(?:curl|wget|mkdir|cp|mv|ln)\b/i.test(active))
  flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
 // Data-flow facts use one command IR. The command boundary, argument words
 // and source offsets can no longer disagree across downloads and aliases.
 const createdDirs=[];
 for(const mkdir of commands.filter(c=>c.name==='mkdir')){
  const tokens=mkdir.words.slice(1);
  let options=true,unknown=false;
  for(const token of tokens){
   if(options&&token==='--'){options=false;continue;}
   if(options&&/^(?:-p|--parents|-v|--verbose)$/.test(token))continue;
   if(options&&token.startsWith('-')){unknown=true;break;}
   options=false;
   const name=literalFileToken(token);
   if(!name){unknown=true;break;}
   createdDirs.push({at:mkdir.start,name});
  }
  if(unknown)flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
 }
 const aliases=[];
 for(const m of commands.filter(c=>/^(?:ln|cp|mv|install)$/.test(c.name))){
  const kind=m.name,tokens=m.words.slice(1);
  const flags=[],operands=[],rawOperands=[];
  let afterDash=false,invalid=false,targetDir=null,directoryOnly=false;
  for(let i=0;i<tokens.length;i++){
   const word=tokens[i];
   if(!afterDash&&word==='--'){afterDash=true;continue;}
   if(kind==='install'&&!afterDash){
    // GNU install copies files unless -d/--directory creates directories.
    if(word==='-d'||word==='--directory'){directoryOnly=true;continue;}
    if(/^-([vCDTbpcs]+)d$/.test(word)){directoryOnly=true;continue;}
    if(/^(?:-m|--mode|-g|--group|-o|--owner)$/.test(word)){
     const value=tokens[++i];
     if(!value||/[$\`]/.test(value)){invalid=true;break;}
     continue;
    }
    if(/^(?:-[mgo].+|--(?:mode|group|owner)=.+)$/.test(word))continue;
    if(/^(?:-D|-T|-v|-C|-p|-s|--compare|--no-target-directory|--verbose|--preserve-timestamps|--strip)$/.test(word)){
     flags.push(word);continue;
    }
    if(word.startsWith('--')&&!/^(?:--target-directory(?:=.+)?)$/.test(word)){
     invalid=true;break;
    }
   }

   if(!afterDash&&(word==='-t'||word==='--target-directory')){
    const value=tokens[++i];
    targetDir=value?literalFileToken(value):null;
    if(!targetDir){invalid=true;break;}
    flags.push('-t');continue;
   }
   if(!afterDash&&word.startsWith('--target-directory=')){
    targetDir=literalFileToken(word.slice('--target-directory='.length));
    if(!targetDir){invalid=true;break;}
    flags.push('-t');continue;
   }
   // GNU short options may be clustered: -vt DIR, -svt DIR and
   // -vtdir all have the same -t operand semantics.
   if(!afterDash&&/^-[A-Za-z]+$/.test(word)){
    const t=word.indexOf('t',1);
    if(t>=0){
     if(t>1)flags.push('-'+word.slice(1,t));
     const attached=word.slice(t+1);
     const rawDest=attached||tokens[++i];
     targetDir=rawDest?literalFileToken(rawDest):null;
     if(!targetDir){invalid=true;break;}
     flags.push('-t');
    }else flags.push(word);
    continue;
   }
   if(!afterDash&&word.startsWith('-')){
    if(!/^--[a-z-]+$/.test(word)){invalid=true;break;}
    flags.push(word);continue;
   }
   const operand=literalFileToken(word);
   if(!operand){invalid=true;break;}
   operands.push(operand);
   rawOperands.push(word);
  }
  if(kind==='install'&&directoryOnly)continue;
  if(invalid||(targetDir?operands.length!==1:operands.length!==2)){
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
  }
  const origin=operands[0],destination=targetDir||operands[1];
  const targetIsDirectory=!!targetDir||
   /\/['"]*$/.test(rawOperands[1]||'')||
   createdDirs.some(d=>d.name===normalizedFile(destination)&&d.at<m.start);
  const to=normalizedFile(targetIsDirectory?
   posix.join(destination,posix.basename(origin)):destination);
  const symbolic=kind==='ln'&&flags.some(x=>
   x==='--symbolic'||/^-[A-Za-z]*s[A-Za-z]*$/.test(x));
  const from=normalizedFile(symbolic&&!origin.startsWith('/')?
   posix.join(posix.dirname(to),origin):origin);
  aliases.push({at:m.start,kind,from,to});
 }
 for(const d of commands.filter(c=>/^(?:curl|wget)$/i.test(c.name))){
  const command=d.words.slice(1).join(' ');
  if(!/\bhttps?:\/\//i.test(command))continue;
  for(const file of downloadedFiles(d,flag)){
   for(const inv of commands.filter(c=>c.start>=d.end)){
    const interpreter=/^(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9.]+)?|node|ruby|perl|php|source|\.)$/i.test(inv.name);
    const word=interpreter?interpreterFileOperand(inv,flag):inv.words[0];
    if(!word)continue;
    const calledFile=literalFileToken(word);
    if(!calledFile)continue;
    const executedAt=inv.start;
    const visible=aliases.filter(a=>a.at<executedAt&&(a.at>=d.start||a.kind==='ln'));
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
    if(tainted.has(calledFile)){flag('REMOTE_DOWNLOADED_FILE_EXECUTION');break;}
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
 // Interpret only actual unquoted PowerShell switches. Options embedded
 // inside UserAgent/Headers strings must NOT cancel -OutFile suppression.
 const suppressesPowerShellOutput=script=>{
  const switches=[];let quote=null,word='',escaped=false;
  const push=()=>{if(word){switches.push(word);word='';}};
  for(const ch of script){
   if(quote){
    if(quote==='"'&&ch.charCodeAt(0)===96){escaped=!escaped;continue;}
    if(ch===quote&&!escaped)quote=null;
    escaped=false;continue;
   }
   if(ch==='"'||ch==="'"){push();quote=ch;continue;}
   if(/[\s(),|]/.test(ch)){push();continue;}
   word+=ch;
  }
  push();
  const out=switches.some(w=>/^-OutFile(?::.*)?$/i.test(w));
  const pass=switches.some(w=>/^-PassT(?:h(?:r(?:u)?)?)?(?::\$true)?$/i.test(w));
  return out&&!pass;
 };
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
 const psNamedOption=String.raw`-[A-Za-z][A-Za-z0-9-]*(?::(?:\$true|\$false))?(?:\s+`+psOptionValue+String.raw`)?`;
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
