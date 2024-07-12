/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { TextureComboBox } from "./texture-combo-box.component";

describe("Texture Combobox Unit Test", () => {
   let textureComb: TextureComboBox;

   //for Bug #20089, Bug #20186.
   it("shape tooltip is not right", () => {
      textureComb = new TextureComboBox();
      //Bug #20089
      textureComb.texture = -1;
      textureComb.index = null;
      expect(textureComb.getTitle()).not.toBeNull();

      //Bug #20186
      textureComb.texture = 0;
      textureComb.index = 0;
      expect(textureComb.getTitle()).toEqual("1");
   });
});