import { type ResearchDependencies, routeResearchQuery } from "./research.ts";

Deno.test("stable machine learning explanation works without configured models", async () => {
  const deps: ResearchDependencies = {
    fetcher: () => {
      throw new Error("stable local knowledge must not require network");
    },
    env: () => undefined,
  };

  const result = await routeResearchQuery(
    "Si alguien me pregunta por el aprendizaje automático, ¿cómo lo explicarías en pocas frases?",
    deps,
    "",
    "GENERAL_KNOWLEDGE",
  );

  if (result.abstained) {
    throw new Error(
      "stable machine-learning explanation must not abstain: " +
        (result.reasonCode ?? result.message ?? "unknown"),
    );
  }
  const answer = (result.displayText ?? "").toLowerCase();
  if (!answer.includes("aprendizaje automático") && !answer.includes("aprendizaje automatico")) {
    throw new Error("expected a grounded machine-learning explanation");
  }
});
