'use strict';
/**
 * Evidence-bound Family Hunter clustering: cross-file hypotheses, NOT a claim
 * that a root cause was proved or all semantic variants were discovered.
 * Pure function; no GitHub writes, no token access, no automatic decisions.
 */
const {createHash}=require('node:crypto');
const RANK=Object.freeze({BLOCKER:4,HIGH:3,MEDIUM:2,LOW:1});
const MAX_FINDINGS=40;
function normalizeEvidence(s){
 return s.normalize('NFKC').trim().toLowerCase().replace(/\s+/g,' ')
   // Variable numbers and quoted strings are not allowed to create new
   // buckets on their own, but keep API names and operators intact.
   .replace(/\b\d+\b/g,'#')
   .replace(/"[^"\n]{0,100}"/g,'\"#\"')
   .slice(0,700);
}
function fingerprint(f){
 return createHash('sha256').update(f.rule+'\0'+normalizeEvidence(f.evidence))
   .digest('hex').slice(0,24);
}
function valid(a){
 if(!a||typeof a!=='object'||Array.isArray(a)||a.engine!=='Ultra Sentinel Core'||
    typeof a.sha!=='string'||!/^[a-f0-9]{40}$/i.test(a.sha)||
    !a.coverage||typeof a.coverage!=='object'||
    !Number.isSafeInteger(a.coverage.returned)||a.coverage.returned<0||
    !Number.isSafeInteger(a.coverage.analyzed)||a.coverage.analyzed<0||
    a.coverage.analyzed>a.coverage.returned||
    typeof a.coverage.partial!=='boolean'||
    !['INCOMPLETE','REVIEW_REQUIRED','NO_CRITICAL_PATTERN'].includes(a.verdict)||
    (a.verdict==='INCOMPLETE')!==a.coverage.partial||
    (a.coverage.returned===0&&!a.coverage.partial)||
    !Number.isSafeInteger(a.omitted)||a.omitted<0||
    !Array.isArray(a.findings)||a.findings.length>MAX_FINDINGS||
    (a.omitted>0&&a.findings.length!==MAX_FINDINGS))return false;
 const unique=new Set();
 for(const f of a.findings){
  if(!f||typeof f!=='object'||f.origin!=='Ultra Sentinel Core'||
     !Object.hasOwn(RANK,f.severity)||!['high','medium','low'].includes(f.confidence)||
     typeof f.rule!=='string'||!/^[A-Z_0-9]{3,80}$/.test(f.rule)||
     typeof f.evidence!=='string'||!f.evidence.trim()||f.evidence.length>4000||
     typeof f.path!=='string'||!f.path||f.path.length>350||
     f.path.startsWith('/')||f.path.split('/').includes('..')||
     !Number.isSafeInteger(f.line)||f.line<0)return false;
  const id=f.rule+'\0'+f.path+'\0'+f.line;
  if(unique.has(id))return false;
  unique.add(id);
 }
 return true;
}
function analyzeFamilies(analysis){
 if(!valid(analysis))throw Error('INVALID_CORE_ANALYSIS');
 const groups=new Map();
 for(const f of analysis.findings){
  const id=fingerprint(f),key=f.rule+':'+id;
  if(!groups.has(key))groups.set(key,{id,rule:f.rule,severity:f.severity,
   occurrences:0,distinctFiles:0,locations:[],status:'HYPOTHESIS_REQUIRES_TESTS'});
  const family=groups.get(key);
  family.occurrences++;
  if(RANK[f.severity]>RANK[family.severity])family.severity=f.severity;
  family.locations.push({path:f.path,line:f.line,severity:f.severity});
 }
 for(const f of groups.values()){
  f.distinctFiles=new Set(f.locations.map(x=>x.path)).size;
  f.locations.sort((a,b)=>a.path.localeCompare(b.path)||a.line-b.line);
 }
 const families=[...groups.values()].sort((a,b)=>
  RANK[b.severity]-RANK[a.severity]||b.occurrences-a.occurrences||
  a.rule.localeCompare(b.rule)||a.id.localeCompare(b.id));
 return {sha:analysis.sha,engine:'Ultra Sentinel Family Hunter',version:'1.0.0',
  status:analysis.coverage.partial||analysis.omitted>0?'INCOMPLETE':analysis.verdict,
  coverage:{...analysis.coverage},omitted:analysis.omitted,
  families,autoCommitAllowed:false,autoMergeAllowed:false,
  limitation:'Heuristic clustering only; validate root cause and variants with tests'};
}
module.exports={analyzeFamilies,fingerprint,normalizeEvidence};
