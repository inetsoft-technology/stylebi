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
      if(index === this.model.dashboards.length - 1) {
         return false;
      }

      return this.model.dashboards[index].enabled &&
         this.model.dashboards[index + 1].enabled;
   }

   canMoveUp(index: number): boolean {
      if(index === 0) {
         return false;
      }

      return this.model.dashboards[index].enabled &&
         this.model.dashboards[index - 1].enabled;
   }

   enabledChanged(index: number): void {
      const dashboard = this.model.dashboards[index];
      this.model.dashboards.splice(index, 1);

      if(dashboard.enabled) {
         for(let i = 0; i < this.model.dashboards.length; i++) {
            if(!this.model.dashboards[i].enabled) {
               this.model.dashboards.splice(i, 0, dashboard);
               return;
            }
         }
      }
      else {
         for(let i = index; i < this.model.dashboards.length; i++) {
            if(!this.model.dashboards[i].enabled) {
               this.model.dashboards.splice(i, 0, dashboard);
               return;
            }
         }
      }

      this.model.dashboards.push(dashboard);
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
