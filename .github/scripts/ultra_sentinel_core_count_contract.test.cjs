'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const workflow=fs.readFileSync(path.resolve(__dirname,'../workflows/ultra-sentinel-core-check.yml'),'utf8');
test('Core gate enforces minimum 150,000 executed tests rather than counting file names',()=>{
 assert.match(workflow,/CORE_MIN_TESTS:\s*['"]?150000/);
 assert.match(workflow,/node --test \.github\/scripts\/ultra_sentinel_\*\.test\.cjs/);
 assert.match(workflow,/tee\s/);
 assert.match(workflow,/# tests/);
 assert.match(workflow,/process\.exit\(1\)/);
});
test('Core gate refuses silent skip and failures',()=>{
 assert.match(workflow,/# skipped/);
 assert.match(workflow,/# fail/);
 assert.match(workflow,/set -euo pipefail/);
});
