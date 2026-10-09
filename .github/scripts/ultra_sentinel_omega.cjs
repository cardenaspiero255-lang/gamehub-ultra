'use strict';
/**
 * OMEGA Contract Model Checker v1.
 * Bounded finite state model verification. Does NOT assert proof of Android SDK
 * runtime behavior or of independently written Kotlin code.
 */
const fs=require('node:fs'),path=require('node:path');
const SCHEMA='ultra-sentinel-omega/v1';
const STATES=['IDLE','RETRY_PENDING'],EVENTS=['SCHEDULE','DISPATCH','RESET'];
const REQUIRED=['BLOCK_REENTRANT_SCHEDULE','DISPATCH_TO_IDLE','RESET_TO_IDLE','FIRST_SCHEDULE_ALLOWED','TOTAL_DETERMINISTIC'];
const CONTRACT='voice-retry-gate-v1';
function key(a,b){return a+'\u0000'+b}
function check(spec){
 const errors=[],counterexamples=[];
 const fail=(reason,trace)=>{errors.push(reason);if(trace)counterexamples.push({reason,trace})};
 if(!spec||typeof spec!=='object'||Array.isArray(spec))return {valid:false,errors:['Contract must be an object'],counterexamples:[],transitionsChecked:0,reachableStates:[]};
 if(spec.schema!==SCHEMA||spec.id!==CONTRACT)fail('Unexpected contract identity');
 if(spec.initial!=='IDLE')fail('Initial state must be IDLE');
 if(JSON.stringify(spec.states)!==JSON.stringify(STATES))fail('State enumeration changed without review');
 if(JSON.stringify(spec.events)!==JSON.stringify(EVENTS))fail('Event enumeration changed without review');
 if(JSON.stringify(spec.properties)!==JSON.stringify(REQUIRED))fail('Required invariants absent or modified');
 if(spec.implementation!=='app/src/main/java/com/cardenaspiero255/gamehubultra/voice/VoiceRecognitionRetryGate.kt')
  fail('Wrong implementation target');
 if(spec.test!=='app/src/test/java/com/cardenaspiero255/gamehubultra/voice/VoiceRecognitionRetryGateContractTest.kt')
  fail('Wrong concrete test target');
 const records=Array.isArray(spec.transitions)?spec.transitions:[];
 if(records.length!==STATES.length*EVENTS.length)fail('Incomplete or oversized transition table');
 const table=new Map();
 for(const t of records.slice(0,20)){
  if(!t||!STATES.includes(t.from)||!STATES.includes(t.to)||!EVENTS.includes(t.event)||
    !(t.accepted===null||typeof t.accepted==='boolean')){
   fail('Malformed transition');continue;
  }
  const k=key(t.from,t.event);
  if(table.has(k))fail('Nondeterministic duplicate transition: '+t.from+'/'+t.event);
  else table.set(k,t);
  if(t.event==='SCHEDULE'){
   const expected=t.from==='IDLE';
   if(t.accepted!==expected||t.to!==(expected?'RETRY_PENDING':'RETRY_PENDING'))
    fail('Scheduling rule violated in '+t.from,[t.from,t.event,t.to]);
  }else if(t.accepted!==null||t.to!=='IDLE'){
   fail('DISPATCH/RESET must return IDLE: '+t.from+'/'+t.event,[t.from,t.event,t.to]);
  }
 }
 for(const a of STATES)for(const e of EVENTS)if(!table.has(key(a,e)))fail('Missing transition: '+a+'/'+e);
 const reached=new Set(['IDLE']);const queue=[{state:'IDLE',trace:[]}];
 while(queue.length&&queue.length<50){
  const item=queue.shift();
  for(const event of EVENTS){
   const tr=table.get(key(item.state,event));if(!tr)continue;
   if(!reached.has(tr.to)){reached.add(tr.to);queue.push({state:tr.to,trace:[...item.trace,event]});}
  }
 }
 for(const a of STATES)if(!reached.has(a))fail('Unreachable state: '+a);
 return {schema:SCHEMA,contract:CONTRACT,valid:errors.length===0,errors,counterexamples,
  transitionsChecked:table.size,reachableStates:[...reached].sort(),
  guarantee:'Abstract two-state transition model only; Kotlin behavior validated separately by regression tests.'};
}
function readAndCheck(root=path.resolve(__dirname,'../sentinel-contracts')){
 const file=path.join(root,'voice-retry.omega.json');
 if(!fs.existsSync(file)||fs.lstatSync(file).isSymbolicLink())throw Error('Trusted contract file missing or symlinked');
 return check(JSON.parse(fs.readFileSync(file,'utf8')));
}
if(require.main===module){
 try{const result=readAndCheck();console.log(JSON.stringify(result,null,2));if(!result.valid)process.exitCode=1}
 catch(error){console.error('OMEGA: '+String(error.message).slice(0,250));process.exitCode=2}
}
module.exports={SCHEMA,CONTRACT,check,readAndCheck};
