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
import { HttpClient } from "@angular/common/http";
import { Component, Input, OnInit, TemplateRef, ViewChild } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";
import { mergeMap } from "rxjs/operators";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { ComponentTool } from "../../../common/util/component-tool";
import {
   ComposerContextProviderFactory,
   ContextProvider
} from "../../../vsobjects/context-provider.service";
import { ModelService } from "../../../widget/services/model.service";
import { SelectDataSourceDialogModel } from "../../data/vs/select-data-source-dialog-model";
import { ViewsheetOptionsPaneModel } from "../../data/vs/viewsheet-options-pane-model";
import { ViewsheetParametersDialogModel } from "../../data/vs/viewsheet-parameters-dialog-model";
import { LocalStorage } from "../../../common/util/local-storage.util";

@Component({
   selector: "viewsheet-options-pane",
   templateUrl: "viewsheet-options-pane.component.html",
   providers: [{
      provide: ContextProvider,
      useFactory: ComposerContextProviderFactory
   }]
})
export class ViewsheetOptionsPane implements OnInit {
   @Input() model: ViewsheetOptionsPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() defaultOrgAsset: boolean = false;
   @Input() runtimeId: string;
   @ViewChild("viewsheetParametersDialog") viewsheetParametersDialog: TemplateRef<any>;
   @ViewChild("selectDataSourceDialog") selectDataSourceDialog: TemplateRef<any>;
   viewsheetParametersModel: ViewsheetParametersDialogModel;
   selectDataSourceModel: SelectDataSourceDialogModel;
   dataSource: AssetEntry;

   constructor(private modalService: NgbModal, private http: HttpClient,
               private contextProvider: ContextProvider, private modelService: ModelService)
   {
   }

   ngOnInit() {
      this.initForm();
      this.dataSource = this.model.selectDataSourceDialogModel.dataSource;

      if(!!this.dataSource && !!this.dataSource.description && !!this.dataSource.alias) {
         let index = this.dataSource.description.lastIndexOf("/");

         if(index != -1) {
            this.dataSource.description = this.dataSource.description.substring(0, index + 1) + this.dataSource.alias;
         }
      }
   }

   initForm(): void {
      this.form.addControl("maxRows", new UntypedFormControl(
         {value: this.model.maxRows, disabled: this.notSelected()},
         [FormValidators.positiveIntegerInRange, FormValidators.isInteger()]));
      this.form.addControl("alias", new UntypedFormControl(
         this.model.alias, [FormValidators.doesNotStartWithNumberOrLetter]));
      this.form.addControl("touchInterval", new UntypedFormControl(
         {value: this.model.touchInterval, disabled: !this.isServerSideUpdate()},
         [Validators.required, FormValidators.inRangeOrNull(1, 86400)]));
      this.form.addControl("snapGrid", new UntypedFormControl(
         {value: this.model.snapGrid, disabled: !this.snapToGrid()},
         [FormValidators.inSanpGridRangeOrNull]));
   }

   changeSource() {
      if(!this.model.selectDataSourceDialogModel.dataSource || this.model.worksheet) {
         this.form.get("maxRows").disable();
      }
      else {
         this.form.get("maxRows").enable();
      }
   }

   snapToGrid(): boolean {
      return LocalStorage.getItem("snap-to-grid") != "false";
   }

   notSelected(): boolean {
      return !this.model.selectDataSourceDialogModel.dataSource;
   }

   clear() {
      this.model.selectDataSourceDialogModel.dataSource = null;
   }

   changeServerSideUpdate() {
      if(this.model.serverSideUpdate) {
         this.form.get("touchInterval").enable();
      }
      else {
         this.form.get("touchInterval").disable();
      }
   }

   isServerSideUpdate(): boolean {
      return this.model.serverSideUpdate;
   }

   showViewsheetParametersDialog(): void {
      this.viewsheetParametersModel = Tool.clone(this.model.viewsheetParametersDialogModel);
      const options: NgbModalOptions = {
         windowClass: "viewsheet-parameters-dialog"
      };

      this.modalService.open(this.viewsheetParametersDialog, options).result.then(
         (result: ViewsheetParametersDialogModel) => {
            this.model.viewsheetParametersDialogModel = result;
         },
         (reject) => {});
   }

   showSelectDataSourceDialog(): void {
      this.selectDataSourceModel = Tool.clone(this.model.selectDataSourceDialogModel);

      this.modalService.open(this.selectDataSourceDialog).result.then(
         (result: SelectDataSourceDialogModel) => {
            this.model.selectDataSourceDialogModel = result;
            this.changeSource();
         },
         (reject) => {});
   }

   isLogicModelDataSource(): boolean {
      if(!this.dataSource) {
         return false;
      }

      return AssetEntryHelper.isLogicModel(this.dataSource);
   }

   convertToWorksheet(): void {
      ComponentTool
         .showConfirmDialog(this.modalService, "_#(js:Confirm)", "_#(js:composer.vs.datasource.convertToWorksheet.prompt)")
         .then((buttonClicked: string) => {
            if(buttonClicked === "ok") {
               this.doConvert();
            }
         });
   }

   private doConvert(): void {
      const url = "../api/composer/vs/viewsheet-property-dialog-model/convert-to-worksheet/" +
         Tool.byteEncode(this.runtimeId);

      this.modelService.getModel<{model: SelectDataSourceDialogModel, hasMvs: boolean}>(url).pipe(
         mergeMap(response => {
            if(response.hasMvs) {
               return ComponentTool.showMessageDialog(this.modalService,
                  "_#(js:Confirm)",
                  "_#(js:composer.vs.datasource.convertToWorksheet.mvwarn)")
                     .then(() => response.model);
               }

               return of(response.model);
            })
         ).subscribe(model => {
            this.model.selectDataSourceDialogModel = model;
            this.dataSource = model.dataSource;
         });
   }
}
