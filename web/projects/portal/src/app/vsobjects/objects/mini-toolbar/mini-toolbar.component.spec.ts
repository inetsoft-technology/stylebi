/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { Component, DebugElement } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { Subject } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { ContextProvider } from "../../context-provider.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSDataTipDirective } from "../data-tip/vs-data-tip.directive";
import { NavigationKeys } from "../navigation-keys";
import { MiniToolbar } from "./mini-toolbar.component";
import { MiniToolbarService } from "./mini-toolbar.service";

// Mirrors the real composition in vs-object-container.component.html: a <mini-toolbar> that
// also carries [VSDataTip][miniToolbar]=true, plus the assembly's own main element (looked up
// by id inside VSDataTipDirective.ngDoCheck) that the data tip popup is positioned relative to.
@Component({
   standalone: true,
   imports: [MiniToolbar, VSDataTipDirective],
   template: `
      <div id="Tip1_main"></div>
      <mini-toolbar VSDataTip [dataTipName]="'Tip1'" [miniToolbar]="true"
         [assembly]="'Tip1'" [top]="500" [left]="500" [width]="200">
      </mini-toolbar>
   `
})
class TestApp {
}

describe("MiniToolbar Tests", () => {
   let fixture: ComponentFixture<TestApp>;
   let miniToolbarDebugEl: DebugElement;
   let dataTipService: any;

   function toolbarDiv(): HTMLElement {
      return miniToolbarDebugEl.nativeElement.querySelector(".mini-toolbar");
   }

   beforeEach(() => {
      dataTipService = {
         isDataTip: vi.fn((name: string) => name === "Tip1"),
         isCurrentDataTip: vi.fn(() => false),
         isDataTipVisible: vi.fn(() => false),
         isFrozen: vi.fn(() => false),
         getVSObjectId: vi.fn(() => "Tip1_main"),
         dataTipX: 100,
         dataTipY: 200,
         dataTipName: "Tip1",
         dataTipAlpha: 1,
         scrolled: new Subject<void>(),
         viewerOffset: {width: 1000, height: 1000, scrollLeft: 0, scrollTop: 0}
      };

      TestBed.configureTestingModule({
         imports: [TestApp],
         providers: [
            {provide: DataTipService, useValue: dataTipService},
            {
               provide: PopComponentService,
               useValue: {
                  getPopComponent: vi.fn(() => null),
                  hasPopUpComponentShowing: vi.fn(() => false),
                  getPopInfo: vi.fn(() => null),
                  isPopComponentShow: vi.fn(() => false)
               }
            },
            {provide: DebounceService, useValue: {debounce: vi.fn(), cancel: vi.fn()}},
            {
               provide: ContextProvider,
               useValue: {composer: false, vsWizard: false, binding: false}
            },
            MiniToolbarService
         ]
      });

      fixture = TestBed.createComponent(TestApp);
      miniToolbarDebugEl = fixture.debugElement.query(By.directive(MiniToolbar));
   });

   // Bug #76399: while this mini-toolbar is the active, visible data tip, VSDataTipDirective
   // (co-located via [VSDataTip][miniToolbar]=true) imperatively owns its position. Before the
   // fix, MiniToolbar's own [style.top.px]/[style.left.px] template bindings independently wrote
   // the static `top`/`left` Inputs to the same element and -- because MiniToolbar's child view
   // refreshes after the parent-view directive's ngDoCheck on the very same change detection
   // pass -- clobbered the directive's correct value back to the static one whenever the static
   // binding's own value changed (e.g. because a toolbar action's visibility changed the
   // computed `left`). This asserts the directive's mouse-tracked value wins and stays applied,
   // not the static `top`/`left` Inputs (500/500), when the tip is active.
   it("lets VSDataTipDirective own the position while this is the active data tip", () => {
      dataTipService.isCurrentDataTip.mockReturnValue(true);
      dataTipService.isDataTipVisible.mockReturnValue(true);

      fixture.detectChanges();

      const div = toolbarDiv();
      expect(div.style.left).toBe("101px");
      expect(div.style.top).toBe("173px");
      expect(div.style.left).not.toBe("500px");
   });

   // Control: when this mini-toolbar is not an active data tip (the ordinary case for the vast
   // majority of mini-toolbars), the static top/left Inputs still drive its position exactly as
   // before this fix.
   it("uses the static top/left Inputs when not an active data tip", () => {
      fixture.detectChanges();

      const div = toolbarDiv();
      expect(div.style.left).toBe("500px");
      // topY = top - miniToolbarHeight(28, gate off) since top(500) > minTop(20)
      expect(div.style.top).toBe("472px");
   });
});

// The topY math is pure and depends only on `top`, the context flags, and the pop-component check,
// so we construct the component directly with light mocks rather than through TestBed.
function makeToolbar(): MiniToolbar {
   const contextProvider: any = { composer: false, vsWizard: false, binding: false };
   // getNextAction()/getPreviousAction() defer focus-moving to a setTimeout that queries
   // ".bd-selected-cell" and calls .focus() on it; stub both so that deferred callback (which
   // fires after the test body returns) does not throw against a bare {}.
   const element: any = { nativeElement: { querySelector: () => ({ focus: () => {} }) } };
   const miniToolbarService: any = {};
   const popComponentService: any = { isPopComponentShow: () => false };
   // No co-located [VSDataTip] directive, so isActiveDataTip is false and topY stays numeric.
   const comp = new MiniToolbar(contextProvider, element, miniToolbarService, popComponentService,
                                null);
   comp.assembly = "Chart1";
   return comp;
}

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

// Bypasses the AbstractVSActions contract entirely: MiniToolbar.getActions() only ever reads
// showingActions off whatever is assigned to `actions`, so a bare object with that property is
// sufficient and keeps these tests decoupled from AbstractVSActions' own fit-ladder logic
// (covered separately in abstract-vs-actions.spec.ts).
function setShowingActions(comp: MiniToolbar, groups: AssemblyActionGroup[]): void {
   comp.actions = { showingActions: groups } as any;
   comp.ngOnChanges({ actions: {} as any });
}

describe("MiniToolbar.topY", () => {
   it("positions the toolbar 28px above the object when the gate is off", () => {
      const comp = makeToolbar();
      comp.top = 100;
      expect(comp.topY).toBe(72); // 100 - 28 - 0
   });

   it("positions the toolbar 24px above the object under the assembly's own vizModern", () => {
      const comp = makeToolbar();
      comp.actions = { getModel: () => ({ vizModern: true }) } as any;
      comp.top = 100;
      expect(comp.topY).toBe(76); // 100 - 24 - 0
   });

   it("stops subtracting its own height when anchored", () => {
      const comp = makeToolbar();
      comp.top = 200;
      comp.anchorInTitleLane = true;

      expect(comp.topY).toBe(200);
   });

   it("still floats above the assembly when not anchored", () => {
      const comp = makeToolbar();
      comp.top = 200;
      comp.anchorInTitleLane = false;

      expect(comp.topY).toBeLessThan(200);
   });

   // Max mode mounts the strip through vs-object-view with [top]="0" [forceAbove]="true". forceAbove
   // bypasses the minTop guard that otherwise keeps this branch positive, so the strip lands above
   // its own origin — clipped by the host's overflow-y: hidden, or drawn off-canvas. Either way the
   // enlarged toolbar never appears. There is no space above origin to float into, so floor it.
   it("never positions the strip above its own origin (max mode: top 0 with forceAbove)", () => {
      const comp = makeToolbar();
      comp.top = 0;
      comp.forceAbove = true;

      expect(comp.topY).toBe(0);
   });

   it("floors at the origin under the gate too", () => {
      const comp = makeToolbar();
      comp.actions = { getModel: () => ({ vizModern: true }) } as any;
      comp.top = 0;
      comp.forceAbove = true;

      expect(comp.topY).toBe(0);
   });

   it("still floats above a low assembly that has room, rather than flooring everything", () => {
      const comp = makeToolbar();
      comp.top = 40;
      comp.forceAbove = true;

      expect(comp.topY).toBe(12); // 40 - 28 - 0, unchanged by the floor
   });
});

// isFocused(i, j) and doAction() key off the group/action indices produced by the two @for loops
// in the template (displayActions -> group index i, group.actions -> action index j). Written
// before the Step 8 template restructure (splitting the kebab out of the mobile-guarded button
// container into its own element) specifically because that restructure changes what gets
// iterated — if it ever renumbers the surviving action buttons, these break and catch it.
describe("MiniToolbar focus indices", () => {
   it("reports isFocused only for the exact group/action landed on by forward navigation", () => {
      const comp = makeToolbar();
      setShowingActions(comp, [
         new AssemblyActionGroup([makeAction("chart show-data"), makeAction("chart open-max-mode")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ]);

      comp.forceShow = true; // group 0, action 0
      comp.navigate(NavigationKeys.RIGHT); // group 0, action 1
      comp.navigate(NavigationKeys.RIGHT); // group 1, action 0 - the trailing kebab action

      expect(comp.isFocused(1, 0)).toBe(true);
      expect(comp.isFocused(0, 1)).toBe(false);
      expect(comp.isFocused(0, 0)).toBe(false);
   });

   it("keeps the kebab's index stable when it shares a group with an action button", () => {
      const comp = makeToolbar();
      setShowingActions(comp, [
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("chart open-max-mode"), makeAction("more actions")])
      ]);

      comp.forceShow = true; // group 0, action 0
      comp.navigate(NavigationKeys.RIGHT); // group 1, action 0
      comp.navigate(NavigationKeys.RIGHT); // group 1, action 1 - the kebab, second in its group

      expect(comp.isFocused(1, 1)).toBe(true);
      expect(comp.isFocused(1, 0)).toBe(false);
   });

   it("skips invisible and disabled actions when navigating backward onto the kebab", () => {
      const comp = makeToolbar();
      setShowingActions(comp, [
         new AssemblyActionGroup([
            makeAction("chart show-data", { visible: () => false }),
            makeAction("chart open-max-mode", { enabled: () => false })
         ]),
         new AssemblyActionGroup([makeAction("more actions")])
      ]);

      comp.forceShow = true; // lands on group 1, action 0: the only visible+enabled action

      expect(comp.isFocused(1, 0)).toBe(true);
   });

   it("doAction stops the click from propagating and invokes the action", () => {
      const comp = makeToolbar();
      const action = makeAction("more actions");
      const event: any = { stopPropagation: vi.fn() };

      comp.doAction(action, event);

      expect(event.stopPropagation).toHaveBeenCalledTimes(1);
      expect(action.action).toHaveBeenCalledWith(event);
   });
});

describe("MiniToolbar kebab split (Step 8)", () => {
   it("does not split when not anchored, so non-chart/gate-off rendering is untouched", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      setShowingActions(comp, [
         new AssemblyActionGroup([makeAction("chart show-data"), makeAction("more actions")])
      ]);

      expect(comp.kebabAction).toBeNull();
      expect(comp.actionButtonGroups).toBe(comp.displayActions);
   });

   it("splits the trailing kebab out of the last group when anchored", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      setShowingActions(comp, [
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("chart open-max-mode"), makeAction("more actions")])
      ]);

      expect(comp.kebabAction.id()).toBe("more actions");
      const ids = comp.actionButtonGroups
         .reduce((acc, g) => acc.concat(g.actions.map(a => a.id())), [] as string[]);
      expect(ids).toEqual(["chart show-data", "chart open-max-mode"]);
   });

   it("leaves earlier groups untouched and only trims the last one", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      const group0 = new AssemblyActionGroup([makeAction("chart show-data")]);
      setShowingActions(comp, [
         group0,
         new AssemblyActionGroup([makeAction("more actions")])
      ]);

      expect(comp.actionButtonGroups[0]).toBe(group0);
   });

   it("finds no kebab when anchored but the last action isn't the resident kebab", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      setShowingActions(comp, [
         new AssemblyActionGroup([makeAction("chart show-data"), makeAction("chart edit")])
      ]);

      expect(comp.kebabAction).toBeNull();
      expect(comp.actionButtonGroups).toBe(comp.displayActions);
   });

   it("finds no kebab when anchored and displayActions is empty (below the 32px floor)", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      setShowingActions(comp, []);

      expect(comp.kebabAction).toBeNull();
      expect(comp.actionButtonGroups).toEqual([]);
   });

   it("isKebabFocused tracks focus landing on the kebab's slot in the untrimmed list", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      setShowingActions(comp, [
         new AssemblyActionGroup([makeAction("chart show-data")]),
         new AssemblyActionGroup([makeAction("more actions")])
      ]);

      expect(comp.isKebabFocused()).toBe(false);

      comp.forceShow = true; // group 0, action 0
      comp.navigate(NavigationKeys.RIGHT); // group 1, action 0 - the kebab

      expect(comp.isKebabFocused()).toBe(true);
   });
});

// Important 3: below the 32px control floor, AbstractVSActions.showingActions leaves both
// actionButtonGroups and kebabAction empty for an anchored strip. Without a guard,
// .mini-toolbar-container would still render — bordered, backgrounded, and (once the
// assembly-hover reveal fires) fully opaque — the one rung the fit ladder says should show no
// chrome at all.
//
// The same emptiness reaches the container off the anchored path, which is what dense does:
// isAnchoredResident() is false there, so the host passes anchorInTitleLane false while
// isAnchoredChromeSuppressed() empties the action list. The guard therefore cannot be conditioned
// on being anchored.
describe("MiniToolbar.showToolbarContainer", () => {
   // This read "mirrors !mobileDevice when not anchored, regardless of content" until 2026-08-20,
   // which recorded the pre-existing behaviour rather than a rule. Dense disproved it: an anchored
   // type at dense is un-anchored by the host *and* has its whole action list suppressed, so
   // "regardless of content" drew an empty bordered pill above the card.
   it("hides the container when not anchored and there is nothing to show", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      comp.mobileDevice = false;
      setShowingActions(comp, []);

      expect(comp.showToolbarContainer).toBe(false);
   });

   it("shows the container when not anchored and actions survive", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      comp.mobileDevice = false;
      setShowingActions(comp, [new AssemblyActionGroup([makeAction("chart show-data")])]);

      expect(comp.showToolbarContainer).toBe(true);
   });

   it("stays hidden on mobile off the anchored path even with actions", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      comp.mobileDevice = true;
      setShowingActions(comp, [new AssemblyActionGroup([makeAction("chart show-data")])]);

      expect(comp.showToolbarContainer).toBe(false);
   });

   it("hides the container when anchored with nothing to show (below the floor)", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      comp.mobileDevice = false;
      setShowingActions(comp, []);

      expect(comp.showToolbarContainer).toBe(false);
   });

   it("shows the container when anchored and only the kebab survives (32-56px band)", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      setShowingActions(comp, [new AssemblyActionGroup([makeAction("more actions")])]);

      expect(comp.showToolbarContainer).toBe(true);
   });

   it("shows the container when anchored and on mobile, as long as the kebab survives", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      comp.mobileDevice = true;
      setShowingActions(comp, [new AssemblyActionGroup([makeAction("more actions")])]);

      expect(comp.showToolbarContainer).toBe(true);
   });

   it("shows the container when anchored with action buttons present", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      setShowingActions(comp, [
         new AssemblyActionGroup([makeAction("chart show-data"), makeAction("more actions")])
      ]);

      expect(comp.showToolbarContainer).toBe(true);
   });
});

// Max mode (isToolbarAnchored() && !maxMode) turns anchorInTitleLane off, but touch has no hover
// to reveal a non-resident strip — residentKebab is the host's signal that the type still carries
// the kebab-only design regardless, so kebabResident (and everything gated on it) must stay true
// on touch even though anchorInTitleLane alone would now say no.
describe("MiniToolbar.kebabResident (touch without anchoring, e.g. max mode)", () => {
   it("is false off the anchored path on desktop, matching the accepted hover-reveal trade-off", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      comp.residentKebab = true;
      comp.mobileDevice = false;

      expect(comp.kebabResident).toBe(false);
   });

   it("is true off the anchored path on touch, once residentKebab says the type carries the design", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      comp.residentKebab = true;
      comp.mobileDevice = true;

      expect(comp.kebabResident).toBe(true);
   });

   it("is false on touch when the type never carried the resident design (residentKebab false)", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      comp.residentKebab = false;
      comp.mobileDevice = true;

      expect(comp.kebabResident).toBe(false);
   });

   it("splits out the kebab and keeps the container visible on touch, unanchored", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      comp.residentKebab = true;
      comp.mobileDevice = true;
      setShowingActions(comp, [
         new AssemblyActionGroup([makeAction("selection-list show-data"), makeAction("more actions")])
      ]);

      expect(comp.kebabAction.id()).toBe("more actions");
      expect(comp.showToolbarContainer).toBe(true);
   });
});

// Important 2: for an anchored strip, the host .mini-toolbar is visibility: visible at rest (see
// mini-toolbar.component.scss), so the pre-existing "is the host hidden" guard never trips for
// it. Without a second, anchored-specific check, any Esc keyup anywhere in the app — closing an
// unrelated dialog, leaving a selection — would silently dismiss an arbitrary chart's strip.
// Real DOM elements with inline styles stand in for the compiled SCSS cascade here (that cascade
// itself, including the visibility: hidden fix for the button click-trap, is exercised against
// the real stylesheet in mini-toolbar.component.tl.spec.ts); this file only pins onKeyUp's own
// branching logic against getComputedStyle.
describe("MiniToolbar.onKeyUp", () => {
   afterEach(() => {
      document.body.innerHTML = "";
   });

   // Mirrors the actual DOM shape onKeyUp queries: element.nativeElement is the <mini-toolbar>
   // host tag, a *container* of the .mini-toolbar div (its template's root), which may itself
   // contain a .mini-toolbar-container holding the action-button groups. The reveal signal is the
   // action groups' display (none at rest, inline-flex on reveal), not the container's opacity —
   // the container is opaque in both states now that the kebab lives inside it, since opacity is
   // not escapable by a descendant.
   function makeElement(hostVisibility: string, groupDisplay?: string): { nativeElement: HTMLElement } {
      const hostTag = document.createElement("div");
      const toolbar = document.createElement("div");
      toolbar.className = "mini-toolbar";
      toolbar.style.visibility = hostVisibility;
      hostTag.appendChild(toolbar);

      const container = document.createElement("div");
      container.className = "mini-toolbar-container";
      toolbar.appendChild(container);

      // The kebab's own group is always present and is never the reveal signal — onKeyUp must skip
      // it via :not(.mini-toolbar-kebab-group) or it would read "inline-flex" and always dismiss.
      const kebabGroup = document.createElement("div");
      kebabGroup.className = "mini-toolbar-button-group mini-toolbar-kebab-group";
      kebabGroup.style.display = "inline-flex";
      container.appendChild(kebabGroup);

      if(groupDisplay !== undefined) {
         const group = document.createElement("div");
         group.className = "mini-toolbar-button-group";
         group.style.display = groupDisplay;
         container.appendChild(group);
      }

      document.body.appendChild(hostTag);
      return { nativeElement: hostTag };
   }

   function wire(comp: MiniToolbar, element: { nativeElement: HTMLElement }): ReturnType<typeof vi.fn> {
      const hideMiniToolbar = vi.fn();
      (comp as any).element = element;
      (comp as any).miniToolbarService = { hideMiniToolbar };
      return hideMiniToolbar;
   }

   it("does nothing when the host itself is visibility: hidden", () => {
      const comp = makeToolbar();
      const hideMiniToolbar = wire(comp, makeElement("hidden"));

      comp.onKeyUp();

      expect(hideMiniToolbar).not.toHaveBeenCalled();
   });

   it("dismisses a non-anchored strip that is genuinely showing", () => {
      const comp = makeToolbar();
      const hideMiniToolbar = wire(comp, makeElement("visible"));

      comp.onKeyUp();

      expect(hideMiniToolbar).toHaveBeenCalledWith("Chart1", true);
   });

   it("does not dismiss an anchored strip at rest, even though its host is visibility: visible", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      const hideMiniToolbar = wire(comp, makeElement("visible", "none"));

      comp.onKeyUp();

      expect(hideMiniToolbar).not.toHaveBeenCalled();
   });

   it("dismisses an anchored strip once its action buttons are actually revealed", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      const hideMiniToolbar = wire(comp, makeElement("visible", "inline-flex"));

      comp.onKeyUp();

      expect(hideMiniToolbar).toHaveBeenCalledWith("Chart1", true);
   });

   // Touch, and the kebab-only height band: no action group renders, so there is nothing revealed
   // for Esc to dismiss. The kebab's own group must not be mistaken for one.
   it("does not dismiss an anchored strip that has only the kebab group", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = true;
      const hideMiniToolbar = wire(comp, makeElement("visible"));

      expect(() => comp.onKeyUp()).not.toThrow();
      expect(hideMiniToolbar).not.toHaveBeenCalled();
   });

   // Max mode turns anchorInTitleLane off (no title lane to anchor into), but a maximised selection
   // on touch is still kebabResident (residentKebab && mobileDevice) and therefore visibility:
   // visible at rest, per .mini-toolbar--anchored in mini-toolbar.component.scss. Keying the guard
   // on anchorInTitleLane alone would miss this case: the host-hidden check never trips, the
   // resident-only check never runs, and Esc would dismiss a strip with no hover to bring it back.
   it("does not dismiss a maximised selection's resident strip on touch, even though anchorInTitleLane is false", () => {
      const comp = makeToolbar();
      comp.anchorInTitleLane = false;
      comp.residentKebab = true;
      comp.mobileDevice = true;
      const hideMiniToolbar = wire(comp, makeElement("visible"));

      comp.onKeyUp();

      expect(hideMiniToolbar).not.toHaveBeenCalled();
   });
});
