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
 * WizardNewObject — single pass
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — onMousemove(): zIndex 9999/999 toggle based on inner-area position;
 *                        first call caches boundingRect
 *   Group 2  [baseline] — onClickCell(): emits toComponentWizard with model bounds Point
 *   Group 3  [baseline] — onMouseEnter() / onMouseLeave(): debounce service calls
 *   Group 4  [baseline] — leaveMenu(): closes dropdown; no-op when dropdownRef is null
 *   Group 5  [baseline] — insertObject() / insertImage() / insertText(): doInsertObject emissions
 *   Group 6  [baseline] — ngOnChanges(): clears boundingRect when model input changes
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   FixedDropdownDirective internals — tested via leaveMenu() mock in Group 4
 */

import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { render } from "@testing-library/angular";

import { WizardNewObject } from "./wizard-new-object.component";
import { WizardNewObjectModel } from "./wizard-new-object-model";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { AssemblyType } from "../../../composer/gui/vs/assembly-type";

const DEBOUNCE_MOCK = { cancel: vi.fn(), debounce: vi.fn() };

function makeModel(overrides: Partial<WizardNewObjectModel> = {}): WizardNewObjectModel {
   return {
      visible: true,
      bounds: { x: 10, y: 20, width: 100, height: 50 } as any,
      ...overrides,
   };
}

async function renderComponent(model?: WizardNewObjectModel) {
   const result = await render(WizardNewObject, {
      inputs: { model: model ?? makeModel(), componentWizardEnable: true },
      providers: [
         { provide: ViewsheetClientService, useValue: { sendEvent: vi.fn() } },
         { provide: DebounceService, useValue: DEBOUNCE_MOCK },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance, fixture: result.fixture };
}

beforeEach(() => {
   DEBOUNCE_MOCK.cancel.mockClear();
   DEBOUNCE_MOCK.debounce.mockClear();
});

// ---------------------------------------------------------------------------
// Group 1 — onMousemove() zIndex toggle [Risk 2]
// ---------------------------------------------------------------------------

describe("WizardNewObject — onMousemove() zIndex management", () => {
   // 🔁 Regression-sensitive: zIndex must rise to 9999 when the cursor is inside
   // the inner area so the new-object overlay stays above wizard objects. If the
   // zIndex stays at 1, the overlay is obscured by adjacent wizard objects and
   // the user cannot click new-object actions.
   it("should set zIndex to 9999 when mouse is inside the inner area", async () => {
      const { comp } = await renderComponent();
      const mockTarget = {
         getBoundingClientRect: vi.fn().mockReturnValue({ left: 0, right: 200, top: 0, bottom: 200 }),
      };

      comp.onMousemove({ target: mockTarget, pageX: 100, pageY: 100 } as any);

      expect(comp.zIndex).toBe(9999);
   });

   it("should reset zIndex to 999 when mouse leaves inner area after entering", async () => {
      const { comp } = await renderComponent();
      const mockTarget = {
         getBoundingClientRect: vi.fn().mockReturnValue({ left: 0, right: 200, top: 0, bottom: 200 }),
      };
      comp.onMousemove({ target: mockTarget, pageX: 100, pageY: 100 } as any); // enter

      comp.onMousemove({ target: mockTarget, pageX: 2, pageY: 2 } as any); // leave (< left+6)

      expect(comp.zIndex).toBe(999);
   });

   it("should cache boundingRect on the first call and reuse it on subsequent calls", async () => {
      const { comp } = await renderComponent();
      const getBoundingClientRectSpy = vi.fn().mockReturnValue({ left: 0, right: 200, top: 0, bottom: 200 });
      const mockTarget = { getBoundingClientRect: getBoundingClientRectSpy };

      comp.onMousemove({ target: mockTarget, pageX: 50, pageY: 50 } as any);
      comp.onMousemove({ target: mockTarget, pageX: 60, pageY: 60 } as any);

      // getBoundingClientRect should only be called once (cached after first call)
      expect(getBoundingClientRectSpy).toHaveBeenCalledOnce();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — onClickCell() [baseline]
// ---------------------------------------------------------------------------

describe("WizardNewObject — onClickCell()", () => {
   it("should emit toComponentWizard with x/y from model.bounds", async () => {
      const model = makeModel({ bounds: { x: 30, y: 40, width: 100, height: 50 } as any });
      const { comp } = await renderComponent(model);
      const emitted: any[] = [];
      comp.toComponentWizard.subscribe((p: any) => emitted.push(p));

      comp.onClickCell();

      expect(emitted).toHaveLength(1);
      expect(emitted[0].x).toBe(30);
      expect(emitted[0].y).toBe(40);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — onMouseEnter() / onMouseLeave() [baseline]
// ---------------------------------------------------------------------------

describe("WizardNewObject — onMouseEnter() / onMouseLeave()", () => {
   it("onMouseEnter() should cancel the debounce for 'wizard new object'", async () => {
      const { comp } = await renderComponent();

      comp.onMouseEnter();

      expect(DEBOUNCE_MOCK.cancel).toHaveBeenCalledWith("wizard new object");
   });

   it("onMouseLeave() should schedule a debounce for 'wizard new object' with 200ms", async () => {
      const { comp } = await renderComponent();

      comp.onMouseLeave();

      expect(DEBOUNCE_MOCK.debounce).toHaveBeenCalledWith(
         "wizard new object",
         expect.any(Function),
         200,
         [],
      );
   });
});

// ---------------------------------------------------------------------------
// Group 4 — leaveMenu() [baseline]
// ---------------------------------------------------------------------------

describe("WizardNewObject — leaveMenu()", () => {
   it("should close the dropdown when dropdownRef is set", async () => {
      const { comp } = await renderComponent();
      const closeSpy = vi.fn();
      (comp as any)["dropdownRef"] = { close: closeSpy };

      comp.leaveMenu();

      expect(closeSpy).toHaveBeenCalledOnce();
   });

   it("should be a no-op when dropdownRef is null", async () => {
      const { comp } = await renderComponent();
      (comp as any)["dropdownRef"] = null;

      expect(() => comp.leaveMenu()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — insertObject() / insertImage() / insertText() [baseline]
// ---------------------------------------------------------------------------

describe("WizardNewObject — insertObject() / insertImage() / insertText()", () => {
   // 🔁 Regression-sensitive: the emitted object must include both type and point.
   // If type is wrong the wizard inserts the wrong assembly; if point is wrong the
   // new object appears at the wrong grid position.
   it("insertObject() should emit doInsertObject with the given type and model.bounds point", async () => {
      const model = makeModel({ bounds: { x: 5, y: 15, width: 100, height: 50 } as any });
      const { comp } = await renderComponent(model);
      const emitted: any[] = [];
      comp.doInsertObject.subscribe((v: any) => emitted.push(v));

      comp.insertObject(AssemblyType.IMAGE_ASSET);

      expect(emitted).toHaveLength(1);
      expect(emitted[0].type).toBe(AssemblyType.IMAGE_ASSET);
      expect(emitted[0].point.x).toBe(5);
      expect(emitted[0].point.y).toBe(15);
   });

   it("insertImage() should delegate to insertObject with IMAGE_ASSET type", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.doInsertObject.subscribe((v: any) => emitted.push(v));

      comp.insertImage();

      expect(emitted[0].type).toBe(AssemblyType.IMAGE_ASSET);
   });

   it("insertText() should delegate to insertObject with TEXT_ASSET type", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.doInsertObject.subscribe((v: any) => emitted.push(v));

      comp.insertText();

      expect(emitted[0].type).toBe(AssemblyType.TEXT_ASSET);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — ngOnChanges() [baseline]
// ---------------------------------------------------------------------------

describe("WizardNewObject — ngOnChanges()", () => {
   it("should clear the cached boundingRect when the model input changes", async () => {
      const { comp } = await renderComponent();
      // Populate the cache with a first mousemove call
      const mockTarget = {
         getBoundingClientRect: vi.fn().mockReturnValue({ left: 0, right: 200, top: 0, bottom: 200 }),
      };
      comp.onMousemove({ target: mockTarget, pageX: 50, pageY: 50 } as any);

      const newModel = makeModel({ bounds: { x: 99, y: 99, width: 10, height: 10 } as any });
      comp.ngOnChanges({ model: new SimpleChange(comp.model, newModel, false) });
      comp.model = newModel;

      // After model change, boundingRect is cleared — next mousemove re-queries getBoundingClientRect
      const newTarget = {
         getBoundingClientRect: vi.fn().mockReturnValue({ left: 0, right: 200, top: 0, bottom: 200 }),
      };
      comp.onMousemove({ target: newTarget, pageX: 50, pageY: 50 } as any);
      expect(newTarget.getBoundingClientRect).toHaveBeenCalledOnce();
   });
});
