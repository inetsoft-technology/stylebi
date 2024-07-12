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
import { Component, OnDestroy, OnInit } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbDatepickerConfig, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { ComponentTool } from "../common/util/component-tool";
import { GuiTool } from "../common/util/gui-tool";
import { SetPrincipalCommand } from "../vsobjects/command/set-principal-command";
import { DragService } from "../widget/services/drag.service";
import { ResizeHandlerService } from "./gui/resize-handler.service";
import { ComposerRecentService } from "./gui/composer-recent.service";

@Component({
   selector: "composer-app",
   templateUrl: "app.component.html",
})
export class ComposerAppComponent implements OnInit, OnDestroy {
   initialSheet: string;
   baseWS: string;
   runtimeId: string;
   deployed: boolean = false;
   wsWizard: boolean;
   vsWizard: boolean;
   styleWizard: boolean;
   scriptWizard: boolean;
   baseDataSource: string; // ws wizard base datasource
   baseDataSourceType: number; // ws wizard base datasource type
   closeOnComplete: boolean;
   saveToFolderId: string;
   principal: SetPrincipalCommand;

   constructor(private dragService: DragService,
               route: ActivatedRoute,
               private router: Router,
               private resizeHandlerService: ResizeHandlerService,
               private ngbDatepickerConfig: NgbDatepickerConfig,
               private titleService: Title,
               private modalService: NgbModal,
               private firstDayOfWeekService: FirstDayOfWeekService,
               private composerRecentService: ComposerRecentService)
   {
      titleService.setTitle("_#(js:Visual Composer)");
      // Need to set a default min and max date otherwise the range is only 20 years.
      ngbDatepickerConfig.minDate = {year: 1900, month: 1, day: 1};
      ngbDatepickerConfig.maxDate = {year: 2099, month: 12, day: 31};

      route.data.subscribe(
         (data) => {
            this.principal = data.setPrincipalCommand;
            this.composerRecentService.currentUser = this.principal?.principal;
         }
      );
   }

   ngOnInit() {
      if(document.body.className.indexOf("app-loaded") == -1) {
         document.body.className += " app-loaded";
      }

      this.dragService.initListeners(document);
      this.resizeHandlerService.initListeners();
      const searchParams = GuiTool.getQueryParameters();
      const vsId = searchParams.has("vsId") ? searchParams.get("vsId")[0] : null;
      const wsId = searchParams.has("wsId") ? searchParams.get("wsId")[0] : null;
      this.initialSheet = vsId || wsId;
      this.baseWS = searchParams.has("baseWS") ? searchParams.get("baseWS")[0] : null;
      this.wsWizard =
         searchParams.has("wsWizard") && searchParams.get("wsWizard")[0] === "true";
      this.baseDataSource =
         searchParams.has("baseDataSource") ? searchParams.get("baseDataSource")[0] : null;
      this.baseDataSourceType =
         searchParams.has("baseDataSourceType") ? +searchParams.get("baseDataSourceType")[0] : -1;
      this.vsWizard =
         searchParams.has("vsWizard") && searchParams.get("vsWizard")[0] === "true";
      this.styleWizard =
         searchParams.has("styleWizard") && searchParams.get("styleWizard")[0] === "true";
      this.scriptWizard =
         searchParams.has("scriptWizard") && searchParams.get("scriptWizard")[0] === "true";
      this.closeOnComplete =
         searchParams.has("closeOnComplete") &&
         searchParams.get("closeOnComplete")[0] === "true";
      this.saveToFolderId =
         searchParams.has("folder") ? searchParams.get("folder")[0] : null;
      this.runtimeId =
         searchParams.has("runtimeId") ? searchParams.get("runtimeId")[0] : null;
      this.deployed =
         searchParams.has("deployed") && searchParams.get("deployed")[0] === "true";

      this.firstDayOfWeekService.getFirstDay().subscribe((model) => {
         this.ngbDatepickerConfig.firstDayOfWeek = model.isoFirstDay;
      });
   }

   ngOnDestroy() {
      this.dragService.removeListeners(document);
      this.resizeHandlerService.removeListeners();
   }

   downloadStarted(url: string): void {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Info)", "_#(js:common.downloadStart)");
   }

   /**
    * Notify outside listeners that composer was closed.
    */
   close(identifier: string): void {
      if(identifier == null) {
         window.close();
      }
      else {
         window.parent.window.postMessage({"composerClose": true, "created": identifier}, "*");
      }
   }
}
