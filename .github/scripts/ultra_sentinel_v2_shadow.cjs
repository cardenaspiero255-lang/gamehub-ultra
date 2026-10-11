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
 // Resolve lexical bindings by identity, not by spelling: a local object
 // named "g" is never confused with an alias in a different scope.
 const bindings=new WeakMap();
 for(const scope of scopes.scopes){
  for(const variable of scope.variables)
   for(const def of variable.defs||[]){
    const id=def.name;
    if(id?.type==='Identifier')bindings.set(id,variable);
   }
  for(const reference of scope.references)
   if(reference.resolved)bindings.set(reference.identifier,reference.resolved);
 }
 const globalAliases=new Set(),builtinOwners=new Map(),builtinMutators=new Map();
 const globalObject=id=>isGlobal(id,['globalThis','global'])||
  id?.type==='Identifier'&&globalAliases.has(bindings.get(id));
 const owner=id=>isGlobal(id,['Object','Reflect'])?id.name:
  id?.type==='Identifier'?builtinOwners.get(bindings.get(id)):null;
 const allowedMutators=new Set(['defineProperty','set','setPrototypeOf','assign','defineProperties']);
 const mutator=expr=>{
  if(expr?.type==='Identifier')return builtinMutators.get(bindings.get(expr))||null;
  if(expr?.type!=='MemberExpression')return null;
  const name=methodName(expr);
  return owner(expr.object)&&allowedMutators.has(name)?name:null;
 };
 // Compute bounded may-alias closure over declarators, assignments and
 // structural patterns. Scope identity avoids conflating same-spelled locals;
 // unknown carriers never certify a native builtin.
 let aliasConverged=false;
 for(let round=0;round<64;round++){
  let changed=false;
  function markAlias(target,source,depth=0){
   if(!target||depth>32)return;
   if(target.type==='Identifier'){
    const binding=bindings.get(target);
    if(!binding)return;
    if(globalObject(source)&&!globalAliases.has(binding)){
     globalAliases.add(binding);changed=true;
    }
    const origin=owner(source);
    if(origin&&!builtinOwners.has(binding)){
     builtinOwners.set(binding,origin);changed=true;
    }
    const action=mutator(source);
    if(action&&!builtinMutators.has(binding)){
     builtinMutators.set(binding,action);changed=true;
    }
    return;
   }
   if(target.type==='AssignmentPattern'){
    markAlias(target.left,source,depth+1);
    markAlias(target.left,target.right,depth+1);
    return;
   }
   if(target.type==='RestElement'){
    markAlias(target.argument,source,depth+1);return;
   }
   if(target.type==='ArrayPattern'&&source?.type==='ArrayExpression'){
    for(let i=0;i<(target.elements||[]).length;i++)
     markAlias(target.elements[i],source.elements?.[i],depth+1);
    return;
   }
   if(target.type==='ObjectPattern'){
    for(const property of target.properties||[]){
     if(property.type!=='Property')continue;
     const key=property.key?.type==='Identifier'&&!property.computed?
      property.key.name:property.key?.type==='Literal'?
       String(property.key.value):null;
     if(key===null)continue;
     const intrinsic=owner(source);
     if(intrinsic&&allowedMutators.has(key)){
      // Destructuring a builtin method preserves its callable identity.
      const name=property.value;
      if(name?.type==='Identifier'){
       const binding=bindings.get(name);
       if(binding&&!builtinMutators.has(binding)){
        builtinMutators.set(binding,key);changed=true;
       }
      }
     }
     if(source?.type!=='ObjectExpression')continue;
     const values=(source.properties||[]).filter(item=>
      item.type==='Property'&&
       (item.key?.type==='Identifier'&&!item.computed?item.key.name:
        item.key?.type==='Literal'?String(item.key.value):null)===key);
     if(values.length)markAlias(property.value,values.at(-1).value,depth+1);
    }
   }
  }
  function visit(node){
   if(!node||typeof node!=='object'||typeof node.type!=='string')return;
   if(node.type==='VariableDeclarator')markAlias(node.id,node.init);
   if(node.type==='AssignmentExpression'&&node.operator==='=')
    markAlias(node.left,node.right);
   for(const [key,value] of Object.entries(node)){
    if(['range','loc','start','end'].includes(key))continue;
    if(Array.isArray(value))for(const element of value)visit(element);
    else visit(value);
   }
  }
  visit(ast);
  if(!changed){aliasConverged=true;break;}
 }
 if(!aliasConverged)throw Error('Shadow global alias closure budget exceeded');
 const tainted=new Set();
 const revoke=name=>{
  tainted.add(name);
  if(name==='globalThis'||name==='global'){
   for(const key of ['globalThis','global','eval','Function'])tainted.add(key);
  }
 };
 function recordWrite(target){
  if(!target)return;
  // Destructuring and iteration assign their nested targets, not just the
  // outer pattern node. Missing a write must never certify a builtin sink.
  if(target.type==='AssignmentPattern'){
   recordWrite(target.left);return;
  }
  if(target.type==='RestElement'){
   recordWrite(target.argument);return;
  }
  if(target.type==='ArrayPattern'){
   for(const item of target.elements||[])recordWrite(item);
   return;
  }
  if(target.type==='ObjectPattern'){
   for(const property of target.properties||[])
    recordWrite(property.type==='Property'?property.value:property.argument);
   return;
  }
  if(target.type==='Identifier'&&
     isGlobal(target,['globalThis','global','eval','Function']))revoke(target.name);
  if(target.type==='MemberExpression'&&globalObject(target.object)){
   const name=methodName(target);
   if(['eval','Function'].includes(name))revoke(name);
   else if(name===null)revoke('globalThis');
  }
 }
 function scanWrites(node){
  if(!node||typeof node!=='object'||typeof node.type!=='string')return;
  if(node.type==='AssignmentExpression')recordWrite(node.left);
  if((node.type==='ForOfStatement'||node.type==='ForInStatement')&&
     node.left?.type!=='VariableDeclaration')recordWrite(node.left);
  if(node.type==='UpdateExpression'||node.type==='UnaryExpression'&&
     node.operator==='delete')recordWrite(node.argument);
  if(node.type==='CallExpression'){
   const action=mutator(node.callee);
   if(action&&globalObject(node.arguments?.[0])){
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
 const allowedRules=new Set(['global-eval','global-function-constructor','global-member-exec']);
 const rules=evidence?.rules;
 if(!evidence||evidence.schema!==SCHEMA||
    !['DEFINITE_SINK','NOT_PROVEN'].includes(evidence.decision)||
    !Array.isArray(rules)||rules.length>allowedRules.size||
    rules.some(rule=>!allowedRules.has(rule))||
    rules.some((rule,index)=>index>0&&rules[index-1]>=rule)||
    (evidence.decision==='DEFINITE_SINK')!==(rules.length>0))
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

