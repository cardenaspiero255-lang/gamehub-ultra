# GameHub Ultra Control Center

Initial live, read-only web dashboard deployed on Vercel:

https://gamehub-ultra-control-center.vercel.app

The page displays public GitHub PRs, Actions workflow status, GitHub Releases (APK when available) and Issues for `cardenaspiero255-lang/gamehub-ultra`. Refreshes automatically every 60 seconds in a visible tab.

## Safety and scope

- No automated fixes, merges, CI triggers, or privileged GitHub mutations.
- Uses only GitHub REST read APIs without client secrets. Public APIs can be rate-limited; failures are shown.
- Sentry crashes remain private and **are not connected**. Integration would require authenticated backend with access controls, not API keys in the browser.
- APK downloads link only to GitHub Release assets; other CI artifacts remain under GitHub Actions.
- Current first deployment was published directly through Vercel rather than Git integration. Next step for automated updates is to move the dashboard source into `control-center/` in this repo, set the Vercel project root to that directory, and link the repository. Do not link to the Android project root.

This PR only records the deployment; it does not change Android code or the CAR-51 PR #158.
