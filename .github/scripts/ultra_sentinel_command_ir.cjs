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
   else commands.push({name:words[0],words,raw:source.slice(start,end).trimEnd(),start,end,operator:pending});
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
  if(!quote&&!escaped&&(ch===';'||ch==='\n'||ch==='|'||ch==='&')){
   commandDone(i);
   let operator=ch;
   if((ch==='|'||ch==='&')&&source[i+1]===ch){operator+=ch;i++;}
   pending=operator;
   continue;
  }
  if(!quote&&!escaped&&/\s/.test(ch)){wordDone();continue;}
  if(start<0)start=i;
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
module.exports={literalFileToken,parseShellCommands,MAX_SOURCE};
