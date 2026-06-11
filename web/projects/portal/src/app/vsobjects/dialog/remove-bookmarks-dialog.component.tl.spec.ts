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
 * RemoveBookmarksDialog — single pass + memory leak
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — Memory leak: constructor valueChanges subscription not cleaned up on destroy
 *   Group 2 [Risk 2] — setFilterDate(): three state writes (condition/dateTime/form) must stay in sync
 *   Group 3 [Risk 2] — commitChanges(): emits live condition state, including mutations from selectOption
 *   Group 4 [Risk 1] — selectOption, cancelChanges, getFilterOptionLabel, isSelectedOption,
 *                       selectedFilterOptionLabel getter
 *
 * Confirmed bugs (it.fails):
 *   Memory leak — constructor subscribes to filterDate.valueChanges with no ngOnDestroy cleanup;
 *   the subscription remains active after the component is destroyed and continues mutating
 *   condition.filterTime on any subsequent form value change.
 *
 * Out of scope:
 *   dropdownWidth getter — depends on nativeElement.offsetWidth which is always 0 in jsdom
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { render } from "@testing-library/angular";

import { RemoveBookmarksDialog } from "./remove-bookmarks-dialog.component";
import { AnnotationFilterOption } from "../model/remove-annotations-condition";

async function renderComp() {
   const result = await render(RemoveBookmarksDialog, {
      imports: [ReactiveFormsModule],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance, fixture: result.fixture };
}

// ---------------------------------------------------------------------------
// Group 1: Memory leak — valueChanges subscription survives destroy
// ---------------------------------------------------------------------------

describe("RemoveBookmarksDialog — memory leak", () => {

   // 🔁 Regression-sensitive: the constructor subscribes to filterDate.valueChanges with no
   // takeUntilDestroyed/unsubscribe; after destroy the subscription still fires and mutates
   // condition.filterTime, leaving a potentially stale value visible to any code that holds
   // a reference to the destroyed instance.
   it.fails("should not update condition.filterTime after the component is destroyed (ngOnDestroy not implemented)", async () => {
      const { comp, fixture } = await renderComp();
      const originalTime = comp.condition.filterTime;
      fixture.destroy();
      // After destroy, setValue should NOT propagate via the still-active subscription
      comp.form.get("filterDate")?.setValue("2024-01-01");
      expect(comp.condition.filterTime).toBe(originalTime); // FAILS: subscription still fires
   });
});

// ---------------------------------------------------------------------------
// Group 2: setFilterDate() — three-state sync
// ---------------------------------------------------------------------------

describe("RemoveBookmarksDialog — setFilterDate", () => {

   // 🔁 Regression-sensitive: condition.filterTime, dateTime, and form control must always
   // agree; if any write is dropped, the filter sent to the server diverges from the picker UI.
   it("should update condition.filterTime, dateTime, and form control value in sync", async () => {
      const { comp } = await renderComp();
      const initialDateTime = comp.dateTime;
      const newDate = "2020-01-01"; // deliberately far from today so dateTime definitely changes
      comp.setFilterDate(newDate);
      expect(comp.condition.filterTime).toBe(newDate);
      expect(comp.form.get("filterDate")?.value).toBe(newDate);
      expect(comp.dateTime).not.toBe(initialDateTime); // toTimeInstant was called with the new value
   });

   it("should preserve filterOption when setFilterDate is called after selectOption", async () => {
      const { comp } = await renderComp();
      comp.selectOption(AnnotationFilterOption.NOT_ACCESSED);
      comp.setFilterDate("2025-06-01");
      // setFilterDate must not reset filterOption, and selectOption must not reset filterTime
      expect(comp.condition.filterTime).toBe("2025-06-01");
      expect(comp.condition.filterOption).toBe(AnnotationFilterOption.NOT_ACCESSED);
   });
});

// ---------------------------------------------------------------------------
// Group 3: commitChanges() — live condition state
// ---------------------------------------------------------------------------

describe("RemoveBookmarksDialog — commitChanges", () => {

   it("should emit the current condition object via onCommit", async () => {
      const { comp } = await renderComp();
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);
      comp.commitChanges();
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.mock.calls[0][0]).toBe(comp.condition);
   });

   it("should emit with updated filterOption after selectOption has been called", async () => {
      const { comp } = await renderComp();
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);
      comp.selectOption(AnnotationFilterOption.NOT_ACCESSED);
      comp.commitChanges();
      expect(spy.mock.calls[0][0].filterOption).toBe(AnnotationFilterOption.NOT_ACCESSED);
   });
});

// ---------------------------------------------------------------------------
// Group 4: selectOption, cancelChanges, getFilterOptionLabel, isSelectedOption, getter
// ---------------------------------------------------------------------------

describe("RemoveBookmarksDialog — selectOption / cancelChanges / labels / isSelectedOption", () => {

   it("selectOption: should update condition.filterOption", async () => {
      const { comp } = await renderComp();
      comp.selectOption(AnnotationFilterOption.NOT_ACCESSED);
      expect(comp.condition.filterOption).toBe(AnnotationFilterOption.NOT_ACCESSED);
   });

   it("cancelChanges: should emit 'cancel' via onCancel", async () => {
      const { comp } = await renderComp();
      const spy = vi.fn();
      comp.onCancel.subscribe(spy);
      comp.cancelChanges();
      expect(spy).toHaveBeenCalledWith("cancel");
   });

   it("getFilterOptionLabel: should return correct i18n keys for OLDER_THAN and NOT_ACCESSED", async () => {
      const { comp } = await renderComp();
      expect(comp.getFilterOptionLabel(AnnotationFilterOption.OLDER_THAN)).toBe("_#(js:Older than)");
      expect(comp.getFilterOptionLabel(AnnotationFilterOption.NOT_ACCESSED)).toBe("_#(js:Not accessed since)");
   });

   it("isSelectedOption and selectedFilterOptionLabel: should reflect current filterOption", async () => {
      const { comp } = await renderComp();
      expect(comp.isSelectedOption(AnnotationFilterOption.OLDER_THAN)).toBe(true);
      expect(comp.isSelectedOption(AnnotationFilterOption.NOT_ACCESSED)).toBe(false);
      expect(comp.selectedFilterOptionLabel).toBe("_#(js:Older than)");

      comp.selectOption(AnnotationFilterOption.NOT_ACCESSED);
      expect(comp.isSelectedOption(AnnotationFilterOption.NOT_ACCESSED)).toBe(true);
      expect(comp.selectedFilterOptionLabel).toBe("_#(js:Not accessed since)");
   });
});
