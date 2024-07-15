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
import { HttpClient, HttpResponse } from "@angular/common/http";
import {
   AfterViewChecked,
   Component,
   EventEmitter,
   Input,
   ChangeDetectorRef,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { FileUploadService } from "../../../common/services/file-upload.service";
import { Tool } from "../../../../../../shared/util/tool";
import {
   ComposerContextProviderFactory,
   ContextProvider
} from "../../../vsobjects/context-provider.service";
import { BaseTableCellModel } from "../../../vsobjects/model/base-table-cell-model";
import { ModelService } from "../../../widget/services/model.service";
import { ImportCSVDialogModel } from "../../data/ws/import-csv-dialog-model";
import { ImportCSVDialogModelValidator } from "../../data/ws/import-csv-dialog-model-validator";
import { Worksheet } from "../../data/ws/worksheet";
import { ComponentTool } from "../../../common/util/component-tool";

const MODEL_URI = "../api/composer/ws/import-csv-dialog-model/";
const SUBMIT_URI = "/events/ws/dialog/import-csv-dialog-model";
const TOUCH_FILE_URI = "../api/composer/ws/import-csv-dialog-model/touch-file/";

@Component({
   selector: "import-csv-dialog",
   templateUrl: "import-csv-dialog.component.html",
   providers: [{
      provide: ContextProvider,
      useFactory: ComposerContextProviderFactory
   }]
})
export class ImportCSVDialog implements OnInit, AfterViewChecked, OnDestroy {
   @Input() worksheet: Worksheet;
   @Input() tableName: string;
   @Input() mashUpData: boolean;
   @Output() onCommit = new EventEmitter<ImportCSVDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   model: ImportCSVDialogModel;
   form: UntypedFormGroup;
   formValid = () => this.model && this.form && this.form.valid && this.model.fileName &&
      this.fileUploaded && !this.errorOnServer && !this.duplicateHeaders && !this.invalidCharacters;

   previewTable: BaseTableCellModel[][];
   private fileToucherID: any;
   private previewSub: Subscription;
   encodingList = ["UTF-8", "GBK", "Unicode"];
   fileUploadProgress: number = null;
   private previewOutOfDate: boolean = false;
   pending: boolean = false;
   errorOnServer: boolean = false;
   fileUploaded: boolean = false;
   previewPending: boolean = false;
   duplicateHeaders: boolean = false;
   invalidCharacters: boolean = false;
   private progressInterval: any = null;
   ignoreTypeColumns: number[];

   constructor(private modelService: ModelService,
               private fileUploadService: FileUploadService,
               private http: HttpClient,
               private changeRef: ChangeDetectorRef,
               private modalService: NgbModal)
   {
   }

   ngOnInit() {
      this.modelService.getModel(MODEL_URI + Tool.byteEncode(this.worksheet.runtimeId))
         .subscribe(
            (data) => {
               this.model = <ImportCSVDialogModel> data;
               this.initForm();
               this.initFileToucher();
            },
            (error) => {
               console.error("Could not fetch the data for this dialog.");
            }
         );

      this.fileUploadService.getObserver().subscribe((val) => {
         // cap at 80%
         this.fileUploadProgress = val.loaded / val.total * 80;

         // fill last 20% by time estimate
         if(val.loaded >= val.total) {
            const percent = val.total / 6000;

            this.progressInterval = setInterval(() => {
               this.fileUploadProgress += 1;

               if(this.fileUploadProgress >= 98 && this.progressInterval != null) {
                  clearInterval(this.progressInterval);
                  this.progressInterval = null;
               }
            }, percent);
         }
      });
   }

   ngAfterViewChecked() {
      if(this.previewOutOfDate) {
         this.updatePreviewTable();
         this.previewOutOfDate = false;
      }
   }

   ngOnDestroy() {
      if(this.previewSub && !this.previewSub.closed) {
         this.previewSub.unsubscribe();
      }

      if(this.fileToucherID != null) {
         clearTimeout(this.fileToucherID);
      }
   }

   initForm() {
      this.form = new UntypedFormGroup({});
      this.form.addControl("encodingSelected", new UntypedFormControl({
         value: this.model.encodingSelected,
         disabled: true
      }, [
         Validators.required
      ]));
      this.form.addControl("sheetSelected", new UntypedFormControl({
         value: this.model.sheetSelected,
         disabled: true
      }, [Validators.required]));
      this.form.addControl("delimiter", new UntypedFormControl({
         value: this.model.delimiter,
         disabled: true
      }, [Validators.required]));
      this.form.addControl("delimiterTab", new UntypedFormControl({
         value: this.model.delimiterTab,
         disabled: true
      }));
      this.form.addControl("detectType", new UntypedFormControl({
         value: this.model.detectType,
         disabled: true
      }));
      this.form.addControl("unpivotCB", new UntypedFormControl(this.model.unpivotCB));
      this.form.addControl("headerCols", new UntypedFormControl(
         {value: this.model.headerCols, disabled: !this.model.unpivotCB}, [
            Validators.required,
            Validators.min(0)
         ]));
      this.form.addControl("firstRowCB", new UntypedFormControl(this.model.firstRowCB));
      this.form.addControl("removeQuotesCB", new UntypedFormControl({
         value: this.model.removeQuotesCB,
         disabled: true
      }));

      // Need to do this because message dialog causes blur and modifies formControl.touched
      for(let controlName in this.form.controls) {
         if(this.form.controls.hasOwnProperty(controlName)) {
            this.form.controls[controlName].markAsTouched();
         }
      }

      this.form.get("unpivotCB").valueChanges.subscribe((value: boolean) => {
         Tool.setFormControlDisabled(this.form.get("headerCols"), !value);

         if(value) {
            this.form.get("firstRowCB").patchValue(false);
         }
      });

      this.form.get("firstRowCB").valueChanges.subscribe((value: boolean) => {
         if(value) {
            this.form.get("unpivotCB").patchValue(false);
         }
      });

      this.form.get("delimiterTab").valueChanges.subscribe((value: boolean) => {
         Tool.setFormControlDisabled(this.form.get("delimiter"), value ||
                                     this.model.fileType != "DELIMITED");
      });

      this.form.valueChanges.subscribe((event) => this.previewOutOfDate = true);
      this.setEnabled();
   }

   private setEnabled() {
      Tool.setFormControlDisabled(this.form.get("unpivotCB"), !this.unpivotEnabled);
   }

   updateFile(event: any) {
      const files: File[] = event.target.files;

      if(files.length === 0) {
         this.reset();
         return;
      }

      this.model.fileName = files[0].name;
      this.form.patchValue({fileName: this.model.fileName}, {emitEvent: false});

      if(files[0].size === 0) {
         this.handleEmptyFile();
         return;
      }

      this.fileUploaded = false;
      this.fileUploadService.upload(MODEL_URI + "upload/" +
                                    Tool.byteEncode(this.worksheet.runtimeId), files)
         .then((res: any) => {
            Object.assign(this.model, res.model);
            this.setEnabled();
            this.fileUploaded = true;

            this.updateForm();
            this.model.headerNames = null;
            this.showLimitMessage(res.limitMessage);
         })
         .catch((res: any) => {
            // when max size is reached, the upload would be terminated immediately.
            // even though we returns a json with message in global exception handler,
            // browser would throw away the response in this case, resulting the
            // client not receiving a meaningful message.
            let msg = "_#(js:common.csvmax2)";

            if(res) {
               const error = JSON.parse(res);
               msg = error.message || res;
            }

            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", msg);

            this.clearProgress();
            this.reset();
         });

      const index: number = this.model.fileName.lastIndexOf(".");

      if(index >= 0) {
         this.model.fileType = this.model.fileName.substring(index + 1).toUpperCase();
         this.model.fileType = this.model.fileType == "XLS" ||
            this.model.fileType == "XLSX" ? this.model.fileType : "DELIMITED";
      }

      Tool.setFormControlDisabled(this.form.get("delimiterTab"),
                                  this.model.fileType != "DELIMITED");
      Tool.setFormControlDisabled(this.form.get("detectType"),
                                  this.model.fileType != "DELIMITED");
   }

   private clearProgress() {
      if(this.progressInterval != null) {
         clearInterval(this.progressInterval);
         this.progressInterval = null;
      }

      this.fileUploadProgress = null;
   }

   updateForm() {
      if(this.encodingList[3]) {
         this.encodingList.splice(3, 1);
      }

      if(this.encodingList.filter((el) => el === this.model.encodingSelected).length === 0) {
         this.encodingList[3] = this.model.encodingSelected;
      }

      this.form.enable();

      for(let val in this.form.value) {
         if(this.form.value.hasOwnProperty(val)) {
            const temp = {};
            temp[val] = this.model[val];
            this.form.patchValue(temp);
         }
      }

      if(this.model.fileType === "DELIMITED") {
         this.form.get("sheetSelected").disable();
         this.form.get("detectType").enable();

         if(this.model.delimiter === "\t") {
            const delimiterTab = this.form.get("delimiterTab");
            delimiterTab.enable();
            delimiterTab.patchValue(true);
            this.form.get("delimiter").patchValue(",");
         }
      }
      else {
         if(this.model.fileType == null) {
            this.form.get("sheetSelected").disable();
         }

         this.form.get("encodingSelected").disable();
         this.form.get("delimiter").disable();
         this.form.get("delimiterTab").disable();
         this.form.get("detectType").disable();
         this.form.get("removeQuotesCB").disable();
      }
   }

   ok(mashUp?: boolean) {
      if(this.pending) {
         return;
      }

      this.pending = true;
      Object.assign(this.model, this.form.value);

      if(this.tableName) {
         this.model.tableName = this.tableName;
      }
      else if(this.model.fileName) {
         this.model.newTableName = this.model.fileName
            .replace(/\.[^.]+$/, "")
            .replace(/[^\uFF00-\uFFEF\u4e00-\u9fa5a-zA-Z0-9 $#_%-]/g, "_")
            .replace(/[\uff1a|[\uff1f]/g, "_")
            .trim();
      }


      this.model.ignoreTypeColumns = this.ignoreTypeColumns;
      this.model.mashUpData = mashUp;
      this.worksheet.socketConnection.sendEvent(SUBMIT_URI, this.model);
      this.onCommit.emit(this.model);
      /* not seem to be necessary
      this.modelService.sendModel(MODEL_URI + "preview/" +
                                  Tool.byteEncode(this.worksheet.runtimeId), this.model)
         .subscribe((res) => {
            this.parsePreviewResponse(res);

            if(!this.errorOnServer) {
               this.worksheet.socketConnection.sendEvent(SUBMIT_URI, this.model);
               this.onCommit.emit(this.model);
            }
         }, (error) => {
            this.pending = false;
         });
      */
   }

   cancel() {
      this.onCancel.emit();
   }

   private initFileToucher() {
      this.fileToucherID = setInterval(() => {
         this.http.put(TOUCH_FILE_URI +
            Tool.byteEncode(this.worksheet.runtimeId), null).subscribe();
      }, 60000);
   }

   private updatePreviewTable(ignoreTypeColumns?: number[]) {
      if(!this.fileUploaded || !this.form.valid) {
         return;
      }

      if(this.previewSub && !this.previewSub.closed) {
         this.previewSub.unsubscribe();
      }

      const currentModel = Object.assign({}, this.model, this.form.getRawValue());
      currentModel.ignoreTypeColumns = this.ignoreTypeColumns = ignoreTypeColumns;
      this.previewPending = true;

      this.previewSub = this.modelService.sendModel(MODEL_URI + "preview/" +
         Tool.byteEncode(this.worksheet.runtimeId), currentModel)
         .subscribe(res => {
            this.previewPending = false;
            this.parsePreviewResponse(res);
         });
   }

   private parsePreviewResponse(res: HttpResponse<any>) {
      this.clearProgress();

      if(res.body && res.body.warnMsg) {
         let mixedIndexes = res.body.mixedIndexes;
         let buttonOptions: any = {"ok": "_#(js:Continue)", "cancel": "_#(js:Cancel)",
            "close": "_#(js:Close)"};

         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Error)", res.body.warnMsg,
            buttonOptions, {backdrop: false})
            .then((buttonClicked) => {
               if(buttonClicked === "ok") {
                  this.updatePreviewTable(mixedIndexes);
               }
               else if(buttonClicked === "cancel") {
                  this.cancel();
               }
            });
         this.errorOnServer = true;
         return;
      }

      let result: {previewTable?: BaseTableCellModel[][], validator: ImportCSVDialogModelValidator, limitMessage: string} = res.body;
      this.previewTable = result.previewTable;
      this.setEnabled();
      this.model.headerNames = null;
      let message = result.validator.message;

      if(message) {
         this.errorOnServer = true;

         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Error)", message, {"ok": "OK"}, {backdrop: false})
            .then(() => {
            });
      }
      else {
         this.errorOnServer = false;
      }

      this.showLimitMessage(result.limitMessage);
   }

   private showLimitMessage(limitMessage: string) {
      if(limitMessage != null && limitMessage.trim().length > 0) {
         ComponentTool.showConfirmDialog(this.modalService,
            "_#(js:Warning)", limitMessage,
            {"ok": "OK"}, {backdrop: false})
            .then(() => {
            });
      }
   }

   private handleEmptyFile() {
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Error)", "_#(js:viewer.worksheet.import.empty)",
         {"ok": "OK"}, {backdrop: false})
         .then(() => {});
      this.reset();
   }

   private reset() {
      this.model.sheetSelected = null;
      this.model.sheetsList = null;
      this.model.fileType = null;
      this.model.encodingSelected = null;
      this.model.fileName = "";
      this.fileUploaded = false;
      this.previewTable = null;
      this.model.headerNames = null;
      this.setEnabled();
      this.updateForm();
   }

   get unpivotEnabled(): boolean {
      return this.previewTable && this.previewTable[0] && this.previewTable[0].length > 1;
   }

   onHeaderRename(event: { column: number, newName: string }) {
      if(!this.model.headerNames) {
         this.model.headerNames = {};
      }

      this.model.headerNames[event.column] = event.newName;
      this.validateHeaders();
   }

   validateHeaders() {
      if(!this.previewTable && this.previewTable.length <= 0) {
         return;
      }

      let cols = this.previewTable[0].length;
      this.duplicateHeaders = false;
      this.invalidCharacters = false;

      for(let col0 = 0; col0 < cols; col0 ++) {
         let header0 = this.model.headerNames[col0];
         header0 = header0 == null ? this.previewTable[0][col0].cellData.toString() : header0;

         if(FormValidators.matchCalcSpecialCharacters(header0)) {
            this.invalidCharacters = true;
            return;
         }

         for(let col1 = 0; col1 < cols; col1 ++) {
            if(col0 != col1) {
               let header1 = this.model.headerNames[col1];
               header1 = header1 == null ? this.previewTable[0][col1].cellData.toString() : header1;

               if(header0.trim() == "") {
                  if(header1.trim() == "") {
                     this.duplicateHeaders = true;
                     return;
                  }
               }
               else if(header0 == header1){
                  this.duplicateHeaders = true;
                  return;
               }
            }
         }
      }
   }
}
