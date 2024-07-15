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

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../common/test/test-utils";
import { BandPanel } from "./band-panel.component";
import { GraphTypes } from "../../common/graph-types";

describe("band panel component unit case", () => {
   let fixture: ComponentFixture<BandPanel>;
   let bandPanel: BandPanel;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [BandPanel],
         providers: [],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(BandPanel);
      bandPanel = <BandPanel>fixture.componentInstance;
      bandPanel.model = TestUtils.createMockTargetInfo();
   });

   //Bug #18988 should enable Entire Chart options
   //Bug #19296 should disable Entire Chart for Expression value mode
   it("should enable Entire Chart options", () => {
      bandPanel.model.measure = {
         dateField: false,
         label: "Sum(id)",
         name : "Sum(id)"
      };
      bandPanel.model.value = "12";
      bandPanel.model.toValue = "Average";

      fixture.detectChanges();
      let entrieChart = fixture.debugElement.query(By.css(".entriChart-cb_id")).nativeElement;
      expect(entrieChart.disabled).toBeFalsy();

      //Bug #19296
      bandPanel.model.value = "=";
      bandPanel.model.toValue = "";
      fixture.detectChanges();
      expect(entrieChart.getAttribute("ng-reflect-is-disabled")).toBe("true");
   });

   //Bugg #19596, Bug #19654 alpha should be enable for 3Dbar
   it("alpha should be enable for 3Dbar", () => {
      bandPanel.chartType = GraphTypes.CHART_3D_BAR;
      fixture.detectChanges();
      expect(bandPanel.alphaEnabled).toBeTruthy();
   });

   //Bug #19651
   it("should not load date column when group all other togetther is true", () => {
      bandPanel.availableFields = [
         {name: "", label: "", groupOthers: false, dateField: false},
         {name: "Sum(id)", label: "Sum(id)", groupOthers: false, dateField: false},
         {name: "Year(date)", label: "Year(date)", groupOthers: true, dateField: true}];
      fixture.detectChanges();
      let fileds = fixture.nativeElement.querySelectorAll("select option");
      expect(fileds.length).toBe(2);
      expect(fileds[0].textContent).toContain("");
      expect(fileds[1].textContent).toContain("Sum(id)");
   });
});