import { handleCarWebhook } from "./handler.mjs";

// Webhook auth is GitHub's SHA-256 HMAC signature over the raw request bytes.
// Never enable this publicly without setting both secrets and a GitHub
// repository webhook matching the configured signing secret.
Deno.serve((request) => handleCarWebhook(request, {
  secret: Deno.env.get("CAR_GITHUB_WEBHOOK_SECRET"),
  token: Deno.env.get("CAR_GITHUB_ISSUES_TOKEN"),
}));
