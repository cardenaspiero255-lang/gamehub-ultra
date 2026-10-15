'use strict';
/**
 * Evidence graph: connects a *new finding* to related historical commits.
 * It reports correlated candidates, NEVER proved causes or fabricated probabilities.
 * Input history must originate from git/GitHub commits; no autonomous blame or revert.
 */
const SHA=/^[a-f0-9]{40}$/i,MAX_FINDINGS=5,MAX_HISTORY=80;
function words(input){
 return [...new Set((String(input||'').toLowerCase().match(/[a-z_][a-z_0-9]{3,}/g)||[])
  .filter(x=>!['android','class','null','with','from','else','return','function','import','main','this','that','error','data','test','user'].includes(x)))].slice(0,70);
}
function clean(s,max=200){return String(s||'').replace(/[\x00-\x1f<>]/g,' ').slice(0,max)}
function validFile(s){return typeof s==='string'&&s.length>4&&s.length<400&&!s.includes('..')&&!s.startsWith('/')}
function buildGraph({headSha,findings=[],history=[]}={}){
 if(!SHA.test(headSha||''))throw Error('Full SHA required for evidence graph');
 const candidates=[];
 const seen=new Set();
 const risks=Array.isArray(findings)?findings.slice(0,MAX_FINDINGS):[];
 const commits=Array.isArray(history)?history.slice(0,MAX_HISTORY):[];
 for(const f of risks){
  const file=f.path;if(!validFile(file))continue;
  const key=clean(f.rule,85)+':'+clean(file,250)+':'+(Number.isInteger(f.line)?f.line:0);
  const alertTerms=words([f.rule,f.reason,f.evidence].filter(Boolean).join(' '));
  const related=[];
  for(const c of commits){
   if(!c||!SHA.test(c.sha||'')||c.sha.toLowerCase()===headSha.toLowerCase())continue;
   const files=(Array.isArray(c.files)?c.files:[]).filter(validFile);
   if(!files.includes(file))continue;
   const terms=words(c.message||'');
   const overlap=terms.filter(w=>alertTerms.includes(w)).slice(0,6);
   // Shared file is weak temporal evidence, shared vocabulary increases relevance.
   // Numerical rank is just sorting, not calibrated probability of blame.
   const rank=1+overlap.length;
   const node={sha:c.sha.toLowerCase(),path:clean(file,250),rank,
    overlap,summary:clean((c.message||'').split('\n')[0],140),
    source:'git-history-file-overlap',causality:'NOT_ESTABLISHED',
    url:'https://github.com/cardenaspiero255-lang/gamehub-ultra/commit/'+c.sha.toLowerCase()};
   const nodeKey=key+':'+c.sha;
   if(!seen.has(nodeKey)){seen.add(nodeKey);related.push(node);}
  }
  related.sort((a,b)=>b.rank-a.rank||a.sha.localeCompare(b.sha));
  if(related.length)candidates.push({finding:key,historyCandidates:related.slice(0,5),
   caveat:'Same-file history and vocabulary are correlations; requires git blame/bisect and a reproducible failing test to prove cause.'});
 }
 return {type:'ultra-sentinel-causal-hypothesis-graph/v1',headSha:headSha.toLowerCase(),
  candidates:candidates.slice(0,MAX_FINDINGS),
  inspectedHistory:Math.min(commits.length,MAX_HISTORY),
  claimsProven:0,rootCauseConfirmed:false,
  next:'Reproduce failure, compare verified good/bad builds, run isolated bisect when explicitly requested.'};
}
module.exports={buildGraph,words,validFile};
