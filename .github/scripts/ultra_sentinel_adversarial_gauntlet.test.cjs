'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {cases,variants,evaluate,markdown}=require('./ultra_sentinel_adversarial_gauntlet.cjs');
const report=evaluate();
const {summary:s,metrics:m}=report;
test('Red-team gauntlet generates numerous distinct, fixed-seed mutations',()=>{
 const variantsSet=variants();
 assert.ok(cases.length>=160,'At least 160 independently labeled cases');
 assert.ok(variantsSet.length>=800,'At least 800 deterministic test scenarios');
 assert.equal(new Set(variantsSet.map(x=>x.id)).size,variantsSet.length);
 assert.ok(Object.keys(report.byFamily).length>=14);
 assert.ok(s.threats>=500);
 assert.ok(s.benign>=65);
 assert.equal(s.total,variantsSet.length);
});
test('Red-team gauntlet always reports misses separately from uncertain verdicts',()=>{
 assert.ok(s.explicit+s.incomplete+s.otherFinding+s.missed===s.threats);
 assert.ok(s.benignClean+s.benignFlagged===s.benign);
 assert.ok(s.unknownBlocked+s.unknownClean===s.unknown);
 assert.ok(m.explicitRecall<=m.failClosedRecall);
 assert.ok(m.failClosedRecall<=1);
 assert.ok(m.benignSpecificity<=1);
 assert.ok(report.issues.every(x=>x.id&&x.family&&x.actual));
});
test('Red-team gauntlet scanners never crash on hostile synthetic inputs',()=>{
 assert.equal(s.crashes,0,report.issues.filter(x=>x.actual==='CRASH').map(x=>x.id).join(', '));
});
test('Red-team gauntlet is safe read-only and emits actionable CI report',()=>{
 const md=markdown(report);
 assert.ok(md.includes('Silent misses'));
 assert.ok(md.includes('Blocked by uncertainty'));
 assert.ok(md.includes('Benign false alarms'));
 assert.ok(md.includes('explicit detection'));
});
console.log('[SENTINEL RED TEAM] '+JSON.stringify({
 scenarios:s.total,attacks:s.threats,detected:s.explicit,uncertain:s.incomplete+s.otherFinding,
 silentMisses:s.missed,benign:s.benign,falsePositives:s.benignFlagged,
 unknownCertifiedClean:s.unknownClean,crashes:s.crashes,
 byFamily:report.byFamily,firstUnresolved:report.issues.slice(0,35)
}));
if(process.env.GITHUB_STEP_SUMMARY){
 fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,markdown(report));
}
