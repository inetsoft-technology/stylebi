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
import { TestBed, ComponentFixture, async } from "@angular/core/testing";
import { TestUtils } from "../../../common/test/test-utils";
import { NgModule } from "@angular/core";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { NgModel, FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { DynamicImagePane } from "./dynamic-image-pane.component";
import { DynamicImagePaneModel } from "../../data/vs/dynamic-image-pane-model";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";

let createDImageModel: () => DynamicImagePaneModel = () => {
   return {
      dynamicImageSelected: true,
      dynamicImageValue: null
   };
};

describe("dynamic image pane component unit case", function() {
   let dynamicImagePane: DynamicImagePane;

   beforeEach(() => {
      dynamicImagePane = new DynamicImagePane();
   });

   //Bug #19462
   //Bug #19953
   it("should load right image name and value", () => {
      dynamicImagePane.model = createDImageModel();
      dynamicImagePane.model.dynamicImageValue = "^UPLOADED^1.png";

      expect(dynamicImagePane.imageName).toBe("1.png");

      dynamicImagePane.updateImageValue(ComboMode.EXPRESSION);
      expect(dynamicImagePane.model.dynamicImageValue).toBe("=");
   });
});