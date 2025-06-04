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
import { Component, EventEmitter, Input, Output, TemplateRef, ViewChild } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ConditionItemPaneProvider } from "../../common/data/condition/condition-item-pane-provider";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { GuiTool } from "../../common/util/gui-tool";

@Component({
   selector: "condition-list",
   templateUrl: "condition-list.component.html",
   styleUrls: ["./condition-list.component.scss"]
})
export class ConditionList {
   public XSchema = XSchema;
   @Input() showDefaultButtons: boolean = true;
   @Input() simplePane: boolean = false;
   @Input() provider: ConditionItemPaneProvider;
   @Input() fields: DataRef[];
   @Input() subqueryTables: SubqueryTable[];
   @Input() conditionList: any[]; // even indexes contain conditions, odd contain junctions
   @Input() isVSContext = true;
   @Input() showOriginalName: boolean = false;
   @Output() conditionListChange: EventEmitter<any[]> = new EventEmitter<any[]>();
   selectedIndex: number;
   @ViewChild("conditionDialog") conditionDialog: TemplateRef<any>;

   constructor(private modalService: NgbModal) {
   }

   edit(): void {
      const oldConditionList = this.conditionList;

      this.modalService.open(this.conditionDialog, {size: "xl", backdrop: false}).result
         .then((conditionList: any[]) => {
               this.conditionListChange.emit(conditionList);
            }, () => {
               this.conditionListChange.emit(oldConditionList);
            }
         );
   }

   clear(): void {
      this.conditionListChange.emit([]);
   }
}
