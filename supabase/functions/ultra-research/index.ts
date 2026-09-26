import "jsr:@supabase/functions-js/edge-runtime.d.ts";

type ResearchRequest = {
  query?: string;
  kind?: string;
  requiresFreshData?: boolean;
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

function validApiKey(req: Request): boolean {
  const supplied = req.headers.get("apikey") ?? "";
  if (!supplied) return false;

  let publishableMatch = false;
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
    publishableMatch = Object.values(keys).includes(supplied);
  } catch {
    publishableMatch = false;
  }

  const legacy = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  return publishableMatch || (legacy.length > 0 && supplied === legacy);
}

function normalize(value: string): string {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .trim();
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

async function weatherEvidence(query: string) {
  const location = extractWeatherLocation(query);
  if (!location) {
    return {
      abstained: true,
      message: "Necesito una ciudad o ubicación en la pregunta para verificar el clima.",
    };
  }

  const geoUrl = new URL("https://geocoding-api.open-meteo.com/v1/search");
  geoUrl.searchParams.set("name", location);
  geoUrl.searchParams.set("count", "1");
  geoUrl.searchParams.set("language", "es");
  geoUrl.searchParams.set("format", "json");

  const geoResponse = await fetch(geoUrl, {
    headers: { "User-Agent": "GameHub-Ultra-CAR73/1.0" },
  });
  if (!geoResponse.ok) {
    return { abstained: true, message: "No pude verificar la ubicación." };
  }
  const geo = await geoResponse.json();
  const place = geo?.results?.[0];
  if (!place) {
    return { abstained: true, message: "No pude encontrar esa ubicación." };
  }

  const forecastUrl = new URL("https://api.open-meteo.com/v1/forecast");
  forecastUrl.searchParams.set("latitude", String(place.latitude));
  forecastUrl.searchParams.set("longitude", String(place.longitude));
  forecastUrl.searchParams.set("current", "temperature_2m,apparent_temperature,weather_code");
  forecastUrl.searchParams.set("timezone", "auto");

  const forecastResponse = await fetch(forecastUrl, {
    headers: { "User-Agent": "GameHub-Ultra-CAR73/1.0" },
  });
  if (!forecastResponse.ok) {
    return { abstained: true, message: "No pude verificar el clima actual." };
  }

  const forecast = await forecastResponse.json();
  const current = forecast?.current;
  if (
    !current ||
    typeof current.temperature_2m !== "number" ||
    typeof current.weather_code !== "number"
  ) {
    return { abstained: true, message: "La fuente climática no devolvió datos verificables." };
  }

  const temp = current.temperature_2m;
  const apparent = current.apparent_temperature;
  const code = current.weather_code;
  const description = weatherDescription(code);
  const placeLabel = [place.name, place.admin1, place.country]
    .filter(Boolean)
    .join(", ");

  const feelsLike =
    typeof apparent === "number" && Math.abs(apparent - temp) >= 1
      ? `, sensación térmica de ${apparent} °C`
      : "";

  return {
    claimKey: `weather:${place.latitude.toFixed(3)},${place.longitude.toFixed(3)}`,
    value: `${temp}|${code}|${current.time ?? ""}`,
    displayText: `En ${placeLabel}: ${temp} °C, ${description}${feelsLike}.`,
    sourceId: forecastUrl.toString(),
    authoritative: true,
    observedAt: current.time ?? null,
  };
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }
  if (!validApiKey(req)) {
    return json({ error: "Unauthorized" }, 401);
  }

  let body: ResearchRequest;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  const query = (body.query ?? "").trim();
  if (!query || query.length > 1200) {
    return json({ error: "Invalid query" }, 400);
  }

  const clean = normalize(query);
  if (/\b(clima|tiempo de hoy|weather|pronostico|forecast)\b/.test(clean)) {
    try {
      return json(await weatherEvidence(query));
    } catch {
      return json({
        abstained: true,
        message: "No pude verificar el clima con la fuente actual.",
      });
    }
  }

  return json({
    abstained: true,
    message: "Esta consulta online todavía no tiene una fuente verificada configurada.",
  });
});
