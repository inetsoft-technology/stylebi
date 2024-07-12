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
import { Component, Input } from "@angular/core";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { PeriodPaneModel } from "../../model/period-pane-model";

@Component({
   selector: "date-comparison-periods-pane",
   templateUrl: "./date-comparison-periods-pane.component.html",
   styleUrls: ["./date-comparison-periods-pane.component.scss"]
})
export class DateComparisonPeriodsPaneComponent {
   @Input() periodPaneModel: PeriodPaneModel;
   @Input() variableValues: string[] = [];
   @Input() disable: boolean = false;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Input() toDateDisabled: boolean = false;
   @Input() intervalEndDate: any = null;
   @Input() weekly: boolean = false;
   isValidStandardPeriod: boolean;
   isValidCustomPeriod: boolean;

   get isValidPeriod(): boolean {
      return this.periodPaneModel.custom ? this.isValidCustomPeriod : this.isValidStandardPeriod;
   }

   validChanged(valid: boolean): void {
      if(this.periodPaneModel.custom) {
         this.isValidCustomPeriod = valid;
      }
      else {
         this.isValidStandardPeriod = valid;
      }
   }
}
