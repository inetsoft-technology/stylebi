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
 * MiniToolbar - resident kebab, rendered
 *
 * mini-toolbar.component.spec.ts exercises the component's TS logic (isFocused, actionButtonGroups,
 * kebabAction, showToolbarContainer) by direct construction, with no template/CSS involved. That
 * suite cannot answer "does the kebab actually render where and how it is supposed to at rest" —
 * only a real Angular render, with the component's own compiled SCSS injected into the document,
 * can. This file is that render.
 *
 * Covers (fix-review round):
 *   - SPEC GAP:    kebab renders (and is visible) at rest under the gate; no action button does.
 *   - Critical 1:  the kebab is a child of .mini-toolbar-container, not a sibling — so it inherits
 *                  the pinned 24px row height and button chrome instead of bare btn-sm metrics.
 *   - Important 2: covered in mini-toolbar.component.spec.ts (onKeyUp is pure logic once the
 *                  container element is available); not duplicated here.
 *   - Important 3: .mini-toolbar-container does not render at all for an empty action list — what
 *                  the sub-32px rung of the fit ladder sends down.
 *   - Important 4: bd-selected-cell lands on the exact rendered button for the focused (i, j),
 *                  both before and after the Step 8 restructure's riskiest case — the kebab
 *                  sharing a group with an action button.
 *
 * Resting-kebab invisibility fix: the resting assertions now read the rendered outcome rather than
 * declared values. The invariant that catches this whole failure mode is zeroingAncestors() below —
 * walk from the rendered kebab up to the strip host and require that no ancestor contributes
 * opacity: 0 or visibility: hidden. The version of this suite that shipped the bug asserted a
 * declared opacity: 0 on the container and a declared opacity: 0.55 on the kebab inside it, in the
 * same test, and passed while the kebab rendered at 0 x 0.55 = 0.
 */

import { render } from "@testing-library/angular";

import { AssemblyAction } from "../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { TestUtils } from "../../../common/test/test-utils";
import { GuiTool } from "../../../common/util/gui-tool";
import { ChartActions } from "../../action/chart-actions";
import { ComposerContextProviderFactory, ContextProvider } from "../../context-provider.service";
import { VSChartModel } from "../../model/vs-chart-model";
import { PopComponentService } from "../data-tip/pop-component.service";
import { MiniToolbar } from "./mini-toolbar.component";
import { MiniToolbarService } from "./mini-toolbar.service";

function makeAction(id: string, overrides: Partial<AssemblyAction> = {}): AssemblyAction {
   return {
      id: () => id,
      label: () => id,
      icon: () => "icon",
      visible: () => true,
      enabled: () => true,
      action: vi.fn(),
      ...overrides
   };
}

async function renderWithActions(actions: any, anchorInTitleLane: boolean,
                                forceHide: boolean = false)
{
   const miniToolbarService = new MiniToolbarService(
      { runOutsideAngular: (fn: () => any) => fn() } as any);

   return render(MiniToolbar, {
      providers: [
         { provide: ContextProvider, useValue: { composer: false, vsWizard: false, binding: false } },
         { provide: MiniToolbarService, useValue: miniToolbarService },
         { provide: PopComponentService, useValue: { isPopComponentShow: () => false } }
      ],
      componentInputs: {
         actions,
         top: 10,
         left: 0,
         width: 100,
         assembly: "Chart1",
         anchorInTitleLane,
         forceHide
      }
   });
}

async function renderToolbar(groups: AssemblyActionGroup[], anchorInTitleLane: boolean,
                             forceHide: boolean = false)
{
   return renderWithActions({ showingActions: groups } as any, anchorInTitleLane, forceHide);
}

/**
 * Every element from `el`'s parent up to and including `stopAt` that would make `el` invisible no
 * matter what `el` itself declares. opacity is the one that matters and the one that got missed:
 * it is not escapable by a descendant, so an opacity: 0 ancestor multiplies any nested opacity to 0.
 * visibility is listed too because an ancestor's hidden still wins wherever the descendant does not
 * declare its own value. Returns descriptions rather than a boolean so a failure names the culprit.
 */
function zeroingAncestors(el: HTMLElement, stopAt: HTMLElement): string[] {
   const offenders: string[] = [];

   for(let node = el.parentElement; node; node = node.parentElement) {
      const style = window.getComputedStyle(node);

      if(style.opacity === "0" || style.visibility === "hidden") {
         offenders.push(`${node.className || node.tagName}` +
            ` (opacity=${style.opacity}, visibility=${style.visibility})`);
      }

      if(node === stopAt) {
         break;
      }
   }

   return offenders;
}

describe("MiniToolbar rendered - kebab placement and resting visibility", () => {
   it("renders the kebab inside .mini-toolbar-container, not as a sibling of it", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data"), makeAction("chart open-max-mode")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], true);

      const container = fixture.nativeElement.querySelector(".mini-toolbar-container");
      const kebab = fixture.nativeElement.querySelector(".mini-toolbar-kebab");

      expect(container).not.toBeNull();
      expect(kebab).not.toBeNull();
      // Critical 1: a sibling kebab would sit outside .mini-toolbar-container entirely, laying
      // out on its own line below the right-aligned pill instead of inside it.
      expect(container.contains(kebab)).toBe(true);
      // Inheriting the container's :host-context(.viz-modern) button rules (24px pinned height,
      // border/background overrides) requires being a .mini-toolbar-container > button, same as
      // the two action buttons alongside it.
      const actionButton = fixture.nativeElement.querySelector(".mini-toolbar-button-group button");
      expect(kebab.closest(".mini-toolbar-container")).toBe(actionButton.closest(".mini-toolbar-container"));
   });

   // Resting-kebab invisibility fix. The previous version of this test asserted
   // containerStyle.opacity === "0" and kebabStyle.opacity === "0.55" side by side and passed,
   // encoding the bug as correct: opacity is not escapable by a descendant — it applies to an
   // element and its whole subtree as one composited group — so those two declared values multiply
   // to 0 x 0.55 = 0 and the kebab rendered fully transparent. The assertions below are on the
   // rendered outcome instead: nothing between the kebab and the strip host may zero it out.
   it("at rest: no ancestor of the kebab zeroes it out, and the kebab itself is dim but clickable", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], true);

      const host: HTMLElement = fixture.nativeElement.querySelector(".mini-toolbar");
      const kebab: HTMLElement = fixture.nativeElement.querySelector(".mini-toolbar-kebab");
      const kebabStyle = window.getComputedStyle(kebab);

      // No hover/focus has been simulated, so this is genuinely the resting state.
      // The invariant: walking up from the rendered kebab to the strip host, no element may
      // contribute opacity: 0 or visibility: hidden. Both are per-element computed-style reads, so
      // jsdom can answer them without any layout. Reported as a list so a regression names the
      // offending element rather than just failing a boolean.
      expect(zeroingAncestors(kebab, host)).toEqual([]);
      expect(kebabStyle.opacity).toBe("0.55");
      expect(kebabStyle.visibility).toBe("visible");
      expect(kebabStyle.pointerEvents).toBe("auto");
      // The pill keeps its chrome at rest — that is what makes the lone kebab read as a button — so
      // the container carries no resting opacity/visibility override of its own. (Its background,
      // border and radius are all var()-valued, which cssstyle leaves unexpanded, so those exact
      // declarations are not readable through getComputedStyle in jsdom; not being switched off is.)
      const containerStyle = window.getComputedStyle(host.querySelector(".mini-toolbar-container"));
      expect(containerStyle.opacity).not.toBe("0");
      expect(containerStyle.visibility).toBe("visible");
   });

   it("at rest: the action groups are out of layout, so the pill can shrink-wrap the kebab", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], true);

      const actionGroup = fixture.nativeElement.querySelector(
         ".mini-toolbar-button-group:not(.mini-toolbar-kebab-group)");
      const kebabGroup = fixture.nativeElement.querySelector(".mini-toolbar-kebab-group");

      expect(actionGroup).not.toBeNull();
      // display: none, not opacity/visibility: .mini-toolbar-button-group sets pointer-events: all
      // unconditionally, so a merely-transparent action button would still be clickable, and a box
      // still in layout would leave a wide empty pill at rest.
      expect(window.getComputedStyle(actionGroup).display).toBe("none");
      expect(window.getComputedStyle(kebabGroup).display).not.toBe("none");
      // The base `.mini-toolbar-button-group + .mini-toolbar-button-group` divider matches on DOM
      // adjacency, which display: none does not break, so it would draw a stray line down the left
      // edge of the kebab-only pill.
      expect(window.getComputedStyle(kebabGroup).borderLeftStyle).toBe("none");
   });

   it("does not take the non-kebab action groups out of layout when not anchored", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], false);

      const groups = fixture.nativeElement.querySelectorAll(".mini-toolbar-button-group");

      expect(groups.length).toBeGreaterThan(0);
      groups.forEach((g: HTMLElement) =>
         expect(window.getComputedStyle(g).display).not.toBe("none"));
   });

   // The height bands live in AbstractVSActions.showingActions, not in this component: what arrives
   // here below the 32px floor is an empty action list, which is all this asserts.
   it("renders no .mini-toolbar-container and no kebab when the action list is empty", async () => {
      const { fixture } = await renderToolbar([], true);

      expect(fixture.nativeElement.querySelector(".mini-toolbar-container")).toBeNull();
      expect(fixture.nativeElement.querySelector(".mini-toolbar-kebab")).toBeNull();
   });

   it("does not render a .mini-toolbar-kebab when not anchored, even with a trailing overflow kebab", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data"), makeAction("more actions")])
      ], false);

      // Not anchored: the legacy width-overflow kebab (if any) renders inline as an ordinary
      // button — this task adds no .mini-toolbar-kebab class or separate element for it.
      expect(fixture.nativeElement.querySelector(".mini-toolbar-kebab")).toBeNull();
      const buttons = fixture.nativeElement.querySelectorAll(".mini-toolbar-button-group button");
      expect(buttons.length).toBe(2);
   });
});

describe("MiniToolbar rendered - focus indices survive the Step 8 restructure", () => {
   it("puts bd-selected-cell on the exact rendered button for (i, j), including the kebab's own slot", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("chart open-max-mode"), makeAction("more actions")])
      ], true);
      const comp: MiniToolbar = fixture.componentInstance;

      // Bypass: focusedGroupIndex/focusedActionIndex are private; setting them directly isolates
      // the render-level assertion from getNextAction()'s own (separately covered) traversal
      // logic and its setTimeout-deferred DOM focus call.
      (comp as any).focusedGroupIndex = 1;
      (comp as any).focusedActionIndex = 0; // "chart open-max-mode" - a real action button
      fixture.detectChanges();

      let selected = fixture.nativeElement.querySelector(".bd-selected-cell");
      expect(selected.getAttribute("title")).toBe("chart open-max-mode");
      expect(selected.classList.contains("mini-toolbar-kebab")).toBe(false);

      (comp as any).focusedActionIndex = 1; // the kebab's own slot in the untrimmed list
      fixture.detectChanges();

      selected = fixture.nativeElement.querySelector(".bd-selected-cell");
      expect(selected.classList.contains("mini-toolbar-kebab")).toBe(true);
      // Exactly one button carries the class at a time — the restructure did not leave the
      // action button also marked selected once focus moved to the kebab's slot.
      expect(fixture.nativeElement.querySelectorAll(".bd-selected-cell").length).toBe(1);
   });
});

// Fix-review round 2, Critical (new): round 1's click-trap fix gave .mini-toolbar-kebab its own
// explicit `visibility: visible` so it would not inherit "hidden" from .mini-toolbar-container at
// rest. That same explicit value, being a declaration on the element itself, also beat the host's
// .hidden-mini-toolbar { visibility: hidden !important } once a live Hide MiniToolbar dismissal
// set that class — !important only wins competing declarations on the *same* element (the host),
// it does not reach down and override a descendant's own rule. The fix scopes every such
// descendant rule with :not(.hidden-mini-toolbar). Nothing before this round exercised forceHide
// against the real stylesheet at all, which is exactly why the regression shipped.
describe("MiniToolbar rendered - Hide MiniToolbar dismissal beats the resident kebab", () => {
   it("hides and de-clicks the kebab once forceHide sets .hidden-mini-toolbar", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], true, true);

      const host = fixture.nativeElement.querySelector(".mini-toolbar");
      const kebab = fixture.nativeElement.querySelector(".mini-toolbar-kebab");

      expect(host.classList.contains("hidden-mini-toolbar")).toBe(true);
      expect(window.getComputedStyle(host).visibility).toBe("hidden");
      // The regression this test exists to catch: any descendant rule that declares
      // visibility: visible without :not(.hidden-mini-toolbar) makes this read "visible" despite the
      // host being forced hidden. The kebab now declares no visibility at all and inherits the
      // host's, which is the strongest form of the same guarantee.
      expect(window.getComputedStyle(kebab).visibility).toBe("hidden");
   });

   it("keeps the kebab visible and clickable when anchored but not dismissed", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], true, false);

      const kebab = fixture.nativeElement.querySelector(".mini-toolbar-kebab");
      const kebabStyle = window.getComputedStyle(kebab);

      // Contrast case for the test above: forceHide is the only thing that differs, confirming
      // the :not(.hidden-mini-toolbar) scoping suppresses the kebab only when actually dismissed,
      // not unconditionally.
      expect(kebabStyle.visibility).toBe("visible");
      expect(kebabStyle.pointerEvents).toBe("auto");
   });
});

// Fix-review round 2, Important (new): jsdom does not evaluate stylesheet @media conditions for
// getComputedStyle (confirmed by an exploratory probe component during this round: a plain rule
// inside `@media (pointer: coarse) { .x { color: blue } }` never applied against a real render —
// the computed color stayed the unconditional rule's "red", and `window.matchMedia` is `undefined`
// in this test environment, matching the same limitation already documented in
// chart-area.component.interaction.tl.spec.ts). No assertion here can therefore observe whether
// the coarse-pointer override actually *matches* in a real browser; this test only confirms the
// declarations were not dropped from the compiled stylesheet, which is the most jsdom can verify.
// The "does it actually stop the clip" half is establishes by CSS reasoning, written up in the
// fix report rather than asserted here as if it were a passing behavioral test.
describe("MiniToolbar rendered - coarse-pointer touch target (compiled CSS only; see fix report)", () => {
   it("ships the 44px coarse-pointer override for both the container and the kebab in its compiled CSS", async () => {
      await renderToolbar([new AssemblyActionGroup([makeAction("more actions")])], true);

      const styleText = Array.from(document.querySelectorAll("style"))
         .map(s => s.textContent)
         .join("\n");
      const coarseIndex = styleText.indexOf("pointer: coarse");

      expect(coarseIndex).toBeGreaterThan(-1);

      // Angular's emulated encapsulation rewrites selectors with long _ngcontent-*/_nghost-*
      // attribute qualifiers (visible in the failure output below if this regresses), so this
      // checks property text anywhere after the media query opens, not a short fixed-length
      // slice of it.
      const afterCoarse = styleText.slice(coarseIndex);

      // The container's clip-fix: height freed from the pinned 24px row (auto, not a literal
      // override of the mouse-pointer value) with a 44px floor, and overflow no longer hidden.
      expect(afterCoarse).toContain("height: auto");
      expect(afterCoarse).toContain("min-height: var(--inet-control-height-touch)");
      expect(afterCoarse).toContain("overflow: visible");
      // The kebab's own floor, unchanged from round 1.
      expect(afterCoarse).toContain("min-width: var(--inet-control-height-touch)");
   });
});

// Final-review round, Critical 1: the cap was passed to ToolbarActionsHandler as-is, but that helper
// spends one of the slots it is given on the overflow control, so "3" produced a two-button strip
// and chart properties-toolbar never reached it at any width. Every existing test asserted the slot
// number, not the button count, which is how it survived five reviews — so this counts the buttons
// the browser actually renders, driven by a real ChartActions rather than hand-built groups.
describe("MiniToolbar rendered - three action buttons plus the kebab under the gate", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   async function renderChartStrip(width: number, height: number) {
      document.body.classList.add("viz-modern");
      const model: VSChartModel = TestUtils.createMockVSChartModel("Chart1");
      model.objectFormat.width = width;
      model.objectFormat.height = height;
      const actions = new ChartActions(model, { getPopComponent: () => "" } as any,
         ComposerContextProviderFactory(), false, null, null,
         new MiniToolbarService({ runOutsideAngular: (fn: () => any) => fn() } as any));

      return renderWithActions(actions, true);
   }

   function buttonTitles(root: HTMLElement): string[] {
      return Array.from(root.querySelectorAll<HTMLElement>(
         ".mini-toolbar-button-group button:not(.mini-toolbar-kebab)"))
         .map(b => b.getAttribute("title"));
   }

   it("renders exactly three action buttons and one kebab on a wide, tall chart", async () => {
      const { fixture } = await renderChartStrip(2000, 400);
      const root: HTMLElement = fixture.nativeElement;

      expect(buttonTitles(root).length).toBe(3);
      expect(root.querySelectorAll(".mini-toolbar-kebab").length).toBe(1);
      // Identified by icon rather than title: labels are _#(js:...) resource keys that the i18n
      // step substitutes at build time, not in this environment. These three icons are the ones
      // ChartActions gives show-data / open-max-mode / properties-toolbar, in stable-first order.
      const icons = Array.from(root.querySelectorAll<HTMLElement>(
         ".mini-toolbar-button-group button:not(.mini-toolbar-kebab) i"))
         .map(i => i.className);

      expect(icons[0]).toContain("show-summary-icon");
      expect(icons[1]).toContain("expand-icon");
      // The regression's actual casualty: chart properties-toolbar, an entire task's deliverable.
      expect(icons[2]).toContain("setting-icon");
   });

   it("renders the kebab alone between the 32px floor and 56px", async () => {
      const { fixture } = await renderChartStrip(2000, 40);
      const root: HTMLElement = fixture.nativeElement;

      expect(buttonTitles(root).length).toBe(0);
      expect(root.querySelectorAll(".mini-toolbar-kebab").length).toBe(1);
   });

   it("renders no chrome at all below the 32px floor", async () => {
      const { fixture } = await renderChartStrip(2000, 24);
      const root: HTMLElement = fixture.nativeElement;

      expect(root.querySelector(".mini-toolbar-container")).toBeNull();
      expect(root.querySelector(".mini-toolbar-kebab")).toBeNull();
   });

   it("drops action buttons before the kebab when width binds below the cap", async () => {
      const { fixture } = await renderChartStrip(120, 400);
      const root: HTMLElement = fixture.nativeElement;

      expect(buttonTitles(root).length).toBe(2);
      expect(root.querySelectorAll(".mini-toolbar-kebab").length).toBe(1);
   });

   // Touch defect: the kebab rendered but tapping it opened nothing. The template's
   // @if (!mobileDevice) means no action button reaches the DOM, yet allowedActionsNum() still
   // handed getMoreActions() a full budget, so it skipped the leading actions as "already on the
   // strip" and returned an empty list. This pairs the rendered DOM with the list the kebab's
   // click actually passes to VSUtil.showDropdownMenus() — asserting the numbers alone is what let
   // the defect through.
   describe("on touch", () => {
      let mobileSpy: any = null;

      afterEach(() => {
         if(mobileSpy) {
            mobileSpy.mockRestore();
            mobileSpy = null;
         }
      });

      async function renderTouchChartStrip(width: number, height: number) {
         // Before renderChartStrip so both ChartActions' and MiniToolbar's mobileDevice field
         // initializers see it.
         mobileSpy = vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
         return renderChartStrip(width, height);
      }

      it("renders the kebab and no action button, and the kebab's list is the actions that did not render", async () => {
         const { fixture } = await renderTouchChartStrip(2000, 400);
         const root: HTMLElement = fixture.nativeElement;
         const actions = fixture.componentInstance.actions as ChartActions;

         expect(buttonTitles(root).length).toBe(0);
         expect(root.querySelectorAll(".mini-toolbar-kebab").length).toBe(1);

         const more = actions.getMoreActions()
            .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);

         expect(more.length).toBeGreaterThan(0);
         expect(more).toContain("chart show-data");
         expect(more).toContain("chart open-max-mode");
      });
   });
});

// Final-review round, Critical 2: <mini-toolbar> is a DOM sibling of .vs-object-parent-container, so
// the assembly-hover selectors in _moving-resize.scss stop matching the moment the pointer lands on
// an action button; without a :hover rule on the strip itself the container hid again 120ms later,
// the pointer fell through, hover re-fired, and the buttons were only intermittently clickable.
//
// jsdom has no pointer and does not evaluate :hover for getComputedStyle (same limitation as the
// @media probe above), so no assertion here can observe the reveal. This checks the rule ships in
// the compiled stylesheet, correctly scoped; the behavioural argument is in the fix report.
describe("MiniToolbar rendered - anchored hover persistence (compiled CSS only; see fix report)", () => {
   it("ships a :hover reveal that puts the action groups back in layout, scoped against a dismissed strip", async () => {
      await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], true);

      const styleText = Array.from(document.querySelectorAll("style"))
         .map(s => s.textContent)
         .join("\n");
      // Angular's emulated encapsulation inserts _ngcontent-*/_nghost-* attribute qualifiers into
      // every compound selector, so this matches the ordered parts of the selector rather than a
      // literal string. ":not(.hidden-mini-toolbar)" before ":hover" is the load-bearing part: an
      // unscoped rule would resurrect a strip dismissed via Hide MiniToolbar.
      expect(styleText).toMatch(
         /\.mini-toolbar--anchored[^{}]*:not\(\.hidden-mini-toolbar\)[^{}]*:hover[^{}]*\.mini-toolbar-button-group[^{},]*:not\(\.mini-toolbar-kebab-group\)[^{}]*[,{]/);
      expect(styleText).toMatch(
         /\.mini-toolbar--anchored[^{}]*:not\(\.hidden-mini-toolbar\)[^{}]*:focus-within[^{}]*\.mini-toolbar-button-group[^{},]*:not\(\.mini-toolbar-kebab-group\)[^{}]*\{[^}]*display:\s*inline-flex/);
   });
});

// Alignment fix: the anchored strip is right-aligned by layout, not by geometry. The host's box is
// the whole title lane (its inline width is the lane width the container binds) and the pill —
// `width: fit-content !important`, which the template's non-important [style.min-width.px] cannot
// beat — right-aligns inside it via an auto left margin. That replaces subtracting an estimated
// strip width from the right inset, which left the strip at the lane's *left* inset because the
// pill's box never spanned the assembly.
//
// These two assertions are layout-free by construction: they read an inline style attribute and one
// resolved declaration. jsdom performs no flow layout, so neither one proves the pill's right edge
// lands on the inset — that follows from `margin-left: auto` on a definite-width block box, argued
// in the fix report. What they do prove is that the two inputs that argument depends on are
// actually present: a lane-wide host box and an auto left margin on the pill.
describe("MiniToolbar rendered - anchored strip spans the lane and the pill margin auto-aligns", () => {
   it("puts the bound lane width on the anchored host box", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], true);
      const host = fixture.nativeElement.querySelector(".mini-toolbar");

      // width: 100 is the input renderWithActions binds; anchored, that is the lane width from
      // VSObjectContainer.getAnchoredToolbarWidth().
      expect(host.style.width).toBe("100px");
   });

   // Each test renders once: two render() calls in one test re-enter TestBed.configureTestingModule
   // after instantiation, which throws.
   it("leaves a non-anchored host box shrink-to-fit, with no inline width", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")])
      ], false);
      const host = fixture.nativeElement.querySelector(".mini-toolbar");

      expect(host.style.width).toBe("");
   });

   it("resolves margin-left: auto on the anchored pill", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ], true);
      const pill = fixture.nativeElement.querySelector(".mini-toolbar-container");

      expect(window.getComputedStyle(pill).marginLeft).toBe("auto");
   });

   it("does not give a non-anchored pill an auto left margin", async () => {
      const { fixture } = await renderToolbar([
         new AssemblyActionGroup([makeAction("chart show-data")])
      ], false);
      const pill = fixture.nativeElement.querySelector(".mini-toolbar-container");

      expect(window.getComputedStyle(pill).marginLeft).not.toBe("auto");
   });
});
