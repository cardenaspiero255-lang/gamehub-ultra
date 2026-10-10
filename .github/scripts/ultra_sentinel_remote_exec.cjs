'use strict';
// Shared read-only classification. Sources are UNTRUSTED DATA: do not execute.
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
const codeArgumentSubstitution=new RegExp(
 '(?:^|[\\s;|&])'+EXECUTOR+'\\b(?:\\s+[-\\w=./]+){0,4}\\s+-(?:c|e|r)\\s+["\\x27]?\\$\\(\\s*'+DOWNLOAD+'\\b','i'
);
const evalSubstitution=new RegExp(
 '(?:^|[\\s;|&])eval\\s+["\\x27]?\\$\\(\\s*'+DOWNLOAD+'\\b','i'
);
const downloaderOutputOption=new RegExp(
 '\\b'+DOWNLOAD+'\\b[^\\n;|&]{0,160}\\s+-(?:o|O)\\s+>\\(\\s*'+EXECUTOR+'\\b','i'
);
const stdinProcessSubstitution=new RegExp(
 '(?:^|[\\s;|&])'+EXECUTOR+'\\b(?:\\s+[-\\w=./]+){0,4}\\s+<\\s*<\\(\\s*'+DOWNLOAD+'\\b','i'
);
const stdinCommandSubstitution=new RegExp(
 '(?:^|[\\s;|&])'+EXECUTOR+'\\b(?:\\s+[-\\w=./]+){0,4}\\s+<{3}\\s*["\\x27]?\\$\\(\\s*'+DOWNLOAD+'\\b','i'
);
function normalizeShellTokens(input){
 // Bounded shell-token canonicalization for scanning, not evaluation.
 return String(input).replace(/\\\r?\n/g,'')
  .replace(/\\(?=[A-Za-z])/g,'')
  .replace(/\x24\x27([A-Za-z]*)\x27/g,'$1')
  .replace(/(['"])([A-Za-z]*)\1/g,'$2');
}
function hasRemoteProcessSubstitution(script){
 if(typeof script!=='string')return false;
 const source=normalizeShellTokens(script);
 return executorSubstitution.test(source)||
  sourceSubstitution.test(source)||
  redirectSubstitution.test(source)||
  codeArgumentSubstitution.test(source)||
  evalSubstitution.test(source)||
  downloaderOutputOption.test(source)||
  stdinProcessSubstitution.test(source)||
  stdinCommandSubstitution.test(source);
}
module.exports={hasRemoteProcessSubstitution};
