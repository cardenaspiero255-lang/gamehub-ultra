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
