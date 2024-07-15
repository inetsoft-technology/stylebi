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
import { NgbModalRef } from "@ng-bootstrap/ng-bootstrap";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { ImportCSVDialogModel } from "../../data/ws/import-csv-dialog-model";
import { ImportCSVDialog } from "./import-csv-dialog.component";

describe("Import CSV Dialog Tests", () => {
   let dialog: ImportCSVDialog;
   let modalService: any;
   let changeDetectorRef: any;

   beforeEach(() => {
      changeDetectorRef = { detectChanges: jest.fn() };
      let modelService: any = { getModel: jest.fn() };
      let fileUploadService: any = { upload: jest.fn(), getObserver: jest.fn() };
      let http: any = { put: jest.fn() };
      modalService = { open: jest.fn() };

      dialog = new ImportCSVDialog(modelService, fileUploadService, http, changeDetectorRef, modalService);
      let model: ImportCSVDialogModel = {
         tableName: "name",
         encodingList: [],
         encodingSelected: undefined,
         sheetsList: [],
         sheetSelected: undefined,
         unpivotCB: true,
         headerCols: 0,
         firstRowCB: false,
         delimiter: "",
         delimiterTab: false,
         fileName: undefined,
         fileType: undefined,
         removeQuotesCB: true,
         confirmed: false
      };

      dialog.model = model;
      dialog.initForm();
   });

   it("unpivot and first row should be mutually exclusive", () => {
      dialog.form.get("firstRowCB").patchValue(true);
      expect(dialog.form.get("unpivotCB").value).toBeFalsy();

      dialog.form.get("unpivotCB").patchValue(true);
      expect(dialog.form.get("firstRowCB").value).toBeFalsy();
   });

   it("unpivot data header columns cannot be negative", () => {
      dialog.form.get("unpivotCB").patchValue(true);
      let control = dialog.form.get("headerCols");

      control.patchValue(-1);
      expect(control.errors).toBeTruthy();

      control.patchValue(0);
      expect(control.errors).toBeFalsy();

      control.patchValue(1);
      expect(control.errors).toBeFalsy();
   });

   it("should throw an error on empty file", () => {
      const event = {target: {files: [new File([], "empty file.txt")]}};
      modalService.open.mockImplementation(() => ({
         componentInstance: new MessageDialog(),
         result: Promise.resolve(null)
      } as NgbModalRef));

      dialog.updateFile(event);
      expect(modalService.open).toHaveBeenCalled();
   });
});
