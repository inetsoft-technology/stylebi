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
import { ComponentTool } from "../../common/util/component-tool";
import { ExportDialogModel } from "../model/export-dialog-model";
import { ExportDialog } from "./export-dialog.component";

describe("Export Dialog Unit Test", () => {
   let createModel: () => ExportDialogModel = () => {
      return {
         fileFormatPaneModel: {
            formatType: 0,
            matchLayout: false,
            includeCurrent: false,
            linkVisible: false,
            sendLink: false,
            selectedBookmarks: [],
            allBookmarks: [],
            allBookmarkLabels: [],
            expandEnabled: false,
            expandSelections: false,
            onlyDataComponents: false
         }
      };
   };

   let exportDialog: ExportDialog;
   let httpService: any;
   let modal: any;

   beforeEach(() => {
      httpService = { get: jest.fn() };
      modal = { open: jest.fn() };
      exportDialog = new ExportDialog(httpService, modal);
   });

   // Bug #17235 should not
   it("should show error", () => {
      exportDialog.model = createModel();
      exportDialog.model.fileFormatPaneModel.includeCurrent = false;
      let showMessageDialog: any = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => {});

      exportDialog.ok();
      expect(showMessageDialog).toHaveBeenCalled();
      expect(showMessageDialog.mock.calls[0][1]).toEqual("_#(js:Error)");
      expect(showMessageDialog.mock.calls[0][2]).toEqual("_#(js:common.fileformatPane.notvoid)");
   });
});
