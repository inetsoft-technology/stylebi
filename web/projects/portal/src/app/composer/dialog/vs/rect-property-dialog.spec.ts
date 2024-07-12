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
import { RectanglePropertyDialogModel } from "../../data/vs/rectangle-property-dialog-model";
import { RectanglePropertyDialog } from "./rectangle-property-dialog.component";
import { of as observableOf } from "rxjs";

let createModel = () => <RectanglePropertyDialogModel> {
   shapeGeneralPaneModel: null,
   rectanglePropertyPaneModel: {
      linePropPaneModel: { color: "#ffffff" },
      fillPropPaneModel: null
   },
   vsAssemblyScriptPaneModel: {}
};

describe("RectanglePropertyDialog Unit Test", () => {
   let _this: RectanglePropertyDialog;
   let contextService: any;
   let dialogService: any;

   beforeEach(() => {
      contextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn(),
         getObjectChange: jest.fn(() => observableOf({}))
      };
      dialogService = { checkScript: jest.fn() };
      _this = new RectanglePropertyDialog(contextService, dialogService);
      _this.model = createModel();
      _this.ngOnInit();
   });

   it("should mock the HTTP response", () => {
      let model = _this.model;
      expect(model).toBeDefined();
      expect(model.rectanglePropertyPaneModel.linePropPaneModel.color).toBe("#ffffff");
   });
});
