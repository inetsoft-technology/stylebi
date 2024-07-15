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
import { ColorSlider } from "./color-slider.component";

describe("ColorSlider Unit Tests", () => {
   let component: ColorSlider;

   beforeEach(() => {
      component = new ColorSlider();
      component.height = 360;
   });

   it("Indicator position is calculated correctly from template bound hue", () => {
      component.hue = 0;
      expect(component.indicatorTop).toEqual("0%");

      component.hue = 50;
      expect(component.indicatorTop).toEqual("50%");

      component.hue = 100;
      expect(component.indicatorTop).toEqual("100%");
   });

   it("Hue is emitted correctly from mouse click", (done) => {
      let counter: number = 0;
      let values: number[] = [0, 50, 100];
      component.hueChanged.subscribe((value: number) => {
         expect(value).toEqual(values[counter++]);

         if(counter == values.length) {
            done();
         }
      });

      component.setHuePosition({offsetY: 0});
      component.setHuePosition({offsetY: 180});
      component.setHuePosition({offsetY: 360});
   });
});
