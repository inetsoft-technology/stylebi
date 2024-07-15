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
import { ColorMap } from "./color-map.component";

describe("ColorMap Unit Tests", () => {
   function normalizeHue(h: number) {
      return h * 100 / 360;
   }

   let component: ColorMap;
   const colorTable: {hsv: number[], top: string, left: string, background: string}[] = [
      {hsv: [0, 0, 0], top: "100%", left: "0%", background: "#ff0000"},
      {hsv: [0, 0, 100], top: "0%", left: "0%", background: "#ff0000"},
      {hsv: [0, 100, 100], top: "0%", left: "100%", background: "#ff0000"},
      {hsv: [normalizeHue(120), 100, 100], top: "0%", left: "100%", background: "#00ff00"},
      {hsv: [normalizeHue(240), 100, 100], top: "0%", left: "100%", background: "#0000ff"},
      {hsv: [normalizeHue(60), 100, 100], top: "0%", left: "100%", background: "#ffff00"},
      {hsv: [normalizeHue(180), 100, 100], top: "0%", left: "100%", background: "#00ffff"},
      {hsv: [normalizeHue(300), 100, 100], top: "0%", left: "100%", background: "#ff00ff"},
      {hsv: [0, 0, 75], top: "25%", left: "0%", background: "#ff0000"},
      {hsv: [0, 0, 50], top: "50%", left: "0%", background: "#ff0000"},
      {hsv: [0, 100, 50], top: "50%", left: "100%", background: "#ff0000"},
      {hsv: [normalizeHue(60), 100, 50], top: "50%", left: "100%", background: "#ffff00"},
      {hsv: [normalizeHue(120), 100, 50], top: "50%", left: "100%", background: "#00ff00"},
      {hsv: [normalizeHue(300), 100, 50], top: "50%", left: "100%", background: "#ff00ff"},
      {hsv: [normalizeHue(180), 100, 50], top: "50%", left: "100%", background: "#00ffff"},
      {hsv: [normalizeHue(240), 100, 50], top: "50%", left: "100%", background: "#0000ff"},
      {hsv: [0, 50, 50], top: "50%", left: "50%", background: "#ff0000"},
      {hsv: [normalizeHue(60), 50, 50], top: "50%", left: "50%", background: "#ffff00"},
      {hsv: [normalizeHue(120), 50, 50], top: "50%", left: "50%", background: "#00ff00"},
      {hsv: [normalizeHue(300), 50, 50], top: "50%", left: "50%", background: "#ff00ff"},
      {hsv: [normalizeHue(180), 50, 50], top: "50%", left: "50%", background: "#00ffff"},
      {hsv: [normalizeHue(240), 50, 50], top: "50%", left: "50%", background: "#0000ff"}
   ];

   function resetColor(hue: number = 0, saturation: number = 0, brightness: number = 0) {
      component.hue = hue;
      component.saturation = saturation;
      component.brightness = brightness;
   }

   beforeEach(() => {
      component = new ColorMap();
      component.width = 100;
      component.height = 100;
   });

   it("Indicator top position is calculated correctly from template bound HSV", () => {
      for(let color of colorTable) {
         resetColor(color.hsv[0], color.hsv[1], color.hsv[2]);
         expect(component.indicatorTop).toEqual(color.top);
      }
   });

   it("Indicator left position is calculated correctly from template bound HSV", () => {
      for(let color of colorTable) {
         resetColor(color.hsv[0], color.hsv[1], color.hsv[2]);
         expect(component.indicatorLeft).toEqual(color.left);
      }
   });

   it("Hue background color is calculated correctly from template bound HSV", () => {
      for(let color of colorTable) {
         resetColor(color.hsv[0], color.hsv[1], color.hsv[2]);
         expect(component.hueBackground).toEqual(color.background);
      }
   });

   it("Saturation is emitted correctly from mouse click", (done) => {
      let counter: number = 0;
      component.saturationChanged.subscribe((value: number) => {
         expect(value).toEqual(colorTable[counter++].hsv[1]);

         if(counter == colorTable.length) {
            done();
         }
      });

      for(let color of colorTable) {
         let x: number = parseInt(color.left.substring(0, color.left.length - 1), 10);
         component.setColorPosition({offsetX: x, offsetY: 0});
      }
   });

   it("Brightness is emitted correctly from mouse click", (done) => {
      let counter: number = 0;
      component.brightnessChanged.subscribe((value: number) => {
         expect(value).toEqual(colorTable[counter++].hsv[2]);

         if(counter == colorTable.length) {
            done();
         }
      });

      for(let color of colorTable) {
         let y: number = parseInt(color.top.substring(0, color.top.length - 1), 10);
         component.setColorPosition({offsetX: 0, offsetY: y});
      }
   });
});
