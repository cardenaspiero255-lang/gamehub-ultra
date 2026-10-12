'use strict';
/* Ultra Sentinel intelligent baseline: pure, bounded, read-only SHADOW evaluator.
 * This is not an approval authority. CI inputs must be obtained independently
 * from the GitHub API at an immutable SHA by a trusted caller.
 */
const {createHash}=require('node:crypto');
const SHA=/^[0-9a-f]{40}$/i;
const MAX_FINDINGS=5000,MAX_TEXT=2048,MAX_ANNOTATIONS=50;
const SEVERITY=Object.freeze({BLOCKER:['BLOCK',5],CRITICAL:['BLOCK',5],HIGH:['BLOCK',4],MAJOR:['ADVISORY',3],WARNING:['ADVISORY',3],MEDIUM:['ADVISORY',3],LOW:['INFO',2],MINOR:['INFO',2],INFO:['INFO',1]});
function digest(value){return createHash('sha256').update(value).digest('hex');}
function validPath(p){
 if(typeof p!=='string'||p.length===0||p.length>240||p.startsWith('/')||p.includes('\\')||!(/^[A-Za-z0-9_.\/-]+$/.test(p)))return false;
 return p.split('/').every(segment=>segment!==''&&segment!=='.'&&segment!=='..');
}
function exactText(value){
 if(typeof value!=='string'||value.length>MAX_TEXT||value.includes('\0'))throw Error('INVALID_CONTEXT');
 return value.replace(/\s+/g,' ').trim();
}
function severityEntry(value){return typeof value==='string'&&Object.prototype.hasOwnProperty.call(SEVERITY,value)?SEVERITY[value]:null;}
function classifySeverity(value){return severityEntry(value)?.[0]||'UNKNOWN';}
function severityRank(value){return severityEntry(value)?.[1]||0;}
function strictFinding(f){
 if(!f||typeof f!=='object'||Array.isArray(f)||!validPath(f.path)||
   typeof f.rule!=='string'||!(/^[A-Za-z0-9_.:-]{1,100}$/.test(f.rule))||
   !Number.isSafeInteger(f.line)||f.line<1||f.line>10000000||
   !severityEntry(f.severity))throw Error('INVALID_FINDING');
 for(const key of ['astKind','symbol','context','cause']){
  if(f[key]!==undefined)exactText(f[key]);
 }
 if(f.reason!==undefined)exactText(f.reason);
 return f;
}
function identity(f,normalizePath=(x)=>x){
 strictFinding(f);
 const loc=exactText(f.symbol||'')?['symbol',exactText(f.symbol)]:['line',String(f.line)];
 return JSON.stringify([f.rule,normalizePath(f.path),exactText(f.astKind||''),...loc,exactText(f.context||''),exactText(f.cause||'')]);
}
function fingerprint(f,normalizePath){return digest(identity(f,normalizePath));}
function renameNormalizer(entries){
 if(!Array.isArray(entries)||entries.length>500)throw Error('INVALID_RENAMES');
 const map=new Map(),targets=new Set();
 for(const r of entries){
  if(!r||r.status!=='renamed'||!validPath(r.previous_filename)||!validPath(r.filename)||r.previous_filename===r.filename||
   map.has(r.previous_filename)||targets.has(r.filename))throw Error('INVALID_RENAMES');
  map.set(r.previous_filename,r.filename);targets.add(r.filename);
 }
 for(const entry of map){
  const a=entry[0],b=entry[1];
  if(map.has(b)||a===b)throw Error('AMBIGUOUS_RENAME_CHAIN');
 }
 return path=>map.get(path)||path;
}
function checkedPaths(paths,name){
 if(!Array.isArray(paths)||paths.length>2000||paths.some(p=>!validPath(p)))throw Error('INVALID_'+name);
 return new Set(paths);
}
function checkedFindings(items){
 if(!Array.isArray(items)||items.length>MAX_FINDINGS)throw Error('INVALID_FINDINGS');
 for(const f of items)strictFinding(f);
 return items;
}
function checkCI(input,headSha){
 if(!Array.isArray(input.requiredChecks)||input.requiredChecks.length<1||input.requiredChecks.length>30||
 !Array.isArray(input.checks)||input.checks.length>100)throw Error('CI_EVIDENCE_INCOMPLETE');
 const needed=new Set();
 for(const n of input.requiredChecks){
  if(typeof n!=='string'||!(/^[A-Za-z0-9 _./:-]{1,100}$/.test(n))||needed.has(n))throw Error('CI_EVIDENCE_INCOMPLETE');
  needed.add(n);
 }
 const found=new Map();
 for(const c of input.checks){
  if(!c||typeof c.name!=='string'||found.has(c.name)||!SHA.test(c.sha||'')||c.sha.toLowerCase()!==headSha||
     c.source!=='trusted'||!['queued','in_progress','completed','pending'].includes(c.status)||
     (c.conclusion!==null&&c.conclusion!==undefined&&typeof c.conclusion!=='string'))throw Error('CI_EVIDENCE_INCOMPLETE');
  found.set(c.name,c);
 }
 let failed=false;
 for(const name of needed){
  const check=found.get(name);
  if(!check)throw Error('CI_EVIDENCE_INCOMPLETE');
  if(check.status!=='completed'||!check.conclusion||check.conclusion==='skipped'||check.conclusion==='neutral')throw Error('CI_EVIDENCE_INCOMPLETE');
  if(check.conclusion!=='success')failed=true;
 }
 return failed?'BLOCKED':'PASS';
}
function familyKey(f,p){
 // If no causal proof is present, do not collapse unrelated variants merely
 // because the rule matches. Keep the full fingerprint as its own family.
 const cause=exactText(f.cause||'');
 return cause?digest(JSON.stringify([f.rule,p,cause])):fingerprint(f);
}
function sarifReport(findings){return {
 version:'2.1.0', $schema:'https://json.schemastore.org/sarif-2.1.0.json',
 runs:[{tool:{driver:{name:'Ultra Sentinel Intelligent Gate (shadow)',version:'0.1.0'}},
 results:findings.slice(0,MAX_ANNOTATIONS).map(x=>({
 ruleId:x.rule,level:x.severityClass==='BLOCK'?'error':x.severityClass==='ADVISORY'?'warning':'note',
 message:{text:'Verified metadata finding: '+x.rule+'; baseline status '+x.classification},
 locations:[{physicalLocation:{artifactLocation:{uri:x.path},region:{startLine:x.line}}}],
 partialFingerprints:{'ultraSentinel/v1':x.fingerprint}
 }))}]};}
function review(input){
 const fail=(code)=>({schema:'ultra-sentinel-intelligent-gate/v1',mode:'shadow',status:'INCOMPLETE',reason:code,autoMergeAllowed:false,
  findings:[],families:[],annotations:[],sarif:sarifReport([]),counts:{new:0,duplicates:0},reportTruncated:false,shadowComparison:'UNKNOWN'});
 try{
  if(!input||typeof input!=='object'||!SHA.test(input.headSha||'')||!SHA.test(input.baseSha||'')||!SHA.test(input.mergeBaseSha||''))throw Error('INVALID_SHA_PROVENANCE');
  if(input.headComplete!==true||input.baseComplete!==true)throw Error('ANALYSIS_INCOMPLETE');
  const headSha=input.headSha.toLowerCase();
  const pathOf=renameNormalizer(input.renames||[]),affected=checkedPaths(input.affectedPaths||[],'AFFECTED_PATHS');
  const changed=checkedPaths(input.changedPaths||[],'CHANGED_PATHS');
  const head=checkedFindings(input.headFindings),base=checkedFindings(input.baseFindings);
  // An AST fingerprint can identify several distinct locations. Preserve each
  // baseline occurrence rather than assigning the whole fingerprint one history.
  const baseMap=new Map();
  for(const f of base){
   const id=fingerprint(f,pathOf),location=pathOf(f.path)+':'+f.line;
   const records=baseMap.get(id)||new Map(),old=records.get(location);
   if(!old||severityRank(f.severity)>severityRank(old.severity))records.set(location,f);
   baseMap.set(id,records);
  }
  // Count distinct HEAD locations before attribution, so a newly added earlier
  // occurrence cannot steal the baseline match of a later existing occurrence.
  const headOccurrences=new Map();
  for(const f of head){
   const id=fingerprint(f),locations=headOccurrences.get(id)||new Set();
   locations.add(f.path+':'+f.line);headOccurrences.set(id,locations);
  }
  // Prefer an exact inherited location for the unsuffixed fingerprint.
  // Adding a new, earlier occurrence must not change an old finding's ID.
  const primaryLocations=new Map();
  for(const entry of headOccurrences){
   const id=entry[0],locations=entry[1],baseline=baseMap.get(id);
   const existing=[...locations].filter(loc=>baseline?.has(loc)).sort();
   const primary=existing.length?existing[0]:[...locations].sort()[0];
   primaryLocations.set(id,primary);
  }
  const findings=[],seen=new Map();let duplicates=0,critical=false;
  const ordered=head.slice().sort((a,b)=>a.path.localeCompare(b.path)||a.line-b.line||a.rule.localeCompare(b.rule)||severityRank(b.severity)-severityRank(a.severity)||a.severity.localeCompare(b.severity));
  for(const f of ordered){
   const rawKey=fingerprint(f);
   const instances=seen.get(rawKey)||new Set();
   if(instances.has(f.path+':'+f.line)){duplicates++;continue;}
   const key=primaryLocations.get(rawKey)===f.path+':'+f.line?rawKey:digest(rawKey+'|distinct-location|'+f.path+':'+f.line);
   instances.add(f.path+':'+f.line);seen.set(rawKey,instances);
   const baseline=baseMap.get(rawKey),location=f.path+':'+f.line;
   const exact=baseline?.get(location);
   // Permit semantic line movement only for unambiguous one-to-one identities.
   // Ambiguous unmatched occurrences are NEW, never silently inherited.
   const moved=baseline?.size===1&&headOccurrences.get(rawKey)?.size===1?baseline.values().next().value:null;
   const old=exact||moved;
   let classification='NEW';
   if(old){
    if(severityRank(f.severity)>severityRank(old.severity))classification='EXACERBATED';
    else if(affected.has(f.path)||(changed.has(f.path)&&old.path===f.path))classification='REACTIVATED';
    else if(old.path!==f.path)classification='MOVED';
    else classification='EXISTING';
   }
   const severityClass=classifySeverity(f.severity);
   if(severityClass==='BLOCK')critical=true;
   findings.push({rule:f.rule,severity:f.severity,severityClass,path:f.path,line:f.line,fingerprint:key,
    classification,familyId:familyKey(f,f.path)});
  }
  findings.sort((a,b)=>a.fingerprint.localeCompare(b.fingerprint));
  const map=new Map();
  for(const f of findings){
   const previous=map.get(f.familyId);
   map.set(f.familyId,{
    id:f.familyId,rule:f.rule,
    severityClass:[previous?.severityClass,f.severityClass].includes('BLOCK')?'BLOCK':([previous?.severityClass,f.severityClass].includes('ADVISORY')?'ADVISORY':'INFO'),
    variants:previous?previous.variants+1:1
   });
  }
  const families=[...map.values()].sort((a,b)=>a.id.localeCompare(b.id));
  const ciStatus=checkCI(input,headSha);
  const status=ciStatus==='BLOCKED'?'BLOCKED':critical?'REVIEW_REQUIRED':findings.some(x=>x.severityClass==='ADVISORY')?'ADVISORY':'PASS';
  // Give security-critical findings first priority in bounded GitHub-facing reports.
  // Preserve the complete, fingerprint-ordered finding inventory separately.
  const reportFindings=[...findings].sort((a,b)=>severityRank(b.severity)-severityRank(a.severity)||a.fingerprint.localeCompare(b.fingerprint));
  const annotations=reportFindings.slice(0,MAX_ANNOTATIONS).map(f=>({path:f.path,line:f.line,level:f.severityClass,summary:f.rule+' '+f.classification,fingerprint:f.fingerprint}));
  const legacy=input.legacyVerdict;
  const shadowComparison=typeof legacy==='string'&&['PASS','ADVISORY','REVIEW_REQUIRED','BLOCKED','INCOMPLETE'].includes(legacy)?(legacy===status?'AGREES':'DIVERGENCE'):'UNKNOWN';
  return {schema:'ultra-sentinel-intelligent-gate/v1',mode:'shadow',status,headSha,mergeBaseSha:input.mergeBaseSha.toLowerCase(),
   autoMergeAllowed:false,findings,families,annotations,sarif:sarifReport(reportFindings),reportTruncated:findings.length>MAX_ANNOTATIONS,
   counts:{new:findings.filter(f=>f.classification==='NEW').length,duplicates,returned:findings.length,reported:annotations.length},
   shadowComparison};
 }catch(error){
  const known=new Set(['INVALID_SHA_PROVENANCE','INVALID_CONTEXT','INVALID_FINDING','INVALID_FINDINGS','INVALID_RENAMES','AMBIGUOUS_RENAME_CHAIN','ANALYSIS_INCOMPLETE','CI_EVIDENCE_INCOMPLETE','INVALID_AFFECTED_PATHS','INVALID_CHANGED_PATHS']);
  return fail(known.has(error.message)?error.message:'UNVERIFIED_INPUT');
 }
}
module.exports={fingerprint,classifySeverity,review};
