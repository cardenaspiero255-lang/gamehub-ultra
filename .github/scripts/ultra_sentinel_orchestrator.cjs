'use strict';
/**
 * Ultra Sentinel Autonomous Candidate v1: Hunter -> Fixer -> Judge.
 * Patch suggestions are never committed, executed or merged by this engine.
 * To avoid unsafe guesses, only isolated GC and simple private Logcat statements
 * produce review-only draft deletions. Other risks require semantic human review.
 */
const VERSION='1.0.0',MAX_SOURCE=240000,MAX_LINES=20;
const SRC=/^app\/src\/main\/[a-zA-Z0-9_/.-]+\.(?:kt|java)$/;
function isPath(file){
 return typeof file==='string'&&file.length<230&&SRC.test(file)&&!file.includes('..')&&!file.includes('//');
}
// Select only source files with supported, isolated candidate families.
 // Deduplicating before download keeps privileged GitHub API reads bounded.
function selectRepairTargets(findings,max=8){
 const limit=Number.isSafeInteger(max)?Math.max(0,Math.min(max,8)):0;
 const selected=[],seen=new Set();
 for(const f of Array.isArray(findings)?findings:[]){
  if(selected.length>=limit)break;
  if(!f||!['FORCED_GC','POTENTIAL_PRIVATE_LOG'].includes(f.rule)||
     !isPath(f.path)||seen.has(f.path))continue;
  seen.add(f.path);selected.push(f.path);
 }
 return selected;
}
function candidate({filename,content,sha,findings}){
 if(!isPath(filename))return {status:'NO_SAFE_TEMPLATE',reason:'Ruta fuera de Kotlin/Java de producción.'};
 if(typeof content!=='string'||content.length>MAX_SOURCE||!content.endsWith('\n'))
  return {status:'NO_SAFE_TEMPLATE',reason:'Fuente demasiado grande, binaria, o sin salto final.'};
 if(!/^[a-f0-9]{40}$/i.test(sha||''))return {status:'NO_SAFE_TEMPLATE',reason:'Falta SHA completo del PR.'};
 const lines=content.split('\n');lines.pop();
 // Build at most one candidate per source to make the blast radius explicit.
 const allowed=new Set(['FORCED_GC','POTENTIAL_PRIVATE_LOG']);
 const matches=(Array.isArray(findings)?findings:[]).filter(f=>allowed.has(f.rule)&&f.path===filename&&Number.isSafeInteger(f.line));
 for(const f of matches){
  const idx=f.line-1;
  if(idx<0||idx>=lines.length)continue;
  const isolatedGc=/^([ \t]*)(?:System\.gc\(\)|Runtime\.getRuntime\(\)\.gc\(\));?[ \t]*$/;
  // Only an isolated call with a non-secret tag and a bare sensitive value is
  // safe enough to propose as a deletion. Never remove side-effecting arguments,
  // concatenate identifiers or embed literal personal data in candidate diffs.
  const isolatedPrivateLog=/^([ \t]*)Log\.(?:d|e|i|v|w)\(\s*(?:TAG|tag|LOG_TAG|"[A-Za-z0-9_-]{1,20}")\s*,\s*(?:transcript|password|authToken|accessToken)\s*\);?[ \t]*$/;
  const accepted=f.rule==='FORCED_GC'?isolatedGc.test(lines[idx]):
   f.rule==='POTENTIAL_PRIVATE_LOG'&&isolatedPrivateLog.test(lines[idx]);
  if(!accepted)continue;
  if(lines.length<3)continue;
  const start=Math.max(0,idx-2),end=Math.min(lines.length,idx+3);
  const before=lines.slice(start,end);
  const removed=idx-start;
  const patchLines=before.map((value,j)=>(j===removed?'-':' ')+value);
  const updated=before.filter((_,j)=>j!==removed);
  const newLines=updated.map((value)=>value);
  const unified=['diff --git a/'+filename+' b/'+filename,'--- a/'+filename,'+++ b/'+filename,
    '@@ -'+(start+1)+','+before.length+' +'+(start+1)+','+newLines.length+' @@',
    ...patchLines].join('\n')+'\n';
  return {status:'DRAFT_PATCH',format:'unified-diff',sha:sha.toLowerCase(),
   filename,rule:f.rule,linesChanged:1,patch:unified,
   expectation:f.rule==='FORCED_GC'?'Eliminar únicamente una llamada aislada a GC forzado y medir jank.':
    'Propuesta de eliminación de Logcat privado aislado; validar que no se pierde comportamiento necesario.',
   test:f.rule==='FORCED_GC'?'Compilar Kotlin; ejecutar pruebas de rendimiento y ArchitectureBoundaryGuardTest.':
    'Compilar Android; probar el comportamiento y verificar que Logcat no publica transcripciones ni tokens.',
   warning:'Borrador SIN aplicar. No se ha ejecutado git apply --check ni compilación.'};
 }
 return {status:'NO_SAFE_TEMPLATE',reason:'El hallazgo requiere contexto semántico; proponer pasos, no inventar un parche.'};
}
// Never trust a caller-supplied DRAFT_PATCH just because it carries an
// allowed rule and a SHA. Accept exactly one deletion in exactly one file;
// the human must still inspect the candidate in its original source context.
function validDeletionPatch(proposal){
 const body=proposal?.patch;
 if(typeof body!=='string'||body.length>10000||!body.endsWith('\n')||
    body.includes('\r')||!isPath(proposal.filename))return false;
 const lines=body.slice(0,-1).split('\n'),f=proposal.filename;
 if(lines.length<6||lines[0]!=='diff --git a/'+f+' b/'+f||
    lines[1]!=='--- a/'+f||lines[2]!=='+++ b/'+f)return false;
 const hunk=lines[3].match(/^@@ -(\d+),(\d+) \+(\d+),(\d+) @@$/);
 if(!hunk)return false;
 const oldStart=Number(hunk[1]),oldCount=Number(hunk[2]),
       newStart=Number(hunk[3]),newCount=Number(hunk[4]);
 if(oldStart<1||newStart!==oldStart||oldCount<2||oldCount>20||
    oldCount-newCount!==1)return false;
 const content=lines.slice(4);
 const removed=content.filter(x=>x.startsWith('-'));
 const added=content.filter(x=>x.startsWith('+'));
 if(removed.length!==1||added.length>0||content.length!==oldCount||
    !content.every(x=>x.startsWith('-')||x.startsWith(' ')))return false;
 const old=removed[0].slice(1);
 const safeGC=/^([ \t]*)(?:System\.gc\(\)|Runtime\.getRuntime\(\)\.gc\(\));?[ \t]*$/;
 const safeLog=/^([ \t]*)Log\.(?:d|e|i|v|w)\(\s*(?:TAG|tag|LOG_TAG|"[A-Za-z0-9_-]{1,20}")\s*,\s*(?:transcript|password|authToken|accessToken)\s*\);?[ \t]*$/;
 return proposal.linesChanged===1 &&
  (proposal.rule==='FORCED_GC'&&safeGC.test(old)||
   proposal.rule==='POTENTIAL_PRIVATE_LOG'&&safeLog.test(old));
}
function judge(proposal,options={}){
 const reasons=[];
 if(!proposal||proposal.status!=='DRAFT_PATCH')reasons.push('No existe un parche de alta confianza');
 else{
  if(!isPath(proposal.filename))reasons.push('Ruta fuera del alcance permitido');
  if(!['FORCED_GC','POTENTIAL_PRIVATE_LOG'].includes(proposal.rule))reasons.push('Regla no autorizada para parche exacto');
  if(!Number.isInteger(proposal.linesChanged)||proposal.linesChanged>MAX_LINES||proposal.linesChanged<1)reasons.push('Cambios exceden presupuesto');
  if(!validDeletionPatch(proposal))reasons.push('Contenido, ruta o hunk del parche no verificables');
  // Reconstruct the exact candidate from independently fetched source and
  // Hunter findings. Syntax-only validation allows patch-context confusion.
  const original=typeof options.source==='string'&&Array.isArray(options.findings)?
   candidate({filename:proposal.filename,content:options.source,
     sha:proposal.sha,findings:options.findings}):null;
  if(original?.status!=='DRAFT_PATCH'||original.patch!==proposal.patch||
     original.rule!==proposal.rule||original.filename!==proposal.filename||
     original.linesChanged!==proposal.linesChanged)
   reasons.push('Parche no coincide exactamente con fuente y hallazgo verificados');
  if(!/^[a-f0-9]{40}$/.test(proposal.sha||''))reasons.push('SHA inválido');
  if(!/^[a-f0-9]{40}$/.test(options.sha||''))reasons.push('Falta SHA esperado inmutable');
  if(options.sha&&proposal.sha!==options.sha)reasons.push('El PR cambió de SHA');
  // An independently attested PR head must agree with the trusted source
  // identity, even if a forged proposal and stale expected SHA agree.
  // The caller must obtain currentHeadSha from the trusted GitHub API,
  // never from PR content, comments, suggested patches, or a candidate job.
  if(Object.prototype.hasOwnProperty.call(options,'currentHeadSha')&&
     (!/^[a-f0-9]{40}$/i.test(options.currentHeadSha||'')||
      options.currentHeadSha.toLowerCase()!==String(options.sha||'').toLowerCase()||
      options.currentHeadSha.toLowerCase()!==String(proposal.sha||'').toLowerCase()))
   reasons.push('SHA del parche no coincide con HEAD comprobado independientemente');
  if(proposal.patch&&/(?:GITHUB_TOKEN|PRIVATE_KEY|SENTRY_AUTH_TOKEN|github\.event\.pull_request\.head)/.test(proposal.patch))
    reasons.push('Posible material sensible');
 }
 const checks=options.checks||{};
 const required=['sentinel-core-tests','android-build','unit-test-coverage','architecture-boundary'];
 const pending=required.filter(k=>checks[k]!=='success');
 // Importantly, an LLM majority or syntax-only scan never authorizes a merge.
 return {role:'JUDGE',status:reasons.length?'REJECT':pending.length?'REVIEW_PENDING':'ELIGIBLE_FOR_HUMAN_REVIEW',
  reasons,pendingChecks:pending,autoCommitAllowed:false,autoMergeAllowed:false,
  maxChangedLines:MAX_LINES};
}
function orchestrate({analysis,sources={},sha,checks={}}){
 const findings=Array.isArray(analysis?.findings)?analysis.findings:[];
 const sourceNames=Object.keys(sources).slice(0,60);
 const proposals=[];
 for(const filename of sourceNames){
  const p=candidate({filename,content:sources[filename],sha,findings});
  if(p.status==='DRAFT_PATCH')proposals.push({proposal:p,judgement:judge(p,{sha,checks,source:sources[filename],findings})});
  if(proposals.length>=3)break;
 }
 const risk=analysis?.coverage?.partial||analysis?.verdict==='INCOMPLETE';
 return {engine:'Ultra Sentinel 3-agent pipeline',version:VERSION,
  sha:String(sha||''),hunter:{findings:findings.length,partial:!!risk},
  fixer:{generated:proposals.length,supportedExactRule:['FORCED_GC','POTENTIAL_PRIVATE_LOG'],proposals},
  judge:{requiresHuman:true,requiredChecks:['sentinel-core-tests','android-build','unit-test-coverage','architecture-boundary'],
   verdict:risk?'INSUFFICIENT_EVIDENCE':proposals.length?'PATCHES_REQUIRE_VALIDATION':'NO_SAFE_AUTOFIX'},
  note:'This tool only drafts; it never executes untrusted PR code or changes branches.'};
}
module.exports={candidate,judge,orchestrate,selectRepairTargets,VERSION};
