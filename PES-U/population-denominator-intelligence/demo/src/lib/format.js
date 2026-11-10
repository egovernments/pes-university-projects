export const fmtInt = (n) =>
  n == null || Number.isNaN(Number(n))
    ? "—"
    : Math.round(Number(n)).toLocaleString("en-US");

export const fmtPct = (n, digits = 0) =>
  n == null || Number.isNaN(Number(n)) ? "—" : `${(Number(n) * 100).toFixed(digits)}%`;
