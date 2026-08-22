
import { useState, useEffect, useRef } from "react";

const COLORS = {
  primary: "#1B6B4A",
  primaryContainer: "#A8F0C6",
  onPrimaryContainer: "#002111",
  secondary: "#4E6355",
  secondaryContainer: "#D0E8D5",
  onSecondaryContainer: "#0B1F14",
  tertiary: "#3B6470",
  tertiaryContainer: "#BFE9F6",
  surface: "#F6FBF3",
  surfaceContainer: "#EAEFE6",
  surfaceContainerLow: "#F0F5EC",
  surfaceContainerHigh: "#E4EAE0",
  surfaceDim: "#D7DCD3",
  onSurface: "#181D19",
  onSurfaceVariant: "#414942",
  outline: "#717971",
  outlineVariant: "#C1C9BF",
  error: "#BA1A1A",
  errorContainer: "#FFDAD6",
  onError: "#FFFFFF",
  scrim: "rgba(0,0,0,0.32)",
  inverseSurface: "#2D322C",
  inverseOnSurface: "#EEF2EA",
  shadow: "rgba(0,0,0,0.08)",
  expired: "#BA1A1A",
  expiredBg: "#FFF0EE",
  reserved: "#7C5800",
  reservedBg: "#FFEFD6",
  badge: "#FF3B30",
};

const DRUG_FORMS = ["Таблетки", "Капсулы", "Раствор", "Капли", "Мазь", "Сироп", "Порошок", "Суппозитории"];
const CATEGORIES = ["Обезболивающее", "Антибиотик", "Витамины", "Жаропонижающее", "Антигистаминное", "Сердечно-сосудистое"];

const MOCK_MEDKITS = [
  { id: 1, name: "Домашняя аптечка", location: "Ванная, верхняя полка", drugCount: 12, shared: false, users: 1 },
  { id: 2, name: "На работе", location: "Офис, стол", drugCount: 4, shared: false, users: 1 },
  { id: 3, name: "Семейная", location: "Кухня", drugCount: 8, shared: true, users: 3 },
];

const MOCK_DRUGS = [
  { id: 1, name: "Ибупрофен", form: "Таблетки", quantity: 20, unit: "шт", expiry: "2025-02-15", category: "Обезболивающее", manufacturer: "Фармстандарт", price: 120, dose: 1, reserved: 0, expired: true, note: "" },
  { id: 2, name: "Амоксициллин", form: "Капсулы", quantity: 14, unit: "шт", expiry: "2027-08-01", category: "Антибиотик", manufacturer: "АВВА РУС", price: 340, dose: 1, reserved: 6, expired: false, note: "Курс до конца недели" },
  { id: 3, name: "Витамин D3", form: "Капли", quantity: 18.5, unit: "мл", expiry: "2027-03-20", category: "Витамины", manufacturer: "Аквион", price: 450, dose: 0.5, reserved: 0, expired: false, note: "" },
  { id: 4, name: "Парацетамол", form: "Таблетки", quantity: 8, unit: "шт", expiry: "2026-12-01", category: "Жаропонижающее", manufacturer: "Фармстандарт", price: 55, dose: 1, reserved: 0, expired: false, note: "" },
  { id: 5, name: "Цетиризин", form: "Таблетки", quantity: 3, unit: "шт", expiry: "2026-06-15", category: "Антигистаминное", manufacturer: "Тева", price: 190, dose: 1, reserved: 3, expired: false, note: "" },
  { id: 6, name: "Нурофен Экспресс", form: "Капсулы", quantity: 0, unit: "шт", expiry: "2025-01-10", category: "Обезболивающее", manufacturer: "Рекитт", price: 280, dose: 1, reserved: 0, expired: true, note: "" },
];

const MOCK_SCHEDULE = [
  { id: 1, drugName: "Амоксициллин", time: "08:00", dose: "1 капс.", status: "taken" },
  { id: 2, drugName: "Витамин D3", time: "09:00", dose: "10 капель", status: "taken" },
  { id: 3, drugName: "Амоксициллин", time: "14:00", dose: "1 капс.", status: "pending" },
  { id: 4, drugName: "Цетиризин", time: "21:00", dose: "1 табл.", status: "upcoming" },
  { id: 5, drugName: "Амоксициллин", time: "22:00", dose: "1 капс.", status: "upcoming" },
];

const VIDAL_RESULTS = [
  { name: "Нурофен", form: "Таблетки", manufacturer: "Рекитт Бенкизер", category: "Обезболивающее" },
  { name: "Нурофен Экспресс", form: "Капсулы", manufacturer: "Рекитт Бенкизер", category: "Обезболивающее" },
  { name: "Нурофен Лонг", form: "Таблетки", manufacturer: "Рекитт Бенкизер", category: "Обезболивающее" },
  { name: "Нурофен для детей", form: "Сироп", manufacturer: "Рекитт Бенкизер", category: "Обезболивающее" },
];

// Icons as simple SVG components
const Icon = ({ name, size = 24, color = COLORS.onSurface }) => {
  const icons = {
    medkit: <path d="M20 6h-4V4c0-1.1-.9-2-2-2h-4c-1.1 0-2 .9-2 2v2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zM10 4h4v2h-4V4zm6 11h-3v3h-2v-3H8v-2h3v-3h2v3h3v2z"/>,
    pill: <path d="M4.22 11.29l5.07-5.07c1.95-1.95 5.12-1.95 7.07 0s1.95 5.12 0 7.07l-5.07 5.07c-1.95 1.95-5.12 1.95-7.07 0s-1.95-5.12 0-7.07zm8.49-1.41L10.59 12l-2.12-2.12 2.12-2.12 2.12 2.12z"/>,
    scan: <path d="M9.5 6.5v3h-3v-3h3M11 5H5v6h6V5zm-1.5 9.5v3h-3v-3h3M11 13H5v6h6v-6zm6.5-6.5v3h-3v-3h3M19 5h-6v6h6V5zm-6 8h1.5v1.5H13V13zm1.5 1.5H16V16h-1.5v-1.5zM16 13h1.5v1.5H16V13zm-3 3h1.5v1.5H13V16zm1.5 1.5H16V19h-1.5v-1.5zM16 16h1.5v1.5H16V16zm1.5-1.5H19V16h-1.5v-1.5zm0 3H19V19h-1.5v-1.5z"/>,
    calendar: <path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM9 10H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2z"/>,
    chart: <path d="M5 9.2h3V19H5V9.2zM10.6 5h2.8v14h-2.8V5zm5.6 8H19v6h-2.8v-6z"/>,
    settings: <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.49.49 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z"/>,
    back: <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>,
    add: <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>,
    search: <path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>,
    share: <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z"/>,
    delete: <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>,
    edit: <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.996.996 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>,
    check: <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>,
    close: <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>,
    warning: <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/>,
    person: <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>,
    people: <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/>,
    move: <path d="M10 9h4V6h3l-5-5-5 5h3v3zm-1 1H6V7l-5 5 5 5v-3h3v-4zm14 2l-5-5v3h-3v4h3v3l5-5zm-9 3h-4v3H7l5 5 5-5h-3v-3z"/>,
    qr: <path d="M3 11h8V3H3v8zm2-6h4v4H5V5zM3 21h8v-8H3v8zm2-6h4v4H5v-4zm8-12v8h8V3h-8zm6 6h-4V5h4v4zm-5.99 4h2v2h-2v-2zm2 2h2v2h-2v-2zm-2 2h2v2h-2v-2zm4 0h2v2h-2v-2zm2 2h2v2h-2v-2zm0-4h2v2h-2v-2zm2-2h2v2h-2v-2z"/>,
    clock: <path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"/>,
    filter: <path d="M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"/>,
    sort: <path d="M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z"/>,
    camera: <path d="M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4z"/>,
    notification: <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z"/>,
    inventory: <path d="M20 2H4c-1 0-2 1-2 2v3.01c0 .72.43 1.34 1 1.69V20c0 1.1 1.1 2 2 2h14c.9 0 2-.9 2-2V8.7c.57-.35 1-.97 1-1.69V4c0-1-1-2-2-2zm-5 12H9v-2h6v2zm5-7H4V4l16-.02V7z"/>,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color}>
      {icons[name] || icons.pill}
    </svg>
  );
};

// Phone frame wrapper
const PhoneFrame = ({ children, title }) => (
  <div style={{
    width: 375, minHeight: 720, maxHeight: 812,
    background: COLORS.surface,
    borderRadius: 40,
    border: `8px solid ${COLORS.inverseSurface}`,
    overflow: "hidden",
    display: "flex",
    flexDirection: "column",
    position: "relative",
    boxShadow: `0 25px 60px ${COLORS.scrim}, 0 8px 20px rgba(0,0,0,0.12)`,
  }}>
    {/* Status bar */}
    <div style={{
      height: 44, padding: "12px 24px 0",
      display: "flex", justifyContent: "space-between", alignItems: "center",
      fontSize: 12, fontWeight: 600, color: COLORS.onSurface,
    }}>
      <span>9:41</span>
      <div style={{ width: 126, height: 34, background: COLORS.onSurface, borderRadius: 17, position: "absolute", left: "50%", transform: "translateX(-50%)", top: 4 }} />
      <span style={{ display: "flex", gap: 4, alignItems: "center" }}>
        <span style={{ fontSize: 11 }}>5G</span>
        <svg width="16" height="12" viewBox="0 0 16 12" fill={COLORS.onSurface}><rect x="0" y="7" width="3" height="5" rx="0.5"/><rect x="4.5" y="4.5" width="3" height="7.5" rx="0.5"/><rect x="9" y="2" width="3" height="10" rx="0.5"/><rect x="13.5" y="0" width="2.5" height="12" rx="0.5"/></svg>
      </span>
    </div>
    <div style={{ flex: 1, overflow: "auto", display: "flex", flexDirection: "column" }}>
      {children}
    </div>
    {/* Home indicator */}
    <div style={{ height: 24, display: "flex", justifyContent: "center", alignItems: "center", paddingBottom: 4 }}>
      <div style={{ width: 134, height: 5, background: COLORS.onSurface, borderRadius: 3, opacity: 0.2 }} />
    </div>
  </div>
);

// Top App Bar
const TopBar = ({ title, onBack, actions, large, subtitle }) => (
  <div style={{
    padding: large ? "8px 16px 16px" : "8px 4px",
    background: COLORS.surface,
  }}>
    <div style={{ display: "flex", alignItems: "center", gap: 4, minHeight: 48 }}>
      {onBack && (
        <button onClick={onBack} style={{ all: "unset", cursor: "pointer", padding: 12, borderRadius: 20, display: "flex" }}>
          <Icon name="back" size={24} color={COLORS.onSurface} />
        </button>
      )}
      {!large && <span style={{ flex: 1, fontSize: 20, fontWeight: 500, color: COLORS.onSurface, paddingLeft: onBack ? 0 : 16, letterSpacing: 0.15 }}>{title}</span>}
      {large && <div style={{ flex: 1 }} />}
      {actions}
    </div>
    {large && (
      <div style={{ paddingLeft: 16, paddingTop: 4 }}>
        <div style={{ fontSize: 28, fontWeight: 600, color: COLORS.onSurface, letterSpacing: -0.5 }}>{title}</div>
        {subtitle && <div style={{ fontSize: 14, color: COLORS.onSurfaceVariant, marginTop: 2 }}>{subtitle}</div>}
      </div>
    )}
  </div>
);

// Bottom Navigation
const BottomNav = ({ active, onChange }) => {
  const items = [
    { key: "medkits", icon: "medkit", label: "Аптечки" },
    { key: "schedule", icon: "calendar", label: "План" },
    { key: "scanner", icon: "scan", label: "Сканер" },
    { key: "stats", icon: "chart", label: "Аналитика" },
    { key: "settings", icon: "settings", label: "Настройки" },
  ];
  return (
    <div style={{
      display: "flex", background: COLORS.surfaceContainer,
      borderTop: `1px solid ${COLORS.outlineVariant}`,
      paddingBottom: 2,
    }}>
      {items.map(it => (
        <button key={it.key} onClick={() => onChange(it.key)} style={{
          all: "unset", cursor: "pointer", flex: 1, display: "flex", flexDirection: "column",
          alignItems: "center", padding: "10px 0 6px", gap: 2,
        }}>
          <div style={{
            width: 56, height: 28, borderRadius: 14, display: "flex", alignItems: "center", justifyContent: "center",
            background: active === it.key ? COLORS.primaryContainer : "transparent",
            transition: "background 0.2s",
          }}>
            <Icon name={it.icon} size={22} color={active === it.key ? COLORS.primary : COLORS.onSurfaceVariant} />
          </div>
          <span style={{
            fontSize: 11, fontWeight: active === it.key ? 600 : 500, letterSpacing: 0.5,
            color: active === it.key ? COLORS.primary : COLORS.onSurfaceVariant,
          }}>{it.label}</span>
        </button>
      ))}
    </div>
  );
};

// Chip
const Chip = ({ label, selected, onClick, color }) => (
  <button onClick={onClick} style={{
    all: "unset", cursor: "pointer",
    padding: "6px 14px", borderRadius: 8,
    fontSize: 13, fontWeight: 500, letterSpacing: 0.1,
    background: selected ? (color || COLORS.primaryContainer) : COLORS.surfaceContainerHigh,
    color: selected ? COLORS.onPrimaryContainer : COLORS.onSurfaceVariant,
    border: `1px solid ${selected ? "transparent" : COLORS.outlineVariant}`,
    transition: "all 0.15s",
    whiteSpace: "nowrap",
  }}>{label}</button>
);

// FAB
const FAB = ({ icon, onClick, label }) => (
  <button onClick={onClick} style={{
    all: "unset", cursor: "pointer",
    position: "absolute", bottom: 80, right: 16,
    background: COLORS.primaryContainer, color: COLORS.onPrimaryContainer,
    borderRadius: label ? 16 : 16, padding: label ? "14px 20px" : 16,
    display: "flex", alignItems: "center", gap: 10,
    boxShadow: `0 4px 12px ${COLORS.scrim}`,
    fontWeight: 600, fontSize: 14, letterSpacing: 0.1,
    zIndex: 10,
  }}>
    <Icon name={icon} size={24} color={COLORS.onPrimaryContainer} />
    {label && <span>{label}</span>}
  </button>
);

// Drug card
const DrugCard = ({ drug, onClick, showMedkit }) => {
  const free = drug.quantity - drug.reserved;
  return (
    <button onClick={onClick} style={{
      all: "unset", cursor: "pointer", width: "100%", boxSizing: "border-box",
      background: drug.expired ? COLORS.expiredBg : COLORS.surfaceContainerLow,
      borderRadius: 16, padding: "14px 16px",
      border: drug.expired ? `1.5px solid ${COLORS.error}33` : `1px solid ${COLORS.outlineVariant}55`,
      display: "flex", flexDirection: "column", gap: 8,
      transition: "transform 0.1s",
    }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ fontSize: 15, fontWeight: 600, color: drug.expired ? COLORS.expired : COLORS.onSurface }}>{drug.name}</span>
            {drug.expired && <span style={{ fontSize: 10, fontWeight: 700, color: COLORS.onError, background: COLORS.expired, padding: "1px 6px", borderRadius: 4, letterSpacing: 0.5 }}>ПРОСРОЧЕН</span>}
          </div>
          <span style={{ fontSize: 13, color: COLORS.onSurfaceVariant }}>{drug.form} · {drug.manufacturer}</span>
        </div>
        <div style={{ textAlign: "right" }}>
          <div style={{ fontSize: 18, fontWeight: 700, color: drug.quantity === 0 ? COLORS.error : COLORS.onSurface }}>{drug.quantity} {drug.unit}</div>
          {drug.reserved > 0 && <div style={{ fontSize: 11, color: COLORS.reserved, fontWeight: 500 }}>🔒 {drug.reserved} заброн.</div>}
        </div>
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        <span style={{ fontSize: 11, color: COLORS.onSurfaceVariant, background: COLORS.surfaceContainerHigh, padding: "2px 8px", borderRadius: 6 }}>
          Годен до: {new Date(drug.expiry).toLocaleDateString("ru")}
        </span>
        {drug.category && <span style={{ fontSize: 11, color: COLORS.onSurfaceVariant, background: COLORS.surfaceContainerHigh, padding: "2px 8px", borderRadius: 6 }}>{drug.category}</span>}
        {drug.dose > 0 && <span style={{ fontSize: 11, color: COLORS.tertiary, background: COLORS.tertiaryContainer, padding: "2px 8px", borderRadius: 6 }}>Доза: {drug.dose} {drug.unit}</span>}
      </div>
    </button>
  );
};

// ─── SCREENS ───

const MedKitsScreen = ({ onSelect, onAdd }) => (
  <>
    <TopBar title="Мои аптечки" large subtitle="3 аптечки · 24 препарата" />
    <div style={{ flex: 1, padding: "0 16px 100px", display: "flex", flexDirection: "column", gap: 10 }}>
      {MOCK_MEDKITS.map(mk => (
        <button key={mk.id} onClick={() => onSelect(mk)} style={{
          all: "unset", cursor: "pointer", background: COLORS.surfaceContainerLow,
          borderRadius: 20, padding: "18px 20px",
          border: `1px solid ${COLORS.outlineVariant}55`,
          display: "flex", flexDirection: "column", gap: 10,
          transition: "transform 0.1s, box-shadow 0.15s",
        }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <div style={{
                width: 44, height: 44, borderRadius: 14,
                background: mk.shared ? COLORS.tertiaryContainer : COLORS.primaryContainer,
                display: "flex", alignItems: "center", justifyContent: "center",
              }}>
                <Icon name={mk.shared ? "people" : "medkit"} size={22} color={mk.shared ? COLORS.tertiary : COLORS.primary} />
              </div>
              <div>
                <div style={{ fontSize: 16, fontWeight: 600, color: COLORS.onSurface }}>{mk.name}</div>
                <div style={{ fontSize: 13, color: COLORS.onSurfaceVariant }}>{mk.location || "Без описания"}</div>
              </div>
            </div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <span style={{ fontSize: 12, color: COLORS.onSurfaceVariant, background: COLORS.surfaceContainerHigh, padding: "3px 10px", borderRadius: 8, fontWeight: 500 }}>
              💊 {mk.drugCount} препаратов
            </span>
            {mk.shared && <span style={{ fontSize: 12, color: COLORS.tertiary, background: COLORS.tertiaryContainer, padding: "3px 10px", borderRadius: 8, fontWeight: 500 }}>
              👥 {mk.users} пользов.
            </span>}
          </div>
        </button>
      ))}
    </div>
    <FAB icon="add" onClick={onAdd} label="Аптечка" />
  </>
);

const MedKitDetailScreen = ({ medkit, onBack, onDrug, onAdd, onShare }) => {
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState("name");
  const sorted = [...MOCK_DRUGS].sort((a, b) => {
    if (a.expired && !b.expired) return -1;
    if (!a.expired && b.expired) return 1;
    return 0;
  });
  const filtered = sorted.filter(d => !searchQuery || d.name.toLowerCase().includes(searchQuery.toLowerCase()));
  const expiredCount = MOCK_DRUGS.filter(d => d.expired).length;

  return (
    <>
      <TopBar title={medkit.name} onBack={onBack}
        actions={
          <div style={{ display: "flex", gap: 2 }}>
            <button onClick={() => setSearchOpen(!searchOpen)} style={{ all: "unset", cursor: "pointer", padding: 12, borderRadius: 20, display: "flex" }}>
              <Icon name="search" size={22} color={COLORS.onSurfaceVariant} />
            </button>
            {medkit.shared || true ? (
              <button onClick={onShare} style={{ all: "unset", cursor: "pointer", padding: 12, borderRadius: 20, display: "flex" }}>
                <Icon name="share" size={22} color={COLORS.onSurfaceVariant} />
              </button>
            ) : null}
            <button style={{ all: "unset", cursor: "pointer", padding: 12, borderRadius: 20, display: "flex" }}>
              <Icon name="filter" size={22} color={COLORS.onSurfaceVariant} />
            </button>
          </div>
        }
      />
      {searchOpen && (
        <div style={{ padding: "0 16px 12px" }}>
          <div style={{
            display: "flex", alignItems: "center", gap: 10, background: COLORS.surfaceContainerHigh,
            borderRadius: 28, padding: "10px 16px",
          }}>
            <Icon name="search" size={20} color={COLORS.onSurfaceVariant} />
            <input
              value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
              placeholder="Поиск препарата..."
              style={{ all: "unset", flex: 1, fontSize: 15, color: COLORS.onSurface }}
              autoFocus
            />
            {searchQuery && (
              <button onClick={() => setSearchQuery("")} style={{ all: "unset", cursor: "pointer", display: "flex" }}>
                <Icon name="close" size={18} color={COLORS.onSurfaceVariant} />
              </button>
            )}
          </div>
        </div>
      )}

      {expiredCount > 0 && (
        <div style={{
          margin: "0 16px 10px", padding: "10px 14px", borderRadius: 12,
          background: COLORS.errorContainer, display: "flex", alignItems: "center", gap: 10,
        }}>
          <Icon name="warning" size={20} color={COLORS.error} />
          <span style={{ fontSize: 13, fontWeight: 500, color: COLORS.error }}>
            {expiredCount} просроченных препаратов
          </span>
        </div>
      )}

      <div style={{ padding: "0 16px 12px", display: "flex", gap: 6, overflowX: "auto" }}>
        <Chip label="Все" selected={sortBy === "name"} onClick={() => setSortBy("name")} />
        <Chip label="Таблетки" selected={sortBy === "tab"} onClick={() => setSortBy("tab")} />
        <Chip label="С курсом" selected={sortBy === "course"} onClick={() => setSortBy("course")} />
        <Chip label="Мало" selected={sortBy === "low"} onClick={() => setSortBy("low")} />
      </div>

      <div style={{ flex: 1, padding: "0 16px 100px", display: "flex", flexDirection: "column", gap: 8 }}>
        {filtered.map(d => <DrugCard key={d.id} drug={d} onClick={() => onDrug(d)} />)}
      </div>
      <FAB icon="add" onClick={onAdd} />
    </>
  );
};

const DrugDetailScreen = ({ drug, onBack, onIntake, onEdit }) => (
  <>
    <TopBar title="Препарат" onBack={onBack}
      actions={
        <div style={{ display: "flex", gap: 2 }}>
          <button onClick={onEdit} style={{ all: "unset", cursor: "pointer", padding: 12, borderRadius: 20, display: "flex" }}>
            <Icon name="edit" size={22} color={COLORS.onSurfaceVariant} />
          </button>
          <button style={{ all: "unset", cursor: "pointer", padding: 12, borderRadius: 20, display: "flex" }}>
            <Icon name="delete" size={22} color={COLORS.error} />
          </button>
        </div>
      }
    />
    <div style={{ flex: 1, padding: "0 16px 24px", display: "flex", flexDirection: "column", gap: 16 }}>
      {/* Hero */}
      <div style={{
        background: drug.expired ? COLORS.expiredBg : `linear-gradient(135deg, ${COLORS.primaryContainer}, ${COLORS.secondaryContainer})`,
        borderRadius: 24, padding: "24px 20px", textAlign: "center",
        border: drug.expired ? `2px solid ${COLORS.error}44` : "none",
      }}>
        <div style={{ fontSize: 24, fontWeight: 700, color: drug.expired ? COLORS.error : COLORS.onPrimaryContainer }}>{drug.name}</div>
        <div style={{ fontSize: 14, color: COLORS.onSurfaceVariant, marginTop: 4 }}>{drug.form} · {drug.manufacturer}</div>
        {drug.expired && <div style={{ marginTop: 10, fontSize: 13, fontWeight: 600, color: COLORS.error, background: COLORS.errorContainer, display: "inline-block", padding: "4px 14px", borderRadius: 8 }}>⚠️ Срок годности истёк</div>}
        <div style={{ marginTop: 16, fontSize: 40, fontWeight: 800, color: drug.expired ? COLORS.error : COLORS.primary }}>
          {drug.quantity} <span style={{ fontSize: 18, fontWeight: 500 }}>{drug.unit}</span>
        </div>
        {drug.reserved > 0 && (
          <div style={{ fontSize: 13, color: COLORS.reserved, marginTop: 4 }}>
            🔒 Забронировано: {drug.reserved} {drug.unit} · Свободно: {drug.quantity - drug.reserved} {drug.unit}
          </div>
        )}
      </div>

      {/* Quick actions */}
      <div style={{ display: "flex", gap: 10 }}>
        <button onClick={onIntake} style={{
          all: "unset", cursor: "pointer", flex: 1, textAlign: "center",
          background: COLORS.primary, color: "#fff", padding: "14px 0",
          borderRadius: 16, fontWeight: 600, fontSize: 15,
        }}>Принять {drug.dose} {drug.unit}</button>
        <button style={{
          all: "unset", cursor: "pointer",
          background: COLORS.surfaceContainerHigh, padding: "14px 18px",
          borderRadius: 16, display: "flex", alignItems: "center",
        }}>
          <Icon name="move" size={22} color={COLORS.onSurfaceVariant} />
        </button>
      </div>

      {/* Info grid */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        {[
          { label: "Годен до", value: new Date(drug.expiry).toLocaleDateString("ru"), warn: drug.expired },
          { label: "Категория", value: drug.category },
          { label: "Однократная доза", value: `${drug.dose} ${drug.unit}` },
          { label: "Цена", value: drug.price ? `${drug.price} ₽` : "—" },
        ].map((item, i) => (
          <div key={i} style={{
            background: item.warn ? COLORS.errorContainer : COLORS.surfaceContainerLow,
            borderRadius: 14, padding: "12px 14px",
          }}>
            <div style={{ fontSize: 11, color: COLORS.onSurfaceVariant, fontWeight: 500, letterSpacing: 0.5, textTransform: "uppercase" }}>{item.label}</div>
            <div style={{ fontSize: 15, fontWeight: 600, color: item.warn ? COLORS.error : COLORS.onSurface, marginTop: 4 }}>{item.value}</div>
          </div>
        ))}
      </div>

      {drug.note && (
        <div style={{ background: COLORS.surfaceContainerLow, borderRadius: 14, padding: "12px 14px" }}>
          <div style={{ fontSize: 11, color: COLORS.onSurfaceVariant, fontWeight: 500, letterSpacing: 0.5, textTransform: "uppercase" }}>Заметка</div>
          <div style={{ fontSize: 14, color: COLORS.onSurface, marginTop: 4 }}>{drug.note}</div>
        </div>
      )}
    </div>
  </>
);

const AddDrugScreen = ({ onBack, onScan }) => {
  const [name, setName] = useState("Нуро");
  const [showSuggestions, setShowSuggestions] = useState(true);

  return (
    <>
      <TopBar title="Новый препарат" onBack={onBack}
        actions={
          <button style={{
            all: "unset", cursor: "pointer", padding: "8px 20px",
            background: COLORS.primary, color: "#fff", borderRadius: 20,
            fontWeight: 600, fontSize: 14, marginRight: 8,
          }}>Добавить</button>
        }
      />
      <div style={{ flex: 1, padding: "0 16px 24px", display: "flex", flexDirection: "column", gap: 14 }}>
        <button onClick={onScan} style={{
          all: "unset", cursor: "pointer", width: "100%", boxSizing: "border-box",
          background: `linear-gradient(135deg, ${COLORS.tertiaryContainer}, ${COLORS.secondaryContainer})`,
          borderRadius: 16, padding: "16px 20px",
          display: "flex", alignItems: "center", gap: 14,
        }}>
          <div style={{ width: 44, height: 44, borderRadius: 14, background: COLORS.tertiary + "22", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Icon name="scan" size={24} color={COLORS.tertiary} />
          </div>
          <div>
            <div style={{ fontSize: 15, fontWeight: 600, color: COLORS.onSurface }}>Сканировать штрихкод</div>
            <div style={{ fontSize: 12, color: COLORS.onSurfaceVariant }}>Автозаполнение из Честного знака</div>
          </div>
        </button>

        <div style={{ fontSize: 13, fontWeight: 500, color: COLORS.onSurfaceVariant, marginTop: 4 }}>Или введите вручную</div>

        {/* Name field with fuzzy search */}
        <div>
          <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.primary, letterSpacing: 0.4 }}>Название *</label>
          <input value={name} onChange={e => { setName(e.target.value); setShowSuggestions(true); }}
            style={{
              width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
              border: `1.5px solid ${COLORS.primary}`, borderRadius: 12, fontSize: 15,
              background: COLORS.surface, color: COLORS.onSurface, outline: "none",
            }}
          />
          {showSuggestions && name.length >= 2 && (
            <div style={{
              marginTop: 4, background: COLORS.surfaceContainerLow, borderRadius: 12,
              border: `1px solid ${COLORS.outlineVariant}`, overflow: "hidden",
              boxShadow: `0 4px 16px ${COLORS.shadow}`,
            }}>
              <div style={{ padding: "8px 14px", fontSize: 11, color: COLORS.onSurfaceVariant, fontWeight: 500, borderBottom: `1px solid ${COLORS.outlineVariant}` }}>
                Найдено в базе — нажмите для автозаполнения
              </div>
              {VIDAL_RESULTS.map((v, i) => (
                <button key={i} onClick={() => { setName(v.name); setShowSuggestions(false); }} style={{
                  all: "unset", cursor: "pointer", width: "100%", boxSizing: "border-box",
                  padding: "10px 14px", display: "flex", justifyContent: "space-between",
                  alignItems: "center", borderBottom: i < VIDAL_RESULTS.length - 1 ? `1px solid ${COLORS.outlineVariant}44` : "none",
                  fontSize: 14, color: COLORS.onSurface,
                }}>
                  <div>
                    <div style={{ fontWeight: 500 }}>{v.name}</div>
                    <div style={{ fontSize: 12, color: COLORS.onSurfaceVariant }}>{v.form} · {v.manufacturer}</div>
                  </div>
                  <span style={{ fontSize: 20, color: COLORS.primary }}>+</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Form selector */}
        <div>
          <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant, letterSpacing: 0.4 }}>Форма *</label>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
            {DRUG_FORMS.map(f => <Chip key={f} label={f} selected={f === "Таблетки"} />)}
          </div>
        </div>

        {/* Quantity + expiry row */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div>
            <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant, letterSpacing: 0.4 }}>Количество *</label>
            <input defaultValue="20" style={{
              width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
              border: `1.5px solid ${COLORS.outlineVariant}`, borderRadius: 12, fontSize: 15,
              background: COLORS.surface, color: COLORS.onSurface, outline: "none",
            }} />
          </div>
          <div>
            <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant, letterSpacing: 0.4 }}>Годен до *</label>
            <input defaultValue="12.2027" style={{
              width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
              border: `1.5px solid ${COLORS.outlineVariant}`, borderRadius: 12, fontSize: 15,
              background: COLORS.surface, color: COLORS.onSurface, outline: "none",
            }} />
          </div>
        </div>

        <div style={{ fontSize: 13, fontWeight: 500, color: COLORS.tertiary, marginTop: 2 }}>+ Дополнительные поля</div>
      </div>
    </>
  );
};

const ScannerScreen = ({ onBack }) => (
  <>
    <TopBar title="" onBack={onBack}
      actions={<span style={{ fontSize: 14, color: COLORS.onSurface, fontWeight: 500, marginRight: 16 }}>Помощь</span>} />
    <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "0 24px", gap: 20 }}>
      <div style={{
        width: 260, height: 260, borderRadius: 24, position: "relative",
        background: `linear-gradient(135deg, #1a1a2e, #16213e)`,
        display: "flex", alignItems: "center", justifyContent: "center",
        overflow: "hidden",
      }}>
        {/* Scanner corners */}
        {[{ top: 16, left: 16 }, { top: 16, right: 16 }, { bottom: 16, left: 16 }, { bottom: 16, right: 16 }].map((pos, i) => (
          <div key={i} style={{
            position: "absolute", ...pos, width: 40, height: 40,
            borderColor: COLORS.primaryContainer,
            borderWidth: 3, borderStyle: "solid",
            borderTop: pos.top !== undefined ? `3px solid ${COLORS.primaryContainer}` : "none",
            borderBottom: pos.bottom !== undefined ? `3px solid ${COLORS.primaryContainer}` : "none",
            borderLeft: pos.left !== undefined ? `3px solid ${COLORS.primaryContainer}` : "none",
            borderRight: pos.right !== undefined ? `3px solid ${COLORS.primaryContainer}` : "none",
            borderRadius: 8,
          }} />
        ))}
        {/* Scan line animation */}
        <div style={{
          position: "absolute", top: "40%", left: 24, right: 24, height: 2,
          background: `linear-gradient(90deg, transparent, ${COLORS.primary}, transparent)`,
          borderRadius: 1, opacity: 0.8,
        }} />
        <Icon name="scan" size={48} color={COLORS.primaryContainer + "66"} />
      </div>
      <div style={{ textAlign: "center" }}>
        <div style={{ fontSize: 18, fontWeight: 600, color: COLORS.onSurface }}>Наведите камеру на штрихкод</div>
        <div style={{ fontSize: 14, color: COLORS.onSurfaceVariant, marginTop: 6, lineHeight: 1.5 }}>
          DataMatrix код на упаковке препарата.{"\n"}Данные заполнятся автоматически.
        </div>
      </div>
      <button style={{
        all: "unset", cursor: "pointer", padding: "12px 32px",
        background: COLORS.surfaceContainerHigh, borderRadius: 20,
        fontSize: 14, fontWeight: 500, color: COLORS.onSurfaceVariant,
      }}>Ввести код вручную</button>
    </div>
  </>
);

const ScheduleScreen = () => {
  const [selectedDate, setSelectedDate] = useState(2);
  const dates = [
    { day: "Вс", num: 2 }, { day: "Пн", num: 3 }, { day: "Вт", num: 4 },
    { day: "Ср", num: 5 }, { day: "Чт", num: 6 }, { day: "Пт", num: 7 }, { day: "Сб", num: 8 },
  ];
  return (
    <>
      <TopBar title="План лечения" large subtitle="Март 2026" />
      {/* Date selector */}
      <div style={{ display: "flex", gap: 4, padding: "0 12px 14px", justifyContent: "space-between" }}>
        {dates.map(d => (
          <button key={d.num} onClick={() => setSelectedDate(d.num)} style={{
            all: "unset", cursor: "pointer", width: 42, textAlign: "center",
            padding: "8px 0", borderRadius: 14,
            background: d.num === selectedDate ? COLORS.primary : "transparent",
          }}>
            <div style={{ fontSize: 11, fontWeight: 500, color: d.num === selectedDate ? "#fff" : COLORS.onSurfaceVariant }}>{d.day}</div>
            <div style={{ fontSize: 17, fontWeight: 700, color: d.num === selectedDate ? "#fff" : COLORS.onSurface, marginTop: 2 }}>{d.num}</div>
            {d.num === 3 && <div style={{ width: 5, height: 5, borderRadius: 3, background: d.num === selectedDate ? "#fff" : COLORS.primary, margin: "3px auto 0" }} />}
          </button>
        ))}
      </div>

      <div style={{ flex: 1, padding: "0 16px 24px", display: "flex", flexDirection: "column", gap: 6 }}>
        {/* Timeline */}
        {MOCK_SCHEDULE.map((item, i) => (
          <div key={item.id} style={{
            display: "flex", gap: 14, alignItems: "flex-start", padding: "10px 0",
          }}>
            {/* Time + dot */}
            <div style={{ width: 50, textAlign: "right", paddingTop: 2 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: item.status === "taken" ? COLORS.onSurfaceVariant : COLORS.onSurface }}>{item.time}</div>
            </div>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", paddingTop: 6 }}>
              <div style={{
                width: 12, height: 12, borderRadius: 6,
                background: item.status === "taken" ? COLORS.primary : item.status === "pending" ? COLORS.tertiary : COLORS.outlineVariant,
                border: item.status === "pending" ? `2px solid ${COLORS.tertiary}` : "none",
              }} />
              {i < MOCK_SCHEDULE.length - 1 && <div style={{ width: 2, flex: 1, minHeight: 30, background: COLORS.outlineVariant, marginTop: 4 }} />}
            </div>
            {/* Card */}
            <div style={{
              flex: 1, background: item.status === "taken" ? COLORS.surfaceContainerHigh : item.status === "pending" ? COLORS.tertiaryContainer : COLORS.surfaceContainerLow,
              borderRadius: 14, padding: "12px 14px",
              opacity: item.status === "taken" ? 0.7 : 1,
              border: item.status === "pending" ? `1.5px solid ${COLORS.tertiary}55` : `1px solid ${COLORS.outlineVariant}44`,
            }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: COLORS.onSurface, textDecoration: item.status === "taken" ? "line-through" : "none" }}>{item.drugName}</div>
                  <div style={{ fontSize: 12, color: COLORS.onSurfaceVariant }}>{item.dose}</div>
                </div>
                {item.status === "taken" && <Icon name="check" size={20} color={COLORS.primary} />}
                {item.status === "pending" && (
                  <button style={{
                    all: "unset", cursor: "pointer", background: COLORS.primary, color: "#fff",
                    padding: "6px 14px", borderRadius: 20, fontSize: 12, fontWeight: 600,
                  }}>Принять</button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </>
  );
};

const ShareScreen = ({ onBack }) => (
  <>
    <TopBar title="Поделиться аптечкой" onBack={onBack} />
    <div style={{ flex: 1, padding: "0 16px 24px", display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{
        background: COLORS.errorContainer, borderRadius: 14, padding: "14px 16px",
        display: "flex", gap: 12, alignItems: "flex-start",
      }}>
        <Icon name="warning" size={22} color={COLORS.error} />
        <div style={{ fontSize: 13, color: COLORS.error, lineHeight: 1.5 }}>
          Генерация кода — необратимое действие. Аптечка будет синхронизироваться через сервер. Переданный доступ нельзя отозвать.
        </div>
      </div>

      <div style={{ textAlign: "center", padding: "16px 0" }}>
        <div style={{ fontSize: 15, fontWeight: 600, color: COLORS.onSurface, marginBottom: 16 }}>QR-код (действует 15 минут)</div>
        <div style={{
          width: 200, height: 200, margin: "0 auto", background: "#fff",
          borderRadius: 16, display: "flex", alignItems: "center", justifyContent: "center",
          border: `2px solid ${COLORS.outlineVariant}`,
        }}>
          <Icon name="qr" size={160} color={COLORS.onSurface} />
        </div>
        <div style={{ fontSize: 12, color: COLORS.onSurfaceVariant, marginTop: 8 }}>
          Покажите код другому пользователю
        </div>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <div style={{ flex: 1, height: 1, background: COLORS.outlineVariant }} />
        <span style={{ fontSize: 13, color: COLORS.onSurfaceVariant }}>или</span>
        <div style={{ flex: 1, height: 1, background: COLORS.outlineVariant }} />
      </div>

      <div style={{
        background: COLORS.surfaceContainerLow, borderRadius: 16, padding: "16px 18px",
        display: "flex", justifyContent: "space-between", alignItems: "center",
      }}>
        <div>
          <div style={{ fontSize: 12, color: COLORS.onSurfaceVariant }}>Текстовый код (1 час)</div>
          <div style={{ fontSize: 20, fontWeight: 700, color: COLORS.primary, letterSpacing: 4, marginTop: 4, fontFamily: "monospace" }}>MK-7X2F-9B</div>
        </div>
        <button style={{
          all: "unset", cursor: "pointer", background: COLORS.primaryContainer, padding: "10px 18px",
          borderRadius: 12, fontSize: 13, fontWeight: 600, color: COLORS.onPrimaryContainer,
        }}>Скопировать</button>
      </div>

      <div style={{ marginTop: 8 }}>
        <button style={{
          all: "unset", cursor: "pointer", width: "100%", boxSizing: "border-box",
          textAlign: "center", padding: "14px 0",
          background: COLORS.surfaceContainerHigh, borderRadius: 16,
          fontSize: 15, fontWeight: 500, color: COLORS.onSurface,
        }}>Подключиться к аптечке</button>
      </div>
    </div>
  </>
);

const StatsScreen = () => (
  <>
    <TopBar title="Аналитика" large />
    <div style={{ flex: 1, padding: "0 16px 24px", display: "flex", flexDirection: "column", gap: 14 }}>
      {/* Summary cards */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        {[
          { label: "Всего препаратов", value: "24", icon: "pill", bg: COLORS.primaryContainer },
          { label: "Просрочено", value: "2", icon: "warning", bg: COLORS.errorContainer },
          { label: "Активных курсов", value: "3", icon: "calendar", bg: COLORS.tertiaryContainer },
          { label: "Общая стоимость", value: "3 240 ₽", icon: "inventory", bg: COLORS.secondaryContainer },
        ].map((card, i) => (
          <div key={i} style={{
            background: card.bg, borderRadius: 18, padding: "16px 14px",
            display: "flex", flexDirection: "column", gap: 8,
          }}>
            <Icon name={card.icon} size={22} color={COLORS.onPrimaryContainer} />
            <div>
              <div style={{ fontSize: 22, fontWeight: 700, color: COLORS.onPrimaryContainer }}>{card.value}</div>
              <div style={{ fontSize: 11, color: COLORS.onPrimaryContainer + "cc", fontWeight: 500 }}>{card.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Forecast */}
      <div style={{ background: COLORS.surfaceContainerLow, borderRadius: 18, padding: "16px 18px" }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: COLORS.onSurface, marginBottom: 12 }}>Прогноз остатков (через 30 дней)</div>
        {[
          { name: "Амоксициллин", current: 14, predicted: 0, pct: 0 },
          { name: "Витамин D3", current: 18.5, predicted: 3.5, pct: 19 },
          { name: "Цетиризин", current: 3, predicted: 0, pct: 0 },
          { name: "Парацетамол", current: 8, predicted: 8, pct: 100 },
        ].map((item, i) => (
          <div key={i} style={{ marginBottom: 12 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, marginBottom: 4 }}>
              <span style={{ fontWeight: 500, color: COLORS.onSurface }}>{item.name}</span>
              <span style={{ color: item.predicted === 0 ? COLORS.error : COLORS.onSurfaceVariant, fontWeight: 600 }}>
                {item.predicted === 0 ? "Закончится!" : `→ ${item.predicted}`}
              </span>
            </div>
            <div style={{ height: 6, background: COLORS.surfaceContainerHigh, borderRadius: 3, overflow: "hidden" }}>
              <div style={{
                height: "100%", borderRadius: 3, width: `${item.pct}%`,
                background: item.pct === 0 ? COLORS.error : item.pct < 30 ? COLORS.reserved : COLORS.primary,
                transition: "width 0.5s",
              }} />
            </div>
          </div>
        ))}
      </div>

      {/* Categories */}
      <div style={{ background: COLORS.surfaceContainerLow, borderRadius: 18, padding: "16px 18px" }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: COLORS.onSurface, marginBottom: 12 }}>По категориям</div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {[
            { label: "Обезболивающее", count: 6, color: "#E8DEF8" },
            { label: "Антибиотик", count: 4, color: "#FFD8E4" },
            { label: "Витамины", count: 5, color: "#D0BCFF" },
            { label: "Жаропонижающее", count: 3, color: "#FFE082" },
            { label: "Антигистаминное", count: 4, color: "#B2DFDB" },
            { label: "Сердечно-сосуд.", count: 2, color: "#FFCCBC" },
          ].map((cat, i) => (
            <div key={i} style={{
              background: cat.color, borderRadius: 10, padding: "8px 12px",
              fontSize: 12, fontWeight: 600, color: "#333",
            }}>{cat.label} ({cat.count})</div>
          ))}
        </div>
      </div>
    </div>
  </>
);

const SettingsScreen = () => (
  <>
    <TopBar title="Настройки" large />
    <div style={{ flex: 1, padding: "0 16px 24px", display: "flex", flexDirection: "column", gap: 2 }}>
      {[
        { section: "Уведомления", items: [
          { label: "Напоминания о приёме", sub: "За 5 минут до", toggle: true },
          { label: "Срок годности", sub: "За 30 дней", toggle: true },
          { label: "Нехватка препарата", sub: "При запасе менее 3 дней", toggle: true },
          { label: "Время по умолчанию", sub: "Утро: 8:00 · День: 14:00 · Вечер: 21:00" },
        ]},
        { section: "Синхронизация", items: [
          { label: "Интервал опроса", sub: "Каждые 15 минут" },
          { label: "Синхронизация по Wi-Fi", sub: "Экономия мобильных данных", toggle: true, off: true },
        ]},
        { section: "Безопасность", items: [
          { label: "Ключ устройства", sub: "Android Keystore · SHA-256" },
          { label: "Экспорт данных", sub: "Скачать локальную копию" },
        ]},
        { section: "О приложении", items: [
          { label: "MedApp", sub: "Версия 1.0.0 · Курсовая работа" },
        ]},
      ].map((section, si) => (
        <div key={si} style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: COLORS.primary, letterSpacing: 0.5, padding: "8px 4px", textTransform: "uppercase" }}>{section.section}</div>
          {section.items.map((item, ii) => (
            <div key={ii} style={{
              display: "flex", justifyContent: "space-between", alignItems: "center",
              padding: "14px 4px", borderBottom: `1px solid ${COLORS.outlineVariant}33`,
            }}>
              <div>
                <div style={{ fontSize: 15, color: COLORS.onSurface }}>{item.label}</div>
                <div style={{ fontSize: 12, color: COLORS.onSurfaceVariant, marginTop: 2 }}>{item.sub}</div>
              </div>
              {item.toggle !== undefined && (
                <div style={{
                  width: 44, height: 26, borderRadius: 13,
                  background: item.off ? COLORS.surfaceContainerHigh : COLORS.primary,
                  display: "flex", alignItems: "center",
                  padding: "0 3px", boxSizing: "border-box",
                  justifyContent: item.off ? "flex-start" : "flex-end",
                }}>
                  <div style={{ width: 20, height: 20, borderRadius: 10, background: "#fff", boxShadow: `0 1px 3px ${COLORS.scrim}` }} />
                </div>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  </>
);

const IntakeDialog = ({ drug, onClose, onConfirm }) => (
  <div style={{
    position: "absolute", inset: 0, background: COLORS.scrim, display: "flex",
    alignItems: "flex-end", justifyContent: "center", zIndex: 100,
  }}>
    <div style={{
      background: COLORS.surface, borderRadius: "28px 28px 0 0", padding: "24px 24px 32px",
      width: "100%", boxSizing: "border-box",
    }}>
      <div style={{ width: 40, height: 4, borderRadius: 2, background: COLORS.outlineVariant, margin: "0 auto 20px" }} />
      <div style={{ fontSize: 20, fontWeight: 600, color: COLORS.onSurface, marginBottom: 4 }}>Приём препарата</div>
      <div style={{ fontSize: 14, color: COLORS.onSurfaceVariant, marginBottom: 20 }}>{drug.name} · {drug.form}</div>

      {drug.expired && (
        <div style={{ background: COLORS.errorContainer, borderRadius: 12, padding: "10px 14px", marginBottom: 16, display: "flex", gap: 10, alignItems: "center" }}>
          <Icon name="warning" size={20} color={COLORS.error} />
          <span style={{ fontSize: 13, color: COLORS.error, fontWeight: 500 }}>Препарат просрочен! Продолжить?</span>
        </div>
      )}

      <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant }}>Количество ({drug.unit})</label>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 8 }}>
        <button style={{
          all: "unset", cursor: "pointer", width: 44, height: 44, borderRadius: 14,
          background: COLORS.surfaceContainerHigh, display: "flex", alignItems: "center", justifyContent: "center",
          fontSize: 22, fontWeight: 600, color: COLORS.onSurface,
        }}>−</button>
        <input defaultValue={drug.dose} style={{
          flex: 1, textAlign: "center", padding: "12px", fontSize: 28, fontWeight: 700,
          border: `1.5px solid ${COLORS.outlineVariant}`, borderRadius: 14,
          background: COLORS.surface, color: COLORS.onSurface, outline: "none",
        }} />
        <button style={{
          all: "unset", cursor: "pointer", width: 44, height: 44, borderRadius: 14,
          background: COLORS.surfaceContainerHigh, display: "flex", alignItems: "center", justifyContent: "center",
          fontSize: 22, fontWeight: 600, color: COLORS.onSurface,
        }}>+</button>
      </div>

      <div style={{ display: "flex", gap: 10, marginTop: 24 }}>
        <button onClick={onClose} style={{
          all: "unset", cursor: "pointer", flex: 1, textAlign: "center",
          padding: "14px 0", borderRadius: 16, fontSize: 15, fontWeight: 600,
          background: COLORS.surfaceContainerHigh, color: COLORS.onSurface,
        }}>Отмена</button>
        <button onClick={onConfirm} style={{
          all: "unset", cursor: "pointer", flex: 1, textAlign: "center",
          padding: "14px 0", borderRadius: 16, fontSize: 15, fontWeight: 600,
          background: COLORS.primary, color: "#fff",
        }}>Принять</button>
      </div>
    </div>
  </div>
);

const CreateMedKitScreen = ({ onBack }) => (
  <>
    <TopBar title="Новая аптечка" onBack={onBack}
      actions={
        <button style={{
          all: "unset", cursor: "pointer", padding: "8px 20px",
          background: COLORS.primary, color: "#fff", borderRadius: 20,
          fontWeight: 600, fontSize: 14, marginRight: 8,
        }}>Создать</button>
      }
    />
    <div style={{ flex: 1, padding: "0 16px 24px", display: "flex", flexDirection: "column", gap: 16 }}>
      <div>
        <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.primary, letterSpacing: 0.4 }}>Название *</label>
        <input defaultValue="" placeholder="Например: Дачная аптечка"
          style={{
            width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
            border: `1.5px solid ${COLORS.primary}`, borderRadius: 12, fontSize: 15,
            background: COLORS.surface, color: COLORS.onSurface, outline: "none",
          }}
        />
      </div>
      <div>
        <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant, letterSpacing: 0.4 }}>Место хранения</label>
        <input defaultValue="" placeholder="Ванная, верхняя полка"
          style={{
            width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
            border: `1.5px solid ${COLORS.outlineVariant}`, borderRadius: 12, fontSize: 15,
            background: COLORS.surface, color: COLORS.onSurface, outline: "none",
          }}
        />
      </div>
    </div>
  </>
);

const TreatmentPlanScreen = ({ drug, onBack }) => (
  <>
    <TopBar title="Курс лечения" onBack={onBack}
      actions={
        <button style={{
          all: "unset", cursor: "pointer", padding: "8px 20px",
          background: COLORS.primary, color: "#fff", borderRadius: 20,
          fontWeight: 600, fontSize: 14, marginRight: 8,
        }}>Сохранить</button>
      }
    />
    <div style={{ flex: 1, padding: "0 16px 24px", display: "flex", flexDirection: "column", gap: 14 }}>
      <div style={{
        background: COLORS.primaryContainer, borderRadius: 16, padding: "14px 18px",
        display: "flex", alignItems: "center", gap: 12,
      }}>
        <Icon name="pill" size={24} color={COLORS.primary} />
        <div>
          <div style={{ fontSize: 15, fontWeight: 600, color: COLORS.onPrimaryContainer }}>{drug?.name || "Амоксициллин"}</div>
          <div style={{ fontSize: 12, color: COLORS.onPrimaryContainer + "bb" }}>Остаток: {drug?.quantity || 14} {drug?.unit || "шт"}</div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <div>
          <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant }}>Доза за приём</label>
          <input defaultValue="1" style={{
            width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
            border: `1.5px solid ${COLORS.outlineVariant}`, borderRadius: 12, fontSize: 15,
            background: COLORS.surface, color: COLORS.onSurface, outline: "none",
          }} />
        </div>
        <div>
          <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant }}>Приёмов в день</label>
          <input defaultValue="3" style={{
            width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
            border: `1.5px solid ${COLORS.outlineVariant}`, borderRadius: 12, fontSize: 15,
            background: COLORS.surface, color: COLORS.onSurface, outline: "none",
          }} />
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <div>
          <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant }}>Начало</label>
          <input defaultValue="05.03.2026" style={{
            width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
            border: `1.5px solid ${COLORS.outlineVariant}`, borderRadius: 12, fontSize: 15,
            background: COLORS.surface, color: COLORS.onSurface, outline: "none",
          }} />
        </div>
        <div>
          <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant }}>Конец</label>
          <input defaultValue="09.03.2026" style={{
            width: "100%", boxSizing: "border-box", padding: "12px 14px", marginTop: 4,
            border: `1.5px solid ${COLORS.outlineVariant}`, borderRadius: 12, fontSize: 15,
            background: COLORS.surface, color: COLORS.onSurface, outline: "none",
          }} />
        </div>
      </div>

      <div>
        <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant }}>Дни недели</label>
        <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
          {["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"].map((d, i) => (
            <button key={d} style={{
              all: "unset", cursor: "pointer", width: 38, height: 38, borderRadius: 12,
              background: i < 5 ? COLORS.primaryContainer : COLORS.surfaceContainerHigh,
              color: i < 5 ? COLORS.primary : COLORS.onSurfaceVariant,
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: 13, fontWeight: 600,
            }}>{d}</button>
          ))}
        </div>
      </div>

      <div>
        <label style={{ fontSize: 12, fontWeight: 500, color: COLORS.onSurfaceVariant }}>Время приёма</label>
        <div style={{ display: "flex", gap: 8, marginTop: 6, flexWrap: "wrap" }}>
          {["08:00", "14:00", "22:00"].map(t => (
            <div key={t} style={{
              background: COLORS.tertiaryContainer, borderRadius: 10, padding: "8px 14px",
              fontSize: 14, fontWeight: 600, color: COLORS.tertiary,
              display: "flex", alignItems: "center", gap: 6,
            }}>
              <Icon name="clock" size={16} color={COLORS.tertiary} />
              {t}
            </div>
          ))}
          <button style={{
            all: "unset", cursor: "pointer", padding: "8px 14px",
            borderRadius: 10, border: `1.5px dashed ${COLORS.outlineVariant}`,
            fontSize: 14, color: COLORS.onSurfaceVariant, fontWeight: 500,
          }}>+ Время</button>
        </div>
      </div>

      <div style={{
        background: COLORS.secondaryContainer, borderRadius: 14, padding: "12px 16px", marginTop: 4,
      }}>
        <div style={{ fontSize: 13, color: COLORS.onSecondaryContainer }}>
          Потребуется: <strong>15 капс.</strong> (3×1 на 5 дн.)
        </div>
        <div style={{ fontSize: 13, color: COLORS.onSecondaryContainer, marginTop: 4 }}>
          В наличии: <strong>14 капс.</strong> — хватит на <strong>4 дня 2 приёма</strong>
        </div>
      </div>
    </div>
  </>
);


// ─── MAIN APP ───

export default function MedAppPrototype() {
  const [screen, setScreen] = useState("medkits");
  const [tab, setTab] = useState("medkits");
  const [selectedMedkit, setSelectedMedkit] = useState(null);
  const [selectedDrug, setSelectedDrug] = useState(null);
  const [showIntake, setShowIntake] = useState(false);
  const [history, setHistory] = useState([]);

  const navigate = (s, data) => {
    setHistory(h => [...h, { screen, tab, selectedMedkit, selectedDrug }]);
    setScreen(s);
    if (data?.medkit) setSelectedMedkit(data.medkit);
    if (data?.drug) setSelectedDrug(data.drug);
  };

  const goBack = () => {
    const prev = history[history.length - 1];
    if (prev) {
      setScreen(prev.screen);
      setTab(prev.tab);
      setSelectedMedkit(prev.selectedMedkit);
      setSelectedDrug(prev.selectedDrug);
      setHistory(h => h.slice(0, -1));
    }
  };

  const handleTab = (t) => {
    setTab(t);
    setScreen(t);
    setHistory([]);
    setSelectedMedkit(null);
    setSelectedDrug(null);
  };

  const screenNames = {
    medkits: "Аптечки",
    medkit_detail: "Аптечка",
    drug_detail: "Препарат",
    add_drug: "Добавление",
    scanner: "Сканер",
    schedule: "План лечения",
    stats: "Аналитика",
    settings: "Настройки",
    share: "Поделиться",
    create_medkit: "Новая аптечка",
    treatment: "Курс лечения",
  };

  const renderScreen = () => {
    switch (screen) {
      case "medkits":
        return <MedKitsScreen
          onSelect={mk => navigate("medkit_detail", { medkit: mk })}
          onAdd={() => navigate("create_medkit")}
        />;
      case "medkit_detail":
        return <MedKitDetailScreen medkit={selectedMedkit || MOCK_MEDKITS[0]}
          onBack={goBack}
          onDrug={d => navigate("drug_detail", { drug: d })}
          onAdd={() => navigate("add_drug")}
          onShare={() => navigate("share")}
        />;
      case "drug_detail":
        return <DrugDetailScreen drug={selectedDrug || MOCK_DRUGS[0]}
          onBack={goBack}
          onIntake={() => setShowIntake(true)}
          onEdit={() => navigate("treatment", { drug: selectedDrug })}
        />;
      case "add_drug":
        return <AddDrugScreen onBack={goBack} onScan={() => navigate("scanner")} />;
      case "scanner":
        return <ScannerScreen onBack={goBack} />;
      case "schedule":
        return <ScheduleScreen />;
      case "stats":
        return <StatsScreen />;
      case "settings":
        return <SettingsScreen />;
      case "share":
        return <ShareScreen onBack={goBack} />;
      case "create_medkit":
        return <CreateMedKitScreen onBack={goBack} />;
      case "treatment":
        return <TreatmentPlanScreen drug={selectedDrug} onBack={goBack} />;
      default:
        return <MedKitsScreen onSelect={mk => navigate("medkit_detail", { medkit: mk })} onAdd={() => navigate("create_medkit")} />;
    }
  };

  const showBottomNav = ["medkits", "schedule", "stats", "settings", "scanner"].includes(screen);

  return (
    <div style={{
      minHeight: "100vh",
      background: "linear-gradient(145deg, #0f0f14, #1a1a2e, #0f0f14)",
      display: "flex", flexDirection: "column", alignItems: "center",
      justifyContent: "center", padding: "24px 16px", gap: 24,
      fontFamily: "'SF Pro Display', -apple-system, 'Roboto', sans-serif",
    }}>
      {/* Title */}
      <div style={{ textAlign: "center", color: "#fff" }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, margin: 0, letterSpacing: -0.5 }}>
          <span style={{ color: COLORS.primaryContainer }}>Med</span>App
          <span style={{ fontSize: 14, fontWeight: 400, color: "#999", marginLeft: 10 }}>UI Prototype</span>
        </h1>
        <p style={{ fontSize: 13, color: "#777", margin: "6px 0 0", maxWidth: 400 }}>
          Органайзер лекарств · Material 3 / Jetpack Compose · Навигация работает — кликай по элементам
        </p>
      </div>

      {/* Breadcrumb */}
      <div style={{
        display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", justifyContent: "center",
      }}>
        {[{ screen: "medkits" }, ...history.slice(-3), { screen }].filter((v, i, a) => a.findIndex(x => x.screen === v.screen) === i).map((h, i, arr) => (
          <span key={i} style={{ fontSize: 12, color: i === arr.length - 1 ? COLORS.primaryContainer : "#666" }}>
            {i > 0 && <span style={{ color: "#444", margin: "0 4px" }}>→</span>}
            {screenNames[h.screen] || h.screen}
          </span>
        ))}
      </div>

      {/* Phone */}
      <PhoneFrame>
        {renderScreen()}
        {showBottomNav && <BottomNav active={tab} onChange={handleTab} />}
        {showIntake && (
          <IntakeDialog
            drug={selectedDrug || MOCK_DRUGS[0]}
            onClose={() => setShowIntake(false)}
            onConfirm={() => setShowIntake(false)}
          />
        )}
      </PhoneFrame>

      {/* Screen list */}
      <div style={{
        display: "flex", gap: 6, flexWrap: "wrap", justifyContent: "center", maxWidth: 500,
      }}>
        {Object.entries(screenNames).map(([key, label]) => (
          <button key={key} onClick={() => { setScreen(key); setHistory([]); setTab(["medkits","schedule","scanner","stats","settings"].includes(key) ? key : tab); }}
            style={{
              all: "unset", cursor: "pointer", padding: "5px 12px", borderRadius: 8,
              fontSize: 11, fontWeight: 500, letterSpacing: 0.3,
              background: screen === key ? COLORS.primary : "#222",
              color: screen === key ? "#fff" : "#888",
              transition: "all 0.15s",
            }}>{label}</button>
        ))}
      </div>
    </div>
  );
}
