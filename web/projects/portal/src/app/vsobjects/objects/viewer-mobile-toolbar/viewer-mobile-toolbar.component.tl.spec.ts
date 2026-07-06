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
 * ViewerMobileToolbarComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — hasMenuAction: correct boolean logic; wrong result hides the
 *                       sandwich menu button or shows it when it should not exist
 *   Group 2 [Risk 2] — showMobileSandwichDropdown: open/close toggle; wrong branch leaves
 *                       the overlay permanently open or permanently closed
 *   Group 3 [Risk 1] — allowedActionsNum: defaultButtons increments by 1 when hasMenuAction
 *                       is true; arithmetic error shows too many or too few toolbar actions
 *
 * Confirmed bugs: none
 *
 * Out of scope:
 *   actions setter — trivially assigns _actions; no logic to contract-test
 *   null-actions guard — the template calls showingActions getter unconditionally regardless
 *     of whether actions is null; the component crashes before hasMenuAction is reached when
 *     actions is undefined; this is a template bug (not a test isolation issue)
 *   showingActions / moreActions getters — delegate entirely to static ToolbarActionsHandler
 *     methods; no logic owned by this component; require real AbstractVSActions instances or
 *     static method spies with full AssemblyAction trees
 */

import { ElementRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { ViewerMobileToolbarComponent } from "./viewer-mobile-toolbar.component";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { ToolbarActionsHandler } from "../../toolbar-actions-handler";

// Always-valid minimal actions mock — the template calls showingActions getter which accesses
// actions.toolbarActions unconditionally; providing empty arrays prevents the template crash.
function makeActions(menuGroups: AssemblyActionGroup[] = []): Partial<AbstractVSActions<any>> {
   return {
      menuActions: menuGroups,
      toolbarActions: [],
      requiresMenuSeparator: () => false,
   };
}

const dropdownRefMock = { close: vi.fn() };
const DROPDOWN_SERVICE_MOCK = {
   open: vi.fn().mockReturnValue(dropdownRefMock),
};

beforeEach(() => {
   DROPDOWN_SERVICE_MOCK.open.mockClear();
   dropdownRefMock.close.mockClear();
});

async function renderComp(actions: Partial<AbstractVSActions<any>> = makeActions()) {
   const { fixture } = await render(ViewerMobileToolbarComponent, {
      providers: [{ provide: FixedDropdownService, useValue: DROPDOWN_SERVICE_MOCK }],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: { actions },
   });
   return { comp: fixture.componentInstance, fixture };
}

// ---------------------------------------------------------------------------
// Group 1: hasMenuAction — visible logic
// ---------------------------------------------------------------------------

describe("ViewerMobileToolbarComponent — hasMenuAction", () => {

   // 🔁 Regression-sensitive: hasMenuAction drives sandwich-button visibility; wrong result
   // hides the menu button for users who have visible menu actions, or shows a non-functional
   // button when all actions are invisible.
   it("should return false when menuActions array is empty", async () => {
      const { comp } = await renderComp(makeActions([]));
      expect(comp.hasMenuAction).toBe(false);
   });

   it("should return false when all menu action groups are invisible", async () => {
      const hiddenGroup = new AssemblyActionGroup([{ visible: () => false } as any]);
      const { comp } = await renderComp(makeActions([hiddenGroup]));
      expect(comp.hasMenuAction).toBe(false);
   });

   it("should return true when at least one menu action group is visible", async () => {
      const visibleGroup = new AssemblyActionGroup([{ visible: () => true } as any]);
      const { comp } = await renderComp(makeActions([visibleGroup]));
      expect(comp.hasMenuAction).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: showMobileSandwichDropdown — open / close toggle
// ---------------------------------------------------------------------------

describe("ViewerMobileToolbarComponent — showMobileSandwichDropdown", () => {

   // 🔁 Regression-sensitive: the open branch reads mobileSandwichElement; if it were
   // undefined (e.g. ViewChild not resolved), the call throws. We set it explicitly to
   // verify the open path works end-to-end without a real DOM ViewChild resolution.
   it("open branch: should call dropdownService.open and set sandwichMenuOpen=true when closed", async () => {
      const { comp } = await renderComp();
      comp.mobileSandwichElement = {
         nativeElement: {
            getBoundingClientRect: () => ({ left: 10, top: 20 }),
            offsetHeight: 50,
         },
      } as ElementRef;

      comp.showMobileSandwichDropdown({});

      expect(DROPDOWN_SERVICE_MOCK.open).toHaveBeenCalledTimes(1);
      expect(DROPDOWN_SERVICE_MOCK.open).toHaveBeenCalledWith(
         {},
         expect.objectContaining({
            position: { x: 10, y: 70 },  // left:10, top:20 + offsetHeight:50
            contextmenu: true,
         })
      );
      expect(comp.sandwichMenuOpen).toBe(true);
   });

   it("close branch: should call mobileSandwichRef.close and set sandwichMenuOpen=false when open", async () => {
      const { comp } = await renderComp();
      comp.sandwichMenuOpen = true;
      comp.mobileSandwichRef = dropdownRefMock as any;

      comp.showMobileSandwichDropdown({});

      expect(dropdownRefMock.close).toHaveBeenCalledTimes(1);
      expect(comp.sandwichMenuOpen).toBe(false);
      expect(DROPDOWN_SERVICE_MOCK.open).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: allowedActionsNum — defaultButtons calculation
// ---------------------------------------------------------------------------

describe("ViewerMobileToolbarComponent — allowedActionsNum", () => {

   // 🔁 Regression-sensitive: defaultButtons is 1 (close button) with no menu action,
   // 2 when hasMenuAction is true. The sandwich menu slot must be reserved or released
   // correctly; wrong value hides toolbar actions or leaves gaps.
   it("should reserve 1 slot (close button only) when hasMenuAction is false", async () => {
      vi.stubGlobal("innerWidth", 460);
      try {
         const { comp } = await renderComp(makeActions([]));
         // hasMenuAction is false (no visible menu actions)
         // Math.floor(460 / 46) - 1 = 10 - 1 = 9
         expect(comp.allowedActionsNum()).toBe(
            Math.floor(460 / ToolbarActionsHandler.MOBILE_BUTTON_WIDTH) - 1
         );
      } finally {
         vi.unstubAllGlobals();
      }
   });

   it("should reserve 2 slots (close + sandwich) when hasMenuAction is true", async () => {
      vi.stubGlobal("innerWidth", 460);
      try {
         const visibleGroup = new AssemblyActionGroup([{ visible: () => true } as any]);
         const { comp } = await renderComp(makeActions([visibleGroup]));
         // hasMenuAction is true → defaultButtons = 2
         // Math.floor(460 / 46) - 2 = 10 - 2 = 8
         expect(comp.allowedActionsNum()).toBe(
            Math.floor(460 / ToolbarActionsHandler.MOBILE_BUTTON_WIDTH) - 2
         );
      } finally {
         vi.unstubAllGlobals();
      }
   });
});
