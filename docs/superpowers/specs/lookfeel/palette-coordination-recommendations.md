# StyleBI Palette Coordination Recommendations

## Purpose

This note records the current recommendation for how the shell palette and the future visualization palette should relate. It is intentionally scoped as a coordination document so palette implementation can proceed in separate phases.

## Core Principle

The shell and the visualization layer should not compete for attention.

- The shell provides calm structure.
- The visualization layer provides color, emphasis, and analytical meaning.

## Layer Roles

### Layer 1: Shell

The shell should use:

- warm or neutral surfaces
- one primary brand accent
- restrained semantic colors
- minimal hue variation in routine chrome

The shell should express hierarchy mostly through:

- typography
- spacing
- borders
- surface contrast
- fill versus outline versus ghost treatments

### Layer 2: Visualization

The visualization layer should use:

- a vivid categorical palette
- readable sequential and diverging ramps
- strong contrast for analytical distinction
- neutral chart scaffolding for axes, gridlines, and labels

The visualization palette should carry:

- series distinction
- threshold meaning
- conditional formatting
- highlighted data emphasis

## What Can Cross Layers

These color families may appear in both layers, but with different intensity and purpose.

### Primary

- Shell: brand/action accent
- Visualization: only for deliberate emphasis such as selected or highlighted data

Primary should not become the default first chart series color.

### Success, Warning, Danger

- Shell: validation, feedback, alerts, states
- Visualization: threshold, KPI, anomaly, exception encoding

The hue families can align, but chart usage may require stronger contrast than shell usage.

### Neutral Grays

- Shell: text, borders, surfaces, hover fills, disabled states
- Visualization: axes, labels, gridlines, de-emphasized marks

## What Should Stay Exclusive to Visualization

These should not become routine shell colors:

- vivid categorical hues
- sequential ramps
- diverging ramps
- heatmap colors
- high-saturation analytical highlight colors

In practice, chart palette colors should not be reused for:

- default buttons
- secondary buttons
- tabs
- passive toolbar icons
- panel fills
- general navigation states

## What Should Stay Exclusive to the Shell

These should not become primary visualization colors:

- shell off-whites
- shell neutral surfaces
- shell border grays
- shell hover fills
- subdued chrome hierarchy colors

These are structural product colors, not analytical colors.

## Semantic Color Guidance

Semantic colors are the one area where both layers may intentionally share hue families.

- Shell semantic colors should be calmer and more restrained.
- Visualization semantic colors can be stronger as long as readability and meaning improve.

Examples:

- shell success: subdued green for validation or system state
- chart success: stronger green for positive KPI or above-threshold encoding

## Accent Usage Rules

- The shell gets one routine accent: `primary`.
- The visualization palette does not inherit shell accent usage by default.
- The shell accent may appear in charts only for selected, focused, or deliberately highlighted data.

This keeps product branding from overwhelming analytical color meaning.

## Contrast and Readability Rules

- Shell surfaces should stay quiet in hue, but still maintain readable structure.
- Shell semantic colors should generally be quieter than chart colors.
- Chart colors should remain distinguishable from shell interactive states.
- A hovered toolbar button should not visually compete with a highlighted chart mark.

## Practical Guardrails

- No chart categorical hue should be used for routine shell chrome.
- No routine shell state should depend on a vivid chart hue unless it is semantic.
- Visualization colors should be reserved for data marks, legends, annotations, thresholds, and analytical emphasis.
- Shell hover and passive selection states should rely mostly on neutrals plus primary.

## Simple Decision Test

When assigning a color, ask:

1. If the chart disappeared, would this color still be needed for product structure?
2. Is this color communicating analytical meaning or only interface hierarchy?

Interpretation:

- If it is needed for product structure, it likely belongs to the shell.
- If it communicates analytical meaning, it likely belongs to visualization or semantic color.
- If it only expresses interface hierarchy, it should usually be neutral.

## Deferred Follow-Up

This document does not define the final chart palette or the exact bridge between shell and chart tokens. That should happen in a separate step after shell palette decisions are complete.
