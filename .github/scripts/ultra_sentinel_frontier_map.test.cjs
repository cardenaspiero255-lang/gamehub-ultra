'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const m=require('./ultra_sentinel_frontier_map.cjs');
const A='a'.repeat(40),B='b'.repeat(40),f='app/src/main/java/Voice.kt';
const git=xs=>xs.map(c=>m.MARK+c.sha+'\t'+c.title+'\n'+c.files.join('\n')+'\n').join('\n');
test('historical edges never assert causality',()=>{
 const g=m.graph(m.parseLog(git([{sha:A,title:'after',files:[f]},{sha:B,title:'before',files:[f]}])));
 assert.equal(g.edges.length,1);assert.equal(g.edges[0].causal,false);assert.equal(g.edges[0].older,B);
});
test('separate files produce no edges',()=>{
 const g=m.graph(m.parseLog(git([{sha:A,title:'one',files:[f]},{sha:B,title:'two',files:['app/src/test/X.kt']}])));
 assert.equal(g.edges.length,0);
});
test('no misleading root cause when history missing',()=>{
 const g=m.graph([]);assert.equal(g.commitsAnalyzed,0);
 assert.ok(g.limitations.some(x=>x.includes('No root causes')));
});
test('path traversal and README excluded',()=>{
 assert.equal(m.validPath('../../secret'),false);assert.equal(m.validPath('app/src/main/../x'),false);
 assert.equal(m.validPath('README.md'),false);assert.equal(m.validPath(f),true);
});
test('HTML cannot execute injected title',()=>{
 const page=m.html(m.graph(m.parseLog(git([{sha:A,title:'<img src=x onerror=alert(1)>',files:[f]}]))));
 assert.ok(!page.includes('<img src=x onerror=alert(1)>'));assert.match(page,/textContent/);
});
test('limit commits and dedupe touched files',()=>{
 const g=m.graph(m.parseLog(git([{sha:A,title:'one',files:[f,f]},{sha:B,title:'two',files:[f]}]),1));
 assert.equal(g.nodes.length,1);assert.equal(g.hotspots[0].commits,1);
});
test('refuse oversized history',()=>assert.throws(()=>m.parseLog('x'.repeat(6000001))));
test('data embedded without network fetching',()=>{
 const page=m.html(m.graph([]));assert.ok(page.includes('application/json'));assert.ok(!page.includes('<iframe'));
});
