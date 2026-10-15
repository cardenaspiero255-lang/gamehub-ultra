'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {manifest}=require('./ultra_sentinel_replay.cjs');
const sha='a'.repeat(40),runId=123;
const valid=()=>({repo:'cardenaspiero255-lang/gamehub-ultra',sha,runId,
 workflow:'Android build',conclusion:'failure',
 url:'https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/123',
 jobs:[{name:'gradle build',conclusion:'failure',failedSteps:['Assemble Debug']}]});
test('build a replay recipe from verified GitHub CI metadata',()=>{
 const r=manifest(valid());assert.equal(r.runId,123);
 assert.match(r.recipe.suggestedCommand,/gradlew/);
 assert.match(r.limits,/Not a replay/);
});
test('reject invalid SHA or repo',()=>{
 assert.throws(()=>manifest({...valid(),sha:'abc'}));
 assert.throws(()=>manifest({...valid(),repo:'outside/repo'}));
});
test('reject URL substitution to malicious host',()=>{
 assert.throws(()=>manifest({...valid(),url:'https://evil.example/actions/runs/123'}));
});
test('reject mismatch run ID in URL',()=>{
 assert.throws(()=>manifest({...valid(),url:valid().url.replace('/123','/999')}));
});
test('only error states create a diagnosis',()=>{
 assert.throws(()=>manifest({...valid(),conclusion:'success'}));
});
test('unrecognized workflow is not guessed',()=>{
 const m=manifest({...valid(),workflow:'Other new pipeline'});
 assert.equal(m.recipe.suggestedCommand,null);
});
test('exclude untrusted log content and environment secrets',()=>{
 const sample={...valid(),jobs:[{name:'build <script>',conclusion:'failure',failedSteps:['KEY=secret\nbad']}]};
 const r=JSON.stringify(manifest(sample));
 assert.ok(!r.includes('<script>'));assert.ok(!r.includes('\nbad'));
 assert.ok(!r.includes('GITHUB_TOKEN'));
});
test('job details capped',()=>{
 const x=valid();x.jobs=Array(200).fill({name:'build',conclusion:'failure',failedSteps:[]});
 assert.equal(manifest(x).jobs.length,30);
});
