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
 * ActionsContextmenuComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — actions setter: visibleActions filtering; wrong filter hides items
 *                       the user should see or shows items that should be hidden
 *   Group 2 [Risk 2] — onClick: action handler must be invoked with the correct sourceEvent
 *                       AND onClose must be emitted; dropping either leaves the menu broken
 *   Group 3 [Risk 2] — itemVisible: dual-path boolean logic with and without actionVisible fn;
 *                       wrong result causes invisible items to appear or visible ones to hide
 *   Group 4 [Risk 2] — closeChild / closeAll / closeDescendants: correct chain teardown;
 *                       stale refs leave orphaned dropdowns attached to the DOM
 *   Group 5 [Risk 3] — oozOpenChild: opens child dropdown with correct position options;
 *                       if dropdownService.open is not called or the child onClose subscription
 *                       is not wired, sub-menus are permanently broken
 *   Group 6 [Risk 1] — isFocused: boolean getter, true and false paths
 *   Group 7 [Risk 1] — closeSelf: emits onClose
 *   Group 8 [Risk 1] — getElementAbsoluteTop / getElementAbsoluteLeft: offsetParent chain
 *   Group 9 [Risk 1] — focused setter: sets focusedGroup and focusedAction
 *   Group 10 [Risk 1] — ngOnDestroy: unsubscribes childSubscription to prevent memory leak
 *
 * Confirmed bugs: none
 *
 * Out of scope:
 *   forwardRef / optional DI — tested indirectly via DROPDOWN_SERVICE_MOCK in Group 5
 *   zone.run() call inside oozOpenChild — framework behaviour, not a component contract
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { By } from "@angular/platform-browser";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";

import { ActionsContextmenuComponent } from "./actions-contextmenu.component";
import { FixedDropdownService } from "./fixed-dropdown.service";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { AssemblyAction } from "../../common/action/assembly-action";

// ---------------------------------------------------------------------------
// Module-level helpers
// ---------------------------------------------------------------------------

function makeAction(overrides: Partial<AssemblyAction> = {}): AssemblyAction {
   return {
      id: () => "id",
      label: () => "label",
      icon: () => "icon",
      visible: () => true,
      enabled: () => true,
      action: vi.fn(),
      ...overrides,
   };
}

function makeMockMouseEvent(
   target: any = { offsetTop: 0, offsetLeft: 0, offsetWidth: 0, offsetParent: null }
): MouseEvent {
   return {
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
      composedPath: vi.fn().mockReturnValue([target]),
      target,
   } as unknown as MouseEvent;
}

// ---------------------------------------------------------------------------
// Module-level mocks — reset in beforeEach
// ---------------------------------------------------------------------------

let mockChildOnClose: Subject<void>;
let mockDropdownRef: { close: ReturnType<typeof vi.fn>; componentInstance: any };
const DROPDOWN_SERVICE_MOCK = { open: vi.fn() };

beforeEach(() => {
   mockChildOnClose = new Subject<void>();
   mockDropdownRef = {
      close: vi.fn().mockReturnValue(true),
      componentInstance: {
         sourceEvent: null as any,
         actions: null as any,
         onClose: mockChildOnClose,
      },
   };
   DROPDOWN_SERVICE_MOCK.open.mockClear();
   DROPDOWN_SERVICE_MOCK.open.mockReturnValue(mockDropdownRef);
});

async function renderComp() {
   const { fixture } = await render(ActionsContextmenuComponent, {
      providers: [{ provide: FixedDropdownService, useValue: DROPDOWN_SERVICE_MOCK }],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: fixture.componentInstance, fixture };
}

// ---------------------------------------------------------------------------
// Group 1: actions setter — visibleActions filtering
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — actions setter", () => {

   // 🔁 Regression-sensitive: visibleActions drives the template loop; wrong filtering
   // silently hides valid menu items or exposes items the user should not see.
   it("should include only groups where getVisible() returns true", async () => {
      const { comp } = await renderComp();
      const visible = new AssemblyActionGroup([makeAction({ visible: () => true })]);
      const invisible = new AssemblyActionGroup([makeAction({ visible: () => false })]);
      comp.actions = [visible, invisible];
      expect(comp.visibleActions).toHaveLength(1);
      expect(comp.visibleActions[0]).toBe(visible);
   });

   it("should set visibleActions to [] when actions is null", async () => {
      const { comp } = await renderComp();
      comp.actions = null;
      expect(comp.visibleActions).toEqual([]);
   });

   it("should further filter groups through actionVisible when provided", async () => {
      const { comp } = await renderComp();
      const allowed = makeAction({ id: () => "allowed", visible: () => true });
      const blocked = makeAction({ id: () => "blocked", visible: () => true });
      const g1 = new AssemblyActionGroup([allowed]);
      const g2 = new AssemblyActionGroup([blocked]);
      comp.actionVisible = (action) => action.id() === "allowed";
      comp.actions = [g1, g2];
      expect(comp.visibleActions).toHaveLength(1);
      expect(comp.visibleActions[0]).toBe(g1);
   });
});

// ---------------------------------------------------------------------------
// Group 2: onClick — action invocation + close emission
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — onClick", () => {

   // 🔁 Regression-sensitive: both the action invocation and the onClose emission are
   // required; dropping onClose leaves the dropdown stuck open after the user acts.
   it("should call action.action with the current sourceEvent", async () => {
      const { comp } = await renderComp();
      const fakeEvent = {} as MouseEvent;
      comp.sourceEvent = fakeEvent;
      const actionFn = vi.fn();
      comp.onClick({ action: actionFn });
      expect(actionFn).toHaveBeenCalledWith(fakeEvent);
   });

   it("should emit onClose after invoking the action", async () => {
      const { comp } = await renderComp();
      const closed: void[] = [];
      comp.onClose.subscribe(v => closed.push(v));
      comp.sourceEvent = {} as MouseEvent;
      comp.onClick({ action: vi.fn() });
      expect(closed).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 3: itemVisible — dual-path boolean with and without actionVisible
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — itemVisible", () => {

   it("should return true when action.action exists and visible() is true (no actionVisible)", async () => {
      const { comp } = await renderComp();
      expect(comp.itemVisible(makeAction({ visible: () => true }))).toBe(true);
   });

   it("should return false when visible() is false (no actionVisible)", async () => {
      const { comp } = await renderComp();
      expect(comp.itemVisible(makeAction({ visible: () => false }))).toBe(false);
   });

   it("should return false when action.action is undefined (no actionVisible)", async () => {
      const { comp } = await renderComp();
      expect(comp.itemVisible(makeAction({ action: undefined }))).toBeFalsy();
   });

   it("should return true when actionVisible returns true and action is visible", async () => {
      const { comp } = await renderComp();
      comp.actionVisible = () => true;
      expect(comp.itemVisible(makeAction({ visible: () => true }))).toBe(true);
   });

   it("should return false when actionVisible returns false even if visible() is true", async () => {
      const { comp } = await renderComp();
      comp.actionVisible = () => false;
      expect(comp.itemVisible(makeAction({ visible: () => true }))).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: closeChild / closeAll / closeDescendants
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — close methods", () => {

   it("closeChild: returns false when dropdownRef is null", async () => {
      const { comp } = await renderComp();
      expect(comp.closeChild()).toBe(false);
   });

   it("closeChild: calls dropdownRef.close(), nullifies ref and instance, returns true", async () => {
      const { comp } = await renderComp();
      comp.dropdownRef = mockDropdownRef as any;
      comp.instance = {} as any;
      const result = comp.closeChild();
      expect(mockDropdownRef.close).toHaveBeenCalledTimes(1);
      expect(result).toBe(true);
      expect(comp.dropdownRef).toBeNull();
      expect(comp.instance).toBeNull();
   });

   it("closeAll: closes child ref AND emits onClose", async () => {
      const { comp } = await renderComp();
      comp.dropdownRef = mockDropdownRef as any;
      const closed: void[] = [];
      comp.onClose.subscribe(v => closed.push(v));
      comp.closeAll();
      expect(mockDropdownRef.close).toHaveBeenCalledTimes(1);
      expect(closed).toHaveLength(1);
   });

   // 🔁 Regression-sensitive: closeDescendants is called before re-opening a sub-menu;
   // if it fails to close the chain, orphaned dropdowns accumulate.
   it("closeDescendants: closes context's ref and this component's own ref", async () => {
      const { comp } = await renderComp();
      comp.dropdownRef = mockDropdownRef as any;
      const context = { closeChild: vi.fn().mockReturnValue(true), instance: null } as any;
      const result = comp.closeDescendants(context);
      expect(context.closeChild).toHaveBeenCalledTimes(1);
      expect(mockDropdownRef.close).toHaveBeenCalledTimes(1);
      expect(result).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: oozOpenChild — child dropdown creation and subscription wiring
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — oozOpenChild", () => {

   // 🔁 Regression-sensitive: sub-menus rely entirely on this path; if dropdownService.open
   // is not called with the correct options or the instance is not wired, sub-menus are dead.
   it("should open a child dropdown with contextmenu options when action has childAction", async () => {
      const { comp } = await renderComp();
      const e = makeMockMouseEvent();
      const childActionFn = vi.fn().mockReturnValue([]);
      comp.oozOpenChild(e, { childAction: childActionFn });
      expect(e.preventDefault).toHaveBeenCalled();
      expect(e.stopPropagation).toHaveBeenCalled();
      expect(DROPDOWN_SERVICE_MOCK.open).toHaveBeenCalledWith(
         ActionsContextmenuComponent,
         expect.objectContaining({ contextmenu: true, closeOnWindowResize: true })
      );
      expect(comp.dropdownRef).toBe(mockDropdownRef as any);
      expect(comp.instance).toBe(mockDropdownRef.componentInstance);
   });

   it("should not open a dropdown when action has no childAction", async () => {
      const { comp } = await renderComp();
      const e = makeMockMouseEvent();
      comp.oozOpenChild(e, {});
      // Gate: oozOpenChild always calls preventDefault() before the childAction guard,
      // so this proves the method ran to the decision point before the negative assertion.
      expect(e.preventDefault).toHaveBeenCalled();
      expect(DROPDOWN_SERVICE_MOCK.open).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: the childSubscription wires child's onClose back to closeAll;
   // without it, closing the child menu leaves this component's state inconsistent.
   it("should wire childSubscription so child onClose triggers closeAll", async () => {
      const { comp } = await renderComp();
      comp.oozOpenChild(makeMockMouseEvent(), { childAction: vi.fn().mockReturnValue([]) });
      const closeAllSpy = vi.spyOn(comp, "closeAll");
      try {
         mockChildOnClose.next();
         expect(closeAllSpy).toHaveBeenCalledTimes(1);
      } finally {
         closeAllSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 6: isFocused — boolean getter, true and false paths
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — isFocused", () => {

   it("should return true when both group and action indices match the focused state", async () => {
      const { comp } = await renderComp();
      comp.focused = { group: 2, action: 3 };
      expect(comp.isFocused(2, 3)).toBe(true);
   });

   it("should return false when the action index does not match", async () => {
      const { comp } = await renderComp();
      comp.focused = { group: 2, action: 3 };
      expect(comp.isFocused(2, 0)).toBe(false);
   });

   it("should return false when the group index does not match", async () => {
      const { comp } = await renderComp();
      comp.focused = { group: 2, action: 3 };
      expect(comp.isFocused(1, 3)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: closeSelf
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — closeSelf", () => {
   it("should emit onClose", async () => {
      const { comp } = await renderComp();
      const closed: void[] = [];
      comp.onClose.subscribe(v => closed.push(v));
      comp.closeSelf();
      expect(closed).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 8: getElementAbsoluteTop / getElementAbsoluteLeft
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — element position helpers", () => {

   it("getElementAbsoluteTop: sums offsetTop values up the offsetParent chain", async () => {
      const { comp } = await renderComp();
      const elem = {
         nativeElement: {
            offsetTop: 10,
            offsetParent: { offsetTop: 20, offsetParent: null },
         },
      } as any;
      expect(comp.getElementAbsoluteTop(elem)).toBe(30);
   });

   it("getElementAbsoluteLeft: sums offsetLeft up the chain then adds the leaf offsetWidth", async () => {
      const { comp } = await renderComp();
      // Implementation: captures width = nativeElement.offsetWidth before the loop,
      // sums offsetLeft in loop, then adds width after the loop.
      const elem = {
         nativeElement: {
            offsetLeft: 10,
            offsetWidth: 50,
            offsetParent: { offsetLeft: 5, offsetParent: null },
         },
      } as any;
      expect(comp.getElementAbsoluteLeft(elem)).toBe(65); // 10 + 5 + 50
   });
});

// ---------------------------------------------------------------------------
// Group 9: focused setter
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — focused setter", () => {
   it("should update internal focusedGroup and focusedAction so that isFocused reflects the new values", async () => {
      const { comp } = await renderComp();
      comp.focused = { group: 1, action: 2 };
      expect(comp.isFocused(1, 2)).toBe(true);
      expect(comp.isFocused(0, 0)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10: ngOnDestroy — childSubscription cleanup
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — ngOnDestroy", () => {

   // 🔁 Regression-sensitive: if childSubscription is not unsubscribed, the closeAll
   // callback fires after the parent component is destroyed, mutating stale state.
   it("should unsubscribe childSubscription and set it to null when component is destroyed", async () => {
      const { comp, fixture } = await renderComp();
      comp.oozOpenChild(makeMockMouseEvent(), { childAction: vi.fn().mockReturnValue([]) });
      // Bypass: childSubscription is private with no public getter; the post-destroy null check
      // requires direct access to verify that ngOnDestroy cleans up the subscription.
      const sub = (comp as any).childSubscription;
      const unsubscribeSpy = vi.spyOn(sub, "unsubscribe");
      try {
         fixture.destroy();
         expect(unsubscribeSpy).toHaveBeenCalled();
         expect((comp as any).childSubscription).toBeNull();
      } finally {
         unsubscribeSpy.mockRestore();
      }
   });

   it("should not throw when destroyed without any childSubscription", async () => {
      const { fixture } = await renderComp();
      expect(() => fixture.destroy()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 11: template rendering — disable-link CSS class and group divider
// ---------------------------------------------------------------------------

describe("ActionsContextmenuComponent — template rendering", () => {

   // 🔁 Regression-sensitive: template applies disable-link via [class.disable-link]="!action.enabled()";
   // if the binding is removed, disabled menu items lose their visual affordance silently.
   it("should apply disable-link class to a disabled action", async () => {
      const { comp, fixture } = await renderComp();
      comp.actions = [new AssemblyActionGroup([makeAction({ enabled: () => false })])];
      comp.changeDetectorRef.markForCheck(); // OnPush: direct property set doesn't auto-mark dirty
      fixture.detectChanges();
      expect(fixture.debugElement.queryAll(By.css("a.disable-link")).length).toBe(1);
   });

   // 🔁 Regression-sensitive: divider is rendered via @if (!_first) between groups;
   // if the condition is removed, all groups merge into a flat unstructured list.
   it("should render one divider between two action groups", async () => {
      const { comp, fixture } = await renderComp();
      comp.actions = [
         new AssemblyActionGroup([makeAction()]),
         new AssemblyActionGroup([makeAction()]),
      ];
      comp.changeDetectorRef.markForCheck(); // OnPush: direct property set doesn't auto-mark dirty
      fixture.detectChanges();
      expect(fixture.debugElement.queryAll(By.css(".dropdown-divider")).length).toBe(1);
   });
});
