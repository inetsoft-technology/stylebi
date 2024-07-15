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
import { ImageScalePaneModel } from "../../data/vs/image-scale-pane-model";
import { ImageScalePane } from "./image-scale-pane.component";
import { async, TestBed, ComponentFixture } from "@angular/core/testing";
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

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule
         ],
         declarations: [
            ImageScalePane
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

      let topField = fixture.nativeElement.querySelector("input#top");
      let leftField = fixture.nativeElement.querySelector("input#left");
      let bottomField = fixture.nativeElement.querySelector("input#bottom");
      let rightField = fixture.nativeElement.querySelector("input#right");

      expect(topField.getAttribute("ng-reflect-is-disabled")).toBe("true");
      expect(leftField.getAttribute("ng-reflect-is-disabled")).toBe("true");
      expect(bottomField.getAttribute("ng-reflect-is-disabled")).toBe("true");
      expect(rightField.getAttribute("ng-reflect-is-disabled")).toBe("true");

      scalePane.animateGif = false;
      fixture.detectChanges();
      expect(topField.getAttribute("ng-reflect-is-disabled")).toBe("false");
      expect(leftField.getAttribute("ng-reflect-is-disabled")).toBe("false");
      expect(bottomField.getAttribute("ng-reflect-is-disabled")).toBe("false");
      expect(rightField.getAttribute("ng-reflect-is-disabled")).toBe("false");
   });
});