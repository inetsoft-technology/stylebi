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
import { of } from "rxjs";
import { ChartFieldmc } from "./chart-fieldmc.component";
import { BindingService } from "../../../services/binding.service";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { DndService } from "../../../../common/dnd/dnd.service";
import { DateComparisonService } from "../../../../vsobjects/util/date-comparison.service";
import { DateLevelExamplesService } from "../../../../common/services/date-level-examples.service";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartConstants } from "../../../../common/util/chart-constants";

export function createChartModel(): ChartBindingModel {
   const model = TestUtils.createMockChartBindingModel();
   model.xfields = [TestUtils.createMockChartDimensionRef("state")];
   model.yfields = [TestUtils.createMockChartAggregateRef("sales")];
   return model;
}

const dndTypeMap: Record<string, number> = {
   xfields: ChartConstants.DROP_REGION_X,
   yfields: ChartConstants.DROP_REGION_Y,
   color: ChartConstants.DROP_REGION_COLOR,
   shape: ChartConstants.DROP_REGION_SHAPE,
   size: ChartConstants.DROP_REGION_SIZE,
   text: ChartConstants.DROP_REGION_TEXT,
};

export const bindingServiceMock = {
   runtimeId: "runtime-1",
   assemblyName: "Chart1",
   objectType: "vschart",
   variableValues: [] as string[],
   bindingModel: null as ChartBindingModel | null,
   getURLParams: vi.fn(() =>
      new HttpParams().set("vsId", "runtime-1").set("assemblyName", "Chart1")
   ),
   getBindingModel: vi.fn(),
};

export const editorServiceMock = {
   changeChartRef: vi.fn(),
   getDNDType: vi.fn((fieldType: string) => dndTypeMap[fieldType] ?? -1),
   getCustomChartTypes: vi.fn(() => of([])),
   getChartStyles: vi.fn(() => of({ styles: [], customChartTypes: [] })),
   get bindingModel(): ChartBindingModel {
      return bindingServiceMock.getBindingModel() as ChartBindingModel;
   },
};

export const dndServiceMock = {
   setDragStartStyle: vi.fn(),
};

export const modalMock = {
   open: vi.fn(),
};

export const dcServiceMock = {
   checkBindingField: vi.fn().mockReturnValue(false),
};

export const uiContextMock = {
   isVS: vi.fn().mockReturnValue(true),
   isAdhoc: vi.fn().mockReturnValue(false),
   isSqlServer: vi.fn().mockReturnValue(false),
};

/** DimensionEditor.ngOnInit posts here; keep display/interaction TL free of real HTTP. */
export const dateLevelExamplesMock = {
   loadDateLevelExamples: vi.fn(() => of({ dateLevelExamples: {} })),
};

/** createMockBindingRef merges DataRef last and clears classType; restore it for chart refs. */
export function normalizeChartFieldRef(field: { classType?: string; measure?: boolean }): void {
   if(field.measure) {
      field.classType = "aggregate";
   }
   else if(!field.classType || field.classType === "string") {
      field.classType = "dimension";
   }
}

export function resetChartFieldmcMocks(chartModel?: ChartBindingModel): ChartBindingModel {
   const model = chartModel ?? createChartModel();
   bindingServiceMock.bindingModel = model;
   bindingServiceMock.getBindingModel.mockReturnValue(model);
   bindingServiceMock.variableValues = [];
   bindingServiceMock.getURLParams.mockClear();
   editorServiceMock.changeChartRef.mockClear();
   editorServiceMock.getDNDType.mockImplementation((fieldType: string) => dndTypeMap[fieldType] ?? -1);
   editorServiceMock.getCustomChartTypes.mockReturnValue(of([]));
   editorServiceMock.getChartStyles.mockReturnValue(of({ styles: [], customChartTypes: [] }));
   dndServiceMock.setDragStartStyle.mockClear();
   dcServiceMock.checkBindingField.mockReturnValue(false);
   uiContextMock.isSqlServer.mockReturnValue(false);
   dateLevelExamplesMock.loadDateLevelExamples.mockReturnValue(of({ dateLevelExamples: {} }));
   return model;
}

export async function renderChartFieldmc(props: Record<string, any> = {}) {
   const model = resetChartFieldmcMocks(props["_model"] as ChartBindingModel | undefined);
   const renderProps = { ...props };
   delete renderProps["_model"];

   if(renderProps.field) {
      normalizeChartFieldRef(renderProps.field);
   }
   for(const ref of [...model.xfields, ...model.yfields]) {
      normalizeChartFieldRef(ref);
   }

   const { fixture, container } = await render(ChartFieldmc, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: BindingService, useValue: bindingServiceMock },
         { provide: ChartEditorService, useValue: editorServiceMock },
         { provide: DndService, useValue: dndServiceMock },
         { provide: NgbModal, useValue: modalMock },
         { provide: DateComparisonService, useValue: dcServiceMock },
         { provide: DateLevelExamplesService, useValue: dateLevelExamplesMock },
         { provide: UIContextService, useValue: uiContextMock },
      ],
      componentProperties: renderProps,
   });

   const comp = fixture.componentInstance as ChartFieldmc;
   comp.dropdown = { close: vi.fn() } as any;

   return { fixture, container, comp, model };
}

export function chartFieldCombo(container: HTMLElement): HTMLElement | null {
   return container.querySelector('[data-test="chart-field-value-dropdown"]');
}

export function chartFieldComboLabel(container: HTMLElement): string | null {
   return chartFieldCombo(container)?.getAttribute("ng-reflect-label") ?? null;
}

export function chartFieldComboValue(container: HTMLElement): string | null {
   return chartFieldCombo(container)?.getAttribute("ng-reflect-value") ?? null;
}

export function chartFieldEditIcon(container: HTMLElement): HTMLElement | null {
   return container.querySelector(".fieldEditIcon i.btn-icon");
}

export function chartTypeButton(container: HTMLElement): HTMLElement | null {
   return container.querySelector("chart-type-button");
}
