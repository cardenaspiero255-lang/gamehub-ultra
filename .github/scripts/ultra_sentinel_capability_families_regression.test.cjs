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
