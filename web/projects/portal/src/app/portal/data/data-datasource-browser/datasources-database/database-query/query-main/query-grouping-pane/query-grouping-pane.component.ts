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
import {
   Component,
   EventEmitter,
   Input,
   Output,
   ViewChild,
   ViewEncapsulation
} from "@angular/core";
import { NgbNavChangeEvent } from "@ng-bootstrap/ng-bootstrap/nav/nav";
import {
   QueryGroupingPaneModel
} from "../../../../../model/datasources/database/query/query-grouping-pane-model";
import {
   OperationModel
} from "../../../../../model/datasources/database/vpm/condition/clause/operation-model";
import { GroupingPaneTabs } from "../../data-query-model.service";
import {
   QueryConditionsPaneComponent
} from "../query-condition-pane/query-conditions-pane.component";

@Component({
   selector: "query-grouping-pane",
   templateUrl: "./query-grouping-pane.component.html",
   styleUrls: ["./query-grouping-pane.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class QueryGroupingPaneComponent {
   @Input() runtimeId: string;
   @Input() model: QueryGroupingPaneModel;
   @Input() databaseName: string;
   @Input() operations: OperationModel[];
   @Input() sessionOperations: OperationModel[];
   @Input() queryFieldsMap: Map<string, string>;
   @Output() groupByValidityChange = new EventEmitter<boolean>();
   @ViewChild("havingConditionsPane") havingConditionsPane: QueryConditionsPaneComponent;
   activeTab: string = GroupingPaneTabs.GROUP_BY;

   updateGroupingPaneTab(event: NgbNavChangeEvent): void {
      event.preventDefault();
      let nextTab = event.nextId;

      if(this.activeTab == GroupingPaneTabs.HAVING) {
         this.havingConditionsPane.checkDirtyConditions().then(() => {
            this.activeTab = nextTab;
         });
      }
      else {
         this.activeTab = nextTab;
      }
   }

   fieldsChanged(fields: string[]): void {
      if(!fields || fields.length == 0) {
         this.model.havingConditions.conditions = [];
      }
   }

   public isGroupByPane(): boolean {
      return this.activeTab == GroupingPaneTabs.GROUP_BY;
   }
}
