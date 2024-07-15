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
import { Component, Input, Output, EventEmitter } from "@angular/core";

import { Sheet } from "../../data/sheet";
import { Viewsheet } from "../../data/vs/viewsheet";
import { GuiTool } from "../../../common/util/gui-tool";
import { HttpParams } from "@angular/common/http";
import { Tool } from "../../../../../../shared/util/tool";
import { map, mergeMap } from "rxjs/operators";
import { from, of } from "rxjs";
import { ComponentTool } from "../../../common/util/component-tool";
import { StompClientService, ViewsheetClientService } from "../../../common/viewsheet-client";
import { DataTipService } from "../../../vsobjects/objects/data-tip/data-tip.service";
import { ModelService } from "../../../widget/services/model.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComposerTabModel } from "../composer-tab-model";
import { LibraryAsset } from "../../data/library-asset";

/**
 * The worksheet tab selector component for the composer.
 */
@Component({
   selector: "sheet-tab-selector",
   templateUrl: "sheet-tab-selector.component.html",
   styleUrls: ["sheet-tab-selector.component.scss", "tab-selector-shared.scss"],
})
export class SheetTabSelectorComponent {
   /** Array of currently open tabs */
   @Input() tabs: ComposerTabModel[] = [];
   @Output() onTabSelected: EventEmitter<ComposerTabModel> = new EventEmitter<ComposerTabModel>();
   @Output() onTabClosed: EventEmitter<ComposerTabModel> = new EventEmitter<ComposerTabModel>();

   constructor(private modelService: ModelService, private modalService: NgbModal) {
   }

   isSelected(tab: ComposerTabModel): boolean {
      return tab?.asset.isFocused;
   }

   /**
    * Informs the component when a tab is selected.
    *
    * @param sheet the sheet that was selected
    */
   onSelect(tab: ComposerTabModel): void {
      if(!this.isSelected(tab)) {
         this.onTabSelected.emit(tab);
      }
   }

   /**
    * Closes the sheet.
    *
    * @param sheet the sheet to close
    */
   closeTab(tab: ComposerTabModel) {
      if(tab.type != "viewsheet") {
         this.onTabClosed.emit(tab);
         return;
      }

      let sheet: Sheet = <Sheet> tab.asset;

      const params = new HttpParams().set("runtimeId", Tool.byteEncode(sheet.runtimeId));
      this.modelService.getModel("../api/vs/checkFormTables", params)
         .pipe(
            mergeMap((showConfirm) => {
               if(showConfirm) {
                  const msg: string = "_#(js:common.warnUnsavedChanges)";
                  return from(
                     ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg)
                  ).pipe(map((result) => result === "ok"));
               }
               else {
                  return of(true);
               }
            })
         )
         .subscribe((close: boolean) => {
            if(close) {
               this.onTabClosed.emit(tab);
            }
         });
   }

   /** Returns css suffix according to type of sheet. */
   getTabClass(tab: ComposerTabModel): string {
      let clazz: string = "";

      switch(tab.type) {
         case "viewsheet":
            clazz = "viewsheet";
            break;
         case "worksheet":
            clazz = "worksheet";
            break;
         case "script":
            clazz = "script";
            break;
         case "tableStyle":
            clazz = "tableStyle";
            break;
         default:
      }

      return clazz;
   }

   getLabel(tab: ComposerTabModel): string {
      if(tab.asset.label != null && tab.asset.label.startsWith("Untitled")) {
         let left = tab.asset.label.substring(8);

         if(!isNaN(parseInt(left, 10))) {
            return "_#(js:Untitled)" + left;
         }
      }

      return tab.asset.label != null ? tab.asset.label : "";
   }

   get isEdge(): boolean {
      return GuiTool.isEdge();
   }

   isModified(tab: ComposerTabModel): boolean {
      if(tab.type == "viewsheet" || tab.type == "worksheet") {
         return (<Sheet> tab.asset).isModified();
      }
      else {
         return (<LibraryAsset> tab.asset).isModified;
      }
   }
}
