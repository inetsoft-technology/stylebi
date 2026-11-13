/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
/**
 * Completeness guard for the Expression pairing of dynamic values (bug #80459).
 *
 * A `<dynamic-combo-box>` / `<dynamic-value-editor>` only shows the Connect to Claude button when
 * its host names the property with `dynamicProperty` (see FormulaEditorDialog.deriveEditorContext).
 * A host that forgets it silently has no button, so this spec makes forgetting fail the build:
 *
 *  1. every `dynamicProperty` literal in a template must be listed in dynamic-value-names.json
 *     (the same file the server's vocabulary test reads), under that template as its host;
 *  2. every manifest entry must still have a host template that carries its literal;
 *  3. every Expression-capable usage (no literal `valueOnly`) must bind `dynamicProperty` or sit
 *     in ALLOWLIST below with a reason. The allowlist is grouped by the rollout PR that will
 *     wire the host: that PR deletes its group, and a stale entry fails here, so the list can
 *     only shrink. A NEW usage, or one that loses its binding, is not in the list and fails.
 */
import { readdirSync, readFileSync } from "fs";
import { dirname, join, relative, resolve, sep } from "path";
import { fileURLToPath } from "url";

const HERE = dirname(fileURLToPath(import.meta.url));
const manifest: ManifestEntry[] = JSON.parse(
   readFileSync(join(HERE, "dynamic-value-names.json"), "utf8"));
const APP = resolve(HERE, "../..");

interface ManifestEntry {
   name: string;
   assemblyTypes: string[];
   host: string;
}

interface Usage {
   tag: string;
   text: string;
   label: string;
   expressionCapable: boolean;
   bindsProperty: boolean;
}

const PLACEHOLDER = "<x>";

/** A `<shelf>` / `<i>` placeholder of a manifest name, as the template's computed form shows it. */
function normalizeManifestName(name: string): string {
   return name.replace(/<[^>]*>/g, PLACEHOLDER);
}

/**
 * The names a template binds as `dynamicProperty`, as comparable keys. A quoted literal is the
 * name itself; a computed expression (`'binding:' + shelf + '[' + i + '].column'`) is rendered
 * with `<x>` for every non-literal part. A binding with no quoted text (`="dynamicProperty"`,
 * a pane forwarding its own input) names nothing and is not checked here.
 */
function boundNames(html: string): string[] {
   const names: string[] = [];
   const re = /(\[)?dynamicProperty\]?\s*=\s*"([^"]*)"/g;
   let m: RegExpExecArray;

   while((m = re.exec(html))) {
      const value = m[2];

      if(!m[1]) {
         names.push(value);
         continue;
      }

      if(!/'[^']*'/.test(value)) {
         continue;
      }

      if(value.includes("+")) {
         names.push(value.split("+").map(part => {
            const literal = /^\s*'([^']*)'\s*$/.exec(part);
            return literal ? literal[1] : PLACEHOLDER;
         }).join("").replace(/(<x>)+/g, PLACEHOLDER));
      }
      else {
         for(const literal of value.match(/'[^']*'/g)) {
            names.push(literal.slice(1, -1));
         }
      }
   }

   return names;
}

/** The opening tags of the named elements in a template, quote-aware so `>` inside a binding is safe. */
function scanTags(html: string, names: string[]): { tag: string, text: string }[] {
   const found: { tag: string, text: string }[] = [];
   const re = new RegExp("<(" + names.join("|") + ")(?![\\w-])", "g");
   let m: RegExpExecArray;

   while((m = re.exec(html))) {
      let i = m.index + m[0].length;
      let quote: string = null;

      for(; i < html.length; i++) {
         const c = html[i];

         if(quote) {
            if(c === quote) {
               quote = null;
            }
         }
         else if(c === "\"" || c === "'") {
            quote = c;
         }
         else if(c === ">") {
            break;
         }
      }

      found.push({ tag: m[1], text: html.substring(m.index, i + 1) });
   }

   return found;
}

/** Every combo / value-editor tag of a template. */
function usages(html: string): Usage[] {
   return scanTags(html, ["dynamic-combo-box", "dynamic-value-editor"]).map(({ tag, text }) => {
      const valueOnly = /\[?valueOnly\]?(="([^"]*)")?/.exec(text);
      const valueOnlyLiteral = !!valueOnly && (valueOnly[1] === undefined || valueOnly[2] === "true");
      const binding = /\[\(?value\)?\]="([^"]*)"/.exec(text) ||
         /\[valueModel\]="([^"]*)"/.exec(text);

      return {
         tag,
         text,
         label: binding ? binding[1] : "",
         expressionCapable: !valueOnlyLiteral,
         bindsProperty: /dynamicProperty/.test(text)
      };
   });
}

function htmlFiles(dir: string, out: string[] = []): string[] {
   for(const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name);

      if(entry.isDirectory()) {
         htmlFiles(path, out);
      }
      else if(entry.name.endsWith(".html")) {
         out.push(path);
      }
   }

   return out;
}

const templates = new Map<string, string>();

for(const file of htmlFiles(APP)) {
   templates.set(relative(APP, file).split(sep).join("/"), readFileSync(file, "utf8"));
}

/**
 * Expression-capable usages that do not bind `dynamicProperty` yet, as [template, bound value].
 * Each group says why; B1..F are the rollout PRs of the #80459 follow-up (design: batch2-design.md
 * section 5), NONE are hosts that intentionally never pair.
 */
/** Panes whose combos name a property but need the assembly and runtime id from their parents. */
const PAIRING_PANES = ["general-prop-pane", "basic-general-pane", "title-prop-pane"];

/** The properties each pairing pane names (its combos' `dynamicProperty` literals). */
const PANE_PROPERTIES: Record<string, string[]> = {
   "general-prop-pane": ["visible", "enabled"],
   "basic-general-pane": ["visible"],
   "title-prop-pane": ["title"],
};

interface UnpairedProperty {
   file: string;
   pane: string;
   property: string;
   assemblyType: string;
   reason: string;
}

/**
 * Properties of ONE pane use that are deliberately left unpaired. The exception is per property:
 * a use is exempt from threading only when EVERY property of its pane is listed here, so the
 * embedded viewsheet dialog must still thread its Visible combo. An entry must name a type the
 * manifest does not list for that property (so the two cannot disagree), and a stale entry fails.
 */
const UNPAIRED_PROPERTIES: UnpairedProperty[] = [
   {
      file: "composer/dialog/vs/viewsheet-object-property-dialog.component.html",
      pane: "general-prop-pane",
      property: "enabled",
      assemblyType: "viewsheet",
      reason: "the server has no `enabled` alias for an embedded viewsheet, and its dialog sets " +
         "showEnabledGroup(false), so the Enabled combo is never shown"
   },
];

/**
 * Dialog component (file) to the assembly type it edits. The reachability test derives, for each
 * manifest entry, which of these dialogs the host pane's usage chain ends at.
 */
const DIALOG_TYPES: Record<string, string> = {
   "composer/dialog/vs/calendar-property-dialog.component.ts": "calendar",
   "composer/dialog/vs/checkbox-property-dialog.component.ts": "checkbox",
   "composer/dialog/vs/combobox-property-dialog.component.ts": "combobox",
   "composer/dialog/vs/gauge-property-dialog.component.ts": "gauge",
   "composer/dialog/vs/group-container-property-dialog.component.ts": "groupcontainer",
   "composer/dialog/vs/image-property-dialog.component.ts": "image",
   "composer/dialog/vs/line-property-dialog.component.ts": "line",
   "composer/dialog/vs/oval-property-dialog.component.ts": "oval",
   "composer/dialog/vs/radiobutton-property-dialog.component.ts": "radiobutton",
   "composer/dialog/vs/rectangle-property-dialog.component.ts": "rectangle",
   "composer/dialog/vs/selection-container-property-dialog.component.ts": "selectioncontainer",
   "composer/dialog/vs/selection-list-property-dialog.component.ts": "selectionlist",
   "composer/dialog/vs/selection-tree-property-dialog.component.ts": "selectiontree",
   "composer/dialog/vs/slider-property-dialog.component.ts": "slider",
   "composer/dialog/vs/spinner-property-dialog.component.ts": "spinner",
   "composer/dialog/vs/submit-property-dialog.component.ts": "submit",
   "composer/dialog/vs/tab-property-dialog.component.ts": "tab",
   "composer/dialog/vs/text-property-dialog.component.ts": "text",
   "composer/dialog/vs/textinput-property-dialog.component.ts": "textinput",
   "composer/dialog/vs/viewsheet-object-property-dialog.component.ts": "viewsheet",
   "vsobjects/dialog/calc-table-property-dialog.component.ts": "calctable",
   "vsobjects/dialog/crosstab-property-dialog.component.ts": "crosstab",
   "vsobjects/dialog/graph/chart-property-dialog.component.ts": "chart",
   "vsobjects/dialog/range-slider-property-dialog.component.ts": "timeslider",
   "vsobjects/dialog/table-view-property-dialog.component.ts": "table",
};

/**
 * Types whose dialog reaches a manifest host but never shows that property, with the server
 * service that hides it. The Java service must really hide it (checked below), and the manifest
 * must NOT list the type: a name the UI cannot produce has no business in the vocabulary.
 */
const HIDDEN_BY_DIALOG: { property: string, type: string, service: string }[] = [
   {
      property: "enabled", type: "groupcontainer",
      service: "composer/vs/dialog/GroupContainerPropertyDialogService.java"
   },
   {
      property: "enabled", type: "viewsheet",
      service: "composer/vs/dialog/ViewsheetObjectPropertyDialogService.java"
   },
];

const JAVA_WEB = resolve(HERE, "../../../../../../../core/src/main/java/inetsoft/web");

/**
 * The selector of the component a template belongs to, or null for a property dialog: a dialog
 * is opened programmatically and gets its own `assemblyName` from PropertyDialog, so the chain
 * stops there. (The print layout editor embeds two dialogs as components; it must stay unpaired.)
 */
function selectorOf(file: string): string | null {
   const ts = file.replace(/\.html$/, ".ts");

   try {
      const source = readFileSync(join(APP, ts), "utf8");
      return /extends\s+PropertyDialog/.test(source) ? null
         : /selector:\s*"([^"]+)"/.exec(source)?.[1] ?? null;
   }
   catch(e) {
      return null;
   }
}

/** The component file (relative to app) of a template when it is a property dialog, else null. */
function dialogFileOf(file: string): string | null {
   const ts = file.replace(/\.html$/, ".ts");

   try {
      return /extends\s+PropertyDialog/.test(readFileSync(join(APP, ts), "utf8")) ? ts : null;
   }
   catch(e) {
      return null;
   }
}

/**
 * The property dialogs that a pane's usage chain ends at: every template using `<tag>` is either
 * a dialog (collected) or another pane whose own selector is followed in turn.
 */
function dialogsReaching(tag: string): Set<string> {
   const dialogs = new Set<string>();
   const seen = new Set<string>([tag]);
   const queue = [tag];

   while(queue.length) {
      const current = queue.shift();

      for(const [file, html] of templates) {
         if(!scanTags(html, [current]).length) {
            continue;
         }

         const dialog = dialogFileOf(file);

         if(dialog) {
            dialogs.add(dialog);
            continue;
         }

         const selector = selectorOf(file);

         if(selector && !seen.has(selector)) {
            seen.add(selector);
            queue.push(selector);
         }
      }
   }

   return dialogs;
}

const ALLOWLIST: Record<string, { reason: string, usages: [string, string][] }> = {
   B1: {
      reason: "PR B1: one-hop property dialog panes, wired by the B1 PR",
      usages: [
         ["composer/dialog/vs/data-output-pane.component.html", "model.column"],
         ["composer/dialog/vs/data-output-pane.component.html", "model.aggregate"],
         ["composer/dialog/vs/data-output-pane.component.html", "model.with"],
         ["composer/dialog/vs/data-output-pane.component.html", "model.num"],
         ["composer/dialog/vs/fill-prop-pane.component.html", "model.color"],
         ["composer/dialog/vs/input-label-pane.component.html", "model.labelText"],
         ["composer/dialog/vs/line-prop-pane.component.html", "model.color"],
         ["composer/dialog/vs/number-range-pane.component.html", "model.min"],
         ["composer/dialog/vs/number-range-pane.component.html", "model.max"],
         ["composer/dialog/vs/number-range-pane.component.html", "model.minorIncrement"],
         ["composer/dialog/vs/number-range-pane.component.html", "model.majorIncrement"],
         ["composer/dialog/vs/numeric-range-pane.component.html", "model.minimum"],
         ["composer/dialog/vs/numeric-range-pane.component.html", "model.maximum"],
         ["composer/dialog/vs/numeric-range-pane.component.html", "model.increment"],
         ["composer/dialog/vs/range-pane.component.html", "rangeValue.value"],
         ["composer/dialog/vs/range-pane.component.html", "model.targetValue"],
         ["composer/dialog/vs/text-pane.component.html", "model.text"],
      ]
   },
   B2: {
      reason: "PR B2: property dialog panes with a deeper host chain, wired by the B2 PR",
      usages: [
         ["composer/dialog/vs/data-input-pane.component.html", "model.columnValue"],
         ["composer/dialog/vs/data-input-pane.component.html", "selectedRow"],
         ["composer/dialog/vs/dynamic-image-pane.component.html", "imageName"],
         ["composer/dialog/vs/selection-measure-pane.component.html", "localMeasure"],
         ["composer/dialog/vs/selection-measure-pane.component.html", "localFormula"],
         ["composer/dialog/vs/selection-tree-id-pane.component.html", "localParentId"],
         ["composer/dialog/vs/selection-tree-id-pane.component.html", "localId"],
         ["composer/dialog/vs/selection-tree-id-pane.component.html", "localLabel"],
         ["vsobjects/dialog/combo-box-editor.component.html", "model.minDate"],
         ["vsobjects/dialog/combo-box-editor.component.html", "model.maxDate"],
      ]
   },
   B3: {
      reason: "PR B3: the calendar advanced pane, wired by the B3 PR",
      usages: [
         ["composer/dialog/vs/calendar-advanced-pane.component.html", "model.min"],
         ["composer/dialog/vs/calendar-advanced-pane.component.html", "model.max"],
      ]
   },
   C: {
      reason: "PR C: binding editor panes, wired by the C PR (own runtime, needs a live check)",
      usages: [
         ["binding/editor/chart/field/calculate-pane-dialog.component.html", "percentageDirection"],
         ["binding/editor/chart/field/chart-fieldmc.component.html", "cellValue"],
         ["binding/editor/chart/field/dimension-editor.component.html", "dateLevel"],
         ["binding/editor/formula-option.component.html", "aggregate.formula"],
         ["binding/editor/formula-option.component.html", "secondColumnValue"],
         ["binding/editor/formula-option.component.html", "aggregate.numValue"],
         ["binding/editor/sort-option.component.html", "dimension.sortByCol"],
         ["binding/editor/sort-option.component.html", "rankingOption"],
         ["binding/editor/sort-option.component.html", "dimension.rankingN"],
         ["binding/editor/sort-option.component.html", "dimension.rankingCol"],
         ["binding/editor/table/aggregate-option.component.html", "aggregate.percentage"],
         ["binding/editor/table/crosstab-option.component.html", "option.rowTotalVisibleValue"],
         ["binding/editor/table/crosstab-option.component.html", "option.colTotalVisibleValue"],
         ["binding/editor/table/group-option.component.html", "field.dateLevel"],
         ["binding/editor/table/group-option.component.html", "summarize"],
         ["binding/editor/table/table-fieldmc.component.html", "cellValue"],
      ]
   },
   D: {
      reason: "PR D: date comparison dialog, wired by the D PR (runtime id via pairingRuntimeId)",
      usages: [
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-custom-periods.component.html", "period.start"],
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-custom-periods.component.html", "period.end"],
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-interval-pane.component.html", "visibleIntervalLevel"],
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-interval-pane.component.html", "contextLevelValue"],
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-interval-pane.component.html", "visibleGranularity"],
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-interval-pane.component.html", "intervalPaneModel.intervalEndDate"],
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-standard-periods.component.html", "standardPeriodPaneModel.preCount.value"],
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-standard-periods.component.html", "visibleStandardPeriodLevel"],
         ["vsobjects/dialog/date-comparison-dialog/date-comparison-standard-periods.component.html", "standardPeriodPaneModel.endDay"],
      ]
   },
   E: {
      reason: "PR E: chart target dialog and computation dialog, wired by the E PR",
      usages: [
         ["widget/dialog/computation-combo-box/computation-dialog.component.html", "model.value"],
         ["widget/dialog/computation-combo-box/computation-dialog.component.html", "model.percentageAggregateVal"],
         ["widget/target/date-input-field.component.html", "displayValue"],
         ["widget/target/label-input-field.component.html", "label"],
         ["widget/target/value-input-field.component.html", "displayValue"],
      ]
   },
   F: {
      reason: "PR F: chart title and legend format dialogs, wired by the F PR",
      usages: [
         ["graph/dialog/legend-format-general-pane.component.html", "model.titleValue"],
         ["graph/dialog/title-format-pane.component.html", "model.title"],
      ]
   },
   NONE: {
      reason: "Never pairs: format panes edit a region of N selected assemblies (no single assembly and name), and the portal scheduler add-parameter dialog has no sheet at all",
      usages: [
         ["format/objects/formatting-pane.component.html", "formatModel?.formatSpec"],
         ["portal/schedule/schedule-task-editor/add-parameter-dialog/add-parameter-dialog.component.html", "model.value"],
         ["vsobjects/format/vs-formats-pane.component.html", "color"],
         ["vsobjects/format/vs-formats-pane.component.html", "backgroundColor"],
         ["vsobjects/format/vs-formats-pane.component.html", "getColorLabel(vsObjectFormat.valueFillColor)"],
      ]
   },
};

describe("dynamic value pairing completeness guard (bug #80459)", () => {
   const entries = manifest;

   describe("manifest", () => {
      it("is not empty and every entry is well formed", () => {
         expect(entries.length).toBeGreaterThan(0);

         for(const entry of entries) {
            expect(entry.name).toMatch(/^\S+$/);
            expect(entry.name.startsWith("prop:")).toBe(false);
            expect(entry.name.length).toBeLessThanOrEqual(200);
            expect(entry.assemblyTypes.length).toBeGreaterThan(0);
            expect(entry.host).toMatch(/\.html$/);
         }

         const keys = entries.map(e => e.name + "@" + e.host);
         expect(new Set(keys).size).toBe(keys.length);
      });

      it("has a host template carrying the literal of every entry", () => {
         const missing = entries.filter(entry => {
            const html = templates.get(entry.host);
            return !html || !boundNames(html).some(
               n => n === entry.name || n === normalizeManifestName(entry.name));
         });

         expect(missing.map(e => e.name + " @ " + e.host)).toEqual([]);
      });
   });

   describe("templates", () => {
      it("only bind dynamicProperty names that the manifest lists for that template", () => {
         const unknown: string[] = [];

         for(const [file, html] of templates) {
            for(const name of boundNames(html)) {
               const listed = entries.some(e => e.host === file &&
                  (e.name === name || normalizeManifestName(e.name) === name));

               if(!listed) {
                  unknown.push(`${file}: '${name}'`);
               }
            }
         }

         expect(unknown).toEqual([]);
      });

      it("bind dynamicProperty on every Expression-capable usage or allowlist it", () => {
         const remaining = new Map<string, number>();

         for(const group of Object.values(ALLOWLIST)) {
            for(const [file, label] of group.usages) {
               remaining.set(`${file}#${label}`, (remaining.get(`${file}#${label}`) ?? 0) + 1);
            }
         }

         const unbound: string[] = [];

         for(const [file, html] of templates) {
            for(const usage of usages(html)) {
               if(!usage.expressionCapable || usage.bindsProperty) {
                  continue;
               }

               const key = `${file}#${usage.label}`;
               const left = remaining.get(key) ?? 0;

               if(left > 0) {
                  remaining.set(key, left - 1);
               }
               else {
                  unbound.push(`${file}: <${usage.tag}> value='${usage.label}'`);
               }
            }
         }

         expect(unbound).toEqual([]);

         // an allowlisted usage that is gone or now binds the property must leave the list
         const stale = [...remaining].filter(([, left]) => left > 0).map(([key]) => key);
         expect(stale).toEqual([]);
      });

      it("keeps a reason on every allowlist group", () => {
         for(const [group, { reason, usages }] of Object.entries(ALLOWLIST)) {
            expect(reason.length, group).toBeGreaterThan(10);
            expect(usages.length, group).toBeGreaterThan(0);
         }
      });
   });

   describe("assembly threading", () => {
      it("passes the assembly and a runtime id down every chain that ends at a pairing pane", () => {
         const used = new Set<string>();
         const reached = new Set(PAIRING_PANES);
         const queue = [...PAIRING_PANES];
         const unthreaded: string[] = [];

         while(queue.length) {
            const tag = queue.shift();

            for(const [file, html] of templates) {
               for(const use of scanTags(html, [tag])) {
                  const excepted = UNPAIRED_PROPERTIES
                     .filter(e => e.file === file && e.pane === tag).map(e => e.property);
                  excepted.forEach(p => used.add(`${file}#${tag}#${p}`));

                  // exempt only when every property this pane names is deliberately unpaired
                  if(PANE_PROPERTIES[tag]?.every(p => excepted.includes(p))) {
                     continue;
                  }

                  if(!/\[assemblyName\]/.test(use.text) || !/\[(vsId|runtimeId)\]/.test(use.text)) {
                     unthreaded.push(`${file}: <${tag}> lacks [assemblyName] or [vsId]/[runtimeId]`);
                  }

                  const selector = selectorOf(file);

                  if(selector && !reached.has(selector)) {
                     reached.add(selector);
                     queue.push(selector);
                  }
               }
            }
         }

         expect(unthreaded).toEqual([]);

         const stale = UNPAIRED_PROPERTIES.map(e => `${e.file}#${e.pane}#${e.property}`)
            .filter(key => !used.has(key));
         expect(stale).toEqual([]);
      });

      it("never excepts a property that the manifest lists for that type, and says why", () => {
         for(const e of UNPAIRED_PROPERTIES) {
            const listed = manifest.some(m => m.name === e.property && m.assemblyTypes.includes(e.assemblyType));

            expect(listed, `${e.property}@${e.assemblyType} is both excepted and in the manifest`).toBe(false);
            expect(e.reason.length).toBeGreaterThan(10);
            expect(PANE_PROPERTIES[e.pane]).toContain(e.property);
         }
      });

      it("the threading scan reaches the dialogs: the chain of a text object has five hops", () => {
         const reached = new Set<string>();
         const walk = (tag: string) => {
            for(const [file, html] of templates) {
               if(scanTags(html, [tag]).length) {
                  const selector = selectorOf(file);

                  if(selector && !reached.has(selector)) {
                     reached.add(selector);
                     walk(selector);
                  }
               }
            }
         };
         walk("general-prop-pane");

         expect([...reached]).toEqual(expect.arrayContaining(
            ["output-general-pane", "text-general-pane", "checkbox-general-pane"]));
      });
   });

   describe("manifest assembly types against the dialogs that host them", () => {
      it("maps every property dialog component to a type", () => {
         const dialogs = [...templates.keys()].map(dialogFileOf).filter(f => !!f).sort();

         expect(dialogs).toEqual(Object.keys(DIALOG_TYPES).sort());
      });

      it("lists exactly the types whose dialog chain reaches the host pane and shows the property", () => {
         for(const entry of manifest) {
            const pane = selectorOf(entry.host);
            const reached = [...dialogsReaching(pane)].map(d => DIALOG_TYPES[d]);
            const hidden = HIDDEN_BY_DIALOG.filter(h => h.property === entry.name).map(h => h.type);

            expect(pane, entry.host).toBeTruthy();
            expect([...entry.assemblyTypes].sort(),
                   `${entry.name}@${entry.host}: declared types vs dialogs reaching the pane`)
               .toEqual(reached.filter(t => !hidden.includes(t)).sort());
         }
      });

      it("only hides a type that the dialog chain really reaches, and the server hides it", () => {
         for(const h of HIDDEN_BY_DIALOG) {
            const entry = manifest.find(m => m.name === h.property);
            const reached = [...dialogsReaching(selectorOf(entry.host))].map(d => DIALOG_TYPES[d]);

            expect(reached, `${h.property}@${h.type} is not reached, drop the entry`).toContain(h.type);
            expect(entry.assemblyTypes, `${h.property}@${h.type} must not be in the manifest`)
               .not.toContain(h.type);
            expect(readFileSync(join(JAVA_WEB, h.service), "utf8"))
               .toMatch(/setShowEnabledGroup\(\s*false\s*\)/);
         }
      });
   });

   // Positive controls: the scanners must see what the guard claims to catch.
   describe("scanner positive controls", () => {
      it("finds every shape of dynamicProperty binding", () => {
         const html = `<dynamic-combo-box dynamicProperty="a"></dynamic-combo-box>
            <dynamic-combo-box [dynamicProperty]="'b'"></dynamic-combo-box>
            <dynamic-combo-box [dynamicProperty]="layoutObject ? null : 'c'"></dynamic-combo-box>
            <dynamic-combo-box [dynamicProperty]="'binding:' + shelf + '[' + i + '].column'"></dynamic-combo-box>
            <dynamic-combo-box [dynamicProperty]="dynamicProperty"></dynamic-combo-box>`;

         expect(boundNames(html)).toEqual(["a", "b", "c", "binding:<x>[<x>].column"]);
         expect(normalizeManifestName("binding:<shelf>[<i>].column")).toBe("binding:<x>[<x>].column");
      });

      it("flags an unbound Expression-capable usage and ignores a valueOnly one", () => {
         const html = `<dynamic-combo-box [value]="a > b ? 1 : 2" [vsId]="x"></dynamic-combo-box>
            <dynamic-combo-box [valueOnly]="true" [value]="v"></dynamic-combo-box>
            <dynamic-combo-box [valueOnly]="!isVs" [value]="w"></dynamic-combo-box>
            <dynamic-value-editor [valueModel]="m"></dynamic-value-editor>
            <dynamic-combo-box [value]="z" dynamicProperty="visible"></dynamic-combo-box>`;
         const found = usages(html);

         expect(found.map(u => [u.expressionCapable, u.bindsProperty, u.label])).toEqual([
            [true, false, "a > b ? 1 : 2"], [false, false, "v"], [true, false, "w"],
            [true, false, "m"], [true, true, "z"]]);
      });

      it("the template scan sees the Expression-capable usages that exist today", () => {
         const total = [...templates.values()].flatMap(html => usages(html))
            .filter(u => u.expressionCapable).length;

         expect(total).toBeGreaterThanOrEqual(70);
      });
   });
});
