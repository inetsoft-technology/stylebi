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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ContextHelp } from "../../../context-help";
import { Searchable } from "../../../searchable";
import { GeneralSettingsChanges } from "../general-settings-page/general-settings-page.component";
import { GeneralSettingsType } from "../general-settings-page/general-settings-type.enum";
import { ClusterSettingsModel } from "./cluster-settings-model";

@Searchable({
   route: "/settings/general#server",
   title: "Server",
   keywords: ["em.settings", "em.settings.general", "em.settings.server"]
})
@ContextHelp({
   route: "/settings/general#server",
   link: "EMGeneralCluster"
})
@Component({
   selector: "em-cluster-settings-view",
   templateUrl: "./cluster-settings-view.component.html",
   styleUrls: ["./cluster-settings-view.component.scss"]
})
export class ClusterSettingsViewComponent {
   @Output() modelChanged = new EventEmitter<GeneralSettingsChanges>();
   private _model: ClusterSettingsModel;
   cluster: boolean;

   @Input() set model(model: ClusterSettingsModel) {
      this._model = model;

      if(this.model) {
         this.cluster = this.model.cluster;
      }
   }

   get model(): ClusterSettingsModel {
      return this._model;
   }

   toggleCluster() {
      this.cluster = !this.cluster;
      this.model.cluster = this.cluster;
      this.emitModel();
   }

   emitModel() {
      this.modelChanged.emit(<GeneralSettingsChanges>{
         model: this.model,
         modelType: GeneralSettingsType.CLUSTER_SETTINGS_MODEL,
         valid: true
      });
   }
}
