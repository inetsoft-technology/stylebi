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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { ToolbarActionGroup } from "../../widget/toolbar/toolbar-action-group";
import { ToolbarAction } from "../../widget/toolbar/toolbar-action";

@Component({
   selector: "object-wizard-tool-bar",
   templateUrl: "../../binding/editor/editor-title-bar.component.html",
   styleUrls: ["../../binding/editor/editor-title-bar.component.scss",
      "../../composer/gui/toolbar/composer-toolbar.component.scss"]
})
export class ObjectWizardToolBarComponent {
   @Input() runtimeId: string;
   @Input() sourceName: string;
   @Input() assemblyType: number;
   @Input() vsObject: VSObjectModel;
   @Input() isFullEditorVisible: boolean = false;
   @Output() onClose: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onFullEditor = new EventEmitter<VSObjectModel>();
   elementName: string = "_#(js:Visualization Recommender)";

   constructor(private context: ContextProvider, private http: HttpClient) {
   }

   get helpLink(): string {
      return "CreatingaViewsheet";
   }

   done() {
      this.close(true);
   }

   cancel() {
      this.close(false);
   }

   private close(ok: boolean) {
      this.onClose.emit(ok);
   }

   openFullEditor() {
      let params = new HttpParams().set("id", this.runtimeId);

      if(!!this.vsObject) {
         params = params.set("assemblyName", this.vsObject.absoluteName);
      }

      this.http.get("../api/vswizard/object/toolbar/full-editor", {params: params})
         .subscribe(() => this.onFullEditor.emit(this.vsObject));
   }

   getAssemblyTypeIcon(): string {
      if(!this.vsObject) {
         return "";
      }

      switch (this.vsObject.objectType) {
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
            label: "_#(js:Full Editor)",
            iconClass: "edit-icon",
            buttonClass: "full-editor-button",
            tooltip: () => "<b>_#(js:Go To Full Editor)</b>",
            enabled: () => true,
            visible: () => this.isFullEditorVisible,
            action: () => this.openFullEditor()
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
            tooltip: () => "<b>_#(js:Cancel)</b>",
            enabled: () => true,
            visible: () => true,
            action: () => this.cancel()
         }
      ];
}
