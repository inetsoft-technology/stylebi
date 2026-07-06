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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { render } from "@testing-library/angular";
import { Observable, of } from "rxjs";

import { IntervalPaneModel } from "../../model/interval-pane-model";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { FirstDayOfWeekModel, FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { DynamicValueEditorComponent } from "../../../widget/date-type-editor/dynamic-value-editor.component";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { DateComparisonIntervalPaneComponent } from "./date-comparison-interval-pane.component";

// ---------------------------------------------------------------------------
// Child component stubs
// ---------------------------------------------------------------------------
// The template unconditionally renders one dynamic-combo-box (granularity) and,
// depending on isCustomPeriod/showEndDate(), up to three more real child
// components. Stubbing them avoids pulling in their own dependency trees while
// still letting Angular bind every @Input/@Output the template references.

@Component({
   selector: "dynamic-combo-box",
   standalone: true,
   template: "",
})
export class DynamicComboBoxStub {
   @Input() values: any[];
   @Input() value: any;
   @Input() variables: any[];
   @Input() disable = false;
   @Input() isCondition = false;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Output() valueChange = new EventEmitter<any>();
   @Output() typeChange = new EventEmitter<number>();
}

@Component({
   selector: "dynamic-value-editor",
   standalone: true,
   template: "",
})
export class DynamicValueEditorStub {
   @Input() valueModel: DynamicValueModel;
   @Input() disable = false;
   @Input() isInterval = false;
   @Input() variableValues: string[] = [];
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Input() defaultValue: string;
   @Input() forceToDefault = false;
   @Input() label: string;
   @Output() onValueModelChange = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Model fixtures
// ---------------------------------------------------------------------------

export function makeDynamicValue(value: any, type: string = ValueTypes.VALUE): DynamicValueModel {
   return { type, value };
}

export function makeIntervalPaneModel(
   overrides: Partial<IntervalPaneModel> = {}
): IntervalPaneModel {
   return {
      level: makeDynamicValue("48"), // IntervalLevel.YEAR_TO_DATE
      granularity: makeDynamicValue("4"), // IntervalLevel.MONTH
      endDayAsToDate: false,
      intervalEndDate: makeDynamicValue("2024-01-15"),
      inclusive: false,
      contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP),
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// renderComponent
// ---------------------------------------------------------------------------

export interface RenderOptions {
   intervalPaneModel?: IntervalPaneModel;
   variableValues?: string[];
   disable?: boolean;
   standardPeriodLevel?: DynamicValueModel;
   periodEndDay?: DynamicValueModel;
   isCustomPeriod?: boolean;
   firstDayOfWeekService?: { getFirstDay: () => Observable<FirstDayOfWeekModel> };
}

export async function renderComponent(opts: RenderOptions = {}) {
   const firstDayOfWeekService = opts.firstDayOfWeekService ?? {
      getFirstDay: vi.fn(() => of({ javaFirstDay: 1, isoFirstDay: 1 })),
   };

   const { fixture } = await render(DateComparisonIntervalPaneComponent, {
      providers: [
         { provide: FirstDayOfWeekService, useValue: firstDayOfWeekService },
      ],
      componentInputs: {
         intervalPaneModel: opts.intervalPaneModel ?? makeIntervalPaneModel(),
         variableValues: opts.variableValues ?? [],
         disable: opts.disable ?? false,
         standardPeriodLevel: opts.standardPeriodLevel ?? makeDynamicValue(XConstants.YEAR_DATE_GROUP),
         // Use an explicit undefined check (not ??) so callers can intentionally pass periodEndDay: null.
         periodEndDay: opts.periodEndDay !== undefined ? opts.periodEndDay : makeDynamicValue(""),
         isCustomPeriod: opts.isCustomPeriod ?? false,
      },
      importOverrides: [
         { replace: DynamicComboBox, with: DynamicComboBoxStub },
         { replace: DynamicValueEditorComponent, with: DynamicValueEditorStub },
      ],
   });

   return {
      fixture,
      comp: fixture.componentInstance as DateComparisonIntervalPaneComponent,
      firstDayOfWeekService,
   };
}
