'use strict';
/**
 * 8,192 independent attack/authorization scenarios, not repeated shell samples.
 * Differential oracle follows the authorization contract, not engine internals:
 * no same-author approval, no bot, current immutable SHA, latest decision wins,
 * any outstanding trusted CHANGES_REQUESTED vetoes approval.
 * Sources are inert JSON objects, not GitHub network requests.
 */
const test=require('node:test');
const assert=require('node:assert/strict');
const {hasIndependentHumanApproval}=require('./ultra_sentinel_policy.cjs');
const SHA='a'.repeat(40);
const AUTHOR='repository-owner';
const association=['OWNER','MEMBER','COLLABORATOR','CONTRIBUTOR',
 'FIRST_TIME_CONTRIBUTOR','NONE','MANNEQUIN',null];
const decisions=['APPROVED','CHANGES_REQUESTED','DISMISSED','COMMENTED'];
const kinds=['User','Bot','Organization',null];
const expectedMember=new Set(['OWNER','MEMBER','COLLABORATOR']);
const relevantState=new Set(['APPROVED','CHANGES_REQUESTED','DISMISSED']);
const CASES=8192;
function subject(i){
 const role=i%8;
 const state=Math.floor(i/8)%4;
 const kind=Math.floor(i/32)%4;
 const sameAuthor=Math.floor(i/128)%2;
 const revision=Math.floor(i/256)%4;
 const disturbance=Math.floor(i/1024)%8;
 const base=1000+i*10;
 const account=sameAuthor?AUTHOR:'independent-reviewer';
 const sha=[SHA,SHA.toUpperCase(),'b'.repeat(40),undefined][revision];
 const first={id:base,state:decisions[state],author_association:association[role],
  user:{login:account,type:kinds[kind]},commit_id:sha};
 const records=[first];
 const peer={id:base+5,state:'APPROVED',author_association:'MEMBER',
  user:{login:'independent-second',type:'User'},commit_id:SHA};
 if(disturbance===1)records.push({...peer,user:{login:'bot-helper',type:'Bot'}});
 if(disturbance===2)records.push({...peer,state:'CHANGES_REQUESTED'});
 if(disturbance===3)records.push({...peer,user:{login:account,type:'User'},state:'DISMISSED'});
 if(disturbance===4)records.push({...peer,user:{login:account,type:'User'},state:'CHANGES_REQUESTED'});
 if(disturbance===5)records.push(peer);
 if(disturbance===6)records.push({...peer,author_association:'CONTRIBUTOR'});
 if(disturbance===7)records.push({...peer,user:{login:account,type:'User'},state:'COMMENTED'});
 if(i%2)records.reverse(); // GitHub API ordering is not an authority claim.
 return {records,params:{sha:SHA,author:AUTHOR},role,state,kind,sameAuthor,revision,disturbance};
}
// Deliberately independent specification oracle: descending search over review
// timestamps (IDs), not the implementation's Map/incremental update loop.
function authorized({records,params}){
 const latest=[];
 const seen=new Set();
 for(const record of [...records].sort((x,y)=>y.id-x.id)){
  if(record.user?.type!=='User'||typeof record.user?.login!=='string'||
     record.user.login.toLowerCase()===params.author.toLowerCase()||
     !expectedMember.has(record.author_association)||
     !relevantState.has(record.state))continue;
  const who=record.user.login.toLowerCase();
  if(!seen.has(who)){seen.add(who);latest.push(record);}
 }
 const veto=latest.some(r=>r.state==='CHANGES_REQUESTED');
 const approval=latest.some(r=>r.state==='APPROVED'&&
   typeof r.commit_id==='string'&&r.commit_id.toLowerCase()===params.sha);
 return !veto&&approval;
}
test('Review authority matrix has exactly 8192 distinct, independently asserted states',()=>{
 const coverage=new Set();
 for(let i=0;i<CASES;i++){
  const s=subject(i);
  coverage.add([s.role,s.state,s.kind,s.sameAuthor,s.revision,s.disturbance].join(':'));
 }
 assert.equal(coverage.size,CASES);
 assert.ok(authorized(subject(0))===true);
 assert.equal(authorized(subject(1)),true);
 assert.equal(authorized(subject(7)),false);
});
for(let i=0;i<CASES;i++){
 test('security review authority '+i.toString(16).padStart(4,'0'),()=>{
  const scenario=subject(i);
  const expected=authorized(scenario);
  const actual=hasIndependentHumanApproval(scenario.records,scenario.params);
  assert.equal(actual,expected,JSON.stringify({
   index:i,role:scenario.role,state:scenario.state,kind:scenario.kind,
   sameAuthor:scenario.sameAuthor,revision:scenario.revision,
   disturbance:scenario.disturbance,records:scenario.records
  }));
 });
}
