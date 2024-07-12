/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Input, OnInit, TemplateRef, ViewChild } from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../shared/util/tool";
import { ChartConfig } from "../../common/util/chart-config";
import { TargetInfo } from "../../widget/target/target-info";
import { ChartTargetLinesPaneModel } from "../model/dialog/chart-target-lines-pane-model";

@Component({
   selector: "chart-target-lines-pane",
   templateUrl: "chart-target-lines-pane.component.html",
   styleUrls: ["chart-target-lines-pane.component.scss"]
})
export class ChartTargetLinesPane implements OnInit {
   @Input() model: ChartTargetLinesPaneModel;
   @Input() variables: string[];
   @Input() hideDcombox: boolean;
   @Input() vsId: string = null;
   @Input() chartType: number;
   selectedIndexes: number[];
   chartTargetModel: TargetInfo;
   editing: boolean = false;
   @ViewChild("chartTargetDialog") chartTargetDialog: TemplateRef<any>;

   get dialogTitle(): string {
      return this.editing ? "_#(js:Edit Target)" : "_#(js:Add Target)";
   }

   constructor(private modalService: NgbModal) {
   }

   ngOnInit() {
      if(this.model.chartTargets.length > 0) {
         this.selectedIndexes = [0];
      }
   }

   select(index: number, evt: MouseEvent) {
      const posInSelected = this.selectedIndexes.indexOf(index);

      if(!evt.ctrlKey && !evt.metaKey && !evt.shiftKey) {
         this.selectedIndexes = [];
      }

      if(evt.shiftKey) {
         if(this.selectedIndexes == null || this.selectedIndexes.length == 0) {
            this.selectedIndexes.push(index);
            return;
         }

         const last = this.selectedIndexes[this.selectedIndexes.length - 1];
         this.selectedIndexes = [];

         // First add the new selected index
         this.selectedIndexes.push(index);

         // Then add all values between new selected index and last
         for (let i = Math.min(index, last) + 1; i < Math.max(index, last); i++) {
            this.selectedIndexes.push(i);
         }

         // Keep the last index unchanged by pushing it in the end
         if(last != index) {
            this.selectedIndexes.push(last);
         }
      }
      else if(evt.ctrlKey) {
         if(posInSelected >= 0) {
            this.selectedIndexes.splice(posInSelected, 1);
         }
         else {
            this.selectedIndexes.push(index);
         }
      }
      else {
         this.selectedIndexes.push(index);
      }
   }

   addTarget() {
      this.editing = false;
      this.chartTargetModel = Tool.clone(this.model.newTargetInfo);
      const options: NgbModalOptions = {windowClass: "chart-target-dialog", backdrop: "static"};
      this.modalService.open(this.chartTargetDialog, options).result.then(
         (result: TargetInfo) => {
            result.index = -1;
            result.changed = true;
            result.targetString = this.createTargetString(result);
            this.model.chartTargets.push(result);
            this.selectedIndexes = [this.model.chartTargets.length - 1];
         },
         (reason: String) => {
            //Cancel
         }
      );
   }

   editTarget(): void {
      this.editing = true;

      if(this.selectedIndexes) {
         const indexToBeEdit = this.selectedIndexes[0];
         this.chartTargetModel = Tool.clone(this.model.chartTargets[indexToBeEdit]);
         const options: NgbModalOptions = {windowClass: "chart-target-dialog", backdrop: "static"};
         this.modalService.open(this.chartTargetDialog, options).result.then(
            (result: TargetInfo) => {
               result.changed = true;
               result.targetString = this.createTargetString(result);
               this.model.chartTargets[indexToBeEdit] = <TargetInfo> result;
            },
            (reason: String) => {
               //Cancel
            }
         );
      }
   }

   deleteTarget(): void {
      this.selectedIndexes = this.selectedIndexes.sort((a, b) => b - a);
      this.selectedIndexes.forEach((index) => {
         if(index > -1) {
            if(this.model.chartTargets[index].index >= 0) {
               this.model.deletedIndexList.push(this.model.chartTargets[index].index);
            }

            this.model.chartTargets.splice(index, 1);
         }

         if(this.model.chartTargets.length > 0) {
            this.selectedIndexes = [Math.max(0, index - 1)];
         }
         else {
            this.selectedIndexes = undefined;
         }
      });
   }

   createTargetString(target: TargetInfo): string {
      let result = "";
      let value = target.value.startsWith("=") ? target.value.substring(1, target.value.length)
         : target.value;

      // Trim if too long
      if(value != null && value.length > 20) {
         value = value.substring(0, 17) + "...";
      }

      let toValue = target.toValue.startsWith("=")
         ? target.toValue.substring(1, target.toValue.length) : target.toValue;

      if(target.tabFlag == 0) {
         result += "Line " + value;
      }
      else if(target.tabFlag == 1) {
         result += "Band " + value + " ->" + toValue;
      }
      else if(target.tabFlag == 2) {
         let strategyInfo = target.strategyInfo;
         result += " " + strategyInfo.value +
            (strategyInfo.name == "Confidence Interval" ? "%" : "");
         result += " " + strategyInfo.name +
            (strategyInfo.name == "Percentage" ? ("s " + strategyInfo.percentageAggregateVal) : "");
      }

      if(target.measure && target.measure.label) {
         result += " of " + target.measure.label;
      }

      result += " [" + ChartConfig.getLineStyleName(target.lineStyle) + "]";
      return result;
   }
}
