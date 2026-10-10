'use strict';
// Inert security fixtures are parsed as JSON data, never evaluated or executed.
const fs=require('node:fs');
const path=require('node:path');
const FILE=path.join(__dirname,'..','sentinel-fixtures','ultra_sentinel_gauntlet_extreme.json');
function extremeFixtures(){
 const st=fs.statSync(FILE);
 if(!st.isFile()||st.size<100||st.size>160000)throw Error('Invalid fixture file');
 const value=JSON.parse(fs.readFileSync(FILE,'utf8'));
 const sizes={unsafe:41,uncertain:5,benign:5};
 if(!value||typeof value!=='object'||Array.isArray(value)||
    Object.keys(value).sort().join(',')!=='benign,uncertain,unsafe')
  throw Error('Unexpected fixture groups');
 const ids=new Set();
 for(const [key,size] of Object.entries(sizes)){
  if(!Array.isArray(value[key])||value[key].length!==size)throw Error('Fixture count mismatch');
  for(const tuple of value[key]){
   if(!Array.isArray(tuple)||tuple.length!==3||
      tuple.some(x=>typeof x!=='string'||x.length<1||x.length>5000)||
      !/^[a-z0-9-]+$/.test(tuple[0])||ids.has(tuple[0]))
    throw Error('Invalid inert fixture');
   ids.add(tuple[0]);
  }
 }
 return value;
}
module.exports={extremeFixtures};
