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
import { GraphTypes } from "../../common/graph-types";
import { TestUtils } from "../../common/test/test-utils";
import { LabelInputField } from "./label-input-field.component";
import { StatPanel } from "./stat-panel.component";

describe("stat panel component unit case", () => {
   let fixture: ComponentFixture<StatPanel>;
   let statPanel: StatPanel;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [
            StatPanel, LabelInputField
         ],
         providers: [],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(StatPanel);
      statPanel = <StatPanel>fixture.componentInstance;
      statPanel.model = TestUtils.createMockTargetInfo();
   });

   //Bug #19596, Bug #19654 alpha should be enable for 3Dbar
   it("alpha should be enable for 3Dbar", () => {
      statPanel.chartType = GraphTypes.CHART_3D_BAR;
      fixture.detectChanges();
      expect(statPanel.alphaEnabled).toBeTruthy();
   });

   //Bug #19651
   it("should not load date column when group all other togetther is true", () => {
      statPanel.availableFields = [
         {name: "", label: "", groupOthers: false, dateField: false},
         {name: "Sum(id)", label: "Sum(id)", groupOthers: false, dateField: false},
         {name: "Year(date)", label: "Year(date)", groupOthers: true, dateField: true}];
      fixture.detectChanges();
      let fileds = fixture.nativeElement.querySelectorAll("select option");
      expect(fileds.length).toBe(2);
      expect(fileds[0].textContent).toContain("");
      expect(fileds[1].textContent).toContain("Sum(id)");
   });

   //Bug #20026 Use target value by default when statistics target line label is empty
   xit("Use target value by default when statistics target line label is empty", () => {
      statPanel.availableFields = [
         {name: "", label: "", groupOthers: false, dateField: false},
         {name: "Sum(id)", label: "Sum(id)", groupOthers: false, dateField: false},
         {name: "Year(date)", label: "Year(date)", groupOthers: false, dateField: true}];
      fixture.detectChanges();

      let labelInput = fixture.debugElement.query(By.css("div.label-input-field input")).nativeElement;
      labelInput.value = "";
      labelInput.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      expect(statPanel.model.labelFormats).toBe("{0}");
   });
});