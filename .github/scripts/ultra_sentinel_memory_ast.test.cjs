'use strict';
// Regression: trusted-main reviews candidate JavaScript only as untrusted
// source data; these tests exercise the same bounded capability AST analyser.
// Kept separate from memory.test.cjs because the mutation sandbox intentionally
// has no installed third-party packages in its copied temporary directory.
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),acorn=require('acorn');
const {findCapabilities}=require('./ultra_sentinel_capabilities.cjs');
function assertTrustedAst(file){
 const text=fs.readFileSync(path.join(__dirname,file),'utf8');
 const ast=acorn.parse(text,{ecmaVersion:'latest',sourceType:'script',
  allowHashBang:true,allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,
  locations:true,ranges:true});
 assert.deepEqual(findCapabilities(ast),[],'failed trusted AST: '+file);
}
test('trusted AST: memory.cjs is independently auditable',()=>{
 assertTrustedAst('ultra_sentinel_memory.cjs');
});
test('trusted AST: memory_ledger.cjs is independently auditable',()=>{
 assertTrustedAst('ultra_sentinel_memory_ledger.cjs');
});
