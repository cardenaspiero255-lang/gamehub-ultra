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
const AST_DEPTH_BUDGET=256;
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
 if(prop?.key?.type==='Literal'&&typeof prop.key.value==='number'&&
    Number.isSafeInteger(prop.key.value)&&prop.key.value>=0)
  return String(prop.key.value);
 return prop?.computed?stringValue(prop.key):
  prop?.key?.type==='Identifier'?prop.key.name:stringValue(prop?.key);
}
function traverse(node,visitor){
 let count=0;
 function walk(current,parent=null,key='',depth=0){
  if(!current||typeof current!=='object'||typeof current.type!=='string')return;
  if(depth>AST_DEPTH_BUDGET)throw Error('AST nesting budget exceeded');
  if(++count>NODE_BUDGET)throw Error('AST budget exceeded');
  visitor(current,parent,key);
  for(const [k,value] of Object.entries(current)){
   if(k==='range'||k==='loc'||k==='start'||k==='end')continue;
   if(Array.isArray(value))for(const part of value)walk(part,current,k,depth+1);
   else walk(value,current,k,depth+1);
  }
 }
 walk(node);
}
function preflightAstBudget(ast){
 const parentOf=new WeakMap();
 traverse(ast,(node,parent)=>{if(parent)parentOf.set(node,parent);});
 return parentOf;
}
function findCapabilities(ast){
 // Reject oversized or pathological ASTs BEFORE the more expensive scope
 // engine allocates resolution graphs. Unknown input is never CLEAR.
 const parentOf=preflightAstBudget(ast);
 const manager=eslintScope.analyze(ast,{ecmaVersion:2022,sourceType:'script',
  optimistic:true,ignoreEval:true,impliedStrict:false});
 const reference=new WeakMap(),declaration=new WeakMap();
 for(const scope of manager.scopes){
  for(const ref of scope.references)reference.set(ref.identifier,ref.resolved||null);
  for(const variable of scope.variables)
   for(const id of variable.identifiers)declaration.set(id,variable);
 }
 const kinds=new Map(), objects=new Map(),bindings=[];
 const arrayBindings=new Map();
 // Preserve Function.bind closures across object/array destructuring and aliases.
 const boundValues=new Map();
 const intrinsicValues=new Map();
 const stableArrayCache=new WeakMap();
 const intrinsicShadowCache=new Map();
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
 // Distinguish a known present benign property from an absent property.
 // A missing marker is NOT equivalent to a benign descriptor when defaults exist.
 const PRESENT=Object.freeze({present:true});
 const UNKNOWN=Object.freeze({unknown:true});
 // Accessors execute on read. Presence does not prove their returned value.
 const ACCESSOR=Object.freeze({accessor:true});
 // Optional means a property is known in one branch but absent in another.
 // Presence MUST NOT be inferred merely from Map.has().
 const optional=(value)=>({optional:true,value});
 const ignoredDefaultNodes=new WeakSet();
 const receiverKinds=new WeakMap(),thisExpressionCache=new WeakMap();
 function markInactiveDefault(node){
  if(node)traverse(node,n=>ignoredDefaultNodes.add(n));
 }
 function mergeDescriptor(a,b,depth=0){
  if(a===b)return a;
  if(depth>MAX_DEPTH)throw Error('Ambiguous descriptor nesting');
  if(a?.optional||b?.optional)
   return optional(mergeDescriptor(a?.optional?a.value:a,b?.optional?b.value:b,depth+1));
  if((a===PRESENT||a?.functionNode)&&(b===PRESENT||b?.functionNode))return PRESENT;
  if(a?.properties&&b?.properties){
   const merged=new Map(a.properties);
   for(const [key,value]of b.properties){
    if(merged.has(key))merged.set(key,mergeDescriptor(merged.get(key),value,depth+1));
    else merged.set(key,value);
   }
   return {properties:merged};
  }
  throw Error('Conflicting property capability provenance');
 }
 function joinObjectBranches(left,right,depth=0){
  if(depth>MAX_DEPTH)throw Error('Object branch provenance depth exceeded');
  const out=new Map();
  for(const name of new Set([...left.keys(),...right.keys()])){
   if(left.has(name)&&right.has(name))
    out.set(name,mergeDescriptor(left.get(name),right.get(name),depth+1));
   else out.set(name,optional(left.has(name)?left.get(name):right.get(name)));
  }
  return out;
 }
 function thisExpressions(fn){
  if(thisExpressionCache.has(fn))return thisExpressionCache.get(fn);
  const hits=[];let seen=0;
  function walk(node){
   if(!node||typeof node!=='object'||typeof node.type!=='string')return;
   if(++seen>NODE_BUDGET)throw Error('Receiver provenance AST budget exceeded');
   if(node!==fn&&['FunctionDeclaration','FunctionExpression'].includes(node.type))return;
   if(node.type==='ThisExpression'){hits.push(node);return;}
   for(const [key,value]of Object.entries(node)){
    if(['range','loc','start','end'].includes(key))continue;
    if(Array.isArray(value))for(const part of value)walk(part);
    else walk(value);
   }
  }
  walk(fn.body);thisExpressionCache.set(fn,hits);return hits;
 }
 function bindReceiver(fn,receiver){
  if(!receiver||fn?.type==='ArrowFunctionExpression')return false;
  const cap=kind(receiver),nodes=thisExpressions(fn);
  if(!cap||cap==='local-object')return false;
  let changed=false;
  for(const node of nodes){
   const existing=receiverKinds.get(node);
   if(existing&&existing!==cap)throw Error('Ambiguous this receiver capability');
   if(!existing){receiverKinds.set(node,cap);changed=true;}
  }
  return changed;
 }
 const returnCache=new WeakMap();
 function returnExpressions(fn){
  if(returnCache.has(fn))return returnCache.get(fn);
  const out=[],body=fn?.body;
  if(!body)return out;
  if(body.type!=='BlockStatement'){out.push(body);returnCache.set(fn,out);return out;}
  let visited=0;
  function walk(node){
   if(!node||typeof node!=='object'||typeof node.type!=='string')return;
   if(++visited>NODE_BUDGET)throw Error('Function return provenance budget exceeded');
   if(node.type==='ReturnStatement'){out.push(node.argument);return;}
   if(node!==body&&['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(node.type))return;
   for(const [key,value] of Object.entries(node)){
    if(['range','loc','start','end'].includes(key))continue;
    if(Array.isArray(value))for(const item of value)walk(item);
    else walk(value);
   }
  }
  walk(body);
  returnCache.set(fn,out);
  return out;
 }

 // A bounded, lexical representation of locally constructed array slots.
 // A callback read from a known array is a real callable, not an unrelated
 // dynamic member call. Unknown indices/spreads are never certified safe.
 // Array bindings declared with const still permit writes to their elements.
 // Closure over a known initial array is safe only while none of its lexical
 // aliases can mutate the slots or send it into an unknown caller.
 // Slice-copy provenance holds only if Array.prototype.slice has not been
 // modified. Reads of that intrinsic remain benign; writes and escapes of
 // the shared prototype are uncertain, including through const aliases.
 function intrinsicOwner(value,depth=0,seen=new Set()){
  if(!value||depth>MAX_DEPTH||value.type!=='Identifier')return null;
  const id=symbol(value);
  if(id==='free:Object')return 'Object';
  if(id==='free:Reflect')return 'Reflect';
  const defs=id?.defs||[];
  if(!id||typeof id!=='object'||seen.has(id)||defs.length!==1||
     defs[0].parent?.kind!=='const'||defs[0].node?.id?.type!=='Identifier'||
     id.references?.some(ref=>ref.isWrite()&&!ref.init))return null;
  seen.add(id);
  return intrinsicOwner(defs[0].node.init,depth+1,seen);
 }
 let nativeSliceIntegrity=null;
 function nativeArraySliceTrusted(){
  if(nativeSliceIntegrity!==null)return nativeSliceIntegrity;
  const constructors=new Set(['free:Array']),prototypes=new Set();
  let converged=false;
  for(let round=0;round<MAX_ROUNDS;round++){
   let changed=false;
   traverse(ast,node=>{
    if(node.type!=='VariableDeclarator'||node.id?.type!=='Identifier')return;
    const target=symbol(node.id),source=node.init;
    if(!target||typeof target!=='object'||!source)return;
    if(source.type==='Identifier'&&constructors.has(symbol(source))&&
       !constructors.has(target)){constructors.add(target);changed=true;}
    if((source.type==='Identifier'&&prototypes.has(symbol(source)))||
       (source.type==='MemberExpression'&&stableMethodName(source)==='prototype'&&
        source.object?.type==='Identifier'&&constructors.has(symbol(source.object)))){
     if(!prototypes.has(target)){prototypes.add(target);changed=true;}
    }
   });
   if(!changed){converged=true;break;}
  }
  if(!converged)throw Error('Native slice alias closure budget exceeded');
  const proto=x=>Boolean(x&&(x.type==='Identifier'&&prototypes.has(symbol(x))||
   x.type==='MemberExpression'&&stableMethodName(x)==='prototype'&&
    x.object?.type==='Identifier'&&constructors.has(symbol(x.object))));
  function localArrayReceiver(value,depth=0,seen=new Set()){
   if(!value||depth>MAX_DEPTH)return false;
   if(value.type==='ArrayExpression')return true;
   if(value.type!=='Identifier')return false;
   const id=symbol(value);
   if(!id||typeof id!=='object'||seen.has(id))return false;
   const defs=id.defs||[];
   if(defs.length!==1||!['const','let'].includes(defs[0].parent?.kind)||
      defs[0].node?.id?.type!=='Identifier')return false;
   if(id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
   seen.add(id);
   return localArrayReceiver(defs[0].node.init,depth+1,seen);
  }
  function nativeSliceHandle(value,depth=0,seen=new Set()){
   if(!value||depth>MAX_DEPTH)return false;
   // Comma expressions preserve the identity of their final operand;
   // never bless a mutatable native slice handle hidden behind (0, fn).
   if(value.type==='SequenceExpression')
    return nativeSliceHandle(value.expressions?.at(-1),depth+1,seen);
   if(value.type==='MemberExpression')
    return stableMethodName(value)==='slice'&&
     (proto(value.object)||localArrayReceiver(value.object));
   if(value.type!=='Identifier')return false;
   const id=symbol(value);
   if(!id||typeof id!=='object'||seen.has(id))return false;
   const defs=id.defs||[];
   if(defs.length!==1||!['const','let','var'].includes(defs[0].parent?.kind)||
      defs[0].node?.id?.type!=='Identifier')return false;
   // A let/var handle is stable only while its binding is not reassigned.
   // Mutable or multiply-defined handles are separately revoked below.
   if(id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
   seen.add(id);
   return nativeSliceHandle(defs[0].node.init,depth+1,seen);
  }
  // Resolve a callable's intrinsic constructor through direct and immutable
  // lexical aliases. A mere property spelled "constructor" on an unrelated
  // object is never evidence of the shared Function.prototype identity.
  function nativeSliceConstructor(value,depth=0,seen=new Set()){
   if(!value||depth>MAX_DEPTH)return false;
   if(value.type==='MemberExpression')
    return stableMethodName(value)==='constructor'&&
     (nativeSliceHandle(value.object,depth+1,new Set(seen))||
      value.object?.type==='Identifier'&&slicePatternAliases.has(symbol(value.object)));
   if(value.type!=='Identifier')return false;
   const id=symbol(value);
   if(!id||typeof id!=='object'||seen.has(id))return false;
   const defs=id.defs||[];
   if(defs.length!==1||defs[0].parent?.kind!=='const'||
      defs[0].node?.id?.type!=='Identifier'||
      id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
   seen.add(id);
   return nativeSliceConstructor(defs[0].node.init,depth+1,seen);
  }
  // Native handles hidden inside apply argument arrays or immutable aliases
  // can escape and be overwritten, so they invalidate intrinsic trust.
  function packedSliceHandle(value,depth=0,seen=new Set()){
   if(!value||depth>MAX_DEPTH)return false;
   if(nativeSliceHandle(value)||sliceCallablePrototype(value))return true;
   if(value.type==='Identifier'&&slicePatternAliases.has(symbol(value)))return true;
   if(value.type==='MemberExpression'&&
      sliceMemberSources(value,depth+1,new Set(seen))
       .some(part=>packedSliceHandle(part,depth+1,new Set(seen))))return true;
   // A rest binding or carrier alias can expose a nested slice handle.
   if(value.type==='MemberExpression'&&value.object?.type==='Identifier'&&
      slicePatternAliases.has(symbol(value.object)))return true;
   if(value.type==='SpreadElement')return packedSliceHandle(value.argument,depth+1,seen);
   if(value.type==='SequenceExpression')
    return value.expressions.some(x=>packedSliceHandle(x,depth+1,new Set(seen)));
   if(value.type==='ConditionalExpression')
    return packedSliceHandle(value.consequent,depth+1,new Set(seen))||
     packedSliceHandle(value.alternate,depth+1,new Set(seen));
   if(value.type==='LogicalExpression')
    return packedSliceHandle(value.left,depth+1,new Set(seen))||
     packedSliceHandle(value.right,depth+1,new Set(seen));
   if(value.type==='ArrayExpression')
    return value.elements.some(x=>packedSliceHandle(x,depth+1,new Set(seen)));
   if(value.type==='ObjectExpression')
    return value.properties.some(p=>p.type!=='Property'||
     packedSliceHandle(p.value,depth+1,new Set(seen)));
   if(value.type==='Identifier'){
    const id=symbol(value),defs=id?.defs||[];
    if(!id||typeof id!=='object'||seen.has(id)||defs.length!==1||
       !['const','let','var'].includes(defs[0].parent?.kind)||
       defs[0].node?.id?.type!=='Identifier'||
       id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
    seen.add(id);
    return packedSliceHandle(defs[0].node.init,depth+1,seen);
   }
   return false;
  }
  // A fully local, immutable literal-returning method cannot mutate a native
  // prototype, even when handed that prototype as a normal argument.
  function inertLocalMethod(callee){
   if(callee?.type!=='MemberExpression'||callee.object?.type!=='Identifier')return false;
   const id=symbol(callee.object),defs=id?.defs||[];
   if(!id||typeof id!=='object'||defs.length!==1||
      defs[0].parent?.kind!=='const'||defs[0].node?.id?.type!=='Identifier'||
      id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
   const obj=defs[0].node.init;
   if(obj?.type!=='ObjectExpression'||obj.properties.some(p=>
      p.type!=='Property'||p.kind!=='init'||p.computed))return false;
   const props=obj.properties.filter(p=>keyName(p)===stableMethodName(callee));
   if(props.length!==1)return false;
   // No local object-method proof survives later reassignments, deletions,
   // descriptor writes or escapes of its receiver to unknown callees.
   let pristine=true;
   traverse(ast,(node,parent)=>{
    if(!pristine)return;
    if(node.type==='MemberExpression'&&node.object?.type==='Identifier'&&
       symbol(node.object)===id){
     if(parent?.type==='AssignmentExpression'&&parent.left===node||
        parent?.type==='UpdateExpression'&&parent.argument===node||
        parent?.type==='UnaryExpression'&&parent.operator==='delete'&&
         parent.argument===node)pristine=false;
    }
    if(node.type==='CallExpression'&&node.arguments?.some(arg=>
       arg?.type==='Identifier'&&symbol(arg)===id))pristine=false;
   });
   if(!pristine)return false;
   const fn=props[0].value;
   return ['FunctionExpression','ArrowFunctionExpression'].includes(fn?.type)&&
    fn.params.every(p=>p.type==='Identifier')&&
    fn.body?.type==='BlockStatement'&&fn.body.body.length===1&&
    fn.body.body[0].type==='ReturnStatement'&&
    fn.body.body[0].argument?.type==='Literal';
  }
  // A native slice handle inherits call/apply from Function.prototype.
  // Writes to that shared prototype (or to a stable alias of it) invalidate
  // the entire native fast-path; unproven escapes fail closed.
  function sliceCallablePrototype(value,depth=0,seen=new Set()){
   if(!value||depth>MAX_DEPTH)return false;
   if(value.type==='MemberExpression'){
    const method=stableMethodName(value);
    return method==='prototype'&&(
      value.object?.type==='Identifier'&&
       symbol(value.object)==='free:Function'||
      nativeSliceConstructor(value.object,depth+1,new Set(seen)))||
     method==='__proto__'&&
      (nativeSliceHandle(value.object,depth+1,new Set(seen))||
       value.object?.type==='Identifier'&&slicePatternAliases.has(symbol(value.object)));
   }
   if(value.type==='Identifier'){
    const id=symbol(value);
    if(!id||typeof id!=='object'||seen.has(id))return false;
    const defs=id.defs||[];
    if(defs.length!==1||defs[0].parent?.kind!=='const'||
       defs[0].node?.id?.type!=='Identifier'||
       id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
    seen.add(id);
    return sliceCallablePrototype(defs[0].node.init,depth+1,seen);
   }
   if(value.type==='CallExpression'){
    const callee=value.callee;
    return callee?.type==='MemberExpression'&&
     stableMethodName(callee)==='getPrototypeOf'&&
     value.arguments?.length===1&&
     nativeSliceHandle(value.arguments[0],depth+1,new Set(seen));
   }
   return false;
  }

  // Monotone may-alias analysis of slice handles projected by destructuring.
  // Keep harmless local reads CLEAR; revoke native trust on writes or escapes.
  const slicePatternAliases=new Set();
  let sliceProjectionSteps=0;
  function slicePatternBudget(depth){
   if(depth>MAX_DEPTH||++sliceProjectionSteps>150000)
    throw Error('Slice pattern provenance budget exceeded');
  }
  function slicePatternKey(node,computed,depth=0,seen=new Set()){
   if(!computed)return node?.type==='Identifier'?node.name:
    node?.type==='Literal'?String(node.value):stringValue(node);
   if(node?.type==='Literal'&&['string','number'].includes(typeof node.value))
    return String(node.value);
   const direct=stringValue(node);
   if(direct!==null)return direct;
   if(node?.type!=='Identifier'||depth>MAX_DEPTH)return null;
   const id=symbol(node),defs=id?.defs||[];
   if(!id||typeof id!=='object'||seen.has(id)||defs.length!==1||
      defs[0].parent?.kind!=='const'||defs[0].node?.id?.type!=='Identifier'||
      id.references?.some(ref=>ref.isWrite()&&!ref.init))return null;
   seen.add(id);
   return slicePatternKey(defs[0].node.init,true,depth+1,seen);
  }
  function sliceLiteralSources(expr,depth=0,seen=new Set()){
   slicePatternBudget(depth);
   if(!expr)return [null];
   if(expr.type==='SequenceExpression')
    return sliceLiteralSources(expr.expressions?.at(-1),depth+1,seen);
   if(expr.type==='ConditionalExpression')
    return [...sliceLiteralSources(expr.consequent,depth+1,new Set(seen)),
     ...sliceLiteralSources(expr.alternate,depth+1,new Set(seen))];
   if(expr.type==='LogicalExpression')
    return [...sliceLiteralSources(expr.left,depth+1,new Set(seen)),
     ...sliceLiteralSources(expr.right,depth+1,new Set(seen))];
   if(expr.type==='AssignmentExpression'&&expr.operator==='=')
    return sliceLiteralSources(expr.right,depth+1,seen);
   if(expr.type==='Identifier'){
    const id=symbol(expr),defs=id?.defs||[];
    if(id&&typeof id==='object'&&!seen.has(id)&&defs.length===1&&
       ['const','let','var'].includes(defs[0].parent?.kind)&&
       defs[0].node?.id?.type==='Identifier'&&
       !id.references?.some(ref=>ref.isWrite()&&!ref.init)){
     seen.add(id);
     return sliceLiteralSources(defs[0].node.init,depth+1,seen);
    }
   }
   return [expr];
  }
  function sliceArraySlots(expr,depth=0){
   slicePatternBudget(depth);
   if(expr?.type!=='ArrayExpression')return null;
   const slots=[];
   for(const item of expr.elements||[]){
    if(item?.type==='SpreadElement'){
     const branches=sliceLiteralSources(item.argument,depth+1);
     const expanded=branches.length===1?sliceArraySlots(branches[0],depth+1):null;
     if(expanded)slots.push(...expanded);
     else slots.push(item.argument); // unknown spread fails closed on escape
    }else slots.push(item);
   }
   return slots;
  }
  function sliceObjectSlots(expr,depth=0){
   slicePatternBudget(depth);
   if(expr?.type!=='ObjectExpression')return null;
   const slots=[];
   for(const p of expr.properties||[]){
    if(p.type==='SpreadElement'){
     for(const v of sliceLiteralSources(p.argument,depth+1)){
      const spread=sliceObjectSlots(v,depth+1);
      if(spread)slots.push(...spread);
      else slots.push({key:null,value:v});
     }
    }else if(p.type==='Property')
     slots.push({key:slicePatternKey(p.key,p.computed),value:p.value});
    else slots.push({key:null,value:p});
   }
   return slots;
  }

  // Resolve nested literal carriers without conflating their contained
  // native-slice functions with the object/array carrying the function.
  function sliceMemberSources(expr,depth=0,seen=new Set()){
   slicePatternBudget(depth);
   if(!expr)return [];
   if(expr.type!=='MemberExpression')return sliceLiteralSources(expr,depth+1,seen);
   const owners=sliceMemberSources(expr.object,depth+1,new Set(seen));
   const key=slicePatternKey(expr.property,expr.computed);
   const candidates=[];
   for(const owner of owners){
    const arr=sliceArraySlots(owner,depth+1);
    if(arr){
     if(key!==null&&/^(0|[1-9][0-9]*)$/.test(key)){
      const index=Number(key);
      if(index<arr.length)candidates.push(arr[index]);
     }else if(key===null)candidates.push(...arr);
     continue;
    }
    const obj=sliceObjectSlots(owner,depth+1);
    if(obj){
     const relevant=obj.filter(x=>key===null||x.key===null||x.key===key);
     const last=key===null?-1:obj.findLastIndex(x=>x.key===key);
     candidates.push(...(last<0?relevant:
      relevant.filter(x=>x.key===null||obj.indexOf(x)>=last)).map(x=>x.value));
    }
   }
   return candidates;
  }
  function markSlicePattern(pattern){
   if(!pattern)return;
   if(pattern.type==='Identifier'){
    const id=symbol(pattern);
    if(id&&typeof id==='object')slicePatternAliases.add(id);
   }else if(pattern.type==='AssignmentPattern')markSlicePattern(pattern.left);
   else if(pattern.type==='RestElement')markSlicePattern(pattern.argument);
   else if(pattern.type==='ArrayPattern')
    for(const item of pattern.elements||[])markSlicePattern(item);
   else if(pattern.type==='ObjectPattern')
    for(const item of pattern.properties||[])
     markSlicePattern(item.type==='Property'?item.value:item.argument);
  }
  function sliceIsUndefined(expr){
   return !expr||expr.type==='Identifier'&&
    expr.name==='undefined'&&symbol(expr)==='free:undefined'||
    expr.type==='UnaryExpression'&&expr.operator==='void';
  }
  function sliceIsDefinitelyDefined(expr){
   return Boolean(expr&&['Literal','ArrayExpression','ObjectExpression',
    'FunctionExpression','ArrowFunctionExpression','ClassExpression',
    'TemplateLiteral'].includes(expr.type));
  }
  function projectSlicePattern(pattern,expr,depth=0){
   if(!pattern)return;
   slicePatternBudget(depth);
   if(pattern.type==='Identifier'){
    if(returnedSliceCapability(expr)||
       expr?.type==='Identifier'&&slicePatternAliases.has(symbol(expr)))
     markSlicePattern(pattern);
    return;
   }
   if(pattern.type==='AssignmentPattern'){
    if(!sliceIsUndefined(expr))projectSlicePattern(pattern.left,expr,depth+1);
    if(sliceIsUndefined(expr)||!sliceIsDefinitelyDefined(expr))
     projectSlicePattern(pattern.left,pattern.right,depth+1);
    return;
   }
   if(pattern.type==='RestElement'){
    if(packedSliceHandle(expr))markSlicePattern(pattern.argument);
    return;
   }
   const branches=sliceLiteralSources(expr,depth+1);
   for(const source of branches){
    if(pattern.type==='ArrayPattern'){
     const slots=sliceArraySlots(source,depth+1);
     if(!slots){
      if(packedSliceHandle(source))markSlicePattern(pattern);
      continue;
     }
     for(let i=0;i<(pattern.elements||[]).length;i++){
      const item=pattern.elements[i];
      if(item?.type==='RestElement'){
       if(slots.slice(i).some(x=>packedSliceHandle(x)))
        markSlicePattern(item.argument);
      }else projectSlicePattern(item,slots[i],depth+1);
     }
    }else if(pattern.type==='ObjectPattern'){
     const slots=sliceObjectSlots(source,depth+1);
     if(!slots){
      if(packedSliceHandle(source))markSlicePattern(pattern);
      continue;
     }
     const consumed=new Set();
     for(const item of pattern.properties||[]){
      if(item.type==='RestElement'){
       if(slots.some(x=>!consumed.has(x.key)&&packedSliceHandle(x.value)))
        markSlicePattern(item.argument);
       continue;
      }
      if(item.type!=='Property')continue;
      const key=slicePatternKey(item.key,item.computed);
      if(key!==null)consumed.add(key);
      const candidates=slots.filter(x=>key===null||x.key===null||x.key===key);
      const last=key===null?-1:slots.findLastIndex(x=>x.key===key);
      const selected=last<0?candidates:
       candidates.filter(x=>x.key===null||slots.indexOf(x)>=last);
      if(!selected.length)projectSlicePattern(item.value,null,depth+1);
      else for(const entry of selected)
       projectSlicePattern(item.value,entry.value,depth+1);
     }
    }
   }
  }
  // Reach a fixed point across destructured and normal lexical aliases.
  let slicePatternConverged=false;
  for(let round=0;round<MAX_ROUNDS;round++){
   const oldSize=slicePatternAliases.size;
   traverse(ast,node=>{
    if(node.type==='VariableDeclarator'){
     if(['ArrayPattern','ObjectPattern'].includes(node.id?.type))
      projectSlicePattern(node.id,node.init);
     else if(node.id?.type==='Identifier'&&node.init?.type==='Identifier'&&
             slicePatternAliases.has(symbol(node.init)))
      markSlicePattern(node.id);
    }
    if(node.type==='AssignmentExpression'&&node.operator==='='){
     if(['ArrayPattern','ObjectPattern'].includes(node.left?.type))
      projectSlicePattern(node.left,node.right);
     else if(node.left?.type==='Identifier'&&node.right?.type==='Identifier'&&
             slicePatternAliases.has(symbol(node.right)))
      markSlicePattern(node.left);
    }
   });
   if(slicePatternAliases.size===oldSize){slicePatternConverged=true;break;}
  }
  if(!slicePatternConverged)
   throw Error('Slice destructuring alias closure budget exceeded');
  // Returned native slice handles escape even through concise arrow bodies,
  // conditional expressions and immutable aliases. Revoke the fast path
  // rather than assuming Function.prototype.call/apply still applies.
  function returnedSliceCapability(value,depth=0){
   if(!value||depth>MAX_DEPTH)return false;
   if(nativeSliceHandle(value)||sliceCallablePrototype(value))return true;
   if(value.type==='ConditionalExpression')
    return returnedSliceCapability(value.consequent,depth+1)||
     returnedSliceCapability(value.alternate,depth+1);
   if(value.type==='LogicalExpression')
    return returnedSliceCapability(value.left,depth+1)||
     returnedSliceCapability(value.right,depth+1);
   if(value.type==='SequenceExpression')
    return returnedSliceCapability(value.expressions?.at(-1),depth+1);
   if(value.type==='AssignmentExpression'&&value.operator==='=')
    return returnedSliceCapability(value.right,depth+1);
   return false;
  }
  let changed=false;
  traverse(ast,(node,parent)=>{
   if(changed)return;
   // Function.prototype.call/apply can be shadowed on a native slice
   // handle independently of Array.prototype.slice itself.
   if(node.type==='MemberExpression'&&sliceCallablePrototype(node.object)){
    const method=stableMethodName(node);
    if(method===null||['call','apply'].includes(method)){
     if(parent?.type==='AssignmentExpression'&&parent.left===node||
        parent?.type==='UpdateExpression'&&parent.argument===node||
        parent?.type==='UnaryExpression'&&parent.operator==='delete'&&
         parent.argument===node)changed=true;
    }
    // Legacy function-prototype mutators change inherited call/apply even
    // though the invoked property is NOT itself named call/apply.
    if(parent?.type==='CallExpression'&&parent.callee===node&&
       (method===null||['__defineGetter__','__defineSetter__'].includes(method)))
     changed=true;
   }
   if(node.type==='MemberExpression'&&node.object?.type==='Identifier'&&
      slicePatternAliases.has(symbol(node.object))){
    const method=stableMethodName(node);
    if(method===null||['call','apply'].includes(method)){
     if(parent?.type==='AssignmentExpression'&&parent.left===node||
        parent?.type==='UpdateExpression'&&parent.argument===node||
        parent?.type==='UnaryExpression'&&parent.operator==='delete'&&
         parent.argument===node)changed=true;
    }
    // Legacy accessors operate on the function object itself, without
    // passing the native handle as a formal call argument.
    if(parent?.type==='CallExpression'&&parent.callee===node&&
       (method===null||['__defineGetter__','__defineSetter__'].includes(method)))
     changed=true;
   }
   if(node.type==='MemberExpression'&&nativeSliceHandle(node.object)&&
      ['call','apply'].includes(stableMethodName(node))){
    if(parent?.type==='AssignmentExpression'&&parent.left===node||
       parent?.type==='UpdateExpression'&&parent.argument===node||
       parent?.type==='UnaryExpression'&&parent.operator==='delete'&&
        parent.argument===node)changed=true;
   }
   if(node.type==='MemberExpression'&&proto(node.object)){
    const key=stableMethodName(node);
    if(key==='slice'||key===null){
     if(parent?.type==='AssignmentExpression'&&parent.left===node||
        parent?.type==='UpdateExpression'&&parent.argument===node||
        parent?.type==='UnaryExpression'&&parent.operator==='delete'&&
         parent.argument===node)changed=true;
    }
    if(parent?.type==='CallExpression'&&parent.callee===node)changed=true;
   }
   if(node.type==='CallExpression'){
    // Treat a native slice callable handed to any external callee as an
    // escape: aliases of Object.defineProperty, setPrototypeOf, etc. can
    // overwrite .call/.apply without a direct, recognizable method name.
    // Native slice.call(array) hands off the ARRAY, not this callable.
    if(node.arguments?.some(argument=>packedSliceHandle(argument)))changed=true;
    // Also recognize the direct global spelling: scope resolvers may not
    // materialize a builtin Array binding in every scope configuration.
    const owner=node.callee?.type==='MemberExpression'?
     intrinsicOwner(node.callee.object):null;
    const operation=node.callee?.type==='MemberExpression'?
     stableMethodName(node.callee):null;
    const intrinsicMutator=owner==='Object'&&
     ['defineProperty','defineProperties','assign'].includes(operation)||
     owner==='Reflect'&&['set','defineProperty'].includes(operation);
    const directArrayPrototype=arg=>arg?.type==='MemberExpression'&&
     stableMethodName(arg)==='prototype'&&arg.object?.type==='Identifier'&&
     arg.object.name==='Array'&&symbol(arg.object)==='free:Array';
    if(!inertLocalMethod(node.callee)&&
       (node.arguments?.some(proto)||intrinsicMutator&&
        node.arguments?.some(directArrayPrototype)))changed=true;
    if(intrinsicMutator&&nativeSliceHandle(node.arguments?.[0])){
     const operation=stableMethodName(node.callee);
     const key=stringValue(node.arguments?.[1]);
     if(['assign','defineProperties'].includes(operation)||key===null||
        ['call','apply'].includes(key))changed=true;
    }
   }
   if(node.type==='Property'&&proto(node.value)||node.type==='ArrayExpression'&&
      node.elements?.some(proto)||node.type==='ReturnStatement'&&proto(node.argument))
    changed=true;
   if(node.type==='AssignmentExpression'&&proto(node.right))changed=true;
   // Assigning a native slice handle to a carrier or mutable binding can
   // expose its call/apply properties; never retain the native fast-path.
   if(node.type==='AssignmentExpression'&&
      (returnedSliceCapability(node.right)||packedSliceHandle(node.right)&&
       node.right?.type!=='Identifier'||
       node.right?.type==='Identifier'&&slicePatternAliases.has(symbol(node.right))))
    changed=true;
   // A mutable declarator may hold a previously native handle and later
   // overwrite its binding. Such flows cannot use the trusted native path.
   // Stable let/var aliases remain eligible for ordinary read-only calls.
   if(node.type==='VariableDeclarator'&&
      parent?.type==='VariableDeclaration'&&parent.kind!=='const'&&
      returnedSliceCapability(node.init)){
    const id=node.id?.type==='Identifier'?symbol(node.id):null;
    if(!id||typeof id!=='object'||id.defs?.length!==1||
       id.references?.some(ref=>ref.isWrite()&&!ref.init))changed=true;
   }
   // Native handles can escape through returned arrays/objects, generators
   // and instance fields, not just direct return statements.
   if(node.type==='ReturnStatement'&&
      (returnedSliceCapability(node.argument)||packedSliceHandle(node.argument))||
      node.type==='ArrowFunctionExpression'&&
       node.body?.type!=='BlockStatement'&&
       (returnedSliceCapability(node.body)||packedSliceHandle(node.body))||
      node.type==='YieldExpression'&&packedSliceHandle(node.argument)||
      node.type==='PropertyDefinition'&&packedSliceHandle(node.value))
    changed=true;
  });
  nativeSliceIntegrity=!changed;
  return nativeSliceIntegrity;
 }
 function ensureStableArray(variable){
  if(stableArrayCache.has(variable)){
   if(stableArrayCache.get(variable))throw Error('Array callback slots may be mutated');
   return;
  }
  const aliases=new Set([variable]);
  for(let round=0;round<MAX_ROUNDS;round++){
   let changed=false;
   traverse(ast,node=>{
    if(node.type==='VariableDeclarator'&&node.id?.type==='Identifier'&&
       node.init?.type==='Identifier'&&aliases.has(symbol(node.init))&&
       !aliases.has(symbol(node.id))){
     aliases.add(symbol(node.id));changed=true;
    }
    if(node.type==='AssignmentExpression'&&node.operator==='='&&
       node.left?.type==='Identifier'&&node.right?.type==='Identifier'&&
       aliases.has(symbol(node.right))&&!aliases.has(symbol(node.left))){
     aliases.add(symbol(node.left));changed=true;
    }
    // A destructured local holds the same array reference, not a copy:
    // const [alias]=[arr] and const [[alias]]=[[arr]].
    function projectedValueDefined(value,depth=0,seen=new Set()){
     if(!value||depth>MAX_DEPTH)return false;
     if(['ArrayExpression','ObjectExpression','Literal','FunctionExpression',
         'ArrowFunctionExpression','ClassExpression','TemplateLiteral',
         'NewExpression'].includes(value.type))return true;
     if(value.type==='Identifier'){
      const variable=symbol(value);
      if(!variable||typeof variable!=='object'||seen.has(variable))return false;
      const defs=variable.defs||[];
      if(defs.length!==1||defs[0].parent?.kind!=='const'||
         defs[0].node?.id?.type!=='Identifier')return false;
      seen.add(variable);
      return projectedValueDefined(defs[0].node?.init,depth+1,seen);
     }
     if(value.type==='AssignmentExpression'&&value.operator==='=')
      return projectedValueDefined(value.right,depth+1,seen);
     return false;
    }
    // Project ObjectPattern defaults through stable lexical object aliases.
    // Unknown writes/escapes invalidate certification rather than silently
    // dropping the array alias that is mutated by the destructured variable.
    // Destructuring snapshots cannot be changed by later straight-line
    // assignments. Branch/loop/closure writes still fail closed.
    function objectPatternSnapshot(pattern){
     let cursor=pattern;
     for(let depth=0;depth<MAX_DEPTH;depth++){
      const parent=parentOf.get(cursor);
      if(!parent)return null;
      if(parent.type==='VariableDeclarator'||parent.type==='AssignmentExpression')
       return Number.isSafeInteger(parent.start)?parent.start:null;
      cursor=parent;
     }
     return null;
    }
    function safePostSnapshotWrite(node,snapshot){
     if(!Number.isSafeInteger(snapshot)||!Number.isSafeInteger(node?.start)||
        node.start<=snapshot)return false;
     let cursor=node;
     for(let depth=0;depth<MAX_DEPTH;depth++){
      const parent=parentOf.get(cursor);
      if(!parent)return false;
      if(parent.type==='Program')return true;
      // Bare nested blocks are unconditional lexical sequences. A conditional,
      // loop, function, try/catch or labelled block still fails closed since
      // the ancestor chain cannot reach Program through this whitelist.
      if(!['MemberExpression','AssignmentExpression','ExpressionStatement',
            'UpdateExpression','UnaryExpression','SequenceExpression',
            'BlockStatement'].includes(parent.type))return false;
      cursor=parent;
     }
     return false;
    }
    // A defaulted array is already captured at the destructuring snapshot.
    // A later escape of an originally primitive-only source cannot mutate
    // that array, PROVIDED no lexical alias ever adds mutable properties.
    function inertLiteralSource(value,depth=0,seen=new Set()){
     if(!value||depth>MAX_DEPTH)return null;
     if(value.type==='ObjectExpression'){
      return value.properties.every(p=>p.type==='Property'&&p.kind==='init'&&
        !p.computed&&stablePatternKey(p)!=='__proto__'&&
        p.value?.type==='Literal')?value:null;
     }
     if(value.type!=='Identifier')return null;
     const id=symbol(value);
     if(!id||typeof id!=='object'||seen.has(id))return null;
     const defs=id.defs||[];
     if(defs.length!==1||!['const','let'].includes(defs[0].parent?.kind)||
        defs[0].node?.id?.type!=='Identifier'||
        id.references?.some(ref=>ref.isWrite()&&!ref.init))return null;
     seen.add(id);
     return inertLiteralSource(defs[0].node.init,depth+1,seen);
    }
    // Only prove a post-snapshot escape harmless if every intervening call
    // is a known read-only builtin or a local function whose entire body
    // consists of returning a literal (or doing nothing). Unknown helpers
    // may write references to the captured callback array into the carrier.
    // Console calls cannot be certified pure once the global console or its
    // method identities have been exposed, rebound or modified.
    function globalCarrier(value,depth=0,seen=new Set()){
     if(!value||depth>MAX_DEPTH||value.type!=='Identifier')return false;
     const id=symbol(value);
     if(id==='free:globalThis'||id==='free:global')return true;
     const defs=id?.defs||[];
     if(!id||typeof id!=='object'||seen.has(id)||defs.length!==1||
        defs[0].parent?.kind!=='const'||defs[0].node?.id?.type!=='Identifier'||
        id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
     seen.add(id);
     return globalCarrier(defs[0].node.init,depth+1,seen);
    }
    let consoleReadOnly=null;
    function pristineConsoleCalls(){
     if(consoleReadOnly!==null)return consoleReadOnly;
     let trusted=true;
     traverse(ast,(node,parent)=>{
      if(!trusted)return;
      // A globalThis.console alias can mutate the same global console object.
      if(node.type==='MemberExpression'&&stableMethodName(node)==='console'&&
         globalCarrier(node.object)){trusted=false;return;}
      if(node.type!=='Identifier'||symbol(node)!=='free:console')return;
      if(parent?.type==='MemberExpression'&&parent.object===node&&
         ['log','info','warn','error','debug','trace'].includes(stableMethodName(parent))){
       const usage=parentOf.get(parent);
       if(usage?.type==='CallExpression'&&usage.callee===parent)return;
      }
      trusted=false;
     });
     consoleReadOnly=trusted;
     return trusted;
    }
    let wrappersReadOnly=null;
    function pristineWrappers(){
     if(wrappersReadOnly!==null)return wrappersReadOnly;
     let stable=true;
     traverse(ast,(node,parent)=>{
      if(!stable)return;
      if(node.type==='MemberExpression'&&
         ['call','apply','bind'].includes(stableMethodName(node))&&
         (parent?.type==='AssignmentExpression'&&parent.left===node||
          parent?.type==='UpdateExpression'&&parent.argument===node||
          parent?.type==='UnaryExpression'&&parent.operator==='delete'&&
           parent.argument===node))stable=false;
      if(node.type==='CallExpression'&&node.callee?.type==='MemberExpression'){
       const owner=intrinsicOwner(node.callee.object);
       const method=stableMethodName(node.callee);
       if(owner==='Object'&&['defineProperty','defineProperties','setPrototypeOf','assign'].includes(method)||
          owner==='Reflect'&&['defineProperty','setPrototypeOf','set'].includes(method))stable=false;
      }
     });
     wrappersReadOnly=stable;
     return stable;
    }
    function provenReadOnlyCall(callee,depth=0,seen=new Set(),call=null){
     if(!callee||depth>MAX_DEPTH)return false;
     if(callee.type==='CallExpression'&&callee.callee?.type==='MemberExpression'&&
        stableMethodName(callee.callee)==='bind'&&pristineWrappers())
      return provenReadOnlyCall(callee.callee.object,depth+1,new Set(seen));
     if(callee.type==='MemberExpression'){
      const wrapper=stableMethodName(callee);
      if(wrapper==='apply'&&callee.object?.type==='Identifier'&&
         symbol(callee.object)==='free:Reflect'&&call?.callee===callee&&
         pristineWrappers())
       return provenReadOnlyCall(call.arguments?.[0],depth+1,new Set(seen));
      if(['call','apply','bind'].includes(wrapper)&&pristineWrappers())
       return provenReadOnlyCall(callee.object,depth+1,new Set(seen));
     }
     // Parameter initializers execute even when the function body is inert.
     // Destructuring or executable defaults must invalidate purity proof.
     const inertParameter=param=>param?.type==='Identifier'||
      param?.type==='RestElement'&&param.argument?.type==='Identifier'||
      param?.type==='AssignmentPattern'&&param.left?.type==='Identifier'&&
       param.right?.type==='Literal';
     const inertFunction=fn=>Boolean(fn&&
      ['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(fn.type)&&
      fn.params?.every(inertParameter)&&
      (fn.body?.type==='Literal'||
       fn.body?.type==='BlockStatement'&&
        (fn.body.body.length===0||
         fn.body.body.length===1&&
         fn.body.body[0]?.type==='ReturnStatement'&&
         (!fn.body.body[0].argument||
          fn.body.body[0].argument.type==='Literal'))));
     if(callee.type==='MemberExpression'){
      const name=stableMethodName(callee);
      const owner=callee.object?.type==='Identifier'?symbol(callee.object):null;
      if(name==='log'&&owner==='free:console'&&pristineConsoleCalls())return true;
      if(name==='getPrototypeOf'&&
         (owner==='free:Object'||owner==='free:Reflect'))return true;
      const expr=callee.object;
      if(expr?.type!=='Identifier')return false;
      const id=symbol(expr),defs=id?.defs||[];
      if(!id||typeof id!=='object'||seen.has(id)||
         defs.length!==1||defs[0].parent?.kind!=='const'||
         id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
      const obj=defs[0].node?.init;
      if(obj?.type!=='ObjectExpression')return false;
      const matching=obj.properties.filter(p=>p.type==='Property'&&
        !p.computed&&stablePatternKey(p)===name);
      return matching.length===1&&inertFunction(matching[0].value);
     }
     if(callee.type!=='Identifier')return false;
     const id=symbol(callee),defs=id?.defs||[];
     if(!id||typeof id!=='object'||seen.has(id)||defs.length!==1||
        id.references?.some(ref=>ref.isWrite()&&!ref.init))return false;
     seen.add(id);
     const decl=defs[0].node;
     if(inertFunction(decl))return true;
     if(decl?.type==='VariableDeclarator'&&
        defs[0].parent?.kind==='const'){
      if(inertFunction(decl.init))return true;
      return provenReadOnlyCall(decl.init,depth+1,seen);
     }
     return false;
    }
    function safePostSnapshotEscape(node,value,snapshot){
     if(!Number.isSafeInteger(snapshot)||!Number.isSafeInteger(node?.start)||
        node.start<=snapshot)return false;
     let cursor=node,inTopLevelStatement=false;
     for(let depth=0;depth<MAX_DEPTH;depth++){
      const parent=parentOf.get(cursor);
      if(!parent)return false;
      if(parent.type==='Program'){inTopLevelStatement=true;break;}
      if(!['MemberExpression','CallExpression','ExpressionStatement',
            'UpdateExpression','UnaryExpression','SequenceExpression',
             'ArrayExpression'].includes(
            parent.type))return false;
      cursor=parent;
     }
     if(!inTopLevelStatement)return false;
     // Calls are the escape boundary. Every unproved callee is potentially
     // effectful even if it appears after the object default snapshot.
     cursor=node;
     for(let depth=0;depth<MAX_DEPTH;depth++){
      const parent=parentOf.get(cursor);
      if(!parent||parent.type==='Program')break;
      if(parent.type==='CallExpression'&&!provenReadOnlyCall(parent.callee,0,new Set(),parent))
       return false;
      cursor=parent;
     }
     const original=inertLiteralSource(value);
     if(!original)return false;
     // Conservatively retain INCOMPLETE for writes to the source at ANY
     // position: a later write could put the captured array into a carrier.
     // Recognize direct, computed and immutable aliases of property and
     // prototype mutators. All mutate the observed carrier, so neither
     // post-snapshot escape nor an apparently inert literal can be trusted.
     function mutatorName(expr,depth=0,seen=new Set()){
      if(!expr||depth>MAX_DEPTH)return null;
      if(expr.type==='MemberExpression'){
       const name=stableMethodName(expr);
       if(['call','apply','bind'].includes(name))
        return mutatorName(expr.object,depth+1,new Set(seen));
       // Never grant global-intrinsic privileges solely because a local
       // method has the same spelling. Bindings are scope-resolved.
       const owner=expr.object?.type==='Identifier'?symbol(expr.object):null;
       if(owner==='free:Object'&&
          ['defineProperty','defineProperties','assign','setPrototypeOf'].includes(name))
        return name;
       if(owner==='free:Reflect'&&['set','defineProperty','setPrototypeOf'].includes(name))
        return name;
       if(['__defineGetter__','__defineSetter__'].includes(name))
        return name;
       return null;
      }
      // A bound intrinsic remains a mutator even when its use is delayed.
      if(expr.type==='CallExpression'&&expr.callee?.type==='MemberExpression'&&
         stableMethodName(expr.callee)==='bind')
       return mutatorName(expr.callee.object,depth+1,new Set(seen));
      if(expr.type!=='Identifier')return null;
      const id=symbol(expr);
      if(!id||typeof id!=='object'||seen.has(id))return null;
      const defs=id.defs||[];
      if(defs.length!==1||defs[0].parent?.kind!=='const'||
         defs[0].node?.id?.type!=='Identifier'||
         id.references?.some(ref=>ref.isWrite()&&!ref.init))return null;
      seen.add(id);
      return mutatorName(defs[0].node.init,depth+1,seen);
     }
     // Receiver provenance: writes to an object's current prototype also
     // alter which properties the source can observe via inheritance.
     function affectsCarrier(target,depth=0,seen=new Set()){
      if(!target||depth>MAX_DEPTH)return false;
      if(inertLiteralSource(target)===original)return true;
      // The prototype returned by Object.getPrototypeOf(source) or
      // Reflect.getPrototypeOf(source) is still part of the source's
      // observable state. Follow only proven immutable lexical aliases.
      if(target.type==='Identifier'){
       const variable=symbol(target);
       if(!variable||typeof variable!=='object'||seen.has(variable))
        return false;
       const defs=variable.defs||[];
       if(defs.length!==1||defs[0].parent?.kind!=='const'||
          defs[0].node?.id?.type!=='Identifier'||
          variable.references?.some(ref=>ref.isWrite()&&!ref.init))
        return false;
       seen.add(variable);
       return affectsCarrier(defs[0].node.init,depth+1,seen);
      }
      if(target.type==='CallExpression'&&target.arguments?.length===1&&
         target.callee?.type==='MemberExpression'&&
         stableMethodName(target.callee)==='getPrototypeOf'&&
         target.callee.object?.type==='Identifier'&&
         ['free:Object','free:Reflect'].includes(symbol(target.callee.object))&&
         inertLiteralSource(target.arguments[0])===original)return true;
      return false;
     }
     let modified=false;
     traverse(ast,(candidate,parent)=>{
      if(modified)return;
      if(candidate.type==='MemberExpression'&&
         inertLiteralSource(candidate.object)===original&&
         (parent?.type==='AssignmentExpression'&&parent.left===candidate||
          parent?.type==='UpdateExpression'&&parent.argument===candidate||
          parent?.type==='UnaryExpression'&&parent.operator==='delete'&&
            parent.argument===candidate||
          parent?.type==='CallExpression'&&parent.callee===candidate))
       modified=true;
      if(candidate.type==='CallExpression'){
       let action=mutatorName(candidate.callee),target=candidate.arguments?.[0];
       if(candidate.callee?.type==='MemberExpression'){
        const wrapper=stableMethodName(candidate.callee);
        if(wrapper==='call'){
         // Each .call layer adds one receiver argument ahead of the
         // real mutator target. For f.call.call(f,thisArg,target), the
         // mutation target is arguments[2], never arguments[1].
         let layers=0,cursor=candidate.callee;
         while(cursor?.type==='MemberExpression'&&
               stableMethodName(cursor)==='call'&&layers<MAX_DEPTH){
          layers++;cursor=cursor.object;
         }
         target=layers>=MAX_DEPTH?null:candidate.arguments?.[layers]||null;
        }else if(wrapper==='apply'){
         const supplied=candidate.arguments?.[1];
         target=supplied?.type==='ArrayExpression'?
          supplied.elements?.[0]:null;
        }
       }else if(candidate.callee?.type==='CallExpression'&&
           candidate.callee.callee?.type==='MemberExpression'&&
           stableMethodName(candidate.callee.callee)==='bind')
        target=candidate.callee.arguments?.[1]||candidate.arguments?.[0];
       // Reflect.apply(fn,receiver,[target,...]) has its own argument shape.
       if(candidate.callee?.type==='MemberExpression'&&
          stableMethodName(candidate.callee)==='apply'&&
          candidate.callee.object?.type==='Identifier'&&
          symbol(candidate.callee.object)==='free:Reflect'&&
          mutatorName(candidate.arguments?.[0])){
        action=mutatorName(candidate.arguments[0]);
        target=candidate.arguments[2]?.type==='ArrayExpression'?
         candidate.arguments[2].elements?.[0]:null;
       }
       if(['__defineGetter__','__defineSetter__'].includes(action)){
        // Instance mutators change the receiver (this), not their key
        // argument. A direct method uses callee.object, .call receiver
        // is arguments[0], and .call.call receiver is arguments[1].
        if(candidate.callee?.type==='MemberExpression'){
         let layers=0,cursor=candidate.callee;
         while(cursor?.type==='MemberExpression'&&
               stableMethodName(cursor)==='call'&&layers<MAX_DEPTH){
          layers++;cursor=cursor.object;
         }
         // .apply supplies its mutated receiver directly in arguments[0],
         // whereas .call wrappers shift the receiver by each call layer.
         target=layers>0?candidate.arguments?.[layers-1]||null:
          stableMethodName(candidate.callee)==='apply'?
           candidate.arguments?.[0]||null:candidate.callee.object;
        }
       }
       if(action&&(target===null||affectsCarrier(target)))modified=true;
      }
     });
     return !modified;
    }
    // Unknown computed keys, accessors, spread and prototype setters must
    // never be projected as absent slots: they may overwrite the default.
    function verifiedObjectLiteral(value){
     if(value?.type!=='ObjectExpression')return null;
     for(const prop of value.properties){
      // The scope engine cannot currently prove a computed identifier's
      // array callback slots survived destructuring. Even if a separate
      // constant-folder knows its string value, do NOT project an absent
      // default until the value provenance is also established.
      if(prop.type!=='Property'||prop.kind!=='init'||
         // A computed lexical key can be trusted only when its constant
         // value is proven and the corresponding property holds an inert
         // scalar. Callback arrays still require full descriptor tracking.
         // A non-literal computed key with an authenticated constant
         // string may be disjoint from the projected slot regardless of
         // its value type. Matching keys are screened separately below.
         prop.computed&&stablePatternKey(prop)===null||
         stablePatternKey(prop)===null||
         stablePatternKey(prop)==='__proto__')
       throw Error('Unresolved object property identity');
     }
     return value;
    }
    function immutableObjectSource(value,depth=0,seen=new Set(),snapshot=null){
     if(!value||depth>MAX_DEPTH)return null;
     if(value.type==='ObjectExpression')return verifiedObjectLiteral(value);
     if(value.type!=='Identifier')return null;
     const root=symbol(value);
     if(!root||typeof root!=='object'||seen.has(root))return null;
     const queue=[root],visited=new Set();
     while(queue.length){
      if(visited.size>MAX_ROUNDS)throw Error('Object alias provenance budget exceeded');
      const current=queue.pop();
      if(visited.has(current))continue;
      visited.add(current);
      for(const ref of current.references||[]){
       if(ref.isWrite()){
        if(ref.init||safePostSnapshotWrite(ref.identifier,snapshot))continue;
        throw Error('Mutable object source alias');
       }
       const id=ref.identifier,parent=parentOf.get(id);
       if(parent?.type==='VariableDeclarator'&&parent.init===id){
        if(parent.id?.type==='Identifier'&&['const','let'].includes(parentOf.get(parent)?.kind)){
         const alias=symbol(parent.id);
         if(!alias||typeof alias!=='object')throw Error('Unknown object source alias');
         queue.push(alias);
        }else if(parent.id?.type!=='ObjectPattern')
         throw Error('Unknown object carrier escape');
        continue;
       }
       // A destructuring assignment reads the same stable local snapshot
       // as a declaration; an unrelated assignment still fails closed.
       if(parent?.type==='AssignmentExpression'&&parent.right===id&&
          parent.operator==='='&&parent.left?.type==='ObjectPattern')
        continue;
       if(parent?.type==='MemberExpression'&&parent.object===id){
        const outer=parentOf.get(parent);
        if(outer?.type==='AssignmentExpression'&&outer.left===parent||
           outer?.type==='UpdateExpression'&&outer.argument===parent||
           outer?.type==='UnaryExpression'&&outer.operator==='delete'&&outer.argument===parent||
           outer?.type==='CallExpression'&&outer.callee===parent)
         if(!safePostSnapshotWrite(parent,snapshot))
          throw Error('Object source property mutation unknown');
        continue;
       }
       if(safePostSnapshotEscape(id,value,snapshot))continue;
       throw Error('Object source escape or mutation unknown');
      }
     }
     const defs=root.defs||[];
     if(defs.length!==1||!['const','let'].includes(defs[0].parent?.kind)||
        defs[0].node?.id?.type!=='Identifier')return null;
     seen.add(root);
     const init=defs[0].node.init;
     if(init?.type==='ObjectExpression')
      return verifiedObjectLiteral(init);
     return immutableObjectSource(init,depth+1,seen,snapshot);
    }
    function project(pattern,value,depth=0){
     if(!pattern||depth>MAX_DEPTH)return;
     // Do not skip an AssignmentPattern merely because its slot is missing:
     // omission is exactly when the default may alias a mutable array.
     if(pattern.type==='AssignmentPattern'){
      const omitted=!value||
       (value.type==='Identifier'&&value.name==='undefined'&&
        symbol(value)==='free:undefined')||
       (value.type==='UnaryExpression'&&value.operator==='void');
      if(omitted)return project(pattern.left,pattern.right,depth+1);
      project(pattern.left,value,depth+1);
      if(!projectedValueDefined(value))project(pattern.left,pattern.right,depth+1);
      return;
     }
     if(!value)return;
     if(pattern.type==='Identifier'&&value.type==='Identifier'&&
        aliases.has(symbol(value))&&!aliases.has(symbol(pattern))){
      aliases.add(symbol(pattern));changed=true;return;
     }
     if(pattern.type==='ArrayPattern'&&value.type==='ArrayExpression')
      for(let i=0;i<pattern.elements.length;i++)
       project(pattern.elements[i],value.elements[i],depth+1);
     if(pattern.type==='ObjectPattern'){
      const sourceObject=immutableObjectSource(value,depth+1,new Set(),
       objectPatternSnapshot(pattern));
      if(sourceObject)for(const entry of pattern.properties){
       if(entry.type!=='Property')continue;
       const key=stablePatternKey(entry);
       const source=sourceObject.properties.findLast(prop=>prop.type==='Property'&&
         stablePatternKey(prop)===key);
       if(source)project(entry.value,source.value,depth+1);
       else project(entry.value,null,depth+1);
      }
     }
    }
    if(node.type==='VariableDeclarator'&&node.init)
     project(node.id,node.init);
    if(node.type==='AssignmentExpression'&&node.operator==='=')
     project(node.left,node.right);
   });
   if(!changed)break;
   if(round===MAX_ROUNDS-1)throw Error('Array alias closure budget exceeded');
  }
  // Derive aliases to nested array rows by their slot identity. A scalar
  // function extracted from an array must not be mistaken for a row alias.
  const rowSlots=new Map();
  const initial=variable?.defs?.[0]?.node?.init;
  if(initial?.type==='ArrayExpression')rowSlots.set(variable,initial.elements);
  for(let round=0;round<MAX_ROUNDS;round++){
   let progressed=false;
   traverse(ast,node=>{
    if(node.type!=='VariableDeclarator'||!node.init)return;
    const src=node.init;
    if(node.id?.type==='Identifier'&&src.type==='Identifier'&&rowSlots.has(symbol(src))){
     const id=symbol(node.id);
     if(!rowSlots.has(id)){rowSlots.set(id,rowSlots.get(symbol(src)));progressed=true;}
    }
    if(node.id?.type==='Identifier'&&src.type==='MemberExpression'&&src.computed&&
       src.object?.type==='Identifier'&&rowSlots.has(symbol(src.object))){
     const source=rowSlots.get(symbol(src.object)),idx=staticArrayIndex(src.property);
     const found=idx===null?null:source?.[idx],id=symbol(node.id);
     if(found?.type==='ArrayExpression'&&!rowSlots.has(id)){
      rowSlots.set(id,found.elements);progressed=true;
     }
    }
    if(node.id?.type==='ObjectPattern'&&src.type==='Identifier'&&rowSlots.has(symbol(src))){
     const source=rowSlots.get(symbol(src));
     for(const prop of node.id.properties){
      if(prop.type!=='Property'||prop.value?.type!=='Identifier')continue;
      const key=stablePatternKey(prop),index=key===null?null:Number(key);
      const found=Number.isSafeInteger(index)&&index>=0?source?.[index]:null;
      const id=symbol(prop.value);
      if(found?.type==='ArrayExpression'&&!rowSlots.has(id)){
       rowSlots.set(id,found.elements);progressed=true;
      }
     }
    }
   });
   if(!progressed)break;
   if(round===MAX_ROUNDS-1)throw Error('Nested row alias budget exceeded');
  }
  for(const id of rowSlots.keys())aliases.add(id);
  const descendsFromArray=node=>{
   if(node?.type==='Identifier')return aliases.has(symbol(node));
   if(node?.type==='MemberExpression')return descendsFromArray(node.object);
   return false;
  };
  let uncertain=false,unknownAliasWrite=false;
  function initialObjectPatternAssignment(alias,ref){
   // A single assignment to a previously uninitialized lexical let
   // destructuring target is its first known value, not a later mutation.
   // Its array provenance is already projected from the source expression.
   const defs=alias?.defs||[];
   if(defs.length!==1||defs[0].parent?.kind!=='let'||
      defs[0].node?.id?.type!=='Identifier'||defs[0].node?.init)
    return false;
   // eslint-scope also reports the uninitialized `let x;` declaration
   // as a write. It has no assigned value and is not a mutation.
   const emptyDeclaration=reference=>{
    const node=reference.identifier,parent=parentOf.get(node);
    return parent?.type==='VariableDeclarator'&&parent.id===node&&
     !parent.init;
   };
   if(emptyDeclaration(ref))return true;
   const writes=(alias.references||[]).filter(r=>
    r.isWrite()&&!r.init&&!emptyDeclaration(r));
   // Scope may emit multiple write references for the same destructuring
   // identifier. Deduplicate by the exact AST node, never by variable name.
   // Distinct later write sites still make the array provenance uncertain.
   if(writes.length===0||writes.some(r=>r.identifier!==ref.identifier))
    return false;
   // ESLint-scope may expose a write identifier at the outer destructuring
   // assignment instead of directly below the property's default. Verify
   // that the write stays inside the LHS ObjectPattern, not a later write.
   let child=ref.identifier;
   for(let depth=0;depth<MAX_DEPTH;depth++){
    const parent=parentOf.get(child);
    if(!parent)return false;
    if(parent.type==='AssignmentExpression')
     return parent.operator==='='&&parent.left?.type==='ObjectPattern'&&
      child===parent.left;
    if(parent.type==='FunctionExpression'||parent.type==='ArrowFunctionExpression'||
       parent.type==='Program'||parent.type==='VariableDeclarator')return false;
    child=parent;
   }
   return false;
  }
  for(const alias of aliases)
   if(alias?.references?.some(ref=>ref.isWrite()&&!ref.init&&
      !initialObjectPatternAssignment(alias,ref))){
    unknownAliasWrite=true;uncertain=true;
   }
    const readonly=new Set(['at','slice','concat','includes','indexOf',
   'lastIndexOf','join','keys','values','entries','findIndex','toString']);
  traverse(ast,(node,parent,key)=>{
   if(node.type==='MemberExpression'&&descendsFromArray(node.object)){
    if(parent?.type==='AssignmentExpression'&&parent.left===node||
       parent?.type==='UpdateExpression'&&parent.argument===node||
       parent?.type==='UnaryExpression'&&parent.operator==='delete'&&parent.argument===node)
     uncertain=true;
    if(parent?.type==='CallExpression'&&parent.callee===node){
     const method=stableMethodName(node);
     // a[0]() is a callback invocation, a.splice() mutates the container.
     // A native call/apply/bind on a known callback slot does not mutate
     // the enclosing array; a splice/fill on any nested row does.
     const callbackIntrinsic=['call','apply','bind'].includes(method)&&
      node.object?.type==='MemberExpression'&&
      /^(0|[1-9][0-9]*)$/.test(stableMethodName(node.object)||'');
     // A native Array slice invoked with call/apply is a read-only
     // operation on its receiver. Shadow writes to slice remain unsafe.
     const nativeSliceWrapper=['call','apply'].includes(method)&&
      node.object?.type==='MemberExpression'&&
      stableMethodName(node.object)==='slice'&&
      descendsFromArray(node.object.object)&&nativeArraySliceTrusted();
     if(method===null||(!/^(0|[1-9][0-9]*)$/.test(method)&&
        !readonly.has(method)&&!callbackIntrinsic&&!nativeSliceWrapper))
      uncertain=true;
    }
   }
   if(node.type==='Identifier'&&aliases.has(symbol(node))&&
      parent?.type==='CallExpression'&&key==='arguments'){
    const callee=parent.callee;
    // Only a direct, known receiver in slice.call/apply is read-only.
    // Extra arguments, unrelated objects and method aliases stay unsafe.
    const safeSlice=callee?.type==='MemberExpression'&&
     ['call','apply'].includes(stableMethodName(callee))&&
     callee.object?.type==='MemberExpression'&&
     stableMethodName(callee.object)==='slice'&&
     descendsFromArray(callee.object.object)&&
     parent.arguments?.[0]===node&&nativeArraySliceTrusted();
    if(!safeSlice)uncertain=true;
   }
   // A local callback array can escape into object or array carriers.
   // Conservatively invalidate its original element map until the carrier
   // and all writes to its reachable properties can be proved immutable.
   // A declaration target in an ObjectPattern is not an escaping value.
   // Only identifier REFERENCES can escape through object/array carriers.
   if(node.type==='Identifier'&&reference.has(node)&&!declaration.has(node)&&
      aliases.has(symbol(node))&&
      (parent?.type==='Property'&&key==='value'||
       parent?.type==='ArrayExpression'&&key==='elements')){
    // Array-pattern projections are already tracked by the alias closure;
    // other container escapes can mutate nested arrays through properties.
    if(parent?.type==='Property')uncertain=true;
    if(parent?.type==='ArrayExpression'){
     // Transient ArrayPattern RHS are projected, not captured permanently.
     let outer=parent,next=parentOf.get(outer);
     while(next?.type==='ArrayExpression'){
      outer=next;next=parentOf.get(outer);
     }
     const temporary=(next?.type==='VariableDeclarator'&&
       next.init===outer&&next.id?.type==='ArrayPattern')||
      (next?.type==='AssignmentExpression'&&next.right===outer&&
       next.left?.type==='ArrayPattern');
     if(!temporary)uncertain=true;
    }
   }
  });
  stableArrayCache.set(variable,uncertain);
  if(uncertain)throw Error('Array callback slots may be mutated'+
   (unknownAliasWrite?' (unknown lexical write)':' (carrier or receiver)'));
 }
 function knownArraySlots(node,depth=0,seen=new Set()){
  if(!node)return null;
  if(depth>MAX_DEPTH)throw Error('Array callback provenance depth exceeded');
  if(node.type==='ArrayExpression')return node.elements;
  if(node.type==='Identifier'){
   const variable=symbol(node);
   if(!variable||typeof variable!=='object'||seen.has(variable))return null;
   if(arrayBindings.has(variable)){
    ensureStableArray(variable);return arrayBindings.get(variable);
   }
   seen.add(variable);
   const defs=variable.defs||[];
   if(defs.length!==1||!['const','let'].includes(defs[0].parent?.kind)||
      defs[0].node?.id?.type!=='Identifier')return null;
   // Lexical let is supported while its binding remains stable. Mutations
   // require a separate conservative invalidation, not silent ADVISORY.
   const slots=knownArraySlots(defs[0].node?.init,depth+1,seen);
   if(slots)ensureStableArray(variable);
   return slots;
  }
  // Preserve provenance of nested arrays and aliases to their rows.
  if(node.type==='MemberExpression'){
   if(!node.computed)return null;
   const outer=knownArraySlots(node.object,depth+1,new Set(seen));
   if(!outer)return null;
   const index=staticArrayIndex(node.property);
   if(index===null||index>=outer.length||!outer[index]||
      outer[index].type==='SpreadElement')
    throw Error('Unknown nested array slot provenance');
   return knownArraySlots(outer[index],depth+1,new Set(seen));
  }
  if(node.type==='SequenceExpression')
   return knownArraySlots(node.expressions?.at(-1),depth+1,seen);
  if(node.type==='CallExpression'){
   // Slice preserves element identity, even via call/apply or a const
   // alias to the native method. Unknown boundaries remain INCOMPLETE.
   function sliceMethod(expr,level=0,seenMethods=new Set()){
    if(!expr||level>MAX_DEPTH)return false;
    if(expr.type==='MemberExpression'){
     if(stableMethodName(expr)!=='slice')return false;
     // Returning false here silently loses the copied callback's identity.
     // An unproven inherited method MUST be INCOMPLETE, not CLEAR.
     if(!nativeArraySliceTrusted())
      throw Error('Inherited array slice intrinsic may be overwritten');
     return true;
    }
    if(expr.type==='Identifier'){
     const id=symbol(expr);
     if(!id||typeof id!=='object'||seenMethods.has(id))return false;
     const defs=id.defs||[];
     if(defs.length!==1||defs[0].parent?.kind!=='const'||
        defs[0].node?.id?.type!=='Identifier')return false;
     if(id.references?.some(ref=>ref.isWrite()&&!ref.init))
      throw Error('Mutable array slice alias');
     seenMethods.add(id);
     return sliceMethod(defs[0].node?.init,level+1,seenMethods);
    }
    return false;
   }
   let sliceSource=null,sliceArgs=[];
   if(node.callee?.type==='MemberExpression'&&
      stableMethodName(node.callee)==='slice'){
    sliceSource=node.callee.object;sliceArgs=node.arguments||[];
   }else if(node.callee?.type==='MemberExpression'&&
      sliceMethod(node.callee.object,depth+1)){
    const method=stableMethodName(node.callee);
    if(method==='call'){
     sliceSource=node.arguments?.[0];
     sliceArgs=(node.arguments||[]).slice(1);
    }else if(method==='apply'){
     sliceSource=node.arguments?.[0];
     const inputs=node.arguments?.[1];
     if(inputs?.type!=='ArrayExpression'||
        inputs.elements.some(e=>!e||e.type==='SpreadElement'))
      throw Error('Unknown slice.apply arguments');
     sliceArgs=inputs.elements;
    }
   }
   if(sliceSource){
    const slots=knownArraySlots(sliceSource,depth+1,new Set(seen));
    if(slots){
     const begin=sliceArgs.length?staticArrayIndex(sliceArgs[0]):0;
     const end=sliceArgs.length>1?staticArrayIndex(sliceArgs[1]):slots.length;
     if(begin===null||end===null)throw Error('Unknown slice bounds');
     return slots.slice(begin,end);
    }
   }
   const {fn}=invocation(node,depth+1);
   if(!fn)return null;
   const returns=returnExpressions(fn);
   if(returns.length!==1)return null;
   return knownArraySlots(returns[0],depth+1,seen);
  }
  return null;
 }
 function staticArrayIndex(node){
  const value=stableKeyValue(node);
  if(value===null||!/^(0|[1-9][0-9]*)$/.test(value))return null;
  const index=Number(value);
  return Number.isSafeInteger(index)&&index>=0?index:null;
 }
 // Bound callables retain the original function and the leading arguments.
 // Resolve direct .bind()(), nested binds and lexical const aliases uniformly.
 // This is an invocation value, not an eagerly executed call.
 // A native Function.prototype intrinsic has a distinct provenance kind.
 // When extracted, its name is not enough: check shadowing on its source.
 // Resolve built-in property writers through immutable, lexical aliases.
 // Do not accept a locally shadowed Object/Reflect as a trusted definer.
 // Root identity is lexical and read-only, not merely a spelling.
 function definerRoot(node,depth=0,seen=new Set()){
  if(!node||depth>MAX_DEPTH)return null;
  if(node.type==='Identifier'){
   const id=symbol(node);
   if(id==='free:Object')return 'Object';
   if(id==='free:Reflect')return 'Reflect';
   if(!id||typeof id!=='object'||seen.has(id))return null;
   const defs=id.defs||[];
   if(defs.length!==1||defs[0].parent?.kind!=='const'||
      defs[0].node?.id?.type!=='Identifier')return null;
   if(id.references?.some(ref=>ref.isWrite()&&!ref.init))
    throw Error('Mutable native definer root');
   seen.add(id);return definerRoot(defs[0].node?.init,depth+1,seen);
  }
  if(node.type==='SequenceExpression')
   return definerRoot(node.expressions?.at(-1),depth+1,seen);
  return null;
 }
 function definerOperation(node,depth=0,seen=new Set()){
  if(!node||depth>MAX_DEPTH)return null;
  if(node.type==='MemberExpression'){
   const root=definerRoot(node.object,depth+1),method=stableMethodName(node);
   if(root==='Object'&&
      ['defineProperty','defineProperties','assign'].includes(method))return method;
   if(root==='Reflect'&&['defineProperty','set'].includes(method))return method;
   return null;
  }
  if(node.type==='Identifier'){
   const key=symbol(node);
   if(!key||typeof key!=='object'||seen.has(key))return null;
   const defs=key.defs||[];
   if(defs.length!==1||defs[0].parent?.kind!=='const'||
      defs[0].node?.id?.type!=='Identifier')return null;
   if(key.references?.some(ref=>ref.isWrite()&&!ref.init))
    throw Error('Mutable property definer alias provenance');
   seen.add(key);
   return definerOperation(defs[0].node?.init,depth+1,seen);
  }
  if(node.type==='SequenceExpression')
   return definerOperation(node.expressions?.at(-1),depth+1,seen);
  return null;
 }
 function definerInvocation(node){
  if(node?.type!=='CallExpression')return null;
  const direct=definerOperation(node.callee);
  if(direct)return {op:direct,args:node.arguments||[]};
  if(node.callee?.type!=='MemberExpression')return null;
  const outer=stableMethodName(node.callee);
  if(outer!=='call'&&outer!=='apply')return null;
  const op=definerOperation(node.callee.object);
  if(!op)return null;
  if(outer==='call')return {op,args:(node.arguments||[]).slice(1)};
  const supplied=node.arguments?.[1];
  if(supplied?.type!=='ArrayExpression'||
     supplied.elements.some(e=>!e||e.type==='SpreadElement'))
   throw Error('Unresolved native definer apply arguments');
  return {op,args:supplied.elements};
 }
 function hasIntrinsicOverride(receiver,method){
  if(receiver?.type!=='Identifier')return false;
  const id=symbol(receiver);
  if(!id)return false;
  let overrides=intrinsicShadowCache.get(id);
  if(!overrides){
   overrides=new Set();
   // Every lexical alias of the same function object shares writable
   // properties. A mutation through any alias invalidates the intrinsic.
   const aliases=new Set([id]);
   const target=resolveFunction(receiver);
   if(target)for(const [candidate,fn] of functions)
    if(fn===target)aliases.add(candidate);
   traverse(ast,node=>{
    if(node.type==='AssignmentExpression'&&node.left?.type==='MemberExpression'&&
       node.left.object?.type==='Identifier'&&aliases.has(symbol(node.left.object))){
     overrides.add(stableMethodName(node.left)||'*');
    }
    if(node.type==='UpdateExpression'&&node.argument?.type==='MemberExpression'&&
       node.argument.object?.type==='Identifier'&&aliases.has(symbol(node.argument.object)))
     overrides.add(stableMethodName(node.argument)||'*');
    if(node.type==='CallExpression'){
     const call=definerInvocation(node);
     const written=call?.args?.[0];
     if(written&&(
        written.type==='Identifier'&&aliases.has(symbol(written))||
        target&&resolveFunction(written)===target))
      overrides.add(['defineProperty','set'].includes(call.op)?
       stableKeyValue(call.args[1])||'*':'*');
    }
   });
   intrinsicShadowCache.set(id,overrides);
  }
  return overrides.has('*')||overrides.has(method);
 }
 function intrinsicName(node,depth=0,seen=new Set()){
  if(!node)return null;
  if(depth>MAX_DEPTH)throw Error('Intrinsic provenance depth exceeded');
  if(node.type==='Identifier'){
   const id=symbol(node);
   if(intrinsicValues.has(id))return intrinsicValues.get(id);
   if(!id||typeof id!=='object'||seen.has(id))return null;
   seen.add(id);
   const defs=id.defs||[];
   if(defs.length!==1||defs[0].parent?.kind!=='const'||
      defs[0].node?.id?.type!=='Identifier')return null;
   return intrinsicName(defs[0].node?.init,depth+1,seen);
  }
  if(node.type==='SequenceExpression')
   return intrinsicName(node.expressions?.at(-1),depth+1,seen);
  if(node.type==='MemberExpression'){
   // Array slots preserve extracted native intrinsics just like functions.
   const slots=node.computed?knownArraySlots(node.object,depth+1):null;
   if(slots){
    const index=staticArrayIndex(node.property);
    if(index===null||index>=slots.length||!slots[index]||
       slots[index].type==='SpreadElement')
     throw Error('Unknown extracted intrinsic array slot provenance');
    return intrinsicName(slots[index],depth+1,new Set(seen));
   }
   const method=stableMethodName(node);
   if(['call','apply','bind'].includes(method)&&
      resolveFunction(node.object,depth+1)){
    if(hasIntrinsicOverride(node.object,method))
     throw Error('Function.prototype intrinsic may be shadowed');
    return method;
   }
   const map=propertyKinds(node.object,depth+1);
   const descriptor=method===null?null:map?.get(method);
   if(descriptor?.optional)throw Error('Optional intrinsic identity unresolved');
   return descriptor?.intrinsic||null;
  }
  return null;
 }
 function rememberIntrinsic(destination,intrinsic){
  if(!destination||!intrinsic)return false;
  const current=intrinsicValues.get(destination);
  if(current&&current!==intrinsic)throw Error('Conflicting intrinsic alias');
  if(current)return false;
  intrinsicValues.set(destination,intrinsic);
  return true;
 }
 function boundCallable(node,depth=0,seen=new Set()){
  if(!node)return null;
  if(depth>MAX_DEPTH)throw Error('Bound function provenance depth exceeded');
  if(node.type==='Identifier'){
   const variable=symbol(node);
   if(!variable||typeof variable!=='object'||seen.has(variable))return null;
   if(boundValues.has(variable))return boundValues.get(variable);
   seen.add(variable);
   const defs=variable.defs||[];
   if(defs.length!==1||defs[0].parent?.kind!=='const'||
      defs[0].node?.id?.type!=='Identifier')return null;
   return boundCallable(defs[0].node?.init,depth+1,seen);
  }
  if(node.type==='SequenceExpression')
   return boundCallable(node.expressions?.at(-1),depth+1,seen);
  if(node.type==='MemberExpression'){
   const props=propertyKinds(node.object,depth+1),key=stableMethodName(node);
   const descriptor=key===null?null:props?.get(key);
   if(descriptor?.optional)throw Error('Unresolved bound member identity');
   return descriptor?.bound||null;
  }
  // Native bind.call and bind.apply both return a bound value.
  if(node.type==='CallExpression'&&node.callee?.type==='MemberExpression'&&
     ['call','apply'].includes(stableMethodName(node.callee))&&
     intrinsicName(node.callee.object,depth+1)==='bind'){
   const outer=stableMethodName(node.callee);
   let args=node.arguments||[];
   if(outer==='apply'){
    const supplied=node.arguments?.[1];
    if(supplied?.type!=='ArrayExpression'||
       supplied.elements.some(e=>!e||e.type==='SpreadElement'))
     throw Error('Unknown native bind.apply argument array');
    args=[node.arguments[0],...supplied.elements];
   }
   const linked=boundCallable(args[0],depth+1,seen);
   const fn=linked?.fn||resolveFunction(args[0],depth+1);
   if(!fn)throw Error('Unknown indirect bind target');
   const boundArgs=[...(linked?.args||[]),...args.slice(2)];
   if(boundArgs.some(arg=>!arg||arg.type==='SpreadElement'))
    throw Error('Unknown prebound spread');
   return {fn,args:boundArgs,receiver:linked?.receiver||args[1]||null};
  }
  if(node.type==='CallExpression'&&node.callee?.type==='MemberExpression'&&
     stableMethodName(node.callee)==='bind'){
   // Binding an extracted native call/apply must retain the original target.
   const native=intrinsicName(node.callee.object,depth+1);
   if(native){
    if(native==='bind')throw Error('Nested native bind target unresolved');
    const fn=resolveFunction(node.arguments?.[0],depth+1);
    if(!fn)throw Error('Unknown native intrinsic bind target');
    if(!node.arguments?.[1]||
       (node.arguments||[]).some(arg=>arg?.type==='SpreadElement'))
     throw Error('Unknown native intrinsic bind arguments');
    if(native==='call')
     return {fn,args:node.arguments.slice(2),receiver:node.arguments[1]};
    const values=node.arguments[2];
    if(!values)return {fn,args:[],receiver:node.arguments[1]};
    if(values.type!=='ArrayExpression'||values.elements.some(e=>!e||e.type==='SpreadElement'))
     throw Error('Unknown native apply bind argument array');
    return {fn,args:values.elements,receiver:node.arguments[1]};
   }
   const earlier=boundCallable(node.callee.object,depth+1,seen);
   const fn=earlier?.fn||resolveFunction(node.callee.object,depth+1);
   if(!fn)return null;
   if((node.arguments||[]).some(arg=>arg?.type==='SpreadElement'))
    throw Error('Unknown prebound spread argument provenance');
   return {fn,args:[...(earlier?.args||[]),...(node.arguments||[]).slice(1)],
    receiver:earlier?.receiver||node.arguments?.[0]||null};
  }
  if(node.type==='CallExpression'){
   // A local factory may return a prebound callable, rather than an ordinary
   // function. Keep that closure's arguments through the return boundary.
   const {fn}=invocation(node,depth+1);
   if(!fn)return null;
   const returns=returnExpressions(fn);
   if(!returns.length)return null;
   const candidates=returns.map(expr=>boundCallable(expr,depth+1,new Set(seen)));
   const available=candidates.filter(Boolean);
   if(!available.length)return null;
   if(available.length!==candidates.length)
    throw Error('Partially known bound factory return');
   const first=available[0];
   if(available.some(item=>item.fn!==first.fn||item.args.length!==first.args.length||
      item.args.some((arg,index)=>arg!==first.args[index])))
    throw Error('Conflicting bound factory return');
   return first;
  }
  return null;
 }
 function resolveFunction(node,depth=0){
  if(!node)return null;
  if(depth>MAX_DEPTH)throw Error('Function provenance depth exceeded');
  if(['FunctionExpression','ArrowFunctionExpression'].includes(node.type))return node;
  if(node.type==='Identifier')return functions.get(symbol(node))||null;
  if(node.type==='ChainExpression')return resolveFunction(node.expression,depth+1);
  if(node.type==='SequenceExpression')return resolveFunction(node.expressions?.at(-1),depth+1);
  if(node.type==='ConditionalExpression'||node.type==='LogicalExpression'){
   const first=resolveFunction(node.type==='ConditionalExpression'?node.consequent:node.left,depth+1);
   const second=resolveFunction(node.type==='ConditionalExpression'?node.alternate:node.right,depth+1);
   if(first&&second&&first===second)return first;
   // Different functions may safely join only when both bodies are statically
   // trivial constants, not capable of executing code or using caller inputs.
   function pureStatic(fn){
    if(!fn||fn.params?.length)return false;
    const b=fn.body;
    if(b?.type==='Literal'||b?.type==='TemplateLiteral')return true;
    if(b?.type!=='BlockStatement')return false;
    return b.body.every(stmt=>stmt.type==='EmptyStatement'||
      (stmt.type==='ReturnStatement'&&(!stmt.argument||
       stmt.argument.type==='Literal'||stmt.argument.type==='TemplateLiteral')));
   }
   if(first&&second&&pureStatic(first)&&pureStatic(second))return null;
   if(first||second)throw Error('Ambiguous conditional function provenance');
   return null;
  }
  if(node.type==='MemberExpression'){
   const slots=node.computed?knownArraySlots(node.object,depth+1):null;
   if(slots){
    const index=node.computed?staticArrayIndex(node.property):null;
    if(index===null)throw Error('Unresolved array callback index provenance');
    if(index>=slots.length||!slots[index])return null;
    if(slots[index].type==='SpreadElement')
     throw Error('Unknown array-spread callback target');
    return resolveFunction(slots[index],depth+1);
   }
   const map=propertyKinds(node.object,depth+1),key=stableMethodName(node);
   const descriptor=key===null?null:map?.get(key);
   if(descriptor?.optional)throw Error('Optional method identity unresolved');
   return descriptor?.functionNode||null;
  }
  if(node.type==='CallExpression'){
   const {fn}=invocation(node,depth+1);
   if(!fn)return null;
   const returned=returnExpressions(fn);
   if(!returned.length)return null;
   const result=returned.map(n=>resolveFunction(n,depth+1));
   const present=result.filter(Boolean);
   if(present.length!==result.length){
    if(present.length)throw Error('Partially known returned function');
    return null;
   }
   if(present.some(n=>n!==present[0]))throw Error('Ambiguous returned function identity');
   return present[0];
  }
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   return resolveFunction(node.right,depth+1);
  return null;
 }
 // Certainty about the Reflect.apply builtin is invalid after any
 // recognizable mutation, including an aliased Object/Reflect definer.
 // Unknown identity must never become a definite execution verdict.
 let reflectApplyIntegrity=null;
 function assertReflectApplyIntegrity(){
  if(reflectApplyIntegrity===null){
   let altered=false;
   traverse(ast,node=>{
    if(altered)return;
    const direct=node.type==='AssignmentExpression'?node.left:
     node.type==='UpdateExpression'||node.type==='UnaryExpression'&&
      node.operator==='delete'?node.argument:null;
    if(direct?.type==='Identifier'&&symbol(direct)==='free:Reflect')
     altered=true;
    if(direct?.type==='MemberExpression'&&
       definerRoot(direct.object)==='Reflect'&&
       [null,'apply'].includes(stableMethodName(direct)))
     altered=true;
    if(node.type==='CallExpression'){
     const write=definerInvocation(node);
     if(write&&definerRoot(write.args?.[0])==='Reflect'){
      const key=stableKeyValue(write.args?.[1]);
      if(!['defineProperty','set'].includes(write.op)||key===null||
         key==='apply')altered=true;
     }
    }
   });
   reflectApplyIntegrity=!altered;
  }
  if(!reflectApplyIntegrity)throw Error('Reflect.apply builtin identity may be overwritten');
 }
 // Model immutable aliases and exact destructuring projections as native
 // Reflect.apply. This preserves the interprocedural argument proof rather
 // than silently treating a dangerous indirect call as CLEAR.
 function reflectsApply(node,depth=0,seen=new Set()){
  if(!node||depth>MAX_DEPTH)return false;
  if(node.type==='MemberExpression'){
   if(stableMethodName(node)!=='apply'||definerRoot(node.object)!=='Reflect')
    return false;
   assertReflectApplyIntegrity();
   return true;
  }
  if(node.type==='Identifier'){
   const id=symbol(node);
   if(!id||typeof id!=='object'||seen.has(id))return false;
   const defs=id.defs||[];
   if(defs.length!==1||defs[0].parent?.kind!=='const')return false;
   if(id.references?.some(ref=>ref.isWrite()&&!ref.init))
    throw Error('Mutable Reflect.apply alias');
   seen.add(id);
   const declaration=defs[0].node;
   if(declaration?.id?.type==='Identifier')
    return reflectsApply(declaration.init,depth+1,seen);
   if(declaration?.id?.type==='ObjectPattern'&&
      definerRoot(declaration.init)==='Reflect'){
    const member=declaration.id.properties?.find(p=>
     p.type==='Property'&&
     (p.key?.type==='Identifier'&&!p.computed?p.key.name:
      p.key?.type==='Literal'?String(p.key.value):null)==='apply'&&
     p.value===defs[0].name);
    if(member){assertReflectApplyIntegrity();return true;}
   }
   if(declaration?.id?.type==='ArrayPattern'&&
      declaration.init?.type==='ArrayExpression'){
    const index=declaration.id.elements?.indexOf(defs[0].name);
    if(index>=0)
     return reflectsApply(declaration.init.elements?.[index],depth+1,seen);
   }
   return false;
  }
  if(node.type==='SequenceExpression')
   return reflectsApply(node.expressions?.at(-1),depth+1,seen);
  return false;
 }
 function invocation(node,depth=0){
  let reflectArgs=null;
  if(reflectsApply(node.callee,depth+1))reflectArgs=node.arguments||[];
  else if(node.callee?.type==='MemberExpression'){
   const wrapper=stableMethodName(node.callee);
   if(['call','apply'].includes(wrapper)&&
      reflectsApply(node.callee.object,depth+1)){
    if(wrapper==='call')reflectArgs=(node.arguments||[]).slice(1);
    else{
     const supplied=node.arguments?.[1];
     if(supplied?.type!=='ArrayExpression'||
        supplied.elements.some(e=>!e||e.type==='SpreadElement'))
      throw Error('Unresolved Reflect.apply wrapper arguments');
     reflectArgs=supplied.elements;
    }
   }
  }
  if(reflectArgs){
   const linked=boundCallable(reflectArgs[0],depth+1);
   const fn=linked?.fn||resolveFunction(reflectArgs[0],depth+1);
   if(!fn)throw Error('Unresolved Reflect.apply target');
   const values=reflectArgs[2];
   if(values?.type!=='ArrayExpression'||
      values.elements.some(e=>!e||e.type==='SpreadElement'))
    throw Error('Unresolved Reflect.apply argument array');
   return {fn,args:[...(linked?.args||[]),...values.elements],
    receiver:linked?.receiver||reflectArgs[1]||null};
  }
  // Native call/apply wrappers around extracted call/apply/bind are
  // interprocedural invocations, not opaque member calls.
  if(node.callee?.type==='MemberExpression'){
   const outer=stableMethodName(node.callee);
   if(outer==='call'||outer==='apply'){
    const native=intrinsicName(node.callee.object,depth+1);
    if(['call','apply','bind'].includes(native)){
     let innerArgs=node.arguments||[];
     if(outer==='apply'){
      const supplied=node.arguments?.[1];
      if(supplied?.type!=='ArrayExpression'||
         supplied.elements.some(e=>!e||e.type==='SpreadElement'))
       throw Error('Unresolved extracted intrinsic apply arguments');
      innerArgs=[node.arguments[0],...supplied.elements];
     }
     const linked=boundCallable(innerArgs[0],depth+1);
     const actual=linked?.fn||resolveFunction(innerArgs[0],depth+1);
     if(!actual)throw Error('Unresolved indirect Function intrinsic target');
     if(native==='bind')return {fn:null,args:[],receiver:null};
     let passed=null;
     if(native==='call')passed=innerArgs.slice(2);
     else if(innerArgs[2]?.type==='ArrayExpression')passed=innerArgs[2].elements;
     else throw Error('Unresolved indirect apply argument provenance');
     if(passed.some(arg=>!arg||arg.type==='SpreadElement'))
      throw Error('Unknown indirect call argument provenance');
     return {fn:actual,args:[...(linked?.args||[]),...passed],
      receiver:linked?.receiver||innerArgs[1]||null};
    }
   }
  }
  const bound=boundCallable(node.callee,depth+1);
  let fn=bound?.fn||resolveFunction(node.callee,depth+1);
  let args=bound?[...bound.args,...(node.arguments||[])]:node.arguments||[];
  let receiver=bound?.receiver||null;
  if(node.callee?.type==='MemberExpression'){
   // call/apply can be reached through immutable computed string aliases.
   // Use the same lexical method resolver as the bind escape guard.
   const name=stableMethodName(node.callee);
   if(name==='call'||name==='apply'){
    if(hasIntrinsicOverride(node.callee.object,name))
     throw Error('Function intrinsic overwritten through alias');
    const linked=boundCallable(node.callee.object,depth+1);
    const target=linked?.fn||resolveFunction(node.callee.object,depth+1);
    if(target){
     fn=target;
     receiver=linked?.receiver||args[0]||null;
     const before=linked?.args||[];
     if(name==='call')args=[...before,...args.slice(1)];
     else if(args[1]?.type==='ArrayExpression')args=[...before,...args[1].elements];
     else throw Error('Unresolved Function.apply argument provenance');
    }
   }else if(fn)receiver=node.callee.object;
  }
  if(fn){
   const expanded=[];
   for(const argument of args){
    if(argument?.type!=='SpreadElement'){expanded.push(argument);continue;}
    const arr=argument.argument;
    if(arr?.type!=='ArrayExpression'||arr.elements.some(v=>!v||v.type==='SpreadElement'))
     throw Error('Unresolved spread call-site parameter provenance');
    expanded.push(...arr.elements);
   }
   args=expanded;
  }
  return {fn,args,receiver};
 }
 function riskyDescriptor(v,depth=0){
  if(depth>MAX_DEPTH)throw Error('Property risk depth exceeded');
  if(v===PRESENT||v?.functionNode)return false;
  if(v===ACCESSOR||v?.optional)return true;
  if(v===UNKNOWN)return true;
  if(typeof v==='string')return true;
  if(v?.properties)return [...v.properties.values()].some(x=>riskyDescriptor(x,depth+1));
  return false;
 }
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
    const name=stablePatternKey(p);
    if(name===null)throw Error('Computed object key provenance unknown');
    if(p.kind!=='init'){
     result.set(name,ACCESSOR);continue;
    }
    const nested=propertyKinds(p.value,depth+1);
    const cap=kind(p.value,depth+1);
    const bound=boundCallable(p.value,depth+1);
    const intrinsic=intrinsicName(p.value,depth+1);
    if(intrinsic)result.set(name,{intrinsic});
    else if(bound)result.set(name,{bound});
    else if(nested)result.set(name,{properties:nested});
    else if(cap&&cap!=='local-object')result.set(name,cap);
    else if(resolveFunction(p.value,depth+1))
     result.set(name,{functionNode:resolveFunction(p.value,depth+1)});
    else if(['Literal','TemplateLiteral'].includes(p.value?.type))result.set(name,PRESENT);
    else result.set(name,UNKNOWN);
   }
   return result;
  }
  if(node.type==='Identifier')return objects.get(symbol(node))||null;
  if(node.type==='MemberExpression'){
   const parent=propertyKinds(node.object,depth+1),name=stableMethodName(node);
   const entry=parent?.get(name);
   if(entry===ACCESSOR)throw Error('Accessor member value provenance unresolved');
   if(entry?.optional)throw Error('Optional member object provenance cannot be certified');
   return entry&&typeof entry==='object'&&entry.properties?entry.properties:null;
  }
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   return propertyKinds(node.right,depth+1);
  if(node.type==='CallExpression'){
   const {fn}=invocation(node);
   if(fn){
    const returned=returnExpressions(fn).map(n=>propertyKinds(n,depth+1));
    const available=returned.filter(Boolean);
    if(available.length){
     if(available.length!==returned.length)throw Error('Incomplete returned object provenance');
     let merged=new Map(available[0]);
     for(const item of available.slice(1))merged=joinObjectBranches(merged,item,depth+1);
     return merged;
    }
   }
  }
  if(node.type==='LogicalExpression'){
   const left=propertyKinds(node.left,depth+1),right=propertyKinds(node.right,depth+1);
   if(!left&&!right)return null;
   if(!left||!right)throw Error('Logical object provenance incomplete');
   return joinObjectBranches(left,right,depth+1);
  }
  if(node.type==='ConditionalExpression'){
   const a=propertyKinds(node.consequent,depth+1),b=propertyKinds(node.alternate,depth+1);
   if(!a&&!b)return null;
   if(!a||!b)throw Error('Conditional object provenance incomplete');
   return joinObjectBranches(a,b,depth+1);
  }
  return null;
 }
 function kind(node,depth=0){
  if(!node)return null;
  if(depth>MAX_DEPTH)throw Error('Capability graph depth exceeded');
  if(node.type==='ChainExpression'||node.type==='AwaitExpression')
   return kind(node.expression||node.argument,depth+1);
  if(node.type==='Identifier')return directKind(node);
  if(node.type==='ThisExpression')return receiverKinds.get(node)||null;
  if(node.type==='ImportExpression')
   return ['vm','node:vm'].includes(stringValue(node.source))?'vm':null;
  if(node.type==='MemberExpression'){
   const root=kind(node.object,depth+1),name=stableMethodName(node);
   const obj=propertyKinds(node.object,depth+1);
   if(obj){
    if(name!==null){
     const v=obj.get(name);
     if(v===ACCESSOR)throw Error('Accessor returned capability cannot be certified');
     if(v?.optional){
      if(typeof v.value==='string')return v.value;
      throw Error('Optional property presence cannot be certified');
     }
     return typeof v==='string'?v:
      v&&typeof v==='object'&&v.properties?'local-object':null;
    }
    if([...obj.values()].some(v=>v==='exec'))return 'exec';
    if([...obj.values()].some(v=>riskyDescriptor(v)))
     throw Error('Unknown computed property may expose a capability');
    return null;
   }
   if(root==='global'&&(EXEC.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='vm'&&(VM.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='script'&&(RUN.has(name)||(node.computed&&name===null)))return 'exec';
   if(root==='module'&&name==='require')return 'loader';
   if(root==='reflect'&&name==='get')return 'getter';
   if(root==='loader'&&name==='bind')return 'loader';
   if(node.computed&&name===null&&resolveFunction(node.object,depth+1))
    return 'uncertain-constructor';
   if(name==='constructor'&&(
    resolveFunction(node.object,depth+1)||
    (node.object?.type==='MemberExpression'&&
     node.object.object?.type==='ArrayExpression'&&
     new Set(['filter','map','reduce','some','every','find','forEach','flatMap','sort','slice']).has(propName(node.object)))))
    return 'exec';
   // A constructor pulled from a value we cannot prove non-executable is uncertain.
   // The extraction itself is not blocked, but its invocation must fail closed.
   if(name==='constructor'&&!obj)return 'uncertain-constructor';
   if(root==='unknown'&&name!==null&&(EXEC.has(name)||VM.has(name)))
    throw Error('Unknown object may expose dynamic execution');
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
   if(callee==='uncertain-constructor')throw Error('Unknown extracted constructor invocation');
   const {fn}=invocation(node);
   if(fn){
    const returned=returnExpressions(fn);
    const values=returned.map(n=>kind(n,depth+1)).filter(Boolean);
    if(values.length){
     if(values.some(v=>v!==values[0]))throw Error('Ambiguous function return capability');
     return values[0];
    }
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
  if(node.type==='LogicalExpression'){
   const left=kind(node.left,depth+1),right=kind(node.right,depth+1);
   if(left===right)return left;
   if(!left)return right;
   if(!right)return left;
   if(left==='local-object'&&right==='local-object')return left;
   throw Error('Ambiguous logical capability provenance');
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
 function rememberBound(destination,bound){
  if(!destination||!bound)return false;
  const previous=boundValues.get(destination);
  if(previous){
   if(previous.fn!==bound.fn||previous.args.length!==bound.args.length||
      previous.args.some((node,index)=>node!==bound.args[index]))
    throw Error('Conflicting bound function provenance');
   return false;
  }
  boundValues.set(destination,bound);return true;
 }
 function bindFunctionAlias(destination,fn){
  if(!destination||!fn)return false;
  const prev=functions.get(destination);
  if(prev&&prev!==fn)throw Error('Conflicting function identity');
  if(!prev){functions.set(destination,fn);return true;}
  return false;
 }
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
   const name=p.type==='RestElement'?null:stablePatternKey(p);
   if(p.type!=='RestElement'&&name===null)throw Error('Computed destructuring key unresolved');
   if(p.type==='RestElement'){
    if(!properties)throw Error('Unknown object rest provenance');
    const excluded=new Set();
    for(const previous of pattern.properties){
     if(previous===p)break;
     if(previous.type==='RestElement')throw Error('Multiple object rest bindings');
     const previousName=stablePatternKey(previous);
     if(previousName===null)throw Error('Computed rest exclusion unknown');
     excluded.add(previousName);
    }
    const residual=new Map([...properties].filter(([key])=>!excluded.has(key)));
    if(dest?.type==='Identifier')changed=assignIdentifier(dest,null,residual,true)||changed;
    else if(dest?.type==='ObjectPattern')changed=bindWithDescriptors(dest,residual)||changed;
    else throw Error('Unsupported rest binding target');
    continue;
   }
   const hasOwn=!!properties&&name!==null&&properties.has(name);
   const found=hasOwn?properties.get(name):null;
   if(found===ACCESSOR)throw Error('Accessor destructuring value is not statically known');
   if(found?.optional&&target?.type!=='AssignmentPattern')
    throw Error('Maybe-absent destructuring property without default');
   const present=found?.optional?found.value:found;
   let scalar=typeof present==='string'?present:null;
   let nested=present&&typeof present==='object'?present.properties:null;
   if(target?.type==='AssignmentPattern'&&(!hasOwn||found===UNKNOWN||found?.optional)){
    const defaultCap=kind(target.right),defaultNested=propertyKinds(target.right);
    if(defaultNested)nested=defaultNested;
    else if(defaultCap&&defaultCap!=='local-object')scalar=defaultCap;
   }
   if(!scalar&&!nested&&!properties){
    if(root==='global'&&(EXEC.has(name)||name===null))scalar='exec';
    else if(root==='vm'&&(VM.has(name)||name===null))scalar='exec';
    else if(root==='reflect'&&name==='get')scalar='getter';
    else if(root==='module'&&name==='require')scalar='loader';
   }
   if(dest?.type==='Identifier'){
    if(!properties&&!root&&name!==null&&(EXEC.has(name)||VM.has(name)))
     uncertainParameters.add(symbol(dest));
    if(present?.functionNode)
     changed=bindFunctionAlias(symbol(dest),present.functionNode)||changed;
    if(present?.bound)
     changed=rememberBound(symbol(dest),present.bound)||changed;
    if(present?.intrinsic)
     changed=rememberIntrinsic(symbol(dest),present.intrinsic)||changed;
    if(present?.arraySlots){
     const id=symbol(dest),previous=arrayBindings.get(id);
     if(previous&&previous!==present.arraySlots)throw Error('Conflicting array row');
     if(!previous){arrayBindings.set(id,present.arraySlots);changed=true;}
    }
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
 // Only a direct, unshadowed Object.freeze of shallow primitive data is
 // safe to export by reference. Functions, aliases, getters, spreads,
 // computed keys and nested mutable objects retain an UNKNOWN verdict.
 function inertFrozenExport(init){
  if(init?.type!=='CallExpression'||init.optional||init.arguments?.length!==1)return false;
  const callee=init.callee;
  if(callee?.type!=='MemberExpression'||callee.computed||
     callee.object?.type!=='Identifier'||symbol(callee.object)!=='free:Object'||
     callee.property?.type!=='Identifier'||callee.property.name!=='freeze')
   return false;
  const primitive=node=>node?.type==='Literal'&&!node.regex&&
   (node.value===null||typeof node.value==='string'||typeof node.value==='boolean'||
    typeof node.value==='number'&&Number.isFinite(node.value));
  const arg=init.arguments[0];
  if(arg?.type==='ArrayExpression')
   return arg.elements.length<=256&&arg.elements.every(primitive);
  if(arg?.type==='ObjectExpression')
   return arg.properties.length<=256&&arg.properties.every(p=>
    p.type==='Property'&&p.kind==='init'&&!p.computed&&!p.method&&
    p.key&&(p.key.type==='Identifier'||p.key.type==='Literal')&&
    keyName(p)!=='__proto__'&&primitive(p.value));
  return false;
 }
 function bind(pattern,value){
  if(!pattern)return false;
  if(pattern.type==='AssignmentPattern'){
   const missing=!value||(value.type==='Identifier'&&value.name==='undefined')||
    (value.type==='UnaryExpression'&&value.operator==='void');
   return bind(pattern.left,missing?pattern.right:value);
  }
  if(pattern.type==='MemberExpression'){
   const parent=propertyKinds(pattern.object),name=propName(pattern);
   // CommonJS exports are a known Node module boundary, not an arbitrary
   // property-write receiver. Only a precisely known, declarative object
   // export is considered analyzable. Unknown or executable values still
   // fail closed. Shadowed/local "module" variables receive no exception.
   if(!parent&&name==='exports'&&pattern.object?.type==='Identifier'&&
      symbol(pattern.object)==='free:module'){
    if(value?.type!=='ObjectExpression')
     throw Error('CommonJS export source provenance unknown');
    const exported=propertyKinds(value);
    if(!exported)throw Error('CommonJS export properties unknown');
    for(const item of value.properties){
     if(item.type!=='Property'||item.kind!=='init')
      throw Error('CommonJS export descriptor not statically known');
     const key=stablePatternKey(item);
     if(key===null)throw Error('CommonJS export key unknown');
     const descriptor=exported.get(key);
     if(descriptor===UNKNOWN){
      // A numeric constant in the same lexical scope is inert data.
      // Do not generalize this to unknown aliases or imported values.
      const variable=item.value?.type==='Identifier'?symbol(item.value):null;
      const def=variable&&typeof variable==='object'&&
       variable.defs?.length===1?variable.defs[0]:null;
      const init=def?.type==='Variable'?def.node?.init:null;
      // The initializer of a let/var alias is not its effective value:
      // reassignment after that initializer could inject unknown capabilities.
      // Even a syntactically invalid write to const must never be certified.
      const postInitWrite=variable&&typeof variable==='object'&&
       variable.references?.some(ref=>ref.isWrite()&&ref.writeExpr!==init);
      const inertNumber=init?.type==='Literal'&&typeof init.value==='number'&&
       Number.isFinite(init.value);
      if(def?.parent?.kind!=='const'||(!inertNumber&&!inertFrozenExport(init))||
         postInitWrite)
       throw Error('Unresolved CommonJS exported value');
     }else if(riskyDescriptor(descriptor))
      throw Error('Dangerous CommonJS exported capability');
    }
    return false;
   }
   // A literal nonzero CLI error status is inert. Never accept an alias to
   // process, a computed key, unknown value or any other global property write.
   if(!parent&&name==='exitCode'&&pattern.computed===false&&
      pattern.object?.type==='Identifier'&&symbol(pattern.object)==='free:process'&&
      value?.type==='Literal'&&value.value===1)return false;
   if(!parent||name===null)throw Error('Unresolved property write provenance');
   const cap=kind(value),nested=propertyKinds(value),fn=resolveFunction(value);
   if(fn){
    const old=parent.get(name);
    if(old&&old.functionNode===fn)return false;
    if(old)throw Error('Conflicting function property write');
    parent.set(name,{functionNode:fn});return true;
   }
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
  if(pattern.type==='Identifier'){
   const functionChanged=bindFunctionAlias(symbol(pattern),resolveFunction(value));
   const boundChanged=rememberBound(symbol(pattern),boundCallable(value));
   const intrinsicChanged=rememberIntrinsic(symbol(pattern),intrinsicName(value));
   return assignIdentifier(pattern,kind(value),propertyKinds(value),value!==null)||
    functionChanged||boundChanged||intrinsicChanged;
  }
  if(pattern.type==='ObjectPattern'){
   const slots=knownArraySlots(value);
   if(slots){
    const descriptors=new Map();
    for(let i=0;i<slots.length;i++){
     const element=slots[i];
     if(!element)continue;
     if(element.type==='SpreadElement')throw Error('Array projection with spread unknown');
     const native=intrinsicName(element),arraySlots=knownArraySlots(element);
     const linked=boundCallable(element),fn=resolveFunction(element);
     const cap=kind(element),nested=propertyKinds(element);
     const descriptor=native?{intrinsic:native}:arraySlots?{arraySlots}:
      linked?{bound:linked}:fn?{functionNode:fn}:
      nested?{properties:nested}:cap&&cap!=='local-object'?cap:
      ['Literal','TemplateLiteral'].includes(element.type)?PRESENT:UNKNOWN;
     descriptors.set(String(i),descriptor);
    }
    return bindWithDescriptors(pattern,descriptors,null);
   }
   return bindWithDescriptors(pattern,propertyKinds(value),kind(value));
  }
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
 const functions=new Map(),functionAliases=[],memberFunctionAliases=[];
 traverse(ast,(node)=>{
  if(node.type==='FunctionDeclaration'&&node.id)functions.set(symbol(node.id),node);
  if(node.type==='VariableDeclarator'&&node.id?.type==='Identifier'&&
      ['ArrowFunctionExpression','FunctionExpression'].includes(node.init?.type))
   functions.set(symbol(node.id),node.init);
  if(node.type==='VariableDeclarator'&&node.id?.type==='Identifier'&&
     node.init?.type==='Identifier')
   functionAliases.push([symbol(node.id),symbol(node.init)]);
  if(node.type==='VariableDeclarator'&&node.id?.type==='Identifier'&&
     node.init?.type==='MemberExpression')
   memberFunctionAliases.push([symbol(node.id),node.init]);
  if(node.type==='AssignmentExpression'&&node.operator==='='&&
     node.left?.type==='Identifier'&&node.right?.type==='Identifier')
   functionAliases.push([symbol(node.left),symbol(node.right)]);
  if(node.type==='AssignmentExpression'&&node.operator==='='&&
     node.left?.type==='Identifier'&&node.right?.type==='MemberExpression')
   memberFunctionAliases.push([symbol(node.left),node.right]);
  if(node.type==='VariableDeclarator'&&node.init)
   bindings.push([node.id,node.init]);
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   bindings.push([node.left,node.right]);
  if(node.type==='ForOfStatement'||node.type==='ForInStatement'){
   const left=node.left;
   const patterns=left?.type==='VariableDeclaration'?
    left.declarations.map(decl=>decl.id):[left];
   // for..of binds each *element*, not the iterable array object. Treating
   // the iterable as the destructured value obscures global capability sinks.
   if(node.type==='ForOfStatement'&&node.right?.type==='ArrayExpression'){
    for(const element of node.right.elements){
     if(!element||element.type==='SpreadElement')
      throw Error('For-of iterable element provenance unresolved');
     for(const pattern of patterns)bindings.push([pattern,element]);
    }
   }else for(const pattern of patterns)bindings.push([pattern,node.right]);
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
 const calls=[];
 traverse(ast,node=>{if(node.type==='CallExpression')calls.push(node);});
 let converged=false;
 for(let round=0;round<MAX_ROUNDS;round++){
  let changed=false;
  // Property provenance grows during binding; recompute shadowing each pass.
  intrinsicShadowCache.clear();
  for(const [pattern,value]of bindings)changed=bind(pattern,value)||changed;
  for(const [destination,source] of memberFunctionAliases){
   const fn=resolveFunction(source);
   if(!fn)continue;
   const previous=functions.get(destination);
   if(previous&&previous!==fn)throw Error('Conflicting member function alias');
   if(!previous){functions.set(destination,fn);changed=true;}
  }
  for(const [destination,source] of functionAliases){
   const fn=functions.get(source);
   if(!fn)continue;
   const previous=functions.get(destination);
   if(previous&&previous!==fn)throw Error('Conflicting propagated function alias');
   if(!previous){functions.set(destination,fn);changed=true;}
  }
  // Object-method targets are resolved only after object descriptors propagate.
  // Re-evaluate calls in the same bounded fixed point, not just once at startup.
  for(const call of calls){
   const {fn,args,receiver}=invocation(call);
   if(fn)changed=bindReceiver(fn,receiver)||changed;
   if(fn)for(let i=0;i<(fn.params||[]).length;i++){
    const param=fn.params[i],argument=args?.[i]||null;
    if(argument||param?.type==='AssignmentPattern')
     changed=bind(param,argument)||changed;
   }
  }
  if(!changed){converged=true;break;}
 }
 if(!converged)throw Error('Unbounded alias propagation');
 // A default is dead only if all KNOWN local call-sites prove its argument
 // present. No single invocation may globally suppress an executable RHS.
 // A syntactically present expression can still evaluate to undefined.
 // Only suppress a default when every possible value is known defined.
 function definitelyProvided(node,depth=0){
  if(!node||depth>MAX_DEPTH)return false;
  if(['Literal','FunctionExpression','ArrowFunctionExpression',
      'ObjectExpression','ArrayExpression','ClassExpression',
      'TemplateLiteral','NewExpression','BinaryExpression',
      'UpdateExpression'].includes(node.type))return true;
  if(node.type==='UnaryExpression')return node.operator!=='void';
  if(node.type==='ConditionalExpression')
   return definitelyProvided(node.consequent,depth+1)&&
     definitelyProvided(node.alternate,depth+1);
  if(node.type==='LogicalExpression')
   return node.operator==='??'?definitelyProvided(node.right,depth+1):
     definitelyProvided(node.left,depth+1)&&definitelyProvided(node.right,depth+1);
  if(node.type==='SequenceExpression')
   return definitelyProvided(node.expressions?.at(-1),depth+1);
  if(node.type==='AssignmentExpression'&&node.operator==='=')
   return definitelyProvided(node.right,depth+1);
  return false;
 }
 function markArrayInactiveDefaults(pattern,source,depth=0){
  if(pattern?.type!=='ArrayPattern'||source?.type!=='ArrayExpression')return;
  if(depth>MAX_DEPTH)throw Error('Array default analysis depth exceeded');
  // Spreads and holes may change or omit positions: never certify defaults
  // when index provenance is ambiguous.
  if(source.elements.some(e=>e?.type==='SpreadElement'))return;
  for(let i=0;i<pattern.elements.length;i++){
   const item=pattern.elements[i],input=source.elements[i];
   if(item?.type==='AssignmentPattern'&&definitelyProvided(input)){
    markInactiveDefault(item.right);
    if(item.left?.type==='ArrayPattern')
     markArrayInactiveDefaults(item.left,input,depth+1);
   }else if(item?.type==='ArrayPattern')
    markArrayInactiveDefaults(item,input,depth+1);
  }
 }
 traverse(ast,node=>{
  if(node.type==='VariableDeclarator'&&node.id?.type==='ArrayPattern'&&node.init)
   markArrayInactiveDefaults(node.id,node.init);
  if(node.type==='AssignmentExpression'&&node.operator==='='&&node.left?.type==='ArrayPattern')
   markArrayInactiveDefaults(node.left,node.right);
  if(node.type==='VariableDeclarator'&&node.id?.type==='ObjectPattern'&&node.init){
   const props=propertyKinds(node.init);
   if(!props)return;
   for(const p of node.id.properties){
    if(p.type!=='Property'||p.value?.type!=='AssignmentPattern')continue;
    const key=keyName(p),entry=key===null?null:props.get(key);
    if(key!==null&&props.has(key)&&entry!==UNKNOWN&&entry!==ACCESSOR&&!entry?.optional)
     markInactiveDefault(p.value.right);
   }
  }
 });
 // Never suppress a function default using a partial list of calls. A function
 // can escape via unknown callbacks, arrays, conditional aliases or a computed
 // method reference. An unresolved invocation must invalidate default silence.
 // We intentionally overapproximate possible calls, never certify missing edges.
 const possiblyInvoked=new Set();
 function addDescriptorFunctions(descriptor,depth=0){
  if(!descriptor||depth>MAX_DEPTH)return;
  if(descriptor.functionNode)possiblyInvoked.add(descriptor.functionNode);
   if(descriptor.bound?.fn)possiblyInvoked.add(descriptor.bound.fn);
  if(descriptor.optional)addDescriptorFunctions(descriptor.value,depth+1);
  if(descriptor.properties)
   for(const value of descriptor.properties.values())
    addDescriptorFunctions(value,depth+1);
 }
 function addObjectFunctions(properties){
  if(properties)for(const value of properties.values())addDescriptorFunctions(value);
 }
 // A bound callback cannot be certified merely because one direct call
 // supplied its defaults. But a concrete pre-bound argument cannot invoke its
 // corresponding default, and merely reading .bind is not a function call.
 // Computed keys use immutable lexical const provenance; unknown keys fail closed.
 function stableKeyValue(node,depth=0,seen=new Set()){
  if(!node||depth>MAX_DEPTH)return null;
  const direct=stringValue(node);
  if(direct!==null)return direct;
  if(node.type==='Literal'&&typeof node.value==='number'&&
     Number.isSafeInteger(node.value)&&node.value>=0)return String(node.value);
  if(node.type==='MemberExpression'){
   // Known array element used as a computed string property key.
   const slots=knownArraySlots(node.object,depth+1);
   const index=node.computed?staticArrayIndex(node.property):null;
   if(slots&&index!==null&&index<slots.length&&slots[index])
    return stableKeyValue(slots[index],depth+1,new Set(seen));
   return null;
  }
  if(node.type==='Identifier'){
   const variable=symbol(node);
   if(!variable||typeof variable!=='object'||seen.has(variable))return null;
   const defs=variable.defs||[];
   if(defs.length!==1||defs[0].parent?.kind!=='const'||
      defs[0].node?.id?.type!=='Identifier')return null;
   seen.add(variable);
   return stableKeyValue(defs[0].node?.init,depth+1,seen);
  }
  if(node.type==='BinaryExpression'&&node.operator==='+'){
   const a=stableKeyValue(node.left,depth+1,new Set(seen));
   const b=stableKeyValue(node.right,depth+1,new Set(seen));
   return a!==null&&b!==null?a+b:null;
  }
  if(node.type==='TemplateLiteral'){
   let value='';
   for(let i=0;i<node.quasis.length;i++){
    value+=node.quasis[i].value.cooked||'';
    if(i<node.expressions.length){
     const part=stableKeyValue(node.expressions[i],depth+1,new Set(seen));
     if(part===null)return null;
     value+=part;
    }
   }
   return value;
  }
  return null;
 }
 function stableMethodName(node){
  return node?.computed?stableKeyValue(node.property):propName(node);
 }
 function stablePatternKey(node){
  return node?.computed?stableKeyValue(node.key):keyName(node);
 }
 traverse(ast,(node,parent)=>{
  if(node.type!=='MemberExpression')return;
  const name=stableMethodName(node);
  if(name!=='bind'&&!(node.computed&&name===null))return;
  const target=resolveFunction(node.object);
  if(target){
   if(name==='bind'&&parent?.type==='UnaryExpression'&&parent.operator==='void')return;
   if(name==='bind'&&parent?.type==='CallExpression'&&parent.callee===node){
    // Function.prototype.bind(thisArg,...prebound) fixes these parameter values.
    // Only suppress a default when every defaulted parameter is covered by a
    // concrete, provably non-undefined value. Unsupported flows remain unsafe.
    const boundArgs=parent.arguments.slice(1);
    if((target.params||[]).every((param,index)=>
      param.type!=='AssignmentPattern'||definitelyProvided(boundArgs[index])))
     return;
   }
   possiblyInvoked.add(target);
   return;
  }
  if(node.object?.type==='MemberExpression'&&stableMethodName(node.object)===null)
   addObjectFunctions(propertyKinds(node.object.object));
 });
 traverse(ast,(node,parent,key)=>{
  if(node.type!=='Identifier'||!reference.has(node))return;
  const fn=functions.get(symbol(node));
  if(!fn||!parent)return;
  if(['ArrayExpression','ConditionalExpression','LogicalExpression',
       'ReturnStatement','SpreadElement'].includes(parent.type))
   possiblyInvoked.add(fn);
  if(parent.type==='CallExpression'&&key==='arguments'&&!invocation(parent).fn)
   possiblyInvoked.add(fn);
 });
 // Carrier graph: local aliases can hold arrays, nested objects and function
 // results. An unknown caller or dynamic property can invoke any callback
 // reachable through those carriers; never silence its executable default.
 const valueSources=new Map();
 for(const [pattern,value] of bindings){
  if(pattern?.type!=='Identifier'||!value)continue;
  const key=symbol(pattern),values=valueSources.get(key)||[];
  values.push(value);valueSources.set(key,values);
 }
 function addCarrierFunctions(node,depth=0,seen=new Set()){
  if(!node)return;
  if(depth>MAX_DEPTH)throw Error('Carrier provenance depth exceeded');
  if(node.type==='Identifier'){
   const key=symbol(node),fn=functions.get(key);
   if(fn)possiblyInvoked.add(fn);
   if(seen.has(key))return;
   seen.add(key);
   addObjectFunctions(objects.get(key));
   for(const source of valueSources.get(key)||[])
    addCarrierFunctions(source,depth+1,seen);
   return;
  }
  if(node.type==='FunctionExpression'||node.type==='ArrowFunctionExpression'){
   possiblyInvoked.add(node);return;
  }
  if(node.type==='ArrayExpression'){
   for(const item of node.elements)addCarrierFunctions(item,depth+1,seen);
   return;
  }
  if(node.type==='ObjectExpression'){
   for(const prop of node.properties)
    addCarrierFunctions(prop.type==='SpreadElement'?prop.argument:prop.value,depth+1,seen);
   return;
  }
  if(node.type==='SpreadElement'||node.type==='ChainExpression'){
   addCarrierFunctions(node.argument||node.expression,depth+1,seen);return;
  }
  if(node.type==='ConditionalExpression'||node.type==='LogicalExpression'){
   addCarrierFunctions(node.type==='ConditionalExpression'?node.consequent:node.left,depth+1,new Set(seen));
   addCarrierFunctions(node.type==='ConditionalExpression'?node.alternate:node.right,depth+1,new Set(seen));return;
  }
  if(node.type==='SequenceExpression'){
   addCarrierFunctions(node.expressions?.at(-1),depth+1,seen);return;
  }
  if(node.type==='AssignmentExpression'){
   addCarrierFunctions(node.right,depth+1,seen);return;
  }
  if(node.type==='MemberExpression'){
   const props=propertyKinds(node.object),name=propName(node);
   if(name===null)addObjectFunctions(props);
   else addDescriptorFunctions(props?.get(name));
   addCarrierFunctions(node.object,depth+1,seen);return;
  }
  if(node.type==='CallExpression'){
   const fn=invocation(node).fn;
   if(fn)for(const output of returnExpressions(fn))
    addCarrierFunctions(output,depth+1,new Set(seen));
  }
 }
 const observed=calls.map(call=>({call,...invocation(call)}));
 for(const {call,fn} of observed){
  if(fn)continue;
  // A provably constant computed key is not an unresolved dynamic call.
  // This must share the same lexical-key resolver as the bind guard above.
  if(call.callee?.type==='MemberExpression'&&stableMethodName(call.callee)===null)
   addCarrierFunctions(call.callee.object);
  for(const arg of call.arguments||[])
   addCarrierFunctions(arg);
 }
 const localFunctions=new Set(functions.values());
 for(const fn of localFunctions){
  if(possiblyInvoked.has(fn))continue;
  const relevant=observed.filter(x=>x.fn===fn);
  if(!relevant.length)continue;
  for(let i=0;i<(fn.params||[]).length;i++){
   const param=fn.params[i];
   if(param.type!=='AssignmentPattern')continue;
   if(relevant.every(x=>definitelyProvided(x.args[i])))
    markInactiveDefault(param.right);
  }
 }
 const sinks=[];
 traverse(ast,(node,parent,key)=>{
  if(ignoredDefaultNodes.has(node))return;
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
   const calleeKind=kind(node.callee);
   if(calleeKind==='uncertain-constructor')throw Error('Unknown constructor call capability');
   if(calleeKind==='exec')sinks.push(node);
   if(node.callee?.type==='MemberExpression'&&propName(node.callee)==='constructor'&&
      kind(node.callee)!=='exec'&&
      !propertyKinds(node.callee.object)&&!kind(node.callee.object))
    throw Error('Unknown constructor capability');
  }
 });
 // The normal descriptor engine cannot yet attest callback-array provenance
 // when a destructured default competes with a computed source key supplied
 // through a lexical identifier. Reject that precise uncertain family instead
 // of silently selecting the default and reporting a false CLEAR.
 function sourceObjectForDefault(expr,depth=0,seen=new Set()){
  if(!expr||depth>MAX_DEPTH)return null;
  // SequenceExpression yields its final operand. Earlier operands might
  // mutate state, so this is used only by the fail-closed ambiguous-default
  // checker, never to certify a source as safe.
  if(expr.type==='SequenceExpression')
   return sourceObjectForDefault(expr.expressions?.at(-1),depth+1,seen);
  if(expr.type==='ObjectExpression')return expr;
  if(expr.type!=='Identifier')return null;
  const id=symbol(expr);
  if(!id||typeof id!=='object'||seen.has(id))return null;
  const defs=id.defs||[];
  if(defs.length!==1||!['const','let'].includes(defs[0].parent?.kind)||
     defs[0].node?.id?.type!=='Identifier'||
     id.references?.some(ref=>ref.isWrite()&&!ref.init))return null;
  seen.add(id);
  return sourceObjectForDefault(defs[0].node.init,depth+1,seen);
 }
 function unprovenComputedArrayDefault(pattern,value,depth=0){
  if(pattern?.type!=='ObjectPattern')return false;
  if(depth>MAX_DEPTH)throw Error('Default projection provenance budget exceeded');
  if(value?.type==='ConditionalExpression')
   return unprovenComputedArrayDefault(pattern,value.consequent,depth+1)||
    unprovenComputedArrayDefault(pattern,value.alternate,depth+1);
  if(value?.type==='LogicalExpression')
   return unprovenComputedArrayDefault(pattern,value.left,depth+1)||
    unprovenComputedArrayDefault(pattern,value.right,depth+1);
  const source=sourceObjectForDefault(value);
  if(!source)return false;
  for(const entry of pattern.properties||[]){
   if(entry.type!=='Property'||entry.value?.type!=='AssignmentPattern')continue;
   const desired=stablePatternKey(entry);
   for(const p of source.properties||[]){
    if(p.type!=='Property'||!p.computed||
       ['Literal','TemplateLiteral'].includes(p.key?.type))continue;
    // Never infer an absent default from a computed key that may match.
    // Its value can be an array, a function, an object, or an expression
    // producing one; proving the key disjoint is the only safe shortcut.
    const key=stablePatternKey(p);
    if(key===null||desired===null||key===desired)return true;
   }
  }
  return false;
 }
 traverse(ast,node=>{
  if(node.type==='VariableDeclarator'&&
     unprovenComputedArrayDefault(node.id,node.init)||
     node.type==='AssignmentExpression'&&node.operator==='='&&
     unprovenComputedArrayDefault(node.left,node.right))
   throw Error('Unproven computed callback-array default provenance');
 });
 return sinks;
}
module.exports={findCapabilities,preflightAstBudget};
