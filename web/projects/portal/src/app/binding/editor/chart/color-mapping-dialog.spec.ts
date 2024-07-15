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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ColorMappingDialog } from "./color-mapping-dialog.component";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { ColorPicker } from "../../../widget/color-picker/color-picker.component";
import { ColorMap } from "../../../common/data/color-map";
import { ColorMappingDialogModel } from "../../data/chart/color-mapping-dialog-model";
import * as V from "../../../common/data/visual-frame-model";
import { TestUtils } from "../../../common/test/test-utils";

describe("Color Mapping Dialog Unit Test", () => {
   let createModel: () => ColorMappingDialogModel = () => {
      return {
         colorMaps: [{
            color: "#ffff00",
            option: "1997"
         },
         {
            color: "#ff0000",
            option: "1997"
         },
         {
            color: "#0000ff",
            option: "1997"
         }],
         globalModel: null,
         useGlobal: false,
         shareColors: false,
         dimensionData: [{label: "1997", value: "1997"},
            {label: "1998", value: "1998"},
            {label: "1999", value: "1999"},
            {label: "2000", value: "2000"}]
      };
   };
   let mockColorFrame: (field: string) => V.CategoricalColorModel = (field: string) => {
      return {
         changed: false,
         clazz: "inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel",
         colorMaps: [{
            color: "#ffff00",
            option: "1997"
         }],
         globalColorMaps: [],
         useGlobal: false,
         shareColors: true,
         colors: ["#518db9", "#b9dbf4", "#62a640", "#ffff00", "#ff0000", "#0000ff"],
         cssColors: [],
         defaultColors: ["#518db9", "#b9dbf4", "#62a640", "#ffff00", "#ff0000", "#0000ff"],
         dateFormat: 5,
         field: field,
         name: null,
         summary: false
      };
   };

   let fixture: ComponentFixture<ColorMappingDialog>;
   let colorMappingDialog: ColorMappingDialog;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            ColorMappingDialog, ColorEditor, ColorPicker
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   //Bug #21331
   it("should not commit duplicate option in color mapping dialog", (done) => {
      fixture = TestBed.createComponent(ColorMappingDialog);
      colorMappingDialog = <ColorMappingDialog>fixture.componentInstance;
      colorMappingDialog.model = createModel();
      colorMappingDialog.field = TestUtils.createMockAestheticInfo("orderdate");
      colorMappingDialog.field.dataInfo = TestUtils.createMockChartDimensionRef("orderdate");
      colorMappingDialog.field.frame = mockColorFrame("orderdate");
      fixture.detectChanges();

      let cMaps: ColorMap[] = [{
         color: "#0000ff",
         option: "1997"
      }];
      colorMappingDialog.onCommit.subscribe((maps: ColorMap[]) => {
         expect(maps).toEqual(cMaps);

         done();
      });
      colorMappingDialog.ok();
   });
});