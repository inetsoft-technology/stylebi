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
 * GroupingDialog — Pass 2: Risk / Async
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — updateOnlyFor: patches onlyFor form control; loads attributes via HTTP;
 *                        resets attributes when "(all)" is selected
 *   Group 2  [Risk 2] — deleteDisabled: returns true when selectedConditionIndex undefined;
 *                        false when defined
 *   Group 3  [Risk 3] — deleteCondition: removes condition at index; resets selectedConditionIndex;
 *                        destructive: off-by-one risk when splicing
 *   Group 4  [Risk 3] — moveConditionUp: swaps items at index and index-1; decrements index;
 *                        race condition when index is 0 (should be blocked by upDisabled)
 *   Group 5  [Risk 3] — moveConditionDown: swaps items at index and index+1; increments index;
 *                        race condition when index is last (should be blocked by downDisabled)
 *   Group 6  [Risk 2] — cancel: emits onCancel with "cancel" string
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — deleteCondition splices from the mutable value array directly; if something
 *     else holds a reference to the array, it will see the mutation without a valueChanges event.
 *   Suspicion B — moveConditionUp/Down mutate the array in-place without emitting valueChanges;
 *     the UI list won't refresh unless change detection is triggered externally.
 */

import { of } from "rxjs";
import { HttpResponse } from "@angular/common/http";
import {
   makeComponent,
   makeInitializedComponent,
   makeGroupingDialogModel,
   makeConditionExpr,
   makeAssetEntry,
   makeModelServiceMock,
} from "./grouping-dialog.component.test-helpers";

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: updateOnlyFor [Risk 3]
// ---------------------------------------------------------------------------

describe("GroupingDialog — updateOnlyFor", () => {

   // 🔁 Regression-sensitive: updateOnlyFor must patch onlyFor and then fetch attributes
   //    via HTTP; if the HTTP subscription is missing, attribute index will never be set.
   it("should patch onlyFor form control with result.data", () => {
      const entry = makeAssetEntry({ path: "DS/Query1" });
      const modelService = {
         ...makeModelServiceMock(),
         sendModel: vi.fn().mockReturnValue(of(new HttpResponse({ body: [{ name: "col1" }] }))),
      };
      const { comp } = makeInitializedComponent({}, { modelService });
      const node = { label: "Query1", leaf: true, data: entry, children: [] } as any;

      comp.updateOnlyFor(node);

      expect(comp.form.get("onlyFor").value).toBe(entry);
   });

   it("should load attributes from HTTP when result.data is not '(all)'", () => {
      const entry = makeAssetEntry();
      const mockAttributes = [{ name: "col1" }, { name: "col2" }];
      const modelService = {
         ...makeModelServiceMock(),
         sendModel: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockAttributes }))),
      };
      const { comp } = makeInitializedComponent({}, { modelService });

      // Clear mock counts from initForm
      modelService.sendModel.mockClear();

      const node = { label: "Q1", leaf: true, data: entry, children: [] } as any;
      comp.form.get("onlyFor").enable();
      comp.updateOnlyFor(node);

      expect(modelService.sendModel).toHaveBeenCalled();
      expect(comp.attributes).toEqual(mockAttributes);
   });

   it("should set attributeIndex to 0 after loading attributes", () => {
      const entry = makeAssetEntry();
      const mockAttributes = [{ name: "col1" }, { name: "col2" }];
      const modelService = {
         ...makeModelServiceMock(),
         sendModel: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockAttributes }))),
      };
      const { comp } = makeInitializedComponent({}, { modelService });
      const node = { label: "Q1", leaf: true, data: entry, children: [] } as any;
      comp.form.get("attribute").enable();
      comp.form.get("attributeIndex").enable();

      comp.updateOnlyFor(node);

      expect(comp.form.get("attributeIndex").value).toBe(0);
   });

   // 🔁 Regression-sensitive: selecting "(all)" must clear attributes and not issue HTTP
   it("should set attributes to null and NOT call HTTP when result.data is '(all)'", () => {
      const modelService = {
         ...makeModelServiceMock(),
         sendModel: vi.fn().mockReturnValue(of(new HttpResponse({ body: [] }))),
      };
      const { comp } = makeInitializedComponent({}, { modelService });

      // Clear mock from initForm
      modelService.sendModel.mockClear();

      const allNode = { label: "(all)", leaf: true, data: "(all)", children: [] } as any;
      comp.updateOnlyFor(allNode);

      expect(comp.attributes).toBeNull();
      expect(modelService.sendModel).not.toHaveBeenCalled();
   });

   it("should update onlyFor form control to '(all)' when selecting all", () => {
      const modelService = {
         ...makeModelServiceMock(),
         sendModel: vi.fn().mockReturnValue(of(new HttpResponse({ body: [] }))),
      };
      const { comp } = makeInitializedComponent({}, { modelService });
      const allNode = { label: "(all)", leaf: true, data: "(all)", children: [] } as any;

      comp.updateOnlyFor(allNode);

      expect(comp.form.get("onlyFor").value).toBe("(all)");
   });
});

// ---------------------------------------------------------------------------
// Group 2: deleteDisabled [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — deleteDisabled", () => {

   it("should return true when selectedConditionIndex is undefined", () => {
      const { comp } = makeInitializedComponent();
      comp.selectedConditionIndex = undefined;
      expect(comp.deleteDisabled).toBe(true);
   });

   it("should return false when selectedConditionIndex is 0", () => {
      const exprs = [makeConditionExpr("C1")];
      const { comp } = makeInitializedComponent({ conditionExpressions: exprs });
      comp.selectedConditionIndex = 0;
      expect(comp.deleteDisabled).toBe(false);
   });

   it("should return false when selectedConditionIndex is any positive number", () => {
      const exprs = [makeConditionExpr("C1"), makeConditionExpr("C2"), makeConditionExpr("C3")];
      const { comp } = makeInitializedComponent({ conditionExpressions: exprs });
      comp.selectedConditionIndex = 2;
      expect(comp.deleteDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: deleteCondition [Risk 3]
// ---------------------------------------------------------------------------

describe("GroupingDialog — deleteCondition", () => {

   // 🔁 Regression-sensitive: deleteCondition splices the array in-place which mutates
   //    the value of the conditionExpressions FormControl directly. If the splice index
   //    is wrong (off-by-one), the wrong condition is removed.
   it("should remove the condition at selectedConditionIndex", () => {
      const c1 = makeConditionExpr("C1");
      const c2 = makeConditionExpr("C2");
      const c3 = makeConditionExpr("C3");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2, c3] });
      comp.selectedConditionIndex = 1; // remove c2

      comp.deleteCondition();

      const remaining = comp.form.get("conditionExpressions").value;
      expect(remaining).toHaveLength(2);
      expect(remaining).toContain(c1);
      expect(remaining).not.toContain(c2);
      expect(remaining).toContain(c3);
   });

   it("should reset selectedConditionIndex to undefined after deletion", () => {
      const c1 = makeConditionExpr("C1");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1] });
      comp.selectedConditionIndex = 0;

      comp.deleteCondition();

      expect(comp.selectedConditionIndex).toBeUndefined();
   });

   it("should remove the first condition correctly (index=0)", () => {
      const c1 = makeConditionExpr("First");
      const c2 = makeConditionExpr("Second");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2] });
      comp.selectedConditionIndex = 0;

      comp.deleteCondition();

      const remaining = comp.form.get("conditionExpressions").value;
      expect(remaining).toHaveLength(1);
      expect(remaining[0]).toBe(c2);
   });

   it("should remove the last condition correctly", () => {
      const c1 = makeConditionExpr("First");
      const c2 = makeConditionExpr("Last");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2] });
      comp.selectedConditionIndex = 1;

      comp.deleteCondition();

      const remaining = comp.form.get("conditionExpressions").value;
      expect(remaining).toHaveLength(1);
      expect(remaining[0]).toBe(c1);
   });
});

// ---------------------------------------------------------------------------
// Group 4: moveConditionUp [Risk 3]
// ---------------------------------------------------------------------------

describe("GroupingDialog — moveConditionUp", () => {

   // 🔁 Regression-sensitive: moveConditionUp must swap index and index-1 correctly;
   //    the index pointer must decrement so the selection tracks the moved item.
   it("should swap condition at index with the one above it", () => {
      const c1 = makeConditionExpr("C1");
      const c2 = makeConditionExpr("C2");
      const c3 = makeConditionExpr("C3");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2, c3] });
      comp.selectedConditionIndex = 1; // move c2 up

      comp.moveConditionUp();

      const list = comp.form.get("conditionExpressions").value;
      expect(list[0]).toBe(c2);
      expect(list[1]).toBe(c1);
      expect(list[2]).toBe(c3);
   });

   it("should decrement selectedConditionIndex after moving up", () => {
      const c1 = makeConditionExpr("C1");
      const c2 = makeConditionExpr("C2");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2] });
      comp.selectedConditionIndex = 1;

      comp.moveConditionUp();

      expect(comp.selectedConditionIndex).toBe(0);
   });

   it("should correctly move condition from index 2 to index 1", () => {
      const c1 = makeConditionExpr("C1");
      const c2 = makeConditionExpr("C2");
      const c3 = makeConditionExpr("C3");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2, c3] });
      comp.selectedConditionIndex = 2;

      comp.moveConditionUp();

      const list = comp.form.get("conditionExpressions").value;
      expect(list[0]).toBe(c1);
      expect(list[1]).toBe(c3);
      expect(list[2]).toBe(c2);
      expect(comp.selectedConditionIndex).toBe(1);
   });
});

// ---------------------------------------------------------------------------
// Group 5: moveConditionDown [Risk 3]
// ---------------------------------------------------------------------------

describe("GroupingDialog — moveConditionDown", () => {

   // 🔁 Regression-sensitive: moveConditionDown must swap index and index+1 correctly;
   //    the index pointer must increment so the selection tracks the moved item.
   it("should swap condition at index with the one below it", () => {
      const c1 = makeConditionExpr("C1");
      const c2 = makeConditionExpr("C2");
      const c3 = makeConditionExpr("C3");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2, c3] });
      comp.selectedConditionIndex = 0; // move c1 down

      comp.moveConditionDown();

      const list = comp.form.get("conditionExpressions").value;
      expect(list[0]).toBe(c2);
      expect(list[1]).toBe(c1);
      expect(list[2]).toBe(c3);
   });

   it("should increment selectedConditionIndex after moving down", () => {
      const c1 = makeConditionExpr("C1");
      const c2 = makeConditionExpr("C2");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2] });
      comp.selectedConditionIndex = 0;

      comp.moveConditionDown();

      expect(comp.selectedConditionIndex).toBe(1);
   });

   it("should correctly move condition from index 1 to index 2", () => {
      const c1 = makeConditionExpr("C1");
      const c2 = makeConditionExpr("C2");
      const c3 = makeConditionExpr("C3");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2, c3] });
      comp.selectedConditionIndex = 1;

      comp.moveConditionDown();

      const list = comp.form.get("conditionExpressions").value;
      expect(list[0]).toBe(c1);
      expect(list[1]).toBe(c3);
      expect(list[2]).toBe(c2);
      expect(comp.selectedConditionIndex).toBe(2);
   });

   it("should correctly handle list with only 2 items (move first to last)", () => {
      const c1 = makeConditionExpr("First");
      const c2 = makeConditionExpr("Second");
      const { comp } = makeInitializedComponent({ conditionExpressions: [c1, c2] });
      comp.selectedConditionIndex = 0;

      comp.moveConditionDown();

      const list = comp.form.get("conditionExpressions").value;
      expect(list[0]).toBe(c2);
      expect(list[1]).toBe(c1);
   });
});

// ---------------------------------------------------------------------------
// Group 6: cancel [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — cancel", () => {

   it("should emit onCancel with the string 'cancel'", () => {
      const { comp } = makeInitializedComponent();
      const spy = vi.fn();
      comp.onCancel.subscribe(spy);

      comp.cancel();

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith("cancel");
   });

   it("should emit onCancel even when the form is invalid", () => {
      const { comp } = makeInitializedComponent();
      const spy = vi.fn();
      comp.onCancel.subscribe(spy);
      // Invalidate the form
      comp.form.get("newName").setValue("");

      comp.cancel();

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith("cancel");
   });

   it("should NOT emit onCommit when cancel is called", () => {
      const { comp } = makeInitializedComponent();
      const commitSpy = vi.fn();
      const cancelSpy = vi.fn();
      comp.onCommit.subscribe(commitSpy);
      comp.onCancel.subscribe(cancelSpy);

      comp.cancel();

      expect(commitSpy).not.toHaveBeenCalled();
      expect(cancelSpy).toHaveBeenCalledTimes(1);
   });
});
