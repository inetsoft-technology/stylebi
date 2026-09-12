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

import { Component, Directive, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { render } from "@testing-library/angular";

import { VsWizardObjectComponent } from "./vs-wizard-object.component";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { VSCalendar } from "../../../vsobjects/objects/calendar/vs-calendar.component";
import { VSChart } from "../../../vsobjects/objects/chart/vs-chart.component";
import { VSCrosstab } from "../../../vsobjects/objects/table/vs-crosstab.component";
import { VSGauge } from "../../../vsobjects/objects/output/gauge/vs-gauge.component";
import { VSImage } from "../../../vsobjects/objects/output/image/vs-image.component";
import { VSRangeSlider } from "../../../vsobjects/objects/range-slider/vs-range-slider.component";
import { VSSelection } from "../../../vsobjects/objects/selection/vs-selection.component";
import { VSTable } from "../../../vsobjects/objects/table/vs-table.component";
import { VSText } from "../../../vsobjects/objects/output/text/vs-text.component";
import { MiniToolbar } from "../../../vsobjects/objects/mini-toolbar/mini-toolbar.component";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";

// ---------------------------------------------------------------------------
// Stubs for imports that carry deep DI chains
// Each stub must declare every @Input/@Output bound in vs-wizard-object.component.html.
// Unknown property bindings are suppressed by NO_ERRORS_SCHEMA; missing @Output
// declarations cause NG0300 binding errors at compile time, so all template-bound
// outputs must be declared.
// ---------------------------------------------------------------------------

@Directive({ selector: "[wInteractable]", standalone: true })
export class InteractableStub {
   @Input() interactableResizable: boolean;
   @Input() interactableDraggable: boolean;
   @Input() interactableIgnoreFrom: string;
   @Input() resizableMargin: any;
   @Input() resizableTopEdge: any;
   @Input() resizableBottomEdge: any;
   @Input() resizableLeftEdge: any;
   @Input() resizableRightEdge: any;
   @Input() resizableRestriction: any;
   @Output() onResizableStart = new EventEmitter<any>();
   @Output() onResizableEnd = new EventEmitter<any>();
   @Output() onResizableMove = new EventEmitter<any>();
   @Output() onDraggableStart = new EventEmitter<any>();
   @Output() onDraggableMove = new EventEmitter<any>();
   @Output() onDraggableEnd = new EventEmitter<any>();
}

@Component({ selector: "vs-calendar", template: "", standalone: true })
export class VSCalendarStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "vs-chart", template: "", standalone: true })
export class VSChartStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "vs-crosstab", template: "", standalone: true })
export class VSCrosstabStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "vs-gauge", template: "", standalone: true })
export class VSGaugeStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "vs-image", template: "", standalone: true })
export class VSImageStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "vs-range-slider", template: "", standalone: true })
export class VSRangeSliderStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   @Input() viewsheetScale: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "vs-selection", template: "", standalone: true })
export class VSSelectionStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "vs-table", template: "", standalone: true })
export class VSTableStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "vs-text", template: "", standalone: true })
export class VSTextStub {
   @Input() vsInfo: any;
   @Input() model: any;
   @Input() selected: any;
   clearSelection() {}
   resized() {}
}

@Component({ selector: "mini-toolbar", template: "", standalone: true })
export class MiniToolbarStub {
   @Input() miniToolbarActions: any;
   @Input() forceAbove: any;
   @Input() top: any;
   @Input() left: any;
   @Input() width: any;
}

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

export function makeVsObject(objectType: string, overrides: Partial<VSObjectModel> = {}): VSObjectModel {
   return {
      absoluteName: "TestObj",
      objectType,
      objectFormat: { left: 10, top: 10, width: 100, height: 50 } as any,
      hasDynamic: false,
      selectedRegions: [],
      ...overrides,
   } as unknown as VSObjectModel;
}

export function makeViewsheet(vsObjects: VSObjectModel[] = []): Viewsheet {
   const vs = new Viewsheet();
   vs.vsObjects = vsObjects;
   return vs;
}

export interface RenderOptions {
   vsObject?: VSObjectModel;
   viewsheet?: Viewsheet;
   heightIncrement?: number;
   widthIncrement?: number;
   maxHeight?: number;
   maxWidth?: number;
}

export async function renderComponent(opts: RenderOptions = {}) {
   const vsObject = opts.vsObject ?? makeVsObject("VSChart");
   const viewsheet = opts.viewsheet ?? makeViewsheet([vsObject]);

   const result = await render(VsWizardObjectComponent, {
      inputs: {
         vsObject,
         viewsheet,
         heightIncrement: opts.heightIncrement ?? 1,
         widthIncrement: opts.widthIncrement ?? 1,
         maxHeight: opts.maxHeight ?? 1000,
         maxWidth: opts.maxWidth ?? 1000,
      },
      importOverrides: [
         { replace: InteractableDirective, with: InteractableStub },
         { replace: VSCalendar, with: VSCalendarStub },
         { replace: VSChart, with: VSChartStub },
         { replace: VSCrosstab, with: VSCrosstabStub },
         { replace: VSGauge, with: VSGaugeStub },
         { replace: VSImage, with: VSImageStub },
         { replace: VSRangeSlider, with: VSRangeSliderStub },
         { replace: VSSelection, with: VSSelectionStub },
         { replace: VSTable, with: VSTableStub },
         { replace: VSText, with: VSTextStub },
         { replace: MiniToolbar, with: MiniToolbarStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance;
   // @ViewChild("objectComponent") resolves to the stub instance. Spy on the stub's
   // own methods so the spy survives subsequent detectChanges() cycles that re-evaluate
   // the ViewChild query and would overwrite a replaced reference.
   if(comp.objectComponent) {
      vi.spyOn(comp.objectComponent, "resized");
      vi.spyOn(comp.objectComponent, "clearSelection");
   }

   return { comp, fixture: result.fixture, vsObject, viewsheet };
}
