'use strict';
/**
 * V2 shadow-mode seed oracle. It independently identifies a narrow set of
 * undeniable global code-execution sinks using only AST and lexical scope.
 * It NEVER evaluates untrusted source, never emits source snippets and NEVER
 * marks input CLEAR. Unsupported flows remain NOT_PROVEN.
 *
 * This is diagnostic evidence, not a substitute for the primary analyzer.
 */
const eslintScope=require('eslint-scope');
const NODE_BUDGET=50000;
const SCHEMA='ultra-sentinel-shadow/v1';
function methodName(member){
 if(!member||member.type!=='MemberExpression')return null;
 if(!member.computed&&member.property?.type==='Identifier')
  return member.property.name;
 if(member.computed&&member.property?.type==='Literal'&&
    typeof member.property.value==='string')return member.property.value;
 return null;
}
function directSinkEvidence(ast){
 if(!ast||ast.type!=='Program')throw Error('Shadow oracle requires a parsed Program');
 const scopes=eslintScope.analyze(ast,{ecmaVersion:2022,sourceType:'script',
  optimistic:true,ignoreEval:true,impliedStrict:false});
 const free=new WeakSet();
 for(const scope of scopes.scopes)
  for(const reference of scope.references)
   if(!reference.resolved)free.add(reference.identifier);
 const isGlobal=(id,names)=>id?.type==='Identifier'&&
  names.includes(id.name)&&free.has(id);
 const findings=new Set();
 let visited=0;
 function walk(node){
  if(!node||typeof node!=='object'||typeof node.type!=='string')return;
  if(++visited>NODE_BUDGET)throw Error('Shadow AST node budget exceeded');
  if(node.type==='CallExpression'||node.type==='NewExpression'){
   const callee=node.callee;
   if(isGlobal(callee,['eval']))
    findings.add('global-eval');
   if(isGlobal(callee,['Function']))
    findings.add('global-function-constructor');
   if(callee?.type==='MemberExpression'&&
      isGlobal(callee.object,['globalThis','global'])&&
      ['eval','Function'].includes(methodName(callee)))
    findings.add('global-member-exec');
  }
  for(const [key,value] of Object.entries(node)){
   if(['range','loc','start','end'].includes(key))continue;
   if(Array.isArray(value)){
    for(const element of value)walk(element);
   }else walk(value);
  }
 }
 walk(ast);
 const rules=Object.freeze([...findings].sort());
 return Object.freeze({schema:SCHEMA,
  decision:rules.length?'DEFINITE_SINK':'NOT_PROVEN',rules});
}
function reconcile(primary,evidence){
 if(!['CLEAR','BLOCKER','INCOMPLETE'].includes(primary))
  throw Error('Unknown primary reviewer decision');
 if(!evidence||evidence.schema!==SCHEMA||
    !['DEFINITE_SINK','NOT_PROVEN'].includes(evidence.decision))
  throw Error('Unknown or invalid shadow evidence');
 const disposition=evidence.decision==='DEFINITE_SINK'&&primary==='CLEAR'?
  'ESCALATE':evidence.decision==='DEFINITE_SINK'&&primary==='INCOMPLETE'?
   'NEEDS_REVIEW':'NO_NEW_EVIDENCE';
 // The report is intentionally constant-size and contains no code or PII.
 return Object.freeze({schema:SCHEMA,primary,shadow:evidence.decision,
  disposition,ruleCount:evidence.rules.length});
}
module.exports={directSinkEvidence,reconcile,SCHEMA,NODE_BUDGET};
