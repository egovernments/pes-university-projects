export const fmtInt = (n) =>
  n == null || Number.isNaN(Number(n))
    ? "—"
    : Math.round(Number(n)).toLocaleString("en-US");

export const fmtPct = (n, digits = 0) =>
  n == null || Number.isNaN(Number(n)) ? "—" : `${(Number(n) * 100).toFixed(digits)}%`;

// Human-friendly labels for the reconciliation match_status enum.
export const STATUS_META = {
  matched_exact: { label: "Matched (exact)", color: "#15803d", short: "Exact" },
  matched_fuzzy: { label: "Matched (fuzzy)", color: "#0d9488", short: "Fuzzy" },
  unmatched_microplan: { label: "Microplan only", color: "#64766e", short: "Microplan only" },
  unmatched_msp: { label: "MSP only", color: "#2563eb", short: "MSP only" },
};

export const statusMeta = (s) =>
  STATUS_META[s] || { label: s || "—", color: "#64748b", short: s || "—" };
