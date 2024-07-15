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
import { AfterViewInit, Component, EventEmitter, Output, ViewChild } from "@angular/core";
import { GettingStartedService, StepIndex } from "./service/getting-started.service";
import { NgbCollapse } from "@ng-bootstrap/ng-bootstrap";
import { ExpandStringDirective } from "../../expand-string/expand-string.directive";
import { PortalDataType } from "../../../portal/data/data-navigation-tree/portal-data-type";
import { LocalStorage } from "../../../common/util/local-storage.util";

@Component({
   selector: "getting-stared-dialog",
   templateUrl: "getting-started-dialog.component.html",
   styleUrls: ["getting-started-dialog.component.scss"]
})
export class GettingStartedDialog implements AfterViewInit {
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("queryCollapse") queryCollapse: NgbCollapse;
   @ViewChild("dataCollapse") dataCollapse: NgbCollapse;
   queryCollapsed = true;
   dataCollapsed = true;
   StepIndex = StepIndex;

   get dataSourceName(): string {
      return this.gettingStartedService.getDataSourcePath();
   }

   get createQueryEnable(): boolean {
      return this.gettingStartedService.datasourceType != PortalDataType.XMLA_SOURCE && !!this.dataSourceName;
   }

   get createDashboardEnable(): boolean {
      return !!this.gettingStartedService.getWorksheetId();
   }

   get createQueryLabel(): string {
      return ExpandStringDirective.expandString(
         "_#(js:getting.started.create.query)", [this.dataSourceName ? this.dataSourceName : ""]);
   }

   constructor(private gettingStartedService: GettingStartedService) {
   }

   ngAfterViewInit(): void {
      if(this.gettingStartedService.getCurrentStep() == 0) {
         this.dataCollapse?.toggle(true);
      }
      else if(this.gettingStartedService.getCurrentStep() == 1) {
         this.queryCollapse?.toggle(true);
      }
   }

   isCurrentStep(stepIndex: number) {
      return this.gettingStartedService.getCurrentStep() == stepIndex;
   }

   ok(): void {
      this.onCommit.emit();
   }

   /**
    * Called when user clicks cancel on dialog. Close dialog.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }

   clickConnectTo() {
      this.gettingStartedService.toConnectDataSource();
      this.ok();
   }

   clickUpload() {
      this.gettingStartedService.toUpLoadFile();
      this.ok();
   }

   createQuery() {
      this.gettingStartedService.toCreateQuery();
      this.ok();
   }

   createWs() {
      this.gettingStartedService.toCreateWs();
      this.ok();
   }

   createVs() {
      this.gettingStartedService.toCreateDashboard();
      this.ok();
   }

   noShow() {
      LocalStorage.setItem("started.noshow", "true");
      this.cancel();
   }
}
