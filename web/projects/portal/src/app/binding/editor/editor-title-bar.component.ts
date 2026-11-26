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
import { Component, EventEmitter, inject, Input, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../common/util/component-tool";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../../common/services/ai-assistant-dialog.service";
import { VSWizardConstants } from "../../vs-wizard/model/vs-wizard-constants";
import { VSWizardUtil } from "../../vs-wizard/util/vs-wizard-util";
import { HelpLinkDirective } from "../../widget/help-link/help-link.directive";

@Component({
    selector: "editor-title-bar",
    templateUrl: "editor-title-bar-return.component.html",
    styleUrls: ["editor-title-bar.component.scss",
        "../../composer/gui/toolbar/composer-toolbar.component.scss"],
    imports: [HelpLinkDirective]
})
export class EditorTitleBar {
   private _elementName: string;

   @Input() set elementName(name: string) {
      this._elementName = name;
   }

   get elementName(): string {
      if(VSWizardUtil.isTempAssembly(this._elementName)) {
         // the recommender's temporary assembly is being edited in the full editor
         return this.objectType == "VSChart" ? "_#(js:Full chart editor)" :
            VSWizardConstants.TEMP_ASSEMBLY;
      }

      return this._elementName;
   }

   @Input() sourceName: string;
   /** The name of the sheet being edited, shown in the breadcrumb beside the way back to it. */
   @Input() sheetName: string;
   @Input() objectType: string;
   @Input() goToWizardVisible: boolean = false;
   @Input() backToReportWizardVisible: boolean = false;
   @Input() reportMode: number;
   @Output() onClose = new EventEmitter<boolean>();
   @Output() onOpenWizardPane = new EventEmitter<any>();
   @Output() onOpenReportWizard = new EventEmitter<any>();
   @Output() onChangeReportMode = new EventEmitter<number>();

   private readonly modalService = inject(NgbModal);

   constructor(public aiAssistantDialogService: AiAssistantDialogService, private aiAssistantService: AiAssistantService) {
   }

   done() {
      this.onClose.emit(true);
   }

   cancel() {
      this.onClose.emit(false);
   }

   /**
    * Go back to the sheet the editor was opened from. The editor does not know whether anything was
    * changed, so it asks what to do with the edit: apply it (as Done does), discard it (as Cancel
    * does) or keep editing.
    */
   back(): void {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Return to the dashboard)",
         "_#(js:Apply or discard your changes before returning to the dashboard.)",
         {
            apply: "_#(js:Apply and return)",
            discard: "_#(js:Discard changes)",
            keep: "_#(js:Keep editing)"
         })
         .then(choice => {
            if(choice == "apply") {
               this.done();
            }
            else if(choice == "discard") {
               this.cancel();
            }
         })
         .catch(() => {
            // a duplicate message was ignored; the dialog is already open
         });
   }

   get helpLink(): string {
      switch(this.objectType) {
         case "VSTable":
            return "CreatingTable";
         case "VSChart":
            return "CreatingaChart";
         case "VSCrosstab":
            return "CreatingCrosstab";
         case "VSCalcTable":
            return "CreatingFreehand";
         default:
            return null;
      }
   }

   openAiAssistantDialog(): void {
      this.aiAssistantService.lastBindingObject =
         (this.objectType && this.elementName) ? this.objectType + "^^" + this.elementName : "";
      this.aiAssistantDialogService.openAiAssistantDialog();
   }
}
