'use strict';
/**
 * Read-only differential attestation. NEVER converts changed workflows into
 * generic trusted CI, nor authorizes GitHub approval/merge. Verify complete
 * source against narrow, trusted-main anchored transformations.
 * Caller MUST fetch trusted source from an immutable reviewed main checkout;
 * arbitrary parameters are not themselves an authenticated GitHub API.
 */
const HEX=/^[a-f0-9]{40}$/i;
const REPO='cardenaspiero255-lang/gamehub-ultra';
const FAIL=reason=>Object.freeze({status:'INCOMPLETE',reason,
  autoApproveAllowed:false,autoMergeAllowed:false});
function replaceExact(text,needle,replacement,count=1){
 if(typeof text!=='string'||text.length>160000||!needle||typeof needle!=='string')throw Error('INVALID_SOURCE');
 const n=text.split(needle).length-1;
 if(n!==count)throw Error('EXPECTED_ANCHOR_MISMATCH');
 return text.split(needle).join(replacement);
}
function buildExpectedAndroid(base){
 let src=replaceExact(base,[
  '      max-parallel: 4',
  '      matrix:',
  '        # Four logical performance checks are packed into two physical emulator',
  '        # runners so Android boot/setup is reused instead of repeated four times.',
  '        shard: [release-apk, ui, performance-a, performance-b]'
 ].join('\n'),[
  '      max-parallel: 6',
  '      matrix:',
  '        # Four real performance checks run on independent API 35 emulators.',
  '        # Keeps baseline + cold-start + library + settings with no serial waves.',
  '        shard: [release-apk, ui, baseline, macro-startup, macro-library, macro-settings]'
 ].join('\n'));
 const oldSdk="          CMDLINE_DIR=\"$(find \"$SDK_ROOT/cmdline-tools\" -mindepth 1 -maxdepth 1 -type d ! -name latest -printf '%f\\n' | sort -V | tail -n1)\"\n          test -n \"$CMDLINE_DIR\"\n          rm -rf \"$SDK_ROOT/cmdline-tools/latest\"\n          ln -s \"$SDK_ROOT/cmdline-tools/$CMDLINE_DIR\" \"$SDK_ROOT/cmdline-tools/latest\"\n          echo \"$SDK_ROOT/cmdline-tools/$CMDLINE_DIR/bin\" >> \"$GITHUB_PATH\"";
 const newSdk="          # Never delete the runner's working cmdline-tools/latest installation.\n          SDK_BIN=\"$SDK_ROOT/cmdline-tools/latest/bin\"\n          if [ ! -x \"$SDK_BIN/sdkmanager\" ]; then\n            CMDLINE_DIR=\"$(find \"$SDK_ROOT/cmdline-tools\" -mindepth 1 -maxdepth 1 -type d ! -name latest -printf '%f\\n' | sort -V | tail -n1)\"\n            if [ -z \"$CMDLINE_DIR\" ]; then\n              echo \"::error::No installed Android command-line tools are available.\"\n              exit 1\n            fi\n            SDK_BIN=\"$SDK_ROOT/cmdline-tools/$CMDLINE_DIR/bin\"\n          fi\n          if [ ! -x \"$SDK_BIN/sdkmanager\" ]; then\n            echo \"::error::Android command-line tools are missing sdkmanager.\"\n            exit 1\n          fi\n          printf '%s\\n' \"$SDK_BIN\" >> \"$GITHUB_PATH\"";
 src=replaceExact(src,oldSdk,newSdk);
 src=replaceExact(src,'performance-*)','baseline|macro-*)',2);
 src=replaceExact(src,[
  '              case "$VALIDATION_SHARD" in',
  '                performance-a)',
  '                  LOGICAL_SHARDS=(baseline macro-startup)',
  '                  ;;',
  '                performance-b)',
  '                  LOGICAL_SHARDS=(macro-library macro-settings)',
  '                  ;;',
  '                *)',
  '                  echo "Unknown packed performance shard: $VALIDATION_SHARD"',
  '                  exit 2',
  '                  ;;',
  '              esac'
 ].join('\n'),[
  '              # Every matrix runner owns exactly one logical benchmark.',
  '              LOGICAL_SHARDS=("$VALIDATION_SHARD")'
 ].join('\n'));
 src=replaceExact(src,
  "        if: always() && startsWith(matrix.shard, 'performance-')",
  "        if: always() && (matrix.shard == 'baseline' || startsWith(matrix.shard, 'macro-'))");
 return src;
}
function buildExpectedCoverage(base){
 return replaceExact(base,[
  '      max-parallel: 4',
  '      matrix:',
  '        runner: [0, 1, 2, 3, 4, 5, 6, 7]'
 ].join('\n'),[
  '      # Seven physical workers keep the six-emulator critical path under 20 runners.',
  '      # All eight coverage workers and all 64 logical shards still execute.',
  '      max-parallel: 7',
  '      matrix:',
  '        runner: [0, 1, 2, 3, 4, 5, 6, 7]'
 ].join('\n'));
}
const EXPECTED_BLOBS=Object.freeze({
 '.github/scripts/check-ci-safety-contract.py':'58357888ddd96d2980061293100ebc0795fdb800',
 '.github/scripts/test-ci-safety-contract-regressions.py':'04ab3e26f8eb108323827df2a2e99c72c3216af8',
 '.github/workflows/android.yml':'0f78d28d5e8974b988490bd9facdf63cb6be6485',
 '.github/workflows/coverage.yml':'e1042881b00273aec31c2a64131ff0df7042e4ef',
 'docs/CI_ACCELERATION_172_V2.md':'786e327cdecc2b8985df1b08e1bb081d17d8e3b4'
});
const EXPECTED_FILES=new Map([
 ['.github/workflows/android.yml','modified'],
 ['.github/workflows/coverage.yml','modified'],
 ['.github/scripts/check-ci-safety-contract.py','modified'],
 ['.github/scripts/test-ci-safety-contract-regressions.py','modified'],
 ['docs/CI_ACCELERATION_172_V2.md','added']
]);
function classifyCiDelta(args){
 try{
  if(!args||typeof args!=='object'||Array.isArray(args))return FAIL('INVALID_INPUT');
 const {repo,baseSha,trustedBaseSha,headSha,expectedHeadSha,
   trusted,candidate,files,changedFileCount}=args;
  if(repo!==REPO||!HEX.test(baseSha||'')||!HEX.test(headSha||'')||
    baseSha!==trustedBaseSha||headSha!==expectedHeadSha)
   return FAIL('INVALID_IMMUTABLE_IDENTITY');
  if(!Array.isArray(files)||files.length!==EXPECTED_FILES.size||
    changedFileCount!==files.length)return FAIL('INCOMPLETE_CHANGED_FILES');
  const seen=new Set();
  for(const f of files){
   if(!f||typeof f.filename!=='string'||typeof f.status!=='string'||
      seen.has(f.filename)||EXPECTED_FILES.get(f.filename)!==f.status||
      EXPECTED_BLOBS[f.filename]!==f.sha)
    return FAIL('UNVERIFIED_PROTECTED_CHANGE');
   seen.add(f.filename);
  }
  if(seen.size!==EXPECTED_FILES.size)return FAIL('MISSING_CHANGE');
  for(const key of ['android','coverage']){
   if(typeof trusted?.[key]!=='string'||typeof candidate?.[key]!=='string'||
    !trusted[key].length||trusted[key].length>160000||
    candidate[key].length>160000)return FAIL('MISSING_WORKFLOW_SOURCE');
  }
  if(buildExpectedAndroid(trusted.android)!==candidate.android ||
    buildExpectedCoverage(trusted.coverage)!==candidate.coverage)
   return FAIL('UNRECOGNIZED_WORKFLOW_DELTA');
  // Opaque constructions inherited from main remain incomplete.
  // A scoped transformation is evidence for human review, not a PASS.
  return Object.freeze({
   schema:'ultra-sentinel-scoped-ci-delta/v1',
   status:'VERIFIED_DELTA_REVIEW_REQUIRED',
   workflowPaths:['.github/workflows/android.yml','.github/workflows/coverage.yml'],
   baseSha,headSha,contractScriptsPinned:true,scopedWorkflowDeltaOnly:true,
   inheritedUnknownsStillRequireReview:true,
   autoApproveAllowed:false,autoMergeAllowed:false
  });
 }catch(_){return FAIL('UNVERIFIED_TRANSFORMATION');}
}
const REQUIRED_JOBS=Object.freeze({
 'Android build':[
 'quality-lint','quality-contract-shard','quality-contracts','release-bundle','quality',
 'device-validation','build','device-validation-release-apk','device-validation-ui',
 'device-validation-baseline','device-validation-macro-startup',
 'device-validation-macro-library','device-validation-macro-settings',
 'android-test-shards',...Array.from({length:8},(_,i)=>'android-test-shard-'+i)],
 'Unit Test Coverage':['coverage',...Array.from({length:8},(_,i)=>'coverage-shard-'+i)]
});
function checkJobEvidence(args){
 try{
  if(!args||typeof args!=='object'||Array.isArray(args))return FAIL('INVALID_INPUT');
  const {repo,headSha,delta,apiComplete,jobsComplete,runs,jobs,trustedWorkflowIds}=args;
  if(repo!==REPO||!HEX.test(headSha||'')||
    delta?.status!=='VERIFIED_DELTA_REVIEW_REQUIRED'||
    delta.headSha!==headSha||apiComplete!==true||jobsComplete!==true||
    !Array.isArray(runs)||runs.length!==2||!jobs||typeof jobs!=='object'||
    !trustedWorkflowIds||typeof trustedWorkflowIds!=='object')
   return FAIL('INVALID_CI_PROVENANCE');
  const names=new Set(),runIds=new Set(),jobIds=new Set();
  for(const run of runs){
   if(!run||!Object.hasOwn(REQUIRED_JOBS,run.name)||names.has(run.name)||
     !Number.isSafeInteger(run.id)||run.id<1||runIds.has(run.id)||
     !Number.isSafeInteger(trustedWorkflowIds[run.name])||
     run.workflow_id!==trustedWorkflowIds[run.name]||
     run.head_sha!==headSha||run.event!=='pull_request'||
     run.status!=='completed'||run.conclusion!=='success'||
     run.head_repository?.full_name!==REPO)
    return FAIL('UNVERIFIED_WORKFLOW_RUN');
   names.add(run.name);runIds.add(run.id);
   const actual=jobs[run.id],expected=REQUIRED_JOBS[run.name];
   if(!Array.isArray(actual)||actual.length!==expected.length)
    return FAIL('INCOMPLETE_CI_JOB_LIST');
   const seen=new Set();
   for(const job of actual){
    if(!job||typeof job.name!=='string'||seen.has(job.name)||
       !expected.includes(job.name)||!Number.isSafeInteger(job.id)||
       job.id<1||jobIds.has(job.id)||job.status!=='completed'||
       job.conclusion!=='success')return FAIL('UNVERIFIED_CI_JOB');
    seen.add(job.name);jobIds.add(job.id);
   }
   if(seen.size!==expected.length)return FAIL('INCOMPLETE_CI_JOB_LIST');
  }
  if(names.size!==2)return FAIL('MISSING_WORKFLOW');
  return Object.freeze({schema:'ultra-sentinel-scoped-ci-evidence/v1',
   status:'VERIFIED_CI_REVIEW_REQUIRED',headSha,
   verifiedJobs:31,autoApproveAllowed:false,autoMergeAllowed:false});
 }catch(_){return FAIL('INCOMPLETE_CI_API_EVIDENCE');}
}
module.exports={buildExpectedAndroid,buildExpectedCoverage,classifyCiDelta,checkJobEvidence};
