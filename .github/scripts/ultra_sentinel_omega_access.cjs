'use strict';
/*
 * OMEGA access-control model: pure finite state checker. It checks abstract
 * authorization invariants, not device bytecode or Android runtime behavior.
 */
const STATES=Object.freeze(['UNKNOWN','GRANTED','DENIED','REVOKED']);
const EVENTS=Object.freeze(['CHECK_GRANTED','CHECK_DENIED','REVOKE','RESET','USE_RESOURCE']);
const TRANSITION=Object.freeze({
 UNKNOWN:Object.freeze({CHECK_GRANTED:'GRANTED',CHECK_DENIED:'DENIED',REVOKE:'REVOKED',RESET:'UNKNOWN',USE_RESOURCE:'UNKNOWN'}),
 GRANTED:Object.freeze({CHECK_GRANTED:'GRANTED',CHECK_DENIED:'DENIED',REVOKE:'REVOKED',RESET:'UNKNOWN',USE_RESOURCE:'GRANTED'}),
 DENIED:Object.freeze({CHECK_GRANTED:'GRANTED',CHECK_DENIED:'DENIED',REVOKE:'REVOKED',RESET:'UNKNOWN',USE_RESOURCE:'DENIED'}),
 REVOKED:Object.freeze({CHECK_GRANTED:'GRANTED',CHECK_DENIED:'DENIED',REVOKE:'REVOKED',RESET:'UNKNOWN',USE_RESOURCE:'REVOKED'})
});
function allowed(state,event){return event==='USE_RESOURCE'&&state==='GRANTED'}
function step(state,event){
 if(!Object.hasOwn(TRANSITION,state)||!EVENTS.includes(event))throw Error('Unknown access state or event');
 const next=TRANSITION[state][event];
 return {from:state,event,to:next,allowed:allowed(state,event)};
}
function verify({maxTrace=6}={}){
 if(!Number.isSafeInteger(maxTrace)||maxTrace<1||maxTrace>8)throw Error('Trace budget exceeded');
 const faults=[],stack=[{state:'UNKNOWN',trace:[]}];
 let transitions=0;
 while(stack.length){
  const cur=stack.pop();
  if(cur.trace.length>=maxTrace)continue;
  for(const event of EVENTS){
   const result=step(cur.state,event);transitions++;
   if(result.event==='USE_RESOURCE'&&result.allowed!==(cur.state==='GRANTED'))
    faults.push({event,trace:[...cur.trace,event],state:cur.state});
   if(cur.state==='REVOKED'&&event==='USE_RESOURCE'&&result.allowed)
    faults.push({event,trace:[...cur.trace,event],state:cur.state});
   if(event==='REVOKE'&&result.to!=='REVOKED')
    faults.push({event,trace:[...cur.trace,event],state:cur.state});
   if(result.to!==TRANSITION[cur.state][event])
    faults.push({event,trace:[...cur.trace,event],state:cur.state});
   stack.push({state:result.to,trace:[...cur.trace,event]});
  }
 }
 return {schema:'ultra-sentinel-omega-access/v1',valid:faults.length===0,
  states:STATES.length,events:EVENTS.length,transitions,faults:faults.slice(0,3),
  linkedRuntimeTests:false,
  scope:'Abstract permission and resource access model; connect to Kotlin tests before claiming runtime correctness.'};
}
if(require.main===module){
 const verdict=verify();console.log(JSON.stringify(verdict));
 if(!verdict.valid)process.exitCode=1;
}
module.exports={STATES,EVENTS,step,verify};
