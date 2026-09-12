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
 * MiniMenu - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - openMenu: dropdown wiring, close emission, and key-nav bootstrap
 *   Group 2 [Risk 2] - navigate UP/DOWN: skip invisible and disabled actions
 *   Group 3 [Risk 2] - navigate SPACE: invoke focused action, close dropdown, and cleanup
 *   Group 4 [Risk 1] - ngOnDestroy: keyboard subscription cleanup
 *
 * Confirmed bugs: none
 *
 * Out of scope:
 *   Template CSS class toggles; the component logic is independent of them
 *
 * Mocking strategy:
 *   - direct class instantiation with a mocked FixedDropdownService
 *   - Subject-backed dropdown close and key-navigation streams
 */

import { Subject } from "rxjs";
import { render } from "@testing-library/angular";

import { AssemblyAction } from "../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { NavigationKeys } from "../navigation-keys";
import { MiniMenu } from "./mini-menu.component";

afterEach(() => vi.restoreAllMocks());

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

function createComponent() {
   const closeEvent = new Subject<void>();
   const dropdown = {
      componentInstance: {
         sourceEvent: null as MouseEvent,
         actions: null as AssemblyActionGroup[],
         focused: null as { group: number, action: number },
      },
      closeEvent,
      close: vi.fn(),
   };
   const dropdownService = {
      open: vi.fn().mockReturnValue(dropdown),
   };
   const comp = new MiniMenu(dropdownService as any);
   return { comp, dropdown, dropdownService, closeEvent };
}

function makeMouseEvent(): MouseEvent {
   return { clientX: 10, clientY: 20 } as MouseEvent;
}

describe("MiniMenu - Group 1: openMenu", () => {
   it("should open the dropdown, wire source data, and emit onClose when the dropdown closes", () => {
      const { comp, dropdown, dropdownService, closeEvent } = createComponent();
      const closed = vi.fn();
      comp.actions = [new AssemblyActionGroup([makeAction()])];
      comp.onClose.subscribe(closed);

      comp.openMenu(makeMouseEvent());
      closeEvent.next();

      expect(dropdownService.open).toHaveBeenCalledWith(
         expect.any(Function),
         { position: { x: 11, y: 20 }, contextmenu: true }
      );
      expect(dropdown.componentInstance.sourceEvent).toEqual(makeMouseEvent());
      expect(dropdown.componentInstance.actions).toBe(comp.actions);
      expect(closed).toHaveBeenCalledWith(true);
   });

   it("should focus the first visible enabled action and react to keyboard navigation events", () => {
      const { comp, dropdown } = createComponent();
      const keyNavigation = new Subject<any>();
      comp.actions = [
         new AssemblyActionGroup([
            makeAction({ visible: () => false }),
            makeAction({ enabled: () => false }),
            makeAction(),
         ]),
      ];
      comp.keyNav = true;
      comp.keyNavigation = keyNavigation;

      comp.openMenu(makeMouseEvent());
      expect(dropdown.componentInstance.focused).toEqual({ group: 0, action: 2 });

      keyNavigation.next({ focused: null, key: NavigationKeys.UP });
      expect(dropdown.componentInstance.focused).toEqual({ group: 0, action: 2 });
   });
});

describe("MiniMenu - Group 2: navigate UP/DOWN", () => {
   it("should move focus to the next visible enabled action across groups", () => {
      const { comp, dropdown } = createComponent();
      comp.actions = [
         new AssemblyActionGroup([makeAction({ visible: () => false })]),
         new AssemblyActionGroup([
            makeAction({ enabled: () => false }),
            makeAction(),
         ]),
      ];

      // Bypass: navigate() writes focus via the private dropdown ref, so inject a mock ref
      // directly to observe the focused indices without a rendered overlay component.
      (comp as any).dropdown = dropdown;
      comp.navigate(NavigationKeys.DOWN);

      expect(dropdown.componentInstance.focused).toEqual({ group: 1, action: 1 });
   });

   it("should move focus to the previous visible enabled action", () => {
      const { comp, dropdown } = createComponent();
      comp.actions = [
         new AssemblyActionGroup([makeAction(), makeAction({ enabled: () => false })]),
         new AssemblyActionGroup([makeAction()]),
      ];

      // Bypass: this branch also depends on the private dropdown ref for updateIndexes().
      (comp as any).dropdown = dropdown;
      comp.navigate(NavigationKeys.DOWN);
      comp.navigate(NavigationKeys.DOWN);
      comp.navigate(NavigationKeys.UP);

      expect(dropdown.componentInstance.focused).toEqual({ group: 0, action: 0 });
   });
});

describe("MiniMenu - Group 3: navigate SPACE", () => {
   it("should invoke the focused action, close the dropdown, emit onClose, and unsubscribe", () => {
      const { comp, dropdown } = createComponent();
      const actionFn = vi.fn();
      const closed = vi.fn();
      const keyNavigation = new Subject<any>();
      comp.actions = [new AssemblyActionGroup([makeAction({ action: actionFn })])];
      comp.keyNav = true;
      comp.keyNavigation = keyNavigation;
      comp.onClose.subscribe(closed);

      comp.openMenu(makeMouseEvent());
      comp.navigate(NavigationKeys.SPACE);

      expect(actionFn).toHaveBeenCalledWith(dropdown.componentInstance.sourceEvent);
      expect(dropdown.close).toHaveBeenCalledTimes(1);
      expect(closed).toHaveBeenCalledWith(true);
      // Bypass: dropdown/subscription are private; verify the SPACE branch cleared the
      // internal dropdown ref and unsubscribed the keyboard listener.
      expect((comp as any).dropdown).toBeNull();
      expect((comp as any).subscription.closed).toBe(true);
   });
});

describe("MiniMenu - Group 4: ngOnDestroy", () => {
   it("should unsubscribe from keyNavigation when destroyed", async () => {
      const dropdown = {
         componentInstance: { sourceEvent: null, actions: null, focused: null },
         closeEvent: new Subject<void>(),
         close: vi.fn(),
      };
      const { fixture } = await render(MiniMenu, {
         componentInputs: {
            actions: [new AssemblyActionGroup([makeAction()])],
            keyNavigation: new Subject<any>(),
         },
         providers: [{
            provide: FixedDropdownService,
            useValue: { open: vi.fn().mockReturnValue(dropdown) },
         }],
      });
      const comp = fixture.componentInstance;
      const keyNavigation = new Subject<any>();
      comp.keyNavigation = keyNavigation;

      comp.openMenu(makeMouseEvent());
      // Bypass: subscription is private with no public getter; inspect it to verify
      // fixture.destroy() triggers the component cleanup path.
      const subscription = (comp as any).subscription;
      const unsubscribeSpy = vi.spyOn(subscription, "unsubscribe");

      try {
         fixture.destroy();
         expect(unsubscribeSpy).toHaveBeenCalledTimes(1);
      }
      finally {
         unsubscribeSpy.mockRestore();
      }
   });
});
