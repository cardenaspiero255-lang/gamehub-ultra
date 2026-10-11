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
 // -e (errexit) and -r (restricted) are flags for POSIX shells, not
 // inline-evaluation switches. Ruby/Perl/PHP have different -e/-r semantics.
 const isShell=/^(?:bash|sh|dash|zsh|ksh|fish)$/.test(language);
 const evalOptions=python?new Set(['-c','-m']):node?new Set(['-e','--eval','-p','--print']):
  isShell?new Set(['-c']):new Set(['-c','-e','-r']);
 const plainFlags=python?
  /^-(?:B|E|I|O|OO|P|q|s|S|u|v|V|x)$/:
  node?/^-(?:v|V|h|i)$/:
  /^-(?:e|f|i|l|n|r|s|u|v|x|p)$/;
 for(let i=0;i<argv.length;i++){
  // Normalize the literal token after shell quote removal. Opaque options
  // are incomplete rather than silently interpreted as script paths.
  const raw=argv[i];
  let a=literalCommandName(raw);
  // Bash permits +x/+e option bundles; do not regress legitimate flags.
  // Both quoted and unquoted forms represent the same literal argument.
  if(a===null&&/^\+[a-zA-Z]+$/.test(raw))a=raw;
  const plusQuoted=/^(['"])(\+[a-zA-Z]+)\1$/.exec(raw);
  if(a===null&&plusQuoted)a=plusQuoted[2];
  if(a===null){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return null;}
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

/**
 * Redirects are shell syntax, not executable operands. Remove only bounded,
 * literal output/fd redirects when examining an exec invocation; an opaque
 * target fails closed rather than silently certifying remote execution clean.
 * Quoted redirection-like arguments are not redirections.
 */
function stripExecRedirections(words,flag){
 const kept=[];
 for(let i=0;i<words.length;i++){
  const token=words[i];
  const redirect=/^(?:(?:[0-9]+)?(?:<>|<&|<|>\||>&|>>|>)|&(?:>>|>))(.*)$/.exec(token);
  if(!redirect){kept.push(token);continue;}
  const target=redirect[1]||words[++i];
  const valid=!!target&&(literalFileToken(target)!==null||
    /^(?:&[0-9]+|&-)$/.test(target)||/^(?:[0-9]+|-)$/.test(target));
  if(!valid){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return null;}
 }
 return kept;
}

function normalizeInvocation(command,flag){
 // A redirect may appear before, between or after executable words in Bash.
 let hasExec=false;
 for(const rawWord of command.words){
  if(literalCommandName(rawWord)==='exec'){hasExec=true;break;}
 }
 const args=hasExec?stripExecRedirections(command.words,flag):command.words;
 if(!args)return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};
 let i=0;
 for(let depth=0;depth<5;depth++){
  const name=literalCommandName(args[i]||'');
  if(name==='command'){
   i++;
   while(i<args.length&&/^-[A-Za-z-]+$/.test(literalCommandName(args[i]||'')||'')){
    const option=literalCommandName(args[i]);
    if(option==='-v'||option==='-V')return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};
    if(option==='-p'){i++;continue;}
    if(option==='--'){i++;break;}
    flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};
   }
  }else if(name==='builtin'){
   // Bash builtin accepts -- before the nested builtin, including repeated
   // command/builtin wrappers. The sentinel must not lose this execution sink.
   i++;
   if(literalCommandName(args[i]||'')==='--')i++;
   const nested=literalCommandName(args[i]||'');
   if(nested==='command'||nested==='builtin')continue;
   if(nested==='source'||nested==='.'||nested==='exec'){
    return {name:nested,words:args.slice(i),raw:args.slice(i).join(' '),
     start:command.start,end:command.end};
   }
   let unverifiedBuiltin=false;
   for(const w of args.slice(i)){
    if(/^(?:curl|wget|bash|sh|install|eval)$/.test(w)){
     unverifiedBuiltin=true;break;
    }
   }
   if(unverifiedBuiltin)flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
   return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};
  }else if(name==='env'||/^\/(?:usr\/(?:local\/)?)?bin\/env$/.test(name)){
   i++;
   while(i<args.length){
    const raw=args[i];
    // GNU env parses quote-stripped arguments (including '--' and -i).
    // Variable assignments are data; their contents are not executed.
    if(/^[A-Za-z_][\w]*=/.test(raw)||/^(?:'[^']*'|"[^"]*")$/.test(raw)&&
       /^[A-Za-z_][\w]*=/.test(raw.slice(1,-1))){i++;continue;}
    const w=literalCommandName(raw);
    if(w===null){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};}
    if(w==='--'){i++;break;}
    if(/^(?:-i|--ignore-environment|-0|--null)$/.test(w)){i++;continue;}
    if(/^(?:-u|--unset|-C|--chdir)$/.test(w)){
     if(++i>=args.length){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};}
     i++;continue;
    }
    if(/^(?:--unset=|--chdir=)/.test(w)){i++;continue;}
    if(w.startsWith('-')){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};}
    break;
   }
  }else if(/^(?:sudo|nohup|nice|timeout|stdbuf|setsid|time)$/.test(name||'')){
   // These wrappers have their own semantics. Fail closed only where a
   // remote-source or execution command could be hidden behind the wrapper;
   // ordinary privileged administrative commands are not findings.
   let unverifiedWrapper=false;
   for(const w of args.slice(i+1)){
    if(/^(?:curl|wget|bash|sh|dash|zsh|python(?:[0-9.]+)?|node|install)$/.test(w)){
     unverifiedWrapper=true;break;
    }
   }
   if(unverifiedWrapper)flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
   return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};
  }else{
   // A dynamic/compound word has already been reported by the bounded IR
   // when relevant. Do not produce standalone false positives for inert code.
   if(!name)return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};
   return {name,words:args.slice(i),raw:args.slice(i).join(' '),
    start:command.start,end:command.end};
  }
  if(i>=args.length)return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};
 }
 flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
 return {name:String(''),words:[],raw:String(''),start:command.start,end:command.end};
}
/**
 * Infer only filenames that receive remote RESPONSE bytes, not logs and not
 * intermediate shell redirection targets. Descriptors evolve left-to-right.
 * Redirects are distinct from curl -o and wget -O output-document options.
 */
// Value-bearing flags are consumed before recognizing the standalone `--`
// delimiter. A literal `--` may itself be an option's VALUE.
// This is a bounded allowlist, not a complete curl/wget option parser.
const CURL_VALUE_FLAGS=new Set([
 '-A','--user-agent','-H','--header','-b','--cookie','-c','--cookie-jar',
 '-w','--write-out','-E','--cert','--cert-type','--key-type',
 '--oauth2-bearer','--trace','--trace-ascii','--trace-config',
 '-D','--dump-header',
 '-d','--data','--data-raw','--data-binary','--data-urlencode','-F','--form',
 '--form-string','-e','--referer','-u','--user','-x','--proxy','-X','--request',
 '-K','--config','--url','--url-query','--resolve','--connect-to','--interface',
 '-U','--proxy-user',
 '--cacert','--capath','--cert','--key','--limit-rate','--retry',
 '--retry-delay','--retry-max-time','--max-time','--connect-timeout'
]);
const WGET_VALUE_FLAGS=new Set([
 '-U','--user-agent','--referer','-e','--execute','-t','--tries',
 '-T','--timeout','--header','--post-data','--post-file','--proxy-user',
 '--proxy-password','-i','--input-file','-P','--directory-prefix',
 '-A','--accept','-R','--reject','--bind-address','--ca-certificate',
 '--certificate','--private-key','--password','--user'
]);
// Canonicalize literal HTTP(S) words by stripping shell quotes, never evaluating.
function literalShellWord(token){
 if(typeof token!=='string'||token.length>2048)return null;
 let quote=null,decoded='';
 for(const ch of token){
  if(ch==='"'||ch==="'"){
   if(quote===ch){quote=null;continue;}
   if(quote===null){quote=ch;continue;}
  }
  if(ch==='\\'||ch==='$'||ch.charCodeAt(0)===96)return null;
  decoded+=ch;
 }
 return quote===null&&decoded!==''&&!/\s/.test(decoded)?decoded:null;
}
function literalRemoteUrlToken(token){
 const decoded=literalShellWord(token);
 // Accept DNS/IPv4 and bracketed IPv6 authorities; no dynamic shell words.
 return decoded!==null&&
  /^https?:\/\/(?:[a-z0-9._~!%:-]+@)?(?:[a-z0-9][a-z0-9._:-]*|\[[0-9a-f:.]+(?:%25[a-z0-9._~-]+)?\](?::[0-9]{1,5})?)(?:[/?#][^\s]*)?$/i.test(decoded);
}
function hasRemoteSource(command){
 const words=command.words.slice(1);
 const valueFlags=command.name==='wget'?WGET_VALUE_FLAGS:CURL_VALUE_FLAGS;
 for(let i=0;i<words.length;i++){
  const w=literalShellWord(words[i])||words[i];
  if(w==='--url'){
   if(++i<words.length&&literalRemoteUrlToken(words[i]))return true;
   continue;
  }
  // curl accepts "--url VALUE", not "--url=VALUE"; never assert a download
  // for an invalid option solely because it embeds a URL.
  if(w.startsWith('--url='))continue;
  if(valueFlags.has(w)){i++;continue;}
  let embeddedFlag=false;
  for(const name of valueFlags)if(name.startsWith('--')&&w.startsWith(name+'=')){
   embeddedFlag=true;break;
  }
  if(embeddedFlag)continue;
  if(literalRemoteUrlToken(words[i]))return true;
 }
 return false;
}
function downloadedFiles(command,flag){
 const words=command.words.slice(1),wget=command.name==='wget';
 const allDirect=[];let dir=null,outputDirSeen=false,optionsActive=true,unknownDownloaderOption=false;
 const valueFlags=wget?WGET_VALUE_FLAGS:CURL_VALUE_FLAGS;
 // Read distinct shell WORDS, not a rejoined string. Rejoining words made
 // options/redirections embedded in quoted -H/-A values executable tokens.
 for(let i=0;i<words.length;i++){
  const word=words[i],literal=literalCommandName(word)||word;
  if(literal==='--'){optionsActive=false;continue;}
  if(!optionsActive)continue;
  // Consume the next word, even if its raw text is exactly '--'. This
  // prevents an option operand from switching off all subsequent scanning.
  if(valueFlags.has(literal)){
   if(++i>=words.length)flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
   continue;
  }
  // Attached values of supported long switches belong to the switch, even
  // when the value looks like an option terminator or a filename argument.
  let embeddedFlag=false;
  for(const flagName of valueFlags)if(flagName.startsWith('--')&&
     literal.startsWith(flagName+'=')){
   embeddedFlag=true;break;
  }
  if(embeddedFlag)continue;
  let value=null,isOutput=false,knownDirectoryOption=false;
  if(wget){
   const adjacent=/^-[A-Za-z]*O(.*)$/.exec(literal);
   const long=/^--output-document=(.*)$/.exec(literal);
   if(adjacent){isOutput=true;value=adjacent[1]||words[++i];}
   else if(literal==='--output-document'){isOutput=true;value=words[++i];}
   else if(long){isOutput=true;value=long[1];}
  }else{
   const adjacent=/^-[fsSLkvIqNn]*o(.*)$/.exec(literal);
   const long=/^--output=(.*)$/.exec(literal);
   if(adjacent){isOutput=true;value=adjacent[1]||words[++i];}
   else if(literal==='--output'){isOutput=true;value=words[++i];}
   else if(long){isOutput=true;value=long[1];}
   const dirInline=/^--output-dir=(.*)$/.exec(literal);
   if(literal==='--output-dir'||dirInline){
    knownDirectoryOption=true;
    outputDirSeen=true;
    const rawDir=dirInline?dirInline[1]:words[++i];
    dir=literalFileToken(rawDir);
    if(!dir){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');return {files:[],unknownDownloaderOption:Boolean(true)};}
   }
  }
  if(!isOutput&&!knownDirectoryOption&&literal.startsWith('-')&&literal!=='-'&&
     !/^-([fsSLkvIqNn]+)$/.test(literal)&&
     !/^(?:--(?:silent|show-error|fail|location|insecure|verbose|head|include|no-buffer|netrc|compressed|disable|progress-bar|fail-with-body|location-trusted|retry-all-errors|no-progress|quiet)|-q)$/.test(literal)){
   // A value-bearing unmodelled option can swallow '--' or the next token.
   // Never certify a possibly executed remote download as harmless.
   unknownDownloaderOption=true;
  }
  if(isOutput){
   if(typeof value!=='string'){
    flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
   }
   allDirect.push(value);
  }
 }
 const direct=wget?allDirect.slice(-1):allDirect;
 const files=new Set();
 for(const value of direct){
  const file=literalFileToken(value);
  if(!file){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;}
  if(file==='-'||file==='.'||file==='..')continue;
  files.add(normalizedFile(outputDirSeen&&dir&&!file.startsWith('/')?
   posix.join(dir,file):file));
 }
 // Simulate fd redirections left-to-right. Only a literal shell redirect
 // operator at a word's START counts; quoted argument text is inert data.
 const MAX_FD=32;
 const fds=new Map([[1,null],[2,null]]);
 const redirect=/^((?:\d{0,3})?>{1,2}[&|]?|&>{1,2})(.*)$/;
 for(let i=0;i<words.length;i++){
  const match=redirect.exec(words[i]);
  if(!match)continue;
  const op=match[1],argument=match[2]||words[++i];
  const numbered=/^(\d+)>/.exec(op);
  const destFd=numbered?Number(numbered[1]):1;
  if(!Number.isSafeInteger(destFd)||destFd>MAX_FD){
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
  }
  if(typeof argument!=='string'){
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
  }
  const isDup=op.endsWith('&');
  // Perform literal shell quote removal before deciding whether >&"3"
  // is an FD copy. Escaped/dynamic operands remain inconclusive.
  const dest=literalFileToken(argument);
  if(!dest){flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;}
  if(isDup&&(/^\d+$/.test(dest)||dest==='-')){
   const sourceFd=Number(dest);
   if(dest!=='-'&&(!Number.isSafeInteger(sourceFd)||sourceFd>MAX_FD||
     !fds.has(sourceFd))){
    flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
   }
   fds.set(destFd,dest==='-'?null:(fds.get(sourceFd)||null));
   continue;
  }
  if(isDup&&op!=='>&'){
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
  }
  if(op.startsWith('&')||op==='>&'){
   fds.set(1,dest);fds.set(2,dest);
  }else fds.set(destFd,dest);
 }
 let urlCount=0;
 for(const word of words)if(literalRemoteUrlToken(word))urlCount++;
 const wgetStdout=direct.length>0&&literalFileToken(direct.at(-1))==='-';
 const mayEmitRemoteStdout=wget?wgetStdout:urlCount>direct.length;
 const finalStdout=fds.get(1);
 if(mayEmitRemoteStdout&&finalStdout&&finalStdout!=='.'&&finalStdout!=='..')
  files.add(normalizedFile(finalStdout));
 // Ambiguous options may consume later output operands; never claim confirmed HIGH.
 return {files:unknownDownloaderOption?[]:[...files],unknownDownloaderOption};
}
function findingsForScript(source,{shell=''}={}){
 if(typeof source!=='string')return [];
 // Direct callers must never interpret an unscannable script as clean.
 // Workflow YAML parsing independently rejects oversized sources.
 if(source.length>MAX_SCRIPT)return ['REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'];
 const findings=new Set();
 // Local mkdir/cp/timeout and dynamic local paths do not by themselves
 // imply remote downloaded code. Keep all actual downloader uncertainty HIGH,
 // including quote-concatenated command names resolved by the shell IR.
 let remotelySourced=false;
 const flag=rule=>{
  if(rule==='REMOTE_EXECUTION_ANALYSIS_INCOMPLETE'&&!remotelySourced)return;
  findings.add(rule);
 };
 const lines=source.split(/\r?\n/);
 // Literal echo/printf of attack documentation is not executable code.
 const activeLines=[];
 for(const line of lines){
  if(!(/^\s*(?:echo|printf)\s+(['"])[^$\x60]*\1\s*$/.test(line)||
   /^\s*#/.test(line)))activeLines.push(line);
 }
 const active=activeLines.join('\n');
 // Analyze filename aliases with command ordering: a link created *after*
 // an attempted execution must not turn an unrelated command into a finding.
 // Links created before a download are considered: curl can overwrite the
 // linked inode, so that alias can still execute the downloaded bytes.
 const parsed=parseShellCommands(active);
 remotelySourced=/\b(?:curl|wget)\b/i.test(active);
 if(!remotelySourced)for(const cmd of parsed.commands){
  for(const word of cmd.words){
   if(/^(?:curl|wget)$/i.test(literalCommandName(word)||'')){
    remotelySourced=true;break;
   }
  }
  if(remotelySourced)break;
 }
 const commands=[];
 for(const item of parsed.commands){
  const invoked=normalizeInvocation(item,flag);
  if(invoked.name)commands.push(invoked);
 }
 // No source containing an uncertain path command is certified as clean.
 if(parsed.incomplete&&/\b(?:curl|wget|mkdir|cp|mv|ln)\b/i.test(active))
  flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
 // Data-flow facts use one command IR. The command boundary, argument words
 // and source offsets can no longer disagree across downloads and aliases.
 const createdDirs=[];
 for(const mkdir of commands){
  if(mkdir.name!=='mkdir')continue;
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
 for(const m of commands){
  if(!/^(?:ln|cp|mv|install)$/.test(m.name))continue;
  const kind=m.name,tokens=m.words.slice(1);
  let operandCount=0,origin=null,destOperand=null,rawDestOperand=null;
  let symbolic=false;
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
     continue;
    }
    if(word.startsWith('--')&&!/^(?:--target-directory(?:=.+)?)$/.test(word)){
     invalid=true;break;
    }
   }

   if(!afterDash&&(word==='-t'||word==='--target-directory')){
    const value=tokens[++i];
    targetDir=value?literalFileToken(value):null;
    if(!targetDir){invalid=true;break;}
    continue;
   }
   if(!afterDash&&word.startsWith('--target-directory=')){
    targetDir=literalFileToken(word.slice('--target-directory='.length));
    if(!targetDir){invalid=true;break;}
    continue;
   }
   // GNU short options may be clustered: -vt DIR, -svt DIR and
   // -vtdir all have the same -t operand semantics.
   if(!afterDash&&/^-[A-Za-z]+$/.test(word)){
    const t=word.indexOf('t',1);
    if(t>=0){
     if(t>1&&kind==='ln'&&word.slice(1,t).includes('s'))symbolic=true;
     const attached=word.slice(t+1);
     const rawDest=attached||tokens[++i];
     targetDir=rawDest?literalFileToken(rawDest):null;
     if(!targetDir){invalid=true;break;}
     // -t sets target directory, not symbolic-link mode.
    }else if(kind==='ln'&&/^-[A-Za-z]*s[A-Za-z]*$/.test(word))symbolic=true;
    continue;
   }
   if(!afterDash&&word.startsWith('-')){
    if(!/^--[a-z-]+$/.test(word)){invalid=true;break;}
    if(kind==='ln'&&word==='--symbolic')symbolic=true;
    continue;
   }
   const operand=literalFileToken(word);
   if(!operand){invalid=true;break;}
   if(operandCount===0)origin=operand;
   if(operandCount===1){destOperand=operand;rawDestOperand=word;}
   operandCount++;
  }
  if(kind==='install'&&directoryOnly)continue;
  if(invalid||(targetDir?operandCount!==1:operandCount!==2)){
   flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');continue;
  }
  const destination=targetDir||destOperand;
  // Conservative reviewer requires no mutable callback-slot carriers.
  // Explicit iteration preserves directory and symlink decisions unchanged.
  let preexistingDestinationDir=false;
  for(const dir of createdDirs){
   if(dir.name===normalizedFile(destination)&&dir.at<m.start){
    preexistingDestinationDir=true;break;
   }
  }
  const targetIsDirectory=!!targetDir||
   /\/['"]*$/.test(rawDestOperand||'')||preexistingDestinationDir;
  const to=normalizedFile(targetIsDirectory?
   posix.join(destination,posix.basename(origin)):destination);
  const from=normalizedFile(symbolic&&!origin.startsWith('/')?
   posix.join(posix.dirname(to),origin):origin);
  aliases.push({at:m.start,kind,from,to});
 }
 for(const d of commands){
  if(!/^(?:curl|wget)$/i.test(d.name))continue;
  if(!hasRemoteSource(d))continue;
  const output=downloadedFiles(d,flag);
  for(const file of output.files){
   for(const inv of commands){
    if(inv.start<d.end)continue;
    const interpreter=/^(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9.]+)?|node|ruby|perl|php|source|\.)$/i.test(inv.name);
    let word=interpreter?interpreterFileOperand(inv,flag):inv.words[0];
    if(inv.name==='exec'){
     let j=1,opaque=false;
     while(j<inv.words.length){
      const token=literalFileToken(inv.words[j]);
      if(!token){opaque=true;break;}
      if(token==='--'){j++;break;}
      if(/^-[cl]+$/.test(token)){j++;continue;}
      if(token==='-a'){
       if(j+1>=inv.words.length){opaque=true;break;}
       j+=2;continue;
      }
      if(token.startsWith('-')){opaque=true;break;}
      break;
     }
     if(opaque)flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
     // exec replaces this shell with a nested program and its script operand.
     const target=opaque?[]:inv.words.slice(j);
     const nested=normalizeInvocation({name:literalCommandName(target[0]||'')||String(''),
      words:target,raw:target.join(' '),start:inv.start,end:inv.end},flag);
     if(!nested||!nested.name){word=null;}
     else{
      const basename=posix.basename(nested.name);
      const knownInterpreter=/^(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9.]+)?|node|ruby|perl|php|source|\.)$/i.test(basename);
      const trustedInterpreterPath=nested.name===basename||
       /^\/(?:usr\/(?:local\/)?)?bin\/[^/]+$/.test(nested.name);
      if(knownInterpreter&&!trustedInterpreterPath)
       flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
      word=knownInterpreter&&trustedInterpreterPath?
       interpreterFileOperand({name:basename,words:nested.words,raw:nested.raw,
        start:nested.start,end:nested.end},flag):nested.words[0];
     }
    }
    if(!word)continue;
    const calledFile=literalFileToken(word);
    if(!calledFile)continue;
    const executedAt=inv.start;
    const visible=[];
    for(const alias of aliases){
     if(alias.at<executedAt&&(alias.at>=d.start||alias.kind==='ln'))
      visible.push(alias);
    }
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
  // Unknown downloader flags matter when downstream execution could occur.
  // A confirmed HIGH remains HIGH (not downgraded to INCOMPLETE); download-only
  // workflows are not condemned solely for options outside our small grammar.
  let laterInterpreter=false;
  for(const inv of commands){
   if(inv.start<d.end)continue;
   if(/^(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9.]+)?|node|ruby|perl|php|source|\.)$/i.test(inv.name)||
       (typeof inv.name==='string'&&inv.name.includes('/'))||
       (inv.name==='exec'&&inv.words.length>1)){
    laterInterpreter=true;break;
   }
  }
  if(output.unknownDownloaderOption&&!findings.has('REMOTE_DOWNLOADED_FILE_EXECUTION')&&
     laterInterpreter)flag('REMOTE_EXECUTION_ANALYSIS_INCOMPLETE');
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
  let out=false,pass=false;
  for(const option of switches){
   if(/^-OutFile(?::.*)?$/i.test(option))out=true;
   if(/^-PassT(?:h(?:r(?:u)?)?)?(?::\$true)?$/i.test(option))pass=true;
  }
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
