'use strict';
// Regression cases for Codex review P1/P2 provenance families. Sources are parsed, never executed.
const test=require('node:test'),assert=require('node:assert/strict');
const acorn=require('acorn');
const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
function result(source){
 const ast=acorn.parse(source,{ecmaVersion:2022,sourceType:'script',ranges:true});
 try{return findCapabilities(ast).length?'BLOCKER':'CLEAR';}
 catch(error){return 'INCOMPLETE: '+error.message;}
}
const dangerous=[
 [
  "P1: nested object provenance",
  "const box={nested:{x:globalThis}}; const g=box.nested.x; g.eval(userPatch)"
 ],
 [
  "P1: nested property write",
  "const box={nested:{}}; box.nested.x=globalThis; box.nested.x.eval(userPatch)"
 ],
 [
  "P1: direct property write",
  "const box={}; box.x=globalThis; box.x.eval(userPatch)"
 ],
 [
  "P1: argument to direct function",
  "function invoke(g){g.eval(userPatch)} invoke(globalThis)"
 ],
 [
  "P1: arrow function parameter",
  "const invoke=(g)=>g.eval(userPatch); invoke(globalThis)"
 ],
 [
  "P1: propagated nested alias",
  "const box={nested:{x:globalThis}}; const middle=box.nested; const g=middle.x; g.eval(userPatch)"
 ],
 [
  "P1: conditional dangerous branch",
  "const g=enabled ? globalThis : {eval:x=>x}; g.eval(userPatch)"
 ],
 [
  "P1: object spread",
  "const box={...{x:globalThis}}; box.x.eval(userPatch)"
 ],
 [
  "P1: destructured array",
  "const [g]=[globalThis]; g.eval(userPatch)"
 ],
 [
  "P1: extracted Function constructor",
  "const F=(()=>{}).constructor; F(userPatch)()"
 ],
 [
  "P1: aliased callee function",
  "function invoke(g){g.eval(userPatch)} const f=invoke; f(globalThis)"
 ],
 [
  "P1: two successive function aliases",
  "function invoke(g){g.eval(userPatch)} const a=invoke;const b=a;b(globalThis)"
 ],
 [
  "P1: function assigned after declaration",
  "function invoke(g){g.eval(userPatch)} let f;f=invoke;f(globalThis)"
 ],
 [
  "P1: logical AND capability",
  "const enabled=true;const g=enabled && globalThis;g.eval(userPatch)"
 ],
 [
  "P1: logical OR capability",
  "const g=flag || globalThis;g.eval(userPatch)"
 ],
 [
  "P1: nullish-coalescing capability",
  "const g=flag ?? globalThis;g.eval(userPatch)"
 ],
 [
  "P1: unknown computed key with global capability",
  "const key='x';const box={x:globalThis};box[key].eval(userPatch)"
 ],
 [
  "P1: immediate function expression invocation",
  "(function(g){g.eval(userPatch)})(globalThis)"
 ],
 [
  "P1: immediate arrow invocation",
  "((g)=>g.eval(userPatch))(globalThis)"
 ],
 [
  "P1: default object destructuring",
  "const {x=globalThis}={};x.eval(userPatch)"
 ],
 [
  "P1: default array destructuring",
  "const [g=globalThis]=[];g.eval(userPatch)"
 ],
 [
  "P1: default function parameter",
  "function invoke(g=globalThis){g.eval(userPatch)}invoke()"
 ],
 [
  "P1: declared function constructor",
  "function f(){};const F=f.constructor;F(userPatch)()"
 ]
];
const benign=[
 [
  "P2: destructured local function",
  "function safe({eval:e}){return e(42)} safe({eval:x=>x})"
 ],
 [
  "P2: benign object function",
  "function safe(g){return g.eval(10)} safe({eval:x=>x})"
 ],
 [
  "P2: shadowed global",
  "function safe(globalThis){return globalThis.eval(42)} safe({eval:x=>x})"
 ],
 [
  "P2: ordinary local constructor",
  "const safe={constructor:x=>x}; safe.constructor(42)"
 ]
];
// New Codex witness families; dangerous inputs are parsed only, never executed.
const codexNewDangerous=[
 [
  "P1 sequence-wrapped IIFE",
  "(0,function(g){g.eval(userPatch)})(globalThis)"
 ],
 [
  "P1 sequence-wrapped arrow",
  "(0,(g)=>g.eval(userPatch))(globalThis)"
 ],
 [
  "P1 function.call capability",
  "function run(g){g.eval(userPatch)} run.call(null,globalThis)"
 ],
 [
  "P1 function.apply capability",
  "function run(g){g.eval(userPatch)} run.apply(null,[globalThis])"
 ],
 [
  "P1 object method invoked",
  "const api={run(g){g.eval(userPatch)}}; api.run(globalThis)"
 ],
 [
  "P1 object function-valued property",
  "const api={run:function(g){g.eval(userPatch)}}; api.run(globalThis)"
 ],
 [
  "P1 rest object capability",
  "const source={safe:1,g:globalThis};const {safe,...rest}=source;rest.g.eval(userPatch)"
 ],
 [
  "P1 rest object capability alias",
  "const source={safe:1,g:globalThis};const {safe,...rest}=source;const alias=rest;alias.g.eval(userPatch)"
 ],
 [
  "P1 nested rest capability",
  "const source={safe:1,nested:{g:globalThis}};const {safe,...rest}=source;rest.nested.g.eval(userPatch)"
 ],
 [
  "P1 standard filter Function.constructor",
  "const F=[].filter.constructor;F(userPatch)()"
 ],
 [
  "P1 standard map Function.constructor",
  "const F=[].map.constructor;F(userPatch)()"
 ],
 [
  "P1 object method Function.constructor",
  "const api={run(){return 1}};const F=api.run.constructor;F(userPatch)()"
 ],
 [
  "P1 function returns global",
  "function expose(){return globalThis}expose().eval(userPatch)"
 ],
 [
  "P1 arrow returns global",
  "const expose=()=>globalThis;expose().eval(userPatch)"
 ],
 [
  "P1 aliased local function return",
  "function expose(){return globalThis}const alias=expose;alias().eval(userPatch)"
 ],
 [
  "P1 function returns nested global object",
  "function expose(){return {g:globalThis}}expose().g.eval(userPatch)"
 ],
 [
  "P1 default still dangerous when absent",
  "const {x=eval}={};x(userPatch)"
 ]
];
const codexNewBenign=[
 [
  "P2 present benign property overrides eval default",
  "const {x=eval}={x:v=>v};x(42)"
 ],
 [
  "P2 present benign property overrides global default",
  "const {x=globalThis}={x:{eval:v=>v}};x.eval(42)"
 ],
 [
  "P2 safe method overrides Function default",
  "const {x=Function}={x:v=>v};x(42)"
 ],
 [
  "P2 present undefined-free literal benign",
  "const source={x:()=>42};const {x=eval}=source;x(42)"
 ],
 [
  "P2 property rest excludes removed dangerous key",
  "const source={g:globalThis,safe:42};const {g,...rest}=source;rest.safe+1"
 ],
 [
  "P2 rest only benign methods",
  "const source={safe:(x)=>x,value:42};const {value,...rest}=source;rest.safe(42)"
 ]
];
dangerous.push(...codexNewDangerous);
benign.push(...codexNewBenign);
for(const [name,source] of dangerous){
 test(name+' cannot be certified safe',()=>{
  assert.notEqual(result(source),'CLEAR',name+' unexpectedly passed the security gate');
 });
}
for(const [name,source] of benign){
 test(name+' stays clear to prevent false-positive review deadlocks',()=>{
  assert.equal(result(source),'CLEAR',name+' unexpectedly blocked');
 });
}
