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
const AST_DEPTH_BUDGET=256;
// Preflight is intentionally independent of the primary analyzer. Reject
// oversized or cyclic ASTs BEFORE eslint-scope allocates scope/reference graphs.
// WeakSet and the traversal stack are per-call: no AST or source is retained.
function preflightShadowAst(ast){
 const discovered=new WeakSet(),pending=[];
 let count=0;
 function enqueue(node,depth){
  if(!node||typeof node!=='object'||typeof node.type!=='string')return;
  if(depth>AST_DEPTH_BUDGET)throw Error('Shadow AST nesting budget exceeded');
  if(discovered.has(node))throw Error('Shadow AST cycle or sharing rejected');
  discovered.add(node);
  if(++count>NODE_BUDGET)throw Error('Shadow AST node budget exceeded');
  pending.push({node,depth});
 }
 enqueue(ast,0);
 while(pending.length){
  const {node,depth}=pending.pop();
  for(const key in node){
   if(!Object.hasOwn(node,key)||['range','loc','start','end'].includes(key))continue;
   const value=node[key];
   if(Array.isArray(value)){
    if(value.length>NODE_BUDGET)throw Error('Shadow AST node budget exceeded');
    for(const element of value)enqueue(element,depth+1);
   }else enqueue(value,depth+1);
  }
 }
}
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
 preflightShadowAst(ast);
 const scopes=eslintScope.analyze(ast,{ecmaVersion:2022,sourceType:'script',
  optimistic:true,ignoreEval:true,impliedStrict:false});
 const free=new WeakSet();
 for(const scope of scopes.scopes)
  for(const reference of scope.references)
   if(!reference.resolved)free.add(reference.identifier);
 const isGlobal=(id,names)=>id?.type==='Identifier'&&
  names.includes(id.name)&&free.has(id);
 const tainted=new Set();
 const revoke=name=>{
  tainted.add(name);
  if(name==='globalThis'||name==='global'){
   for(const key of ['globalThis','global','eval','Function'])tainted.add(key);
  }
 };
 function recordWrite(target){
  if(target?.type==='Identifier'&&
     isGlobal(target,['globalThis','global','eval','Function']))revoke(target.name);
  if(target?.type==='MemberExpression'&&
     isGlobal(target.object,['globalThis','global'])){
   const name=methodName(target);
   if(['eval','Function'].includes(name))revoke(name);
   else if(name===null)revoke('globalThis');
  }
 }
 function scanWrites(node){
  if(!node||typeof node!=='object'||typeof node.type!=='string')return;
  if(node.type==='AssignmentExpression')recordWrite(node.left);
  if(node.type==='UpdateExpression'||node.type==='UnaryExpression'&&
     node.operator==='delete')recordWrite(node.argument);
  if(node.type==='CallExpression'&&node.callee?.type==='MemberExpression'&&
     isGlobal(node.callee.object,['Object','Reflect'])){
   const action=methodName(node.callee);
   if(['defineProperty','set','setPrototypeOf','assign','defineProperties'].includes(action)&&
      isGlobal(node.arguments?.[0],['globalThis','global'])){
    const key=node.arguments?.[1];
    if(key?.type==='Literal'&&['eval','Function'].includes(key.value))revoke(key.value);
    else revoke('globalThis');
   }
  }
  for(const [key,value] of Object.entries(node)){
   if(['range','loc','start','end'].includes(key))continue;
   if(Array.isArray(value))for(const element of value)scanWrites(element);
   else scanWrites(value);
  }
 }
 scanWrites(ast);
 const findings=new Set();
 let visited=0;
 function walk(node){
  if(!node||typeof node!=='object'||typeof node.type!=='string')return;
  if(++visited>NODE_BUDGET)throw Error('Shadow AST node budget exceeded');
  if(node.type==='CallExpression'||node.type==='NewExpression'){
   const callee=node.callee;
   if(isGlobal(callee,['eval'])&&!tainted.has('eval'))
    findings.add('global-eval');
   if(isGlobal(callee,['Function'])&&!tainted.has('Function'))
    findings.add('global-function-constructor');
   if(callee?.type==='MemberExpression'&&
      isGlobal(callee.object,['globalThis','global'])&&
      ['eval','Function'].includes(methodName(callee))&&
      !tainted.has(callee.object.name)&&!tainted.has(methodName(callee)))
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
// Research phase: aggregate bounded differential evidence without promoting
// the shadow analyzer to an authoritative security gate. Unknown or malformed
// input never contributes a CLEAR verdict, and no user source is logged.
const BATCH_LIMIT=32,SOURCE_CHAR_LIMIT=4096,BATCH_SCHEMA='ultra-sentinel-shadow-batch/v1';
function auditShadowBatch(sources){
 if(!Array.isArray(sources)||sources.length>BATCH_LIMIT||
    sources.some(source=>typeof source!=='string'||source.length>SOURCE_CHAR_LIMIT))
  throw Error('Shadow audit input exceeds hard budget');
 const acorn=require('acorn');
 const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
 let escalations=0,incomplete=0,provenSinks=0;
 for(const source of sources){
  try{
   const ast=acorn.parse(source,{ecmaVersion:2022,sourceType:'script',ranges:true});
   const shadow=directSinkEvidence(ast);
   if(shadow.decision==='DEFINITE_SINK')provenSinks++;
   let primary='INCOMPLETE';
   try{primary=findCapabilities(ast).length?'BLOCKER':'CLEAR';}
   catch{incomplete++;}
   const decision=reconcile(primary,shadow);
   if(decision.disposition==='ESCALATE')escalations++;
  }catch{
   // Parsing, scope analysis and budget errors require human inspection.
   // This batch never blesses such input as safe.
   incomplete++;
  }
 }
 return Object.freeze({schema:BATCH_SCHEMA,mode:'DIAGNOSTIC_ONLY',
  inspected:sources.length,provenSinks,escalations,incomplete,
  requiresReview:escalations>0||incomplete>0});
}
module.exports={directSinkEvidence,reconcile,auditShadowBatch,SCHEMA,NODE_BUDGET,
 BATCH_LIMIT,SOURCE_CHAR_LIMIT};

