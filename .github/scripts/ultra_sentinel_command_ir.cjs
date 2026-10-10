'use strict';
/**
 * Bounded, read-only command representation for shell security rules.
 * Never execute, interpolate or evaluate any untrusted script.
 *
 * Recognized grammar: literal words and ;, newline, &&, ||, |, & operators.
 * Anything outside the supported syntax remains untrusted: consumers must
 * fail closed on incomplete parses or nonliteral path/option operands.
 */
const posix=require('node:path').posix;
const MAX_SOURCE=160000,MAX_COMMANDS=4096,MAX_WORDS=64,MAX_WORD_LENGTH=2048;
// Reserved control words introduce compound shell flow beyond this bounded IR.
// Fail closed rather than interpreting `then curl` as an ordinary command.
const COMPOUND_WORDS=new Set(['if','then','elif','else','fi','while','until',
 'do','done','for','select','case','esac','in','function','coproc']);
function literalFileToken(token){
 if(typeof token!=='string'||token.length>MAX_WORD_LENGTH)return null;
 let quote=null,decoded='';
 for(const ch of token){
  if(ch==='"'||ch==="'"){
   if(quote===ch){quote=null;continue;}
   if(quote===null){quote=ch;continue;}
  }
  if(ch==='\\'||ch==='$'||ch.charCodeAt(0)===96)return null;
  decoded+=ch;
 }
 if(quote!==null||!/^(?:\.\/)?[a-z0-9_.\/-]+$/i.test(decoded))return null;
 return posix.normalize(decoded.replace(/^(?:\.\/)+/,''));
}
// Command names get shell quote removal without path normalization.
// Relative local executables (./curl) must NOT impersonate curl in PATH.
function literalCommandName(word){
 if(typeof word!=='string'||word.length>MAX_WORD_LENGTH)return null;
 let quote=null,result='';
 for(const ch of word){
  if(ch==="'"||ch==='"'){
   if(quote===ch){quote=null;continue;}
   if(!quote){quote=ch;continue;}
  }
  if(ch==='$'||ch==='\\'||ch.charCodeAt(0)===96)return null;
  result+=ch;
 }
 return quote||!/^[a-zA-Z0-9_./-]+$/.test(result)?null:result;
}
/**
 * @returns {{commands:Array<{name:string,words:string[],raw:string,start:number,end:number,operator:string|null}>,incomplete:boolean}}
 */
function parseShellCommands(source){
 if(typeof source!=='string'||source.length>MAX_SOURCE)return {commands:[],incomplete:true};
 const commands=[];let incomplete=false,quote=null,escaped=false;
 let word='',words=[],start=-1,pending=null;
 const wordDone=()=>{
  if(!word)return;
  if(words.length>=MAX_WORDS)incomplete=true;
  else words.push(word);
  word='';
 };
 const commandDone=(end)=>{
  wordDone();
  if(words.length&&start>=0){
   if(commands.length>=MAX_COMMANDS)incomplete=true;
   else{
    const name=literalCommandName(words[0]);
    if(name===null||COMPOUND_WORDS.has(name))incomplete=true;
    commands.push({name:name||words[0],words,raw:source.slice(start,end).trimEnd(),start,end,operator:pending});
   }
  }
  words=[];start=-1;
 };
 for(let i=0;i<source.length;i++){
  const ch=source[i];
  if(!quote&&!escaped&&ch==='#'&&!word){
   commandDone(i);
   while(i<source.length&&source[i]!=='\n')i++;
   if(i<source.length){pending='\n';continue;}
   break;
  }
  // Bash combined stdout redirects (>&, >|, &>, &>>) are NOT control
  // operators. Leave them in the command so the sink analyzer can read them.
  const redirectionPart=!quote&&!escaped&&(
   ((ch==='&'||ch==='|')&&source[i-1]==='>')||
   (ch==='&'&&source[i+1]==='>'));
  if(!quote&&!escaped&&!redirectionPart&&(ch===';'||ch==='\n'||ch==='|'||ch==='&')){
   commandDone(i);
   let operator=ch;
   if((ch==='|'||ch==='&')&&source[i+1]===ch){operator+=ch;i++;}
   pending=operator;
   continue;
  }
  if(!quote&&!escaped&&/\s/.test(ch)){wordDone();continue;}
  if(start<0)start=i;
  // Dynamic expansions and grouping are outside the bounded IR grammar.
  // Refuse to certify these scripts clean if a remote-source command occurs.
  if(!escaped&&quote!=="'"&&(ch==='$'||ch.charCodeAt(0)===96||
    (!quote&&/[()<]/.test(ch))))incomplete=true;
  if(ch==='\\'&&quote!=="'"&&!escaped){escaped=true;word+=ch;}
  else{
   if(escaped){
    if(ch==='\n')incomplete=true; // line continuations require richer shell grammar
    escaped=false;
   }else if(ch==='"'||ch==="'"){
    if(quote===ch)quote=null;
    else if(quote===null)quote=ch;
   }
   word+=ch;
  }
  if(word.length>MAX_WORD_LENGTH)incomplete=true;
 }
 commandDone(source.length);
 if(quote||escaped)incomplete=true;
 return {commands,incomplete};
}
module.exports={literalFileToken,literalCommandName,parseShellCommands,MAX_SOURCE};
