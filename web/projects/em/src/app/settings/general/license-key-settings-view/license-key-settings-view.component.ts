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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { MatTableDataSource, MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from "@angular/material/table";
import { ContextHelp } from "../../../context-help";
import { Searchable } from "../../../searchable";
import { GeneralSettingsChanges } from "../general-settings-page/general-settings-page.component";
import { GeneralSettingsType } from "../general-settings-page/general-settings-type.enum";
import { LicenseKeyModel, LicenseKeySettingsModel } from "./license-key-settings-model";
import { MatCard, MatCardTitle, MatCardContent } from "@angular/material/card";
import { ApiKeyComponent } from "./api-key/api-key.component";
import { LicenseKeyListComponent } from "./license-key-list/license-key-list.component";
import { NgIf } from "@angular/common";

export interface ClusterLicense {
   server: string;
   license: string;
   licenseType: string;
}

@Searchable({
   route: "/settings/general#license",
   title: "License Keys",
   keywords: [
      "em.settings", "em.settings.general", "em.settings.license",
      "em.settings.keys"
   ]
})
@ContextHelp({
   route: "/settings/general#license",
   link: "EMGeneralLicense"
})
@Component({
    selector: "em-license-key-settings-view",
    templateUrl: "./license-key-settings-view.component.html",
    styleUrls: ["./license-key-settings-view.component.scss"],
    standalone: true,
    imports: [NgIf, LicenseKeyListComponent, ApiKeyComponent, MatCard, MatCardTitle, MatCardContent, MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow]
})
export class LicenseKeySettingsViewComponent {
   @Input() isEnterprise: boolean;
   @Output() modelChanged = new EventEmitter<GeneralSettingsChanges>();

   @Input()
   get model(): LicenseKeySettingsModel {
      return this._model;
   }

   set model(model: LicenseKeySettingsModel) {
      this._model = model || {
         serverKeys: [],
         clusterKeys: {}
      };

      this.updateClusterDataSource();
   }

   get serverKeys(): LicenseKeyModel[] {
      return this.model.serverKeys || [];
   }

   set serverKeys(value: LicenseKeyModel[]) {
      this.model.serverKeys = value;
      this.emitUpdate();
   }

   get valid(): boolean {
      return !!this.serverKeys && this.serverKeys.length > 0;
   }

   get pooledClusterLicense(): boolean {
      return !!this.clusterDataSource.data && this.clusterDataSource.data.length > 0;
   }

   cluster = false;
   clusterDataSource = new MatTableDataSource<ClusterLicense>();
   clusterColumnsToDisplay = ["server", "license"];
   private _model: LicenseKeySettingsModel = {
      serverKeys: [],
      clusterKeys: {}
   };

   constructor() {
   }

   private emitUpdate() {
      this.modelChanged.emit({
         model: this.model,
         modelType: GeneralSettingsType.LICENSEKEY_SETTINGS_MODEL,
         valid: this.valid
      });
   }

   private updateClusterDataSource(): void {
      const data = [];

      if(!!this.model.clusterKeys) {
         for(let server in this.model.clusterKeys) {
            if(this.model.clusterKeys.hasOwnProperty(server)) {
               const license = this.model.clusterKeys[server];
               let licenseType = "";

               if(!!this.model.serverKeys) {
                  const key = this.model.serverKeys.find(k => k.key === license);

                  if(!!key) {
                     licenseType = key.type;
                  }
               }

               data.push({server, license, licenseType});
            }
         }
      }

      data.sort((a, b) => a.server.localeCompare(b.server));
      this.clusterDataSource.data = data;
   }
}
