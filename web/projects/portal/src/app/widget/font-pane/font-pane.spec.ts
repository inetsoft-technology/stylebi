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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FontInfo } from "../../common/data/format-info-model";
import { DebounceService } from "../services/debounce.service";
import { FontService } from "../services/font.service";
import { FontPane } from "./font-pane.component";

describe("Font Pane Unit Test", () => {
   let mockFontModel: () => FontInfo = () => {
      return {
         fontFamily: "",
         fontSize: "11",
         fontStyle: "normal",
         fontUnderline: "normal",
         fontStrikethrough: "normal",
         fontWeight: "normal"
      };
   };

   let fixture: ComponentFixture<FontPane>;
   let fontPane: FontPane;

   beforeEach(() => {
      const fontService = { getAllFonts: jest.fn() };
      const debounceService = { debounce: jest.fn(), cancel: jest.fn() };
      debounceService.debounce.mockImplementation((key, fn, delay, args) => {
         fn.apply(args);
      });

      TestBed.configureTestingModule({
         imports: [],
         declarations: [
            FontPane
         ],
         providers: [
            { provide: DebounceService, useValue: debounceService },
            { provide: FontService, useValue: fontService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();

      fixture = TestBed.createComponent(FontPane);
      fontPane = fixture.componentInstance;
   });

   //for Bug #19781
   it("font should be editted correctly", () => {
      fontPane.fontModel = mockFontModel();
      fontPane.fonts = ["Agency FB", "Algerian", "Roboto", "Roboto Black", "Roboto Narrow"];
      fixture.detectChanges();

      fontPane.toggleWeight();
      expect(fontPane.fontModel.fontFamily).toEqual("Default");
      expect(fontPane.fontModel.fontWeight).toEqual("bold");

      fontPane.toggleStyle();
      expect(fontPane.fontModel.fontFamily).toEqual("Default");
      expect(fontPane.fontModel.fontStyle).toEqual("italic");

      fontPane.toggleUnderline();
      expect(fontPane.fontModel.fontFamily).toEqual("Default");
      expect(fontPane.fontModel.fontUnderline).toEqual("underline");

      fontPane.toggleStrikethrough();
      expect(fontPane.fontModel.fontFamily).toEqual("Default");
      expect(fontPane.fontModel.fontStrikethrough).toEqual("strikethrough");

      fontPane.fontModel.fontSize = "12";
      fontPane.changeFontSize();
      expect(fontPane.fontModel.fontFamily).toEqual("Default");
      expect(fontPane.fontModel.fontSize).toEqual("12");
   });
});
