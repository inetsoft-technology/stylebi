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
import {
   Component,
   OnInit,
   Output,
   EventEmitter,
   Input,
   Renderer2,
   ElementRef
} from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable, of, Subscription } from "rxjs";
import { catchError, timeout } from "rxjs/operators";
import { AssetEntry, createAssetEntry } from "../../../../../../shared/data/asset-entry";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { CommonKVModel } from "../../../common/data/common-kv-model";
import { ViewsheetPropertyDialogModel } from "../../data/vs/viewsheet-property-dialog-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { Viewsheet } from "../../data/vs/viewsheet";
import { BaseResizeableDialogComponent } from "../../../vsobjects/dialog/base-resizeable-dialog.component";
import { ComponentTool } from "../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

const VIEWSHEET_PROPERTY_TEST_SCRIPT_URL = "../api/composer/vs/viewsheet-property-dialog-model/test-script";

@Component({
   selector: "viewsheet-property-dialog",
   templateUrl: "viewsheet-property-dialog.component.html",
})
export class ViewsheetPropertyDialog extends BaseResizeableDialogComponent implements OnInit {
   @Input() model: ViewsheetPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   @Input() viewsheet: Viewsheet;
   @Input() isPrintLayout: boolean;
   @Input() openToScript: boolean = false;
   form: UntypedFormGroup;
   @Output() onCommit: EventEmitter<ViewsheetPropertyDialogModel> =
      new EventEmitter<ViewsheetPropertyDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   private orgInfo: CommonKVModel<string, string> = null;

   constructor(private httpClient: HttpClient, private modalService: NgbModal,
               protected renderer: Renderer2, protected element: ElementRef,
               private appInfoService: AppInfoService)
   {
      super(renderer, element);

      this.appInfoService.getCurrentOrgInfo().subscribe((orgInfo) => {
         this.orgInfo = orgInfo;
      })
   }

   formValid = () => {
      return this.model && this.form && this.form.valid;
   };

   ngOnInit(): void {
      this.form = new UntypedFormGroup({
         viewsheetOptionsPaneForm: new UntypedFormGroup({}),
         screensPaneForm: new UntypedFormGroup({}),
      });
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.testScript(true);
   }

   testScript(supportTimeout: boolean = true): void {
      const params = new HttpParams().set("runtimeId", this.model.id);

      this.httpClient.post(VIEWSHEET_PROPERTY_TEST_SCRIPT_URL, this.model, { params: params })
         .pipe(
           timeout(supportTimeout ? 5000 : null),
            catchError(error => this.handleError(error))
         )
         .subscribe((result: any) => {
            if(result?.timeout) {
               let timeoutMsg = "_#(js:composer.vs.testScript.timeout)";

               ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", timeoutMsg,
                  {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
                  .then(option => {
                     if(option == "yes") {
                        this.onCommit.emit(this.model);
                     }
                     else {
                        this.testScript(false);
                     }
                  });

               return;
            }

            if(!!result?.body) {
               let msg = "_#(js:composer.vs.testScript)" + "\n" + result?.body;

               ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg,
                  {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
                  .then(option => {
                     if(option == "yes") {
                        this.onCommit.emit(this.model);
                     }
                  });
            }
            else {
               this.onCommit.emit(this.model);
            }
         });
   }

   handleError(error: any): Observable<any> {
      if(error?.name == "TimeoutError") {
         let res = {
            timeout: true
         };

         return of(res);
      }

      return of(error)
   }

   isDefaultOrgAsset() {
      if(!this.viewsheet || !this.viewsheet.id) {
         return false;
      }

      let assetEntry: AssetEntry = createAssetEntry(this.viewsheet.id);
      return assetEntry?.organization != this.orgInfo.key;
   }
}
