'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const text=()=>fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-auto-review.yml'),'utf8');
test('trusted independent reviewer writes an artifact before attempting PR comment',()=>{
 const wf=text();
 assert.match(wf,/ultra-sentinel-core-report/);
 assert.match(wf,/writeFileSync/);
 assert.ok(wf.indexOf("writeFileSync")<wf.indexOf('issues.createComment'));
 assert.match(wf,/retention-days: 7/);
 assert.match(wf,/if-no-files-found: warn/);
});
test('403 comment restrictions degrade to explicit warning rather than suppress report',()=>{
 const wf=text();
 assert.match(wf,/catch\s*\(\s*error\s*\)/);
 assert.match(wf,/error\.status\s*===\s*403/);
 assert.match(wf,/Comment write denied/);
 assert.match(wf,/await core\.summary\.addHeading\("Ultra Sentinel Core"\)/);
});
test('independent reviewer remains pinned to trusted main and gates verified risk',()=>{
 const wf=text();
 assert.match(wf,/ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
 assert.match(wf,/if \(evidence\.coverage\.partial\) core\.setFailed/);
 assert.match(wf,/if \(evidence\.findings\.some\(f => f\.severity === "BLOCKER"\)\)/);
 assert.doesNotMatch(wf,/auto-merge\s*:\s*true/);
});
test('consensus gracefully handles 403 and does not certify missing reviewers',()=>{
 const wf=text();
 assert.match(wf,/Consensus comment unavailable/);
 assert.match(wf,/if \(fresh === 0\) core\.warning/);
 assert.match(wf,/Independent Sentinel Core failed/);
});

test('paid model providers require explicit opt-in, never consume exhausted quotas on every PR push',()=>{
 const wf=text();
 assert.match(wf,/run_external_providers:\s*\n\s*description:/);
 assert.match(wf,/type: boolean\s*\n\s*default: false/);
 const providerBlock=wf.split('\n  providers:\n')[1].split('\n  consensus:\n')[0];
 assert.match(providerBlock,/github\.event_name == 'workflow_dispatch'/);
 assert.match(providerBlock,/inputs\.run_external_providers == true/);
 assert.doesNotMatch(providerBlock,/github\.event\.pull_request\.head\.repo\.full_name == github\.repository\s*$/m);
});

test('only reporting jobs request GitHub pull request comment write permission',()=>{
 const wf=text();
 const top=wf.slice(0,wf.indexOf('\nconcurrency:'));
 assert.match(top,/permissions:\s*\n\s*contents: read\s*\n\s*pull-requests: read/);
 assert.doesNotMatch(top,/issues: write|pull-requests: write/);
 const blocks=Object.fromEntries(['core','providers','consensus'].map((n,i,ns)=>{
   const start=wf.indexOf('\n  '+n+':');
   const end=i+1<ns.length?wf.indexOf('\n  '+ns[i+1]+':',start):wf.length;
   return [n,wf.slice(start,end)];
 }));
 for(const section of ['core','consensus']){
   assert.match(blocks[section],/permissions:\s*\n\s*contents: read\s*\n\s*pull-requests: write/);
 }
 assert.doesNotMatch(blocks.providers,/pull-requests: write|issues: write/);
});
