'use strict';
// This module is a real, unprivileged Node test; no fixture code is executed.
const test=require('node:test');
const assert=require('node:assert/strict');
const {extremeFixtures}=require('./ultra_sentinel_gauntlet_extreme.cjs');
test('51 static adversarial JSON fixtures are complete and distinct',()=>{
 const f=extremeFixtures();
 assert.deepEqual(Object.keys(f),['unsafe','uncertain','benign']);
 assert.deepEqual([f.unsafe.length,f.uncertain.length,f.benign.length],[41,5,5]);
 assert.equal(new Set(Object.values(f).flat().map(x=>x[0])).size,51);
 assert.ok(f.unsafe.some(x=>x[0]==='python-heredoc-eof'));
 assert.ok(f.unsafe.some(x=>x[0]==='node-fetch-eval'));
});
