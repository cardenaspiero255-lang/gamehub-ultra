'use strict';
/* Read-only Git history correlation view; does NOT infer or assert bug causality. */
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const MARK='@@SENTINEL_COMMIT@@',REPO='cardenaspiero255-lang/gamehub-ultra';
const MAX=1500,MAX_INPUT=6000000;
function validPath(s){
 return typeof s==='string'&&s.length<400&&!/[\x00-\x1f]/.test(s)&&
 /^(?:app\/src\/|\.github\/(?:scripts\/|workflows\/))/.test(s)&&!s.includes('..');
}
function parseLog(log,limit=600){
 if(typeof log!=='string'||log.length>MAX_INPUT)throw Error('History too large');
 const commits=[],max=Math.max(1,Math.min(MAX,Number(limit)||600));let current=null;
 for(const line of log.split('\n')){
  if(line.startsWith(MARK)){
   const m=line.match(/^@@SENTINEL_COMMIT@@([a-f0-9]{40})\t(.*)$/);
   if(!m){current=null;continue}
   if(commits.length>=max)break;
   current={sha:m[1],title:m[2].slice(0,160),files:[]};commits.push(current);continue;
  }
  const file=line.trim();if(current&&validPath(file)&&current.files.length<140&&!current.files.includes(file))current.files.push(file);
 }
 return commits;
}
function graph(rows){
 const nodes=[],edges=[],recent=new Map(),stats=new Map(),limit=Array.isArray(rows)?rows.slice(0,MAX):[];
 for(const r of limit){
  if(!/^[a-f0-9]{40}$/.test(r.sha||''))continue;
  nodes.push({sha:r.sha,title:String(r.title||'').slice(0,160),
   url:'https://github.com/'+REPO+'/commit/'+r.sha});
  for(const name of (r.files||[]).filter(validPath).slice(0,140)){
   stats.set(name,(stats.get(name)||0)+1);
   if(recent.has(name)&&edges.length<3500){
    edges.push({newer:recent.get(name),older:r.sha,file:name,causal:false,type:'same_file_history'});
   }
   recent.set(name,r.sha);
  }
 }
 const hotspots=[...stats].map(([file,commits])=>({file,commits}))
   .sort((a,b)=>b.commits-a.commits||a.file.localeCompare(b.file)).slice(0,120);
 return {schema:'ultra-sentinel-frontier-map/v1',repo:REPO,mode:'correlation-not-causation',
  commitsAnalyzed:nodes.length,nodes,edges,hotspots,limitations:[
   'Shared files and timing do not establish a causal relationship.',
   'No root causes confirmed without a reproduced failing test or verified bisect.',
   'Bounded git history, excludes merge commits and unknown external incidents.'
  ]};
}
const TEMPLATE="<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n<title>Ultra Sentinel Frontier — Historia</title><style>\nbody{font:16px system-ui;background:#101014;color:#ededf4;margin:auto;padding:25px;max-width:1000px}\nh1{color:#ff6868}a{color:#ff8383}section{border:1px solid #555;padding:17px;border-radius:12px;margin:18px 0}\ninput{background:#202028;color:white;border:1px solid #666;border-radius:9px;padding:12px;width:min(94%,620px)}\nli{padding:6px 0}small{color:#c7c7d0}\n</style></head><body>\n<h1>Ultra Sentinel Frontier</h1><p>Mapa de historial: evidencia de archivos compartidos, NO causas comprobadas.</p>\n<input id=\"query\" type=\"search\" placeholder=\"Filtrar archivos y commits\">\n<section><strong id=\"summary\"></strong></section>\n<section><h2>Archivos más modificados</h2><ul id=\"hotspots\"></ul></section>\n<section><h2>Commits</h2><ul id=\"commits\"></ul></section>\n<section><h2>Limitaciones</h2><ul id=\"limits\"></ul></section>\n<script id=\"data\" type=\"application/json\">__DATA__</script>\n<script>\n'use strict';\nconst report=JSON.parse(document.getElementById('data').textContent);\nconst paths=document.getElementById('hotspots');\nconst commits=document.getElementById('commits');\ndocument.getElementById('summary').textContent=report.commitsAnalyzed+' commits, '+report.edges.length+' enlaces de archivo';\nfunction item(parent,value,url){\n const li=document.createElement('li');\n if(url){const anchor=document.createElement('a');anchor.href=url;anchor.rel='noopener noreferrer';anchor.textContent=value;li.appendChild(anchor)}\n else li.textContent=value;\n parent.appendChild(li);\n}\nfunction render(){\n const q=document.getElementById('query').value.toLowerCase();\n paths.replaceChildren();commits.replaceChildren();\n for(const p of report.hotspots.filter(x=>x.file.toLowerCase().includes(q)).slice(0,70))item(paths,p.commits+' cambios: '+p.file);\n for(const c of report.nodes.filter(x=>(x.sha+' '+x.title).toLowerCase().includes(q)).slice(0,120))\n  item(commits,c.sha.slice(0,10)+' '+c.title,c.url);\n}\nfor(const issue of report.limitations)item(document.getElementById('limits'),issue);\ndocument.getElementById('query').addEventListener('input',render);render();\n</script></body></html>";
function html(report){
 const data=JSON.stringify(report).replace(/</g,'\\u003c').replace(/>/g,'\\u003e')
  .replace(/&/g,'\\u0026').replace(/\u2028/g,'\\u2028').replace(/\u2029/g,'\\u2029');
 return TEMPLATE.replace('__DATA__',data);
}
function run(root=process.cwd(),outDir=process.cwd(),limit=600){
 const max=Math.max(1,Math.min(MAX,Number(limit)||600));
 const p=cp.spawnSync('git',['log','--no-merges','--name-only',
  '--format='+MARK+'%H%x09%s','--max-count='+max,'--','app/src/','.github/scripts/','.github/workflows/'],
  {cwd:root,encoding:'utf8',timeout:20000,maxBuffer:MAX_INPUT+50000});
 if(p.error||p.status!==0)throw Error('Git history unavailable');
 const result=graph(parseLog(p.stdout,max)),out=path.resolve(outDir);
 fs.mkdirSync(out,{recursive:true});
 fs.writeFileSync(path.join(out,'frontier-history.json'),JSON.stringify(result,null,2));
 fs.writeFileSync(path.join(out,'frontier-history.html'),html(result));
 return {commits:result.commitsAnalyzed,links:result.edges.length,files:result.hotspots.length};
}
if(require.main===module){
 try{console.log(JSON.stringify(run(process.cwd(),process.env.SENTINEL_MAP_OUT||process.cwd(),
   process.env.SENTINEL_MAP_LIMIT||600),null,2))}
 catch(e){console.error('Frontier: '+String(e.message).slice(0,180));process.exitCode=2}
}
module.exports={validPath,parseLog,graph,html,run,MARK};
