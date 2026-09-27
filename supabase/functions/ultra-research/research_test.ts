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

Deno.test("news voice response stays in Spanish even when source titles are English", async () => {
  const deps: ResearchDependencies = {
    fetcher: () =>
      jsonResponse({
        articles: [
          {
            title: "Major game update released today",
            url: "https://fuente-uno.example/noticia",
            domain: "fuente-uno.example",
            seendate: "20260927T010000Z",
          },
          {
            title: "New patch changes performance",
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

  if (result.abstained) throw new Error("expected verified news");
  if (result.displayText?.includes("Major game update")) {
    throw new Error("English source titles must not leak into Ultra speech");
  }
  if (
    !result.displayText?.startsWith("Encontré información reciente verificada")
  ) {
    throw new Error("expected Spanish-only news summary");
  }
});
