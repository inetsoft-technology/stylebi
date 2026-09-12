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

import { HttpParams } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { TableFieldmc } from "./table-fieldmc.component";
import { BindingService } from "../../services/binding.service";
import { TableEditorService } from "../../services/table/table-editor.service";
import { DndService } from "../../../common/dnd/dnd.service";
import { DateComparisonService } from "../../../vsobjects/util/date-comparison.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { TestUtils } from "../../../common/test/test-utils";
import { TableBindingModel } from "../../data/table/table-binding-model";
import { CrosstabBindingModel } from "../../data/table/crosstab-binding-model";
import { AbstractBindingRef } from "../../data/abstract-binding-ref";
import { BAggregateRef } from "../../data/b-aggregate-ref";
import { BDimensionRef } from "../../data/b-dimension-ref";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";

export function createTableBindingModel(): TableBindingModel {
   const model = TestUtils.createMockTableBindingModel();
   model.details = [TestUtils.createMockBDimensionRef("state")];
   model.allRows = [];
   model.source = {
      source: "Orders",
      type: 1,
      supportFullOutJoin: true,
      joinSources: ["Orders"],
   } as any;
   return model;
}

export const bindingServiceMock = {
   runtimeId: "runtime-1",
   assemblyName: "Table1",
   objectType: "vstable",
   bindingModel: null as TableBindingModel | null,
   getURLParams: vi.fn(() =>
      new HttpParams().set("vsId", "runtime-1").set("assemblyName", "Table1")
   ),
   getBindingModel: vi.fn(),
};

export const editorServiceMock = {
   setBindingModel: vi.fn(),
};

export const dndServiceMock = {
   setDragStartStyle: vi.fn(),
};

export const modalMock = {
   open: vi.fn(() => ({ result: Promise.resolve(true) })),
};

export const dcServiceMock = {
   checkBindingField: vi.fn().mockReturnValue(false),
};

export const uiContextMock = {
   isVS: vi.fn().mockReturnValue(true),
   isAdhoc: vi.fn().mockReturnValue(false),
   isSqlServer: vi.fn().mockReturnValue(false),
};

export function asTableField(field: AbstractBindingRef): AbstractBindingRef {
   if(field.classType === "aggregate" || field.classType === "BAggregateRefModel" ||
      (field as any).formulaOptionModel != null)
   {
      return Object.assign(new BAggregateRef(), field);
   }

   return Object.assign(new BDimensionRef(), field);
}

export function normalizeTableField(field: AbstractBindingRef): AbstractBindingRef {
   const normalized = asTableField(field);

   if(normalized instanceof BAggregateRef ||
      (normalized as any).formulaOptionModel != null)
   {
      normalized.classType = "BAggregateRefModel";
   }
   else {
      normalized.classType = "BDimensionRefModel";
   }

   return normalized;
}

export function resetTableFieldmcMocks(model?: TableBindingModel): TableBindingModel {
   const bindingModel = model ?? createTableBindingModel();
   bindingServiceMock.bindingModel = bindingModel;
   bindingServiceMock.getBindingModel.mockReturnValue(bindingModel);
   editorServiceMock.setBindingModel.mockClear();
   dndServiceMock.setDragStartStyle.mockClear();
   dcServiceMock.checkBindingField.mockReturnValue(false);
   return bindingModel;
}

export async function renderTableFieldmc(props: Record<string, any> = {}) {
   const model = resetTableFieldmcMocks(props["_model"] as TableBindingModel | undefined);
   const renderProps: Record<string, any> = { ...props, bindingModel: props["_model"] ?? model };
   delete renderProps["_model"];

   if(!renderProps.field) {
      renderProps.field = normalizeTableField(TestUtils.createMockBDimensionRef("state"));
   }
   else {
      renderProps.field = normalizeTableField(renderProps.field);
   }

   const { fixture, container } = await render(TableFieldmc, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: BindingService, useValue: bindingServiceMock },
         { provide: TableEditorService, useValue: editorServiceMock },
         { provide: DndService, useValue: dndServiceMock },
         { provide: NgbModal, useValue: modalMock },
         { provide: DateComparisonService, useValue: dcServiceMock },
         { provide: UIContextService, useValue: uiContextMock },
      ],
      componentProperties: renderProps,
   });

   const comp = fixture.componentInstance as TableFieldmc;
   comp.dropdown = { close: vi.fn() } as any;
   comp.combobox = { type: ComboMode.VALUE } as any;
   return { fixture, container, comp, model };
}

export function tableFieldCombo(container: HTMLElement): HTMLElement | null {
   return container.querySelector("dynamic-combo-box.fieldCombo");
}

export function tableFieldComboValue(container: HTMLElement): string | null {
   return tableFieldCombo(container)?.getAttribute("ng-reflect-value") ?? null;
}

export function tableFieldEditIcon(container: HTMLElement): HTMLElement | null {
   return container.querySelector(".fieldEditIcon i.btn-icon");
}

export function tableFieldOptionIcon(container: HTMLElement): HTMLElement | null {
   return container.querySelector(".setting-icon");
}

export function createCrosstabModel(): CrosstabBindingModel {
   const model = TestUtils.createMockCrosstabBindingModel();
   model.hasDateComparison = false;
   model.rows = [TestUtils.createMockBDimensionRef("state")];
   model.cols = [TestUtils.createMockBDimensionRef("city")];
   model.source = createTableBindingModel().source;
   return model;
}
