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
 const objectLiterals=new WeakMap();
 function propertyKinds(node,depth=0){
  if(!node)return null;
  if(depth>MAX_DEPTH)throw Error('Object provenance depth exceeded');
  if(node.type==='ObjectExpression'){
   if(objectLiterals.has(node))return objectLiterals.get(node);
   const result=new Map();
   objectLiterals.set(node,result);
   for(const p of node.properties){
    if(p.type==='SpreadElement'){
     const spread=propertyKinds(p.argument,depth+1);
     if(!spread)throw Error('Unresolved object spread provenance');
     for(const [k,v]of spread)result.set(k,v);
     continue;
    }
    if(p.type!=='Property')throw Error('Unsupported object binding');
    const name=keyName(p);
    if(name===null)throw Error('Computed object key provenance unknown');
    const nested=propertyKinds(p.value,depth+1);
    const cap=kind(p.value,depth+1);
    if(nested)result.set(name,{properties:nested});
    else if(cap&&cap!=='local-object')result.set(name,cap);
   }
   return result;
  }
  if(node.type==='Identifier')return objects.get(symbol(node))||null;
  if(node.type==='MemberExpression'){
   const parent=propertyKinds(node.object,depth+1),name=propName(node);
   const entry=parent?.get(name);
   return entry&&typeof entry==='object'&&entry.properties?entry.properties:null;
  }
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   return propertyKinds(node.right,depth+1);
  if(node.type==='ConditionalExpression'){
   const a=propertyKinds(node.consequent,depth+1),b=propertyKinds(node.alternate,depth+1);
   if(!a&&!b)return null;
   if(!a||!b)throw Error('Conditional object provenance incomplete');
   const merged=new Map(a);
   for(const [k,v]of b){
    if(merged.has(k)&&merged.get(k)!==v)throw Error('Ambiguous branch property provenance');
    merged.set(k,v);
   }
   return merged;
  }
  return null;
 }
 function kind(node,depth=0){
  if(!node)return null;
  if(depth>MAX_DEPTH)throw Error('Capability graph depth exceeded');
  if(node.type==='ChainExpression'||node.type==='AwaitExpression')
   return kind(node.expression||node.argument,depth+1);
  if(node.type==='Identifier')return directKind(node);
  if(node.type==='ImportExpression')
   return ['vm','node:vm'].includes(stringValue(node.source))?'vm':null;
  if(node.type==='MemberExpression'){
   const root=kind(node.object,depth+1),name=propName(node);
   const obj=propertyKinds(node.object,depth+1);
   if(obj){
    if(name!==null){const v=obj.get(name);return typeof v==='string'?v:
      v&&typeof v==='object'&&v.properties?'local-object':null;}
    if([...obj.values()].some(v=>v==='exec'))return 'exec';
    return null;
   }
   if(root==='global'&&(EXEC.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='vm'&&(VM.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='script'&&(RUN.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='module'&&name==='require')return 'loader';
   if(root==='reflect'&&name==='get')return 'getter';
   if(root==='loader'&&name==='bind')return 'loader';
   if(name==='constructor'&&['FunctionExpression','ArrowFunctionExpression'].includes(node.object?.type))return 'exec';
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
   if(a===b)return a;
   if(!a)return b;
   if(!b)return a;
   if(a==='local-object'&&b==='local-object')return a;
   throw Error('Ambiguous conditional capability provenance');
  }
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   return kind(node.right,depth+1);
  if(node.type==='SequenceExpression')
   return kind(node.expressions?.at(-1),depth+1);
  if(node.type==='ObjectExpression')return 'local-object';
  return null;
 }
 const knownParameterInputs=new Set(),uncertainParameters=new Set();
 function assignIdentifier(id,cap,props,known=true){
  if(id?.type!=='Identifier')return false;
  const key=symbol(id);
  if(known)knownParameterInputs.add(key);
  let changed=false;
  if(cap&&cap!=='local-object'){
   const prev=kinds.get(key);
   if(prev&&prev!==cap)throw Error('Conflicting capability bindings');
   if(!prev){kinds.set(key,cap);changed=true;}
  }
  if(props){
   const prev=objects.get(key);
   if(!prev){objects.set(key,new Map(props));changed=true;}
   else for(const [name,value]of props)
    if(!prev.has(name)){prev.set(name,value);changed=true;}
    else if(prev.get(name)!==value&&
      (typeof value!=='object'||typeof prev.get(name)!=='object'))
      throw Error('Conflicting property capability');
  }
  return changed;
 }
 function bindWithDescriptors(pattern,properties,root=null){
  if(!pattern||pattern.type!=='ObjectPattern')return false;
  let changed=false;
  for(const p of pattern.properties){
   const target=p.type==='RestElement'?p.argument:p.value;
   const dest=target?.type==='AssignmentPattern'?target.left:target;
   const name=p.type==='RestElement'?null:keyName(p);
   const found=properties&&name!==null?properties.get(name):null;
   let scalar=typeof found==='string'?found:null;
   const nested=found&&typeof found==='object'?found.properties:null;
   if(!scalar&&!nested&&!properties){
    if(root==='global'&&(EXEC.has(name)||name===null))scalar='exec';
    else if(root==='vm'&&(VM.has(name)||name===null))scalar='exec';
    else if(root==='reflect'&&name==='get')scalar='getter';
    else if(root==='module'&&name==='require')scalar='loader';
   }
   if(dest?.type==='Identifier'){
    if(!properties&&!root&&name!==null&&(EXEC.has(name)||VM.has(name)))
     uncertainParameters.add(symbol(dest));
    changed=assignIdentifier(dest,scalar,nested,!!properties||!!root)||changed;
   }else if(dest?.type==='ObjectPattern'){
    if(!nested&&!scalar)throw Error('Nested destructuring source unknown');
    changed=bindWithDescriptors(dest,nested,scalar)||changed;
   }else if(dest?.type==='ArrayPattern'){
    if(!nested)throw Error('Nested array source unknown');
    throw Error('Nested array destructuring requires manual review');
   }
  }
  return changed;
 }
 function bind(pattern,value){
  if(!pattern)return false;
  if(pattern.type==='AssignmentPattern')return bind(pattern.left,value);
  if(pattern.type==='MemberExpression'){
   const parent=propertyKinds(pattern.object),name=propName(pattern);
   if(!parent||name===null)throw Error('Unresolved property write provenance');
   const cap=kind(value),nested=propertyKinds(value);
   if(nested){const old=parent.get(name);
    if(!old){parent.set(name,{properties:nested});return true;}
    if(old.properties!==nested)throw Error('Ambiguous property write');
    return false;
   }
   if(cap&&cap!=='local-object'){
    const old=parent.get(name);
    if(old&&old!==cap)throw Error('Conflicting property write');
    if(!old){parent.set(name,cap);return true;}
   }
   return false;
  }
  if(pattern.type==='Identifier')
   return assignIdentifier(pattern,kind(value),propertyKinds(value),value!==null);
  if(pattern.type==='ObjectPattern')
   return bindWithDescriptors(pattern,propertyKinds(value),kind(value));
  if(pattern.type==='ArrayPattern'){
   let elements=null;
   if(value?.type==='ArrayExpression')elements=value.elements;
   if(!elements)throw Error('Array destructuring needs exact provenance');
   let changed=false;
   for(let i=0;i<pattern.elements.length;i++){
    const id=pattern.elements[i];
    if(id?.type==='RestElement')throw Error('Array rest binding unknown');
    if(id)changed=bind(id,elements[i])||changed;
   }
   return changed;
  }
  throw Error('Unsupported binding pattern');
 }
 const functions=new Map(),functionAliases=[];
 traverse(ast,(node)=>{
  if(node.type==='FunctionDeclaration'&&node.id)functions.set(symbol(node.id),node);
  if(node.type==='VariableDeclarator'&&node.id?.type==='Identifier'&&
      ['ArrowFunctionExpression','FunctionExpression'].includes(node.init?.type))
   functions.set(symbol(node.id),node.init);
  if(node.type==='VariableDeclarator'&&node.id?.type==='Identifier'&&
     node.init?.type==='Identifier')
   functionAliases.push([symbol(node.id),symbol(node.init)]);
  if(node.type==='AssignmentExpression'&&node.operator==='='&&
     node.left?.type==='Identifier'&&node.right?.type==='Identifier')
   functionAliases.push([symbol(node.left),symbol(node.right)]);
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
 // Follow lexical aliases before binding arguments to callee parameters.
 // Unknown or conflicting function provenance is never silently certified.
 let aliasesConverged=false;
 for(let pass=0;pass<MAX_ROUNDS;pass++){
  let changed=false;
  for(const [destination,source] of functionAliases){
   const target=functions.get(source);
   if(!target)continue;
   const previous=functions.get(destination);
   if(previous&&previous!==target)
    throw Error('Ambiguous function alias provenance');
   if(!previous){functions.set(destination,target);changed=true;}
  }
  if(!changed){aliasesConverged=true;break;}
 }
 if(!aliasesConverged)throw Error('Function alias budget exceeded');
 traverse(ast,(node)=>{
  if(node.type!=='CallExpression'||node.callee?.type!=='Identifier')return;
  const fn=functions.get(symbol(node.callee));
  if(fn){for(let i=0;i<(fn.params||[]).length;i++)
   if(node.arguments?.[i])bindings.push([fn.params[i],node.arguments[i]]);}
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
   if(uncertainParameters.has(symbol(node))&&!knownParameterInputs.has(symbol(node))&&
      parent?.type==='CallExpression'&&key==='callee')
    throw Error('Unresolved destructured parameter invocation');
   if(kind(node)==='exec')sinks.push(node);
  }else if(node.type==='MemberExpression'){
   if(kind(node)==='exec')sinks.push(node);
  }else if(node.type==='CallExpression'||node.type==='NewExpression'){
   if(kind(node.callee)==='exec')sinks.push(node);
   if(node.callee?.type==='MemberExpression'&&propName(node.callee)==='constructor'&&
      kind(node.callee)!=='exec'&&
      !propertyKinds(node.callee.object)&&!kind(node.callee.object))
    throw Error('Unknown constructor capability');
  }
 });
 return sinks;
}
module.exports={findCapabilities};
