'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const E=require('./ultra_sentinel_evidence.cjs');
const SHA='a'.repeat(40),OTHER='b'.repeat(40),RUN=123456789;
const run=(overrides={})=>({
 id:RUN,head_sha:SHA,status:'completed',conclusion:'success',
 name:'Android build',event:'push',head_branch:'main',run_attempt:1,
 repository:{full_name:'cardenaspiero255-lang/gamehub-ultra'},
 html_url:'https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/'+RUN,
 ...overrides
});
const issue=(id,release)=>({
 id:String(id),project:{slug:'gamehub-ultra'},level:'error',count:'1',
 firstSeen:'2026-10-09T01:00:00Z',lastSeen:'2026-10-09T02:00:00Z',
 firstRelease:{version:release},title:'Sensitive Bearer secret user@private.test'
});
test('reject every incorrect workflow attestation and stale SHA',()=>{
 assert.equal(E.validateRun(run(),SHA)?.sha,SHA);
 assert.equal(E.validateRun(run({event:'workflow_dispatch'}),SHA)?.sha,SHA);
 for(const override of [
  {head_sha:OTHER},{conclusion:'failure'},{status:'in_progress'},{name:'Other workflow'},
  {repository:{full_name:'attacker/fork'}},{id:0},
  {event:'pull_request'}, {event:'push',head_branch:'feature/unsafe'},
  {event:'workflow_dispatch',head_branch:'feature/unsafe'},
  {event:'schedule',head_branch:'main'},
  {html_url:'https://evil.example/actions/runs/'+RUN}
 ])assert.equal(E.validateRun(run(override),SHA),null);
});
test('fixed-domain GitHub query never accepts path injection or partial SHA',()=>{
 assert.match(E.apiPath(SHA),/^\/repos\/cardenaspiero255-lang\/gamehub-ultra\/actions\/runs\?/);
 assert.throws(()=>E.apiPath('../../etc/passwd'));
 assert.throws(()=>E.apiPath('a'.repeat(12)));
});
test('release association demands exact 40-char commit, never substring',()=>{
 assert.equal(E.releaseSha(issue(1,SHA)),SHA);
 assert.equal(E.releaseSha(issue(1,'gamehub-ultra@'+SHA)),SHA);
 assert.equal(E.releaseSha(issue(1,'unknown-app@'+SHA)),null);
 assert.equal(E.releaseSha(issue(1,'gamehub-ultra@'+SHA+'-untrusted')),null);
 assert.equal(E.releaseSha(issue(1,'com.example.game@2.1+'+SHA)),SHA);
 assert.equal(E.releaseSha(issue(1,'legacy-'+SHA+'-unverified')),null);
 assert.equal(E.releaseSha(issue(1,OTHER)),OTHER);
 assert.equal(E.releaseSha(issue(1,'LATEST')),null);
});
test('correlation requires a independently attested CI match and redacts user data',()=>{
 const raw=[issue(12,'gamehub-ultra@'+SHA),issue(13,OTHER),issue(14,'not-a-release')];
 const trusted=E.attachVerifiedReleases(raw,[E.validateRun(run(),SHA)]);
 assert.equal(trusted.length,1);
 assert.equal(trusted[0].sha,SHA);
 assert.equal(trusted[0].verification,'verified-github-api-run');
 assert.ok(!JSON.stringify(trusted).includes('Bearer'));
 assert.ok(!JSON.stringify(trusted).includes('private.test'));
 assert.ok(!JSON.stringify(trusted).includes('"issueId"'));
});
test('requires a green Coverage run for strong release assessment',()=>{
 const android=E.validateRun(run(),SHA);
 const coverage=E.validateRun(run({name:'Unit Test Coverage',id:RUN+1,html_url:'https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/'+(RUN+1)}),SHA);
 assert.equal(E.assessRelease(SHA,[android],true).status,'INCOMPLETE');
 assert.equal(E.assessRelease(SHA,[android,coverage],true).status,'CI_VERIFIED');
 assert.equal(E.assessRelease(SHA,[android,coverage],false).status,'CONSENT_REQUIRED');
});
test('network client is read-only, bounded, fixed-host and fails closed on redirects',async()=>{
 const seen=[];
 const requester=(opts,callback)=>{
  seen.push(opts);
  const {EventEmitter}=require('node:events');
  const req=new EventEmitter();
  req.end=()=>{
   const res=new EventEmitter();res.statusCode=200;res.resume=()=>{};
   callback(res);
   process.nextTick(()=>{
    res.emit('data',Buffer.from(JSON.stringify({workflow_runs:[run()]})));
    res.emit('end');
   });
  };
  req.destroy=e=>req.emit('error',e);
  return req;
 };
 const out=await E.fetchVerifiedRuns({sha:SHA,token:'x'.repeat(30),request:requester});
 assert.equal(out.length,1);
 assert.equal(out[0].sha,SHA);
 assert.equal(seen[0].hostname,'api.github.com');
 assert.equal(seen[0].method,'GET');
 assert.ok(!seen[0].path.includes('xxxxxxxxx'));
});
