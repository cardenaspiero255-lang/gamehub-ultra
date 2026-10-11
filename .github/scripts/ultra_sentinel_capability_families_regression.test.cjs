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
// Codex P1 regressions: flow-sensitive defaults, explicit this, aliases, optional properties.
const codexFivePhasesDangerous=[
 [
  "P1 explicit this via call",
  "function run(){this.eval(userPatch)}run.call(globalThis)"
 ],
 [
  "P1 explicit this via apply",
  "function run(){this.eval(userPatch)}run.apply(globalThis,[])"
 ],
 [
  "P1 alias function this call",
  "function run(){this.eval(userPatch)}const f=run;f.call(globalThis)"
 ],
 [
  "P1 this invoke multiple callsites",
  "function run(){this.eval(userPatch)}run.call({eval:x=>x});run.call(globalThis)"
 ],
 [
  "P1 member method alias",
  "const obj={m(g){g.eval(userPatch)}};const alias=obj.m;alias(globalThis)"
 ],
 [
  "P1 chained member method alias",
  "const obj={m(g){g.eval(userPatch)}};const alias=obj.m;const again=alias;again(globalThis)"
 ],
 [
  "P1 bracket member alias",
  "const obj={m(g){g.eval(userPatch)}};const alias=obj['m'];alias(globalThis)"
 ],
 [
  "P1 assigned member alias",
  "const obj={m(g){g.eval(userPatch)}};let alias;alias=obj.m;alias(globalThis)"
 ],
 [
  "P1 default with two callsites",
  "function f(x=eval(userPatch)){};f(1);f()"
 ],
 [
  "P1 default empty spread",
  "function f(x=eval(userPatch)){};f(...[])"
 ],
 [
  "P1 default undefined after value",
  "function f(x=eval(userPatch)){};f(1);f(undefined)"
 ],
 [
  "P1 default void after value",
  "function f(x=eval(userPatch)){};f(1);f(void 0)"
 ],
 [
  "P1 conditional optional property",
  "const src=flag?{x:v=>v}:{};const {x=eval}=src;x(userPatch)"
 ],
 [
  "P1 reversed optional property",
  "const src=flag?{}:{x:v=>v};const {x=eval}=src;x(userPatch)"
 ],
 [
  "P1 optional global capability default",
  "const src=flag?{x:v=>v}:{};const {x=globalThis}=src;x.eval(userPatch)"
 ],
 [
  "P1 optional property logical OR",
  "const src=flag||{x:v=>v};const {x=eval}=src;x(userPatch)"
 ]
];
const codexFivePhasesBenign=[
 [
  "P2 safe explicit this argument",
  "function f(){this.eval(42)}f.call({eval:x=>x})"
 ],
 [
  "P2 safe object method alias",
  "const o={m(g){g.eval(42)}};const alias=o.m;alias({eval:x=>x})"
 ],
 [
  "P2 single known-active argument keeps default inactive",
  "function f(x=eval(userPatch)){};f(1)"
 ],
 [
  "P2 all callsites preserve safe defaults",
  "function f(x=eval(userPatch)){};f(1);f(2)"
 ],
 [
  "P2 equal present properties across branches",
  "const src=flag?{x:v=>v}:{x:z=>z};const {x=eval}=src;x(42)"
 ],
 [
  "P2 definite own property overrides global default",
  "const src=flag?{x:v=>v}:{x:z=>z};const {x=globalThis}=src;x(42)"
 ]
];

// Codex review 2026-10-11: returned functions, destructured methods, escaped defaults,
// and array presence. Input snippets are parsed, never executed.
const codexReturnAndEscapeDangerous=[
 [
  "P1 factory returned function this.call",
  "'use strict'; function make(){return function(){this.eval(userPatch)}}make().call(globalThis)"
 ],
 [
  "P1 factory returned function this.apply",
  "'use strict'; function make(){return function(){this.eval(userPatch)}}make().apply(globalThis,[])"
 ],
 [
  "P1 factory returned arrow function args",
  "function make(){return (g)=>g.eval(userPatch)}make()(globalThis)"
 ],
 [
  "P1 factory aliased returned function",
  "function make(){return function(g){g.eval(userPatch)}}const cb=make();cb(globalThis)"
 ],
 [
  "P1 factory return function identifier",
  "function make(){function invoke(g){g.eval(userPatch)}return invoke}make()(globalThis)"
 ],
 [
  "P1 method returned function this",
  "function make(){return function(){this.eval(userPatch)}}const cb=make();cb.call(globalThis)"
 ],
 [
  "P1 destructured renamed object method",
  "const o={m(g){g.eval(userPatch)}};const {m:a}=o;a(globalThis)"
 ],
 [
  "P1 destructured shorthand object method",
  "const o={m(g){g.eval(userPatch)}};const {m}=o;m(globalThis)"
 ],
 [
  "P1 destructured alias chain",
  "const o={m(g){g.eval(userPatch)}};const {m:a}=o;const b=a;b(globalThis)"
 ],
 [
  "P1 destructured bracket-like computed constant",
  "const o={m(g){g.eval(userPatch)}};const {['m']:a}=o;a(globalThis)"
 ],
 [
  "P1 destructured assignment method",
  "const o={m(g){g.eval(userPatch)}};let a;({m:a}=o);a(globalThis)"
 ],
 [
  "P1 destructured nested object method",
  "const o={inner:{m(g){g.eval(userPatch)}}};const {inner:{m:a}}=o;a(globalThis)"
 ],
 [
  "P1 method through object rest",
  "const o={value:1,m(g){g.eval(userPatch)}};const {value,...rest}=o;rest.m(globalThis)"
 ],
 [
  "P1 escaped function via shorthand property",
  "function f(x=eval(userPatch)){};f(1);const o={f};o.f()"
 ],
 [
  "P1 escaped function via named property",
  "function f(x=eval(userPatch)){};f(1);const o={alias:f};o.alias()"
 ],
 [
  "P1 escaped function via returned object",
  "function f(x=eval(userPatch)){};f(1);function get(){return {f}}get().f()"
 ],
 [
  "P1 escaped function through parameter",
  "function f(x=eval(userPatch)){};f(1);function invoke(fn){fn()}invoke(f)"
 ],
 [
  "P1 escaped function through method assignment",
  "function f(x=eval(userPatch)){};f(1);const o={};o.f=f;o.f()"
 ],
 [
  "P1 array default undefined literal",
  "const [x=eval(userPatch)]=[undefined];x()"
 ],
 [
  "P1 array default void zero",
  "const [x=eval(userPatch)]=[void 0];x()"
 ],
 [
  "P1 array default missing",
  "const [x=eval(userPatch)]=[];x()"
 ],
 [
  "P1 array default hole",
  "const [x=eval(userPatch)]=[,];x()"
 ],
 [
  "P1 array default present other index",
  "const [a,x=eval(userPatch)]=[1];x()"
 ],
 [
  "P1 array default global absent",
  "const [x=globalThis]=[];x.eval(userPatch)"
 ]
];
const codexReturnAndEscapeBenign=[
 [
  "P2 array default present local function",
  "const [x=eval(userPatch)]=[()=>1];x()"
 ],
 [
  "P2 array default present normal function",
  "const [x=eval(userPatch)]=[function(){return 1}];x()"
 ],
 [
  "P2 array default present second element",
  "const [a,x=eval(userPatch)]=[0,()=>1];x()"
 ],
 [
  "P2 array default present local object",
  "const [x=globalThis]=[{eval:v=>v}];x.eval(42)"
 ],
 [
  "P2 array default present across literals",
  "const [x=Function]=[()=>1];x(2)"
 ],
 [
  "P2 nested array default present",
  "const [[x=eval(userPatch)]]=[[()=>1]];x()"
 ],
 [
  "P2 destructured method with benign input",
  "const o={m(g){g.eval(42)}};const {m:a}=o;a({eval:v=>v})"
 ],
 [
  "P2 factory returns harmless method",
  "function make(){return function(){this.eval(42)}}make().call({eval:v=>v})"
 ],
 [
  "P2 escaped function with safe default and all calls present",
  "function f(x=eval(userPatch)){};f(1);const o={f};o.f(2)"
 ]
];
// Conservative defaults across indirect or escaping function uses.
const codexEscapeFamilyDangerous=[
 [
  "P1 escaped default passed to unresolved callback",
  "function f(x=eval(userPatch)){};f(1);external(f)"
 ],
 [
  "P1 escaped default passed to Reflect.apply",
  "function f(x=eval(userPatch)){};f(1);Reflect.apply(f,null,[])"
 ],
 [
  "P1 escaped default stored in array",
  "function f(x=eval(userPatch)){};f(1);const arr=[f];arr[0]()"
 ],
 [
  "P1 escaped default computed method",
  "function f(x=eval(userPatch)){};f(1);const o={f};o[key]()"
 ],
 [
  "P1 escaped default object passed unknown",
  "function f(x=eval(userPatch)){};f(1);const o={f};unknown(o)"
 ],
 [
  "P1 escaped default conditional function",
  "function f(x=eval(userPatch)){};f(1);const cb=flag?f:()=>1;cb()"
 ],
 [
  "P1 escaped default literal object unknown call",
  "function f(x=eval(userPatch)){};f(1);unknown({f})"
 ]
];
const codexEscapeFamilyBenign=[
 [
  "P2 known function alias all args present",
  "function f(x=eval(userPatch)){};f(1);const alias=f;alias(2)"
 ],
 [
  "P2 direct method brackets all args present",
  "function f(x=eval(userPatch)){};f(1);const o={f};o['f'](2)"
 ],
 [
  "P2 callback local function passes explicit argument",
  "function f(x=eval(userPatch)){};const execute=(fn)=>fn(2);execute(f)"
 ]
];
// Additional collection-carrier escape regressions. Static parse only.
const codexCarrierFamilyDangerous=[
 [
  "P1 callback carrier escapes in an array variable",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};const packed=[carrier];unknown(packed)"
 ],
 [
  "P1 callback carrier passed as inline array",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};unknown([carrier])"
 ],
 [
  "P1 callback carrier escapes via array spread",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};const packed=[carrier];unknown(...packed)"
 ],
 [
  "P1 callback carrier through nested array in object",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};const outer={items:[carrier]};unknown(outer)"
 ],
 [
  "P1 dynamic callback carrier from array element",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};const packed=[carrier];packed[0][key]()"
 ],
 [
  "P1 callback carrier from function-returned array",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};const packed=[carrier];function expose(){return packed}unknown(expose())"
 ],
 [
  "P1 callback carrier conditional array",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};const packed=flag?[carrier]:[];unknown(packed)"
 ],
 [
  "P1 callback carrier nested array",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};const packed=[[carrier]];unknown(packed)"
 ]
];
const codexCarrierFamilyBenign=[
 [
  "P2 known method calls with concrete defaults still clear",
  "function f(x=eval(userPatch)){}f(1);const carrier={f};carrier.f(2)"
 ],
 [
  "P2 unrelated arrays do not activate function defaults",
  "function f(x=eval(userPatch)){}f(1);const array=[42,{safe:()=>1}];array[0]"
 ]
];
// Codex: maybe-undefined defaults, conditional aliases, getters and nested arrays.
const codexUndefinedAndAccessorDangerous=[
 [
  "P1 conditionally undefined array default",
  "const [x=eval(userPatch)]=[flag?(()=>1):undefined];x()"
 ],
 [
  "P1 opposite conditional undefined default",
  "const [x=eval(userPatch)]=[flag?undefined:(()=>1)];x()"
 ],
 [
  "P1 untrusted result array default",
  "const [x=eval(userPatch)]=[getValue()];x()"
 ],
 [
  "P1 unknown identifier array default",
  "const [x=eval(userPatch)]=[maybe];x()"
 ],
 [
  "P1 conditional function parameter default",
  "function f(x=eval(userPatch)){};f(flag?(()=>1):undefined)"
 ],
 [
  "P1 unknown argument parameter default",
  "function f(x=eval(userPatch)){};f(maybe)"
 ],
 [
  "P1 function conditional aliases",
  "function bad(g){g.eval(userPatch)}function safe(){}const f=flag?bad:safe;f(globalThis)"
 ],
 [
  "P1 reversed conditional aliases",
  "function bad(g){g.eval(userPatch)}function safe(){}const f=flag?safe:bad;f(globalThis)"
 ],
 [
  "P1 logical function alias",
  "function bad(g){g.eval(userPatch)}const f=flag||bad;f(globalThis)"
 ],
 [
  "P1 factory returning conditional aliases",
  "function bad(g){g.eval(userPatch)}function safe(){}function make(){return flag?bad:safe}make()(globalThis)"
 ],
 [
  "P1 getter undefined default",
  "const {x=eval(userPatch)}={get x(){return undefined}};x()"
 ],
 [
  "P1 getter conditional default",
  "const {x=eval(userPatch)}={get x(){return flag?(()=>1):undefined}};x()"
 ],
 [
  "P1 getter global capability result",
  "const obj={get x(){return globalThis}};obj.x.eval(userPatch)"
 ],
 [
  "P1 setter-only missing value default",
  "const obj={set x(v){}};const {x=eval(userPatch)}=obj;x()"
 ],
 [
  "P1 escaped default array callback",
  "function f(x=eval(userPatch)){};f(1);[f].forEach(cb=>cb())"
 ]
];
const codexUndefinedAndAccessorBenign=[
 [
  "P2 inner default in nested array assignment",
  "const [[x=eval(userPatch)]=[]]=[[()=>1]];x()"
 ],
 [
  "P2 inner default in nested function array",
  "const [[x=eval(userPatch)]=[]]=[[function(){return 1}]];x()"
 ],
 [
  "P2 array default conditional safe branches",
  "const [x=eval(userPatch)]=[flag?(()=>1):(()=>2)];x()"
 ],
 [
  "P2 parameter default conditional safe branches",
  "function f(x=eval(userPatch)){};f(flag?(()=>1):(()=>2))"
 ],
 [
  "P2 array default with null literal",
  "const [x=eval(userPatch)]=[null];x===null"
 ],
 [
  "P2 array default with false literal",
  "const [x=eval(userPatch)]=[false];x===false"
 ],
 [
  "P2 array default with zero literal",
  "const [x=eval(userPatch)]=[0];x===0"
 ]
];
dangerous.push(...codexNewDangerous);
dangerous.push(...codexUndefinedAndAccessorDangerous);
benign.push(...codexUndefinedAndAccessorBenign);
dangerous.push(...codexCarrierFamilyDangerous);
benign.push(...codexCarrierFamilyBenign);
dangerous.push(...codexEscapeFamilyDangerous);
benign.push(...codexEscapeFamilyBenign);
dangerous.push(...codexReturnAndEscapeDangerous);
benign.push(...codexReturnAndEscapeBenign);
dangerous.push(...codexFivePhasesDangerous);
benign.push(...codexFivePhasesBenign);
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
