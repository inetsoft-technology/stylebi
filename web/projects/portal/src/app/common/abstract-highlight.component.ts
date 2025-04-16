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
import { Tool } from "../../../../shared/util/tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { HighlightDialogModel } from "../widget/highlight/highlight-dialog-model";
import { EventEmitter, Output, Directive } from "@angular/core";
import { ComponentTool } from "./util/component-tool";

@Directive()
export class AbstractHighlight {
   @Output() onCommit = new EventEmitter<HighlightDialogModel>();
   protected _model: HighlightDialogModel;
   protected conditionsChanged: boolean = false;

   constructor(protected modalService: NgbModal) {
   }

   validateHighlights(): boolean {
      let highlightsStr: string = "";
      let highlightsStr2: string = "";

      for(let highlight of this._model.highlights) {
         if(!highlight.foreground && !highlight.background &&
            (!highlight.fontInfo || !highlight.fontInfo.fontFamily) ||
            (!highlight.vsConditionDialogModel ||
               highlight.vsConditionDialogModel.conditionList.length == 0))
         {
            highlightsStr = highlightsStr + "[" + highlight.name + "]";
         }
         else if(highlight.applyRow && !this.validateRowHighlightName(highlight.name)) {
            highlightsStr2 = highlightsStr2 + "[" + highlight.name + "]";
         }
      }

      if(highlightsStr.length > 0) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            Tool.formatCatalogString("_#(js:highlights.incomplete.definitions)", [highlightsStr]));
         return false;
      }
      else if(highlightsStr2.length > 0) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            Tool.formatCatalogString("_#(js:highlight.names.already.used)", [highlightsStr2]));
         return false;
      }
      else {
         return true;
      }
   }

   validateRowHighlightName(name: String): boolean {
      if(this._model.usedHighlightNames != null) {
         for(let usedName of this._model.usedHighlightNames) {
            if(name == usedName) {
               return false;
            }
         }
      }

      return true;
   }

   confirmConditionChanges(callback: () => any) {
      ComponentTool.showConfirmDialog(
         this.modalService, "Conditions Modified",
         "The edited form data will be lost. Continue anyway?")
         .then((result: string) => {
            if(result === "ok") {
               callback();
            }
         });
   }

   ok(): void {
      // Check if highlights are valid and if not, display message why
      if(this.validateHighlights()) {
         // For form tables, if a user has modified the table in the viewer and changes the
         // highlights, this will remove the modifications (inserted/appended/deleted row) so
         // confirm that the user wants to proceed
         if(this._model.confirmChanges && this.conditionsChanged) {
            this.confirmConditionChanges(() => {
               this.onCommit.emit(this._model);
            });
         }
         else {
            this.onCommit.emit(this._model);
         }
      }
   }
}
