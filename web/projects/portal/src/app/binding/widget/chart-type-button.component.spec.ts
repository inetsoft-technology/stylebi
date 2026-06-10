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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";
import { GraphTypes } from "../../common/graph-types";
import { ChartEditorService } from "../services/chart/chart-editor.service";
import { ChartTypeButton } from "./chart-type-button.component";

describe("chart type button component unit case", () => {
   let fixture: ComponentFixture<ChartTypeButton>;
   let button: ChartTypeButton;
   let editorService: any;

   beforeEach(() => {
      editorService = {
         getCustomChartTypes: vi.fn(() => of([])),
         getChartStyles: vi.fn(() => of({ styles: [], stackStyles: [] })),
         changeChartType: vi.fn(),
         bindingModel: {
            chartType: GraphTypes.CHART_BAR_STACK,
            multiStyles: false,
            stackMeasures: false,
            separated: true,
            xfields: [],
            yfields: [],
            geoFields: [],
            groupFields: []
         }
      };

      TestBed.configureTestingModule({
         imports: [ChartTypeButton],
         providers: [
            { provide: ChartEditorService, useValue: editorService },
            { provide: NgbModal, useValue: { open: vi.fn() } }
         ]
      }).compileComponents();

      fixture = TestBed.createComponent(ChartTypeButton);
      button = fixture.componentInstance;
   });

   // Bug: disabling stack created duplicate bindings because closing the dropdown
   // re-fired openChange -> toggled -> changeChartType, sending the event twice and
   // racing on the shared chart info in the backend.
   it("sends changeChartType only once when closing re-enters toggled", () => {
      button.chartType = GraphTypes.CHART_BAR;
      button.multiStyles = false;
      button.stackMeasures = false;
      button.refName = undefined;
      // simulate the dropdown close synchronously re-triggering the close handler
      button.dropdown = { close: vi.fn(() => button.toggled(false)) } as any;

      button.changeChartType();

      expect(editorService.changeChartType).toHaveBeenCalledTimes(1);
   });
});
