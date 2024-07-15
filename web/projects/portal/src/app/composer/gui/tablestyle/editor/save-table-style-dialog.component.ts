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
import { Component, EventEmitter, Input, NgZone, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { SaveTableStyleDialogModel } from "../../../data/tablestyle/save-table-style-dialog-model";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { HttpClient, HttpParams } from "@angular/common/http";
import { SaveLibraryDialogModelValidator } from "../../../data/tablestyle/save-library-dialog-model-validator";
import { ComponentTool } from "../../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

const CONFIRM_MESSAGE = {
   title: "_#(js:Confirm)",
   options: {"yes": "_#(js:Yes)", "no": "_#(js:No)"},
   optionsOnlyOk: {"ok": "_#(js:OK)"},
   optionsWithCancel: {"yes": "_#(js:Yes)", "no": "_#(js:No)", "cancel": "_#(js:Cancel)"}
};

@Component({
   selector: "save-table-style-dialog",
   templateUrl: "save-table-style-dialog.component.html"
})
export class SaveTableStyleDialog implements OnInit {
   @Input() defaultFolder: AssetEntry;
   @Input() model: SaveTableStyleDialogModel;
   @Output() onCommit: EventEmitter<SaveTableStyleDialogModel> = new EventEmitter<SaveTableStyleDialogModel>();
   @Output() onCancel = new EventEmitter();
   form: UntypedFormGroup;
   formValid = () => this.model && this.form && this.form.valid;

   constructor(private zone: NgZone, private http: HttpClient,
               private modalService: NgbModal) {
   }

   cancel(): void {
      this.onCancel.emit();
   }

   ngOnInit() {
      if(this.defaultFolder.path == "/" || this.defaultFolder.path == "Table Style") {
         this.defaultFolder.path = "/Table Style";
      }

      this.initForm();
   }

   initForm() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.name, [
            Validators.required,
            FormValidators.assetEntryBannedCharacters,
            FormValidators.assetNameStartWithCharDigit
         ])});

      this.form.get("name").valueChanges.subscribe((val) => {
         if(this.model.name != val) {
            this.model.name = val;
         }
      });
   }

   selectFolder(node: TreeNodeModel) {
      if(!node.leaf) {
         this.model.identifier = node.data.identifier;
         this.model.folder = node.data.properties["folder"];
      }
      else {
         this.model.identifier = node.data.identifier;
         this.model.name = node.label;
         this.model.folder = node.data.properties["folder"];
         this.form.get("name").setValue(node.label);
      }
   }

   ok() {
      let params: HttpParams = new HttpParams()
         .set("name", this.model.name)
         .set("identifier", this.model.identifier)

      if(!!this.model.folder) {
         params = params.set("folder", this.model.folder);
      }

      this.http.get<SaveLibraryDialogModelValidator>("../api/composer/table-style/check-save-as-permission",
         {params}).subscribe((validator) => {
         let promise = Promise.resolve(true);

         if(!!validator.alreadyExists && !validator.permissionDenied) {
            promise = promise.then(() => {
               return validator.allowOverwrite ?
                  ComponentTool.showConfirmDialog(this.modalService,
                     CONFIRM_MESSAGE.title, validator.alreadyExists,
                     CONFIRM_MESSAGE.options)
                     .then((buttonClicked) => buttonClicked === "yes") :
                  ComponentTool.showConfirmDialog(this.modalService,
                     "_#(js:Error)", validator.alreadyExists,
                     CONFIRM_MESSAGE.optionsOnlyOk).then(() => false);
            });
         }
         else if(!!validator.permissionDenied) {
            if(validator.permissionDenied.length > 0) {
               ComponentTool.showMessageDialog(
                  this.modalService, "Error", validator.permissionDenied)
                  .then(() => {});
               return;
            }
         }

         promise.then((confirmed) => {
            if(confirmed) {
               this.onCommit.emit(this.model);
            }
         });
      });
   }

   enter() {
      this.zone.run(() => this.ok());
   }
}
