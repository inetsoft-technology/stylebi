/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { async, TestBed, ComponentFixture } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { StaticSizePane } from "./static-size-pane.component";
import { Slider } from "../../../widget/slider.component";

describe("Static Size Pane Unit Test", () => {
   let fixture: ComponentFixture<StaticSizePane>;
   let sizePane: StaticSizePane;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            StaticSizePane, Slider
         ]
      }).compileComponents();
   }));

   //Bug #20286 slider min value should be 1
   it("should use correct min value when initialization", () => {
      fixture = TestBed.createComponent(StaticSizePane);
      sizePane = <StaticSizePane>fixture.componentInstance;
      fixture.detectChanges();

      expect(sizePane.sliderOptions.min).toEqual(1);
   });
});