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
import { HttpClient } from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { ActivatedRoute, NavigationExtras, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable ,  Subscription } from "rxjs";
import { tap } from "rxjs/operators";
import { DashboardModel } from "../../common/data/dashboard-model";
import { AssetLoadingService } from "../../common/services/asset-loading.service";
import { GuiTool } from "../../common/util/gui-tool";
import { Tool } from "../../../../../shared/util/tool";
import { ModelService } from "../../widget/services/model.service";
import { ArrangeDashboardDialog } from "../dialog/arrange-dashboard-dialog.component";
import { EditDashboardDialog } from "../dialog/edit-dashboard-dialog.component";
import { CurrentRouteService } from "../services/current-route.service";
import { DashboardTabModel } from "./dashboard-tab-model";
import { DashboardService } from "./dashboard.service";
import { HideNavService } from "../services/hide-nav.service";
import { ComponentTool } from "../../common/util/component-tool";

const DASHBOARD_TAB_MODEL_URI: string = "../api/portal/dashboard-tab-model";
const DELETE_DASHBOARD_URI: string = "../api/portal/dashboard/deleteDashboard/";

@Component({
   templateUrl: "dashboard-tab.component.html",
   styleUrls: ["../portal-tab.component.scss", "dashboard-tab.component.scss"],
   providers: [ DashboardService ]
})
export class DashboardTabComponent implements OnInit, OnDestroy {
   model: DashboardTabModel;
   selectedDashboard: DashboardModel;
   selectedDashboardIndex: number = -1;
   selectedDashboardName: string;
   mobile: boolean;
   private subscriptions = new Subscription();

   constructor(private modalService: NgbModal,
               private modelService: ModelService,
               private router: Router,
               route: ActivatedRoute,
               private http: HttpClient,
               private hideNavService: HideNavService,
               dashboardService: DashboardService,
               currentRouteService: CurrentRouteService,
               private assetLoadingService: AssetLoadingService)
   {
      this.subscriptions.add(dashboardService.newDashboard.subscribe(
         () => this.newDashboard()
      ));

      this.subscriptions.add(currentRouteService.dashboard.subscribe(
         (dashboardName) => {
            this.selectedDashboardName = dashboardName;

            if(this.model) {
               this.selectedDashboardIndex = this.model.dashboards.findIndex(
                  (db) => db.name === dashboardName);

               if(this.selectedDashboardIndex < 0) {
                  this.selectedDashboardIndex = this.model.dashboards.findIndex(
                     (db) => db.identifier === dashboardName);
                  this.selectedDashboardName = this.selectedDashboardIndex < 0 ?
                     null : this.model.dashboards[this.selectedDashboardIndex].name;
               }

               this.selectedDashboard = this.selectedDashboardIndex < 0 ?
                  null : this.model.dashboards[this.selectedDashboardIndex];
            }
         }
      ));

      this.subscriptions.add(route.data.subscribe(
         (data) => {
            this.model = data.dashboardTabModel;
         }
      ));
   }

   ngOnInit() {
      this.mobile = GuiTool.isMobileDevice();

      if(this.model && this.model.dashboards.length > 0) {
         const selectionIndex =
            this.model.dashboards.findIndex((db) => db.name === this.selectedDashboardName);

         if(selectionIndex >= 0) {
            this.selectedDashboardIndex = selectionIndex;
            this.selectedDashboard = Tool.clone(this.model.dashboards[selectionIndex]);
         }
      }
      else {
         this.router.navigate(["/portal/tab/dashboard"]);
      }
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   selectTab(index: number): void {
      const reload = this.selectedDashboard != null &&
         this.selectedDashboard.name == this.model.dashboards[index].name;
      const confirm = reload && this.assetLoadingService.isLoading(this.selectedDashboard.name);
      this.selectDashboard(this.model.dashboards[index], reload, confirm);
   }

   selectDashboard(dashboard: DashboardModel, reloading: boolean = false, confirm: boolean = false) {
      this.selectedDashboard = Tool.clone(dashboard);
      this.openDashboard(dashboard, reloading, confirm);
   }

   private openDashboard(dashboard: DashboardModel, reloading: boolean, confirm: boolean = false) {
      const hideNavExtra: NavigationExtras = {
         queryParams: this.hideNavService.appendParameter({})
      };

      if(reloading) {
         if(confirm) {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
               "_#(js:repository.tree.confirmReloadEntry)")
               .then((buttonClicked) => {
                  if(buttonClicked === "ok") {
                     this.reloadDashboard(dashboard, hideNavExtra);
                  }
               });
         }
         else {
            this.reloadDashboard(dashboard, hideNavExtra);
         }
      }
       else {
         this.router.navigate([`/portal/tab/dashboard/vs/dashboard/${dashboard.name}`], hideNavExtra);
       }
   }

   private reloadDashboard(dashboard: DashboardModel, hideNavExtra: NavigationExtras) {
      const tempExtras: NavigationExtras = {
         queryParams: this.hideNavService.appendParameter({notLoadDashboard: "true"})
      };

      this.router.navigate([`/portal/tab/dashboard`], tempExtras).then(
         () => this.router.navigate([`/portal/tab/dashboard/vs/dashboard/${dashboard.name}`], hideNavExtra)
      );
   }

   private updateModel(): Observable<DashboardTabModel> {
      return this.modelService.getModel<DashboardTabModel>(DASHBOARD_TAB_MODEL_URI).pipe(
         tap((model) => this.model = model)
      );
   }

   newDashboard() {
      this.editDashboard(null);
   }

   editDashboard(dashboard: DashboardModel) {
      const dialog = ComponentTool.showDialog(this.modalService, EditDashboardDialog, (result: DashboardModel) => {
            this.updateModel().subscribe((model) => {
               if(model.dashboards.length > 0) {
                  for(let i = 0; i < model.dashboards.length; i++) {
                     if(model.dashboards[i].name === result.name) {
                        this.selectedDashboardIndex = i;
                        this.selectDashboard(model.dashboards[i], true);
                        break;
                     }
                  }
               }
            });
         }, { size: "lg", backdrop: "static" });
      dialog.dashboard = Tool.clone(dashboard);
      dialog.composerEnabled = this.model.composerEnabled;
   }

   deleteDashboard() {
      const msg = "_#(js:em.common.dashboardDelete)";
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg).then(
         (result: string) => {
            if(result === "ok") {
               this.http.delete(DELETE_DASHBOARD_URI + this.selectedDashboard.name)
                  .subscribe((success) => {
                     if(success) {
                        this.updateModel().subscribe((model) => {
                           if(model.dashboards && model.dashboards.length > 0) {
                              this.selectedDashboardIndex = 0;
                              this.selectDashboard(model.dashboards[0]);
                           }
                           else {
                              this.selectedDashboardIndex = -1;
                              this.selectedDashboard = null;
                              this.router.navigate(["/portal/tab/dashboard"]);
                           }
                        });
                     }
                     else {
                        ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                           "_#(js:viewer.dashboardDeleteError)");
                     }
                  });
            }
         });
   }

   openArrangeDashboardDialog(): void {
      ComponentTool.showDialog(this.modalService, ArrangeDashboardDialog, (result) => {
         this.updateModel().subscribe((model) => {
            if(model.dashboards.length > 0) {
               if(this.selectedDashboard) {
                  this.selectedDashboardIndex = model.dashboards.findIndex(
                     (db) => db.name === this.selectedDashboard.name);

                  if(this.selectedDashboardIndex >= 0) {
                     return;
                  }
               }

               this.selectedDashboardIndex = 0;
               this.selectDashboard(model.dashboards[0]);
            }
            else {
               this.selectedDashboardIndex = -1;
               this.selectedDashboard = null;
               this.router.navigate(["/portal/tab/dashboard"]);
            }
         });
      }, { size: "lg", backdrop: "static" });
   }

   getDashboardLabel(dashboard: DashboardModel): string {
      return dashboard.label;
   }
}
