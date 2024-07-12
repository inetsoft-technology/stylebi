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
import { By } from "@angular/platform-browser";
import { SizePositionPaneModel } from "../model/size-position-pane-model";
import { SizePositionPane } from "./size-position-pane.component";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";

describe("size position pane unit case", () => {
   let fixture: ComponentFixture<SizePositionPane>;
   let sizePosiitonPane: SizePositionPane;
   let model: SizePositionPaneModel;
   let createModel: () => SizePositionPaneModel = () => {
      return {
         top: 46,
         left: 157,
         width: 210,
         height: 216,
         container: false,
         titleHeight: null,
         cellHeight: null,
         locked: false,
         scaleVertical: false
      };
   };

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ ReactiveFormsModule, FormsModule, NgbModule ],
         declarations: [SizePositionPane]
      }).compileComponents();

      fixture = TestBed.createComponent(SizePositionPane);
      sizePosiitonPane = <SizePositionPane>fixture.componentInstance;
      model = createModel();
   });

   //Bug #18453 size position pane validator.
   it("check the size position pane valid", () => {
      sizePosiitonPane.model = model;
      fixture.detectChanges();
      expect(sizePosiitonPane.form.valid).toBeTruthy();
   });

   //Bug #18354
   it("postion size value valid check", () => {
      model.height = 22.5;
      model.left = 10.5;
      model.width = 23.6;
      model.top = 10.5;
      sizePosiitonPane.model = model;
      fixture.detectChanges();
      let errors = fixture.debugElement.queryAll(By.css("div.alert-danger"));
      expect(errors[0].nativeElement.textContent).toContain("viewer.viewsheet.layout.topValid");
      expect(errors[1].nativeElement.textContent).toContain("viewer.viewsheet.layout.leftValid");
      expect(errors[2].nativeElement.textContent).toContain("viewer.viewsheet.layout.widthValid");
      expect(errors[3].nativeElement.textContent).toContain("viewer.viewsheet.layout.heightValid");
   });

   //Bug #18804
   it("layout pane of image and text should be disable on layout", () => {
      sizePosiitonPane.layoutObject = true;
      sizePosiitonPane.model = model;
      fixture.detectChanges();
      expect(sizePosiitonPane.layoutEnabled).toBeFalsy();

      fixture.debugElement.queryAll(By.css("fieldset input")).forEach(input => {
         expect(input.nativeElement.disabled).toBeTruthy();
      });
   });
});

