export type ResearchResult = {
  claimKey?: string;
  value?: string;
  displayText?: string;
  sourceId?: string;
  sourceIds?: string[];
  independentSourceCount?: number;
  authoritative?: boolean;
  trustedReference?: boolean;
  observedAt?: string | null;
  abstained?: boolean;
  message?: string;
};

export type ResearchFetcher = (
  input: string | URL,
  init?: RequestInit,
) => Promise<Response> | Response;

export type ResearchDependencies = {
  fetcher: ResearchFetcher;
  env: (name: string) => string | undefined;
};

type JsonObject = Record<string, unknown>;

const USER_AGENT = "GameHub-Ultra-CAR73/1.0";

function abstain(message: string): ResearchResult {
  return { abstained: true, message };
}

function normalize(value: string): string {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function stripAssistantInvocation(value: string): string {
  return value
    .replace(/^\s*(?:gamehub\s+ultra|gamehub|ultra)\s*[,;:.-]?\s*/i, "")
    .trim();
}

function slug(value: string): string {
  return normalize(value)
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 120);
}

function stringValue(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function numberValue(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value
      .filter((item): item is string => typeof item === "string")
      .map((item) => item.trim())
      .filter(Boolean)
    : [];
}

function unique(values: string[]): string[] {
  return [...new Set(values.filter(Boolean))];
}

function hostname(value: string): string | null {
  try {
    return new URL(value).hostname.toLowerCase();
  } catch {
    return null;
  }
}

function independentDomains(urls: string[]): number {
  return unique(urls.map((url) => hostname(url) ?? "").filter(Boolean)).length;
}

async function fetchJson(
  deps: ResearchDependencies,
  input: string | URL,
  init?: RequestInit,
): Promise<JsonObject | null> {
  const response = await deps.fetcher(input, init);
  if (!response.ok) return null;
  const body = await response.json();
  return body && typeof body === "object" ? body as JsonObject : null;
}

function extractWeatherLocation(query: string): string | null {
  const clean = query.replace(/[?¿!¡]/g, " ").replace(/\s+/g, " ").trim();
  const patterns = [
    /(?:clima|tiempo|weather|pronostico|forecast)(?:\s+de\s+hoy|\s+hoy)?\s+(?:en|de|para)\s+(.+)$/i,
    /(?:en|de|para)\s+([\p{L}][\p{L}\s.'-]{1,80})$/iu,
  ];
  for (const pattern of patterns) {
    const match = clean.match(pattern);
    if (match?.[1]) {
      return match[1]
        .replace(/\b(?:hoy|today|ahora|now)\b/gi, " ")
        .replace(/\s+/g, " ")
        .trim();
    }
  }
  return null;
}

function weatherDescription(code: number): string {
  if (code === 0) return "despejado";
  if ([1, 2].includes(code)) return "parcialmente nublado";
  if (code === 3) return "nublado";
  if ([45, 48].includes(code)) return "con niebla";
  if ([51, 53, 55, 56, 57].includes(code)) return "con llovizna";
  if ([61, 63, 65, 66, 67, 80, 81, 82].includes(code)) return "con lluvia";
  if ([71, 73, 75, 77, 85, 86].includes(code)) return "con nieve";
  if ([95, 96, 99].includes(code)) return "con tormenta";
  return "con condiciones variables";
}

async function weatherEvidence(
  query: string,
  deps: ResearchDependencies,
): Promise<ResearchResult> {
  const location = extractWeatherLocation(query);
  if (!location) {
    return abstain(
      "Necesito una ciudad o ubicación en la pregunta para verificar el clima.",
    );
  }

  const geoUrl = new URL("https://geocoding-api.open-meteo.com/v1/search");
  geoUrl.searchParams.set("name", location);
  geoUrl.searchParams.set("count", "1");
  geoUrl.searchParams.set("language", "es");
  geoUrl.searchParams.set("format", "json");

  const geo = await fetchJson(deps, geoUrl, {
    headers: { "User-Agent": USER_AGENT },
  });
  const places = Array.isArray(geo?.results) ? geo.results : [];
  const place = places[0] as JsonObject | undefined;
  const latitude = numberValue(place?.latitude);
  const longitude = numberValue(place?.longitude);
  if (!place || latitude == null || longitude == null) {
    return abstain("No pude encontrar esa ubicación.");
  }

  const forecastUrl = new URL("https://api.open-meteo.com/v1/forecast");
  forecastUrl.searchParams.set("latitude", String(latitude));
  forecastUrl.searchParams.set("longitude", String(longitude));
  forecastUrl.searchParams.set(
    "current",
    "temperature_2m,apparent_temperature,weather_code",
  );
  forecastUrl.searchParams.set("timezone", "auto");

  const forecast = await fetchJson(deps, forecastUrl, {
    headers: { "User-Agent": USER_AGENT },
  });
  const current = forecast?.current as JsonObject | undefined;
  const temperature = numberValue(current?.temperature_2m);
  const apparent = numberValue(current?.apparent_temperature);
  const weatherCode = numberValue(current?.weather_code);
  if (temperature == null || weatherCode == null) {
    return abstain("La fuente climática no devolvió datos verificables.");
  }

  const description = weatherDescription(weatherCode);
  const placeLabel = [
    stringValue(place.name),
    stringValue(place.admin1),
    stringValue(place.country),
  ].filter(Boolean).join(", ");
  const feelsLike = apparent != null && Math.abs(apparent - temperature) >= 1
    ? `, sensación térmica de ${apparent} °C`
    : "";
  const observedAt = stringValue(current?.time);

  return {
    claimKey: `weather:${latitude.toFixed(3)},${longitude.toFixed(3)}`,
    value: `${temperature}|${weatherCode}|${observedAt ?? ""}`,
    displayText:
      `En ${placeLabel}: ${temperature} °C, ${description}${feelsLike}.`,
    sourceId: forecastUrl.toString(),
    sourceIds: [forecastUrl.toString()],
    independentSourceCount: 1,
    authoritative: true,
    observedAt,
  };
}

function extractNewsTopic(query: string): string {
  return stripAssistantInvocation(query)
    .replace(
      /^(?:noticias(?:\s+actuales)?(?:\s+de|\s+sobre)?|news(?:\s+about|\s+on)?|que\s+salio\s+nuevo\s+de|salio\s+nuevo\s+de|latest\s+news(?:\s+about|\s+on)?)\s*/i,
      "",
    )
    .replace(/[?¿!¡]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

async function newsEvidence(
  query: string,
  deps: ResearchDependencies,
): Promise<ResearchResult> {
  const topic = extractNewsTopic(query);
  if (!topic) return abstain("Necesito un tema para buscar noticias actuales.");

  const url = new URL("https://api.gdeltproject.org/api/v2/doc/doc");
  url.searchParams.set("query", topic);
  url.searchParams.set("mode", "ArtList");
  url.searchParams.set("maxrecords", "10");
  url.searchParams.set("format", "json");
  url.searchParams.set("sort", "HybridRel");
  url.searchParams.set("timespan", "7d");

  const payload = await fetchJson(deps, url, {
    headers: { "User-Agent": USER_AGENT },
  });
  const rawArticles = Array.isArray(payload?.articles) ? payload.articles : [];
  const selected: Array<{
    title: string;
    url: string;
    domain: string;
    seen: string;
  }> = [];
  const usedDomains = new Set<string>();

  for (const raw of rawArticles) {
    if (!raw || typeof raw !== "object") continue;
    const article = raw as JsonObject;
    const title = stringValue(article.title);
    const articleUrl = stringValue(article.url);
    if (!title || !articleUrl) continue;
    const domain = stringValue(article.domain) ?? hostname(articleUrl);
    if (!domain || usedDomains.has(domain.toLowerCase())) continue;
    usedDomains.add(domain.toLowerCase());
    selected.push({
      title,
      url: articleUrl,
      domain,
      seen: stringValue(article.seendate) ?? "",
    });
    if (selected.length >= 3) break;
  }

  if (selected.length < 2) {
    return abstain(
      "No encontré dos fuentes independientes suficientes para verificar esas noticias.",
    );
  }

  const sourceIds = selected.map((article) => article.url);
  return {
    claimKey: `news:${slug(topic)}`,
    value: selected
      .map((article) => `${normalize(article.title)}|${article.seen}`)
      .join("||"),
    displayText:
      `Noticias verificadas sobre ${topic}: ${selected[0].title}. También: ${selected[1].title}.`,
    sourceId: sourceIds[0],
    sourceIds,
    independentSourceCount: independentDomains(sourceIds),
    authoritative: false,
    observedAt: selected[0].seen || null,
  };
}

function githubHeaders(deps: ResearchDependencies): HeadersInit {
  const token = deps.env("GITHUB_PUBLIC_TOKEN")?.trim();
  return {
    "Accept": "application/vnd.github+json",
    "User-Agent": USER_AGENT,
    ...(token ? { "Authorization": `Bearer ${token}` } : {}),
  };
}

function decodeBase64Utf8(value: string): string {
  const binary = atob(value.replace(/\s+/g, ""));
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function pathScore(path: string, product: string): number {
  const normalizedPath = normalize(path).replace(/[^a-z0-9]+/g, " ");
  const tokens = normalize(product)
    .split(/\s+/)
    .filter((token) => token.length > 1);
  return tokens.reduce(
    (score, token) => score + (normalizedPath.includes(token) ? 1 : 0),
    0,
  );
}

async function searchTechApiPath(
  product: string,
  deps: ResearchDependencies,
): Promise<string | null> {
  const searchUrl = new URL("https://api.github.com/search/code");
  searchUrl.searchParams.set(
    "q",
    `${product} repo:GetTechAPI/TechAPI path:data/smartphone extension:json`,
  );
  searchUrl.searchParams.set("per_page", "5");

  const searchResponse = await deps.fetcher(searchUrl, {
    headers: githubHeaders(deps),
  });
  if (searchResponse.ok) {
    const payload = await searchResponse.json() as JsonObject;
    const items = Array.isArray(payload.items) ? payload.items : [];
    const candidates = items
      .filter((item): item is JsonObject => Boolean(item) && typeof item === "object")
      .map((item) => stringValue(item.path))
      .filter((path): path is string => Boolean(path))
      .filter((path) => path.startsWith("data/smartphone/") && path.endsWith(".json"))
      .sort((left, right) => pathScore(right, product) - pathScore(left, product));
    if (candidates[0]) return candidates[0];
  }

  const treeUrl =
    "https://api.github.com/repos/GetTechAPI/TechAPI/git/trees/develop?recursive=1";
  const tree = await fetchJson(deps, treeUrl, {
    headers: githubHeaders(deps),
  });
  const entries = Array.isArray(tree?.tree) ? tree.tree : [];
  const candidates = entries
    .filter((entry): entry is JsonObject =>
      Boolean(entry) && typeof entry === "object"
    )
    .map((entry) => stringValue(entry.path))
    .filter((path): path is string => Boolean(path))
    .filter((path) => path.startsWith("data/smartphone/") && path.endsWith(".json"))
    .map((path) => ({ path, score: pathScore(path, product) }))
    .filter((candidate) => candidate.score > 0)
    .sort((left, right) => right.score - left.score);

  return candidates[0]?.path ?? null;
}

async function fetchTechApiRecord(
  path: string,
  deps: ResearchDependencies,
): Promise<JsonObject | null> {
  const contentsUrl =
    `https://api.github.com/repos/GetTechAPI/TechAPI/contents/${path}?ref=develop`;
  const response = await deps.fetcher(contentsUrl, {
    headers: githubHeaders(deps),
  });
  if (!response.ok) return null;
  const payload = await response.json() as JsonObject;

  if (
    "verified" in payload ||
    "source_urls" in payload ||
    "battery_mah" in payload
  ) {
    return payload;
  }

  const encoded = stringValue(payload.content);
  if (encoded && stringValue(payload.encoding)?.toLowerCase() === "base64") {
    try {
      return JSON.parse(decodeBase64Utf8(encoded)) as JsonObject;
    } catch {
      return null;
    }
  }

  const downloadUrl = stringValue(payload.download_url);
  if (!downloadUrl) return null;
  return await fetchJson(deps, downloadUrl, {
    headers: { "User-Agent": USER_AGENT },
  });
}

function likelyOfficialSource(brand: string | null, source: string): boolean {
  const host = hostname(source);
  if (!host) return false;
  const normalizedBrand = normalize(brand ?? "");
  const officialHosts: Record<string, string[]> = {
    samsung: ["samsung.com"],
    redmagic: ["redmagic.gg"],
    apple: ["apple.com"],
    google: ["store.google.com", "google.com"],
    xiaomi: ["mi.com", "xiaomi.com"],
    oneplus: ["oneplus.com"],
    oppo: ["oppo.com"],
    vivo: ["vivo.com"],
    realme: ["realme.com"],
    motorola: ["motorola.com"],
    asus: ["asus.com"],
    sony: ["sony.com"],
    honor: ["honor.com"],
    huawei: ["huawei.com"],
    nothing: ["nothing.tech"],
  };
  return (officialHosts[normalizedBrand] ?? [])
    .some((official) => host === official || host.endsWith(`.${official}`));
}

function specsDisplay(record: JsonObject): string {
  const name = stringValue(record.name) ?? "Dispositivo";
  const soc = stringValue(record.soc);
  const battery = numberValue(record.battery_mah);
  const charging = numberValue(record.charging_wired_w);
  const ram = numberValue(record.ram_gb);
  const display = record.display && typeof record.display === "object"
    ? record.display as JsonObject
    : null;
  const refresh = numberValue(display?.refresh_hz);
  const parts = [
    soc ? `SoC ${soc}` : null,
    battery != null ? `batería ${battery} mAh` : null,
    charging != null ? `carga ${charging} W` : null,
    ram != null ? `${ram} GB RAM` : null,
    refresh != null ? `pantalla ${refresh} Hz` : null,
  ].filter(Boolean);
  return `${name}: ${parts.length ? parts.join(", ") : "ficha verificada disponible"}.`;
}

async function specificationEvidenceForProduct(
  product: string,
  deps: ResearchDependencies,
): Promise<ResearchResult> {
  const path = await searchTechApiPath(product, deps);
  if (!path) {
    return abstain("No encontré una ficha verificable para ese producto.");
  }

  const record = await fetchTechApiRecord(path, deps);
  if (!record || record.verified !== true) {
    return abstain(
      "Encontré una ficha, pero todavía no está marcada como verificada.",
    );
  }

  const sources = unique(stringArray(record.source_urls));
  if (!sources.length) {
    return abstain("La ficha no incluye fuentes verificables.");
  }

  const name = stringValue(record.name) ?? product;
  const brand = stringValue(record.brand);
  const value = JSON.stringify({
    soc: stringValue(record.soc),
    battery_mah: numberValue(record.battery_mah),
    charging_wired_w: numberValue(record.charging_wired_w),
    ram_gb: numberValue(record.ram_gb),
    storage_options_gb: record.storage_options_gb ?? null,
    display: record.display ?? null,
    ip_rating: stringValue(record.ip_rating),
  });

  return {
    claimKey: `spec:${slug(name)}`,
    value,
    displayText: specsDisplay(record),
    sourceId: sources[0],
    sourceIds: sources,
    independentSourceCount: Math.max(1, independentDomains(sources)),
    authoritative: sources.some((source) =>
      likelyOfficialSource(brand, source)
    ),
  };
}

function extractSpecProduct(query: string): string {
  return stripAssistantInvocation(query)
    .replace(
      /\b(?:especificaciones(?:\s+actuales)?|specs|specifications|ficha\s+tecnica)\b/gi,
      " ",
    )
    .replace(/\b(?:de|del|la|el|actuales?|current)\b/gi, " ")
    .replace(/[?¿!¡]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function extractComparisonProducts(query: string): [string, string] | null {
  const match = stripAssistantInvocation(query).match(
    /(?:compara|compare)\s+(?:el\s+|la\s+)?(.+?)\s+(?:con|vs\.?|versus)\s+(?:el\s+|la\s+)?([^\n?!]+)/i,
  );
  if (!match?.[1] || !match[2]) return null;
  return [match[1].trim(), match[2].trim()];
}

async function comparisonEvidence(
  query: string,
  deps: ResearchDependencies,
): Promise<ResearchResult> {
  const products = extractComparisonProducts(query);
  if (!products) {
    return abstain("Necesito dos productos claros para compararlos.");
  }

  const [left, right] = await Promise.all([
    specificationEvidenceForProduct(products[0], deps),
    specificationEvidenceForProduct(products[1], deps),
  ]);
  if (left.abstained || right.abstained) {
    return abstain(
      "No pude verificar con suficiente confianza las fichas de ambos productos.",
    );
  }

  const sources = unique([
    ...(left.sourceIds ?? (left.sourceId ? [left.sourceId] : [])),
    ...(right.sourceIds ?? (right.sourceId ? [right.sourceId] : [])),
  ]);

  return {
    claimKey: `comparison:${slug(products[0])}:${slug(products[1])}`,
    value: `${left.value ?? ""}||${right.value ?? ""}`,
    displayText:
      `Comparación verificada: ${left.displayText ?? products[0]} ${right.displayText ?? products[1]}`,
    sourceId: sources[0],
    sourceIds: sources,
    independentSourceCount: Math.max(1, independentDomains(sources)),
    authoritative: left.authoritative === true && right.authoritative === true,
  };
}

function extractPriceProduct(query: string): string {
  return stripAssistantInvocation(query)
    .replace(
      /\b(?:precio(?:\s+actual)?|price|cuanto\s+cuesta|cuánto\s+cuesta|valor)\b/gi,
      " ",
    )
    .replace(/\b(?:de|del|el|la|actual|current)\b/gi, " ")
    .replace(/[?¿!¡]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

async function priceEvidence(
  query: string,
  deps: ResearchDependencies,
): Promise<ResearchResult> {
  const token = deps.env("MERCADOLIBRE_ACCESS_TOKEN")?.trim();
  if (!token) {
    return abstain(
      "El proveedor de precio actual necesita una credencial de Mercado Libre configurada.",
    );
  }

  const product = extractPriceProduct(query);
  if (!product) return abstain("Necesito un producto para verificar su precio.");

  const url = new URL("https://api.mercadolibre.com/sites/MLC/search");
  url.searchParams.set("q", product);
  url.searchParams.set("limit", "10");

  const payload = await fetchJson(deps, url, {
    headers: {
      "Authorization": `Bearer ${token}`,
      "User-Agent": USER_AGENT,
    },
  });
  const rawResults = Array.isArray(payload?.results) ? payload.results : [];
  const listings = rawResults
    .filter((item): item is JsonObject =>
      Boolean(item) && typeof item === "object"
    )
    .map((item) => ({
      title: stringValue(item.title),
      price: numberValue(item.price),
      currency: stringValue(item.currency_id),
      url: stringValue(item.permalink),
      sellerId: item.seller && typeof item.seller === "object"
        ? stringValue((item.seller as JsonObject).id) ??
          String((item.seller as JsonObject).id ?? "")
        : "",
    }))
    .filter((item) =>
      item.title && item.price != null && item.price > 0 && item.currency &&
      item.url
    );

  if (!listings.length) {
    return abstain("No encontré publicaciones actuales con precio verificable.");
  }

  const prices = listings.map((item) => item.price as number).sort((a, b) => a - b);
  const middle = Math.floor(prices.length / 2);
  const median = prices.length % 2 === 0
    ? (prices[middle - 1] + prices[middle]) / 2
    : prices[middle];
  const currency = listings[0].currency as string;
  const sources = unique(
    listings.map((item) => item.url as string).slice(0, 5),
  );
  const sellerCount = new Set(
    listings.map((item) => item.sellerId).filter(Boolean),
  ).size;

  return {
    claimKey: `price:mlc:${slug(product)}`,
    value: `${currency}:${median}:${prices[0]}:${prices[prices.length - 1]}`,
    displayText:
      `En Mercado Libre Chile encontré ${listings.length} publicaciones de ${product}; precio mediano ${Math.round(median)} ${currency}, rango ${Math.round(prices[0])}-${Math.round(prices[prices.length - 1])} ${currency}.`,
    sourceId: sources[0],
    sourceIds: sources,
    independentSourceCount: Math.max(1, sellerCount),
    authoritative: true,
    observedAt: new Date().toISOString(),
  };
}



const GENERAL_KNOWLEDGE_STOP_WORDS = new Set([
  "a", "al", "an", "and", "are", "como", "cual", "cuales", "de", "del",
  "did", "dime", "do", "does", "el", "en", "es", "explica", "explicame",
  "explain", "fue", "how", "in", "is", "la", "las", "los", "me", "of",
  "on", "o", "para", "por", "porque", "que", "quien", "quienes", "son",
  "tell", "the", "un", "una", "what", "who", "why", "y",
]);

function generalKnowledgeTokens(value: string): string[] {
  const clean = normalize(stripAssistantInvocation(value))
    .replace(/[^a-z0-9]+/g, " ")
    .trim();

  return unique(
    clean
      .split(/\s+/)
      .filter((token) => token.length >= 2)
      .filter((token) => !GENERAL_KNOWLEDGE_STOP_WORDS.has(token)),
  );
}

function generalKnowledgeRelevance(
  query: string,
  title: string,
  extract: string,
): number {
  const tokens = generalKnowledgeTokens(query);
  if (!tokens.length) return 0;

  const haystack = ` ${normalize(`${title} ${extract}`)
    .replace(/[^a-z0-9]+/g, " ")} `;
  return tokens.filter((token) => haystack.includes(` ${token} `)).length;
}

function generalKnowledgeIsRelevant(
  query: string,
  title: string,
  extract: string,
): boolean {
  const tokenCount = generalKnowledgeTokens(query).length;
  if (!tokenCount) return false;
  const requiredMatches = tokenCount >= 2 ? 2 : 1;
  return generalKnowledgeRelevance(query, title, extract) >= requiredMatches;
}

function generalKnowledgeSearchText(query: string): string {
  const clean = stripAssistantInvocation(query)
    .replace(/[¿?¡!]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  return clean
    .replace(
      /^(?:explicame|explícame|dime|cuentame|cuéntame|que es|qué es|quien es|quién es|quien fue|quién fue|como funciona|cómo funciona)\s+/i,
      "",
    )
    .trim() || clean;
}

function conciseReferenceExtract(value: string): string {
  const compact = value.replace(/\s+/g, " ").trim();
  if (compact.length <= 720) return compact;

  const firstSentences = compact.match(/[^.!?]+[.!?]+/g)?.slice(0, 3).join(" ")
    .trim();
  if (firstSentences && firstSentences.length >= 120) {
    return firstSentences.slice(0, 720).trim();
  }
  return compact.slice(0, 717).trimEnd() + "...";
}

async function wikipediaGeneralKnowledgeEvidence(
  query: string,
  deps: ResearchDependencies,
  language: "es" | "en" = "es",
): Promise<ResearchResult> {
  const searchText = generalKnowledgeSearchText(query);
  if (!searchText) {
    return abstain("Necesito una pregunta más específica.");
  }

  const url = new URL(`https://${language}.wikipedia.org/w/api.php`);
  url.searchParams.set("action", "query");
  url.searchParams.set("generator", "search");
  url.searchParams.set("gsrsearch", searchText);
  url.searchParams.set("gsrlimit", "3");
  url.searchParams.set("prop", "extracts|info");
  url.searchParams.set("exintro", "1");
  url.searchParams.set("explaintext", "1");
  url.searchParams.set("inprop", "url");
  url.searchParams.set("format", "json");
  url.searchParams.set("utf8", "1");
  url.searchParams.set("origin", "*");

  const payload = await fetchJson(deps, url, {
    headers: {
      "User-Agent": USER_AGENT,
      "Accept": "application/json",
    },
  });
  const queryObject = payload?.query;
  if (!queryObject || typeof queryObject !== "object") {
    return abstain("No encontré una referencia fiable para esa pregunta.");
  }

  const pagesObject = (queryObject as JsonObject).pages;
  if (!pagesObject || typeof pagesObject !== "object") {
    return abstain("No encontré una referencia fiable para esa pregunta.");
  }

  const pages = Object.values(pagesObject as JsonObject)
    .filter((page): page is JsonObject => Boolean(page) && typeof page === "object")
    .sort((left, right) => {
      const leftIndex = numberValue(left.index) ?? Number.MAX_SAFE_INTEGER;
      const rightIndex = numberValue(right.index) ?? Number.MAX_SAFE_INTEGER;
      return leftIndex - rightIndex;
    });

  const selected = pages
    .map((page) => {
      const title = stringValue(page.title) ?? "";
      const extract = stringValue(page.extract) ?? "";
      return {
        page,
        title,
        extract,
        relevance: generalKnowledgeRelevance(query, title, extract),
      };
    })
    .filter((candidate) =>
      candidate.extract.length >= 80 &&
      generalKnowledgeIsRelevant(query, candidate.title, candidate.extract)
    )
    .sort((left, right) => right.relevance - left.relevance)[0];
  if (!selected) {
    return abstain(
      "No encontré una referencia suficientemente relacionada con la pregunta.",
    );
  }

  const title = selected.title || searchText;
  const extract = selected.extract;

  const pageUrl = stringValue(selected.page.fullurl) ??
    `https://${language}.wikipedia.org/wiki/${
      encodeURIComponent(title.replace(/\s+/g, "_"))
    }`;
  const answer = conciseReferenceExtract(extract);
  const sourceName = language === "es" ? "Wikipedia" : "Wikipedia (inglés)";

  return {
    claimKey: `general:${slug(title)}`,
    value: slug(title) || slug(searchText),
    displayText: `${answer} Fuente: ${sourceName}.`,
    sourceId: pageUrl,
    sourceIds: [pageUrl],
    independentSourceCount: 1,
    trustedReference: true,
  };
}

async function generalKnowledgeEvidence(
  query: string,
  deps: ResearchDependencies,
): Promise<ResearchResult> {
  const spanish = await wikipediaGeneralKnowledgeEvidence(query, deps, "es");
  if (!spanish.abstained) return spanish;
  return await wikipediaGeneralKnowledgeEvidence(query, deps, "en");
}

export async function routeResearchQuery(
  query: string,
  deps: ResearchDependencies,
  context = "",
  kind = "",
): Promise<ResearchResult> {
  const clean = normalize(query);
  const cleanContext = normalize(context);
  const combinedSignals = `${clean} ${cleanContext}`.trim();

  const weatherSignal = /\b(clima|tiempo de hoy|weather|pronostico|forecast)\b/;
  const newsSignal =
    /\b(noticias|news|salio nuevo|que salio nuevo|latest news|released)\b/;
  const priceSignal = /\b(precio|price|cuanto cuesta|valor)\b/;
  const comparisonSignal = /\b(compara|compare|versus|vs)\b/;
  const specsSignal =
    /\b(especificaciones|specs|specifications|ficha tecnica)\b/;

  if (
    weatherSignal.test(clean) ||
    (kind === "CURRENT_DATA" && weatherSignal.test(cleanContext))
  ) {
    return await weatherEvidence(query, deps);
  }
  if (
    newsSignal.test(clean) ||
    (kind === "CURRENT_DATA" && newsSignal.test(cleanContext))
  ) {
    return await newsEvidence(query, deps);
  }
  if (
    priceSignal.test(clean) ||
    (kind === "CURRENT_DATA" && priceSignal.test(cleanContext))
  ) {
    return await priceEvidence(query, deps);
  }
  if (
    comparisonSignal.test(clean) ||
    kind === "COMPARISON_RESEARCH" ||
    comparisonSignal.test(cleanContext)
  ) {
    const comparisonQuery = comparisonSignal.test(clean)
      ? query
      : context;
    return await comparisonEvidence(comparisonQuery, deps);
  }
  if (
    specsSignal.test(clean) ||
    specsSignal.test(cleanContext) ||
    (
      kind === "CURRENT_DATA" &&
      /\b(spec|specs|ficha|modelo|hardware)\b/.test(combinedSignals)
    )
  ) {
    return await specificationEvidenceForProduct(
      extractSpecProduct(query),
      deps,
    );
  }

  if (kind === "GENERAL_KNOWLEDGE") {
    return await generalKnowledgeEvidence(query, deps);
  }

  return abstain(
    "Esta consulta online todavía no tiene una fuente verificada configurada.",
  );
}
