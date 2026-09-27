import { type ResearchDependencies, routeResearchQuery } from "./research.ts";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

Deno.test("news requires two independent current sources before returning", async () => {
  const deps: ResearchDependencies = {
    fetcher: async (input) => {
      const url = String(input);
      if (!url.includes("api.gdeltproject.org/api/v2/doc/doc")) {
        throw new Error("unexpected URL " + url);
      }
      return jsonResponse({
        articles: [
          {
            title: "Resident Evil update one",
            url: "https://news-one.example/re-update",
            domain: "news-one.example",
            seendate: "20260926T220000Z",
          },
          {
            title: "Resident Evil update two",
            url: "https://news-two.example/re-update",
            domain: "news-two.example",
            seendate: "20260926T221500Z",
          },
        ],
      });
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, noticias actuales de Resident Evil",
    deps,
  );

  if (result.abstained) throw new Error("expected verified news result");
  if (result.independentSourceCount !== 2) {
    throw new Error("expected two independent sources");
  }
  if (result.sourceIds?.length !== 2) {
    throw new Error("expected both source URLs");
  }
});

Deno.test("verified TechAPI specifications preserve primary source URLs", async () => {
  const deps: ResearchDependencies = {
    fetcher: async (input) => {
      const url = String(input);
      if (url.includes("api.github.com/search/code")) {
        return jsonResponse({
          items: [
            {
              path: "data/smartphone/samsung/2026/galaxy-s26-ultra.json",
              url:
                "https://api.github.com/repos/GetTechAPI/TechAPI/contents/data/smartphone/samsung/2026/galaxy-s26-ultra.json?ref=develop",
            },
          ],
        });
      }
      if (url.includes("api.github.com/repos/GetTechAPI/TechAPI/contents/")) {
        return jsonResponse({
          name: "Galaxy S26 Ultra",
          brand: "samsung",
          soc: "snapdragon-8-elite-gen-5",
          battery_mah: 5000,
          charging_wired_w: 60,
          ram_gb: 12,
          verified: true,
          source_urls: [
            "https://www.samsung.com/global/galaxy/galaxy-s26-ultra/specs/",
          ],
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, especificaciones del Galaxy S26 Ultra",
    deps,
  );

  if (result.abstained) throw new Error("expected verified specs result");
  if (!result.authoritative) throw new Error("expected authoritative result");
  if (
    !result.sourceIds?.includes(
      "https://www.samsung.com/global/galaxy/galaxy-s26-ultra/specs/",
    )
  ) {
    throw new Error("expected official Samsung source");
  }
});

Deno.test("unverified specification records abstain instead of inventing", async () => {
  const deps: ResearchDependencies = {
    fetcher: async (input) => {
      const url = String(input);
      if (url.includes("api.github.com/search/code")) {
        return jsonResponse({
          items: [{
            path:
              "data/smartphone/redmagic/2026/red-magic-11s-pro/redmagic-red-magic-11s-pro-12gb-256gb-5g.json",
            url:
              "https://api.github.com/repos/GetTechAPI/TechAPI/contents/data/smartphone/redmagic/2026/red-magic-11s-pro/redmagic-red-magic-11s-pro-12gb-256gb-5g.json?ref=develop",
          }],
        });
      }
      if (url.includes("api.github.com/repos/GetTechAPI/TechAPI/contents/")) {
        return jsonResponse({
          name: "Red Magic 11S Pro",
          battery_mah: 8000,
          verified: false,
          source_urls: ["https://example.invalid/unverified-dataset"],
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, especificaciones del RedMagic 11S Pro",
    deps,
  );

  if (!result.abstained) {
    throw new Error("unverified specs must not be presented as verified");
  }
});

Deno.test("current marketplace price abstains when no price credential exists", async () => {
  const deps: ResearchDependencies = {
    fetcher: async () => {
      throw new Error("price fetch should not run without credentials");
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, precio actual del Galaxy S26 Ultra",
    deps,
  );

  if (!result.abstained) throw new Error("expected safe abstention");
  if (!result.message?.toLowerCase().includes("precio")) {
    throw new Error("expected a price-specific limitation");
  }
});
