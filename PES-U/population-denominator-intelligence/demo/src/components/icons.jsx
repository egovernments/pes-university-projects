// Lightweight inline SVG icons (Lucide-style, stroke 1.75). No emoji, themeable.
const base = {
  width: "1em",
  height: "1em",
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.75,
  strokeLinecap: "round",
  strokeLinejoin: "round",
};

export const IconMap = (p) => (
  <svg {...base} {...p}>
    <path d="m9 6-6 3v12l6-3 6 3 6-3V6l-6 3-6-3Z" />
    <path d="M9 6v12M15 9v12" />
  </svg>
);

export const IconFilter = (p) => (
  <svg {...base} {...p}>
    <path d="M3 5h18l-7 8v6l-4-2v-4L3 5Z" />
  </svg>
);

export const IconLayers = (p) => (
  <svg {...base} {...p}>
    <path d="m12 3 9 5-9 5-9-5 9-5Z" />
    <path d="m3 14 9 5 9-5" />
  </svg>
);

export const IconPin = (p) => (
  <svg {...base} {...p}>
    <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
    <circle cx="12" cy="10" r="3" />
  </svg>
);

export const IconGauge = (p) => (
  <svg {...base} {...p}>
    <path d="M12 14a2 2 0 0 0 2-2c0-1.1-2-5-2-5s-2 3.9-2 5a2 2 0 0 0 2 2Z" />
    <path d="M4.5 18a9 9 0 1 1 15 0" />
  </svg>
);

export const IconShield = (p) => (
  <svg {...base} {...p}>
    <path d="M12 3 5 6v5c0 4.5 3 7.6 7 9 4-1.4 7-4.5 7-9V6l-7-3Z" />
    <path d="M12 8v4M12 15h.01" />
  </svg>
);
