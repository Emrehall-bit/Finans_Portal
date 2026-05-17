export default function AiResponseMeta({ metadata, providerUsed, fallbackUsed = false, dataQuality = "" }) {
  const meta = normalizeMetadata(metadata, providerUsed, fallbackUsed, dataQuality);
  if (!meta) {
    return null;
  }

  const segments = [];

  if (meta.deterministicFallbackUsed) {
    segments.push("Fallback analiz");
  } else if (meta.providerUsed) {
    const modelLabel = humanizeModel(meta.modelUsed);
    segments.push(modelLabel ? `AI: ${meta.providerUsed} · ${modelLabel}` : `AI: ${meta.providerUsed}`);
  }

  if (meta.cacheHit) {
    segments.push("Cache");
  }

  if (meta.fallbackUsed && !meta.deterministicFallbackUsed) {
    segments.push("Fallback provider");
  }

  const qualityLabel = qualityHint(meta.dataQuality);
  if (qualityLabel) {
    segments.push(qualityLabel);
  }

  if (meta.generatedAt) {
    const generatedLabel = formatGeneratedAt(meta.generatedAt);
    if (generatedLabel) {
      segments.push(generatedLabel);
    }
  }

  if (segments.length === 0) {
    return null;
  }

  return <p className="ai-response-meta">{segments.join(" · ")}</p>;
}

function normalizeMetadata(metadata, providerUsed, fallbackUsed, dataQuality) {
  if (metadata && typeof metadata === "object") {
    return {
      providerUsed: String(metadata.providerUsed || providerUsed || "").toUpperCase() || null,
      modelUsed: metadata.modelUsed || null,
      fallbackUsed: Boolean(metadata.fallbackUsed),
      aiEnhanced: Boolean(metadata.aiEnhanced),
      deterministicFallbackUsed: Boolean(metadata.deterministicFallbackUsed),
      cacheHit: Boolean(metadata.cacheHit),
      generatedAt: metadata.generatedAt || null,
      dataQuality: String(metadata.dataQuality || dataQuality || "").toUpperCase(),
    };
  }

  if (!providerUsed && !fallbackUsed && !dataQuality) {
    return null;
  }

  return {
    providerUsed: providerUsed ? String(providerUsed).toUpperCase() : null,
    modelUsed: null,
    fallbackUsed: Boolean(fallbackUsed),
    aiEnhanced: Boolean(providerUsed),
    deterministicFallbackUsed: !providerUsed,
    cacheHit: false,
    generatedAt: null,
    dataQuality: String(dataQuality || "").toUpperCase(),
  };
}

function humanizeModel(value) {
  const raw = String(value || "").trim();
  if (!raw) {
    return "";
  }

  if (raw.startsWith("llama-3.3")) {
    return "Llama 3.3";
  }
  if (raw.startsWith("llama-3.1")) {
    return "Llama 3.1";
  }
  if (raw.startsWith("gemini-2.5-flash-lite")) {
    return "Gemini 2.5 Flash Lite";
  }
  if (raw.startsWith("gemini-2.5-flash")) {
    return "Gemini 2.5 Flash";
  }
  if (raw.startsWith("gemini-1.5")) {
    return "Gemini 1.5";
  }

  return raw
    .split("-")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function qualityHint(value) {
  switch (String(value || "").toUpperCase()) {
    case "PARTIAL":
      return "Veri kismi";
    case "LOW":
    case "LIMITED":
    case "INSUFFICIENT":
      return "Veri sinirli";
    default:
      return "";
  }
}

function formatGeneratedAt(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });
}
