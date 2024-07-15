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
import { ColorPane } from "./cp-color-pane.component";

describe("Color Picker Unit Test", () => {
   let modalService: any;
   let recentColorService: any;
   let colorPane: ColorPane;

   beforeEach(() => {
      modalService = { open: jest.fn() };
      recentColorService = { colorSelected: jest.fn() };
   });

   //for Bug #20049
   it("recent color is emited correctly", (done) => {
      colorPane = new ColorPane(modalService, recentColorService);
      let counter = 0;
      let colors = ["#00ff00", "#ff00ff", "#00ff00"];
      colorPane.colorChanged.subscribe((value: string) => {
         expect(value).toEqual(colors[counter++]);

         if(counter == colors.length) {
            done();
         }
      });

      colorPane.selectColor("#00ff00");
      colorPane.selectColor("#ff00ff");
      colorPane.selectColor("#00ff00");
   });

   //Bug #20721
   it("should support to set color", (done) => {
      colorPane = new ColorPane(modalService, recentColorService);
      colorPane.colorChanged.subscribe((value: string) => {
         expect(value).toEqual("#111111");

         done();
      });
      colorPane.setColorValue("111111");
   });
});