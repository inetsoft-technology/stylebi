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
 * EmbeddedTableDialog — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — rows / cols validation: non-positive integers are rejected
 *   Group 2 [Risk 3] — name validation: duplicate assembly names are rejected
 *   Group 3 [Risk 2] — ngOnInit: loads model from server and initializes form
 *   Group 4 [Risk 2] — saveChanges: emits onCommit with the current form values
 *   Group 5 [Risk 1] — cancelChanges: emits onCancel
 *
 * Old spec coverage ported (Risk 3):
 *   "should not allow non-positive number of rows or columns" (rows=0, rows=-1, cols=0, cols=-1)
 *   "should not allow duplicate names"
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { EmbeddedTableDialog } from "./embedded-table-dialog.component";
import { ModelService } from "../../../widget/services/model.service";
import { EmbeddedTableDialogModel } from "../../data/ws/embedded-table-dialog-model";

const MODEL_SERVICE_MOCK = { getModel: vi.fn() };

const DEFAULT_MODEL: EmbeddedTableDialogModel = {
   name: "NewTable1",
   rows: 10,
   cols: 5,
};

function makeWorksheet(assemblyNames: string[] = [], runtimeId = "ws-123") {
   return {
      runtimeId,
      assemblyNames: () => assemblyNames,
   } as any;
}

async function renderComponent(assemblyNames: string[] = [], model = DEFAULT_MODEL) {
   MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(model));

   const { fixture } = await render(EmbeddedTableDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [{ provide: ModelService, useValue: MODEL_SERVICE_MOCK }],
      componentProperties: { worksheet: makeWorksheet(assemblyNames) },
   });

   return fixture.componentInstance as EmbeddedTableDialog;
}

beforeEach(() => MODEL_SERVICE_MOCK.getModel.mockReset());
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: rows / cols validation [Risk 3]
// ---------------------------------------------------------------------------

describe("EmbeddedTableDialog — rows/cols validation", () => {
   // 🔁 Regression-sensitive: old spec regression — non-positive values create unusable tables.
   it("should mark rows as invalid when value is 0", async () => {
      const comp = await renderComponent();
      comp.form.controls["rows"].setValue(0);
      expect(comp.form.controls["rows"].valid).toBe(false);
   });

   it("should mark rows as invalid when value is negative", async () => {
      const comp = await renderComponent();
      comp.form.controls["rows"].setValue(-1);
      expect(comp.form.controls["rows"].valid).toBe(false);
   });

   it("should mark rows as valid when value is 1", async () => {
      const comp = await renderComponent();
      comp.form.controls["rows"].setValue(1);
      expect(comp.form.controls["rows"].valid).toBe(true);
   });

   it("should mark cols as invalid when value is 0", async () => {
      const comp = await renderComponent();
      comp.form.controls["cols"].setValue(0);
      expect(comp.form.controls["cols"].valid).toBe(false);
   });

   it("should mark cols as invalid when value is negative", async () => {
      const comp = await renderComponent();
      comp.form.controls["cols"].setValue(-3);
      expect(comp.form.controls["cols"].valid).toBe(false);
   });

   it("should mark cols as valid when value is 1", async () => {
      const comp = await renderComponent();
      comp.form.controls["cols"].setValue(1);
      expect(comp.form.controls["cols"].valid).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: name duplicate validation [Risk 3]
// ---------------------------------------------------------------------------

describe("EmbeddedTableDialog — name duplicate validation", () => {
   // 🔁 Regression-sensitive: old spec regression — duplicate names cause server-side conflicts.
   it("should mark name as invalid when it matches an existing assembly name (case-insensitive)", async () => {
      const comp = await renderComponent(["ExistingTable"]);
      comp.form.controls["name"].setValue("ExistingTable");
      expect(comp.form.controls["name"].valid).toBe(false);
   });

   it("should mark name as invalid for case-insensitive duplicate", async () => {
      const comp = await renderComponent(["ExistingTable"]);
      comp.form.controls["name"].setValue("existingtable");
      expect(comp.form.controls["name"].valid).toBe(false);
   });

   it("should mark name as valid when it does not match any existing assembly name", async () => {
      const comp = await renderComponent(["ExistingTable"]);
      comp.form.controls["name"].setValue("NewUniqueName");
      expect(comp.form.controls["name"].valid).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnInit — model loading [Risk 2]
// ---------------------------------------------------------------------------

describe("EmbeddedTableDialog — ngOnInit", () => {
   it("should call modelService.getModel with a URI derived from the worksheet runtimeId", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(DEFAULT_MODEL));
      await render(EmbeddedTableDialog, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: ModelService, useValue: MODEL_SERVICE_MOCK }],
         componentProperties: { worksheet: makeWorksheet([], "my-runtime-id") },
      });

      expect(MODEL_SERVICE_MOCK.getModel).toHaveBeenCalledWith(
         expect.stringContaining("embedded-table-dialog-model")
      );
   });

   it("should initialize the form with name, rows, and cols from the loaded model", async () => {
      const model = { name: "Table1", rows: 5, cols: 3 };
      const comp = await renderComponent([], model);

      expect(comp.form.value.name).toBe("Table1");
      expect(comp.form.value.rows).toBe(5);
      expect(comp.form.value.cols).toBe(3);
   });
});

// ---------------------------------------------------------------------------
// Group 4: saveChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("EmbeddedTableDialog — saveChanges", () => {
   // 🔁 Regression-sensitive: onCommit must carry the form values; if model is not updated
   //    from form, stale values are saved.
   it("should emit onCommit with the current form values", async () => {
      const comp = await renderComponent();
      const emitted: EmbeddedTableDialogModel[] = [];
      comp.onCommit.subscribe(m => emitted.push(m));

      comp.form.controls["name"].setValue("MyTable");
      comp.form.controls["rows"].setValue(20);
      comp.form.controls["cols"].setValue(8);
      comp.saveChanges();

      expect(emitted).toHaveLength(1);
      expect(emitted[0].name).toBe("MyTable");
      expect(emitted[0].rows).toBe(20);
      expect(emitted[0].cols).toBe(8);
   });
});

// ---------------------------------------------------------------------------
// Group 5: cancelChanges [Risk 1]
// ---------------------------------------------------------------------------

describe("EmbeddedTableDialog — cancelChanges", () => {
   it("should emit via onCancel when cancelChanges is called", async () => {
      const comp = await renderComponent();
      const emitted: string[] = [];
      comp.onCancel.subscribe(v => emitted.push(v));

      comp.cancelChanges();

      expect(emitted).toHaveLength(1);
   });
});
