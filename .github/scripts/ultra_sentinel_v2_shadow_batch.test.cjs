'use strict';
// Ultra Sentinel V2: bounded differential shadow-mode diagnostics.
// Attack-like samples are AST-only strings. Never execute their source.
const test=require('node:test'),assert=require('node:assert/strict');
const {auditShadowBatch}=require('./ultra_sentinel_v2_shadow.cjs');

test('shadow batch is diagnostic-only and compares independent proven sinks',()=>{
 const report=auditShadowBatch([
  "eval('x')",
  "globalThis['eval']('x')",
  "new Function('return 1')",
  "function f(eval){eval('safe')}",
  "const x=42"
 ]);
 assert.equal(report.schema,'ultra-sentinel-shadow-batch/v1');
 assert.equal(report.mode,'DIAGNOSTIC_ONLY');
 assert.equal(report.inspected,5);
 assert.equal(report.provenSinks,3);
 assert.equal(report.incomplete,0);
 assert.equal(report.escalations,0);
 assert.equal(report.requiresReview,false);
});
test('shadow batch treats malformed input as INCOMPLETE, never CLEAR',()=>{
 const report=auditShadowBatch(["const a = ;"]);
 assert.equal(report.inspected,1);
 assert.equal(report.incomplete,1);
 assert.equal(report.requiresReview,true);
});
test('shadow batch rejects oversized arrays before analyzing them',()=>{
 assert.throws(()=>auditShadowBatch(Array(33).fill('')),/budget/i);
});
test('shadow batch rejects oversized source before parsing',()=>{
 assert.throws(()=>auditShadowBatch(['x'.repeat(4097)]),/budget/i);
});
test('shadow batch output cannot leak source snippets',()=>{
 const secret='PRIVATE_REDACTED_MARKER';
 const report=auditShadowBatch(["const secret='"+secret+"';"]);
 assert.equal(report.inspected,1);
 assert.equal(JSON.stringify(report).includes(secret),false);
});
