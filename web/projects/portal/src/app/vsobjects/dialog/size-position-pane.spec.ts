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
import { By } from "@angular/platform-browser";
import { SizePositionPaneModel } from "../model/size-position-pane-model";
import { NumberStepperComponent } from "../../widget/number-stepper/number-stepper.component";
import { SizePositionPane } from "./size-position-pane.component";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";

describe("size position pane unit case", () => {
   let fixture: ComponentFixture<SizePositionPane>;
   let sizePosiitonPane: SizePositionPane;
   let model: SizePositionPaneModel;
   let createModel: (overrides?: Partial<SizePositionPaneModel>) => SizePositionPaneModel = (overrides = {}) => {
      return Object.assign({
         top: 46,
         left: 157,
         width: 210,
         height: 216,
         container: false,
         titleHeight: null,
         cellHeight: null,
         locked: false,
         scaleVertical: false
      }, overrides) as SizePositionPaneModel;
   };

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, SizePositionPane]
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
      let errors = fixture.debugElement.queryAll(By.css("div.shell-alert--danger"));
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

   it("should not render a follow-density checkbox when the model does not offer one", () => {
      fixture.componentInstance.model = createModel({titleHeight: 20});
      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css("#titleHeightFollowsDensity"))).toBeNull();
   });

   it("should render the checkbox and disable the stepper while following", () => {
      fixture.componentInstance.model = createModel({titleHeight: 26, titleHeightFollowsDensity: true});
      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css("#titleHeightFollowsDensity"))).not.toBeNull();
      expect(fixture.componentInstance.form.controls["titleHeight"].disabled).toBeTruthy();
   });

   it("should keep the displayed height when the checkbox is cleared", () => {
      fixture.componentInstance.model = createModel({titleHeight: 26, titleHeightFollowsDensity: true});
      fixture.detectChanges();

      fixture.componentInstance.titleHeightFollowChanged(false);
      fixture.detectChanges();

      expect(fixture.componentInstance.model.titleHeightFollowsDensity).toBeFalsy();
      expect(fixture.componentInstance.model.titleHeight).toBe(26);
      expect(fixture.componentInstance.form.controls["titleHeight"].disabled).toBeFalsy();
   });

   it("should keep the stepper disabled when titleHeightEnable is toggled back on while following", () => {
      fixture.componentInstance.model = createModel({titleHeight: 26, titleHeightFollowsDensity: true});
      fixture.componentInstance.titleHeightEnable = true;
      fixture.detectChanges();
      expect(fixture.componentInstance.form.controls["titleHeight"].disabled).toBeTruthy();

      fixture.componentInstance.titleHeightEnable = false;
      fixture.componentInstance.titleHeightEnable = true;

      expect(fixture.componentInstance.form.controls["titleHeight"].disabled).toBeTruthy();
   });
});

