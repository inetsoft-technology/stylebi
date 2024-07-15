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
import {
   PresentationViewsheetToolbarOptionsModel
} from "./presentation-viewsheet-toolbar-options-model";
import { ToolbarOption } from "../toolbar-options-table-view/toolbar-options-table-view.component";
import { Searchable } from "../../../searchable";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/presentation/settings#viewsheet-toolbar",
   title: "Viewsheet Toolbar Options",
   keywords: [
      "em.settings", "em.settings.presentation", "em.settings.viewsheet",
      "em.settings.toolbar"
   ]
})
@ContextHelp({
   route: "/settings/presentation/settings#viewsheet-toolbar",
   link: "EMPresentationDashboardToolbar"
})
@Component({
   selector: "em-presentation-viewsheet-toolbar-options-view",
   templateUrl: "./presentation-viewsheet-toolbar-options-view.component.html",
   styleUrls: ["./presentation-viewsheet-toolbar-options-view.component.scss"]
})
export class PresentationViewsheetToolbarOptionsViewComponent {
   @Input() model: PresentationViewsheetToolbarOptionsModel;
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();
   title: string = "_#(js:Viewsheet Toolbar Options)";

   changeReportToolbarSettings(options: ToolbarOption[]) {
      this.modelChanged.emit(<PresentationSettingsChanges>{
         model: <PresentationViewsheetToolbarOptionsModel>{options: options},
         modelType: PresentationSettingsType.VIEWSHEET_TOOLBAR_SETTINGS_MODEL,
         valid: true
      });
   }
}
