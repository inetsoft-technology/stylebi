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
import { NgbModule, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { CategoricalShapePane } from "./categorical-shape-pane.component";
import { CategoricalTextureModel, CategoricalShapeModel, VisualFrameModel } from "../../../../common/data/visual-frame-model";
import { TextureComboBox } from "./texture-combo-box.component";
import { TextureItem } from "./texture-item.component";
import { ChartConfig } from "../../../../common/util/chart-config";
import { TestUtils } from "../../../../common/test/test-utils";

describe("Categorical Shape Pane Unit Test", () => {
   let mockCategoricalTextureModel: () => CategoricalTextureModel = () => {
      return Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.CategoricalTextureModel",
         textures: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
      }, TestUtils.createMockVisualFrameModel("Employee"));
   };
   let mockCategoricalShapeModel: () => CategoricalShapeModel = () => {
      return Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.CategoricalShapeModel",
         shapes: ["909", "910", "902", "903", "904", "905", "906", "907"]
      }, TestUtils.createMockVisualFrameModel("state"));
   };
   let fixture: ComponentFixture<CategoricalShapePane>;
   let shapePane: CategoricalShapePane;

   function configureTestEnv(): void {
      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule, FormsModule, NgbModule
         ],
         declarations: [
            CategoricalShapePane, TextureComboBox, TextureItem
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }

   //for Bug #19177, Bug #21855
   it("reset can not work for second time", () => {
      //Bug #19177
      shapePane = new CategoricalShapePane();
      let model1: CategoricalTextureModel = mockCategoricalTextureModel();
      shapePane.frameModel = model1;
      shapePane.changeShape(1, 2);
      expect(model1.textures[2]).toEqual(1);

      shapePane.reset();
      expect(model1.textures).toEqual(ChartConfig.TEXTURE_STYLES.slice(1));

      //Bug #21855
      let model2: CategoricalShapeModel = mockCategoricalShapeModel();
      shapePane.frameModel = model2;
      shapePane.reset();
      expect(model2.shapes).toEqual(ChartConfig.SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES));
   });

   //for Bug #19842
   it("should not delete texture shape when show next", () => {
      configureTestEnv();
      fixture = TestBed.createComponent(CategoricalShapePane);
      shapePane = <CategoricalShapePane>fixture.componentInstance;
      let model = mockCategoricalTextureModel();
      shapePane.frameModel = model;
      fixture.detectChanges();

      for(let i = 0; i < model.textures.length - 1; i++) {
         shapePane.showNext();
      }
      fixture.detectChanges();

      let items = fixture.nativeElement.querySelectorAll("texture-combo-box[ng-reflect-texture]");
      expect(items.length).toEqual(8);
   });
});