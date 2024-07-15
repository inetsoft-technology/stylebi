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
import { Component, EventEmitter, Input, Output, } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { Observable } from "rxjs";
import { Searchable } from "../../../searchable";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { EditPortalTabDialogData } from "./edit-portal-tab-dialog/edit-portal-tab-dialog-data";
import { EditPortalTabDialogComponent } from "./edit-portal-tab-dialog/edit-portal-tab-dialog.component";
import { PortalIntegrationSettingsModel } from "./portal-integration-settings-model";
import { PortalTabModel } from "./portal-tab-model";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/presentation/settings#portal-integration",
   title: "Portal Integration",
   keywords: [
      "em.settings", "em.settings.presentation", "em.settings.portal",
      "em.settings.integration"
   ]
})
@ContextHelp({
   route: "/settings/presentation/settings#portal-integration",
   link: "EMPresentationPortalIntegration"
})
@Component({
   selector: "em-portal-integration-view",
   templateUrl: "./portal-integration-view.component.html",
   styleUrls: ["./portal-integration-view.component.scss"]
})
export class PortalIntegrationViewComponent {
   @Input() model: PortalIntegrationSettingsModel;
   @Input() isSysAdmin: boolean;
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();

   constructor(private dialog: MatDialog) {
   }

   moveUpDisabled(index: number): boolean {
      return index == 0 || this.model.tabs[index].editable && !this.model.tabs[index - 1].editable ||
         (this.model.tabs[index-1].name == "Report" &&
         this.model.tabs[index].name != "Report" &&
         this.model.tabs[index].name != "Dashboard") ||
         (this.model.tabs[index-1].name == "Dashboard" &&
         this.model.tabs[index].name != "Dashboard" &&
         this.model.tabs[index].name != "Report");
   }

   moveDownDisabled(index: number): boolean {
      return index == this.model.tabs.length - 1 ||
         !this.model.tabs[index].editable && this.model.tabs[index + 1].editable ||
         (this.model.tabs[index].name == "Report" &&
         this.model.tabs[index+1].name != "Report" &&
         this.model.tabs[index+1].name != "Dashboard") ||
         (this.model.tabs[index].name == "Dashboard" &&
         this.model.tabs[index+1].name != "Dashboard" &&
         this.model.tabs[index+1].name != "Report");
   }

   moveUp(index: number) {
      const element = this.model.tabs.splice(index, 1);
      this.model.tabs.splice(index - 1, 0, ...element);
      this.emitModel();
   }

   moveDown(index: number) {
      const element = this.model.tabs.splice(index, 1);
      this.model.tabs.splice(index + 1, 0, ...element);
      this.emitModel();
   }

   add() {
      this.openPortalTabDialog().subscribe(result => {
         if(result) {
            this.model.tabs.push(result);
            this.emitModel();
         }
      });
   }

   edit(tab: PortalTabModel) {
      const index = this.model.tabs.findIndex(t => t.name === tab.name);
      this.openPortalTabDialog(tab).subscribe(result => {
         if(result) {
            this.model.tabs[index] = result;
            this.emitModel();
         }
      });
   }

   delete(index: number) {
      this.model.tabs.splice(index, 1);
      this.emitModel();
   }

   private openPortalTabDialog(tab?: PortalTabModel): Observable<PortalTabModel> {
      const tabs = this.model.tabs.map(t => t.name);
      return this.dialog.open(EditPortalTabDialogComponent, {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: new EditPortalTabDialogData(tab, tabs)
      }).afterClosed();
   }

   emitModel() {
      this.modelChanged.emit(<PresentationSettingsChanges>{
         model: this.model,
         modelType: PresentationSettingsType.PORTAL_INTEGRATION_SETTINGS_MODEL,
         valid: true
      });
   }
}
