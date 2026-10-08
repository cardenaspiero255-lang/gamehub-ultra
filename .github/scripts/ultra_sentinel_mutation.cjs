'use strict';
/**
 * Ultra Sentinel Mutation Lab — bounded and sandboxed.
 * Mutates only copies of our reviewer implementation (NOT Android production).
 * Each mutant is applied independently in a disposable OS temp directory.
 * Survived mutation = a possible test gap, NOT proof that product is defective.
 */
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawnSync}=require('node:child_process');
const PREFIX='ultra_sentinel_';
const SAFE=/^ultra_sentinel_[a-z_]+\.cjs$/;
const CASES=Object.freeze([
 ['report','sha-equality-reversed',"if(String(analysis.sha).toLowerCase()!==sha.toLowerCase())","if(String(analysis.sha).toLowerCase()===sha.toLowerCase())"],
 ['report','wrong-commit-accepted',"if(expected&&report.sha!==expected.sha.toLowerCase())return null;","if(false&&report.sha!==expected.sha.toLowerCase())return null;"],
 ['report','wrong-pr-accepted',"if(expected&&report.pr!==expected.pr)return null;","if(false&&report.pr!==expected.pr)return null;"],
 ['report','partial-coverage-disregarded',"const partial=Boolean(analysis.coverage?.partial)||analysis.verdict==='INCOMPLETE'||analysis.omitted>0;","const partial=false;"],
 ['report','report-limit-disabled',"analysis.findings.slice(0,10)","analysis.findings.slice(0,1000)"],
 ['core','missing-sha-undetected',"const sha=/^[0-9a-f]{40}$/i.test(config.sha||'')?config.sha:'unknown';","const sha='unknown';"],
 ['core','coverage-no-files-disregarded',"if(!list.length){partial=true;","if(!list.length){partial=false;"],
 ['core','all-patches-truncated',"const MAX_FILES=300,MAX_PATCH=100000;","const MAX_FILES=300,MAX_PATCH=3;"],
 ['core','test-gap-rule-disabled',"if(androidTouched.length&&!testTouched.length)","if(false)"],
 ['orchestrator','unsafe-path-gate-disabled',"if(!isPath(filename))return","if(false)return"],
 ['orchestrator','wrong-sha-judge-disabled',"if(options.sha&&proposal.sha!==options.sha)","if(false&&proposal.sha!==options.sha)"],
 ['orchestrator','automerge-incorrectly-allowed',"autoMergeAllowed:false","autoMergeAllowed:true"],
 ['memory','unverified-memory-admitted',"if(entry.type!=='verified_fix')return false;","if(false)return false;"],
 ['memory','foreign-url-admitted',"validUrl(entry.evidenceUrl)","true"]
]);
const T={report:'ultra_sentinel_report.test.cjs',core:'ultra_sentinel_core.test.cjs',
 orchestrator:'ultra_sentinel_orchestrator.test.cjs',memory:'ultra_sentinel_memory.test.cjs'};
const modulePath=name=>'ultra_sentinel_'+name+'.cjs';
function plan(mutants=CASES){
 return mutants.map(([file,id,from,to])=>{
  if(!T[file]||!SAFE.test(modulePath(file))||typeof from!=='string'||!from||from===to)throw Error('Unsafe mutation descriptor');
  return {file,id,from,to,test:T[file]};
 });
}
function replaceExactlyOnce(text,from,to){
 const first=text.indexOf(from);if(first===-1)throw Error('Mutation target missing');
 if(text.indexOf(from,first+from.length)!==-1)throw Error('Mutation target ambiguous');
 return text.slice(0,first)+to+text.slice(first+from.length);
}
function runTest(testFile,cwd,timeoutMs){
 const response=spawnSync(process.execPath,['--test',path.join('.github','scripts',testFile)],{
  cwd,timeout:timeoutMs,encoding:'utf8',windowsHide:true,maxBuffer:1000000,
  env:{PATH:process.env.PATH||'',HOME:cwd,CI:'true',TMPDIR:cwd}
 });
 if(response.error&&response.error.code==='ETIMEDOUT')return {status:'timeout'};
 if(response.error)return {status:'environment_error',detail:String(response.error.message).slice(0,120)};
 return {status:response.status===0?'pass':'fail',signal:response.signal||null};
}
function evaluate({sourceDir=__dirname,maxMutants=16,timeoutMs=12000}={}){
 const scenarios=plan().slice(0,Math.max(1,Math.min(20,maxMutants)));
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'ultra-sentinel-mut-'));
 const dir=path.join(temp,'.github','scripts');fs.mkdirSync(dir,{recursive:true});
 try{
  for(const f of fs.readdirSync(sourceDir)){
   if(!/^ultra_sentinel_[\w.]+\.cjs$/.test(f))continue;
   fs.copyFileSync(path.join(sourceDir,f),path.join(dir,f));
  }
  const baselines={};
  for(const test of new Set(scenarios.map(x=>x.test))){
   baselines[test]=runTest(test,temp,timeoutMs);
   if(baselines[test].status!=='pass')throw Error('Baseline tests not green: '+test+' ('+baselines[test].status+')');
  }
  const results=[];
  for(const m of scenarios){
   const target=path.join(dir,modulePath(m.file));
   if(!fs.existsSync(target))throw Error('Missing module: '+m.file);
   const before=fs.readFileSync(target,'utf8');
   const mutated=replaceExactlyOnce(before,m.from,m.to);
   let state='inconclusive';
   try{
    fs.writeFileSync(target,mutated);
    const run=runTest(m.test,temp,timeoutMs);
    state=run.status==='fail'?'killed':run.status==='pass'?'survived':'inconclusive';
   }finally{fs.writeFileSync(target,before)}
   results.push({id:m.id,module:m.file,outcome:state,test:m.test});
  }
  const killed=results.filter(x=>x.outcome==='killed').length;
  const survived=results.filter(x=>x.outcome==='survived').length;
  const inconclusive=results.filter(x=>x.outcome==='inconclusive').length;
  return {engine:'Ultra Sentinel Mutation Lab',version:'1.0',domain:'Sentinel JavaScript core only',
   summary:{total:results.length,killed,survived,inconclusive,
     score:killed+survived?killed/(killed+survived):null},
   results,
   warning:'No Kotlin or Android production mutation performed. Do not treat this as coverage of the app.'};
 }finally{fs.rmSync(temp,{recursive:true,force:true})}
}
if(require.main===module){
 try{
  const result=evaluate({maxMutants:process.env.SENTINEL_MUTANT_LIMIT?Number(process.env.SENTINEL_MUTANT_LIMIT):16});
  console.log(JSON.stringify(result,null,2));
  if(process.env.SENTINEL_MUTATION_GATE==='1'&&(result.summary.survived>0||result.summary.inconclusive>0))
   process.exitCode=1;
 }catch(err){console.error('Ultra Sentinel Mutation Lab: '+String(err.message).slice(0,500));process.exitCode=2}
}
module.exports={CASES,plan,replaceExactlyOnce,evaluate};
