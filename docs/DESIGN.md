# SeiDesk RM — UI & Design System Specification

## Product feel

Calm, precise, and tool-first. The remote desktop session remains the primary focus; client controls are legible and ergonomic without obstructing remote interaction. SeiDesk RM uses a unified design token system (`ui/theme/SeiTokens.kt`), an obsidian dark aesthetic with electric indigo accents, clean tonal surface hierarchy, and JetBrains Mono monospace typography across all UI text.

## Matte policy & tonal depth

- **Matte policy:** Glassmorphism and backdrop blur are removed across all standard application UI (with the purple music player as the sole specialized exception).
- **Tonal depth:** Visual hierarchy is created through layered solid tonal surfaces (`SeiBg` < `SeiSurface` < `SeiSurfaceAlt` < `SeiSurfaceRaised`), subtle 1 dp borders (`SeiLine`, `SeiLineStrong`), and disciplined spacing rhythms (4, 8, 12, 16, 20, 24 dp).
- **Session HUD plates:** Session controls sit over remote pixels, so HUD plates maintain neutral translucent dark backgrounds (`0xD910131B`) with legible contrast over light and dark remote screens.
- **RailButton active state:** Active states use an accent ring with tinted fill; opaque white discs are prohibited.

## Typography

- The app exclusively uses **JetBrains Mono** static fonts (SIL Open Font License 1.1) in 400 (Regular), 500 (Medium), 600 (SemiBold), and 700 (Bold) weights.
- All 15 Material 3 typography roles are explicitly defined in `SeiTypography` with `fontFeatureSettings = "calt 0"` to prevent unintended code ligatures in technical data.
- Minimum type size is strictly 11 sp across all UI labels (with HUD telemetry explicitly at 10–11 sp).

## Contrast & accessibility

- All text and background token pairs achieve **WCAG AA contrast (≥ 4.5:1)** for standard text:
  - Faint secondary text: `#8494AB` (5.8:1 on `SeiSurface`).
  - Dark accent as text: `#818CF8` (6.0:1 on `SeiSurface`).
  - Primary button fill: `#5558E6` (5.35:1 against white text).
- Minimum touch targets are at least 44 dp.
- Accessibility: device cards provide merged TalkBack nodes with custom actions; status indicators always pair color dots with text labels.

## Motion & feedback

- Motion follows standardized durations (`SeiMotion`: 120 ms fast, 200 ms normal, 320 ms slow) with `FastOutSlowInEasing`.
- Interactive elements feature tactile micro-interactions via `Modifier.pressScale(0.97f)` and light haptics on primary buttons and navigation.

## Publisher attribution

- The official publisher is **Seijixinghe888**.
- Third-party components and font licensing are documented in `THIRD_PARTY_NOTICES.md` and `SEIDESK-NOTICE.md`.
