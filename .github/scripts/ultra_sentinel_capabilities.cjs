'use strict';
/**
 * Ultra Sentinel capability provenance.
 * Candidate AST is untrusted DATA. This module never imports or evaluates it.
 * ESLint Scope (pinned) resolves lexical references and shadowing; a bounded
 * monotone analysis propagates high-risk capabilities. Unknown extraction and
 * unsupported syntax fail closed, not silently 'ADVISORY'.
 */
const eslintScope=require('eslint-scope');
const NODE_BUDGET=50000;
const MAX_ROUNDS=32;
const MAX_DEPTH=18;
const GLOBAL=new Set(['globalThis','global']);
const EXEC=new Set(['eval','Function','AsyncFunction','GeneratorFunction']);
const VM=new Set(['runInThisContext','runInNewContext','runInContext',
 'compileFunction','createScript','Script','SourceTextModule']);
const RUN=new Set(['runInThisContext','runInNewContext','runInContext','runInContext']);
const builtin=new Map([
 ['globalThis','global'],['global','global'],['eval','exec'],
 ['Function','exec'],['AsyncFunction','exec'],['GeneratorFunction','exec'],
 ['require','loader'],['module','module'],['vm','vm'],['Reflect','reflect']
]);
function stringValue(node,depth=0){
 if(!node||depth>8)return null;
 if(node.type==='Literal')return typeof node.value==='string'?node.value:null;
 if(node.type==='TemplateLiteral'){
  let value='';
  for(let i=0;i<node.quasis.length;i++){
   if(typeof node.quasis[i]?.value?.cooked!=='string')return null;
   value+=node.quasis[i].value.cooked;
   if(i<node.expressions.length){
    const part=stringValue(node.expressions[i],depth+1);
    if(part===null)return null;
    value+=part;
   }
  }
  return value;
 }
 if(node.type==='BinaryExpression'&&node.operator==='+'){
  const a=stringValue(node.left,depth+1),b=stringValue(node.right,depth+1);
  return a===null||b===null?null:a+b;
 }
 return null;
}
function propName(node){
 return node?.computed?stringValue(node.property):
  node?.property?.type==='Identifier'?node.property.name:stringValue(node?.property);
}
function keyName(prop){
 return prop?.computed?stringValue(prop.key):
  prop?.key?.type==='Identifier'?prop.key.name:stringValue(prop?.key);
}
function traverse(node,visitor){
 let count=0;
 function walk(current,parent=null,key=''){
  if(!current||typeof current!=='object'||typeof current.type!=='string')return;
  if(++count>NODE_BUDGET)throw Error('AST budget exceeded');
  visitor(current,parent,key);
  for(const [k,value] of Object.entries(current)){
   if(k==='range'||k==='loc'||k==='start'||k==='end')continue;
   if(Array.isArray(value))for(const part of value)walk(part,current,k);
   else walk(value,current,k);
  }
 }
 walk(node);
}
function findCapabilities(ast){
 const manager=eslintScope.analyze(ast,{ecmaVersion:2022,sourceType:'script',
  optimistic:true,ignoreEval:true,impliedStrict:false});
 const reference=new WeakMap(),declaration=new WeakMap();
 for(const scope of manager.scopes){
  for(const ref of scope.references)reference.set(ref.identifier,ref.resolved||null);
  for(const variable of scope.variables)
   for(const id of variable.identifiers)declaration.set(id,variable);
 }
 const kinds=new Map(), objects=new Map(),bindings=[];
 const unknownIds=new Map();
 function symbol(node){
  if(node?.type!=='Identifier')return null;
  if(reference.has(node)){
   const variable=reference.get(node);
   return variable||'free:'+node.name;
  }
  return declaration.get(node)||'free:'+node.name;
 }
 function directKind(node){
  if(node?.type!=='Identifier')return null;
  const key=symbol(node);
  if(typeof key==='string'&&key.startsWith('free:'))
   return builtin.get(node.name)||null;
  return kinds.get(key)||null;
 }
 function propertyKinds(node,depth=0){
  if(!node||depth>MAX_DEPTH)throw Error('Object provenance depth exceeded');
  if(node.type==='ObjectExpression'){
   if(node.properties.some(p=>p.type==='SpreadElement'))return null;
   const result=new Map();
   for(const p of node.properties){
    if(p.type!=='Property')return null;
    const name=keyName(p);
    if(name===null)return null;
    const cap=kind(p.value,depth+1);
    if(cap)result.set(name,cap);
   }
   return result;
  }
  if(node.type==='Identifier')return objects.get(symbol(node))||null;
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   return propertyKinds(node.right,depth+1);
  return null;
 }
 function kind(node,depth=0){
  if(!node||depth>MAX_DEPTH)throw Error('Capability graph depth exceeded');
  if(node.type==='ChainExpression'||node.type==='AwaitExpression')
   return kind(node.expression||node.argument,depth+1);
  if(node.type==='Identifier')return directKind(node);
  if(node.type==='ImportExpression')
   return ['vm','node:vm'].includes(stringValue(node.source))?'vm':null;
  if(node.type==='MemberExpression'){
   const root=kind(node.object,depth+1),name=propName(node);
   const obj=propertyKinds(node.object,depth+1);
   if(obj){
    if(name!==null)return obj.get(name)||null;
    if([...obj.values()].some(v=>v==='exec'))return 'exec';
    return null;
   }
   if(root==='global'&&(EXEC.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='vm'&&(VM.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='script'&&(RUN.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='module'&&name==='require')return 'loader';
   if(root==='reflect'&&name==='get')return 'getter';
   if(root==='loader'&&name==='bind')return 'loader';
   return null;
  }
  if(node.type==='CallExpression'||node.type==='NewExpression'){
   const callee=kind(node.callee,depth+1);
   if(callee==='loader'&&['vm','node:vm'].includes(stringValue(node.arguments?.[0])))
    return 'vm';
   if(callee==='getter'){
    const root=kind(node.arguments?.[0],depth+1);
    const name=stringValue(node.arguments?.[1]);
    if(root==='global'&&(EXEC.has(name)||name===null))return 'exec';
    if(root==='vm'&&(VM.has(name)||name===null))return 'exec';
   }
   if(node.callee?.type==='MemberExpression'){
    const name=propName(node.callee);
    if(kind(node.callee.object,depth+1)==='vm'&&
       (name==='createScript'||name==='Script'))return 'script';
   }
   return callee==='loader'&&node.callee?.type==='MemberExpression'&&
    propName(node.callee)==='bind'?'loader':null;
  }
  if(node.type==='ArrayExpression'&&node.elements?.length){
   const entries=node.elements.map(n=>kind(n,depth+1));
   return entries[0]&&entries.every(v=>v===entries[0])?entries[0]:null;
  }
  if(node.type==='ConditionalExpression'){
   const a=kind(node.consequent,depth+1),b=kind(node.alternate,depth+1);
   return a&&a===b?a:null;
  }
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   return kind(node.right,depth+1);
  if(node.type==='SequenceExpression')
   return kind(node.expressions?.at(-1),depth+1);
  if(node.type==='ObjectExpression')return 'local-object';
  return null;
 }
 function bind(pattern,value){
  if(!pattern)return false;
  if(pattern.type==='AssignmentPattern')return bind(pattern.left,value);
  if(pattern.type==='Identifier'){
   const key=symbol(pattern),c=kind(value),props=propertyKinds(value);
   let changed=false;
   if(c&&c!=='local-object'&&!kinds.has(key)){kinds.set(key,c);changed=true;}
   if(props){
    const previous=objects.get(key);
    if(!previous||[...props].some(([k,v])=>previous.get(k)!==v)){
     objects.set(key,new Map(props));changed=true;
    }
   }
   return changed;
  }
  if(pattern.type!=='ObjectPattern')return false;
  const properties=propertyKinds(value),root=kind(value);
  let changed=false;
  for(const p of pattern.properties){
   const target=p.type==='RestElement'?p.argument:p.value;
   const dest=target?.type==='AssignmentPattern'?target.left:target;
   if(dest?.type!=='Identifier')continue;
   const name=p.type==='RestElement'?null:keyName(p);
   const found=properties?(name===null?null:properties.get(name)):null;
   let c=found||null;
   if(!c&&!properties){
    if(root==='global'&&(EXEC.has(name)||name===null))c='exec';
    else if(root==='vm'&&(VM.has(name)||name===null))c='exec';
    else if(root==='reflect'&&name==='get')c='getter';
    else if(root==='module'&&name==='require')c='loader';
    else if(!root&&(EXEC.has(name)||VM.has(name)))c='exec';
   }
   const key=symbol(dest);
   if(c&&!kinds.has(key)){kinds.set(key,c);changed=true;}
  }
  return changed;
 }
 traverse(ast,(node)=>{
  if(node.type==='VariableDeclarator'&&node.init)
   bindings.push([node.id,node.init]);
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   bindings.push([node.left,node.right]);
  if(node.type==='ForOfStatement'||node.type==='ForInStatement'){
   const left=node.left;
   if(left?.type==='VariableDeclaration')for(const decl of left.declarations)
    bindings.push([decl.id,node.right]);
   else bindings.push([left,node.right]);
  }
  if(['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(node.type))
   for(const param of node.params){
    const id=param.type==='AssignmentPattern'?param.left:param;
    if(id.type==='ObjectPattern')bindings.push([id,null]);
   }
  if(node.type==='CatchClause'&&node.param?.type==='ObjectPattern')
   bindings.push([node.param,null]);
 });
 let converged=false;
 for(let round=0;round<MAX_ROUNDS;round++){
  let changed=false;
  for(const [pattern,value]of bindings)changed=bind(pattern,value)||changed;
  if(!changed){converged=true;break;}
 }
 if(!converged)throw Error('Unbounded alias propagation');
 const sinks=[];
 traverse(ast,(node,parent,key)=>{
  if(node.type==='Identifier'){
   if(!reference.has(node))return;
   if(parent?.type==='MemberExpression'&&key==='property'&&!parent.computed)return;
   if(kind(node)==='exec')sinks.push(node);
  }else if(node.type==='MemberExpression'){
   if(kind(node)==='exec')sinks.push(node);
  }else if(node.type==='CallExpression'||node.type==='NewExpression'){
   if(kind(node.callee)==='exec')sinks.push(node);
   if(node.callee?.type==='MemberExpression'&&propName(node.callee)==='constructor')
    sinks.push(node);
  }
 });
 return sinks;
}
module.exports={findCapabilities};
