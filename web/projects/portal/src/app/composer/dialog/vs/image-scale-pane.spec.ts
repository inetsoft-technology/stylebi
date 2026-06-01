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
import { ImageScalePaneModel } from "../../data/vs/image-scale-pane-model";
import { NumberStepperComponent } from "../../../widget/number-stepper/number-stepper.component";
import { ImageScalePane } from "./image-scale-pane.component";
import { waitForAsync, TestBed, ComponentFixture } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";

let createModel: () => ImageScalePaneModel = () => {
   return {
      scaleImageChecked: false,
      maintainAspectRatio: null,
      animateGif: false,
      tile: false,
      top: 0,
      bottom: 0,
      left: 0,
      right: 0,
      objectHeight: 0,
      objectWidth: 0
   };
};

describe("Image Scale Pane Test", () => {
   let fixture: ComponentFixture<ImageScalePane>;
   let scalePane: ImageScalePane;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ImageScalePane,
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ImageScalePane);
      scalePane = <ImageScalePane> fixture.componentInstance;
      scalePane.model = createModel();
   }));

   // Bug #17257 The 'Top/Bottom/Left/Right' property should disable when set 'Animate GIF Image' property is true
   it("should disable top/buttom/left/right when animate gif image", () => {
      scalePane.animateGif = true;
      scalePane.model.scaleImageChecked = true;
      scalePane.model.maintainAspectRatio = false;
      fixture.detectChanges();

      let steppers = fixture.nativeElement.querySelectorAll("number-stepper");
      let topField = steppers[0];
      let leftField = steppers[1];
      let bottomField = steppers[2];
      let rightField = steppers[3];

      expect(topField.getAttribute("ng-reflect-disabled")).toBe("true");
      expect(leftField.getAttribute("ng-reflect-disabled")).toBe("true");
      expect(bottomField.getAttribute("ng-reflect-disabled")).toBe("true");
      expect(rightField.getAttribute("ng-reflect-disabled")).toBe("true");

      scalePane.animateGif = false;
      fixture.detectChanges();
      expect(topField.getAttribute("ng-reflect-disabled")).toBe("false");
      expect(leftField.getAttribute("ng-reflect-disabled")).toBe("false");
      expect(bottomField.getAttribute("ng-reflect-disabled")).toBe("false");
      expect(rightField.getAttribute("ng-reflect-disabled")).toBe("false");
   });
});