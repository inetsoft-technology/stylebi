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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { Tool } from "../../../../../../shared/util/tool";
import { ModelService } from "../../../widget/services/model.service";
import { VPMPrincipalDialogModel } from "../../data/ws/vpm-principal-dialog-model";
import { Worksheet } from "../../data/ws/worksheet";

const WORKSHEET_VPM_PRINCIPAL_URI = "../api/composer/ws/dialog/vpm-principal-dialog/";

@Component({
   selector: "vpm-principal-dialog",
   templateUrl: "vpm-principal-dialog.component.html"
})
export class VPMPrincipalDialogComponent implements OnInit {
   @Input() worksheet: Worksheet;
   @Output() onCommit = new EventEmitter<VPMPrincipalDialogModel>();
   @Output() onCancel = new EventEmitter<void>();
   model: VPMPrincipalDialogModel;
   form: UntypedFormGroup;
   sessionIds: string[];

   constructor(private modelService: ModelService) {
   }

   ngOnInit(): void {
      this.modelService.getModel(WORKSHEET_VPM_PRINCIPAL_URI + Tool.byteEncode(this.worksheet.runtimeId))
         .subscribe((model: VPMPrincipalDialogModel) => {
            this.model = model;

            if(model.vpmSelectable) {
               this.initForm();
            }
         });
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         vpmEnabled: new UntypedFormControl(),
         sessionType: new UntypedFormControl(),
         sessionId: new UntypedFormControl()
      });

      const sessionType = this.form.get("sessionType");
      const sessionId = this.form.get("sessionId");

      this.form.get("vpmEnabled").valueChanges.subscribe((value) => {
         if(value) {
            sessionType.enable({emitEvent: true});
            sessionId.enable({emitEvent: true});
         }
         else {
            sessionType.disable({emitEvent: false});
            sessionId.disable({emitEvent: false});
         }
      });

      sessionType.valueChanges.subscribe((value) => {
         this.resetSessionIds(value);
      });

      this.form.setValue({
         vpmEnabled: this.model.vpmEnabled,
         sessionType: this.model.sessionType,
         sessionId: this.model.sessionId
      });
   }

   ok(): void {
      if(this.form) {
         const newModel: VPMPrincipalDialogModel = {...this.model, ...this.form.getRawValue()};
         this.onCommit.emit(newModel);
      }
      else {
         this.onCommit.emit(null);
      }
   }

   cancel(): void {
      this.onCancel.emit();
   }

   private resetSessionIds(sessionType: string) {
      this.setSessionIds(sessionType);
      this.resetSessionId();
   }

   private setSessionIds(value: string): void {
      if(value === "user") {
         this.sessionIds = [...this.model.users];
      }
      else if(value === "role") {
         this.sessionIds = [...this.model.roles];
      }
   }

   private resetSessionId(): void {
      const sessionId = this.form.get("sessionId");
      let value = null;

      if(this.sessionIds && this.sessionIds.length > 0) {
         value = this.sessionIds[0];
      }

      sessionId.setValue(value);
   }
}
