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
// Codex computed-bind and pre-bound default evidence; fixture text is never executed.
const codexBindFamilyDangerous=[
 [
  "P1 computed constant bind escape",
  "function f(x=eval(userPatch)){};f(1);const key='bind';const wrapped=f[key](null);wrapped()"
 ],
 [
  "P1 computed string-concat bind escape",
  "function f(x=eval(userPatch)){};f(1);const key='bi'+'nd';f[key](null)()"
 ],
 [
  "P1 computed variable bind escape",
  "function f(x=eval(userPatch)){};f(1);let key='bind';f[key](null)()"
 ],
 [
  "P1 computed dynamic bind escape",
  "function f(x=eval(userPatch)){};f(1);f[key](null)()"
 ],
 [
  "P1 alias computed bind escape",
  "function f(x=eval(userPatch)){};f(1);const alias=f;const key='bind';alias[key](null)()"
 ],
 [
  "P1 bound undefined invokes default",
  "function f(x=eval(userPatch)){};f(1);f.bind(null,undefined)()"
 ],
 [
  "P1 bound unknown invokes default",
  "function f(x=eval(userPatch)){};f(1);f.bind(null,maybe)()"
 ],
 [
  "P1 second default absent from bind",
  "function f(x=eval(userPatch),y=eval(userPatch)){};f(1,2);f.bind(null,42)()"
 ]
];
const codexBindFamilyBenign=[
 [
  "P2 bound value suppresses default",
  "function f(x=eval(userPatch)){};f(1);f.bind(null,42)()"
 ],
 [
  "P2 bound value with constant key",
  "function f(x=eval(userPatch)){};f(1);const key='bind';f[key](null,42)()"
 ],
 [
  "P2 bound alias with concrete value",
  "function f(x=eval(userPatch)){};f(1);const wrapped=f.bind(null,42);wrapped()"
 ],
 [
  "P2 reading bind does not invoke callback",
  "function f(x=eval(userPatch)){};f(1);void f.bind"
 ],
 [
  "P2 two parameters both prebound",
  "function f(x=eval(userPatch),y=eval(userPatch)){};f(1,2);f.bind(null,42,43)()"
 ]
];
// Codex HEAD 5f54e72d42: five reproduced P1 families.
// Security assertions demand a PROVEN sink, not merely an INCOMPLETE parse.
const codexFiveNewDangerous=[
 ["P1 bound call retains prebound capability","function f(g){g.eval('attack')}f.bind(null,globalThis).call(null)"],
 ["P1 bound apply retains prebound capability","function f(g){g.eval('attack')}f.bind(null,globalThis).apply(null,[])"],
 ["P1 bound call through lexical alias","function f(g){g.eval('attack')}const b=f.bind(null,globalThis);b.call(null)"],
 ["P1 bound apply through lexical alias","function f(g){g.eval('attack')}const b=f.bind(null,globalThis);b.apply(null,[])"],
 ["P1 nested bound call retains arguments","function f(a,g){g.eval('attack')}f.bind(null,1).bind(null,globalThis).call(null)"],
 ["P1 constant computed constructor extraction","const key='constructor';const C=(()=>{})[key];C('attack')()"],
 ["P1 concatenated computed constructor key","const key='con'+'structor';const C=function(){};C[key]('attack')()"],
 ["P1 array projected into object destructuring","function f(g){g.eval('attack')}const a=[f];const {0:b}=a;b(globalThis)"],
 ["P1 inline array projected into object destructuring","function f(g){g.eval('attack')}const {0:b}=[f];b(globalThis)"],
 ["P1 array numeric string projected callback","function f(g){g.eval('attack')}const a=[f];const {'0':b}=a;b(globalThis)"],
 ["P1 mutable array callback","function f(g){g.eval('attack')}let a=[f];a[0](globalThis)"],
 ["P1 mutable array callback alias","function f(g){g.eval('attack')}let a=[f];const b=a;b[0](globalThis)"],
 ["P1 destructured bound array callback","function f(g){g.eval('attack')}const [b]=[f.bind(null,globalThis)];b()"],
 ["P1 destructured bound array call","function f(g){g.eval('attack')}const [b]=[f.bind(null,globalThis)];b.call(null)"],
 ["P1 destructured bound array apply","function f(g){g.eval('attack')}const [b]=[f.bind(null,globalThis)];b.apply(null,[])"]
];
const codexFiveNewBenign=[
 ["P2 bound call benign value","function f(g){g.eval(42)}f.bind(null,{eval:x=>x}).call(null)"],
 ["P2 bound apply benign value","function f(g){g.eval(42)}f.bind(null,{eval:x=>x}).apply(null,[])"],
 ["P2 projected array benign callback","function f(g){g.eval(42)}const a=[f];const {0:b}=a;b({eval:x=>x})"],
 ["P2 mutable array benign callback","function f(g){g.eval(42)}let a=[f];a[0]({eval:x=>x})"],
 ["P2 destructured bound benign callback","function f(g){g.eval(42)}const [b]=[f.bind(null,{eval:x=>x})];b()"],
 ["P2 computed local constructor property","const key='constructor';const o={constructor:()=>42};o[key]()"]
];
for(const [name,source] of codexFiveNewDangerous)
 test(name+' proven sink is BLOCKER',()=>assert.equal(result(source),'BLOCKER',name));
for(const [name,source] of codexFiveNewBenign)
 test(name+' does not block local harmless value',()=>assert.equal(result(source),'CLEAR',name));

// Codex independent review of HEAD e1cb319, five additional escape families.
// Fixtures are parsed, not executed. Sinks proven locally require BLOCKER.
const codexRound2Dangerous=[
 ["P1 const constructor key via alias","const a='constructor';const k=a;const C=(()=>{})[k];C('attack')()"],
 ["P1 constructor key three-hop alias","const a='constructor';const b=a;const k=b;const C=function(){};C[k]('attack')()"],
 ["P1 factory returns bound executable callable","function bad(g){g.eval('attack')}function make(){return bad.bind(null,globalThis)}make()()"],
 ["P1 aliased factory bound callable","function bad(g){g.eval('attack')}function make(){return bad.bind(null,globalThis)}const wrapped=make();wrapped()"],
 ["P1 bound callable held in object","function bad(g){g.eval('attack')}const o={b:bad.bind(null,globalThis)};o.b()"],
 ["P1 bound callable nested object property","function bad(g){g.eval('attack')}const o={nested:{b:bad.bind(null,globalThis)}};o.nested.b()"],
 ["P1 bound callable destructured from object","function bad(g){g.eval('attack')}const o={b:bad.bind(null,globalThis)};const {b}=o;b()"],
 ["P1 computed zero index in object destructuring","function bad(g){g.eval('attack')}const i=0;const {[i]:b}=[bad];b(globalThis)"],
 ["P1 computed string index through alias","function bad(g){g.eval('attack')}const key='0';const index=key;const {[index]:b}=[bad];b(globalThis)"]
];
const codexRound2Uncertain=[
 ["P1 array splice mutates callback slots","function bad(g){g.eval('attack')}function safe(){}const a=[safe];a.splice(0,1,bad);a[0](globalThis)"],
 ["P1 array push mutates callback slots","function bad(g){g.eval('attack')}const a=[];a.push(bad);a[0](globalThis)"],
 ["P1 array fill mutates callback slots","function bad(g){g.eval('attack')}function safe(){}const a=[safe];a.fill(bad);a[0](globalThis)"],
 ["P1 array alias mutation invalidates cached slots","function bad(g){g.eval('attack')}function safe(){}const a=[safe];const b=a;b.splice(0,1,bad);a[0](globalThis)"]
];
const codexRound2Benign=[
 ["P2 safe constructor alias on local object","const a='constructor';const key=a;const o={constructor:()=>42};o[key]()"],
 ["P2 factory returns safe bound object","function bad(g){g.eval(10)}function make(){return bad.bind(null,{eval:x=>x})}make()"],
 ["P2 bound safe object property","function bad(g){g.eval(10)}const o={b:bad.bind(null,{eval:x=>x})};o.b()"],
 ["P2 computed benign array projection","function safe(g){g.eval(10)}const i=0;const {[i]:b}=[safe];b({eval:x=>x})"],
 ["P2 array literal safe callback unchanged","function safe(g){g.eval(10)}const a=[safe];a[0]({eval:x=>x})"]
];
for(const [name,src] of codexRound2Dangerous)
 test(name+' provenance is a proven BLOCKER',()=>assert.equal(result(src),'BLOCKER',name));
for(const [name,src] of codexRound2Uncertain)
 test(name+' cannot be certified CLEAR after mutation',()=>assert.notEqual(result(src),'CLEAR',name));
for(const [name,src] of codexRound2Benign)
 test(name+' remains CLEAR',()=>assert.equal(result(src),'CLEAR',name));

// P1 review of HEAD 78e12bb: container-escape aliases and indirect intrinsics.
// All fixtures are parsed only; no evaluated payloads.
const codexThirdDangerous=[
 ["P1 computed function constructor from literal array", "(()=>{})[['constructor'][0]]('attack')()"],
 ["P1 computed function constructor from const array", "const keys=['constructor'];(()=>{})[keys[0]]('attack')()"],
 ["P1 nested function call.call propagates global", "function bad(g){g.eval('attack')}(()=>{}).call.call(bad,null,globalThis)"],
 ["P1 Function.apply.call propagates global", "function bad(g){g.eval('attack')}(()=>{}).apply.call(bad,null,[globalThis])"],
 ["P1 bound Function.bind.call propagates global", "function bad(g){g.eval('attack')}(()=>{}).bind.call(bad,null,globalThis)()"]
];
const codexThirdUncertain=[
 ["P1 aliased array mutated by destructuring", "function bad(g){g.eval('attack')}function safe(){}const a=[safe];const [b]=[a];b.splice(0,1,bad);a[0](globalThis)"],
 ["P1 nested destructured alias mutates callback", "function bad(g){g.eval('attack')}function safe(){}const a=[safe];const [[b]]=[[a]];b.fill(bad);a[0](globalThis)"],
 ["P1 array alias mutator through object", "function bad(g){g.eval('attack')}function safe(){}const a=[safe];const o={arr:a};o.arr.splice(0,1,bad);a[0](globalThis)"],
 ["P1 computed unknown function property", "const key=dynamic;(()=>{})[key]('attack')()"]
];
const codexThirdBenign=[
 ["P2 literal array computed local method", "const k=['run'];const obj={run:()=>42};obj[k[0]]()"],
 ["P2 ordinary nested array destructuring", "const a=[1];const [b]=[a];b[0]===1"],
 ["P2 function call.call with harmless callback", "const f=(g)=>g+1;(()=>{}).call.call(f,null,41)"],
 ["P2 function apply.call with harmless callback", "const f=(g)=>g+1;(()=>{}).apply.call(f,null,[41])"]
];
for(const [name,src] of codexThirdDangerous)
 test(name+' detects proven execution sink',()=>assert.equal(result(src),'BLOCKER',name));
for(const [name,src] of codexThirdUncertain)
 test(name+' never silently certifies a possible capability',()=>assert.notEqual(result(src),'CLEAR',name));
for(const [name,src] of codexThirdBenign)
 test(name+' remains benign',()=>assert.equal(result(src),'CLEAR',name));

// Round four: Codex identified defaulted destructuring aliases,
// extracted Function.prototype intrinsics and shadowed function properties.
// All snippets are STATIC inputs to the scanner, never executed.
const round4P1=[
 ["P1 array default aliases mutable original","function bad(g){g.eval('attack')}function safe(){}const a=[safe];const [b=a]=[];b.fill(bad);a[0](globalThis)"],
 ["P1 nested array default aliases mutable original","function bad(g){g.eval('attack')}function safe(){}const a=[safe];const [[b=a]=[]]=[[]];b.splice(0,1,bad);a[0](globalThis)"],
 ["P1 assignment default aliases mutable original","function bad(g){g.eval('attack')}function safe(){}const a=[safe];let b;[b=a]=[];b.push(bad);a[0](globalThis)"],
 ["P1 extracted apply through const identifier","function bad(g){g.eval('attack')}const op=(()=>{}).apply;op.call(bad,null,[globalThis])"],
 ["P1 extracted apply stored in object","function bad(g){g.eval('attack')}const ops={a:(()=>{}).apply};ops.a.call(bad,null,[globalThis])"],
 ["P1 extracted apply in array pattern","function bad(g){g.eval('attack')}const [op]=[(()=>{}).apply];op.call(bad,null,[globalThis])"],
 ["P1 extracted bind through const alias","function bad(g){g.eval('attack')}const op=(()=>{}).bind;op.call(bad,null,globalThis)()"]
];
const round4P2=[
 ["P2 own call property via defineProperty must be uncertain","function bad(g){g.eval('attack')}function innocent(){};Object.defineProperty(innocent,'call',{value:{call(){}}});innocent.call.call(bad,null,globalThis)"],
 ["P2 own call property by direct assignment must be uncertain","function bad(g){g.eval('attack')}function innocent(){};innocent.call={call(){}};innocent.call.call(bad,null,globalThis)"]
];
const round4Benign=[
 ["P2 ordinary extracted apply with harmless arguments","const op=(()=>{}).apply;const fn=g=>g+1;op.call(fn,null,[3])"],
 ["P2 array default concrete value should not alias original","function safe(){}const a=[safe],replacement=[safe];const [b=a]=[replacement];b.push(safe);a[0]()"]
];
for(const [name,source] of round4P1)
 test(name+' never certifies dynamic execution',()=>assert.notEqual(result(source),'CLEAR',name));
for(const [name,source] of round4P2)
 test(name+' reports uncertain instead of a fabricated execution sink',()=>{
  assert.match(result(source),/^INCOMPLETE:/,name);
 });
for(const [name,source] of round4Benign)
 test(name+' stays CLEAR',()=>assert.equal(result(source),'CLEAR',name));

// Round five: shadowed function intrinsic through lexical aliases and
// Function.prototype intrinsics extracted from statically indexed arrays.
const round5Dangerous=[
 ["P1 call extracted from indexed array","function bad(g){g.eval('attack')}const ops=[(()=>{}).call];const c=ops[0];c.call(bad,null,globalThis)"],
 ["P1 apply extracted through indexed array alias","function bad(g){g.eval('attack')}const ops=[(()=>{}).apply];const copy=ops;const key=0;const op=copy[key];op.call(bad,null,[globalThis])"],
 ["P1 method override via aliased function reference","function bad(g){g.eval('attack')}function innocent(){}const alias=innocent;Object.defineProperty(alias,'call',{value:function(target,g){target(g)}});innocent.call(bad,globalThis)"],
 ["P1 direct method override through function alias","function bad(g){g.eval('attack')}function innocent(){}const alias=innocent;alias.call=function(target,g){target(g)};innocent.call(bad,globalThis)"]
];
const round5Benign=[
 ["P2 harmless indexed intrinsic call","const ops=[(()=>{}).call];const f=x=>x+1;ops[0].call(f,null,41)"],
 ["P2 uncertain overwritten call via alias remains INCOMPLETE","function bad(g){g.eval('attack')}function innocent(){}const alias=innocent;Object.defineProperty(alias,'call',{value:{call(){}}});innocent.call.call(bad,null,globalThis)"]
];
for(const [name,source] of round5Dangerous)
 test(name+' cannot silently pass',()=>assert.notEqual(result(source),'CLEAR',name));
for(const [name,source] of round5Benign)
 test(name+' preserves conservative precision',()=>{
  if(name.startsWith('P2 harmless'))assert.equal(result(source),'CLEAR',name);
  else assert.match(result(source),/^INCOMPLETE:/,name);
 });

// Round six: independent Codex review on b36e06b — four P1 families.
// All snippets are passed only to the static parser; no source is executed.
const round6Dangerous=[
 ["P1 extracted call invoked through apply","function bad(g){g.eval('attack')}const ops=[(()=>{}).call];const op=ops[0];op.apply(bad,[null,globalThis])"],
 ["P1 extracted call invoked through bind","function bad(g){g.eval('attack')}const ops=[(()=>{}).call];const op=ops[0];op.bind(bad,null)(globalThis)"],
 ["P1 extracted apply invoked through apply","function bad(g){g.eval('attack')}const op=(()=>{}).apply;op.apply(bad,[null,[globalThis]])"],
 ["P1 Reflect.defineProperty overrides call","function bad(g){g.eval('attack')}function innocent(){}Reflect.defineProperty(innocent,'call',{value:function(t,g){t(g)}});innocent.call(bad,globalThis)"],
 ["P1 aliased Object.defineProperty overrides call","function bad(g){g.eval('attack')}function innocent(){}const dp=Object.defineProperty;dp(innocent,'call',{value:function(t,g){t(g)}});innocent.call(bad,globalThis)"],
 ["P1 aliased Reflect.defineProperty overrides call","function bad(g){g.eval('attack')}function innocent(){}const dp=Reflect.defineProperty;dp(innocent,'call',{value:function(t,g){t(g)}});innocent.call(bad,globalThis)"],
 ["P1 extracted intrinsic via ObjectPattern from array","function bad(g){g.eval('attack')}const ops=[(()=>{}).call];const {0:op}=ops;op.call(bad,null,globalThis)"],
 ["P1 extracted intrinsic via nested arrays","function bad(g){g.eval('attack')}const ops=[[(()=>{}).call]];ops[0][0].call(bad,null,globalThis)"],
 ["P1 extracted intrinsic via nested array alias","function bad(g){g.eval('attack')}const ops=[[(()=>{}).call]];const row=ops[0];row[0].call(bad,null,globalThis)"]
];
const round6Benign=[
 ["P2 harmless extracted call invoked via apply","const op=(()=>{}).call;const fn=g=>g+1;op.apply(fn,[null,41])"],
 ["P2 harmless intrinsic ObjectPattern projection","const ops=[(()=>{}).call];const {0:op}=ops;const fn=g=>g+1;op.call(fn,null,41)"],
 ["P2 harmless nested array intrinsic","const ops=[[(()=>{}).call]];const fn=g=>g+1;ops[0][0].call(fn,null,41)"]
];
for(const [name,source] of round6Dangerous)
 test(name+' cannot be certified CLEAR',()=>assert.notEqual(result(source),'CLEAR',name));
for(const [name,source] of round6Benign)
 test(name+' remains CLEAR',()=>assert.equal(result(source),'CLEAR',name));

// Round seven: independent Codex findings on 1a82cf7, plus family variants.
// No source string in this suite is executed; only parsed as untrusted AST.
const round7Dangerous=[
 ["P1 extracted bind via apply returns executable closure","function bad(g){g.eval('attack')}const op=(()=>{}).bind;op.apply(bad,[null,globalThis])()"],
 ["P1 Reflect root alias changes native call","function bad(g){g.eval('attack')}function innocent(){}const R=Reflect;R.defineProperty(innocent,'call',{value:function(t,g){t(g)}});innocent.call(bad,globalThis)"],
 ["P1 Object root alias changes native call","function bad(g){g.eval('attack')}function innocent(){}const O=Object;O.defineProperty(innocent,'call',{value:function(t,g){t(g)}});innocent.call(bad,globalThis)"],
 ["P1 aliased Reflect definer invoked by call","function bad(g){g.eval('attack')}function innocent(){}const R=Reflect;const dp=R.defineProperty;dp.call(R,innocent,'call',{value:function(t,g){t(g)}});innocent.call(bad,globalThis)"],
 ["P1 aliased Reflect definer invoked by apply","function bad(g){g.eval('attack')}function innocent(){}const dp=Reflect.defineProperty;dp.apply(Reflect,[innocent,'call',{value:function(t,g){t(g)}}]);innocent.call(bad,globalThis)"],
 ["P1 nested row via ObjectPattern preserves native call","function bad(g){g.eval('attack')}const ops=[[(()=>{}).call]];const {0:row}=ops;row[0].call(bad,null,globalThis)"],
 ["P1 nested array mutator invalidates initial slots","function bad(g){g.eval('attack')}function safe(){}const ops=[[safe]];ops[0].splice(0,1,bad);ops[0][0](globalThis)"],
 ["P1 mutation through row alias invalidates parent slots","function bad(g){g.eval('attack')}function safe(){}const ops=[[safe]];const row=ops[0];row.splice(0,1,bad);ops[0][0](globalThis)"]
];
const round7Benign=[
 ["P2 harmless native bind through apply","const op=(()=>{}).bind;const safe=g=>g+1;op.apply(safe,[null,41])()"],
 ["P2 harmless root Reflect alias write","function safe(){}const R=Reflect;R.defineProperty(safe,'label',{value:1});safe()"],
 ["P2 harmless nested ObjectPattern projection","const ops=[[(()=>{}).call]];const {0:row}=ops;const safe=g=>g+1;row[0].call(safe,null,41)"],
 ["P2 read-only nested rows remain valid","const ops=[[()=>1]];ops[0].slice(0);ops[0][0]()"]
];
for(const [name,source] of round7Dangerous)
 test(name+' cannot be certified CLEAR',()=>assert.notEqual(result(source),'CLEAR',name));
for(const [name,source] of round7Benign)
 test(name+' remains CLEAR',()=>assert.equal(result(source),'CLEAR',name));

// Round eight: Codex review on 820937a; native Reflect and shallow copy flows.
const round8Dangerous=[
 ["P1 Reflect.apply executes known local function","function bad(g){g.eval('attack')}Reflect.apply(bad,null,[globalThis])"],
 ["P1 aliased Reflect.apply executes local function","function bad(g){g.eval('attack')}const R=Reflect;R.apply(bad,null,[globalThis])"],
 ["P1 Reflect.set shadows native call","function bad(g){g.eval('attack')}function innocent(){}Reflect.set(innocent,'call',function(t,g){t(g)});innocent.call(bad,globalThis)"],
 ["P1 property-carried definer receiver","function bad(g){g.eval('attack')}function innocent(){}const o={f:innocent};Object.defineProperty(o.f,'call',{value:function(t,g){t(g)}});innocent.call(bad,globalThis)"],
 ["P1 slice copies dangerous callback","function bad(g){g.eval('attack')}const ops=[bad];const copy=ops.slice();copy[0](globalThis)"],
 ["P1 slice with offset preserves dangerous callback","function safe(){}function bad(g){g.eval('attack')}const ops=[safe,bad];const copy=ops.slice(1);copy[0](globalThis)"]
];
const round8Benign=[
 ["P2 harmless Reflect.apply invocation","const safe=g=>g+1;Reflect.apply(safe,null,[41])"],
 ["P2 unrelated Reflect.set cannot shadow call","function safe(){}Reflect.set(safe,'label',42);safe()"],
 ["P2 property carried safe independent write","function safe(){}const o={f:safe};Object.defineProperty(o.f,'label',{value:42});safe()"],
 ["P2 copied safe array callback stays clear","const safe=g=>g+1;const ops=[safe];const copy=ops.slice();copy[0](41)"]
];
for(const [name,source] of round8Dangerous)
 test(name+' cannot be certified CLEAR',()=>assert.notEqual(result(source),'CLEAR',name));
for(const [name,source] of round8Benign)
 test(name+' remains CLEAR',()=>assert.equal(result(source),'CLEAR',name));

// Round nine: independent Codex review on 63c77ad, four remaining P1 families.
const round9Dangerous=[
 ["P1 array stored inside a second array can escape","function bad(g){g.eval('attack')}function safe(){}const a=[safe];const h=[a];h[0].splice(0,1,bad);a[0](globalThis)"],
 ["P1 defaulted ObjectPattern array alias tracks mutation","function bad(g){g.eval('attack')}function safe(){}const a=[safe];const {x=a}={};x.splice(0,1,bad);a[0](globalThis)"],
 ["P1 immutable alias to Reflect.apply executes callback","function bad(g){g.eval('attack')}const ra=Reflect.apply;ra(bad,null,[globalThis])"],
 ["P1 extracted Reflect.apply invoked via call","function bad(g){g.eval('attack')}Reflect.apply.call(null,bad,null,[globalThis])"],
 ["P1 extracted Reflect.apply invoked via apply","function bad(g){g.eval('attack')}const ra=Reflect.apply;ra.apply(null,[bad,null,[globalThis]])"],
 ["P1 slice through Function.prototype.call retains callback","function bad(g){g.eval('attack')}const a=[bad];const copy=a.slice.call(a);copy[0](globalThis)"],
 ["P1 aliased slice through Function.prototype.call","function bad(g){g.eval('attack')}const a=[bad];const slicer=a.slice;const copy=slicer.call(a);copy[0](globalThis)"]
];
const round9Benign=[
 ["P2 transient ArrayPattern expression is not an escape","const a=[()=>1];const [b]=[a];b[0]()"],
 ["P2 ObjectPattern default not invoked with defined benign array","const a=[()=>1],other=[()=>2];const {x=a}={x:other};x[0]()"],
 ["P2 harmless Reflect.apply alias","const safe=x=>x+1;const ra=Reflect.apply;ra(safe,null,[41])"],
 ["P2 harmless Reflect.apply through call","const safe=x=>x+1;Reflect.apply.call(null,safe,null,[41])"],
 ["P2 harmless slice through call","const a=[()=>1];const copy=a.slice.call(a);copy[0]()"]
];
for(const [name,source] of round9Dangerous)
 test(name+' cannot be certified CLEAR',()=>assert.notEqual(result(source),'CLEAR',name));
for(const [name,source] of round9Benign)
 test(name+' remains CLEAR',()=>assert.equal(result(source),'CLEAR',name));

dangerous.push(...codexNewDangerous);
dangerous.push(...codexBindFamilyDangerous);
benign.push(...codexBindFamilyBenign);
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
  if(name==='P1 escaped default passed to Reflect.apply'){
   for(const [variant,sample] of [
    ['bound local function','function f(x=eval(userPatch)){};f(1);const wrapped=f.bind(null);wrapped()'],
    ['bound object method','function f(x=eval(userPatch)){};f(1);const o={f};o.f.bind(null)()'],
    ['bound computed method','function f(x=eval(userPatch)){};f(1);const wrapped=f[\'bind\'](null);wrapped()'],
    // The method key is a stable lexical constant, not an unknown callback.
    // Both invocation forms must retain the effective argument list.
    ['constant computed call with omitted default',"function f(x=eval(userPatch)){};f(1);const key='call';f[key](null)"],
    ['constant computed call with undefined',"function f(x=eval(userPatch)){};f(1);const key='call';f[key](null,undefined)"],
    ['constant computed apply with empty array',"function f(x=eval(userPatch)){};f(1);const key='apply';f[key](null,[])"],
    ['constant computed apply with undefined',"function f(x=eval(userPatch)){};f(1);const key='apply';f[key](null,[undefined])"]
   ])assert.notEqual(result(sample),'CLEAR',variant+' bypassed executable default gate');
  }

  if(name==='P1 escaped default passed to Reflect.apply'){
   // Codex P1: these inputs must be attributed to the dangerous callable,
   // not merely rejected for an unrelated parser/provenance exception.
   for(const [variant,sample] of [
    ['direct bound function arguments','function bad(g){g.eval(userPatch)};bad.bind(null,globalThis)()'],
    ['aliased bound function arguments','function bad(g){g.eval(userPatch)};const wrapped=bad.bind(null,globalThis);wrapped()'],
    ['array-indexed local callback','function bad(g){g.eval(userPatch)};const callbacks=[bad];callbacks[0](globalThis)'],
    ['array-alias indexed callback','function bad(g){g.eval(userPatch)};const callbacks=[bad];const copy=callbacks;copy[0](globalThis)']
   ])assert.equal(result(sample),'BLOCKER',variant+' lost a proven global eval sink');
  } });
}
for(const [name,source] of benign){
 test(name+' stays clear to prevent false-positive review deadlocks',()=>{
  assert.equal(result(source),'CLEAR',name+' unexpectedly blocked');
  if(name==='P2 bound value suppresses default'){
   for(const [variant,sample] of [
    ['constant computed call with value',"function f(x=eval(userPatch)){};f(1);const key='call';f[key](null,42)"],
    ['constant computed call with null',"function f(x=eval(userPatch)){};f(1);const key='call';f[key](null,null)"],
    ['constant computed apply with value',"function f(x=eval(userPatch)){};f(1);const key='apply';f[key](null,[42])"],
    ['constant computed apply with false',"function f(x=eval(userPatch)){};f(1);const key='apply';f[key](null,[false])"]
   ])assert.equal(result(sample),'CLEAR',variant+' incorrectly enabled an inactive default');
  }

  if(name==='P2 bound value suppresses default'){
   for(const [variant,sample] of [
    ['bound function with local safe object','function bad(g){g.eval(userPatch)};bad.bind(null,{eval(){}})()'],
    ['array callback with local safe object','function bad(g){g.eval(userPatch)};const callbacks=[bad];callbacks[0]({eval(){}})']
   ])assert.equal(result(sample),'CLEAR',variant+' was blocked despite a proven benign receiver');
  } });
}
