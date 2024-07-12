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
import { Component, NgModule, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, TestBed, ComponentFixture } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { CombinedColorPane } from "./combined-color-pane.component";
import { StaticColorEditor } from "./static-color-editor.component";
import { StaticColorModel } from "../../../../common/data/visual-frame-model";

describe("Combined Color Pane Unit Test", () => {
   let createMockStaticColorModel: (color: string) => StaticColorModel = (color: string) => {
      return {
         clazz: "inetsoft.web.binding.model.graph.aesthetic.StaticColorModel",
         name: null,
         field: null,
         summary: false,
         changed: false,
         color: color,
         cssColor: "",
         defaultColor: "#518db9",
      };
   };

   let fixture: ComponentFixture<CombinedColorPane>;
   let combinedColorPane: CombinedColorPane;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            CombinedColorPane, StaticColorEditor
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   //for Bug #19580, Bug #20376
   it("should display refresh button on combined color pane", () => {
      let colorFrame1 = createMockStaticColorModel("#d84d3f");
      let summaryFrame = createMockStaticColorModel("#FFCC00");
      summaryFrame.summary = true;
      summaryFrame.defaultColor = "#7030a0";
      fixture = TestBed.createComponent(CombinedColorPane);
      combinedColorPane = <CombinedColorPane>fixture.componentInstance;
      combinedColorPane.frameInfos = [{
         frame: colorFrame1,
         name: "Sum(id)"
      },
      {
         frame: summaryFrame,
         summary: true,
         name: "Summary"
      }];
      fixture.detectChanges();

      let resetBtn: Element = fixture.nativeElement.querySelector("i.reset-icon");
      expect(resetBtn).not.toBeNull();

      //Bug #20376
      combinedColorPane.reset();
      expect(combinedColorPane.frameInfos[0].frame.color).toEqual("#518db9");
      expect(combinedColorPane.frameInfos[1].frame.color).toEqual("#7030a0");
   });
});
