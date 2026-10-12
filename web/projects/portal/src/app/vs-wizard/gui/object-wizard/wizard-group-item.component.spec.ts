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

import { SimpleChange } from "@angular/core";
import { of } from "rxjs";
import { ViewsheetClientService } from "../../../common/viewsheet-client";

import { VSWizardGroupItem } from "./wizard-group-item.component";

describe("VSWizardGroupItem", () => {
  let component: VSWizardGroupItem;
  let stompClient: any;
  let viewsheetClientService: any;
  let dialogService: any;
  let treeService: any;
  let modelService: any;
  let examplesService: any;
  let zone: any;

  beforeEach(() => {
    stompClient = { connect: vi.fn(), subscribe: vi.fn() };
    zone = { run: vi.fn() };
    viewsheetClientService = new ViewsheetClientService(stompClient, zone);
    dialogService = {
      open: vi.fn(),
      assemblyDelete: vi.fn(),
      objectDelete: vi.fn()
    };
    treeService = {
      getTableName: vi.fn(() => "Table")
    };
     modelService = {
        sendModel: vi.fn(),
        getModel: vi.fn()
     };
    examplesService = {
       loadDateLevelExamples: vi.fn((levels: string[], dataType: string) =>
          of({ dateLevelExamples: levels.map(l => dataType + "-" + l) }))
    };

    component = new VSWizardGroupItem(dialogService, viewsheetClientService,
       treeService, modelService, examplesService);
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });

  function setDataRef(ref: any): void {
    const previous = component.dataRef;
    component.dataRef = ref;
    component.ngOnChanges({ dataRef: new SimpleChange(previous, ref, previous == null) });
  }

  // moving a date dimension up reuses the row instance created for a string dimension
  it("should reload date level examples when the row is handed a date ref", () => {
    setDataRef({ name: "Category", dataType: "string" });
    expect(examplesService.loadDateLevelExamples).not.toHaveBeenCalled();
    expect(component.dateLevelExamples).toEqual([]);

    setDataRef({ name: "Date", dataType: "date", dateLevel: "5" });
    expect(examplesService.loadDateLevelExamples).toHaveBeenCalledTimes(1);
    expect(examplesService.loadDateLevelExamples.mock.calls[0][1]).toBe("date");
    expect(component.dateLevelExamples[0]).toBe("date-" + component.dateGroups[0].value);
  });

  it("should not reload examples when the new ref has the same data type", () => {
    setDataRef({ name: "Date", dataType: "date", dateLevel: "5" });
    setDataRef({ name: "Date2", dataType: "date", dateLevel: "5" });
    expect(examplesService.loadDateLevelExamples).toHaveBeenCalledTimes(1);
  });

  it("should clear examples when a date row is handed a non-date ref", () => {
    setDataRef({ name: "Date", dataType: "timeInstant", dateLevel: "5" });
    expect(component.dateLevelExamples.length).toBe(component.dateTimeGroups.length);

    setDataRef({ name: "Category", dataType: "string" });
    expect(component.dateLevelExamples).toEqual([]);
  });
});
