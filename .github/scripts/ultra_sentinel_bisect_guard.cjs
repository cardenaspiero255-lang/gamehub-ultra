'use strict';
/* Classify bisect endpoints without treating dependency/network outages as regressions.
 * Git bisect uses exit 1 for a confirmed test failure and 125 for uncertain runs.
 */
const fs=require('node:fs');
const MAX_BYTES=100000;
function classify(code,log){
 if(code===0)return 0;
 if(!Number.isSafeInteger(code)||code!==1||typeof log!=='string'||log.length>MAX_BYTES)
  return 125;
 const testFailed=/Execution failed for task ':app:testDebugUnitTest'\./.test(log)&&
  /> There were failing tests\./.test(log);
 const infrastructure=/Received status code 429|Could not (find|resolve)|daemon disappeared|OutOfMemoryError|No space left on device|ConnectException|SocketTimeoutException/i.test(log);
 return testFailed&&!infrastructure?1:125;
}
if(require.main===module){
 try{
  const exit=Number(process.argv[2]);
  const filename=process.argv[3];
  if(!filename||!fs.statSync(filename).isFile()||fs.statSync(filename).size>MAX_BYTES){
   process.exitCode=125;
  }else{
   process.exitCode=classify(exit,fs.readFileSync(filename,'utf8'));
  }
 }catch{process.exitCode=125}
}
module.exports={classify,MAX_BYTES};
