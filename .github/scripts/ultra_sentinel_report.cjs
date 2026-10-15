'use strict';
/* Portable Ultra Sentinel report (v1): public PR metadata only, no secrets, no credential-bearing code.
 * Store at end of trusted bot GitHub issue comments so authorized agents can pull findings directly.
 */
const SCHEMA='ultra-sentinel-report/v1';
const OPEN='<!-- ULTRA_SENTINEL_REPORT_V1';
const CLOSE='ULTRA_SENTINEL_REPORT_V1_END -->';
function clean(value,max=280){
 return String(value??'').replace(/[\x00-\x1f<>]/g,' ').replace(/-->/g,'- - >')
  .replace(/Bearer\s+[A-Za-z0-9._-]+/gi,'Bearer [REDACTED]')
  .replace(/(?:sk-|ghp_|github_pat_|glpat-|xoxb-)[A-Za-z0-9_-]{8,}/g,'[REDACTED]')
  .replace(/(TOKEN|PASSWORD|SECRET|API_KEY)\s*[:=]\s*\S+/gi,'$1=[REDACTED]')
  .slice(0,max);
}
function buildReport(args){
 const pr=Number(args?.pr);
 const sha=String(args?.sha||'');
 const analysis=args?.analysis;
 if(!Number.isSafeInteger(pr)||pr<1||!(/^[0-9a-f]{40}$/i.test(sha))||!analysis||!Array.isArray(analysis.findings))
   throw new Error('Invalid PR, SHA or audit payload');
 if(String(analysis.sha).toLowerCase()!==sha.toLowerCase())
   throw new Error('Analysis belongs to a different commit');
 const risks=analysis.findings.slice(0,10).map((f,i)=>{
   const s=analysis.remediations?.suggestions?.find(x=>x.rule===f.rule&&x.path===f.path&&x.line===f.line)
    ;
   return {
    rule:clean(f.rule,85),severity:clean(f.severity,20),
    location:{path:clean(f.path,240),line:Number.isSafeInteger(f.line)?f.line:0},
    confidence:clean(f.confidence,12),
    why:clean(f.reason,360),check:clean(f.verification,340),
    repair:s?{
     title:clean(s.title,150),
     steps:Array.isArray(s.steps)?s.steps.slice(0,5).map(x=>clean(x,260)):[],
     test:clean(s.test,300),
     caution:clean(s.caution,300),
     humanFeedback:s.humanFeedback?{
       decision:clean(s.humanFeedback.decision,10),
       reason:clean(s.humanFeedback.reason,270),
       status:'HUMAN_FEEDBACK_NOT_A_TRAINED_MODEL'
     }:null,
     handoff:clean(s.handoff,1200),
     memory:Array.isArray(s.relatedEvidence)?s.relatedEvidence.slice(0,2).map(m=>({
       id:clean(m.id,90),type:clean(m.type,25),
       verification:clean(m.verification,30),advice:clean(m.advice,250),
       source:m.evidenceUrl?clean(m.evidenceUrl,250):null
     })):[]
    }:null
   };
 });
 const partial=Boolean(analysis.coverage?.partial)||analysis.verdict==='INCOMPLETE'||analysis.omitted>0;
 return {
  schema:SCHEMA,repo:'cardenaspiero255-lang/gamehub-ultra',pr,sha:sha.toLowerCase(),
  engine:clean(analysis.engine,70),engineVersion:clean(analysis.version,20),
  status:partial?'incomplete':risks.some(x=>['HIGH','BLOCKER'].includes(x.severity))?'review_required':'no_critical_signal',
  partial,coverage:{
   returned:Number(analysis.coverage?.returned)||0,analyzed:Number(analysis.coverage?.analyzed)||0
  },
  count:Number(analysis.findings.length)||0,provided:risks.length,reportTruncated:analysis.findings.length>risks.length,findings:risks,
  causalCandidates:(args?.causalGraph?.candidates||[]).slice(0,3).map(group=>({
   finding:clean(group.finding,300),
   commits:(group.historyCandidates||[]).slice(0,3).map(c=>({
    sha:clean(c.sha,40),summary:clean(c.summary,140),rank:Number(c.rank)||1,
    evidence:'same_file_history',causality:'NOT_ESTABLISHED',url:clean(c.url,220)
   }))
  })),
  limitations:[
   'Diff-only heuristics: no proof of correctness or confirmed bug.',
   'Remediation templates require source-context inspection, RED/GREEN tests and review.',
   'Never merge or approve only from this report.'
  ]
 };
}
function serialize(report){
 if(!report||report.schema!==SCHEMA||!(/^[0-9a-f]{40}$/.test(report.sha)))throw new Error('Bad report');
 const json=JSON.stringify(report).replace(/</g,'\\u003c').replace(/>/g,'\\u003e');
 if(json.length>26000)throw new Error('Report exceeds comment limit');
 return OPEN+'\n'+json+'\n'+CLOSE;
}
function parseComment(body,expected){
 if(typeof body!=='string'||body.length>70000)throw new Error('Invalid GitHub comment');
 const start=body.indexOf(OPEN+'\n');
 if(start<0)return null;
 const from=start+OPEN.length+1;
 const end=body.indexOf('\n'+CLOSE,from);
 if(end<0||end-from>26000)throw new Error('Malformed or truncated Sentinel JSON');
 const report=JSON.parse(body.slice(from,end));
 if(report.schema!==SCHEMA||report.repo!=='cardenaspiero255-lang/gamehub-ultra'||!(/^[0-9a-f]{40}$/.test(report.sha))||!Number.isSafeInteger(report.pr)||report.pr<1)
   throw new Error('Report does not match schema');
 if(expected&&report.sha!==expected.sha.toLowerCase())return null;
 if(expected&&report.pr!==expected.pr)return null;
 return report;
}
module.exports={SCHEMA,buildReport,serialize,parseComment};
