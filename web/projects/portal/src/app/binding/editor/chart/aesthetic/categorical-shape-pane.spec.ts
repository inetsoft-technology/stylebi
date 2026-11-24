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
import { StyleConstants } from "../../../../common/util/style-constants";
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
            ReactiveFormsModule,
            FormsModule,
            NgbModule,
            CategoricalShapePane,
            TextureComboBox,
            TextureItem,
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

   it("reset gives the filled list on a marked chart", () => {
      shapePane = new CategoricalShapePane();
      shapePane.bindingModel = <any> {vizModern: true};
      let model: CategoricalShapeModel = mockCategoricalShapeModel();
      shapePane.frameModel = model;
      shapePane.reset();
      expect(model.shapes)
         .toEqual(ChartConfig.MODERN_SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES));
      expect(model.shapes[0]).toEqual(StyleConstants.FILLED_CIRCLE + "");
      expect(model.shapes.length).toEqual(32);
   });

   it("reset keeps the classic list when the mark is false or unknown", () => {
      for(let binding of [<any> {vizModern: false}, <any> {}, null, undefined]) {
         shapePane = new CategoricalShapePane();
         shapePane.bindingModel = binding;
         let model: CategoricalShapeModel = mockCategoricalShapeModel();
         shapePane.frameModel = model;
         shapePane.reset();
         expect(model.shapes).toEqual(ChartConfig.SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES));
      }
   });

   it("the filled list reorders the first eleven shapes and keeps the rest", () => {
      let classic = ChartConfig.SHAPE_STYLES;
      let modern = ChartConfig.MODERN_SHAPE_STYLES;
      expect(modern.length).toEqual(classic.length);
      expect(modern.slice(0, 11).slice().sort()).toEqual(classic.slice(0, 11).slice().sort());
      expect(modern.slice(11)).toEqual(classic.slice(11));
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

   describe("pop-up chrome", () => {
      // i18n keys are not resolved in unit tests
      const plain = (s: string) => (s || "").replace(/_#\((.*?)\)/g, "$1");

      function create(model: any): void {
         configureTestEnv();
         fixture = TestBed.createComponent(CategoricalShapePane);
         shapePane = <CategoricalShapePane>fixture.componentInstance;
         shapePane.frameModel = model;
         fixture.detectChanges();
      }

      const pager = (i: number) =>
         fixture.nativeElement.querySelectorAll(".sp-slots > .sp-btn")[i] as HTMLButtonElement;

      it("should disable Previous on the first page and Next on the last page", () => {
         create(mockCategoricalTextureModel());
         expect(pager(0).disabled).toBe(true);
         expect(pager(1).disabled).toBe(false);

         shapePane.showNext();
         shapePane.showNext();
         shapePane.showNext();
         fixture.detectChanges();
         expect(shapePane.currIndex).toBe(3);
         expect(pager(0).disabled).toBe(false);
         expect(pager(1).disabled).toBe(true);
      });

      it("should disable Next and keep the start index with fewer than eight items", () => {
         let model = mockCategoricalShapeModel();
         model.shapes = ["909", "910", "902"];
         create(model);
         expect(pager(1).disabled).toBe(true);
         expect(shapePane.currIndex).toBe(0);
      });

      it("should number the slots by page and label the shape slots", () => {
         let model = mockCategoricalShapeModel();
         model.shapes = model.shapes.concat(["909", "910", "902", "903"]);
         create(model);
         const numbers = () => Array.from<HTMLElement>(
            fixture.nativeElement.querySelectorAll(".sp-slot__number"))
            .map(e => e.textContent.trim());
         expect(numbers()).toEqual(["1", "2", "3", "4", "5", "6", "7", "8"]);

         const slots = fixture.nativeElement.querySelectorAll("shape-combo-box button.sp-btn");
         expect(slots.length).toBe(8);
         expect(plain(slots[0].getAttribute("aria-label"))).toBe("Shape 1");
         expect(plain(slots[7].getAttribute("aria-label"))).toBe("Shape 8");
         expect(slots[0].getAttribute("aria-haspopup")).toBe("true");

         pager(1).click();
         fixture.detectChanges();
         expect(numbers()[0]).toBe("2");
         expect(plain(fixture.nativeElement.querySelector("shape-combo-box button.sp-btn")
            .getAttribute("aria-label"))).toBe("Shape 2");
      });

      it("should show the shape hint", () => {
         create(mockCategoricalShapeModel());
         expect(plain(fixture.nativeElement.querySelector(".sp-hint").textContent.trim()))
            .toBe("Shapes are assigned to the values in order.");
      });

      it("should show the texture hint", () => {
         create(mockCategoricalTextureModel());
         expect(plain(fixture.nativeElement.querySelector(".sp-hint").textContent.trim()))
            .toBe("Textures are assigned to the values in order.");
      });

      it("should wire the footer: Apply emits once, Reset only resets in memory", () => {
         let model = mockCategoricalShapeModel();
         create(model);
         let applied: any[] = [];
         shapePane.apply.subscribe(v => applied.push(v));
         const buttons = fixture.nativeElement.querySelectorAll(".sp-footer button");
         expect(buttons.length).toBe(2);
         expect(plain(buttons[1].getAttribute("title"))).toBe("Apply");

         buttons[0].click();
         expect(applied.length).toBe(0);
         expect(model.shapes).toEqual(ChartConfig.SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES));

         buttons[1].click();
         expect(applied).toEqual([false]);
      });
   });
});