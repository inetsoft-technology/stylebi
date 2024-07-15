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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { VSWizardConstants } from "../../vs-wizard/model/vs-wizard-constants";
import { VSWizardUtil } from "../../vs-wizard/util/vs-wizard-util";
import { ToolbarActionGroup } from "../../widget/toolbar/toolbar-action-group";
import { ToolbarAction } from "../../widget/toolbar/toolbar-action";

@Component({
   selector: "editor-title-bar",
   templateUrl: "editor-title-bar.component.html",
   styleUrls: ["editor-title-bar.component.scss",
      "../../composer/gui/toolbar/composer-toolbar.component.scss"]
})
export class EditorTitleBar {
   private _elementName: string;

   @Input() set elementName(name: string) {
      this._elementName = name;
   }

   get elementName(): string {
      return VSWizardUtil.isTempAssembly(this._elementName) ?
         VSWizardConstants.TEMP_ASSEMBLY : this._elementName;
   }

   @Input() sourceName: string;
   @Input() objectType: string;
   @Input() goToWizardVisible: boolean = false;
   @Input() backToReportWizardVisible: boolean = false;
   @Input() reportMode: number;
   @Output() onClose = new EventEmitter<boolean>();
   @Output() onOpenWizardPane = new EventEmitter<any>();
   @Output() onOpenReportWizard = new EventEmitter<any>();
   @Output() onChangeReportMode = new EventEmitter<number>();

   done() {
      this.onClose.emit(true);
   }

   cancel() {
      this.onClose.emit(false);
   }

   getAssemblyTypeIcon(): string {
      switch(this.objectType) {
         case "VSChart":
            return "chart-icon";
         case "VSCrosstab":
            return "crosstab-icon";
         case "VSTable":
            return "table-icon";
         case "VSGauge":
            return "gauge-icon";
         case "VSText":
            return "text-icon";
         case "VSSelectionList":
            return "selection-list-icon";
         case "VSSelectionTree":
            return "selection-tree-icon";
         case "VSCalendar":
            return "calendar-icon";
         case "VSRangeSlider":
            return "range-slider-icon";
         default:
            return "";
      }
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

   get actionGroup(): ToolbarActionGroup {
      return <ToolbarActionGroup> {
         label: "",
         iconClass: "",
         buttonClass: "",
         enabled: () => true,
         visible: () => true,
         action: () => {},
         actions: this.actions
      };
   }

   actions: ToolbarAction[] =
      [
         {
            label: "_#(js:To Wizard)",
            iconClass: "edit-icon",
            buttonClass: "wizard-button",
            tooltip: () => "<b>_#(js:Go To Wizard)</b>",
            enabled: () => true,
            visible: () => this.goToWizardVisible,
            action: () => this.onOpenWizardPane.emit()
         },
         {
            label: "_#(js:To Wizard)",
            iconClass: "edit-icon",
            buttonClass: "wizard-button",
            tooltip: () => "<b>_#(js:Back To Wizard)</b>",
            enabled: () => true,
            visible: () => this.backToReportWizardVisible,
            action: () => this.onOpenReportWizard.emit()
         },
         {
            label: "_#(js:Finish)",
            iconClass: "submit-icon",
            buttonClass: "finish-button",
            tooltip: () => "<b>_#(js:Finish Editing)</b>",
            enabled: () => true,
            visible: () => true,
            action: () => this.done()
         },
         {
            label: "_#(js:Cancel)",
            iconClass: "close-icon",
            buttonClass: "close-button",
            tooltip: () => "<b>_#(js:Cancel)<b>",
            enabled: () => true,
            visible: () => true,
            action: () => this.cancel()
         }
      ];
}
