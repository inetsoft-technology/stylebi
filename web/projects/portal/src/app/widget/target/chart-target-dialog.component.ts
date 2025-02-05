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

import { Component, Input, Output, OnInit, EventEmitter, ViewChild } from "@angular/core";
import { TargetInfo, MeasureInfo } from "./target-info";
import { Tool } from "../../../../../shared/util/tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { LinePanel } from "./line-panel.component";
import { BandPanel } from "./band-panel.component";
import { StatPanel } from "./stat-panel.component";
import { UIContextService } from "../../common/services/ui-context.service";
import { ComponentTool } from "../../common/util/component-tool";
import { GraphTypes } from "../../common/graph-types";

@Component({
   selector: "chart-target-dialog",
   templateUrl: "chart-target-dialog.component.html",
})
export class ChartTargetDialog implements OnInit {
   @Input() chartTarget: TargetInfo;
   @Input() availableFields: MeasureInfo[];
   @Input() title: string = "_#(js:Add Target)";
   @Input() variables: string[] = [];
   @Input() hideDcombox: boolean;
   @Input() vsId: string = null;
   @Input() assetId: string = null;
   @Input() chartType: number;
   @Output() confirm: EventEmitter<TargetInfo> = new EventEmitter<TargetInfo>();
   @Output() cancel: EventEmitter<string> = new EventEmitter<string>();
   labelValues: string[] =  ["", "(_#(js:Target Value))", "(_#(js:Target Formula))", "(_#(js:Field Name))"];

   @ViewChild(LinePanel) linePanel: LinePanel;
   @ViewChild(BandPanel) bandPanel: BandPanel;
   @ViewChild(StatPanel) statPanel: StatPanel;
   activeTab: string;

   constructor(private modalService: NgbModal,
               private uiContextService: UIContextService)
   {
   }

   cancelChanges(): void {
      this.cancel.emit("cancel");
   }

   getLineAvailableFields() {
      let availFields: MeasureInfo[] = [];

      for(let field of this.availableFields) {
         let found = false;

         if(!!this.variables) {
            for(let variable of this.variables) {
               if(field.label == variable) {
                  found = true;
                  break;
               }
            }
         }

         if(!found) {
            availFields.push(field);
         }
      }

      return availFields;
   }

   saveChanges(): void {
      if(this.chartTarget.tabFlag == 2 &&
         this.chartTarget.measure && this.chartTarget.measure.dateField)
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:designer.chartProp.statisticsDateField)");
         return;
      }

      if(this.chartTarget.label && this.chartTarget.label.startsWith("(")) {
         let index = this.labelValues.indexOf(this.chartTarget.label);
         this.chartTarget.label = index > 0 ? ("{" + (index - 1) + "}") : this.chartTarget.label;
      }

      if(this.chartTarget.toLabel && this.chartTarget.toLabel.startsWith("(")) {
         let index = this.labelValues.indexOf(this.chartTarget.toLabel);
         this.chartTarget.toLabel = index > 0 ? ("{" + (index - 1) + "}") : this.chartTarget.toLabel;
      }

      this.confirm.emit(this.chartTarget);
   }

   ngOnInit() {
      for(let entry of this.availableFields) {
         let found: boolean = entry.label == this.chartTarget.measure.label;

         if(found) {
            this.chartTarget.measure = entry;
         }
      }

      this.activeTab = this.chartTarget.tabFlag == 2 && this.isBoxplot() ? null
         : `tab${this.chartTarget.tabFlag}`;
   }

   isValid(): boolean {
      if(this.chartTarget.tabFlag == 0 || this.chartTarget.tabFlag == 1) {
         if(!this.chartTarget.value) {
            return false;
         }

         if(this.chartTarget.tabFlag == 1 && !this.chartTarget.toValue) {
            return false;
         }
      }
      else if(!this.chartTarget.measure || !this.chartTarget.measure.label) {
         return false;
      }

      if(this.linePanel && !this.linePanel.isValid()) {
         return false;
      }

      if(this.bandPanel && !this.bandPanel.isValid()) {
         return false;
      }

      if(this.statPanel && this.statPanel.alphaInvalid) {
         return false;
      }

      return true;
   }

   get cshid(): string {
      return "AddingTargetLineVS";
   }

   isBoxplot(): boolean {
      return GraphTypes.CHART_BOXPLOT == this.chartType;
   }

   tabChange(event: any) {
      this.chartTarget.tabFlag = event.nextId.substring(3);
      this.activeTab = `tab${this.chartTarget.tabFlag}`;
   }
}
