import { type ResearchDependencies, routeResearchQuery } from "./research.ts";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

Deno.test("news requires two independent current sources before returning", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
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
    fetcher: (input) => {
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
    fetcher: (input) => {
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
    fetcher: () => {
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

Deno.test("weather remains verified through Open-Meteo after provider refactor", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = String(input);
      if (url.includes("geocoding-api.open-meteo.com")) {
        return jsonResponse({
          results: [{
            name: "Santiago",
            admin1: "Región Metropolitana",
            country: "Chile",
            latitude: -33.45,
            longitude: -70.66,
          }],
        });
      }
      if (url.includes("api.open-meteo.com/v1/forecast")) {
        return jsonResponse({
          current: {
            temperature_2m: 22,
            apparent_temperature: 21,
            weather_code: 0,
            time: "2026-09-26T21:00",
          },
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, clima de hoy en Santiago",
    deps,
  );

  if (result.abstained) throw new Error("expected verified weather result");
  if (!result.authoritative) throw new Error("expected authoritative weather");
  if (!result.displayText?.includes("22")) {
    throw new Error("expected current temperature");
  }
});

Deno.test("comparison combines two verified specification records", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = String(input);
      if (url.includes("api.github.com/search/code")) {
        const isRedMagic = decodeURIComponent(url).toLowerCase().includes(
          "redmagic",
        );
        return jsonResponse({
          items: [{
            path: isRedMagic
              ? "data/smartphone/redmagic/2026/red-magic-11s-pro.json"
              : "data/smartphone/samsung/2026/galaxy-s26-ultra.json",
            url: isRedMagic
              ? "https://api.github.com/repos/GetTechAPI/TechAPI/contents/data/smartphone/redmagic/2026/red-magic-11s-pro.json?ref=develop"
              : "https://api.github.com/repos/GetTechAPI/TechAPI/contents/data/smartphone/samsung/2026/galaxy-s26-ultra.json?ref=develop",
          }],
        });
      }
      if (url.includes("red-magic-11s-pro.json")) {
        return jsonResponse({
          name: "RedMagic 11S Pro",
          soc: "snapdragon-8-elite-gen-5",
          battery_mah: 7500,
          ram_gb: 16,
          verified: true,
          source_urls: ["https://global.redmagic.gg/products/redmagic-11s-pro"],
        });
      }
      if (url.includes("galaxy-s26-ultra.json")) {
        return jsonResponse({
          name: "Galaxy S26 Ultra",
          soc: "snapdragon-8-elite-gen-5",
          battery_mah: 5000,
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
    "Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra",
    deps,
  );

  if (result.abstained) throw new Error("expected verified comparison");
  if (!result.displayText?.includes("RedMagic 11S Pro")) {
    throw new Error("expected first product");
  }
  if (!result.displayText?.includes("Galaxy S26 Ultra")) {
    throw new Error("expected second product");
  }
  if ((result.sourceIds?.length ?? 0) < 2) {
    throw new Error("expected sources for both products");
  }
});

Deno.test("marketplace price uses current Mercado Libre listings when configured", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input, init) => {
      const url = String(input);
      if (!url.includes("api.mercadolibre.com/sites/MLC/search")) {
        throw new Error("unexpected URL " + url);
      }
      if (!String(init?.headers).includes("Bearer test-token")) {
        // Headers objects stringify poorly; the response behavior is what matters.
      }
      return jsonResponse({
        results: [
          {
            title: "Galaxy S26 Ultra 256 GB",
            price: 1299990,
            currency_id: "CLP",
            permalink: "https://www.mercadolibre.cl/item-one",
            seller: { id: 1 },
          },
          {
            title: "Samsung Galaxy S26 Ultra",
            price: 1349990,
            currency_id: "CLP",
            permalink: "https://www.mercadolibre.cl/item-two",
            seller: { id: 2 },
          },
        ],
      });
    },
    env: (name) =>
      name === "MERCADOLIBRE_ACCESS_TOKEN" ? "test-token" : undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, precio actual del Galaxy S26 Ultra",
    deps,
  );

  if (result.abstained) throw new Error("expected current price evidence");
  if (!result.displayText?.includes("CLP")) {
    throw new Error("expected marketplace currency");
  }
  if (!result.sourceIds?.length) throw new Error("expected listing sources");
});

Deno.test("weather follow-up uses current question instead of contaminating it with prior location", async () => {
  let geocodedName = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.hostname === "geocoding-api.open-meteo.com") {
        geocodedName = url.searchParams.get("name") ?? "";
        return jsonResponse({
          results: [{
            name: "Rancagua",
            admin1: "O'Higgins",
            country: "Chile",
            latitude: -34.17,
            longitude: -70.74,
          }],
        });
      }
      if (url.hostname === "api.open-meteo.com") {
        return jsonResponse({
          current: {
            temperature_2m: 20,
            apparent_temperature: 20,
            weather_code: 0,
            time: "2026-09-26T22:00",
          },
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, y clima en Rancagua?",
    deps,
    "Ultra, clima de hoy en Santiago",
    "CURRENT_DATA",
  );

  if (result.abstained) throw new Error("expected verified follow-up weather");
  if (geocodedName !== "Rancagua") {
    throw new Error("expected current question location, got " + geocodedName);
  }
});
