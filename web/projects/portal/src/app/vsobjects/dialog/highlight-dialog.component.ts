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
   Input,
   Output,
   EventEmitter,
   TemplateRef,
   ViewChild,
   OnInit
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { HighlightDialogModel } from "../../widget/highlight/highlight-dialog-model";
import { HighlightModel } from "../../widget/highlight/highlight-model";
import { VSConditionDialogModel } from "../../common/data/condition/vs-condition-dialog-model";
import { Tool } from "../../../../../shared/util/tool";
import { TrapInfo } from "../../common/data/trap-info";
import { VSTrapService } from "../util/vs-trap.service";
import { AbstractHighlight } from "../../common/abstract-highlight.component";
import {DataRef} from "../../common/data/data-ref";

const CHECK_TRAP_REST_URI: string = "../api/composer/viewsheet/check-highlight-dialog-trap/";

@Component({
   selector: "highlight-dialog",
   templateUrl: "highlight-dialog.component.html"
})
export class HighlightDialog extends AbstractHighlight implements OnInit {
   @Input() runtimeId: string;
   @Input() assemblyName: string;
   @Input() variableValues: string[];
   @Output() onCancel = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();
   @ViewChild("vsConditionDialog") vsConditionDialog: TemplateRef<any>;
   selectedHighlight: HighlightModel;
   fields: DataRef[];
   modalVSConditionDialogModel: VSConditionDialogModel;
   renameIndex: number = -1;
   checkTrapMethod: (callback: () => void, conditionModel: VSConditionDialogModel) => void =
      (callback: () => void, conditionModel: VSConditionDialogModel) => {
         this.checkTrap(callback, conditionModel);
      };

   @Input()
   set model(model: HighlightDialogModel) {
      this._model = model;
      this.fields = this.model.fields;
   }

   get model(): HighlightDialogModel {
      return this._model;
   }

   constructor(protected modalService: NgbModal,
               private trapService: VSTrapService) {
      super(modalService);
   }

   ngOnInit(): void {
      if(!!this.model && this.model.highlights.length > 0) {
         this.selectHighlight(this.model.highlights[0]);
      }
   }

   selectHighlight(highlight: HighlightModel) {
      this.selectedHighlight = highlight;
   }

   editConditions(): void {
      let options: NgbModalOptions = {
         size: "lg",
         backdrop: "static",
         windowClass: "condition-dialog"
      };

      // Send vs-condition-dialog a copy so changes to the model there
      // don't propogate to highlight dialog
      this.modalVSConditionDialogModel = Tool.clone(this.selectedHighlight.vsConditionDialogModel);

      this.modalService.open(this.vsConditionDialog, options).result.then(
         (result: VSConditionDialogModel) => {
            if(this.fields != null && this.fields.length > 0 &&
               (result.fields == null || result.fields.length == 0))
            {
               result.fields = this.fields;
            }

            this.conditionsChanged = !Tool.isEquals(
                                        this.selectedHighlight.vsConditionDialogModel, result);
            this.selectedHighlight.vsConditionDialogModel = result;
         },
         (reject: any) => {
         }
      );
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   checkTrap(callback: () => void, conditionModel: VSConditionDialogModel): void {
      const tempCondition: VSConditionDialogModel
         = Tool.clone(this.selectedHighlight.vsConditionDialogModel);
      this.selectedHighlight.vsConditionDialogModel = conditionModel;
      const trapInfo = new TrapInfo(CHECK_TRAP_REST_URI, this.assemblyName,
         this.runtimeId, Tool.clone(this.model));

      this.trapService.checkTrap(trapInfo,
         () => callback(),
         () => {},
         () => callback()
      );

      this.selectedHighlight.vsConditionDialogModel = tempCondition;
   }

   apply(event: boolean): void {
      this.onApply.emit({collapse: event, result: this.getServerAppliedModel()});
   }
}

