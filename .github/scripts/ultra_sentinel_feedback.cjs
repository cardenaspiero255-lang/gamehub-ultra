'use strict';
/**
 * Ultra Sentinel feedback: explicit opt-in human labels stored as GitHub PR comments.
 * Only whitelisted GitHub collaborators, matching SHA and bounded JSON records.
 * No model fine-tuning, no self-modifying source or automatic merge.
 */
const START='<!-- ULTRA_SENTINEL_FEEDBACK_V1\n',END='\nULTRA_SENTINEL_FEEDBACK_V1_END -->';
const TRUSTED=new Set(['OWNER','MEMBER','COLLABORATOR']);
const HEX=/^[a-f0-9]{40}$/i;
const RULE=/^[A-Z][A-Z0-9_]{2,75}$/;
function sanitize(s,n=320){
 return String(s??'').replace(/[\x00-\x1f<>]/g,' ')
   .replace(/(?:sk-|ghp_|github_pat_)[A-Za-z0-9_-]{8,}/g,'[REDACTED]')
   .replace(/Bearer\s+\S+/gi,'Bearer [REDACTED]').slice(0,n);
}
function parse(comment,expected){
 if(!comment||!TRUSTED.has(comment.author_association)||typeof comment.body!=='string')return null;
 const body=comment.body.replace(/\r\n/g,'\n');
 const start=body.indexOf(START);
 if(start<0||start>5000)return null;
 const stop=body.indexOf(END,start+START.length);
 if(stop<0||stop-(start+START.length)>2000)return null;
 let data;
 try{data=JSON.parse(body.slice(start+START.length,stop))}catch{return null}
 if(!data||!['reject','accept'].includes(data.decision)||!HEX.test(data.sha||'')||
  !RULE.test(data.rule||'')||!Number.isInteger(data.pr)||data.pr<1||
  typeof data.reason!=='string'||data.reason.trim().length<12)return null;
 if(expected&&(data.sha.toLowerCase()!==expected.sha.toLowerCase()||data.pr!==expected.pr))return null;
 if(data.reason.length>350||Object.prototype.hasOwnProperty.call(data,'instructions'))return null;
 return {decision:data.decision,sha:data.sha.toLowerCase(),pr:data.pr,rule:data.rule,
  reason:sanitize(data.reason,350),author:sanitize(comment.user?.login||'verified-collaborator',70),
  commentId:Number.isSafeInteger(comment.id)?comment.id:null,
  status:'HUMAN_FEEDBACK_NOT_A_TRAINED_MODEL'};
}
function annotate(analysis,comments,{pr,sha}){
 if(!analysis||!Array.isArray(analysis.findings)||!analysis.remediations||!Array.isArray(analysis.remediations.suggestions))
  throw Error('Invalid Sentinel analysis');
 const events=(Array.isArray(comments)?comments:[]).slice(-100).map(x=>parse(x,{pr,sha})).filter(Boolean);
 const latest=new Map();
 for(const e of events)latest.set(e.rule,e);
 for(const suggestion of analysis.remediations.suggestions){
  const review=latest.get(suggestion.rule);
  if(!review)continue;
  suggestion.humanFeedback={decision:review.decision,reason:review.reason,
    author:review.author,status:review.status};
  // Explicit negative feedback blocks autopatch eligibility; never deletes findings.
  if(review.decision==='reject'){
    suggestion.autofix=false;
    suggestion.caution=(sanitize(suggestion.caution,210)+' Human reviewer rejected this fix: '+review.reason).slice(0,550);
  }
 }
 return {feedback:events.length,affected:analysis.remediations.suggestions.filter(x=>x.humanFeedback).length,
  noCodeMutation:true,noAutomaticModelTraining:true};
}
module.exports={START,END,parse,annotate};
