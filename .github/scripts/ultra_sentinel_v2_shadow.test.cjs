'use strict';
// Independent bounded, read-only seed oracle for the V2 shadow audit.
const test=require('node:test'),assert=require('node:assert/strict');
const acorn=require('acorn');
const {directSinkEvidence,reconcile}=require('./ultra_sentinel_v2_shadow.cjs');
const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
const ast=s=>acorn.parse(s,{ecmaVersion:2022,sourceType:'script',ranges:true});
test('shadow sees an unshadowed direct eval invocation',()=>{
 const evidence=directSinkEvidence(ast("eval('x')"));
 assert.equal(evidence.decision,'DEFINITE_SINK');
 assert.deepEqual(evidence.rules,['global-eval']);
});
test('shadow sees a computed globalThis eval invocation',()=>{
 const evidence=directSinkEvidence(ast("globalThis['eval']('x')"));
 assert.equal(evidence.decision,'DEFINITE_SINK');
 assert.deepEqual(evidence.rules,['global-member-exec']);
});
test('shadow sees a free Function constructor',()=>{
 const evidence=directSinkEvidence(ast("new Function('return 1')"));
 assert.equal(evidence.decision,'DEFINITE_SINK');
 assert.deepEqual(evidence.rules,['global-function-constructor']);
});
test('shadow does not treat a locally shadowed eval as global',()=>{
 assert.equal(directSinkEvidence(ast("function f(eval){eval('safe')}")).decision,'NOT_PROVEN');
});
test('shadow does not treat a locally shadowed globalThis as builtin',()=>{
 assert.equal(directSinkEvidence(ast("function f(globalThis){globalThis.eval('safe')}")).decision,'NOT_PROVEN');
});
test('shadow never parses string content as executable code',()=>{
 assert.equal(directSinkEvidence(ast("const s=\"eval('x')\";")).decision,'NOT_PROVEN');
});
test('shadow escalates a direct sink missed by a candidate analyzer',()=>{
 const report=reconcile('CLEAR',directSinkEvidence(ast("eval('x')")));
 assert.equal(report.disposition,'ESCALATE');
 assert.equal(report.primary,'CLEAR');
});
test('shadow never certifies an unrecognized script as safe',()=>{
 const report=reconcile('INCOMPLETE',directSinkEvidence(ast("const x=41")));
 assert.equal(report.disposition,'NO_NEW_EVIDENCE');
 assert.equal(report.shadow,'NOT_PROVEN');
});
test('baseline and independent shadow agree on a direct global sink',()=>{
 const source=ast("globalThis.eval(userPatch)");
 const primary=findCapabilities(source).length?'BLOCKER':'CLEAR';
 const report=reconcile(primary,directSinkEvidence(source));
 assert.equal(report.primary,'BLOCKER');
 assert.notEqual(report.disposition,'ESCALATE');
});

test('shadow refuses pathological nesting before expensive scope resolution',()=>{
 const root={type:'Program',sourceType:'script',body:[]};
 let current=root;
 for(let index=0;index<300;index++){
  const child={type:'BlockStatement',body:[]};
  current.body.push(child);
  current=child;
 }
 assert.throws(()=>directSinkEvidence(root),/Shadow AST nesting budget exceeded/);
});
test('shadow refuses cyclic AST input instead of traversing forever',()=>{
 const root={type:'Program',sourceType:'script',body:[]};
 root.body.push(root);
 assert.throws(()=>directSinkEvidence(root),/Shadow AST cycle or sharing rejected/);
});

test('shadow does not claim a reassigned globalThis eval is definitely builtin',()=>{
 const cases=[
  "globalThis={eval(){return 1}};globalThis.eval('safe')",
  "const g=globalThis;g.eval=()=>1;globalThis.eval('safe')",
  "const g=globalThis;const alias=g;alias['eval']=()=>1;globalThis.eval('safe')",
  "const g=globalThis;Object.assign(g,{eval:()=>1});globalThis.eval('safe')",
  "const g=globalThis;Reflect.set(g,'Function',()=>1);globalThis.Function('safe')",
  "const O=Object;O.defineProperty(globalThis,'eval',{value:()=>1});globalThis.eval('safe')",
  "const R=Reflect;R.set(globalThis,'Function',()=>1);globalThis.Function('safe')",
  "const write=Object.defineProperty;write(globalThis,'eval',{value:()=>1});globalThis.eval('safe')",
  "const set=Reflect.set;set(globalThis,'Function',()=>1);globalThis.Function('safe')",
  "let g;g=globalThis;g.eval=()=>1;globalThis.eval('safe')",
  "let g;[g]=[globalThis];g.eval=()=>1;globalThis.eval('safe')",
  "let g;({x:g}={x:globalThis});g.eval=()=>1;globalThis.eval('safe')",
  "let g;g=globalThis;const h=g;h.Function=()=>1;globalThis.Function('safe')",
  "const {defineProperty:write}=Object;write(globalThis,'eval',{value:()=>1});globalThis.eval('safe')",
  "const {set:write}=Reflect;write(globalThis,'Function',()=>1);globalThis.Function('safe')",
  "const [write]=[Object.defineProperty];write(globalThis,'eval',{value:()=>1});globalThis.eval('safe')"
 ];
 for(const source of cases)
  assert.equal(directSinkEvidence(ast(source)).decision,'NOT_PROVEN',source);
});
test('shadow does not claim a reassigned free eval is definitely builtin',()=>{
 const cases=[
  "eval=function(){return 1};eval('safe')",
  "({eval}={eval:()=>1});eval('safe')",
  "[eval]=[()=>1];eval('safe')",
  "({nested:{eval}}={nested:{eval:()=>1}});eval('safe')",
  "for (eval of [()=>1]) {} eval('safe')",
  "for (Function of [()=>1]) {} Function('safe')"
 ];
 for(const source of cases)
  assert.equal(directSinkEvidence(ast(source)).decision,'NOT_PROVEN',source);
});
test('shadow does not claim a replaced globalThis.Function is definitely builtin',()=>{
 const source="globalThis.Function=function(){return 1};globalThis.Function('safe')";
 assert.equal(directSinkEvidence(ast(source)).decision,'NOT_PROVEN');
});
test('shadow refuses wide arrays with shared AST identity without enqueuing every entry',()=>{
 const leaf={type:'EmptyStatement'};
 const root={type:'Program',sourceType:'script',body:Array(100001).fill(leaf)};
 assert.throws(()=>directSinkEvidence(root),/Shadow AST (node budget exceeded|cycle or sharing rejected)/);
});
test('shadow refuses shared AST node identity in a small tree',()=>{
 const leaf={type:'EmptyStatement'};
 const root={type:'Program',sourceType:'script',body:[leaf,leaf]};
 assert.throws(()=>directSinkEvidence(root),/Shadow AST cycle or sharing rejected/);
});

test('shadow rejects contradictory or malformed evidence envelopes',()=>{
 const {SCHEMA}=require('./ultra_sentinel_v2_shadow.cjs');
 const bad=[
  {schema:SCHEMA,decision:'DEFINITE_SINK',rules:[]},
  {schema:SCHEMA,decision:'NOT_PROVEN',rules:['global-eval']},
  {schema:SCHEMA,decision:'DEFINITE_SINK',rules:'global-eval'},
  {schema:SCHEMA,decision:'DEFINITE_SINK',rules:['unknown-rule']},
  {schema:SCHEMA,decision:'DEFINITE_SINK',rules:['global-eval','global-eval']},
  {schema:SCHEMA,decision:'DEFINITE_SINK',rules:['global-member-exec','global-eval']}
 ];
 for(const evidence of bad)assert.throws(()=>reconcile('CLEAR',evidence),/invalid shadow evidence/);
});
