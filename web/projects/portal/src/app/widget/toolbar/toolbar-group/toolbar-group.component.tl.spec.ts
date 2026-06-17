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
 * ToolbarGroup — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngAfterViewInit: dropdown.openChange subscription notifies DropdownObserver
 *   Group 2 [Risk 3] — ngOnDestroy: unsubscribes so no DropdownObserver notify fires after destroy;
 *                       also calls onDropdownClosed if dropdown was open at destroy time
 *   Group 3 [Risk 2] — getStyle: switch branches for each secondLevelParent value
 *   Group 4 [Risk 2] — bottomPlacement: placeOnRight → "bottom-right", else "bottom-left"
 *   Group 5 [Risk 2] — getSnapToModel: "Snap to grid" → snapToGrid, else → snapToObjects
 *   Group 6 [Risk 1] — isCheckboxInput: true only when iconClass === "form-check-input"
 *
 * Out of scope:
 *   ngOnInit dropdownId — time-based ID, non-deterministic; display-only side-effect.
 *   click() event.stopPropagation — pure event delegation; covered by e2e.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { ToolbarGroup } from "./toolbar-group.component";
import { DropdownObserver } from "../../services/dropdown-observer.service";
import { ToolbarAction } from "../toolbar-action";

const DROPDOWN_OBSERVER_MOCK = {
   onDropdownOpened: vi.fn(),
   onDropdownClosed: vi.fn(),
};

function makeDropdownMock(isOpen = false) {
   const subject = new Subject<boolean>();
   return {
      openChange: subject.asObservable(),
      isOpen: vi.fn(() => isOpen),
      _subject: subject,
   };
}

async function renderComponent(props: Partial<ToolbarGroup> = {}) {
   const { fixture } = await render(ToolbarGroup, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: DropdownObserver, useValue: DROPDOWN_OBSERVER_MOCK },
      ],
      componentProperties: {
         asMenu: false,
         childGroupToolbar: false,
         placeOnRight: false,
         snapToGrid: false,
         snapToObjects: false,
         ...props,
      },
   });
   return fixture.componentInstance as ToolbarGroup;
}

beforeEach(() => {
   DROPDOWN_OBSERVER_MOCK.onDropdownOpened.mockReset();
   DROPDOWN_OBSERVER_MOCK.onDropdownClosed.mockReset();
});
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngAfterViewInit — dropdown.openChange subscription [Risk 3]
// ---------------------------------------------------------------------------

describe("ToolbarGroup — ngAfterViewInit dropdown openChange", () => {
   // 🔁 Regression-sensitive: DropdownObserver.onDropdownOpened/Closed must fire when
   //    the toolbar dropdown opens/closes; breaking this breaks multi-dropdown close logic.
   it("should call onDropdownOpened when dropdown emits open=true", async () => {
      const comp = await renderComponent();
      const dropdownMock = makeDropdownMock(false);
      comp.dropdown = dropdownMock as any;

      comp.ngAfterViewInit();
      dropdownMock._subject.next(true);

      expect(DROPDOWN_OBSERVER_MOCK.onDropdownOpened).toHaveBeenCalledTimes(1);
   });

   it("should call onDropdownClosed when dropdown emits open=false", async () => {
      const comp = await renderComponent();
      const dropdownMock = makeDropdownMock(false);
      comp.dropdown = dropdownMock as any;

      comp.ngAfterViewInit();
      dropdownMock._subject.next(false);

      expect(DROPDOWN_OBSERVER_MOCK.onDropdownClosed).toHaveBeenCalledTimes(1);
   });

   it("should not call DropdownObserver when there is no dropdown ViewChild", async () => {
      const comp = await renderComponent();
      comp.dropdown = undefined as any;

      comp.ngAfterViewInit();

      expect(DROPDOWN_OBSERVER_MOCK.onDropdownOpened).not.toHaveBeenCalled();
      expect(DROPDOWN_OBSERVER_MOCK.onDropdownClosed).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngOnDestroy — memory leak + open-dropdown cleanup [Risk 3]
// ---------------------------------------------------------------------------

describe("ToolbarGroup — ngOnDestroy memory leak", () => {
   // 🔁 Regression-sensitive: if destroy$ is not completed, the openChange subscription
   //    survives and calls DropdownObserver on a destroyed component.
   it("should not call DropdownObserver after destroy when dropdown emits", async () => {
      const comp = await renderComponent();
      const dropdownMock = makeDropdownMock(false);
      comp.dropdown = dropdownMock as any;
      comp.ngAfterViewInit();

      DROPDOWN_OBSERVER_MOCK.onDropdownOpened.mockReset();
      DROPDOWN_OBSERVER_MOCK.onDropdownClosed.mockReset();
      comp.ngOnDestroy();

      dropdownMock._subject.next(true);

      expect(DROPDOWN_OBSERVER_MOCK.onDropdownOpened).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: if the dropdown is open when the component is destroyed
   //    (e.g., user navigates away), the observer must be notified so its open count stays correct.
   it("should call onDropdownClosed on destroy when the dropdown is currently open", async () => {
      const comp = await renderComponent();
      const dropdownMock = makeDropdownMock(true); // isOpen() === true
      comp.dropdown = dropdownMock as any;

      comp.ngOnDestroy();

      expect(DROPDOWN_OBSERVER_MOCK.onDropdownClosed).toHaveBeenCalledTimes(1);
   });

   it("should NOT call onDropdownClosed on destroy when the dropdown is closed", async () => {
      const comp = await renderComponent();
      const dropdownMock = makeDropdownMock(false); // isOpen() === false
      comp.dropdown = dropdownMock as any;

      comp.ngOnDestroy();

      expect(DROPDOWN_OBSERVER_MOCK.onDropdownClosed).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: getStyle [Risk 2]
// ---------------------------------------------------------------------------

describe("ToolbarGroup — getStyle (non-childGroupToolbar)", () => {
   // 🔁 Regression-sensitive: getStyle drives CSS class on toolbar buttons; wrong class
   //    breaks button layout in the composer toolbar.
   it("should return toolbar button classes for secondLevelParent=0", async () => {
      const comp = await renderComponent({ childGroupToolbar: false });
      expect(comp.getStyle(0)).toBe("btn composer-btn toolbar-btn pb-1 ps-1 pe-1");
   });

   it("should return bottomPlacement class for secondLevelParent=1", async () => {
      const comp = await renderComponent({ childGroupToolbar: false, placeOnRight: false });
      expect(comp.getStyle(1)).toBe("bottom-left");
   });

   it("should return 'dropdown-item' for secondLevelParent=2", async () => {
      const comp = await renderComponent({ childGroupToolbar: false });
      expect(comp.getStyle(2)).toBe("dropdown-item");
   });

   it("should return 'item-label' for secondLevelParent=3", async () => {
      const comp = await renderComponent({ childGroupToolbar: false });
      expect(comp.getStyle(3)).toBe("item-label");
   });
});

describe("ToolbarGroup — getStyle (childGroupToolbar=true)", () => {
   it("should return 'dropdown-item' for secondLevelParent=0", async () => {
      const comp = await renderComponent({ childGroupToolbar: true });
      expect(comp.getStyle(0)).toBe("dropdown-item");
   });

   it("should return 'left' for secondLevelParent=1", async () => {
      const comp = await renderComponent({ childGroupToolbar: true });
      expect(comp.getStyle(1)).toBe("left");
   });

   it("should return 'second-dropdown-item' for secondLevelParent=2", async () => {
      const comp = await renderComponent({ childGroupToolbar: true });
      expect(comp.getStyle(2)).toBe("second-dropdown-item");
   });

   it("should return 'second-item-label' for secondLevelParent=3", async () => {
      const comp = await renderComponent({ childGroupToolbar: true });
      expect(comp.getStyle(3)).toBe("second-item-label");
   });
});

// ---------------------------------------------------------------------------
// Group 4: bottomPlacement getter [Risk 2]
// ---------------------------------------------------------------------------

describe("ToolbarGroup — bottomPlacement", () => {
   // 🔁 Regression-sensitive: controls dropdown placement direction; wrong value misaligns menus.
   it("should return 'bottom-right' when placeOnRight=true", async () => {
      const comp = await renderComponent({ placeOnRight: true });
      expect(comp.bottomPlacement).toBe("bottom-right");
   });

   it("should return 'bottom-left' when placeOnRight=false", async () => {
      const comp = await renderComponent({ placeOnRight: false });
      expect(comp.bottomPlacement).toBe("bottom-left");
   });
});

// ---------------------------------------------------------------------------
// Group 5: getSnapToModel [Risk 2]
// ---------------------------------------------------------------------------

describe("ToolbarGroup — getSnapToModel", () => {
   // 🔁 Regression-sensitive: wrong binding causes snap state to appear out of sync with actual grid.
   it("should return snapToGrid when action.label is 'Snap to grid'", async () => {
      const comp = await renderComponent({ snapToGrid: true, snapToObjects: false });
      const action = { label: "Snap to grid" } as ToolbarAction;
      expect(comp.getSnapToModel(action)).toBe(true);
   });

   it("should return snapToObjects when action.label is anything else", async () => {
      const comp = await renderComponent({ snapToGrid: false, snapToObjects: true });
      const action = { label: "Snap to objects" } as ToolbarAction;
      expect(comp.getSnapToModel(action)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6: isCheckboxInput [Risk 1]
// ---------------------------------------------------------------------------

describe("ToolbarGroup — isCheckboxInput", () => {
   it("should return true when action.iconClass is 'form-check-input'", async () => {
      const comp = await renderComponent();
      const action = { iconClass: "form-check-input" } as ToolbarAction;
      expect(comp.isCheckboxInput(action)).toBe(true);
   });

   it("should return false when action.iconClass is something else", async () => {
      const comp = await renderComponent();
      const action = { iconClass: "delete-icon" } as ToolbarAction;
      expect(comp.isCheckboxInput(action)).toBe(false);
   });
});
