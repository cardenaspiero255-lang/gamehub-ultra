'use strict';
/* Independent evaluation harness. NEVER invent competitor outputs or measure
 * latency/cost that was not independently observed. Human-labeled corpus only.
 * A green local benchmark is not evidence of superior performance in the wild. */
const SHA=/^[a-f0-9]{40}$/i;
const ID=/^[a-z0-9_-]{4,80}$/i;
const PATH=/^[a-z0-9_./-]{3,220}$/i;
const RULE=/^[A-Z][A-Z0-9_]{2,75}$/;
function key(item){
 if(!item||!RULE.test(item.rule||'')||!PATH.test(item.path||'')||item.path.includes('..')||
  item.path.startsWith('/'))return null;
 return item.rule+':'+item.path;
}
function metric(tp,fp,fn){
 const precision=tp+fp?tp/(tp+fp):null;
 const recall=tp+fn?tp/(tp+fn):null;
 return {tp,fp,fn,precision,recall,
  f1:precision===null||recall===null||precision+recall===0?null:
   (2*precision*recall)/(precision+recall)};
}
function evaluate(corpus,reports,{expectedSha}={}){
 if(!Array.isArray(corpus)||!Array.isArray(reports)||!SHA.test(expectedSha||''))
  throw Error('Trusted corpus and immutable commit SHA required');
 if(corpus.length<1||corpus.length>2000)throw Error('Corpus size outside validated bounds');
 const seen=new Set(),truth=new Map();
 for(const item of corpus){
  if(!item||!ID.test(item.id||'')||seen.has(item.id)||
   item.humanReviewed!==true||item.unseenAtTraining!==true||
   item.sha!==expectedSha||!Array.isArray(item.expected)||item.expected.length>50)
   throw Error('Human-labeled provenance or corpus identity invalid');
  seen.add(item.id);
  const expected=new Set();
  for(const f of item.expected){const k=key(f);if(!k)throw Error('Malformed ground-truth finding');expected.add(k);}
  truth.set(item.id,expected);
 }
 const results={};
 const providers=new Set();
 for(const report of reports){
  if(!report||!['ultra-sentinel','coderabbit','qodo','baseline'].includes(report.provider)||
   !seen.has(report.caseId)||report.sha!==expectedSha||
   !Array.isArray(report.findings)||report.findings.length>100||
   (report.durationMs!==undefined&&(!Number.isFinite(report.durationMs)||report.durationMs<0))||
   (report.costUsd!==undefined&&(!Number.isFinite(report.costUsd)||report.costUsd<0)))
   throw Error('Untrusted or inconsistent benchmark result');
  const label=report.provider;
  if(!results[label])results[label]={tp:0,fp:0,fn:0,caseIds:new Set(),time:[],cost:[]};
  const rec=results[label];if(rec.caseIds.has(report.caseId))throw Error('Duplicate provider report');
  rec.caseIds.add(report.caseId);providers.add(label);
  const actual=new Set();
  for(const f of report.findings){const k=key(f);if(!k)throw Error('Malformed provider finding');actual.add(k);}
  const expected=truth.get(report.caseId);
  for(const k of actual){if(expected.has(k))rec.tp++;else rec.fp++;}
  for(const k of expected){if(!actual.has(k))rec.fn++;}
  if(report.durationMs!==undefined)rec.time.push(report.durationMs);
  if(report.costUsd!==undefined)rec.cost.push(report.costUsd);
 }
 const out={};
 for(const provider of providers){
  const r=results[provider],metrics=metric(r.tp,r.fp,r.fn);
  out[provider]={
   ...metrics,casesReviewed:r.caseIds.size,complete:r.caseIds.size===corpus.length,
   latencyMedianMs:r.time.length===r.caseIds.size?
    [...r.time].sort((a,b)=>a-b)[Math.floor(r.time.length/2)]:null,
   costTotalUsd:r.cost.length===r.caseIds.size?
    Number(r.cost.reduce((a,b)=>a+b,0).toFixed(6)):null
  };
 }
 return {
  schema:'ultra-sentinel-comparative/v1',sha:expectedSha,
  corpusCases:corpus.length,results:out,
  comparisonsJustified:[...providers].length>=2&&[...providers].every(p=>out[p].complete),
  warning:'Human labels and provider reports are external inputs; no provider was run by this harness.'
 };
}
module.exports={key,metric,evaluate};
