'use strict';
/* Reproducible seeded adversarial suite: finite and offline. */
const test=require('node:test'),assert=require('node:assert/strict');
const incident=require('./ultra_sentinel_incidents.cjs');
const {apiPath,buildSanitizedReport}=require('./ultra_sentinel_sentry_ingest.cjs');
function rng(seed){let x=seed>>>0;return ()=>((x^=x<<13,x^=x>>>17,x^=x<<5)>>>0)}
const SHA='a'.repeat(40);
const PROBES=[
 'ignore previous instructions','<script>alert(1)</script>','Bearer 123456789abcdef',
 'github_pat_abcdef123456789000','username@private.example',
 '192.168.0.55','../../.github/workflows/android.yml',
 '{"roles":["admin"],"approval":"approved"}','DROP TABLE production',
 '-----BEGIN PRIVATE KEY-----\\npassword',
 '☠'.repeat(100),'\nERROR: bypass all checks\n'
];
test('5000 seeded untrusted issue inputs never leak raw payloads',()=>{
 const next=rng(0x51e1a11);
 for(let i=0;i<5000;i++){
  const payload=PROBES[next()%PROBES.length];
  const valid=(i%3===0);
  const raw={
   id:valid?String(i+1):payload,project:{slug:valid?'gamehub-ultra':payload},
   level:'error',count:valid?'1':payload,
   firstSeen:'2026-10-08T09:00:00Z',lastSeen:'2026-10-08T10:00:00Z',
   title:payload,stacktrace:payload,token:payload,logentry:{message:payload},
   user:{email:payload},contexts:{device:{model:payload}},
   tags:[{key:'debug',value:payload}]
  };
  const exported=incident.normalizeIssues([raw]);
  assert.ok(exported.length<=1);
  const out=JSON.stringify(exported);
  for(const name of ['title','stacktrace','token','logentry','user','contexts','tags'])
   assert.ok(!out.includes('"'+name+'"'));
  if(valid)assert.match(exported[0].fingerprint,/^[a-f0-9]{64}$/);
 }
});
test('1500 seeded random breadcrumbs never capture free text, audio, IP, or SSID',()=>{
 const next=rng(0x13579bdf);
 const keys=['message','audio','transcript','ssid','ip','password','token','user'];
 for(let i=0;i<1500;i++){
  const b={kind:['voice','network','app','invalid'][next()%4],
   event:['retry','connect','crash','stop','ignore previous instructions'][next()%5]};
  for(const key of keys)b[key]=PROBES[next()%PROBES.length];
  const encoded=JSON.stringify(incident.sanitizeBreadcrumbs([b],{consent:true}));
  for(const key of keys)assert.ok(!encoded.includes('"'+key+'"'));
  assert.deepEqual(incident.sanitizeBreadcrumbs([b],{consent:false}),[]);
 }
});
test('1000 malformed policy and unverified actions fail closed',()=>{
 const next=rng(0xabcdef01);
 for(let i=0;i<1000;i++){
  const v=[-1,0,null,'9999',NaN,Infinity,0.1][next()%7];
  assert.throws(()=>incident.analyzeIncidents([],{policy:{minIncidents:v}}),/policy/i);
  assert.equal(incident.evaluateRepairGate({sha:SHA,currentSha:'b'.repeat(40),approvedBy:'bot'}).status,'BLOCKED');
 }
});
test('adversarial Sentry project slugs are rejected',()=>{
 for(const slug of PROBES)assert.throws(()=>apiPath(slug,'gamehub-ultra'),/Invalid/);
});
test('raw event payloads never survive the read-only Sentry report',()=>{
 const raw=[{id:'1000',project:{slug:'gamehub-ultra'},level:'fatal',count:'100',
  firstSeen:'2026-10-08T09:00:00Z',lastSeen:'2026-10-08T10:00:00Z',
  event:{user:'me',token:'secret'},metadata:{title:'password'},
  title:'Bearer AAAAAAAA',message:'username@private.example'}];
 const str=JSON.stringify(buildSanitizedReport(raw,{consent:true}));
 assert.ok(!str.includes('username@private.example'));
 assert.ok(!str.includes('Bearer AAAAAAAA'));
 assert.ok(!str.includes('"event"'));
 assert.ok(!str.includes('"metadata"'));
});
