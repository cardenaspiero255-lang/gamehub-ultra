"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const yaml = require("js-yaml");
const config = yaml.load(fs.readFileSync(path.resolve(".mergify.yml"), "utf8"));
const rules = config.pull_request_rules || [];
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
  assert.equal(rules.length,2);
  for (const rule of rules) {
    assert.ok(rule.conditions.includes("-draft"));
    assert.ok(rule.conditions.includes("-conflict"));
    assert.ok(rule.conditions.includes("#review-threads-unresolved = 0"));
    for (const n of required) assert.ok(rule.conditions.includes("check-success = @github-actions/"+n));
  }
});
test("no rule can automatically merge any known governance or privileged file, including mixed changes",()=>{
  for(const rule of rules) for(const file of forbidden)
    assert.equal(allowsFile(rule,file),false,rule.name+" wrongly permits "+file);
  for(const rule of rules) for(const file of forbidden)
    assert.equal(permitted.every(p=>allowsFile(rule,p)) && allowsFile(rule,file),false,rule.name+" permitted mixed "+file);
});
test("rules still permit pure ordinary Android source and regression tests",()=>{
  for(const rule of rules) for(const file of permitted)
    assert.equal(allowsFile(rule,file),true,rule.name+" blocks ordinary source "+file);
});
test("bot rule never accepts owner identity and requires narrowly scoped CAR branch and title",()=>{
  const bot=rules.find(x=>x.conditions.includes("author = copilot-swe-agent[bot]"));
  assert.ok(bot);
  assert.ok(bot.conditions.includes("head ~= ^copilot/"));
  assert.ok(bot.conditions.some(x=>x.startsWith("title ~= ^CAR-")));
  assert.ok(!bot.conditions.includes("label = automerge:approved"));
});
