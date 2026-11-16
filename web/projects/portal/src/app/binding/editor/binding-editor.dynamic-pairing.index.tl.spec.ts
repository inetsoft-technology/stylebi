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
 * The position in a binding name is the REAL `@for ... track $index` of the data editors, not a
 * literal handed to a pane (bug #80459, PR C review finding 3). The two editors are rendered with
 * three fields; in DOM order each field's Expression editor must carry the name of its position,
 * and after the `refs` array is reordered the names stay on the positions while the fields move.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { DndService } from "../../common/dnd/dnd.service";
import { DateLevelExamplesService } from "../../common/services/date-level-examples.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { TestUtils } from "../../common/test/test-utils";
import { DateComparisonService } from "../../vsobjects/util/date-comparison.service";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { BindingService } from "../services/binding.service";
import { ChartEditorService } from "../services/chart/chart-editor.service";
import { TableEditorService } from "../services/table/table-editor.service";
import { ChartDataEditor } from "./chart/chart-data-editor.component";
import {
   bindingServiceMock as chartBinding, dateLevelExamplesMock, dcServiceMock as chartDc,
   dndServiceMock as chartDnd, editorServiceMock as chartEditor, modalMock as chartModal,
   normalizeChartFieldRef, resetChartFieldmcMocks, uiContextMock as chartUi
} from "./chart/field/chart-fieldmc.component.test-helpers";
import { TableDataEditor } from "./table/table-data-editor.component";
import {
   bindingServiceMock as tableBinding, createCrosstabModel, dcServiceMock as tableDc,
   dndServiceMock as tableDnd, editorServiceMock as tableEditor, modalMock as tableModal,
   normalizeTableField, resetTableFieldmcMocks, uiContextMock as tableUi
} from "./table/table-fieldmc.component.test-helpers";

/** [name, tooltip] of every dynamic combo, in DOM order. */
function comboNames(fixture: any): [string, string][] {
   return fixture.debugElement.queryAll(By.directive(DynamicComboBox))
      .map((d: any) => [d.componentInstance.dynamicProperty, d.componentInstance.tooltip]);
}

afterEach(() => vi.restoreAllMocks());

describe("chart-data-editor supplies the shelf position through its own $index", () => {
   function chartFields(): any[] {
      return ["a", "b", "c"].map(n => {
         const f = TestUtils.createMockChartDimensionRef(n);
         f.view = "view-" + n;
         f.columnValue = n;
         normalizeChartFieldRef(f);
         return f;
      });
   }

   async function renderEditor(fieldType: string, refs: any[]) {
      const model = resetChartFieldmcMocks();
      chartBinding.objectType = "VSChart";
      return render(ChartDataEditor, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: BindingService, useValue: chartBinding },
            { provide: ChartEditorService, useValue: chartEditor },
            { provide: DndService, useValue: chartDnd },
            { provide: NgbModal, useValue: chartModal },
            { provide: DateComparisonService, useValue: chartDc },
            { provide: UIContextService, useValue: chartUi },
            { provide: DateLevelExamplesService, useValue: dateLevelExamplesMock },
         ],
         componentProperties: { fieldType, refs, bindingModel: model, grayedOutValues: [] },
      });
   }

   it.each([["xfields", "x"], ["yfields", "y"], ["groupfields", "group"]])(
      "%s: names follow the DOM position, also after the refs are reordered",
      async (fieldType, shelf) => {
         const [a, b, c] = chartFields();
         const { fixture } = await renderEditor(fieldType, [a, b, c]);
         fixture.detectChanges();

         expect(comboNames(fixture)).toEqual([
            [`binding:${shelf}[0].column`, "view-a"],
            [`binding:${shelf}[1].column`, "view-b"],
            [`binding:${shelf}[2].column`, "view-c"]]);

         fixture.componentRef.setInput("refs", [c, a, b]);
         fixture.detectChanges();

         expect(comboNames(fixture)).toEqual([
            [`binding:${shelf}[0].column`, "view-c"],
            [`binding:${shelf}[1].column`, "view-a"],
            [`binding:${shelf}[2].column`, "view-b"]]);
      });
});

describe("table-data-editor supplies the crosstab position through its own $index", () => {
   function tableFields(aggregate: boolean): any[] {
      return ["a", "b", "c"].map(n => {
         const f: any = aggregate ? TestUtils.createMockBAggregateRef(n)
            : TestUtils.createMockBDimensionRef(n);
         f.view = "view-" + n;
         return normalizeTableField(f);
      });
   }

   async function renderEditor(fieldType: string, refs: any[]) {
      const model = createCrosstabModel();
      resetTableFieldmcMocks(model as any);
      tableBinding.objectType = "VSCrosstab";
      return render(TableDataEditor, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: BindingService, useValue: tableBinding },
            { provide: TableEditorService, useValue: tableEditor },
            { provide: DndService, useValue: tableDnd },
            { provide: NgbModal, useValue: tableModal },
            { provide: DateComparisonService, useValue: tableDc },
            { provide: UIContextService, useValue: tableUi },
         ],
         componentProperties: {
            fieldType, refs, groupNum: 1, bindingModel: model as any, grayedOutValues: []
         },
      });
   }

   afterEach(() => {
      tableBinding.objectType = "vstable";
   });

   it.each([["rows", false], ["cols", false], ["aggregates", true]])(
      "%s: names follow the DOM position, also after the refs are reordered",
      async (fieldType, aggregate) => {
         const [a, b, c] = tableFields(aggregate);
         const { fixture } = await renderEditor(fieldType, [a, b, c]);
         fixture.detectChanges();

         expect(comboNames(fixture)).toEqual([
            [`binding:${fieldType}[0].column`, "view-a"],
            [`binding:${fieldType}[1].column`, "view-b"],
            [`binding:${fieldType}[2].column`, "view-c"]]);

         fixture.componentRef.setInput("refs", [c, a, b]);
         fixture.detectChanges();

         expect(comboNames(fixture)).toEqual([
            [`binding:${fieldType}[0].column`, "view-c"],
            [`binding:${fieldType}[1].column`, "view-a"],
            [`binding:${fieldType}[2].column`, "view-b"]]);
      });
});
