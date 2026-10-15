'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {matchesReviewHead}=require('./ultra_sentinel_review_sha.cjs');
const sha='abc123abc123';
test('Grok escaped backticks and ordinary provider head markers are recognized',()=>{
 assert.equal(matchesReviewHead('Grok head '+String.fromCharCode(92,96)+sha+String.fromCharCode(92,96),sha),true);
 assert.equal(matchesReviewHead('Claude head '+sha,sha),true);
 assert.equal(matchesReviewHead('Groq head '+String.fromCharCode(96)+sha+String.fromCharCode(96),sha),true);
});
test('mismatched or prefix-colliding review SHA is rejected',()=>{
 assert.equal(matchesReviewHead('head def123abc123',sha),false);
 assert.equal(matchesReviewHead('head '+sha+'b',sha),false);
 assert.equal(matchesReviewHead('unrelated sha '+sha,sha),false);
 assert.equal(matchesReviewHead(null,sha),false);
});
