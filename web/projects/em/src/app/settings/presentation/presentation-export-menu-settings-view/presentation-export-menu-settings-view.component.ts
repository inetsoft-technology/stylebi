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
import { PresentationExportMenuSettingsModel } from "./presentation-export-menu-settings-model";
import { Searchable } from "../../../searchable";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/presentation/settings#export-menu",
   title: "Export Menu Options",
   keywords: [
      "em.settings", "em.settings.presentation"
   ]
})
@ContextHelp({
   route: "/settings/presentation/settings#export-menu",
   link: "EMExportMenu"
})
@Component({
   selector: "em-presentation-report-export-menu-settings-view",
   templateUrl: "./presentation-export-menu-settings-view.component.html",
   styleUrls: ["./presentation-export-menu-settings-view.component.scss"]
})
export class PresentationExportMenuSettingsViewComponent {
   @Output() modelChanged: EventEmitter<PresentationSettingsChanges> = new EventEmitter();
   @Input() model: PresentationExportMenuSettingsModel;

   vsTitle: string = "_#(js:Viewsheet)";

   onExportMenuSettingsChanged() {
      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.EXPORT_MENU_SETTINGS_MODEL,
         valid: true
      });
   }
}
