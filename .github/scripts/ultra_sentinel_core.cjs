'use strict';
/* Ultra Sentinel Core: an independent, domain-specific, evidence-based reviewer.
   No external AI services, no execution of PR code, no automatic merges. */
const VERSION='2.0.0';
const {buildRemediations,markdown:repairMarkdown}=require('./ultra_sentinel_remediation.cjs');
const {search:searchMemory}=require('./ultra_sentinel_memory.cjs');
const MAX_FILES=300,MAX_PATCH=100000;
const SEVERITY={BLOCKER:4,HIGH:3,MEDIUM:2,LOW:1};
function sanitize(s){
 return String(s??'').replace(/[\r\n<>|]/g,' ')
 .replace(/Bearer\s+\S+/gi,'Bearer [REDACTED]')
 .replace(/(?:sk-|ghp_|github_pat_)[\w-]{10,}/g,'[REDACTED]')
 .slice(0,185);
}
function parsePatch(text){
 if(typeof text!=='string')return {added:[],partial:true};
 let n=0,active=false;const added=[],scan=[];
 for(const row of text.slice(0,MAX_PATCH).split('\n')){
   const h=row.match(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/);
   if(h){n=+h[1];active=true;continue;}
   if(!active||row.startsWith('+++')||row.startsWith('---')||row.startsWith('\\'))continue;
   if(row.startsWith('+')){const item={line:n,text:row.slice(1)};added.push(item);scan.push({...item,added:true});n++;}
   else if(row.startsWith(' ')){scan.push({line:n,text:row.slice(1),added:false});n++;}
 }
 return {added,scan,partial:text.length>MAX_PATCH};
}
function production(path){
 return /^(app\/src\/main\/|supabase\/functions\/|\.github\/(?:workflows|scripts)\/|control-center\/)/.test(path)
 && !/(\.md$|\/test\/|\/tests\/|Test\.(kt|java)$|\.(?:test|spec|benchmark)\.(?:cjs|mjs|js|ts|jsx|tsx)$|(?:^|\/)ultra_sentinel_benchmark\.cjs$)/i.test(path);
}
function tags(path){
 const d={voice:/voice|wake|speech|tts/i,android:/app\/src\/main/i,ci:/\.github\/workflows/i,security:/auth|token|credential|secret/i,ai:/frontier|research|agent|intent/i,performance:/thermal|framepacing|performance/i,network:/network|dns|wifi|router/i};
 return Object.keys(d).filter(k=>d[k].test(path));
}
const DOMAIN_VALIDATION=Object.freeze({
 voice:['Voice onError síncrono no reinicia SpeechRecognizer recursivamente.','TTS puede interrumpirse; la escucha no genera ecos ni respuestas repetidas.','Permisos revocados y cierre de Service cancelan el micrófono.'],
 ai:['Fuentes verificadas sin alucinaciones en respuestas generales.','Memoria limitada por sesión y sin cruces de datos privados.','Cálculos matemáticos offline y sinónimos ambiguos tienen tests.'],
 network:['No prometer acceso al router sin autorización y API compatibles.','Probar jitter, pérdida de red, retries y degradación de confianza.','Separar medición de latencia real de predicciones.'],
 performance:['Umbrales configurables y muestras mínimas para predicción térmica.','Medir frame pacing y GC con Macrobenchmark.','Cancelar trabajos cuando desaparece el componente Android.'],
 security:['No registrar credenciales ni conversaciones privadas.','Validar límites de autenticación y privilegios por operación.','Tratar PR/diff, prompts y datos de web como entrada no confiable.'],
 ci:['Revisar secrets y checkout con pull_request_target.','Verificar cobertura, build, smoke y SHA actual antes de aprobar.','No publicar APK sin firma y artefacto verificado.'],
 android:['Probar lifecycle, SDK/API y cancelación de tareas.','Revisar callbacks reentrantes y errores de primer arranque.','Verificar comportamiento real en dispositivo compatible.']
});
function validationPlan(files){
 const domains=[...new Set((files||[]).flatMap(f=>tags(String(f?.filename||''))))].sort();
 return {domains,checks:domains.flatMap(domain=>(DOMAIN_VALIDATION[domain]||[]).map(check=>({domain,check}))).slice(0,24)};
}
function executableText(source,state={}){
 // Kotlin/Java lexer: only Kotlin has executable template interpolation.
 // Java literals and text blocks remain inert.
 // The stack survives contiguous diff lines, including multiline templates.
 const s=String(source||'');
 if(!Array.isArray(state.frames))state.frames=[{kind:'code'}];
 let out='',i=0;
 while(i<s.length){
  const f=state.frames[state.frames.length-1],ch=s[i];
  if(f.kind==='comment'){
   // Kotlin permits nested block comments; preserve depth across lines.
   if(f.kotlinNested&&s.startsWith('/*',i)){f.depth++;i+=2;continue;}
   if(s.startsWith('*/',i)){
    f.depth--;i+=2;
    if(f.depth===0)state.frames.pop();
    continue;
   }
   i++;continue;
  }
  if(f.kind==='raw'){
   if(s.startsWith('"""',i)){state.frames.pop();i+=3;continue;}
   if(state.kotlin&&s.startsWith('$'+'{',i)){state.frames.push({kind:'expr',depth:1});i+=2;out+=' ';continue;}
   i++;continue;
  }
  if(f.kind==='string'){
   if(ch==='\\'){i=Math.min(s.length,i+2);continue;}
   if(state.kotlin&&f.quote==='"'&&s.startsWith('$'+'{',i)){
    state.frames.push({kind:'expr',depth:1});i+=2;out+=' ';continue;
   }
   if(ch===f.quote)state.frames.pop();
   i++;continue;
  }
  // Executable context, including interpolation expressions.
  if(s.startsWith('//',i))break;
  if(s.startsWith('/*',i)){state.frames.push({kind:'comment',depth:1,kotlinNested:state.kotlin});i+=2;continue;}
  if(s.startsWith('"""',i)){state.frames.push({kind:'raw'});i+=3;continue;}
  if(ch==='"'||ch==="'"){state.frames.push({kind:'string',quote:ch});i++;continue;}
  if(f.kind==='expr'){
   if(ch==='{')f.depth++;
   else if(ch==='}'){
    f.depth--;
    if(f.depth===0){state.frames.pop();out+=' ';i++;continue;}
   }
  }
  out+=ch;i++;
 }
 return out;
}
function analyze(files,config={}){
 const list=Array.isArray(files)?files:[],names=list.map(f=>String(f?.filename||''));
 const alerts=[],warnings=[],dedupe=new Set();let scanned=0,partial=list.length>MAX_FILES;
 const sha=/^[0-9a-f]{40}$/i.test(config.sha||'')?config.sha:'unknown';
 const put=(rule,severity,confidence,path,line,text,why,check)=>{
   const key=rule+'#'+path+'#'+line;if(dedupe.has(key))return;dedupe.add(key);
   alerts.push({rule,severity,confidence,path:sanitize(path),line,
     evidence:sanitize(text),reason:why,verification:check,domains:tags(path),
     origin:'Ultra Sentinel Core',status:'NEEDS_VERIFICATION'});
 };
 for(const f of list.slice(0,MAX_FILES)){
   const path=String(f?.filename||'');
   if(!path||path.includes('..')||!production(path)||f.status==='removed')continue;
   const patch=parsePatch(f.patch);
   if(patch.partial||(!patch.added.length&&Number(f.changes)>0)){
     partial=true;warnings.push('Parche ausente o truncado: '+sanitize(path));continue;
   }
   if(!patch.added.length)continue;
   scanned++;
   const rows=patch.added;
   const all=rows.map(x=>x.text).join('\n');
   const android=path.startsWith('app/src/main/');
   const workflow=/^\.github\/workflows\/.*\.ya?ml$/i.test(path);
   // A diff beginning in the middle of a Kotlin file can start inside a
   // block comment or raw string. Never guess lexical mode from a hunk.
   // The optional fullSource must be provided from the same immutable HEAD;
   // cross-check every added/context line against it before trusting its lexer.
   const kotlin=android&&path.endsWith('.kt');
   const sourceProvided=f.fullSource!==undefined;
   const fullLines=kotlin&&typeof f.fullSource==='string'&&
     Buffer.byteLength(f.fullSource,'utf8')<=160000?
     f.fullSource.split(/\r?\n/):null;
   const sourceMatches=!!(fullLines&&patch.scan.every(x=>
     x.line>0&&x.line<=fullLines.length&&fullLines[x.line-1]===x.text));
   if(kotlin&&sourceProvided&&!sourceMatches){
     partial=true;
     warnings.push('Archivo Kotlin completo no coincide con el parche o supera el límite: '+sanitize(path));
   }
   const executableByLine=new Map(),uncertainAdded=new Set();
   if(kotlin&&sourceMatches){
     const wanted=new Set(rows.map(x=>x.line)),lexState={kotlin:true};
     for(let i=0;i<fullLines.length;i++){
       const code=executableText(fullLines[i],lexState);
       if(wanted.has(i+1))executableByLine.set(i+1,code);
     }
   }else{
     let lexState={},previousLine=null,hunkUnknown=false;
     for(const entry of patch.scan){
       if(previousLine===null||entry.line!==previousLine+1){
         lexState={};
         hunkUnknown=kotlin&&(entry.line!==1||sourceProvided);
         if(hunkUnknown){
           partial=true;
           warnings.push('Estado léxico Kotlin previo al hunk desconocido; revisar archivo completo: '+sanitize(path));
         }
       }
       lexState.kotlin=path.endsWith('.kt');
       const code=executableText(entry.text,lexState);
       if(entry.added){
         if(hunkUnknown)uncertainAdded.add(entry.line);
         else executableByLine.set(entry.line,code);
       }
       previousLine=entry.line;
     }
   }
   for(let i=0;i<rows.length;i++){
     const x=rows[i],t=x.text.trim();
     // Unknown Kotlin context may be comment or string text. Do not offer
     // an actionable finding or a repair from an ambiguous hunk.
     if(uncertainAdded.has(x.line))continue;
     const code=executableByLine.get(x.line)||'';
     if(!t||/^(\/\/|\/\*|\*|#)/.test(t))continue;
     if(android&&/\bGlobalScope\s*\.\s*(launch|async)\b/.test(code))
       put('UNSCOPED_COROUTINE','HIGH','high',path,x.line,t,'Una tarea puede superar el ciclo de vida Android.','Probar cancelación de Activity y Service.');
     if(android&&/(?:\brunBlocking\s*(?:\(|\{)|\bThread\.sleep\s*\()/.test(code))
       put('BLOCKING_ANDROID_CALL','HIGH','medium',path,x.line,t,'Posible bloqueo de UI/callback; falta verificar el hilo.','Reproducir ANR y medir el main looper.');
     if(android&&/\b(?:System\.gc|Runtime\.getRuntime\(\)\.gc)\s*\(/.test(code))
       put('FORCED_GC','MEDIUM','high',path,x.line,t,'GC forzado puede causar pausas de frames.','Comparar jank con Macrobenchmark.');
     if(android&&/\bwhile\s*\(\s*true\s*\)/.test(code)){
       const near=rows.slice(i,i+16).map(v=>v.text).join(' ');
       if(!/\b(isActive|ensureActive|break|return|delay|yield)\b/.test(near))
         put('NON_CANCELLABLE_LOOP','HIGH','medium',path,x.line,t,'Bucle sin salida visible en el diff.','Probar cancelación con timeout.');
     }
     if(android&&/\bstartListening\s*\(/.test(code)){
       const near=rows.slice(Math.max(0,i-12),i+1).map(v=>v.text).join(' ');
       if(/\bonError\s*\(/.test(near)&&!/\b(post|postDelayed|schedule|retryGate|Handler)\b/.test(near))
         put('SPEECH_REENTRANT_RETRY','HIGH','medium',path,x.line,t,'Posible reinicio recursivo de SpeechRecognizer.','Simular onError síncrono repetido sin desbordar pila.');
     }
     if(android&&/\b(Log|Timber)\.(d|i|e|v|w)\s*\(.*(transcript|password|authToken|accessToken)/i.test(code))
       put('POTENTIAL_PRIVATE_LOG','HIGH','medium',path,x.line,'[REDACTED LOG STATEMENT]','Posible fuga de datos privados en Logcat.','Probar que logs no registran información sensible.');
     if(workflow&&/pull_request_target\s*:/.test(all)&&/^\s*ref:\s*\$\{\{\s*github\.event\.pull_request\.head\./.test(t))
       put('PRIVILEGED_UNTRUSTED_CHECKOUT','BLOCKER','high',path,x.line,t,'Código de PR no confiable junto a workflow privilegiado.','Auditar scopes y fork PR sin secretos.');
     if(/(?:SENTRY_AUTH_TOKEN|OPENAI_API_KEY|GITHUB_TOKEN|SUPABASE_SERVICE_ROLE_KEY)\s*[:=]\s*["'][^"']{10,}["']/.test(t))
       put('POTENTIAL_HARDCODED_SECRET','BLOCKER','medium',path,x.line,'[REDACTED POTENTIAL SECRET]','Posible credencial literal en código.','Comprobar y revocar si es real; leer desde secret store.');
   }
   if(android){
     for(let i=0;i<rows.length;i++){
       const decl=rows[i].text.match(/\bfun\s+([A-Za-z_]\w*)\s*\(/);
       if(!decl||uncertainAdded.has(rows[i].line)||
         ['toString','equals','hashCode'].includes(decl[1]))continue;
       // A disconnected diff hunk is not evidence of self-recursion.
       const candidates=[];let expected=rows[i].line+1;
       for(const next of rows.slice(i+1,i+20)){
         if(next.line!==expected)break;
         if(uncertainAdded.has(next.line))break;
         candidates.push(next);expected++;
       }
       // A separate Kotlin function is a scope boundary, not a self-call.
       const nextMethod=candidates.findIndex(x=>/^\s*(?:(?:public|private|internal|override|suspend|protected|open|inline)\s+)*fun\s+[A-Za-z_]\w*\s*\(/.test(x.text));
       const close=nextMethod===-1?candidates:candidates.slice(0,nextMethod);
       const recur=close.find(x=>new RegExp('(?:^|[^\\w.])'+decl[1]+'\\s*\\(').test(x.text));
       if(recur&&!close.some(x=>/\b(remaining|maxDepth|visited|budget|tailrec)\b|\bdepth\s*(?:>=|<=|>|<)\s*\d+\b/i.test(x.text)))
         put('UNBOUNDED_RECURSION','HIGH','medium',path,recur.line,recur.text,'Posible autollamada sin límite visible.','Probar entradas cíclicas con profundidad acotada.');
     }
   }
 }
 const androidTouched=names.filter(n=>n.startsWith('app/src/main/')&&/\.(kt|java)$/.test(n));
 const testTouched=names.filter(n=>/app\/src\/(?:test|androidTest)\//.test(n));
 if(androidTouched.length&&!testTouched.length)
   put('REGRESSION_TEST_COVERAGE','MEDIUM','low',androidTouched[0],0,'No test modificado en el PR','El PR modifica Kotlin/Java de producción sin pruebas modificadas visibles.','Buscar pruebas existentes por comportamiento y añadir regresiones.');
 if(!list.length){partial=true;warnings.push('Sin archivos disponibles; no se realizó auditoría.');}
 if(partial)warnings.push('Cobertura parcial: la ausencia de alertas no significa que el PR esté limpio.');
 alerts.sort((a,b)=>SEVERITY[b.severity]-SEVERITY[a.severity]||a.path.localeCompare(b.path)||a.line-b.line);
 const plan=validationPlan(list);
 const repairs=buildRemediations({sha,findings:alerts.slice(0,40)});
 // Only validated historical fixes are marked verified. Curated playbooks are explicitly advisory.
 for(const suggestion of repairs.suggestions){
   const f=alerts.find(x=>x.rule===suggestion.rule&&x.path===suggestion.path&&x.line===suggestion.line);
   suggestion.relatedEvidence=searchMemory({rule:suggestion.rule,path:suggestion.path,reason:f?.reason,domain:f?.domains?.[0]},[],2);
 }
 return {engine:'Ultra Sentinel Core',version:VERSION,sha,mode:'independent-rule-and-structure-reasoner',
  coverage:{returned:list.length,analyzed:scanned,partial},validationPlan:plan,remediations:repairs,findings:alerts.slice(0,40),
  omitted:Math.max(0,alerts.length-40),warnings:warnings.slice(0,25),
  verdict:partial?'INCOMPLETE':alerts.some(x=>SEVERITY[x.severity]>=3)?'REVIEW_REQUIRED':'NO_CRITICAL_PATTERN',
  note:'Heurísticas verificables: no es un modelo fundacional entrenado, ni sustituye compilación o revisión humana.'};
}
function markdown(result){
 const lines=['<!-- ultra-sentinel-core -->','## Ultra Sentinel Core · Analista propio','',
   '**Commit:** '+result.sha+' · **Motor:** '+result.version,
   '**Resultado:** '+result.verdict,
   '**Cobertura:** '+result.coverage.analyzed+' archivos analizados de '+result.coverage.returned+'.',
   '','**Independiente:** esta revisión se ejecuta incluso sin Claude, Grok, DeepSeek o Groq.',
   '**Limitación:** inferencias técnicas sobre líneas cambiadas; no es un modelo neuronal entrenado.'];
 for(const warn of result.warnings)lines.push('- Aviso: '+sanitize(warn));
 if(!result.findings.length)lines.push('','Ningún patrón de alerta detectado. **Esto NO certifica que el CAR esté correcto.**');
 for(const f of result.findings){
   lines.push('','### ['+f.severity+'] '+f.rule+' — '+f.path+':'+f.line,
     '- Evidencia (redactada): '+f.evidence,
     '- Hipótesis: '+f.reason,
     '- Prueba necesaria: '+f.verification,
     '- Confianza heurística: '+f.confidence+'.');
 }
 if(result.validationPlan.checks.length){
   lines.push('','### Tests adversariales propuestos por Ultra Sentinel');
   for(const item of result.validationPlan.checks)lines.push('- **'+item.domain+'**: '+item.check);
 }
 if(result.remediations?.suggestions?.length){
   lines.push('',repairMarkdown({...result.remediations,suggestions:result.remediations.suggestions.slice(0,6)}));
 }
 lines.push('','Sin auto-merge ni auto-aprobación. Exigir pruebas y revisiones completas.');
 return lines.join('\n').slice(0,58000);
}
module.exports={analyze,parsePatch,markdown,VERSION};
