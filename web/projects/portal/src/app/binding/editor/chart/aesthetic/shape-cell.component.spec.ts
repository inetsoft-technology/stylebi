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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import * as V from "../../../../common/data/visual-frame-model";
import { ShapeCell } from "./shape-cell.component";

describe("ShapeCell", () => {
   let fixture: ComponentFixture<ShapeCell>;

   function render(frame: V.VisualFrameModel, isMixed: boolean = false): HTMLElement {
      fixture = TestBed.createComponent(ShapeCell);
      fixture.componentInstance.frameModel = frame;
      fixture.componentInstance.isMixed = isMixed;
      fixture.detectChanges();
      return fixture.nativeElement;
   }

   beforeEach(() => TestBed.configureTestingModule({}));

   it("draws a from-data shape as a triangle, circle and square, with no frame of its own", () => {
      const el = render(new V.CategoricalShapeModel());
      const glyph = el.querySelector("svg.shape-cell__glyph");

      expect(glyph).not.toBeNull();
      expect(glyph.querySelector("polygon")).not.toBeNull();
      expect(glyph.querySelector("circle")).not.toBeNull();
      expect(glyph.querySelector("rect")).not.toBeNull();
      // the tile's border is the only frame: nothing is stroked inside it
      expect(glyph.querySelector("[stroke]")).toBeNull();
   });

   it("draws from-data textures and lines without an inner frame", () => {
      const texture = render(new V.CategoricalTextureModel());
      expect(texture.querySelectorAll("svg.shape-cell__glyph rect").length).toBe(9);
      expect(texture.querySelector("svg.shape-cell__glyph [stroke]")).toBeNull();

      const line = render(new V.CategoricalLineModel());
      expect(line.querySelectorAll("svg.shape-cell__glyph rect").length).toBe(15);
      expect(line.querySelector("svg.shape-cell__glyph [stroke]")).toBeNull();
   });

   it("names the state in the tooltip", () => {
      expect(render(new V.StaticShapeModel()).firstElementChild.getAttribute("title"))
         .toBe("_#(js:Edit Shape) (_#(js:Static shape))");
      expect(render(new V.StaticShapeModel(), true).firstElementChild.getAttribute("title"))
         .toBe("_#(js:Edit Shape) (_#(js:Mixed shapes))");
      expect(render(new V.CategoricalShapeModel()).firstElementChild.getAttribute("title"))
         .toBe("_#(js:Edit Shape) (_#(js:Shape from data))");
      expect(render(new V.CategoricalTextureModel()).firstElementChild.getAttribute("title"))
         .toBe("_#(js:Edit Shape) (_#(js:Texture from data))");
      expect(render(new V.StaticLineModel()).firstElementChild.getAttribute("title"))
         .toBe("_#(js:Edit Shape) (_#(js:Static line))");
      expect(render(new V.CategoricalLineModel()).firstElementChild.getAttribute("title"))
         .toBe("_#(js:Edit Shape) (_#(js:Line from data))");
   });
});
