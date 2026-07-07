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

import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { CalcDataPane } from "./calc-data-pane.component";
import { BindingService } from "../../services/binding.service";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { TestUtils } from "../../../common/test/test-utils";
import { CalcTableBindingModel } from "../../data/table/calc-table-binding-model";
import { CellBindingInfo } from "../../data/table/cell-binding-info";

export const editorServiceMock = {
   getAggregates: vi.fn(() => []),
   getCellBinding: vi.fn(),
   getCellNames: vi.fn(() => []),
   cellScript: "cell['A1']",
   loadCellScript: vi.fn(),
   setCellBinding: vi.fn(),
   getSelectCells: vi.fn(() => [TestUtils.createMockCalcTableCell()]),
   getCellNamesWithDefaults: vi.fn(() => [{ label: "(none)", value: null }]),
   changeColumnValue: vi.fn(),
};

export const bindingServiceMock = {
   isGrayedOutField: vi.fn().mockReturnValue(false),
};

export const modalMock = { open: vi.fn() };

export const changeDetectorRefMock = {
   detectChanges: vi.fn(),
};

export function createCalcBindingModel(): CalcTableBindingModel {
   const model = TestUtils.createMockCalcTableBindingModel();
   model.availableFields = [
      TestUtils.createMockDataRef("state"),
      TestUtils.createMockDataRef("sales"),
   ];
   return model;
}

export function createCellBinding(name = "state"): CellBindingInfo {
   const binding = TestUtils.createMockCellBindingInfo(name);
   binding.type = CellBindingInfo.BIND_COLUMN;
   binding.btype = CellBindingInfo.DETAIL;
   binding.value = "state";
   binding.expansion = CellBindingInfo.EXPAND_NONE;
   binding.order = { option: 0, interval: 1 } as any;
   return binding;
}

export function resetCalcDataPaneMocks(cellBinding?: CellBindingInfo): CellBindingInfo {
   const binding = cellBinding ?? createCellBinding();
   editorServiceMock.getCellBinding.mockReturnValue(binding);
   editorServiceMock.getSelectCells.mockReturnValue([TestUtils.createMockCalcTableCell()]);
   editorServiceMock.getCellNames.mockReturnValue(["state"]);
   editorServiceMock.setCellBinding.mockClear();
   editorServiceMock.changeColumnValue.mockClear();
   editorServiceMock.loadCellScript.mockClear();
   return binding;
}

export async function renderCalcDataPane(props: Record<string, any> = {}) {
   const cellBinding = resetCalcDataPaneMocks(props["_cellBinding"] as CellBindingInfo | undefined);
   const model = props["_model"] as CalcTableBindingModel ?? createCalcBindingModel();
   const renderProps = {
      vsId: "vs1",
      assemblyName: "CalcTable1",
      bindingModel: model,
      ...props,
   };
   delete renderProps["_model"];
   delete renderProps["_cellBinding"];

   const { fixture, container } = await render(CalcDataPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: VSCalcTableEditorService, useValue: editorServiceMock },
         { provide: BindingService, useValue: bindingServiceMock },
         { provide: NgbModal, useValue: modalMock },
         { provide: ChangeDetectorRef, useValue: changeDetectorRefMock },
      ],
      componentProperties: renderProps,
   });

   const comp = fixture.componentInstance as CalcDataPane;
   comp.dropdown = { toArray: () => [{ close: vi.fn() }] } as any;
   return { fixture, container, comp, model, cellBinding };
}
