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
 * DataInputPane — Angular Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — validateDateFormat: char-allow/required-token rules gate the DatePipe path
 *   Group 2 [Risk 3]  — onQueryDateFormatToggle: toggling off clears invalid; toggling on is a no-op
 *   Group 3 [Risk 3]  — ngOnChanges: switching dataType away from date resets queryDateFormat and invalid
 *   Group 4 [Risk 2]  — ngOnInit: pre-validates format when conditions met; skips when comboBox=false
 *   Group 5 [Risk 2]  — DOM contract: error alert rendered when dateFormatInvalid; absent otherwise
 *
 * KEY contracts:
 *   Allowed chars: y M d : - / . ' (space) ,  — time tokens H/m/s are explicitly blocked.
 *   All three tokens y, M, d must be present; any missing token → invalid regardless of allowed chars.
 *   dateFormatInvalidChange EventEmitter fires on EVERY setDateFormatInvalid() call (valid or invalid).
 *   isDateType is true for "date" (XSchema.DATE) and "timeInstant" (XSchema.TIME_INSTANT) only.
 */

import { CommonModule, DatePipe } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { render, screen } from "@testing-library/angular";
import { DataInputPaneModel } from "../../data/vs/data-input-pane-model";
import { XSchema } from "../../../common/data/xschema";
import { DataInputPane } from "./data-input-pane.component";

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

function createModel(overrides: Partial<DataInputPaneModel> = {}): DataInputPaneModel {
   return {
      table: "",          // empty table → updateColumns() skips HTTP call
      tableLabel: "",
      rowValue: "",
      columnValue: "",
      defaultValue: "",
      targetTree: null,
      variable: false,
      writeBackDirectly: false,
      queryDateFormat: false,
      dateFormatPattern: "",
      ...overrides,
   };
}

interface RenderProps {
   model?: DataInputPaneModel;
   comboBox?: boolean;
   dataType?: string;
}

async function renderPane(props: RenderProps = {}) {
   return render(DataInputPane, {
      imports: [CommonModule, FormsModule, HttpClientTestingModule],
      providers: [DatePipe],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         model: props.model ?? createModel(),
         comboBox: props.comboBox ?? true,
         checkBox: false,   // must be false to render the date-format section
         dataType: props.dataType ?? XSchema.DATE,
         runtimeId: "vs1",
         variableValues: [],
      },
   });
}

// ---------------------------------------------------------------------------
// Group 1 — validateDateFormat: char-allow / required-token rules [Risk 3]
// ---------------------------------------------------------------------------

describe("DataInputPane — validateDateFormat — char/token rules [Group 1, Risk 3]", () => {

   // 🔁 Regression-sensitive: canonical valid format must not be rejected after regex refactoring
   // Risk Point/Contract: allowed=/^[yMd:\-/. ,']+$/ AND required=(?=.*y+)(?=.*M+)(?=.*d+) must both pass
   it("should accept 'yyyy-MM-dd' and set dateFormatInvalid=false, emitting false", async () => {
      const { fixture } = await renderPane();
      const comp = fixture.componentInstance;
      const emitted: boolean[] = [];
      comp.dateFormatInvalidChange.subscribe(v => emitted.push(v));

      comp.validateDateFormat("yyyy-MM-dd");

      expect(comp.dateFormatInvalid).toBe(false);   // state flag
      expect(emitted).toEqual([false]);             // emitter fires once with correct value

      // Same formatted output for two dates must still be invalid (r1 === r2 branch).
      const transformSpy = jest.spyOn((comp as any).datePipe, "transform").mockReturnValue("same");
      comp.validateDateFormat("yyyy-MM-dd");
      expect(comp.dateFormatInvalid).toBe(true);
      transformSpy.mockRestore();

      // DatePipe throwing should be caught and mapped to invalid=true.
      const throwSpy = jest.spyOn((comp as any).datePipe, "transform").mockImplementation(() => {
         throw new Error("boom");
      });
      comp.validateDateFormat("yyyy-MM-dd");
      expect(comp.dateFormatInvalid).toBe(true);
      expect(emitted).toEqual([false, true, true]);
      throwSpy.mockRestore();
   });

   // 🔁 Regression-sensitive: relaxing the allowed regex to include H/m/s would silently change query semantics
   // Risk Point/Contract: allowed regex explicitly excludes time tokens; they must keep failing the char check
   it("should reject 'yyyy-MM-dd HH:mm:ss' (time tokens) and set dateFormatInvalid=true, emitting true", async () => {
      const { fixture } = await renderPane();
      const comp = fixture.componentInstance;
      const emitted: boolean[] = [];
      comp.dateFormatInvalidChange.subscribe(v => emitted.push(v));

      comp.validateDateFormat("yyyy-MM-dd HH:mm:ss");

      expect(comp.dateFormatInvalid).toBe(true);
      expect(emitted).toEqual([true]);
   });

   // Contract: 'yyyy-MM' passes the allowed-char check but must fail the required-token check
   // Why High Value: without 'd', the query receives the same date value all month, silently returning stale data
   it("should reject 'yyyy-MM' (missing 'd' token) and set dateFormatInvalid=true, emitting true", async () => {
      const { fixture } = await renderPane();
      const comp = fixture.componentInstance;
      const emitted: boolean[] = [];
      comp.dateFormatInvalidChange.subscribe(v => emitted.push(v));

      comp.validateDateFormat("yyyy-MM");

      expect(comp.dateFormatInvalid).toBe(true);
      expect(emitted).toEqual([true]);
   });

   // Boundary: empty string must be rejected — it must not silently pass as "no format applied"
   it("should reject empty string as invalid and emit true", async () => {
      const { fixture } = await renderPane();
      const comp = fixture.componentInstance;
      const emitted: boolean[] = [];
      comp.dateFormatInvalidChange.subscribe(v => emitted.push(v));

      comp.validateDateFormat("");

      expect(comp.dateFormatInvalid).toBe(true);
      expect(emitted).toEqual([true]);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — onQueryDateFormatToggle: clear vs preserve invalid state [Risk 3]
// ---------------------------------------------------------------------------

describe("DataInputPane — onQueryDateFormatToggle — clear vs preserve invalid state [Group 2, Risk 3]", () => {

   // 🔁 Regression-sensitive: if unchecking the checkbox does not clear invalid, the parent dialog OK button stays blocked
   // Risk Point/Contract: toggle off must call setDateFormatInvalid(false); parent dialog listens via dateFormatInvalidChange
   it("should clear dateFormatInvalid and emit false when toggled off (checked=false)", async () => {
      const { fixture } = await renderPane();
      const comp = fixture.componentInstance;

      comp.validateDateFormat("bad!");     // set invalid=true
      expect(comp.dateFormatInvalid).toBe(true);

      const emitted: boolean[] = [];
      comp.dateFormatInvalidChange.subscribe(v => emitted.push(v));

      comp.onQueryDateFormatToggle(false);

      expect(comp.dateFormatInvalid).toBe(false);
      expect(emitted).toEqual([false]);   // D3: emitter must confirm the cleared state

      // Calling toggle-off again while already false should still emit false.
      comp.onQueryDateFormatToggle(false);
      expect(comp.dateFormatInvalid).toBe(false);
      expect(emitted).toEqual([false, false]);
   });

   // Risk Point/Contract: toggling ON is a no-op for dateFormatInvalid — it must not silently validate a bad format
   // Why High Value: if toggle-on reset invalid to false, user could submit a bad format without seeing any error
   it("should leave dateFormatInvalid unchanged and emit nothing when toggled on (checked=true)", async () => {
      const { fixture } = await renderPane();
      const comp = fixture.componentInstance;

      comp.validateDateFormat("bad!");     // set invalid=true
      const invalidBefore = comp.dateFormatInvalid;

      const emitted: boolean[] = [];
      comp.dateFormatInvalidChange.subscribe(v => emitted.push(v));

      comp.onQueryDateFormatToggle(true);

      expect(comp.dateFormatInvalid).toBe(invalidBefore);   // unchanged
      expect(emitted).toEqual([]);    // nothing emitted
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ngOnChanges: dataType switch resets format state [Risk 3]
// ---------------------------------------------------------------------------

describe("DataInputPane — ngOnChanges — dataType switch resets format state [Group 3, Risk 3]", () => {

   // 🔁 Regression-sensitive: stale queryDateFormat on a non-date column would silently corrupt query parameters
   // Risk Point/Contract: reset must happen when !isDateType is true after the new dataType takes effect
   it("should reset queryDateFormat to false and clear dateFormatInvalid when dataType changes to non-date", async () => {
      const { fixture } = await renderPane({ dataType: XSchema.DATE });
      const comp = fixture.componentInstance;
      comp.model.queryDateFormat = true;
      comp.validateDateFormat("bad!");     // set invalid=true
      comp.dataType = "string";           // simulate Angular updating the @Input before ngOnChanges

      comp.ngOnChanges({
         dataType: new SimpleChange(XSchema.DATE, "string", false),
      });

      expect(comp.model.queryDateFormat).toBe(false);
      expect(comp.dateFormatInvalid).toBe(false);
   });

   // Risk Point/Contract: the `!changes["dataType"].firstChange` guard prevents reset during component init
   // Why High Value: without this guard, a queryDateFormat value loaded from a saved model would be lost on open
   it("should NOT reset queryDateFormat when the dataType change is the first (initial) binding", async () => {
      const { fixture } = await renderPane({ dataType: XSchema.DATE });
      const comp = fixture.componentInstance;
      comp.model.queryDateFormat = true;

      comp.ngOnChanges({
         dataType: new SimpleChange(undefined, XSchema.DATE, true),   // firstChange=true
      });

      expect(comp.model.queryDateFormat).toBe(true);   // preserved

      // Non-dataType changes must not trigger reset behavior.
      comp.ngOnChanges({
         comboBox: new SimpleChange(true, false, false),
      });
      expect(comp.model.queryDateFormat).toBe(true);
   });

   // Boundary: switching between two date types (DATE → TIME_INSTANT) must not trigger the reset
   it("should NOT reset queryDateFormat when dataType changes from DATE to TIME_INSTANT (both are date types)", async () => {
      const { fixture } = await renderPane({ dataType: XSchema.DATE });
      const comp = fixture.componentInstance;
      comp.model.queryDateFormat = true;
      comp.dataType = XSchema.TIME_INSTANT;   // update component input before ngOnChanges runs

      comp.ngOnChanges({
         dataType: new SimpleChange(XSchema.DATE, XSchema.TIME_INSTANT, false),
      });

      expect(comp.model.queryDateFormat).toBe(true);   // isDateType still true → no reset
   });
});

// ---------------------------------------------------------------------------
// Group 4 — ngOnInit: initial format validation [Risk 2]
// ---------------------------------------------------------------------------

describe("DataInputPane — ngOnInit — pre-validates format when conditions met [Group 4, Risk 2]", () => {

   // Why High Value: if ngOnInit skips validation, a previously saved invalid format silently passes into the dialog
   it("should call validateDateFormat on init when comboBox=true, date type, queryDateFormat=true, and pattern is set", async () => {
      const model = createModel({ queryDateFormat: true, dateFormatPattern: "bad!" });

      // render() triggers ngOnInit internally
      const { fixture } = await renderPane({ model, comboBox: true, dataType: XSchema.DATE });

      expect(fixture.componentInstance.dateFormatInvalid).toBe(true);
   });

   // Risk Point/Contract: the format-date feature is exclusive to comboboxes — non-combobox inputs must never trigger validation
   it("should NOT validate format on init when comboBox is false", async () => {
      const model = createModel({ queryDateFormat: true, dateFormatPattern: "bad!" });

      const { fixture } = await renderPane({ model, comboBox: false, dataType: XSchema.DATE });

      expect(fixture.componentInstance.dateFormatInvalid).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — DOM contract: error alert visibility [Risk 2]
// ---------------------------------------------------------------------------

describe("DataInputPane — DOM — error alert rendered based on dateFormatInvalid [Group 5, Risk 2]", () => {

   // 🔁 Regression-sensitive: the alert div must stay inside the queryDateFormat *ngIf block; moving it breaks UX
   // Why High Value: if the alert is absent, users cannot tell their format is wrong and will submit bad data silently
   it("should render the 'Invalid Date Format' alert when dateFormatInvalid becomes true", async () => {
      const model = createModel({ queryDateFormat: true });
      const { fixture } = await renderPane({ model });

      fixture.componentInstance.validateDateFormat("bad!");
      fixture.detectChanges();

      expect(screen.queryByText("_#(Invalid Date Format)")).toBeInTheDocument();
   });

   // Risk Point/Contract: no false-positive error — alert must be absent when the format is valid
   it("should NOT render the 'Invalid Date Format' alert when dateFormatInvalid is false", async () => {
      const model = createModel({ queryDateFormat: true });
      const { fixture } = await renderPane({ model });

      fixture.componentInstance.validateDateFormat("yyyy-MM-dd");
      fixture.detectChanges();

      expect(screen.queryByText("_#(Invalid Date Format)")).not.toBeInTheDocument();

      // Alert is nested under queryDateFormat block; turning it off hides alert even if invalid=true.
      fixture.componentInstance.validateDateFormat("bad!");
      fixture.componentInstance.model.queryDateFormat = false;
      fixture.detectChanges();
      expect(screen.queryByText("_#(Invalid Date Format)")).not.toBeInTheDocument();
   });
});
