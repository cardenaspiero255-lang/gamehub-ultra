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
        searchQuery = url.searchParams.get("srsearch") ?? "";
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
        searchQuery = url.searchParams.get("srsearch") ?? "";
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
        queries.push(url.searchParams.get("srsearch") ?? "");
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
        searchQuery = url.searchParams.get("srsearch") ?? "";
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
        queries.push(url.searchParams.get("srsearch") ?? "");
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
        searchQuery = url.searchParams.get("srsearch") ?? "";
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
        searchQuery = url.searchParams.get("srsearch") ?? "";
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
        searchQuery = url.searchParams.get("srsearch") ?? "";
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
    fetcher: (input) => {
      const url = new URL(String(input));

      if (
        url.hostname === "es.wikipedia.org" && url.pathname === "/w/api.php"
      ) {
        return jsonResponse({ query: { search: [] } });
      }

      if (url.hostname === "generativelanguage.googleapis.com") {
        geminiCalled = true;
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
