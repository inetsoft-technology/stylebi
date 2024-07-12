/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { GraphTypes } from "../../common/graph-types";
import { UIContextService } from "../../common/services/ui-context.service";
import { ChartStylePane } from "./chart-style-pane.component";
import { ModelService } from "../../widget/services/model.service";
import { FeatureFlagsService } from "../../../../../shared/feature-flags/feature-flags.service";

describe("chart style pane component unit case", () => {
   let fixture: ComponentFixture<ChartStylePane>;
   let stylePane: ChartStylePane;
   let uiContextService: any;
   let modelService: any = { getModel: jest.fn() };
   let featureFlagService = { isFeatureEnabled: jest.fn() };

   beforeEach(() => {
      uiContextService = { isAdhoc: jest.fn() };
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [ChartStylePane],
         providers: [
            {provide: UIContextService, useValue: uiContextService},
            {provide: ModelService, useValue: modelService},
            {provide: FeatureFlagsService, useValue: featureFlagService}
         ]
      }).compileComponents();

      fixture = TestBed.createComponent(ChartStylePane);
      stylePane = <ChartStylePane>fixture.componentInstance;
      fixture.detectChanges();
   });

   //Bug #19389 and Bug #19135
   it("stack button status check", () => {
      stylePane.multiStyles = true;
      stylePane.chartType = GraphTypes.CHART_BAR;
      stylePane.refName = "Sum(id)";
      fixture.detectChanges();
      expect(stylePane.stackEnabled()).toBeTruthy();

      stylePane.chartType = GraphTypes.CHART_PIE;
       fixture.detectChanges();
      expect(stylePane.stackEnabled()).toBeFalsy();
   });
});