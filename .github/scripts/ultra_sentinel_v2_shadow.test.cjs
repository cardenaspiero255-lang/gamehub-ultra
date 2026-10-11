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
