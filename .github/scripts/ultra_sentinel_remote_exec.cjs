'use strict';
// Shared read-only detection of downloaded code executed via process substitution.
// Only classify; NEVER execute scripts, shell commands or workflow data.
const EXECUTOR='(?:bash|sh|dash|zsh|ksh|fish|python(?:[0-9]+(?:\\.[0-9]+)?)?|node(?:js)?|ruby|perl|php|pwsh|powershell)';
const DOWNLOAD='(?:curl|wget)';
const executorSubstitution=new RegExp(
 '(?:^|[\\s;|&])(?:'+EXECUTOR+'|source)\\b(?:\\s+[-\\w=./]+){0,4}\\s+<\\(\\s*'+DOWNLOAD+'\\b','i'
);
const sourceSubstitution=new RegExp(
 '(?:^|[\\s;|&])\\.\\s+<\\(\\s*'+DOWNLOAD+'\\b','i'
);
const redirectSubstitution=new RegExp(
 '\\b'+DOWNLOAD+'\\b[^\\n;|&]{0,160}>\\s*>\\(\\s*'+EXECUTOR+'\\b','i'
);
function normalizeShellTokens(input){
 // Bounded canonicalization for conservative scanning, NOT shell evaluation.
 return String(input).replace(/\\\r?\n/g,'')
  .replace(/\\(?=[A-Za-z])/g,'')
  .replace(/\x24\x27([A-Za-z]*)\x27/g,'$1')
  .replace(/(['"])([A-Za-z]*)\1/g,'$2');
}
function hasRemoteProcessSubstitution(script){
 if(typeof script!=='string')return false;
 const source=normalizeShellTokens(script);
 return executorSubstitution.test(source)||
  sourceSubstitution.test(source)||redirectSubstitution.test(source);
}
module.exports={hasRemoteProcessSubstitution};
