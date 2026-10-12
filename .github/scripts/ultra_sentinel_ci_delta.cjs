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
const crypto=require('node:crypto');
const KNOWN_BASE='7aa5df3e3c704965862ea728fb4c390d2c15a7f0';
const KNOWN_HEAD='bb08782350201f97576a974a4684a611c21701c0';
const MAIN_SCRIPT_BLOBS=Object.freeze({safetyContract:'e297ca93aface84cda6a7769cd21da6e47e0e7af',regressions:'f3c8fa8c0f0c2a9361a33a12fe7386729404e5df'});
const PATCH_SAFETY_CONTRACT="@@ -516,18 +516,21 @@ def main() -> None:\n         fail(\"device validation strategy is missing\")\n     if strategy.get(\"fail-fast\") is not False:\n         fail(\"device validation matrix must keep fail-fast disabled\")\n-    if strategy.get(\"max-parallel\") != 4:\n-        fail(\"device validation matrix must keep four physical runners\")\n+    device_parallel = strategy.get(\"max-parallel\")\n+    if device_parallel != 6:\n+        fail(\"device validation must run six independent API 35 shards\")\n     matrix = strategy.get(\"matrix\")\n     if not isinstance(matrix, dict):\n         fail(\"device validation matrix definition is missing\")\n     if set(matrix.get(\"shard\", [])) != {\n         \"release-apk\",\n         \"ui\",\n-        \"performance-a\",\n-        \"performance-b\",\n+        \"baseline\",\n+        \"macro-startup\",\n+        \"macro-library\",\n+        \"macro-settings\",\n     }:\n-        fail(\"device validation lost release/ui/packed performance coverage\")\n+        fail(\"device validation lost release/ui or one of four performance validations\")\n \n     retry_test = require_step(\n         android,\n@@ -557,7 +560,7 @@ def main() -> None:\n         \":app:assembleDebugAndroidTest\",\n         \":app:assembleNonMinifiedRelease\",\n         \":baseline-profile:assembleNonMinifiedRelease\",\n-        \"performance-*\",\n+        \"baseline|macro-*)\",\n         \"--max-workers=12\",\n         \"EMULATOR_LAUNCH_EPOCH=\",\n         \"-no-window\",\n@@ -570,6 +573,18 @@ def main() -> None:\n     if \":app:bundleRelease\" in str(prebuild.get(\"run\", \"\")):\n         fail(\"AAB build must stay isolated in release-bundle shard\")\n \n+    selector = require_step(\n+        android,\n+        \"device-validation-shard\",\n+        \"Use current Android command-line tools\",\n+        shell=\"bash\",\n+    )\n+    require_run_fragment(selector, \"safe SDK tools\", 'SDK_BIN=\"$SDK_ROOT/cmdline-tools/latest/bin\"')\n+    require_run_fragment(selector, \"safe SDK tools\", 'if [ ! -x \"$SDK_BIN/sdkmanager\" ]; then')\n+    require_run_fragment(selector, \"safe SDK tools\", 'printf \\'%s\\\\n\\' \"$SDK_BIN\" >> \"$GITHUB_PATH\"')\n+    if 'rm -rf \"$SDK_ROOT/cmdline-tools/latest\"' in str(selector.get(\"run\", \"\")):\n+        fail(\"device runner must not destroy the preinstalled SDK\")\n+\n     readiness = require_step(\n         android,\n         \"device-validation-shard\",\n@@ -595,8 +610,10 @@ def main() -> None:\n         \"adb uninstall\",\n         \":app:connectedDebugAndroidTest\",\n         \":baseline-profile:connectedNonMinifiedReleaseAndroidTest\",\n-        'LOGICAL_SHARDS=(baseline macro-startup)',\n-        'LOGICAL_SHARDS=(macro-library macro-settings)',\n+        'LOGICAL_SHARDS=(\"$VALIDATION_SHARD\")',\n+        'macro-startup) EXPECTED_METHOD=\"coldStartup\"',\n+        'macro-library) EXPECTED_METHOD=\"navigationToLibrary\"',\n+        'macro-settings) EXPECTED_METHOD=\"navigationToSettings\"',\n         'RULE=\"BaselineProfile\"',\n         'RULE=\"Macrobenchmark\"',\n         \"coldStartup\",\n@@ -632,7 +649,7 @@ def main() -> None:\n     )\n     validate_run = str(validate.get(\"run\", \"\"))\n     if validate_run.count(\":baseline-profile:connectedNonMinifiedReleaseAndroidTest\") != 1:\n-        fail(\"packed performance script must contain exactly one looped instrumentation command\")\n+        fail(\"performance tests must use one fail-closed instrumentation command\")\n     for expected_command in (\n         (\"adb\", \"install\", \"$APK\"),\n         (\"adb\", \"install\", \"-r\", \"$APK\"),\n@@ -758,7 +775,7 @@ def main() -> None:\n         \"device-validation-shard\",\n         \"Upload benchmark reports\",\n         uses_prefix=\"actions/upload-artifact@\",\n-        allowed_if=\"always() && startsWith(matrix.shard, 'performance-')\",\n+        allowed_if=\"always() && (matrix.shard == 'baseline' || startsWith(matrix.shard, 'macro-'))\",\n     )\n     benchmark_with = benchmark_upload.get(\"with\")\n     if not isinstance(benchmark_with, dict):\n@@ -899,8 +916,12 @@ def main() -> None:\n         fail(\"coverage shard strategy is missing\")\n     if coverage_strategy.get(\"fail-fast\") is not False:\n         fail(\"coverage shards must keep fail-fast disabled\")\n-    if coverage_strategy.get(\"max-parallel\") != 4:\n-        fail(\"coverage physical concurrency changed\")\n+    if coverage_strategy.get(\"max-parallel\") != 7:\n+        fail(\"coverage must retain seven bounded concurrent workers and eight total runners\")\n+    # 4 Android unit runners + 6 emulator runners + 3 support jobs + 7 Coverage = 20.\n+    # Account for all four simultaneous resource families; no overcommit by CI design.\n+    if 4 + device_parallel + 3 + coverage_strategy.get(\"max-parallel\") > 20:\n+        fail(\"combined CI runner allocation exceeds the 20-runner budget\")\n     coverage_matrix = coverage_strategy.get(\"matrix\")\n     if (\n         not isinstance(coverage_matrix, dict)";
const PATCH_REGRESSIONS="@@ -389,23 +389,15 @@ def main() -> None:\n     reject_mutation(\n         \"performance physical shard removed\",\n         android_replace=(\n-            \"        shard: [release-apk, ui, performance-a, performance-b]\\n\",\n-            \"        shard: [release-apk, ui, performance-a]\\n\",\n+            \"        shard: [release-apk, ui, baseline, macro-startup, macro-library, macro-settings]\\n\",\n+            \"        shard: [release-apk, ui, baseline, macro-startup, macro-library]\\n\",\n         ),\n     )\n     reject_mutation(\n         \"device physical parallelism reduced\",\n         android_replace=(\n-            \"      max-parallel: 4\\n\"\n-            \"      matrix:\\n\"\n-            \"        # Four logical performance checks are packed into two physical emulator\\n\"\n-            \"        # runners so Android boot/setup is reused instead of repeated four times.\\n\"\n-            \"        shard: [release-apk, ui, performance-a, performance-b]\\n\",\n-            \"      max-parallel: 2\\n\"\n-            \"      matrix:\\n\"\n-            \"        # Four logical performance checks are packed into two physical emulator\\n\"\n-            \"        # runners so Android boot/setup is reused instead of repeated four times.\\n\"\n-            \"        shard: [release-apk, ui, performance-a, performance-b]\\n\",\n+            \"      max-parallel: 6\\n      matrix:\\n        # Four real performance checks run on independent API 35 emulators.\\n        # Keeps baseline + cold-start + library + settings with no serial waves.\\n        shard: [release-apk, ui, baseline, macro-startup, macro-library, macro-settings]\",\n+            \"      max-parallel: 4\\n      matrix:\\n        # Four real performance checks run on independent API 35 emulators.\\n        # Keeps baseline + cold-start + library + settings with no serial waves.\\n        shard: [release-apk, ui, baseline, macro-startup, macro-library, macro-settings]\",\n         ),\n     )\n     reject_mutation(\n@@ -446,15 +438,15 @@ def main() -> None:\n     reject_mutation(\n         \"packed startup macro removed\",\n         android_replace=(\n-            \"                  LOGICAL_SHARDS=(baseline macro-startup)\\n\",\n-            \"                  LOGICAL_SHARDS=(baseline)\\n\",\n+            \"              LOGICAL_SHARDS=(\\\"$VALIDATION_SHARD\\\")\\n\",\n+            \"              LOGICAL_SHARDS=()\\n\",\n         ),\n     )\n     reject_mutation(\n         \"packed settings macro removed\",\n         android_replace=(\n-            \"                  LOGICAL_SHARDS=(macro-library macro-settings)\\n\",\n-            \"                  LOGICAL_SHARDS=(macro-library)\\n\",\n+            \"                    macro-settings) EXPECTED_METHOD=\\\"navigationToSettings\\\" ;;\",\n+            \"                    macro-settings) EXPECTED_METHOD=\\\"navigationToLibrary\\\" ;;\",\n         ),\n     )\n     reject_mutation(\n@@ -565,7 +557,7 @@ def main() -> None:\n     reject_mutation(\n         \"coverage physical concurrency raised\",\n         coverage_replace=(\n-            \"      max-parallel: 4\\n\",\n+            \"      max-parallel: 7\\n\",\n             \"      max-parallel: 8\\n\",\n         ),\n     )\n@@ -642,6 +634,42 @@ def main() -> None:\n         ),\n     )\n \n+    reject_mutation(\n+        \"coverage parallel regression to two waves\",\n+        coverage_replace=(\n+            \"      max-parallel: 7\\n\",\n+            \"      max-parallel: 4\\n\",\n+        ),\n+    )\n+    reject_mutation(\n+        \"benchmark artifact upload made advisory\",\n+        android_replace=(\n+            \"        if: always() && (matrix.shard == 'baseline' || startsWith(matrix.shard, 'macro-'))\\n\",\n+            \"        if: always()\\n\",\n+        ),\n+    )\n+    reject_mutation(\n+        \"SDK selector deletes runner-installed tools\",\n+        android_replace=(\n+            \"          printf '%s\\\\n' \\\"$SDK_BIN\\\" >> \\\"$GITHUB_PATH\\\"\\n\",\n+            \"          rm -rf \\\"$SDK_ROOT/cmdline-tools/latest\\\"\\n          printf '%s\\\\n' \\\"$SDK_BIN\\\" >> \\\"$GITHUB_PATH\\\"\\n\",\n+        ),\n+    )\n+    reject_mutation(\n+        \"startup benchmark method mapped to library\",\n+        android_replace=(\n+            \"                    macro-startup) EXPECTED_METHOD=\\\"coldStartup\\\" ;;\",\n+            \"                    macro-startup) EXPECTED_METHOD=\\\"navigationToLibrary\\\" ;;\",\n+        ),\n+    )\n+    reject_mutation(\n+        \"library benchmark method mapped to settings\",\n+        android_replace=(\n+            \"                    macro-library) EXPECTED_METHOD=\\\"navigationToLibrary\\\" ;;\",\n+            \"                    macro-library) EXPECTED_METHOD=\\\"navigationToSettings\\\" ;;\",\n+        ),\n+    )\n+\n     print(\n         \"CI safety regression suite passed: packed parallel gates fail closed.\"\n     )";
const EXPECTED_DOC="# PR #172 — CI V2: independent benchmark critical path (2026-10-12)\n\nThis supersedes the old Android 5/Coverage 8 and preinstalled-SDK probe optimization. The branch merges the current main tree, then changes only the new benchmark topology, concurrency contracts and safe Android tools selection.\n\n## Observed baseline\n\n- Android main PR #187 (SHA 7cbdabf): 20 jobs PASS; longest performance-a was 562 s; performance-b 373 s; performance-a sequential baseline and startup 2m22s + 49s in the later main run.\n- Android main merge #187 (SHA 7aa5df3): performance-a 476s; performance-b 467s; total Android ~8 minutes.\n- Coverage old main (PR #171): 6m25s; original PR #172 recorded 3m40s with 8 parallel workers, but did **not** reduce the Android critical path.\n\n## V2 strategy\n\n- **Six** real API 35 device runners: release APK, UI, baseline profile, startup macro, library macro, settings macro. Each previously packed performance check is now independently executed; none is replaced by an inert command or skipped test.\n- **Four** Android test workers and **seven** concurrent Coverage workers (eight total Coverage shards, 64 logical). Three Android support workers = at most **20 simultaneously provisioned primary runners** under the accounting contract. CI may still queue for unrelated workflows.\n- Release signing, Sentry, 90% patch coverage, full JaCoCo aggregation, adversarial/SDK retry tests, result fan-ins, benchmark XML and report artifacts remain blocking.\n- Uses the trusted main SDK action, **not** the old preinstalled-SDK probe. Retains the separate non-destructive cmdline-tools/latest correctness fix because the previous destructive replacement was a confirmed CI failure.\n- Every workflow/contract mutation is checked by `.github/scripts/test-ci-safety-contract-regressions.py`; unknown evidence fails closed. No self-approval of protected workflows.\n\n## Acceptance and measurement\n\nCompare successful GitHub Actions runs of the previous main against this PR's HEAD (wall-clock, longest shard, queue, benchmark XML, Android 20+ jobs, Coverage 9/9). Do not state a guaranteed number of minutes until real CI validates it. Require trusted AST and independent approval on the exact SHA for modified workflows before merging.\n";
function applyTrustedPatch(base,patch){
 const orig=base.split('\n'),lines=patch.split('\n'),out=[];
 let idx=0,pos=0,hunks=0;
 const header=/^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@/;
 while(idx<lines.length){
  const m=header.exec(lines[idx]);
  if(!m){if(lines[idx]!==''&&idx!==0)throw Error('UNEXPECTED_PATCH_HEADER');idx++;continue;}
  const start=Number(m[1])-1;
  if(start<pos||start>orig.length)throw Error('INVALID_PATCH_START');
  while(pos<start)out.push(orig[pos++]);
  let removed=0,inserted=0;idx++;hunks++;
  while(idx<lines.length&&!lines[idx].startsWith('@@ ')){
   const row=lines[idx++];
   if(row==='')continue;
   if(row.startsWith('\\ No newline'))throw Error('UNSUPPORTED_NO_NEWLINE');
   const ch=row[0],v=row.slice(1);
   if(ch==='+'){out.push(v);inserted++;}
   else if(ch===' '||ch==='-'){
    if(orig[pos]!==v)throw Error('PATCH_CONTEXT_MISMATCH');
    if(ch===' '){out.push(v);inserted++;}
    removed++;pos++;
   }else throw Error('INVALID_PATCH_ROW');
  }
  if(removed!==Number(m[2]||1)||inserted!==Number(m[4]||1))
   throw Error('PATCH_HUNK_COUNT_MISMATCH');
 }
 if(!hunks)throw Error('NO_PATCH_HUNKS');
 out.push(...orig.slice(pos));return out.join('\n');
}
function expectedContract(base){return applyTrustedPatch(base,PATCH_SAFETY_CONTRACT);}
function expectedRegressions(base){return applyTrustedPatch(base,PATCH_REGRESSIONS);}
function gitBlobSha(body){return crypto.createHash('sha1').update('blob '+Buffer.byteLength(body)+'\0'+body).digest('hex');}
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
    baseSha!==trustedBaseSha||headSha!==expectedHeadSha||
    baseSha!==KNOWN_BASE||headSha!==KNOWN_HEAD)
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
      !trusted[key].length||!candidate[key].length||
      trusted[key].length>160000||candidate[key].length>160000)
    return FAIL('MISSING_WORKFLOW_SOURCE');
  }
  for(const key of ['safetyContract','regressions']){
   if(typeof trusted?.[key]!=='string'||typeof candidate?.[key]!=='string'||
      gitBlobSha(trusted[key])!==MAIN_SCRIPT_BLOBS[key]||
      candidate[key]!== (key==='safetyContract'?expectedContract(trusted[key]):expectedRegressions(trusted[key])))
    return FAIL('UNVERIFIED_CONTRACT_FULL_BYTES');
  }
  if(typeof candidate?.documentation!=='string'||
     candidate.documentation!==EXPECTED_DOC)
   return FAIL('UNVERIFIED_DOCUMENT_FULL_BYTES');
  if(buildExpectedAndroid(trusted.android)!==candidate.android||
     buildExpectedCoverage(trusted.coverage)!==candidate.coverage)
   return FAIL('UNRECOGNIZED_WORKFLOW_DELTA');
  const contentBlobs={
   '.github/workflows/android.yml':candidate.android,
   '.github/workflows/coverage.yml':candidate.coverage,
   '.github/scripts/check-ci-safety-contract.py':candidate.safetyContract,
   '.github/scripts/test-ci-safety-contract-regressions.py':candidate.regressions,
   'docs/CI_ACCELERATION_172_V2.md':candidate.documentation
  };
  for(const [path,body] of Object.entries(contentBlobs))
   if(gitBlobSha(body)!==EXPECTED_BLOBS[path])
    return FAIL('UNVERIFIED_HEAD_BLOB_CONTENT');
  for(const [key,sourceSha] of Object.entries({
   android:'418287ceb11221f1879ee81cbef40c4b008c4f04',
   coverage:'5ccf1c5de322a29c6e5b1d884ea659d310efa83c',
   safetyContract:'e297ca93aface84cda6a7769cd21da6e47e0e7af',
   regressions:'f3c8fa8c0f0c2a9361a33a12fe7386729404e5df'
  }))
   if(gitBlobSha(trusted[key])!==sourceSha)
    return FAIL('UNTRUSTED_BASE_BLOB_CONTENT');
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
module.exports={buildExpectedAndroid,buildExpectedCoverage,buildExpectedContract:expectedContract,buildExpectedRegressions:expectedRegressions,buildExpectedDocs:()=>EXPECTED_DOC,classifyCiDelta,checkJobEvidence};
