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
function judge(proposal,options={}){
 const reasons=[];
 if(!proposal||proposal.status!=='DRAFT_PATCH')reasons.push('No existe un parche de alta confianza');
 else{
  if(!isPath(proposal.filename))reasons.push('Ruta fuera del alcance permitido');
  if(!['FORCED_GC','POTENTIAL_PRIVATE_LOG'].includes(proposal.rule))reasons.push('Regla no autorizada para parche exacto');
  if(!Number.isInteger(proposal.linesChanged)||proposal.linesChanged>MAX_LINES||proposal.linesChanged<1)reasons.push('Cambios exceden presupuesto');
  if(typeof proposal.patch!=='string'||proposal.patch.length>10000)reasons.push('Diff mal formado o demasiado extenso');
  if(!/^[a-f0-9]{40}$/.test(proposal.sha||''))reasons.push('SHA inválido');
  if(options.sha&&proposal.sha!==options.sha)reasons.push('El PR cambió de SHA');
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
  if(p.status==='DRAFT_PATCH')proposals.push({proposal:p,judgement:judge(p,{sha,checks})});
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
module.exports={candidate,judge,orchestrate,VERSION};
