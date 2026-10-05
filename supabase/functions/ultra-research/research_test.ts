import { type ResearchDependencies, routeResearchQuery } from "./research.ts";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function wikipediaSearchParam(url: URL): string {
  return url.searchParams.get("gsrsearch") ??
    url.searchParams.get("srsearch") ??
    "";
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
            title: "Resident Evil recibe una actualización importante",
            url: "https://news-one.example/re-update",
            domain: "news-one.example",
            seendate: "20260926T220000Z",
          },
          {
            title: "Nuevo parche mejora el rendimiento de Resident Evil",
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

Deno.test("marketplace price can read OAuth access token from Vault resolver", async () => {
  let authorization = "";
  const deps: ResearchDependencies = {
    fetcher: (_input, init) => {
      const headers = new Headers(init?.headers);
      authorization = headers.get("Authorization") ?? "";
      return jsonResponse({
        results: [{
          title: "RedMagic 12 Pro",
          price: 999990,
          currency_id: "CLP",
          permalink: "https://www.mercadolibre.cl/redmagic-12-pro",
          seller: { id: 7 },
        }],
      });
    },
    env: () => undefined,
    secret: (name) =>
      Promise.resolve(
        name === "mercadolibre_access_token" ? "vault-token" : undefined,
      ),
  };

  const result = await routeResearchQuery(
    "Ultra, precio actual del RedMagic 12 Pro",
    deps,
  );

  if (result.abstained) throw new Error("expected Vault-backed price evidence");
  if (authorization !== "Bearer vault-token") {
    throw new Error("expected OAuth token from Vault resolver");
  }
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

Deno.test("general knowledge returns a sourced answer instead of the gaming fallback", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        return jsonResponse({
          query: { search: [{ title: "Dispersión de Rayleigh" }] },
        });
      }
      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/page/summary/")
      ) {
        return jsonResponse({
          title: "Dispersión de Rayleigh",
          extract:
            "La dispersión de Rayleigh explica por qué las longitudes de onda cortas de la luz visible se dispersan más en la atmósfera, haciendo que el cielo se vea azul.",
          content_urls: {
            desktop: {
              page: "https://es.wikipedia.org/wiki/Dispersi%C3%B3n_de_Rayleigh",
            },
          },
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, por qué el cielo es azul",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected sourced general answer");
  if (!result.displayText?.toLowerCase().includes("cielo")) {
    throw new Error("expected an actual answer to the question");
  }
  if (!result.sourceIds?.some((source) => source.includes("wikipedia.org"))) {
    throw new Error("expected a visible source");
  }
});

Deno.test("explicit general-knowledge kind wins over incidental price words", async () => {
  let searchQuery = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        searchQuery = wikipediaSearchParam(url);
        return jsonResponse({
          query: { search: [{ title: "Valor esperado" }] },
        });
      }
      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/page/summary/")
      ) {
        return jsonResponse({
          title: "Valor esperado",
          type: "standard",
          extract:
            "El valor esperado es una medida del resultado medio de una variable aleatoria.",
          content_urls: {
            desktop: {
              page: "https://es.wikipedia.org/wiki/Valor_esperado",
            },
          },
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, qué es el valor esperado",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected general knowledge result");
  if (!searchQuery.toLowerCase().includes("valor esperado")) {
    throw new Error(
      "expected Wikipedia research, not marketplace price lookup",
    );
  }
});

Deno.test("general-knowledge follow-up searches with previous topic context", async () => {
  let searchQuery = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        searchQuery = wikipediaSearchParam(url);
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }
      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/page/summary/")
      ) {
        return jsonResponse({
          title: "Vulkan",
          type: "standard",
          extract: "Vulkan es una API gráfica de bajo nivel.",
          content_urls: {
            desktop: { page: "https://es.wikipedia.org/wiki/Vulkan" },
          },
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "¿y para qué sirve?",
    deps,
    "Ultra, explícame qué es Vulkan",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected contextual answer");
  if (searchQuery.toLowerCase() !== "vulkan") {
    throw new Error(
      "expected a clean previous topic for the follow-up research query",
    );
  }
});

Deno.test("Wikipedia disambiguation summaries are not treated as authoritative answers", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        return jsonResponse({
          query: { search: [{ title: "Mercurio" }] },
        });
      }
      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/page/summary/")
      ) {
        return jsonResponse({
          title: "Mercurio",
          type: "disambiguation",
          extract: "Mercurio puede referirse a varios conceptos.",
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, qué es Mercurio",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (!result.abstained) {
    throw new Error("disambiguation text must not be returned as the answer");
  }
});

Deno.test("Wikipedia requests identify the GameHub Ultra operator", async () => {
  const userAgents: string[] = [];
  const deps: ResearchDependencies = {
    fetcher: (input, init) => {
      const headers = new Headers(init?.headers);
      userAgents.push(headers.get("User-Agent") ?? "");
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }
      return jsonResponse({
        title: "Vulkan",
        type: "standard",
        extract: "Vulkan es una API gráfica.",
      });
    },
    env: () => undefined,
  };

  await routeResearchQuery(
    "Ultra, qué es Vulkan",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (
    userAgents.some((value) =>
      !value.includes("github.com/cardenaspiero255-lang/gamehub-ultra")
    )
  ) {
    throw new Error("expected operator contact in Wikipedia User-Agent");
  }
});

Deno.test("English general knowledge still returns Spanish encyclopedia content", async () => {
  const hosts: string[] = [];
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      hosts.push(url.hostname);
      if (url.pathname === "/w/api.php") {
        if (url.searchParams.get("generator") === "search") {
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Fotosíntesis",
                  extract:
                    "La fotosíntesis convierte energía luminosa en energía química.",
                  fullurl: "https://es.wikipedia.org/wiki/Fotos%C3%ADntesis",
                },
              },
            },
          });
        }
        return jsonResponse({
          query: { search: [{ title: "Fotosíntesis" }] },
        });
      }
      return jsonResponse({
        title: "Fotosíntesis",
        type: "standard",
        extract:
          "La fotosíntesis es el proceso por el que los organismos convierten la energía de la luz en energía química.",
        content_urls: {
          desktop: { page: "https://es.wikipedia.org/wiki/Fotos%C3%ADntesis" },
        },
      });
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, what is photosynthesis?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected English knowledge answer");
  if (hosts.some((host) => host !== "es.wikipedia.org")) {
    throw new Error(
      "expected every encyclopedia request to use es.wikipedia.org",
    );
  }
  if (!result.displayText?.includes("fotosíntesis")) {
    throw new Error("expected a Spanish answer");
  }
});

Deno.test("Spanish factual prefixes are removed before encyclopedia search", async () => {
  const queries: string[] = [];
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        queries.push(wikipediaSearchParam(url));
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }
      return jsonResponse({
        title: "Vulkan",
        type: "standard",
        extract: "Vulkan es una API gráfica.",
      });
    },
    env: () => undefined,
  };

  await routeResearchQuery(
    "Ultra, para qué sirve Vulkan",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (queries[0]?.toLowerCase() !== "vulkan") {
    throw new Error("expected a clean encyclopedia topic");
  }
});

Deno.test("Spanish question language ignores English words inside entity names", async () => {
  const hosts: string[] = [];
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      hosts.push(url.hostname);
      if (url.pathname === "/w/api.php") {
        return jsonResponse({
          query: { search: [{ title: "Doctor Who" }] },
        });
      }
      return jsonResponse({
        title: "Doctor Who",
        type: "standard",
        extract: "Doctor Who es una serie británica de ciencia ficción.",
        content_urls: {
          desktop: { page: "https://es.wikipedia.org/wiki/Doctor_Who" },
        },
      });
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, qué es Doctor Who?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected Spanish knowledge answer");
  if (hosts.some((host) => host !== "es.wikipedia.org")) {
    throw new Error(
      "expected Spanish question syntax to select es.wikipedia.org",
    );
  }
});

Deno.test("a complete new topic ignores previous knowledge context", async () => {
  let searchQuery = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        searchQuery = wikipediaSearchParam(url);
        if (url.searchParams.get("generator") === "search") {
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Fotosíntesis",
                  extract:
                    "La fotosíntesis convierte energía luminosa en energía química.",
                  fullurl: "https://es.wikipedia.org/wiki/Fotos%C3%ADntesis",
                },
              },
            },
          });
        }
        return jsonResponse({
          query: { search: [{ title: "Fotosíntesis" }] },
        });
      }
      return jsonResponse({
        title: "Fotosíntesis",
        type: "standard",
        extract:
          "La fotosíntesis convierte energía luminosa en energía química.",
        content_urls: {
          desktop: { page: "https://es.wikipedia.org/wiki/Fotos%C3%ADntesis" },
        },
      });
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "¿y qué es la fotosíntesis?",
    deps,
    "Ultra, explícame qué es Vulkan",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected new-topic answer");
  if (searchQuery.toLowerCase() !== "la fotosíntesis") {
    throw new Error("expected current complete topic without previous context");
  }
});

Deno.test("technical troubleshooting falls back to Stack Overflow en español without API keys", async () => {
  const visited: string[] = [];
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      visited.push(url.toString());

      if (
        url.hostname === "api.stackexchange.com" &&
        url.pathname === "/2.3/search/advanced"
      ) {
        if (url.searchParams.get("site") !== "es.stackoverflow") {
          throw new Error("expected Stack Overflow en español");
        }
        return jsonResponse({
          items: [{
            question_id: 123,
            accepted_answer_id: 456,
            link: "https://es.stackoverflow.com/questions/123/ejemplo",
            title: "Error de Gradle al compilar Android",
          }],
        });
      }

      if (
        url.hostname === "api.stackexchange.com" &&
        url.pathname === "/2.3/answers/456"
      ) {
        return jsonResponse({
          items: [{
            answer_id: 456,
            score: 8,
            is_accepted: true,
            body:
              "<p>Revisa que la versión del plugin de Android sea compatible con la versión de Gradle y sincroniza el proyecto de nuevo.</p>",
            link: "https://es.stackoverflow.com/a/456",
          }],
        });
      }

      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, cómo soluciono un error de Gradle al compilar Android",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected technical answer");
  if (
    !result.displayText?.startsWith(
      "Según una respuesta aceptada de Stack Overflow en español:",
    )
  ) {
    throw new Error("expected attributed Spanish technical answer");
  }
  if (!result.displayText?.includes("Revisa que la versión")) {
    throw new Error("expected accepted answer excerpt");
  }
  if (
    !result.sourceIds?.some((source) => source.includes("es.stackoverflow.com"))
  ) {
    throw new Error("expected visible Stack Overflow source");
  }
  if (!visited.some((url) => url.includes("/2.3/search/advanced"))) {
    throw new Error("expected Stack Exchange search");
  }
});

Deno.test("news returns actual Spanish details from independent sources", async () => {
  const deps: ResearchDependencies = {
    fetcher: () =>
      jsonResponse({
        articles: [
          {
            title: "Resident Evil recibe una actualización importante",
            url: "https://fuente-uno.example/noticia",
            domain: "fuente-uno.example",
            seendate: "20260927T010000Z",
          },
          {
            title: "Nuevo parche mejora el rendimiento de Resident Evil",
            url: "https://fuente-dos.example/noticia",
            domain: "fuente-dos.example",
            seendate: "20260927T011000Z",
          },
        ],
      }),
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, noticias de Resident Evil",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (result.abstained) throw new Error("expected verified Spanish news");
  if (!result.displayText?.includes("actualización importante")) {
    throw new Error("expected first verified news detail");
  }
  if (!result.displayText?.includes("parche mejora el rendimiento")) {
    throw new Error("expected second verified news detail");
  }
});

Deno.test("news abstains when verified titles cannot be presented in Spanish", async () => {
  const deps: ResearchDependencies = {
    fetcher: () =>
      jsonResponse({
        articles: [
          {
            title: "Major game update released today",
            url: "https://source-one.example/news",
            domain: "source-one.example",
            seendate: "20260927T010000Z",
          },
          {
            title: "New patch changes performance",
            url: "https://source-two.example/news",
            domain: "source-two.example",
            seendate: "20260927T011000Z",
          },
        ],
      }),
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, noticias de Resident Evil",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (!result.abstained) {
    throw new Error(
      "English-only news must not be spoken by Spanish-only Ultra",
    );
  }
});

Deno.test("dependent knowledge follow-up keeps the previous subject", async () => {
  const queries: string[] = [];
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        queries.push(wikipediaSearchParam(url));
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }
      return jsonResponse({
        title: "Vulkan",
        type: "standard",
        extract: "Vulkan fue creado por el Grupo Khronos.",
        content_urls: {
          desktop: { page: "https://es.wikipedia.org/wiki/Vulkan" },
        },
      });
    },
    env: () => undefined,
  };

  await routeResearchQuery(
    "¿y quién lo creó?",
    deps,
    "Ultra, explícame qué es Vulkan",
    "GENERAL_KNOWLEDGE",
  );

  if (queries[0]?.toLowerCase() !== "vulkan") {
    throw new Error("expected previous subject for referential follow-up");
  }
});

Deno.test("dependent follow-up may add a qualifier without replacing its subject", async () => {
  let searchQuery = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        searchQuery = wikipediaSearchParam(url);
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }
      return jsonResponse({
        title: "Vulkan",
        type: "standard",
        extract: "Vulkan se usa en Android para gráficos de alto rendimiento.",
        content_urls: {
          desktop: { page: "https://es.wikipedia.org/wiki/Vulkan" },
        },
      });
    },
    env: () => undefined,
  };

  await routeResearchQuery(
    "¿y para qué sirve en Android?",
    deps,
    "Ultra, explícame qué es Vulkan",
    "GENERAL_KNOWLEDGE",
  );

  if (searchQuery.toLowerCase() !== "vulkan android") {
    throw new Error("expected previous subject plus current qualifier");
  }
});

Deno.test("complete new subject in a follow-up does not keep prior context", async () => {
  let searchQuery = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        searchQuery = wikipediaSearchParam(url);
        return jsonResponse({
          query: { search: [{ title: "Android" }] },
        });
      }
      return jsonResponse({
        title: "Android",
        type: "standard",
        extract: "Android es un sistema operativo móvil.",
        content_urls: {
          desktop: { page: "https://es.wikipedia.org/wiki/Android" },
        },
      });
    },
    env: () => undefined,
  };

  await routeResearchQuery(
    "¿y qué es Android?",
    deps,
    "Ultra, explícame qué es Vulkan",
    "GENERAL_KNOWLEDGE",
  );

  if (searchQuery.toLowerCase() !== "android") {
    throw new Error("expected the complete new subject only");
  }
});

Deno.test("speaker labels are stripped before assistant invocation in context", async () => {
  let searchQuery = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        searchQuery = wikipediaSearchParam(url);
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }
      return jsonResponse({
        title: "Vulkan",
        type: "standard",
        extract: "Vulkan es una API gráfica.",
        content_urls: {
          desktop: { page: "https://es.wikipedia.org/wiki/Vulkan" },
        },
      });
    },
    env: () => undefined,
  };

  await routeResearchQuery(
    "¿y para qué sirve?",
    deps,
    "Tú: Ultra, explícame qué es Vulkan",
    "GENERAL_KNOWLEDGE",
  );

  if (searchQuery.toLowerCase() !== "vulkan") {
    throw new Error("expected clean subject from labeled context");
  }
});

Deno.test("Gemini cannot extend verified evidence with unsupported claims", async () => {
  let geminiApiKey = "";
  let geminiPrompt = "";
  const deps: ResearchDependencies = {
    fetcher: (input, init) => {
      const url = new URL(String(input));

      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }

      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/page/summary/")
      ) {
        return jsonResponse({
          title: "Vulkan",
          type: "standard",
          extract: "Vulkan es una API gráfica de bajo nivel.",
          content_urls: {
            desktop: { page: "https://es.wikipedia.org/wiki/Vulkan" },
          },
        });
      }

      if (url.hostname === "generativelanguage.googleapis.com") {
        const headers = new Headers(init?.headers);
        geminiApiKey = headers.get("x-goog-api-key") ?? "";
        const request = JSON.parse(String(init?.body)) as {
          contents?: Array<{ parts?: Array<{ text?: string }> }>;
        };
        geminiPrompt = request.contents?.[0]?.parts?.[0]?.text ?? "";
        return jsonResponse({
          candidates: [{
            finishReason: "STOP",
            content: {
              parts: [{
                text:
                  "Vulkan es una API gráfica de bajo nivel que permite un control más directo del hardware gráfico.",
              }],
            },
          }],
        });
      }

      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "GEMINI_API_KEY" ? "gemini-test-key" : undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, qué es Vulkan",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected verified answer");
  if (geminiApiKey !== "gemini-test-key") {
    throw new Error("expected Gemini key in x-goog-api-key header");
  }
  if (!geminiPrompt.includes("Responde únicamente en español")) {
    throw new Error("expected Spanish-only synthesis instruction");
  }
  if (!geminiPrompt.includes("Vulkan es una API gráfica de bajo nivel.")) {
    throw new Error("expected verified evidence in Gemini prompt");
  }
  if (result.displayText !== "Vulkan es una API gráfica de bajo nivel.") {
    throw new Error("unsupported synthesis must not replace verified evidence");
  }
});

Deno.test("Gemini MAX_TOKENS preserves complete verified evidence", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));

      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }

      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/page/summary/")
      ) {
        return jsonResponse({
          title: "Vulkan",
          type: "standard",
          extract: "Vulkan es una API gráfica de bajo nivel.",
          content_urls: {
            desktop: { page: "https://es.wikipedia.org/wiki/Vulkan" },
          },
        });
      }

      if (url.hostname === "generativelanguage.googleapis.com") {
        return jsonResponse({
          candidates: [{
            finishReason: "MAX_TOKENS",
            content: {
              parts: [{ text: "Vulkan es una API gráfica de" }],
            },
          }],
        });
      }

      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "GEMINI_API_KEY" ? "gemini-test-key" : undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, qué es Vulkan",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.displayText !== "Vulkan es una API gráfica de bajo nivel.") {
    throw new Error("truncated Gemini output must preserve verified evidence");
  }
});

Deno.test("Gemini failure preserves verified provider answer", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));

      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        return jsonResponse({
          query: { search: [{ title: "Vulkan" }] },
        });
      }

      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/page/summary/")
      ) {
        return jsonResponse({
          title: "Vulkan",
          type: "standard",
          extract: "Vulkan es una API gráfica de bajo nivel.",
          content_urls: {
            desktop: { page: "https://es.wikipedia.org/wiki/Vulkan" },
          },
        });
      }

      if (url.hostname === "generativelanguage.googleapis.com") {
        return new Response("quota", { status: 429 });
      }

      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "GEMINI_API_KEY" ? "gemini-test-key" : undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, qué es Vulkan",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected verified fallback");
  if (result.displayText !== "Vulkan es una API gráfica de bajo nivel.") {
    throw new Error("expected original verified answer after Gemini failure");
  }
});

Deno.test("Spanish news title using preposition a is not rejected as English", async () => {
  const deps: ResearchDependencies = {
    fetcher: () =>
      jsonResponse({
        articles: [
          {
            title: "Llega a Resident Evil la update de rendimiento",
            url: "https://fuente-uno.example/noticia",
            domain: "fuente-uno.example",
            seendate: "20260927T020000Z",
          },
          {
            title: "Resident Evil recibe un nuevo parche en consolas",
            url: "https://fuente-dos.example/noticia",
            domain: "fuente-dos.example",
            seendate: "20260927T021000Z",
          },
        ],
      }),
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, noticias de Resident Evil",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (result.abstained) {
    throw new Error("expected both Spanish news sources to be accepted");
  }
  if (result.independentSourceCount !== 2) {
    throw new Error("expected two independent Spanish news sources");
  }
});

Deno.test("general knowledge falls back to Tavily when Wikipedia has no result", async () => {
  let tavilyAuthorization = "";
  let tavilyRequest: Record<string, unknown> = {};

  const deps: ResearchDependencies = {
    fetcher: (input, init) => {
      const url = new URL(String(input));

      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname === "/w/api.php"
      ) {
        return jsonResponse({ query: { search: [] } });
      }

      if (url.hostname === "api.tavily.com") {
        const headers = new Headers(init?.headers);
        tavilyAuthorization = headers.get("Authorization") ?? "";
        tavilyRequest = JSON.parse(String(init?.body)) as Record<
          string,
          unknown
        >;
        return jsonResponse({
          results: [
            {
              title: "Fuente uno",
              url: "https://fuente-uno.example/vulkan",
              content:
                "Vulkan es una API gráfica de bajo nivel para gráficos y cómputo.",
              score: 0.92,
            },
            {
              title: "Fuente dos",
              url: "https://fuente-dos.example/vulkan",
              content:
                "Vulkan permite un control más directo de la GPU y sus recursos.",
              score: 0.87,
            },
          ],
        });
      }

      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, explícame Vulkan",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected Tavily fallback evidence");
  if (tavilyAuthorization !== "Bearer tvly-test-key") {
    throw new Error("expected Tavily bearer authentication");
  }
  if (tavilyRequest.search_depth !== "basic") {
    throw new Error("expected credit-efficient Tavily basic search");
  }
  if (tavilyRequest.include_answer !== false) {
    throw new Error("Tavily answer must not replace grounded synthesis");
  }
  if (result.independentSourceCount !== 2) {
    throw new Error("expected two independent Tavily sources");
  }
  if (result.sourceIds?.length !== 2) {
    throw new Error("expected Tavily source URLs");
  }
});

Deno.test("unsupported current query falls back to Tavily web search", async () => {
  let tavilyCalled = false;
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.hostname !== "api.tavily.com") {
        throw new Error("unexpected URL " + url);
      }
      tavilyCalled = true;
      return jsonResponse({
        results: [
          {
            title: "Android Developers",
            url: "https://developer.android.com/about/versions",
            content:
              "Android Developers publica información de las versiones actuales.",
            score: 0.95,
          },
          {
            title: "Fuente tecnológica",
            url: "https://tecnologia.example/android-version",
            content:
              "La versión actual de Android se documenta junto con sus cambios.",
            score: 0.82,
          },
        ],
      });
    },
    env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, cuál es la versión actual de Android",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (!tavilyCalled) throw new Error("expected Tavily current-data fallback");
  if (result.abstained) throw new Error("expected current web evidence");
  if (result.sourceIds?.length !== 2) {
    throw new Error("expected current Tavily sources");
  }
});

Deno.test("general knowledge falls back to Gemini when verified sources are unavailable", async () => {
  let geminiCalled = false;
  const deps: ResearchDependencies = {
    fetcher: (input, init) => {
      const url = new URL(String(input));

      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        return jsonResponse({ query: { search: [] } });
      }

      if (url.hostname === "generativelanguage.googleapis.com") {
        geminiCalled = true;
        const body = JSON.parse(String(init?.body)) as {
          generationConfig?: Record<string, unknown>;
        };
        const generationConfig = body.generationConfig ?? {};
        if (generationConfig.maxOutputTokens !== 1500) {
          throw new Error(
            "Gemini general fallback needs output-token headroom",
          );
        }
        const thinkingConfig = generationConfig.thinkingConfig as
          | Record<string, unknown>
          | undefined;
        if (thinkingConfig?.thinkingLevel !== "minimal") {
          throw new Error(
            "Gemini general fallback must use minimal thinking",
          );
        }
        return jsonResponse({
          candidates: [{
            finishReason: "STOP",
            content: {
              parts: [{
                text:
                  "Los sentimientos son experiencias afectivas conscientes que surgen al interpretar emociones, pensamientos y situaciones.",
              }],
            },
          }],
        });
      }

      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "GEMINI_API_KEY" ? "gemini-test-key" : undefined,
  };

  const result = await routeResearchQuery(
    "Ultra, qué son los sentimientos",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (!geminiCalled) throw new Error("expected Gemini general fallback");
  if (result.abstained) throw new Error("expected a general assistant answer");
  if (result.authoritative !== false) {
    throw new Error("Gemini fallback must not be marked authoritative");
  }
  if (!result.displayText?.includes("experiencias afectivas")) {
    throw new Error("expected the Gemini fallback answer");
  }
});

Deno.test(
  "general knowledge survives Wikipedia network failure via Gemini fallback",
  async () => {
    let geminiCalled = false;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (url.hostname === "es.wikipedia.org") {
          throw new TypeError("simulated network failure");
        }
        if (url.hostname === "generativelanguage.googleapis.com") {
          geminiCalled = true;
          return jsonResponse({
            candidates: [{
              finishReason: "STOP",
              content: {
                parts: [{
                  text:
                    "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.",
                }],
              },
            }],
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "GEMINI_API_KEY" ? "gemini-test-key" : undefined,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!geminiCalled) {
      throw new Error(
        "expected Gemini fallback after Wikipedia failure",
      );
    }
    if (result.abstained) {
      throw new Error(
        "stable general knowledge must remain answerable",
      );
    }
    if (!result.displayText?.toLowerCase().includes("motor")) {
      throw new Error("expected a useful motor definition");
    }
  },
);

Deno.test(
  "general knowledge falls back to Wikipedia Action API when summary endpoint fails",
  async () => {
    let actionExtractCalled = false;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: {
              search: [{ title: "Motor" }],
            },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return new Response("unavailable", { status: 503 });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          (url.searchParams.get("prop") ?? "").includes("extracts")
        ) {
          actionExtractCalled = true;
          return jsonResponse({
            query: {
              pages: {
                "123": {
                  title: "Motor",
                  extract:
                    "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.",
                  canonicalurl: "https://es.wikipedia.org/wiki/Motor",
                },
              },
            },
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
      sleep: () => Promise.resolve(),
      random: () => 0,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!actionExtractCalled) {
      throw new Error("expected Wikipedia Action API fallback");
    }
    if (result.abstained) {
      throw new Error("stable definition must remain answerable");
    }
    if (!result.displayText?.toLowerCase().includes("motor")) {
      throw new Error("expected a motor definition from Action API");
    }
  },
);

Deno.test(
  "general knowledge corroborates Wikipedia with independent Tavily sources from Vault",
  async () => {
    let tavilyCalled = false;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Motor" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract:
              "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.",
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }

        if (url.hostname === "api.tavily.com") {
          tavilyCalled = true;
          return jsonResponse({
            results: [
              {
                title: "Britannica motor",
                url: "https://www.britannica.com/technology/motor",
                content:
                  "Un motor convierte energía en movimiento mecánico y trabajo útil.",
                score: 0.93,
              },
              {
                title: "Engineering reference",
                url: "https://engineering.example/motor",
                content:
                  "Los motores convierten distintas formas de energía en trabajo mecánico.",
                score: 0.89,
              },
            ],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
      secret: (name) =>
        Promise.resolve(
          name === "TAVILY_API_KEY" ? "vault-tvly-test-key" : undefined,
        ),
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!tavilyCalled) {
      throw new Error("expected Tavily corroboration for Wikipedia evidence");
    }
    if (result.abstained) {
      throw new Error("expected corroborated stable knowledge answer");
    }
    const sources = result.sourceIds ?? [];
    if (sources.length < 3) {
      throw new Error("expected Wikipedia plus independent web sources");
    }
    if ((result.independentSourceCount ?? 0) < 3) {
      throw new Error("expected three independent corroborating domains");
    }
    if (
      result.displayText !==
        "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico."
    ) {
      throw new Error(
        "verified Wikipedia wording should remain the safe answer",
      );
    }
  },
);

Deno.test(
  "Tavily numeric disagreement does not corroborate Wikipedia",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Marte" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Marte",
            type: "standard",
            extract: "Marte tiene 2 lunas naturales.",
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Marte" },
            },
          });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Astronomía uno",
                url: "https://astronomia-uno.example/marte",
                content: "Marte tiene 3 lunas naturales.",
                score: 0.95,
              },
              {
                title: "Astronomía dos",
                url: "https://astronomia-dos.example/marte",
                content: "El planeta Marte posee 3 lunas naturales.",
                score: 0.92,
              },
            ],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
      secret: (name) =>
        Promise.resolve(
          name === "TAVILY_API_KEY" ? "vault-tvly-test-key" : undefined,
        ),
    };

    const result = await routeResearchQuery(
      "Ultra, ¿cuántas lunas tiene Marte?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("Wikipedia evidence should remain usable");
    }
    if ((result.independentSourceCount ?? 0) !== 1) {
      throw new Error(
        "contradictory Tavily sources must not increase corroboration",
      );
    }
    if ((result.sourceIds ?? []).length !== 1) {
      throw new Error(
        "contradictory Tavily sources must not be exposed as supporting sources",
      );
    }
    if (!result.displayText?.includes("2 lunas")) {
      throw new Error("expected the primary Wikipedia fact to be preserved");
    }
  },
);

Deno.test(
  "stalled Gemini general fallback advances to xAI",
  async () => {
    let xaiCalled = false;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php"
        ) {
          return jsonResponse({ query: { search: [] } });
        }

        if (url.hostname === "generativelanguage.googleapis.com") {
          return new Promise<Response>(() => {});
        }

        if (url.hostname === "api.x.ai") {
          xaiCalled = true;
          return jsonResponse({
            choices: [{
              message: {
                content:
                  "Un motor es una máquina que convierte energía en movimiento o trabajo mecánico.",
              },
            }],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "GEMINI_API_KEY") return "gemini-test-key";
        if (name === "XAI_API_KEY") return "xai-test-key";
        if (name === "ULTRA_GENERAL_MODEL_TIMEOUT_MS") return "250";
        return undefined;
      },
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!xaiCalled) {
      throw new Error("xAI must run after the bounded Gemini fallback expires");
    }
    if (result.abstained || result.sourceId !== "xai-general-assistant") {
      throw new Error("xAI should recover the general-knowledge answer");
    }
  },
);

Deno.test(
  "stalled Gemini secret lookup advances to xAI",
  async () => {
    let xaiCalled = false;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php"
        ) {
          return jsonResponse({ query: { search: [] } });
        }

        if (url.hostname === "api.x.ai") {
          xaiCalled = true;
          return jsonResponse({
            choices: [{
              message: {
                content:
                  "La fotosíntesis transforma energía luminosa en energía química.",
              },
            }],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "XAI_API_KEY") return "xai-test-key";
        if (name === "ULTRA_GENERAL_MODEL_TIMEOUT_MS") return "250";
        return undefined;
      },
      secret: (name) => {
        if (name === "GEMINI_API_KEY") {
          return new Promise<string | undefined>(() => {});
        }
        return Promise.resolve(undefined);
      },
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es la fotosíntesis?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!xaiCalled) {
      throw new Error("xAI must run after a stalled Gemini secret lookup");
    }
    if (result.abstained || result.sourceId !== "xai-general-assistant") {
      throw new Error("xAI should answer after the Gemini secret deadline");
    }
  },
);

Deno.test(
  "stalled xAI general fallback returns a bounded abstention",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php"
        ) {
          return jsonResponse({ query: { search: [] } });
        }

        if (url.hostname === "api.x.ai") {
          return new Promise<Response>(() => {});
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "XAI_API_KEY") return "xai-test-key";
        if (name === "ULTRA_GENERAL_MODEL_TIMEOUT_MS") return "250";
        return undefined;
      },
    };

    const started = performance.now();
    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );
    const elapsed = performance.now() - started;

    if (!result.abstained) {
      throw new Error("stalled final general model should abstain");
    }
    if (elapsed >= 1_000) {
      throw new Error(
        "general-model fallback exceeded its bounded timeout: " + elapsed,
      );
    }
  },
);

Deno.test("xAI general caller timeout can exceed the default fetch attempt timeout", async () => {
  let aborted = false;
  const deps: ResearchDependencies = {
    fetcher: (input, init) => {
      const url = new URL(String(input));
      if (url.hostname === "es.wikipedia.org") {
        return jsonResponse({ query: { search: [] } });
      }
      if (url.hostname === "api.x.ai") {
        return new Promise<Response>((resolve, reject) => {
          const timer = setTimeout(() => {
            resolve(jsonResponse({
              choices: [{
                message: {
                  content: "Un motor convierte energía en trabajo mecánico.",
                },
              }],
            }));
          }, 2_600);
          init?.signal?.addEventListener("abort", () => {
            aborted = true;
            clearTimeout(timer);
            reject(new DOMException("aborted", "AbortError"));
          }, { once: true });
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: (name) => {
      if (name === "XAI_API_KEY") return "xai-test-key";
      if (name === "ULTRA_XAI_GENERAL_TIMEOUT_MS") return "3000";
      if (name === "ULTRA_FETCH_RETRY_BUDGET_MS") return "4000";
      return undefined;
    },
  };

  const result = await routeResearchQuery(
    "Ultra, ¿qué es un motor?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (aborted) {
    throw new Error(
      "default fetch timeout must not preempt the xAI caller timeout",
    );
  }
  if (result.abstained || result.sourceId !== "xai-general-assistant") {
    throw new Error(
      "xAI should be allowed to answer within the caller timeout",
    );
  }
});

Deno.test(
  "general knowledge falls back from unavailable Gemini to xAI Grok",
  async () => {
    let xaiCalled = false;
    const deps: ResearchDependencies = {
      fetcher: (input, init) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php"
        ) {
          return jsonResponse({ query: { search: [] } });
        }

        if (url.hostname === "api.x.ai") {
          xaiCalled = true;
          const headers = new Headers(init?.headers);
          if (headers.get("Authorization") !== "Bearer xai-test-key") {
            throw new Error("expected xAI bearer authentication");
          }
          const body = JSON.parse(String(init?.body)) as Record<
            string,
            unknown
          >;
          if (body.reasoning_effort !== "low") {
            throw new Error("xAI fallback must use low reasoning effort");
          }
          if (body.max_completion_tokens !== 1500) {
            throw new Error(
              "xAI fallback needs completion-token headroom for reasoning",
            );
          }
          if ("max_tokens" in body) {
            throw new Error("xAI fallback must not use legacy max_tokens");
          }
          return jsonResponse({
            choices: [{
              message: {
                content:
                  "Un motor es una máquina que convierte energía en movimiento o trabajo mecánico.",
              },
            }],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "XAI_API_KEY") return "xai-test-key";
        if (name === "ULTRA_XAI_SYNTHESIS_TIMEOUT_MS") return "500";
        return undefined;
      },
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!xaiCalled) {
      throw new Error(
        "expected xAI fallback when verified sources and Gemini are unavailable",
      );
    }
    if (result.abstained) {
      throw new Error("expected xAI to keep stable knowledge answerable");
    }
    if (result.sourceId !== "xai-general-assistant") {
      throw new Error("expected xAI provenance");
    }
    if (!result.displayText?.toLowerCase().includes("motor")) {
      throw new Error("expected a useful motor definition from xAI");
    }
  },
);

Deno.test(
  "verified evidence uses xAI as grounded synthesizer when Gemini is unavailable",
  async () => {
    let xaiCalled = false;
    let xaiRequestBody: Record<string, unknown> | null = null;
    const verifiedText =
      "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.";

    const deps: ResearchDependencies = {
      fetcher: (input, init) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Motor" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract: verifiedText,
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Britannica motor",
                url: "https://www.britannica.com/technology/motor",
                content:
                  "Un motor convierte energía en movimiento mecánico y trabajo útil.",
                score: 0.93,
              },
              {
                title: "Engineering reference",
                url: "https://engineering.example/motor",
                content:
                  "Los motores convierten distintas formas de energía en trabajo mecánico.",
                score: 0.89,
              },
            ],
          });
        }

        if (url.hostname === "api.x.ai") {
          xaiCalled = true;
          const headers = new Headers(init?.headers);
          if (headers.get("Authorization") !== "Bearer xai-test-key") {
            throw new Error("expected xAI bearer authentication");
          }

          const body = JSON.parse(String(init?.body)) as Record<
            string,
            unknown
          >;
          if (body.model !== "grok-4.7") {
            throw new Error("expected grok-4.7 grounded synthesis model");
          }
          xaiRequestBody = body;

          const serialized = JSON.stringify(body);
          if (!serialized.includes(verifiedText)) {
            throw new Error("xAI synthesis must receive verified evidence");
          }

          return jsonResponse({
            choices: [{
              message: {
                content: verifiedText,
              },
            }],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "XAI_API_KEY") return "xai-test-key";
        if (name === "TAVILY_API_KEY") return "tvly-test-key";
        return undefined;
      },
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!xaiCalled) {
      throw new Error(
        "expected xAI grounded synthesis when Gemini is unavailable",
      );
    }
    const capturedXaiRequest = xaiRequestBody as
      | Record<
        string,
        unknown
      >
      | null;
    if (!capturedXaiRequest) {
      throw new Error("expected grounded xAI request body");
    }
    if (capturedXaiRequest.reasoning_effort !== "low") {
      throw new Error("grounded xAI synthesis must use low reasoning effort");
    }
    if (capturedXaiRequest.max_completion_tokens !== 1500) {
      throw new Error(
        "grounded xAI synthesis needs completion-token headroom",
      );
    }
    if ("max_tokens" in capturedXaiRequest) {
      throw new Error(
        "grounded xAI synthesis must not use legacy max_tokens",
      );
    }
    if (result.abstained) {
      throw new Error("grounded xAI synthesis must preserve the answer");
    }
    if (result.displayText !== verifiedText) {
      throw new Error("xAI must not add claims outside verified evidence");
    }
    if ((result.sourceIds ?? []).length < 3) {
      throw new Error("grounded synthesis must preserve verified sources");
    }
  },
);

Deno.test(
  "vault lookup failure does not discard available Wikipedia evidence",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Motor" }] },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract:
              "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.",
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
      secret: () => Promise.reject(new Error("vault unavailable")),
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("Wikipedia evidence must survive optional Vault failure");
    }
    if (result.sourceId !== "https://es.wikipedia.org/wiki/Motor") {
      throw new Error("expected Wikipedia to remain the usable source");
    }
  },
);

Deno.test(
  "unrelated Tavily pages do not count as corroboration for Wikipedia",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Motor" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract:
              "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.",
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Pronóstico del tiempo",
                url: "https://weather.example/hoy",
                content:
                  "El pronóstico anuncia lluvia y bajas temperaturas durante la tarde.",
                score: 0.98,
              },
              {
                title: "Cuidados para gatos",
                url: "https://pets.example/gatos",
                content:
                  "Los gatos domésticos necesitan alimentación, agua y revisiones veterinarias.",
                score: 0.97,
              },
            ],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("Wikipedia answer should remain usable");
    }
    if ((result.independentSourceCount ?? 0) !== 1) {
      throw new Error("unrelated web pages must not increase corroboration");
    }
    if ((result.sourceIds ?? []).length !== 1) {
      throw new Error("unrelated Tavily URLs must not be attached as sources");
    }
    if (
      result.sourceId !== "https://es.wikipedia.org/wiki/Motor" ||
      result.sourceIds?.[0] !== "https://es.wikipedia.org/wiki/Motor"
    ) {
      throw new Error("Wikipedia must remain the sole retained source");
    }
  },
);

Deno.test(
  "contradictory Tavily snippets do not corroborate Wikipedia",
  async () => {
    const wikiSource = "https://es.wikipedia.org/wiki/Plut%C3%B3n";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Plutón" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Plutón",
            type: "standard",
            extract:
              "Plutón es un planeta enano del sistema solar situado más allá de Neptuno.",
            content_urls: { desktop: { page: wikiSource } },
          });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Clasificación de Plutón",
                url: "https://negative-a.example/pluton",
                content:
                  "Plutón no es un planeta enano del sistema solar según esta página.",
                score: 0.99,
              },
              {
                title: "Debate sobre Plutón",
                url: "https://negative-b.example/pluton",
                content:
                  "Esta fuente afirma que Plutón no es un planeta enano.",
                score: 0.98,
              },
            ],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es Plutón?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("Wikipedia evidence should remain usable");
    }
    if ((result.independentSourceCount ?? 0) !== 1) {
      throw new Error("contradictory snippets must not increase corroboration");
    }
    if (
      result.sourceId !== wikiSource ||
      result.sourceIds?.length !== 1 ||
      result.sourceIds[0] !== wikiSource
    ) {
      throw new Error("contradictory Tavily sources must not be retained");
    }
  },
);

Deno.test(
  "partial numeric overlap does not corroborate Wikipedia",
  async () => {
    const wikiSource = "https://es.wikipedia.org/wiki/Agua";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Agua" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Agua",
            type: "standard",
            extract:
              "El agua hierve a 100 grados y se congela a 0 grados en este ejemplo.",
            content_urls: { desktop: { page: wikiSource } },
          });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Propiedades del agua",
                url: "https://wrong-a.example/agua",
                content:
                  "El agua hierve a 90 grados y se congela a 0 grados en este ejemplo.",
                score: 0.99,
              },
              {
                title: "Referencia del agua",
                url: "https://wrong-b.example/agua",
                content: "El agua hierve a 90 grados y se congela a 0 grados.",
                score: 0.98,
              },
            ],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es el agua?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("Wikipedia evidence should remain usable");
    }
    if ((result.independentSourceCount ?? 0) !== 1) {
      throw new Error(
        "partial numeric overlap must not increase corroboration",
      );
    }
    if (
      result.sourceId !== wikiSource ||
      result.sourceIds?.length !== 1 ||
      result.sourceIds[0] !== wikiSource
    ) {
      throw new Error(
        "partially conflicting Tavily sources must not be retained",
      );
    }
  },
);

Deno.test(
  "opposite signed Tavily values do not corroborate Wikipedia",
  async () => {
    const wikiSource = "https://es.wikipedia.org/wiki/Marte";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Marte" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Marte",
            type: "standard",
            extract:
              "Marte usa una escala de referencia que registra -10 grados en este ejemplo.",
            content_urls: { desktop: { page: wikiSource } },
          });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Escala de Marte",
                url: "https://wrong-a.example/marte",
                content:
                  "Marte usa una escala de referencia que registra 10 grados en este ejemplo.",
                score: 0.99,
              },
              {
                title: "Referencia marciana",
                url: "https://wrong-b.example/marte",
                content: "La escala de referencia de Marte registra 10 grados.",
                score: 0.98,
              },
            ],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es Marte?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("Wikipedia evidence should remain usable");
    }
    if ((result.independentSourceCount ?? 0) !== 1) {
      throw new Error(
        "opposite signed values must not increase corroboration",
      );
    }
    if (
      result.sourceId !== wikiSource ||
      result.sourceIds?.length !== 1 ||
      result.sourceIds[0] !== wikiSource
    ) {
      throw new Error(
        "opposite signed Tavily sources must not be retained",
      );
    }
  },
);

Deno.test(
  "written quantity conflicts from Tavily do not corroborate Wikipedia",
  async () => {
    const wikiSource = "https://es.wikipedia.org/wiki/Marte";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Marte" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Marte",
            type: "standard",
            extract: "Marte tiene dos lunas conocidas, Fobos y Deimos.",
            content_urls: { desktop: { page: wikiSource } },
          });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Lunas de Marte",
                url: "https://wrong-a.example/marte",
                content: "Marte tiene tres lunas conocidas.",
                score: 0.99,
              },
              {
                title: "Satélites de Marte",
                url: "https://wrong-b.example/marte",
                content: "El planeta Marte posee tres lunas.",
                score: 0.98,
              },
            ],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿cuántas lunas tiene Marte?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("verified Wikipedia evidence should remain usable");
    }
    if ((result.independentSourceCount ?? 0) !== 1) {
      throw new Error(
        "written quantity conflicts must not increase corroboration",
      );
    }
    if (
      result.sourceId !== wikiSource ||
      result.sourceIds?.length !== 1 ||
      result.sourceIds[0] !== wikiSource
    ) {
      throw new Error(
        "conflicting Tavily sources with written quantities must be rejected",
      );
    }
  },
);

Deno.test(
  "unrelated Tavily fallback is rejected so Gemini can answer the topic",
  async () => {
    let geminiCalled = false;
    const expected =
      "Un motor es una máquina que transforma energía en movimiento o trabajo.";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({ query: { search: [] } });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Pronóstico del tiempo",
                url: "https://weather.example/hoy",
                content:
                  "La lluvia continuará durante la tarde con temperaturas bajas.",
                score: 0.99,
              },
              {
                title: "Cuidados para gatos",
                url: "https://pets.example/gatos",
                content:
                  "Los gatos necesitan agua, alimento y controles veterinarios.",
                score: 0.98,
              },
            ],
          });
        }

        if (url.hostname === "generativelanguage.googleapis.com") {
          geminiCalled = true;
          return jsonResponse({
            candidates: [{
              finishReason: "STOP",
              content: { parts: [{ text: expected }] },
            }],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "TAVILY_API_KEY") return "tvly-test-key";
        if (name === "GEMINI_API_KEY") return "gemini-test-key";
        return undefined;
      },
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!geminiCalled) {
      throw new Error(
        "Gemini general fallback should receive the rejected topic",
      );
    }
    if (result.abstained || result.displayText !== expected) {
      throw new Error(
        "Gemini should answer after irrelevant Tavily results are rejected",
      );
    }
    if (result.sourceId !== "gemini-general-assistant") {
      throw new Error(
        "irrelevant Tavily evidence must not become the final source",
      );
    }
  },
);

Deno.test(
  "question phrasing alone cannot make unrelated Tavily fallback relevant",
  async () => {
    let geminiCalled = false;
    const expected =
      "La fotosíntesis transforma energía luminosa en energía química.";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({ query: { search: [] } });
        }

        if (url.hostname === "api.tavily.com") {
          return jsonResponse({
            results: [
              {
                title: "Cómo funciona el clima",
                url: "https://weather.example/funciona",
                content:
                  "El clima funciona mediante interacciones atmosféricas y oceánicas.",
                score: 0.99,
              },
              {
                title: "Cómo funciona la economía",
                url: "https://economy.example/funciona",
                content:
                  "La economía funciona mediante producción, intercambio y consumo.",
                score: 0.98,
              },
            ],
          });
        }

        if (url.hostname === "generativelanguage.googleapis.com") {
          geminiCalled = true;
          return jsonResponse({
            candidates: [{
              finishReason: "STOP",
              content: { parts: [{ text: expected }] },
            }],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "TAVILY_API_KEY") return "tvly-test-key";
        if (name === "GEMINI_API_KEY") return "gemini-test-key";
        return undefined;
      },
    };

    const result = await routeResearchQuery(
      "Ultra, ¿cómo funciona la fotosíntesis?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!geminiCalled) {
      throw new Error(
        "question phrasing must not make unrelated Tavily pages relevant",
      );
    }
    if (result.abstained || result.sourceId !== "gemini-general-assistant") {
      throw new Error(
        "Gemini should answer after phrasing-only Tavily matches are rejected",
      );
    }
  },
);

Deno.test(
  "Wikipedia primary stage obeys shared route deadline and leaves time for Gemini",
  async () => {
    let wikipediaSawSignal = false;
    let geminiCalled = false;
    const expected =
      "El ADN es la molécula que almacena información genética en los seres vivos.";
    const deps: ResearchDependencies = {
      fetcher: (input, init) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          wikipediaSawSignal = Boolean(init?.signal);
          return new Promise<Response>((_, reject) => {
            init?.signal?.addEventListener(
              "abort",
              () => reject(new DOMException("aborted", "AbortError")),
              { once: true },
            );
          });
        }

        if (url.hostname === "generativelanguage.googleapis.com") {
          geminiCalled = true;
          return jsonResponse({
            candidates: [{
              finishReason: "STOP",
              content: { parts: [{ text: expected }] },
            }],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "GEMINI_API_KEY") return "gemini-test-key";
        if (name === "ULTRA_GENERAL_ROUTE_TIMEOUT_MS") return "900";
        if (name === "ULTRA_PRIMARY_EVIDENCE_TIMEOUT_MS") return "200";
        return undefined;
      },
    };

    const started = performance.now();
    const result = await routeResearchQuery(
      "Ultra, ¿qué es el ADN?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );
    const elapsed = performance.now() - started;

    if (!wikipediaSawSignal) {
      throw new Error("Wikipedia requests must receive an abort signal");
    }
    if (!geminiCalled) {
      throw new Error("Gemini should run with the remaining route budget");
    }
    if (result.abstained || result.displayText !== expected) {
      throw new Error(
        "Gemini should recover after the bounded Wikipedia stage",
      );
    }
    if (elapsed >= 900) {
      throw new Error("shared route deadline was exhausted before fallback");
    }
  },
);

Deno.test(
  "slow sole Tavily fallback gets more time than optional corroboration",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: async (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({ query: { search: [] } });
        }

        if (url.hostname === "api.tavily.com") {
          await new Promise((resolve) => setTimeout(resolve, 650));
          return jsonResponse({
            results: [
              {
                title: "Motor reference",
                url: "https://engineering.example/motor",
                content:
                  "Un motor transforma energía en movimiento y trabajo mecánico.",
                score: 0.94,
              },
              {
                title: "Mechanical reference",
                url: "https://physics.example/motor",
                content:
                  "Los motores convierten energía en trabajo mecánico y movimiento.",
                score: 0.91,
              },
            ],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error(
        "sole Tavily fallback should receive a full fallback budget",
      );
    }
    if (!result.sourceIds?.includes("https://engineering.example/motor")) {
      throw new Error(
        "expected Tavily evidence after the longer fallback wait",
      );
    }
  },
);

Deno.test(
  "verified Gemini synthesis uses latency tuned thinking budget",
  async () => {
    const verifiedText =
      "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.";
    let geminiCalled = false;
    const deps: ResearchDependencies = {
      fetcher: (input, init) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({ query: { search: [{ title: "Motor" }] } });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract: verifiedText,
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }
        if (url.hostname === "generativelanguage.googleapis.com") {
          geminiCalled = true;
          const body = JSON.parse(String(init?.body)) as {
            generationConfig?: Record<string, unknown>;
          };
          const generationConfig = body.generationConfig ?? {};
          if (generationConfig.maxOutputTokens !== 1500) {
            throw new Error(
              "Gemini synthesis needs output-token headroom",
            );
          }
          const thinkingConfig = generationConfig.thinkingConfig as
            | Record<string, unknown>
            | undefined;
          if (thinkingConfig?.thinkingLevel !== "minimal") {
            throw new Error(
              "Gemini synthesis must use minimal thinking",
            );
          }
          return jsonResponse({
            candidates: [{
              finishReason: "STOP",
              content: { parts: [{ text: verifiedText }] },
            }],
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "GEMINI_API_KEY" ? "gemini-test-key" : undefined,
    };

    const result = await routeResearchQuery(
      "Ultra, ¿qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (!geminiCalled) {
      throw new Error("expected grounded Gemini synthesis");
    }
    if (result.abstained || result.displayText !== verifiedText) {
      throw new Error("Gemini synthesis must preserve verified evidence");
    }
  },
);

Deno.test(
  "stalled Gemini optional synthesis returns verified evidence promptly",
  async () => {
    const verifiedText =
      "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({ query: { search: [{ title: "Motor" }] } });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract: verifiedText,
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }
        if (url.hostname === "generativelanguage.googleapis.com") {
          return new Promise<Response>(() => {});
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "GEMINI_API_KEY" ? "gemini-test-key" : undefined,
    };

    let guardTimer: number | undefined;
    const guard = new Promise<never>((_, reject) => {
      guardTimer = setTimeout(
        () =>
          reject(
            new Error("stalled Gemini synthesis blocked verified evidence"),
          ),
        1_300,
      );
    });

    let result;
    try {
      result = await Promise.race([
        routeResearchQuery(
          "Ultra, ¿qué es un motor?",
          deps,
          "",
          "GENERAL_KNOWLEDGE",
        ),
        guard,
      ]);
    } finally {
      if (guardTimer !== undefined) clearTimeout(guardTimer);
    }

    if (result.abstained || result.displayText !== verifiedText) {
      throw new Error(
        "verified Wikipedia evidence must survive stalled Gemini synthesis",
      );
    }
  },
);

Deno.test(
  "stalled xAI optional synthesis returns verified evidence promptly",
  async () => {
    const verifiedText =
      "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({ query: { search: [{ title: "Motor" }] } });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract: verifiedText,
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }
        if (url.hostname === "api.x.ai") {
          return new Promise<Response>(() => {});
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "XAI_API_KEY" ? "xai-test-key" : undefined,
    };

    let guardTimer: number | undefined;
    const guard = new Promise<never>((_, reject) => {
      guardTimer = setTimeout(
        () =>
          reject(new Error("stalled xAI synthesis blocked verified evidence")),
        1_300,
      );
    });

    let result;
    try {
      result = await Promise.race([
        routeResearchQuery(
          "Ultra, ¿qué es un motor?",
          deps,
          "",
          "GENERAL_KNOWLEDGE",
        ),
        guard,
      ]);
    } finally {
      if (guardTimer !== undefined) clearTimeout(guardTimer);
    }

    if (result.abstained || result.displayText !== verifiedText) {
      throw new Error(
        "verified Wikipedia evidence must survive stalled xAI synthesis",
      );
    }
  },
);

Deno.test(
  "stalled Tavily after Wikipedia abstention cannot block Gemini fallback",
  async () => {
    const expected =
      "La fotosíntesis es el proceso por el que organismos convierten energía luminosa en energía química.";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({ query: { search: [] } });
        }

        if (url.hostname === "api.tavily.com") {
          return new Promise<Response>(() => {});
        }

        if (url.hostname === "generativelanguage.googleapis.com") {
          return jsonResponse({
            candidates: [{
              finishReason: "STOP",
              content: { parts: [{ text: expected }] },
            }],
          });
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "TAVILY_API_KEY") return "tvly-test-key";
        if (name === "GEMINI_API_KEY") return "gemini-test-key";
        if (name === "ULTRA_FALLBACK_SEARCH_TIMEOUT_MS") return "500";
        return undefined;
      },
    };

    let guardTimer: number | undefined;
    const guard = new Promise<never>((_, reject) => {
      guardTimer = setTimeout(
        () => reject(new Error("stalled Tavily blocked Gemini fallback")),
        1_000,
      );
    });

    let result;
    try {
      result = await Promise.race([
        routeResearchQuery(
          "Ultra, ¿qué es la fotosíntesis?",
          deps,
          "",
          "GENERAL_KNOWLEDGE",
        ),
        guard,
      ]);
    } finally {
      if (guardTimer !== undefined) clearTimeout(guardTimer);
    }

    if (result.abstained) {
      throw new Error(
        "Gemini fallback should answer after bounded Tavily wait",
      );
    }
    if (result.displayText !== expected) {
      throw new Error("expected Gemini fallback answer");
    }
  },
);

Deno.test(
  "slow optional Tavily corroboration cannot hold a ready Wikipedia answer",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Motor" }] },
          });
        }

        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract:
              "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.",
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }

        if (url.hostname === "api.tavily.com") {
          return new Promise<Response>(() => {});
        }

        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "TAVILY_API_KEY" ? "tvly-test-key" : undefined,
    };

    let guardTimer: number | undefined;
    const guard = new Promise<never>((_, reject) => {
      guardTimer = setTimeout(
        () => reject(new Error("optional Tavily blocked the ready answer")),
        700,
      );
    });

    let result;
    try {
      result = await Promise.race([
        routeResearchQuery(
          "Ultra, ¿qué es un motor?",
          deps,
          "",
          "GENERAL_KNOWLEDGE",
        ),
        guard,
      ]);
    } finally {
      if (guardTimer !== undefined) clearTimeout(guardTimer);
    }

    if (result.abstained) {
      throw new Error("ready Wikipedia evidence should be returned");
    }
    if (result.sourceId !== "https://es.wikipedia.org/wiki/Motor") {
      throw new Error("expected the ready Wikipedia answer");
    }
  },
);

Deno.test("Gemini 2.5 requests use thinkingBudget instead of thinkingLevel", async () => {
  let observedThinking: Record<string, unknown> | undefined;
  const deps = {
    fetcher: (_url: URL | Request | string, init?: RequestInit) => {
      const request = JSON.parse(String(init?.body ?? "{}")) as {
        generationConfig?: { thinkingConfig?: Record<string, unknown> };
      };
      observedThinking = request.generationConfig?.thinkingConfig;
      return new Response(
        JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: "Un motor transforma energía en trabajo mecánico.",
              }],
            },
          }],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    },
    env: (name: string) => {
      if (name === "GEMINI_API_KEY") return "gemini-test-key";
      if (name === "GEMINI_MODEL") return "gemini-2.5-flash";
      return undefined;
    },
  };

  await routeResearchQuery(
    "¿Qué es un motor?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (!observedThinking || observedThinking.thinkingBudget === undefined) {
    throw new Error("Gemini 2.5 must use thinkingBudget");
  }
  if ("thinkingLevel" in observedThinking) {
    throw new Error("Gemini 2.5 must not send thinkingLevel");
  }
});

Deno.test("general model caller timeout can exceed the default fetch attempt timeout", async () => {
  let aborted = false;
  const deps: ResearchDependencies = {
    fetcher: (input, init) => {
      const url = new URL(String(input));
      if (url.hostname === "es.wikipedia.org") {
        return jsonResponse({ query: { search: [] } });
      }
      if (url.hostname === "generativelanguage.googleapis.com") {
        return new Promise<Response>((resolve, reject) => {
          const timer = setTimeout(() => {
            resolve(jsonResponse({
              candidates: [{
                finishReason: "STOP",
                content: {
                  parts: [{
                    text: "Un motor convierte energía en trabajo mecánico.",
                  }],
                },
              }],
            }));
          }, 2_600);
          init?.signal?.addEventListener("abort", () => {
            aborted = true;
            clearTimeout(timer);
            reject(new DOMException("aborted", "AbortError"));
          }, { once: true });
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: (name) => {
      if (name === "GEMINI_API_KEY") return "gemini-test-key";
      if (name === "ULTRA_GENERAL_MODEL_TIMEOUT_MS") return "3000";
      if (name === "ULTRA_FETCH_RETRY_BUDGET_MS") return "4000";
      return undefined;
    },
  };

  const result = await routeResearchQuery(
    "Ultra, ¿qué es un motor?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (aborted) {
    throw new Error(
      "default fetch timeout must not preempt the caller timeout",
    );
  }
  if (result.abstained || result.sourceId !== "gemini-general-assistant") {
    throw new Error(
      "Gemini should be allowed to answer within the caller timeout",
    );
  }
});

Deno.test("retrying upstream requests respect a bounded attempt timeout", async () => {
  let attempts = 0;
  const deps: ResearchDependencies = {
    fetcher: (_input, init) => {
      attempts += 1;
      return new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener(
          "abort",
          () => reject(new DOMException("aborted", "AbortError")),
          { once: true },
        );
      });
    },
    env: (name) => {
      if (name === "ULTRA_FETCH_ATTEMPT_TIMEOUT_MS") return "40";
      if (name === "ULTRA_FETCH_RETRY_BUDGET_MS") return "90";
      return undefined;
    },
    sleep: () => Promise.resolve(),
    random: () => 0,
  };

  const startedAt = performance.now();
  const result = await routeResearchQuery(
    "Ultra, clima de hoy en Santiago",
    deps,
    "",
    "CURRENT_DATA",
  );
  const elapsed = performance.now() - startedAt;

  if (!result.abstained) {
    throw new Error("timed out weather lookup must abstain");
  }
  if (attempts > 3) {
    throw new Error("retry attempts exceeded the configured cap");
  }
  if (elapsed > 350) {
    throw new Error("retry budget did not bound the stalled upstream request");
  }
});

Deno.test("weather falls back to independently sourced web evidence when Open-Meteo fails", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.hostname.includes("open-meteo.com")) {
        return new Response("upstream failure", { status: 400 });
      }
      if (url.hostname === "api.tavily.com") {
        return jsonResponse({
          results: [
            {
              title: "Clima en Talca hoy",
              url: "https://weather-one.example/talca",
              content:
                "El clima en Talca hoy registra 18 grados y cielo despejado.",
              score: 0.9,
            },
            {
              title: "Tiempo actual en Talca",
              url: "https://weather-two.example/talca",
              content:
                "Talca registra hoy 18 grados con condiciones despejadas.",
              score: 0.8,
            },
          ],
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "TAVILY_API_KEY" ? "test-key" : undefined,
    secret: (name) =>
      Promise.resolve(name === "TAVILY_API_KEY" ? "test-key" : undefined),
  };

  const result = await routeResearchQuery(
    "Temperatura de hoy en Talca",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (result.abstained) throw new Error("expected verified weather fallback");
  if ((result.independentSourceCount ?? 0) < 2) {
    throw new Error(
      "weather fallback must retain independent-source verification",
    );
  }
});

Deno.test("news falls back to independently sourced web evidence when GDELT is insufficient", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.hostname === "api.gdeltproject.org") {
        return jsonResponse({ articles: [] });
      }
      if (url.hostname === "api.tavily.com") {
        return jsonResponse({
          results: [
            {
              title: "Novedades de Android",
              url: "https://news-one.example/android",
              content:
                "Android recibe hoy una nueva actualización con mejoras de seguridad.",
              score: 0.9,
            },
            {
              title: "Actualización de Android",
              url: "https://news-two.example/android",
              content:
                "La actualización de Android añade nuevas mejoras de seguridad.",
              score: 0.8,
            },
          ],
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "TAVILY_API_KEY" ? "test-key" : undefined,
    secret: (name) =>
      Promise.resolve(name === "TAVILY_API_KEY" ? "test-key" : undefined),
  };

  const result = await routeResearchQuery(
    "Noticias actuales sobre Android",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (result.abstained) throw new Error("expected verified news fallback");
  if ((result.independentSourceCount ?? 0) < 2) {
    throw new Error(
      "news fallback must retain independent-source verification",
    );
  }
});

Deno.test("purpose-form general knowledge queries normalize leading articles before Wikipedia search", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        const search = wikipediaSearchParam(url);
        if (search !== "sistema operativo") {
          return jsonResponse({ query: { search: [] } });
        }
        return jsonResponse({
          query: { search: [{ title: "Sistema operativo" }] },
        });
      }
      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/api/rest_v1/page/summary/")
      ) {
        return jsonResponse({
          extract:
            "Un sistema operativo administra el hardware y los recursos de un dispositivo.",
          content_urls: {
            desktop: {
              page: "https://es.wikipedia.org/wiki/Sistema_operativo",
            },
          },
        });
      }
      if (url.hostname === "api.tavily.com") {
        return jsonResponse({ results: [] });
      }
      if (url.hostname === "generativelanguage.googleapis.com") {
        throw new Error(
          "Gemini must not be needed for this stable knowledge query",
        );
      }
      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "TAVILY_API_KEY" ? "test-key" : undefined,
    secret: (name) =>
      Promise.resolve(name === "TAVILY_API_KEY" ? "test-key" : undefined),
  };

  const result = await routeResearchQuery(
    "¿Para qué sirve un sistema operativo?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) {
    throw new Error(
      "expected Wikipedia-backed answer for normalized purpose query",
    );
  }
  if (!result.sourceIds?.some((source) => source.includes("wikipedia.org"))) {
    throw new Error("expected Wikipedia evidence without model fallback");
  }
});

Deno.test("mAh stable knowledge resolves through the canonical ampere-hour topic without model keys", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname === "/w/api.php"
      ) {
        const search = wikipediaSearchParam(url);
        if (search !== "Amperio-hora") {
          return jsonResponse({ query: { search: [] } });
        }
        return jsonResponse({
          query: {
            pages: {
              "1": {
                pageid: 1,
                index: 1,
                title: "Amperio-hora",
                extract:
                  "El amperio-hora es una unidad de carga eléctrica. El miliamperio-hora, mAh, equivale a una milésima de amperio-hora y se usa habitualmente para expresar la capacidad de baterías.",
                canonicalurl: "https://es.wikipedia.org/wiki/Amperio-hora",
              },
            },
          },
        });
      }
      if (url.hostname === "api.tavily.com") {
        return jsonResponse({ results: [] });
      }
      if (
        url.hostname === "generativelanguage.googleapis.com" ||
        url.hostname === "api.x.ai"
      ) {
        throw new Error("model fallback must not be needed for mAh");
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "¿Qué significa mAh en una batería?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) {
    throw new Error(
      "expected stable mAh knowledge to resolve without a configured model",
    );
  }
  if (!result.sourceIds?.some((source) => source.includes("wikipedia.org"))) {
    throw new Error("expected canonical Wikipedia evidence for mAh");
  }
});

Deno.test("weather uses a second authoritative provider when Open-Meteo is unavailable", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.hostname.includes("open-meteo.com")) {
        return new Response("upstream failure", { status: 503 });
      }
      if (url.hostname === "nominatim.openstreetmap.org") {
        return jsonResponse([
          {
            lat: "-33.0472",
            lon: "-71.6127",
            display_name: "Valparaíso, Chile",
          },
        ]);
      }
      if (url.hostname === "api.met.no") {
        return jsonResponse({
          properties: {
            timeseries: [{
              time: "2026-10-04T20:00:00Z",
              data: {
                instant: { details: { air_temperature: 14.2 } },
                next_1_hours: { summary: { symbol_code: "partlycloudy_day" } },
              },
            }],
          },
        });
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "¿Qué tiempo hace ahora en Valparaíso?",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (result.abstained) {
    throw new Error("expected authoritative weather fallback");
  }
  if (result.authoritative !== true) {
    throw new Error("secondary weather provider must remain authoritative");
  }
  if (!result.sourceIds?.some((source) => source.includes("api.met.no"))) {
    throw new Error("expected MET Norway as weather fallback source");
  }
});

Deno.test("news uses independent Google News publishers when GDELT and Tavily are unavailable", async () => {
  const rss = '<?xml version="1.0"?><rss><channel>' +
    '<item><title>Android recibe una actualización importante</title><link>https://news.google.com/rss/articles/android-one</link><pubDate>Sun, 04 Oct 2026 18:00:00 GMT</pubDate><source url="https://tecnologia.example">Tecnología Uno</source></item>' +
    '<item><title>Nuevas funciones llegan a Android</title><link>https://news.google.com/rss/articles/android-two</link><pubDate>Sun, 04 Oct 2026 17:30:00 GMT</pubDate><source url="https://moviles.example">Móviles Dos</source></item>' +
    "</channel></rss>";

  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.hostname === "api.gdeltproject.org") {
        return jsonResponse({ articles: [] });
      }
      if (url.hostname === "news.google.com") {
        return new Response(rss, {
          status: 200,
          headers: { "Content-Type": "application/rss+xml" },
        });
      }
      if (url.hostname === "api.tavily.com") {
        return new Response("plan unavailable", { status: 432 });
      }
      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "TAVILY_API_KEY" ? "test-key" : undefined,
    secret: (name) =>
      Promise.resolve(name === "TAVILY_API_KEY" ? "test-key" : undefined),
  };

  const result = await routeResearchQuery(
    "¿Qué noticias recientes hay sobre Android?",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (result.abstained) throw new Error("expected Google News fallback");
  if ((result.independentSourceCount ?? 0) < 2) {
    throw new Error("news fallback must keep two independent publishers");
  }
  if (
    !result.sourceIds?.every((source) => source.includes("news.google.com"))
  ) {
    throw new Error("expected article-level Google News links as citations");
  }
});

Deno.test("Tavily retries a compatibility payload after a request-shape rejection", async () => {
  let tavilyCalls = 0;
  const deps: ResearchDependencies = {
    fetcher: (input, init) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        return jsonResponse({ query: { search: [] } });
      }
      if (url.hostname === "api.tavily.com") {
        tavilyCalls += 1;
        const body = JSON.parse(String(init?.body ?? "{}"));
        if (tavilyCalls === 1) {
          if (body.filter_by_language !== true) {
            throw new Error(
              "first Tavily request should use the preferred payload",
            );
          }
          return new Response("bad request", { status: 400 });
        }
        if ("filter_by_language" in body || "language" in body) {
          throw new Error(
            "compatibility retry must remove language-only fields",
          );
        }
        return jsonResponse({
          results: [
            {
              title: "Motor eléctrico explicado",
              url: "https://source-one.example/motor",
              content:
                "Un motor eléctrico convierte energía eléctrica en movimiento mecánico.",
              score: 0.9,
            },
            {
              title: "Cómo funciona un motor eléctrico",
              url: "https://source-two.example/motor",
              content:
                "El motor eléctrico transforma energía eléctrica en energía mecánica.",
              score: 0.8,
            },
          ],
        });
      }
      if (url.hostname === "generativelanguage.googleapis.com") {
        throw new Error(
          "Gemini must not be needed after Tavily compatibility retry",
        );
      }
      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "TAVILY_API_KEY" ? "test-key" : undefined,
    secret: (name) =>
      Promise.resolve(name === "TAVILY_API_KEY" ? "test-key" : undefined),
  };

  const result = await routeResearchQuery(
    "¿Qué es un motor eléctrico?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) {
    throw new Error("expected compatible Tavily fallback after 400");
  }
  if (tavilyCalls !== 2) {
    throw new Error(
      "expected one preferred request and one compatibility retry",
    );
  }
});

Deno.test("temperature de hoy phrasing routes to the authoritative weather provider", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.hostname === "geocoding-api.open-meteo.com") {
        return jsonResponse({
          results: [{
            name: "Talca",
            admin1: "Maule",
            country: "Chile",
            latitude: -35.4264,
            longitude: -71.6554,
          }],
        });
      }
      if (url.hostname === "api.open-meteo.com") {
        return jsonResponse({
          current: {
            temperature_2m: 18,
            apparent_temperature: 18,
            weather_code: 0,
            time: "2026-10-04T17:00",
          },
        });
      }
      if (url.hostname === "api.tavily.com") {
        throw new Error("weather phrasing must not fall through to Tavily");
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Temperatura de hoy en Talca",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (result.abstained) throw new Error("expected weather answer");
  if (!result.sourceIds?.some((source) => source.includes("open-meteo.com"))) {
    throw new Error("expected authoritative Open-Meteo evidence");
  }
});

Deno.test("conversational news recency phrases use compact news topics without Tavily", async () => {
  const rss = '<?xml version="1.0"?><rss><channel>' +
    '<item><title>Novedad tecnológica uno</title><link>https://news.google.com/rss/articles/one</link><pubDate>Sun, 04 Oct 2026 18:00:00 GMT</pubDate><source url="https://medio-uno.example">Medio Uno</source></item>' +
    '<item><title>Novedad tecnológica dos</title><link>https://news.google.com/rss/articles/two</link><pubDate>Sun, 04 Oct 2026 17:00:00 GMT</pubDate><source url="https://medio-dos.example">Medio Dos</source></item>' +
    "</channel></rss>";
  let expectedTopic = "";

  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.hostname === "api.gdeltproject.org") {
        if (url.searchParams.get("query") !== expectedTopic) {
          return jsonResponse({ articles: [] });
        }
        return jsonResponse({ articles: [] });
      }
      if (url.hostname === "news.google.com") {
        if (url.searchParams.get("q") !== expectedTopic) {
          throw new Error("news provider received an unnormalized topic");
        }
        return new Response(rss, {
          status: 200,
          headers: { "Content-Type": "application/rss+xml" },
        });
      }
      if (url.hostname === "api.tavily.com") {
        throw new Error(
          "news recency phrasing must not fall through to Tavily",
        );
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  for (
    const [query, topic] of [
      [
        "¿Qué novedades hay hoy sobre inteligencia artificial?",
        "inteligencia artificial",
      ],
      ["¿Qué ha pasado recientemente en tecnología?", "tecnología"],
    ]
  ) {
    expectedTopic = topic;
    const result = await routeResearchQuery(
      query,
      deps,
      "",
      "CURRENT_DATA",
    );
    if (result.abstained) {
      throw new Error(
        "expected news answer for conversational recency phrasing",
      );
    }
    if ((result.independentSourceCount ?? 0) < 2) {
      throw new Error("expected two independent news publishers");
    }
  }
});

Deno.test("what-does-it-do phrasing normalizes to the stable encyclopedia topic", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        const search = wikipediaSearchParam(url);
        return search === "procesador"
          ? jsonResponse({
            query: { search: [{ title: "Unidad central de procesamiento" }] },
          })
          : jsonResponse({ query: { search: [] } });
      }
      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/api/rest_v1/page/summary/")
      ) {
        return jsonResponse({
          extract:
            "La unidad central de procesamiento ejecuta instrucciones y procesa datos.",
          content_urls: {
            desktop: {
              page:
                "https://es.wikipedia.org/wiki/Unidad_central_de_procesamiento",
            },
          },
        });
      }
      if (url.hostname === "api.tavily.com") {
        throw new Error("stable encyclopedia topic must not require Tavily");
      }
      if (url.hostname === "generativelanguage.googleapis.com") {
        throw new Error("stable encyclopedia topic must not require Gemini");
      }
      throw new Error("unexpected URL " + url);
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Explícame qué hace un procesador",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected encyclopedia answer");
  if (!result.sourceIds?.some((source) => source.includes("wikipedia.org"))) {
    throw new Error("expected Wikipedia evidence");
  }
});

Deno.test("stable definition mislabeled as current data recovers through general knowledge", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        if (wikipediaSearchParam(url) !== "un motor") {
          return jsonResponse({ query: { search: [] } });
        }
        return jsonResponse({
          query: { search: [{ title: "Motor" }] },
        });
      }
      if (
        url.hostname === "es.wikipedia.org" &&
        url.pathname.includes("/api/rest_v1/page/summary/")
      ) {
        return jsonResponse({
          extract:
            "Un motor es una máquina que transforma energía en trabajo mecánico.",
          content_urls: {
            desktop: {
              page: "https://es.wikipedia.org/wiki/Motor",
            },
          },
        });
      }
      if (url.hostname === "api.tavily.com") {
        return new Response("bad request", { status: 400 });
      }
      throw new Error("unexpected URL " + url);
    },
    env: (name) => name === "TAVILY_API_KEY" ? "test-key" : undefined,
    secret: (name) =>
      Promise.resolve(name === "TAVILY_API_KEY" ? "test-key" : undefined),
  };

  const result = await routeResearchQuery(
    "Qué es un motor",
    deps,
    "",
    "CURRENT_DATA",
  );

  if (result.abstained) {
    throw new Error(
      "stable definition should recover from a stale client kind",
    );
  }
  if (!result.sourceIds?.some((source) => source.includes("wikipedia.org"))) {
    throw new Error("expected general-knowledge evidence from Wikipedia");
  }
});

Deno.test(
  "runtime-generated knowledge phrasing is normalized to the actual topic",
  async () => {
    const observedSearches: string[] = [];
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
        ) {
          const search = wikipediaSearchParam(url);
          observedSearches.push(search);
          if (search !== "la erosión") {
            return jsonResponse({ query: { search: [] } });
          }
          return jsonResponse({
            query: { search: [{ title: "Erosión" }] },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            extract:
              "La erosión es el desgaste y transporte de suelo y roca por agentes naturales.",
            content_urls: {
              desktop: {
                page: "https://es.wikipedia.org/wiki/Erosi%C3%B3n",
              },
            },
          });
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
    };

    const result = await routeResearchQuery(
      "Dame una explicación clara de la erosión y su función principal.",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("expected correctly normalized encyclopedia result");
    }
    if (observedSearches[0] !== "la erosión") {
      throw new Error(
        "dynamic prompt wrapper leaked into Wikipedia search: " +
          observedSearches[0],
      );
    }
    if (!result.displayText?.toLowerCase().includes("erosión")) {
      throw new Error("expected erosion answer");
    }
  },
);

Deno.test(
  "irrelevant Wikipedia candidate is rejected before it can answer the user",
  async () => {
    let tavilyCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Primer viaje de James Cook" }] },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            extract:
              "El primer viaje de James Cook fue una expedición por el océano Pacífico.",
            content_urls: {
              desktop: {
                page:
                  "https://es.wikipedia.org/wiki/Primer_viaje_de_James_Cook",
              },
            },
          });
        }
        if (url.hostname === "api.tavily.com") {
          tavilyCalls += 1;
          return jsonResponse({
            results: [
              {
                title: "Erosión del suelo",
                url: "https://science-one.example/erosion",
                content:
                  "La erosión desgasta y transporta partículas de suelo y roca mediante agua, viento u otros agentes.",
                score: 0.95,
              },
              {
                title: "Qué es la erosión",
                url: "https://science-two.example/erosion",
                content:
                  "La erosión es un proceso de desgaste del suelo y las rocas y su posterior transporte.",
                score: 0.9,
              },
            ],
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: (name) => name === "TAVILY_API_KEY" ? "test-key" : undefined,
      secret: (name) =>
        Promise.resolve(name === "TAVILY_API_KEY" ? "test-key" : undefined),
    };

    const result = await routeResearchQuery(
      "Dame una explicación clara de la erosión y su función principal.",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("expected relevant Tavily fallback");
    }
    if (tavilyCalls === 0) {
      throw new Error("irrelevant Wikipedia result must force a fallback");
    }
    const answer = result.displayText?.toLowerCase() ?? "";
    if (!answer.includes("eros")) {
      throw new Error("expected answer about erosion");
    }
    if (answer.includes("james cook")) {
      throw new Error("irrelevant Wikipedia answer leaked to the user");
    }
  },
);

Deno.test(
  "general knowledge skips an irrelevant Wikipedia result and uses a later relevant candidate",
  async () => {
    const summaries: string[] = [];
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: {
              search: [
                { title: "Crucifixión de Jesús" },
                { title: "Yeso" },
              ],
            },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          const title = decodeURIComponent(
            url.pathname.split("/").pop() ?? "",
          ).replaceAll("_", " ");
          summaries.push(title);
          if (title.includes("Crucifixión")) {
            return jsonResponse({
              title: "Crucifixión de Jesús",
              type: "standard",
              extract:
                "La crucifixión de Jesús ocurrió en Judea durante el siglo I.",
              content_urls: {
                desktop: {
                  page:
                    "https://es.wikipedia.org/wiki/Crucifixi%C3%B3n_de_Jes%C3%BAs",
                },
              },
            });
          }
          return jsonResponse({
            title: "Yeso",
            type: "standard",
            extract:
              "El yeso es un material usado en construcción para revestimientos, tabiques y acabados.",
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Yeso" },
            },
          });
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
    };

    const result = await routeResearchQuery(
      "Si alguien me pregunta por el yeso en construcción, ¿cómo lo explicarías en pocas frases?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) throw new Error("expected relevant Wikipedia answer");
    if (!result.displayText?.toLowerCase().includes("yeso")) {
      throw new Error("expected answer about yeso");
    }
    if (result.displayText?.toLowerCase().includes("crucifix")) {
      throw new Error("irrelevant first search result leaked");
    }
    if (summaries.length < 2) {
      throw new Error("expected the resolver to inspect a later candidate");
    }
  },
);

Deno.test(
  "multi-token knowledge topics require strong relevance instead of one generic overlap",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: {
              search: [
                { title: "High Frame Rate" },
                { title: "Frecuencia de imagen en videojuegos" },
              ],
            },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          const title = decodeURIComponent(
            url.pathname.split("/").pop() ?? "",
          ).replaceAll("_", " ");
          if (title.includes("High Frame Rate")) {
            return jsonResponse({
              title: "High Frame Rate",
              type: "standard",
              extract:
                "High Frame Rate es una técnica de proyección cinematográfica a más de 24 fps.",
              content_urls: {
                desktop: {
                  page: "https://es.wikipedia.org/wiki/High_Frame_Rate",
                },
              },
            });
          }
          return jsonResponse({
            title: "Frecuencia de imagen en videojuegos",
            type: "standard",
            extract:
              "En videojuegos, FPS suele referirse a fotogramas por segundo y mide cuántas imágenes se muestran cada segundo.",
            content_urls: {
              desktop: {
                page:
                  "https://es.wikipedia.org/wiki/Frecuencia_de_imagen_en_videojuegos",
              },
            },
          });
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
    };

    const result = await routeResearchQuery(
      "¿Qué significa FPS en videojuegos?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) throw new Error("expected relevant FPS answer");
    const text = result.displayText?.toLowerCase() ?? "";
    if (!text.includes("videojuegos") || !text.includes("fotogram")) {
      throw new Error("expected gaming FPS meaning, not generic cinema HFR");
    }
  },
);

Deno.test(
  "rate-limited Wikipedia retries before falling through to a general model",
  async () => {
    let wikipediaGeneratorCalls = 0;
    let modelCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("generator") === "search"
        ) {
          wikipediaGeneratorCalls += 1;
          if (wikipediaGeneratorCalls === 1) {
            return new Response("rate limited", {
              status: 429,
              headers: { "Retry-After": "0" },
            });
          }
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Cultura",
                  extract:
                    "La cultura es el conjunto de conocimientos, costumbres, prácticas y expresiones compartidas por una sociedad.",
                  canonicalurl: "https://es.wikipedia.org/wiki/Cultura",
                },
              },
            },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php"
        ) {
          return jsonResponse({ query: { search: [] } });
        }
        if (
          url.hostname === "generativelanguage.googleapis.com" ||
          url.hostname === "api.x.ai"
        ) {
          modelCalls += 1;
          throw new Error(
            "model fallback must not be needed after Wikipedia retry",
          );
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
      sleep: () => Promise.resolve(),
      random: () => 0,
    };

    const result = await routeResearchQuery(
      "Explícame de forma sencilla qué es la cultura.",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("expected Wikipedia retry to recover stable knowledge");
    }
    if (wikipediaGeneratorCalls !== 2) {
      throw new Error(
        "expected one bounded Wikipedia rate-limit retry; calls=" +
          wikipediaGeneratorCalls,
      );
    }
    if (modelCalls !== 0) {
      throw new Error("Wikipedia recovery must avoid model fallback");
    }
    if (!result.displayText?.toLowerCase().includes("cultura")) {
      throw new Error("expected recovered culture answer");
    }
  },
);

Deno.test(
  "Wikipedia tolerates a short 429 burst before stable knowledge abstains",
  async () => {
    let wikipediaGeneratorCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("generator") === "search"
        ) {
          wikipediaGeneratorCalls += 1;
          if (wikipediaGeneratorCalls <= 4) {
            return new Response("rate limited", { status: 429 });
          }
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Mitología griega",
                  extract:
                    "La mitología griega reúne relatos sobre dioses y héroes de la antigua Grecia.",
                  canonicalurl:
                    "https://es.wikipedia.org/wiki/Mitolog%C3%ADa_griega",
                },
              },
            },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php"
        ) {
          return jsonResponse({ query: { search: [] } });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
      sleep: () => Promise.resolve(),
      random: () => 0,
    };

    const result = await routeResearchQuery(
      "¿Qué debería saber una persona sobre la mitología griega?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("short Wikipedia throttling must recover");
    }
    if (wikipediaGeneratorCalls !== 5) {
      throw new Error(
        "expected five bounded Wikipedia attempts; calls=" +
          wikipediaGeneratorCalls,
      );
    }
  },
);

Deno.test(
  "stable knowledge falls back to Spanish Wikidata when Wikipedia is unavailable",
  async () => {
    let wikidataCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (url.hostname === "es.wikipedia.org") {
          return new Response("temporarily unavailable", { status: 503 });
        }
        if (
          url.hostname === "www.wikidata.org" &&
          url.pathname === "/w/api.php"
        ) {
          wikidataCalls += 1;
          if (url.searchParams.get("search") !== "germinación") {
            return jsonResponse({ search: [] });
          }
          return jsonResponse({
            search: [{
              id: "Q100001",
              label: "Germinación",
              description:
                "proceso por el que una semilla inicia su desarrollo y produce un brote",
              concepturi: "https://www.wikidata.org/entity/Q100001",
            }],
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
      sleep: () => Promise.resolve(),
      random: () => 0,
    };

    const result = await routeResearchQuery(
      "¿Para qué sirve o por qué es importante la germinación de una semilla?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) {
      throw new Error("expected Wikidata stable-knowledge fallback");
    }
    if (wikidataCalls < 1) {
      throw new Error("expected Wikidata to be consulted");
    }
    if (!result.displayText?.toLowerCase().includes("germin")) {
      throw new Error("expected germination answer from Wikidata");
    }
    if (!result.sourceIds?.some((source) => source.includes("wikidata.org"))) {
      throw new Error("expected visible Wikidata source");
    }
  },
);

Deno.test(
  "refresh-rate aliases and matching brand evidence remain valid primary knowledge",
  async () => {
    const cases = [
      {
        query: "¿Qué significa 120 Hz en una televisión?",
        title: "Frecuencia de actualización",
        extract:
          "La frecuencia de actualización de una pantalla indica cuántas veces se renueva la imagen por segundo.",
        expected: "frecuencia",
      },
      {
        query: "¿Qué es una tasa de refresco de 120 Hz?",
        title: "Tasa de refresco",
        extract:
          "La tasa de refresco describe cuántas veces por segundo una pantalla actualiza la imagen mostrada.",
        expected: "refresco",
      },
      {
        query: "¿Qué fabrica NVIDIA?",
        title: "Nvidia",
        extract:
          "Nvidia es una empresa tecnológica que diseña unidades de procesamiento gráfico y otros productos de computación.",
        expected: "nvidia",
      },
    ];

    for (const testCase of cases) {
      let modelCalls = 0;
      const deps: ResearchDependencies = {
        fetcher: (input) => {
          const url = new URL(String(input));
          if (
            url.hostname === "es.wikipedia.org" &&
            url.pathname === "/w/api.php" &&
            url.searchParams.get("generator") === "search"
          ) {
            return jsonResponse({
              query: {
                pages: {
                  "1": {
                    pageid: 1,
                    index: 1,
                    title: testCase.title,
                    extract: testCase.extract,
                    canonicalurl: "https://es.wikipedia.org/wiki/" +
                      encodeURIComponent(testCase.title.replace(/ /g, "_")),
                  },
                },
              },
            });
          }
          if (
            url.hostname === "generativelanguage.googleapis.com" ||
            url.hostname === "api.x.ai"
          ) {
            modelCalls += 1;
            throw new Error("primary evidence should avoid model fallback");
          }
          throw new Error("unexpected URL " + url);
        },
        env: () => undefined,
      };

      const result = await routeResearchQuery(
        testCase.query,
        deps,
        "",
        "GENERAL_KNOWLEDGE",
      );
      if (result.abstained) {
        throw new Error(
          "expected semantic primary evidence for " + testCase.query,
        );
      }
      if (
        !(result.displayText ?? "").toLowerCase().includes(testCase.expected)
      ) {
        throw new Error("unexpected answer for " + testCase.query);
      }
      if (modelCalls !== 0) {
        throw new Error("primary knowledge unexpectedly used a model");
      }
    }
  },
);

Deno.test(
  "operating system smoke question resolves from canonical primary knowledge",
  async () => {
    let modelCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("generator") === "search"
        ) {
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Sistema operativo",
                  extract:
                    "Un sistema operativo es el software principal que administra los recursos de un dispositivo y permite ejecutar aplicaciones.",
                  canonicalurl: "https://es.wikipedia.org/wiki/Sistema_operativo",
                },
              },
            },
          });
        }
        if (
          url.hostname === "generativelanguage.googleapis.com" ||
          url.hostname === "api.x.ai"
        ) {
          modelCalls += 1;
          throw new Error("primary evidence should avoid model fallback");
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
    };

    const result = await routeResearchQuery(
      "¿Para qué sirve un sistema operativo?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );
    if (result.abstained) {
      throw new Error("expected operating-system primary evidence");
    }
    if (!result.displayText?.toLowerCase().includes("sistema operativo")) {
      throw new Error("expected operating-system answer");
    }
    if (modelCalls !== 0) {
      throw new Error("operating-system knowledge unexpectedly used a model");
    }
  },
);

Deno.test(
  "stable common concepts use bare Wikidata labels when Wikipedia is unavailable",
  async () => {
    const cases = [
      {
        query: "¿Cómo funciona un ventilador?",
        expectedSearch: "ventilador",
        label: "Ventilador",
        description: "máquina que mueve aire mediante aspas giratorias",
      },
      {
        query: "¿Para qué sirve o por qué es importante la ética?",
        expectedSearch: "ética",
        label: "Ética",
        description: "rama de la filosofía que estudia la conducta moral",
      },
    ];

    for (const testCase of cases) {
      const deps: ResearchDependencies = {
        fetcher: (input) => {
          const url = new URL(String(input));
          if (url.hostname === "es.wikipedia.org") {
            return new Response("temporarily unavailable", { status: 503 });
          }
          if (url.hostname === "www.wikidata.org") {
            if (url.searchParams.get("search") !== testCase.expectedSearch) {
              return jsonResponse({ search: [] });
            }
            return jsonResponse({
              search: [{
                id: "Q-test",
                label: testCase.label,
                description: testCase.description,
                concepturi: "https://www.wikidata.org/entity/Q-test",
              }],
            });
          }
          throw new Error("unexpected URL " + url);
        },
        env: () => undefined,
        sleep: () => Promise.resolve(),
        random: () => 0,
      };

      const result = await routeResearchQuery(
        testCase.query,
        deps,
        "",
        "GENERAL_KNOWLEDGE",
      );
      if (result.abstained) {
        throw new Error("stable concept should resolve without model keys");
      }
    }
  },
);

Deno.test(
  "search hints cannot validate an unrelated brand as authoritative",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("generator") === "search"
        ) {
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Samsung",
                  extract:
                    "Samsung es una empresa tecnológica que fabrica productos electrónicos.",
                  canonicalurl: "https://es.wikipedia.org/wiki/Samsung",
                },
              },
            },
          });
        }
        if (url.hostname === "es.wikipedia.org") {
          return jsonResponse({ query: { search: [] } });
        }
        if (url.hostname === "www.wikidata.org") {
          return jsonResponse({ search: [] });
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
      sleep: () => Promise.resolve(),
      random: () => 0,
    };

    const result = await routeResearchQuery(
      "¿Qué productos fabrica Sony?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );
    if (!result.abstained) {
      throw new Error(
        "generic search hints must not validate an unrelated brand",
      );
    }
  },
);

Deno.test(
  "rate-limited Gemini falls through to xAI without retrying the same 429",
  async () => {
    let geminiCalls = 0;
    let xaiCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php"
        ) {
          return jsonResponse({ query: { search: [] } });
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        if (url.hostname === "generativelanguage.googleapis.com") {
          geminiCalls += 1;
          return new Response("quota", { status: 429 });
        }
        if (url.hostname === "api.x.ai") {
          xaiCalls += 1;
          return jsonResponse({
            choices: [{
              message: {
                content:
                  "Un catalizador acelera una reacción química sin consumirse de forma permanente en ella.",
              },
            }],
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "GEMINI_API_KEY") return "gemini-test-key";
        if (name === "XAI_API_KEY") return "xai-test-key";
        return undefined;
      },
      sleep: () => Promise.resolve(),
      random: () => 0,
    };

    const result = await routeResearchQuery(
      "¿Qué es un catalizador?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (geminiCalls !== 1) {
      throw new Error(
        "429 must not hammer the same provider; calls=" + geminiCalls,
      );
    }
    if (xaiCalls !== 1 || result.abstained) {
      throw new Error("expected immediate cross-provider fallback");
    }
    if (!result.displayText?.toLowerCase().includes("catalizador")) {
      throw new Error("expected useful xAI fallback");
    }
  },
);

Deno.test(
  "optional grounded synthesis can be disabled without disabling model fallback",
  async () => {
    let geminiCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php"
        ) {
          return jsonResponse({
            query: { search: [{ title: "Motor" }] },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Motor",
            type: "standard",
            extract:
              "Un motor es una máquina que transforma energía en trabajo mecánico.",
            content_urls: {
              desktop: { page: "https://es.wikipedia.org/wiki/Motor" },
            },
          });
        }
        if (url.hostname === "generativelanguage.googleapis.com") {
          geminiCalls += 1;
          return jsonResponse({
            candidates: [{
              finishReason: "STOP",
              content: {
                parts: [{
                  text:
                    "Un motor es una máquina que transforma energía en trabajo mecánico.",
                }],
              },
            }],
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "GEMINI_API_KEY") return "gemini-test-key";
        if (name === "ULTRA_DISABLE_OPTIONAL_SYNTHESIS") return "1";
        return undefined;
      },
    };

    const result = await routeResearchQuery(
      "¿Qué es un motor?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) throw new Error("expected verified answer");
    if (geminiCalls !== 0) {
      throw new Error("disabled optional synthesis must preserve model quota");
    }
    if (!result.displayText?.toLowerCase().includes("motor")) {
      throw new Error("verified evidence must still be returned");
    }
  },
);

Deno.test(
  "general knowledge resolves ranked Wikipedia candidates in one primary request",
  async () => {
    let wikipediaCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (url.hostname === "es.wikipedia.org") {
          wikipediaCalls += 1;
          if (
            url.pathname !== "/w/api.php" ||
            url.searchParams.get("generator") !== "search"
          ) {
            throw new Error(
              "expected single generator-search request before any fallback",
            );
          }
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Crucifixión de Jesús",
                  extract:
                    "La crucifixión de Jesús ocurrió en Judea durante el siglo I.",
                  canonicalurl:
                    "https://es.wikipedia.org/wiki/Crucifixi%C3%B3n_de_Jes%C3%BAs",
                },
                "2": {
                  pageid: 2,
                  index: 2,
                  title: "Yeso",
                  extract:
                    "El yeso es un material empleado en construcción para revestimientos y acabados.",
                  canonicalurl: "https://es.wikipedia.org/wiki/Yeso",
                },
              },
            },
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
    };

    const result = await routeResearchQuery(
      "Si alguien me pregunta por el yeso en construcción, ¿cómo lo explicarías en pocas frases?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) throw new Error("expected Wikipedia answer");
    if (wikipediaCalls !== 1) {
      throw new Error(
        "primary Wikipedia resolution should use one request; calls=" +
          wikipediaCalls,
      );
    }
    if (!result.displayText?.toLowerCase().includes("yeso")) {
      throw new Error("expected the relevant ranked candidate");
    }
  },
);

Deno.test(
  "ambiguous short knowledge entities receive domain disambiguation hints",
  async () => {
    const observedSearches: string[] = [];
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("generator") === "search"
        ) {
          const search = url.searchParams.get("gsrsearch") ?? "";
          observedSearches.push(search);
          const isNpc = search.toLowerCase().includes("npc");
          return jsonResponse({
            query: {
              pages: isNpc
                ? {
                  "1": {
                    pageid: 1,
                    index: 1,
                    title: "NPC Rieti",
                    extract: "NPC Rieti es un equipo de baloncesto italiano.",
                    canonicalurl: "https://es.wikipedia.org/wiki/NPC_Rieti",
                  },
                  "2": {
                    pageid: 2,
                    index: 2,
                    title: "Personaje no jugador",
                    extract:
                      "Un personaje no jugador o NPC es un personaje de videojuego que no controla directamente un jugador.",
                    canonicalurl:
                      "https://es.wikipedia.org/wiki/Personaje_no_jugador",
                  },
                }
                : {
                  "3": {
                    pageid: 3,
                    index: 1,
                    title: "Sinónimo (taxonomía)",
                    extract:
                      "En taxonomía, sinonimia es la existencia de más de un nombre científico para un taxón.",
                    canonicalurl:
                      "https://es.wikipedia.org/wiki/Sin%C3%B3nimo_(taxonom%C3%ADa)",
                  },
                  "4": {
                    pageid: 4,
                    index: 2,
                    title: "Sinonimia (semántica)",
                    extract:
                      "En lingüística, un sinónimo es una palabra con significado igual o semejante al de otra palabra.",
                    canonicalurl:
                      "https://es.wikipedia.org/wiki/Sinonimia_(sem%C3%A1ntica)",
                  },
                },
            },
          });
        }
        throw new Error("unexpected URL " + url);
      },
      env: () => undefined,
    };

    const npc = await routeResearchQuery(
      "¿Qué es un NPC?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );
    if (
      npc.abstained || !npc.displayText?.toLowerCase().includes("videojuego")
    ) {
      throw new Error("NPC must resolve to the gaming meaning");
    }

    const synonym = await routeResearchQuery(
      "¿Qué es un sinónimo?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );
    if (
      synonym.abstained ||
      !synonym.displayText?.toLowerCase().includes("palabra")
    ) {
      throw new Error("sinónimo must resolve to the linguistic meaning");
    }

    if (
      !observedSearches.some((value) =>
        value.toLowerCase().includes("videojuegos")
      ) ||
      !observedSearches.some((value) => {
        const normalized = value.toLowerCase();
        return normalized.includes("sinonimia") &&
          normalized.includes("semántica");
      })
    ) {
      throw new Error("expected domain-specific search hints");
    }
  },
);

Deno.test(
  "ambiguous VPN query prefers the networking concept over a branded VPN product",
  async () => {
    let modelCalls = 0;
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("generator") === "search"
        ) {
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Mozilla VPN",
                  extract:
                    "Mozilla VPN es una aplicación y servicio de red privada virtual desarrollado por Mozilla.",
                  canonicalurl: "https://es.wikipedia.org/wiki/Mozilla_VPN",
                },
                "2": {
                  pageid: 2,
                  index: 2,
                  title: "Red privada virtual",
                  extract:
                    "Una red privada virtual o VPN extiende una red privada sobre una red pública y permite una conexión protegida entre dispositivos.",
                  canonicalurl:
                    "https://es.wikipedia.org/wiki/Red_privada_virtual",
                },
              },
            },
          });
        }
        if (
          url.hostname === "generativelanguage.googleapis.com" ||
          url.hostname === "api.x.ai"
        ) {
          modelCalls += 1;
          return new Response("quota", { status: 429 });
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        throw new Error("unexpected URL " + url);
      },
      env: (name) => {
        if (name === "GEMINI_API_KEY") return "test-gemini";
        if (name === "XAI_API_KEY") return "test-xai";
        if (name === "ULTRA_DISABLE_OPTIONAL_SYNTHESIS") return "1";
        return undefined;
      },
    };

    const result = await routeResearchQuery(
      "¿Qué es una VPN?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) throw new Error("expected stable VPN answer");
    const answer = result.displayText?.toLowerCase() ?? "";
    if (!answer.includes("red privada virtual")) {
      throw new Error("expected the general networking concept");
    }
    if (answer.includes("mozilla vpn")) {
      throw new Error("branded VPN product must not win generic concept query");
    }
    if (modelCalls !== 0) {
      throw new Error("verified primary evidence must avoid model quota");
    }
  },
);

Deno.test(
  "stable smoke topics resolve from primary knowledge without consuming model quota",
  async () => {
    const cases = [
      {
        query: "¿Para qué sirve o por qué es importante el ISO en fotografía?",
        searchMustContain: ["iso", "fotografia"],
        title: "Sensibilidad ISO",
        extract:
          "La sensibilidad ISO en fotografía describe la sensibilidad usada para determinar la exposición de una imagen.",
        expected: "fotograf",
      },
      {
        query: "¿Cómo respiran los peces?",
        searchMustContain: ["peces", "respir"],
        title: "Respiración de los peces",
        extract:
          "La mayoría de los peces respira mediante branquias, que intercambian gases con el agua.",
        expected: "branquias",
      },
      {
        query: "¿Cómo funciona un parlante Bluetooth?",
        searchMustContain: ["bluetooth", "altavoz"],
        title: "Altavoz Bluetooth",
        extract:
          "Un altavoz Bluetooth recibe audio digital por Bluetooth y lo convierte en sonido mediante sus transductores.",
        expected: "bluetooth",
      },
      {
        query:
          "Si alguien me pregunta por la navegación autónoma, ¿cómo lo explicarías en pocas frases?",
        searchMustContain: ["navegacion", "robotica"],
        title: "Navegación autónoma",
        extract:
          "La navegación autónoma permite que un robot determine su posición, planifique una ruta y se desplace sin control humano continuo.",
        expected: "robot",
      },
      {
        query: "¿Qué tipo de empresa es Lenovo?",
        searchMustContain: ["lenovo", "empresa"],
        title: "Lenovo",
        extract:
          "Lenovo es una empresa tecnológica multinacional que fabrica computadoras personales, dispositivos y otros productos electrónicos.",
        expected: "empresa",
      },
      {
        query: "¿Quién es ElRubius?",
        searchMustContain: ["rubius", "youtuber"],
        title: "El Rubius",
        extract:
          "El Rubius es un youtuber y creador de contenido español conocido por sus videos de entretenimiento y videojuegos.",
        expected: "youtuber",
      },
      {
        query: "¿Qué es HDR en una TV?",
        searchMustContain: ["hdr", "rango"],
        title: "Alto rango dinámico",
        extract:
          "El alto rango dinámico o HDR en televisión amplía el rango de luminancia y contraste para representar más detalle entre zonas oscuras y brillantes.",
        expected: "rango",
      },
      {
        query: "¿Qué significa IP68 en un celular?",
        searchMustContain: ["ip68", "proteccion"],
        title: "Grado de protección IP",
        extract:
          "IP68 es una clasificación del grado de protección frente a polvo y agua usada en dispositivos electrónicos.",
        expected: "proteccion",
      },
      {
        query: "¿Quién es Fernanfloo?",
        searchMustContain: ["fernanfloo", "youtuber"],
        title: "Fernanfloo",
        extract:
          "Fernanfloo es un youtuber y creador de contenido salvadoreño conocido por videos de videojuegos y entretenimiento.",
        expected: "youtuber",
      },
      {
        query:
          "Resume qué es la higiene dental de una mascota sin asumir conocimientos técnicos.",
        searchMustContain: ["higiene", "bucodental"],
        title: "Higiene bucodental",
        extract:
          "La higiene bucodental es el cuidado de los dientes, las encías, la lengua y toda la cavidad bucal en general.",
        expected: "dientes",
      },
      {
        query: "¿Qué significa 120 Hz en una televisión?",
        searchMustContain: ["120", "television"],
        title: "Frecuencia de actualización",
        extract:
          "En una televisión, 120 Hz significa que la pantalla puede actualizar la imagen hasta 120 veces por segundo.",
        expected: "120",
      },
      {
        query: "¿Qué significa 120 Hz en una televisión?",
        searchMustContain: ["120", "refresco"],
        title: "Tasa de refresco",
        extract:
          "Una tasa de refresco de 120 Hz indica que una pantalla puede actualizar la imagen hasta 120 veces por segundo.",
        expected: "120",
      },
      {
        query:
          "Resume qué es la higiene dental de una mascota sin asumir conocimientos técnicos.",
        searchMustContain: ["higiene", "bucodental"],
        title: "Higiene bucodental",
        extract:
          "La higiene bucodental es el cuidado de los dientes, las encías, la lengua y toda la cavidad bucal en general.",
        expected: "dientes",
      },
    ];

    for (const testCase of cases) {
      let modelCalls = 0;
      let observedSearch = "";
      const deps: ResearchDependencies = {
        fetcher: (input) => {
          const url = new URL(String(input));
          if (
            url.hostname === "es.wikipedia.org" &&
            url.pathname === "/w/api.php" &&
            url.searchParams.get("generator") === "search"
          ) {
            observedSearch = wikipediaSearchParam(url)
              .normalize("NFD")
              .replace(/\p{Diacritic}/gu, "")
              .toLowerCase();
            return jsonResponse({
              query: {
                pages: {
                  "1": {
                    pageid: 1,
                    index: 1,
                    title: testCase.title,
                    extract: testCase.extract,
                    canonicalurl: "https://es.wikipedia.org/wiki/" +
                      encodeURIComponent(testCase.title.replaceAll(" ", "_")),
                  },
                },
              },
            });
          }
          if (url.hostname === "api.tavily.com") {
            return jsonResponse({ results: [] });
          }
          if (
            url.hostname === "generativelanguage.googleapis.com" ||
            url.hostname === "api.x.ai"
          ) {
            modelCalls += 1;
            return new Response("quota", { status: 429 });
          }
          throw new Error("unexpected URL " + url);
        },
        env: (name) => {
          if (name === "GEMINI_API_KEY") return "test-gemini";
          if (name === "XAI_API_KEY") return "test-xai";
          if (name === "ULTRA_DISABLE_OPTIONAL_SYNTHESIS") return "1";
          return undefined;
        },
      };

      const result = await routeResearchQuery(
        testCase.query,
        deps,
        "",
        "GENERAL_KNOWLEDGE",
      );

      if (result.abstained) {
        throw new Error("unexpected abstention for " + testCase.query);
      }
      for (const token of testCase.searchMustContain) {
        if (!observedSearch.includes(token)) {
          throw new Error(
            "missing search hint " + token + " for " + testCase.query +
              ": " + observedSearch,
          );
        }
      }
      const normalizedAnswer = (result.displayText ?? "")
        .normalize("NFD")
        .replace(/\p{Diacritic}/gu, "")
        .toLowerCase();
      if (!normalizedAnswer.includes(testCase.expected)) {
        throw new Error("unexpected answer for " + testCase.query);
      }
      if (modelCalls !== 0) {
        throw new Error(
          "stable primary answer consumed model quota for " + testCase.query,
        );
      }
    }
  },
);

Deno.test(
  "synonym and alternate pet dental wrapper resolve without configured models",
  async () => {
    const cases = [
      {
        query: "¿Qué es un sinónimo?",
        title: "Sinonimia (semántica)",
        extract:
          "La sinonimia es una relación semántica de identidad o semejanza de significados entre expresiones o palabras llamadas sinónimos.",
        expectedSearch: ["sinonimia", "semantica"],
        expectedAnswer: "palabras",
      },
      {
        query:
          "¿Qué debería saber una persona sobre la higiene dental de una mascota?",
        title: "Higiene bucodental",
        extract:
          "La higiene bucodental es el cuidado de los dientes, las encías, la lengua y toda la cavidad bucal en general.",
        expectedSearch: ["higiene", "bucodental"],
        expectedAnswer: "dientes",
      },
    ];

    for (const testCase of cases) {
      let observedSearch = "";
      let modelCalls = 0;
      const deps: ResearchDependencies = {
        fetcher: (input) => {
          const url = new URL(String(input));
          if (
            url.hostname === "es.wikipedia.org" &&
            url.pathname === "/w/api.php" &&
            url.searchParams.get("generator") === "search"
          ) {
            observedSearch = wikipediaSearchParam(url)
              .normalize("NFD")
              .replace(/\p{Diacritic}/gu, "")
              .toLowerCase();
            return jsonResponse({
              query: {
                pages: {
                  "1": {
                    pageid: 1,
                    index: 1,
                    title: testCase.title,
                    extract: testCase.extract,
                    canonicalurl: "https://es.wikipedia.org/wiki/" +
                      encodeURIComponent(testCase.title.replaceAll(" ", "_")),
                  },
                },
              },
            });
          }
          if (url.hostname === "api.tavily.com") {
            return jsonResponse({ results: [] });
          }
          if (
            url.hostname === "generativelanguage.googleapis.com" ||
            url.hostname === "api.x.ai"
          ) {
            modelCalls += 1;
            return new Response("not configured", { status: 503 });
          }
          throw new Error("unexpected URL " + url);
        },
        env: (name) =>
          name === "ULTRA_DISABLE_OPTIONAL_SYNTHESIS" ? "1" : undefined,
      };

      const result = await routeResearchQuery(
        testCase.query,
        deps,
        "",
        "GENERAL_KNOWLEDGE",
      );
      if (result.abstained) {
        throw new Error("unexpected abstention for " + testCase.query);
      }
      for (const token of testCase.expectedSearch) {
        if (!observedSearch.includes(token)) {
          throw new Error(
            "missing stable search hint " + token + ": " + observedSearch,
          );
        }
      }
      const answer = (result.displayText ?? "").normalize("NFD")
        .replace(/\p{Diacritic}/gu, "").toLowerCase();
      if (!answer.includes(testCase.expectedAnswer)) {
        throw new Error("unexpected stable answer for " + testCase.query);
      }
      if (modelCalls !== 0) {
        throw new Error("stable answer unexpectedly used a model");
      }
    }
  },
);

Deno.test(
  "QLED and pet dental variants stay on stable primary evidence without models",
  async () => {
    const cases = [
      {
        query: "¿Qué es QLED?",
        title: "QLED",
        extract:
          "QLED es una tecnología de pantalla basada en puntos cuánticos usada en televisores para reproducir color y brillo.",
        expectedSearch: ["qled", "television"],
        expectedAnswer: "pantalla",
      },
      {
        query:
          "Si alguien me pregunta por la higiene dental de una mascota, ¿cómo lo explicarías en pocas frases?",
        title: "Higiene bucodental",
        extract:
          "La higiene bucodental es el cuidado de los dientes, las encías, la lengua y toda la cavidad bucal en general.",
        expectedSearch: ["higiene", "bucodental"],
        expectedAnswer: "dientes",
      },
    ];

    for (const testCase of cases) {
      let observedSearch = "";
      let modelCalls = 0;
      const deps: ResearchDependencies = {
        fetcher: (input) => {
          const url = new URL(String(input));
          if (
            url.hostname === "es.wikipedia.org" &&
            url.pathname === "/w/api.php" &&
            url.searchParams.get("generator") === "search"
          ) {
            observedSearch = wikipediaSearchParam(url)
              .normalize("NFD")
              .replace(/\p{Diacritic}/gu, "")
              .toLowerCase();
            return jsonResponse({
              query: {
                pages: {
                  "1": {
                    pageid: 1,
                    index: 1,
                    title: testCase.title,
                    extract: testCase.extract,
                    canonicalurl: "https://es.wikipedia.org/wiki/" +
                      encodeURIComponent(testCase.title.replaceAll(" ", "_")),
                  },
                },
              },
            });
          }
          if (url.hostname === "api.tavily.com") {
            return jsonResponse({ results: [] });
          }
          if (
            url.hostname === "generativelanguage.googleapis.com" ||
            url.hostname === "api.x.ai"
          ) {
            modelCalls += 1;
            return new Response("not configured", { status: 503 });
          }
          throw new Error("unexpected URL " + url);
        },
        env: (name) =>
          name === "ULTRA_DISABLE_OPTIONAL_SYNTHESIS" ? "1" : undefined,
      };

      const result = await routeResearchQuery(
        testCase.query,
        deps,
        "",
        "GENERAL_KNOWLEDGE",
      );

      if (result.abstained) {
        throw new Error("unexpected abstention for " + testCase.query);
      }
      for (const token of testCase.expectedSearch) {
        if (!observedSearch.includes(token)) {
          throw new Error(
            "missing stable search hint " + token + ": " + observedSearch,
          );
        }
      }
      if (
        !(result.displayText ?? "")
          .normalize("NFD")
          .replace(/\p{Diacritic}/gu, "")
          .toLowerCase()
          .includes(testCase.expectedAnswer)
      ) {
        throw new Error("unexpected stable answer for " + testCase.query);
      }
      if (modelCalls !== 0) {
        throw new Error("stable answer unexpectedly used a model");
      }
    }
  },
);

Deno.test(
  "dependent bear follow-up keeps the previous subject and resolves the intended comparison",
  async () => {
    let observedSearch = "";
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("generator") === "search"
        ) {
          observedSearch = wikipediaSearchParam(url).toLowerCase();
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Oso polar",
                  extract:
                    "El oso polar es una especie de mamífero carnívoro de la familia de los osos y se encuentra entre los osos actuales de mayor tamaño.",
                  canonicalurl: "https://es.wikipedia.org/wiki/Ursus_maritimus",
                },
              },
            },
          });
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        throw new Error("unexpected URL " + url);
      },
      env: (name) =>
        name === "ULTRA_DISABLE_OPTIONAL_SYNTHESIS" ? "1" : undefined,
    };

    const result = await routeResearchQuery(
      "¿Cuál es el más grande?",
      deps,
      "Tú: Háblame de los osos.\nUltra: Los osos son mamíferos de la familia Ursidae.",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) throw new Error("expected contextual bear answer");
    if (!observedSearch.includes("oso")) {
      throw new Error("previous bear topic was lost: " + observedSearch);
    }
    if (!result.displayText?.toLowerCase().includes("oso polar")) {
      throw new Error("expected contextual answer about the largest bear");
    }
  },
);

Deno.test(
  "generic synonym query rejects unrelated linguistics evidence before fallback",
  async () => {
    const deps: ResearchDependencies = {
      fetcher: (input) => {
        const url = new URL(String(input));
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("generator") === "search"
        ) {
          return jsonResponse({
            query: {
              pages: {
                "1": {
                  pageid: 1,
                  index: 1,
                  title: "Lingüística antropológica",
                  extract:
                    "La lingüística antropológica estudia el lenguaje, las palabras y su contexto social y cultural.",
                  canonicalurl:
                    "https://es.wikipedia.org/wiki/Ling%C3%BC%C3%ADstica_antropol%C3%B3gica",
                },
              },
            },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname === "/w/api.php" &&
          url.searchParams.get("list") === "search"
        ) {
          return jsonResponse({
            query: {
              search: [{ title: "Sinonimia (semántica)" }],
            },
          });
        }
        if (
          url.hostname === "es.wikipedia.org" &&
          url.pathname.includes("/api/rest_v1/page/summary/")
        ) {
          return jsonResponse({
            title: "Sinonimia (semántica)",
            type: "standard",
            extract:
              "Un sinónimo es una palabra que tiene un significado igual o semejante al de otra.",
            content_urls: {
              desktop: {
                page:
                  "https://es.wikipedia.org/wiki/Sinonimia_(sem%C3%A1ntica)",
              },
            },
          });
        }
        if (url.hostname === "api.tavily.com") {
          return jsonResponse({ results: [] });
        }
        throw new Error("unexpected URL " + url);
      },
      env: (name) =>
        name === "ULTRA_DISABLE_OPTIONAL_SYNTHESIS" ? "1" : undefined,
    };

    const result = await routeResearchQuery(
      "¿Qué es un sinónimo?",
      deps,
      "",
      "GENERAL_KNOWLEDGE",
    );

    if (result.abstained) throw new Error("expected semantic synonym answer");
    const answer = result.displayText?.toLowerCase() ?? "";
    if (!answer.includes("sinónimo") || !answer.includes("palabra")) {
      throw new Error("expected a real synonym definition");
    }
    if (answer.includes("antropol")) {
      throw new Error("unrelated linguistics evidence leaked");
    }
  },
);

Deno.test("new Spanish topic is not merged into previous context", async () => {
  let searchQuery = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        searchQuery = wikipediaSearchParam(url);
        return jsonResponse({
          query: { search: [{ title: "Fotosíntesis" }] },
        });
      }
      return jsonResponse({
        title: "Fotosíntesis",
        type: "standard",
        extract:
          "La fotosíntesis convierte energía luminosa en energía química.",
      });
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "¿Por qué es importante la fotosíntesis?",
    deps,
    "Ultra, explícame qué es Vulkan",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected independent new topic");
  if (searchQuery.toLowerCase().includes("vulkan")) {
    throw new Error("new topic merged with previous context");
  }
});

Deno.test("complete purpose question stays independent of previous context", async () => {
  let searchQuery = "";
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        searchQuery = wikipediaSearchParam(url);
        return jsonResponse({
          query: { search: [{ title: "Brújula" }] },
        });
      }
      return jsonResponse({
        title: "Brújula",
        type: "standard",
        extract:
          "La brújula es un instrumento de orientación que indica el norte magnético.",
      });
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "¿Para qué sirve la brújula?",
    deps,
    "Ultra, explícame qué es Vulkan",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) throw new Error("expected independent purpose answer");
  if (searchQuery.toLowerCase().includes("vulkan")) {
    throw new Error("complete purpose question inherited previous context");
  }
});

Deno.test("English knowledge accepts a valid Spanish cognate topic", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        return jsonResponse({
          query: { search: [{ title: "Fotosíntesis" }] },
        });
      }
      return jsonResponse({
        title: "Fotosíntesis",
        type: "standard",
        extract:
          "La fotosíntesis convierte energía luminosa en energía química.",
      });
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "what is photosynthesis?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) {
    throw new Error("valid cross-language cognate evidence was rejected");
  }
});

Deno.test("English queries reject unrelated encyclopedia evidence", async () => {
  const deps: ResearchDependencies = {
    fetcher: (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/w/api.php") {
        return jsonResponse({
          query: { search: [{ title: "Motor de combustión interna" }] },
        });
      }
      return jsonResponse({
        title: "Motor de combustión interna",
        type: "standard",
        extract: "Un motor transforma energía en movimiento.",
      });
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "what is photosynthesis?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (!result.abstained) {
    throw new Error("unrelated evidence must be rejected");
  }
});
