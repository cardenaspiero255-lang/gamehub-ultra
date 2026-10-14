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
// Include the exact fail-closed reason in the test title: the Core shard
// aggregates failures by name and otherwise hides the underlying exception.
// The assertion is NEVER skipped when the scanner cannot attest a file.
function attestFile(file){
 let failure=null;
 try{assertTrustedAst(file);}catch(err){failure=err;}
 test('trusted AST: '+file+' is independently auditable'+
  (failure?' ['+String(failure.message).slice(0,130)+']':''),()=>{
  if(failure)throw failure;
 });
}
attestFile('ultra_sentinel_memory.cjs');
attestFile('ultra_sentinel_memory_ledger.cjs');
