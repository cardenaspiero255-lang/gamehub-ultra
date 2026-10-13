"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const yaml = require("js-yaml");
const config = yaml.load(fs.readFileSync(path.resolve(".mergify.yml"), "utf8"));
const rules = config.pull_request_rules || [];
const mergeRules = rules.filter(rule => rule.actions != null && Object.hasOwn(rule.actions, "merge"));

// The policy is fail-closed: a future re-enablement needs independent approval.
// YAML null, false or {} still count as a configured action/setting.
function automaticMergePaths(candidate) {
  const found = [];
  if (!candidate || typeof candidate !== "object" || Array.isArray(candidate)) {
    return ["invalid root configuration"];
  }
  const pullRules = candidate.pull_request_rules ?? [];
  if (!Array.isArray(pullRules)) return ["invalid pull_request_rules"];
  for (const [i, rule] of pullRules.entries()) {
    if (!rule || typeof rule !== "object" || Array.isArray(rule)) {
      found.push("pull_request_rules[" + i + "]: invalid rule");
      continue;
    }
    const actions = rule.actions;
    if (actions === undefined) continue;
    if (!actions || typeof actions !== "object" || Array.isArray(actions)) {
      found.push("pull_request_rules[" + i + "].actions: invalid");
      continue;
    }
    for (const kind of ["merge", "queue"]) {
      if (Object.hasOwn(actions, kind)) found.push("pull_request_rules[" + i + "].actions." + kind);
    }
  }
  for (const name of ["merge_protections_settings", "merge_protection_settings"]) {
    if (!Object.hasOwn(candidate, name)) continue;
    const settings = candidate[name];
    if (!settings || typeof settings !== "object" || Array.isArray(settings)) {
      found.push(name + ": invalid");
      continue;
    }
    for (const key of ["auto_merge_conditions", "auto_merge"]) {
      if (Object.hasOwn(settings, key)) found.push(name + "." + key);
    }
  }
  if (Object.hasOwn(candidate, "queue_rules") && !Array.isArray(candidate.queue_rules)) {
    found.push("queue_rules: invalid");
  }
  for (const [i, queue] of (Array.isArray(candidate.queue_rules) ? candidate.queue_rules : []).entries()) {
    if (!queue || typeof queue !== "object" || Array.isArray(queue)) {
      found.push("queue_rules[" + i + "]: invalid");
    } else if (Object.hasOwn(queue, "autoqueue")) {
      found.push("queue_rules[" + i + "].autoqueue");
    }
  }
  return found;
}

test("detects all Mergify merge actions including null and empty options",()=>{
  const hasMerge = rule => rule.actions != null && Object.hasOwn(rule.actions, "merge");
  assert.equal(hasMerge({actions:{merge:null}}),true);
  assert.equal(hasMerge({actions:{merge:{}}}),true);
  assert.equal(hasMerge({actions:{merge:{method:"squash"}}}),true);
  assert.equal(hasMerge({actions:{comment:{message:"ok"}}}),false);
  assert.equal(hasMerge({actions:{}}),false);
  assert.equal(hasMerge({}),false);
});
test("no automatic merge without SHA-bound authenticated CAR provenance and completed human handoff",()=>{
  assert.equal(mergeRules.length,0,"unsafe Copilot auto-merge must remain disabled until provenance, all pending checks and human handoff are verified");
});
const required = ["build","coverage","Ultra Sentinel Core — own reviewer","read-only-review"];
const permitted = [
  "app/src/main/java/com/gamehub/Feature.kt",
  "app/src/test/java/com/gamehub/FeatureTest.kt",
  "app/src/androidTest/java/com/gamehub/FeatureTest.kt",
];
const forbidden = [
  ".mergify.yml", ".github/workflows/ci.yml", ".github/agents/carl.agent.md",
  ".github/ISSUE_TEMPLATE/car-agent.yml", ".githooks/pre-commit",
  "supabase/config.toml", "supabase/functions/car-autopilot/handler.mjs",
  "supabase/migrations/20261201_claim.sql", "package.json", "package-lock.json",
  "app/src/main/AndroidManifest.xml", "app/src/main/res/xml/network_security_config.xml",
  "app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
  "baseline-profile/build.gradle.kts", "docs/SECURITY.md",
  "control-center/api/github.js", "README.md", "scripts/run.cjs"
];
function allowsFile(rule, file) {
  const excludes = rule.conditions.filter(x=>x.startsWith("-files ~= "));
  return !excludes.some(x=>new RegExp(x.slice("-files ~= ".length)).test(file));
}
test("Mergify always requires all four current-HEAD checks and unresolved threads = 0",()=>{
  assert.equal(mergeRules.length,0);
  for (const rule of mergeRules) {
    assert.ok(rule.conditions.includes("-draft"));
    assert.ok(rule.conditions.includes("-conflict"));
    assert.ok(rule.conditions.includes("#review-threads-unresolved = 0"));
    for (const n of required) assert.ok(rule.conditions.includes("check-success = @github-actions/"+n));
  }
});
test("no rule can automatically merge any known governance or privileged file, including mixed changes",()=>{
  for(const rule of mergeRules) for(const file of forbidden)
    assert.equal(allowsFile(rule,file),false,rule.name+" wrongly permits "+file);
  for(const rule of mergeRules) for(const file of forbidden)
    assert.equal(permitted.every(p=>allowsFile(rule,p)) && allowsFile(rule,file),false,rule.name+" permitted mixed "+file);
});
test("rules still permit pure ordinary Android source and regression tests",()=>{
  for(const rule of mergeRules) for(const file of permitted)
    assert.equal(allowsFile(rule,file),true,rule.name+" blocks ordinary source "+file);
});
test("bot rule never accepts owner identity and requires narrowly scoped CAR branch and title",()=>{
  const bot=mergeRules.find(x=>x.conditions.includes("author = copilot-swe-agent[bot]"));
  assert.equal(bot,undefined);
  if (!bot) return;
  assert.ok(bot.conditions.includes("head ~= ^copilot/"));
  assert.ok(bot.conditions.some(x=>x.startsWith("title ~= ^CAR-")));
  assert.ok(!bot.conditions.includes("label = automerge:approved"));
});

test("owner PRs never auto-merge based on an old persistent approval label",()=>{
  assert.equal(mergeRules.some(rule=>rule.conditions.includes("author = cardenaspiero255-lang")),false);
});


// Use adversarial YAML round-trips rather than inspecting only the real empty file:
// otherwise future bypasses would pass the test suite vacuously.
test("fail-closed: no Mergify merge, queue or auto-merge path exists", () => {
  assert.deepEqual(automaticMergePaths(config), [], "Unsafe Mergify auto-merge path found");
});
const dangerousConfigurations = [
  ["merge:null", {pull_request_rules:[{name:"x",conditions:[],actions:{merge:null}}]}],
  ["merge:{}", {pull_request_rules:[{name:"x",conditions:[],actions:{merge:{}}}]}],
  ["queue:null", {pull_request_rules:[{name:"x",conditions:[],actions:{queue:null}}]}],
  ["queue:{}", {pull_request_rules:[{name:"x",conditions:[],actions:{queue:{}}}]}],
  ["mixed actions", {pull_request_rules:[{name:"x",conditions:[],actions:{comment:{message:"hi"},queue:null,merge:null}}]}],
  ["queue in disabled rule", {pull_request_rules:[{name:"x",disabled:{reason:"paused"},conditions:[],actions:{queue:null}}]}],
  ["auto_merge_conditions:true", {merge_protections_settings:{auto_merge_conditions:true}}],
  ["auto_merge_conditions:list", {merge_protections_settings:{auto_merge_conditions:["label = ok"]}}],
  ["auto_merge_conditions:null", {merge_protections_settings:{auto_merge_conditions:null}}],
  ["auto_merge_conditions:false", {merge_protections_settings:{auto_merge_conditions:false}}],
  ["auto_merge:legacy", {merge_protections_settings:{auto_merge:true}}],
  ["auto_merge:singular settings", {merge_protection_settings:{auto_merge:true}}],
  ["queue_rules.autoqueue:true", {queue_rules:[{name:"q",queue_conditions:[],autoqueue:true}]}],
  ["queue_rules.autoqueue:null", {queue_rules:[{name:"q",queue_conditions:[],autoqueue:null}]}],
  ["queue_rules.autoqueue:false", {queue_rules:[{name:"q",queue_conditions:[],autoqueue:false}]}],
];
for (const [name, candidate] of dangerousConfigurations) {
  test("adversarial auto-merge configuration is rejected: " + name, () => {
    const decoded = yaml.load(yaml.dump(candidate));
    assert.notEqual(automaticMergePaths(decoded).length, 0, name + " escaped the guard");
  });
}
test("benign comments and manual-only queues do not trigger the auto-merge guard", () => {
  const safe = yaml.load(yaml.dump({
    pull_request_rules:[{name:"comment",conditions:["base = main"],actions:{comment:{message:"ok"}}}],
    queue_rules:[{name:"manual",queue_conditions:["label = manual"]}]
  }));
  assert.deepEqual(automaticMergePaths(safe), []);
});
test("invalid policy roots and action shapes fail closed", () => {
  for (const candidate of [null, [], {pull_request_rules:"invalid"}, {queue_rules:{}},
    {pull_request_rules:[{name:"x", actions:null}]}, {merge_protections_settings:null}]) {
    assert.notEqual(automaticMergePaths(candidate).length, 0);
  }
});
