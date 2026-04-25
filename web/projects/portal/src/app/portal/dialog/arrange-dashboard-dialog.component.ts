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
import { Component, EventEmitter, OnInit, Output } from "@angular/core";
import { ModelService } from "../../widget/services/model.service";
import { ArrangeDashboardDialogModel } from "./arrange-dashboard-dialog-model";

const ARRANGE_DASHBOARD_DIALOG_MODEL_URI: string = "../api/portal/arrange-dashboard-dialog-model";

@Component({
   selector: "arrange-dashboard-dialog",
   templateUrl: "arrange-dashboard-dialog.component.html",
})
export class ArrangeDashboardDialog implements OnInit {
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   model: ArrangeDashboardDialogModel;

   constructor(private modelService: ModelService) {
   }

   ngOnInit(): void {
      this.modelService.getModel(ARRANGE_DASHBOARD_DIALOG_MODEL_URI).subscribe(
         (data) => {
            this.model = <ArrangeDashboardDialogModel> data;
         }
      );
   }

   closeDialog(): void {
      this.onCancel.emit("cancel");
   }

   okClicked(): void {
      this.modelService.sendModel(ARRANGE_DASHBOARD_DIALOG_MODEL_URI, this.model).subscribe(
         (data) => {
            if(data) {
               this.onCommit.emit("ok");
            }
         },
         (err) => {
         }
      );
   }

   moveDashboard(fromRow: number, toRow: number): void {
      const fromElem = this.model.dashboards[fromRow];
      const toElem = this.model.dashboards[toRow];
      this.model.dashboards.splice(fromRow, 1, toElem);
      this.model.dashboards.splice(toRow, 1, fromElem);
   }

   canMoveDown(index: number): boolean {
      return index < this.model.dashboards.length - 1;
   }

   canMoveUp(index: number): boolean {
      return index > 0;
   }

   trackDashboard(index: number, dashboard: any): string {
      return dashboard.name || String(index);
   }

   enableAll(): void {
      for(let dashboard of this.model.dashboards) {
         dashboard.enabled = true;
      }
   }

   disableAll(): void {
      for(let dashboard of this.model.dashboards) {
         dashboard.enabled = false;
      }
   }

   isValid(): boolean {
      return true;
   }
}
