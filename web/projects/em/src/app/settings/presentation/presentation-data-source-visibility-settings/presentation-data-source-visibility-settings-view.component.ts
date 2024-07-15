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
import { MatDialog } from "@angular/material/dialog";
import { Observable } from "rxjs";
import { AddDataSourceTypeDialogComponent } from "./add-data-source-type-dialog/add-data-source-type-dialog.component";
import { PresentationDataSourceVisibilitySettingsModel } from "./presentation-data-source-visibility-settings-model";
import { Searchable } from "../../../searchable";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/presentation/settings#data-source-visibility",
   title: "Data Source Visibility",
   keywords: [
      "em.settings", "em.settings.presentation"
   ]
})
@ContextHelp({
   route: "/settings/presentation/settings#data-source-visibility",
   link: "EMPresentationDataSourceVisibility"
})
@Component({
   selector: "em-presentation-data-source-visibility-settings-view",
   templateUrl: "./presentation-data-source-visibility-settings-view.component.html",
   styleUrls: ["./presentation-data-source-visibility-settings-view.component.scss"]
})
export class PresentationDataSourceVisibilitySettingsViewComponent {
   @Output() modelChanged: EventEmitter<PresentationSettingsChanges> = new EventEmitter();
   @Input() model: PresentationDataSourceVisibilitySettingsModel;

   vsTitle: string = "_#(js:Viewsheet)";

   constructor(private dialog: MatDialog) {
   }

   addVisible() {
      this.openDataSourceTypeDialog(true).subscribe(result => {
         if(result) {
            this.model.visibleDataSources.push(result);
            this.emitModel();
         }
      });
   }

   addHidden() {
      this.openDataSourceTypeDialog(false).subscribe(result => {
         if(result) {
            this.model.hiddenDataSources.push(result);
            this.emitModel();
         }
      });
   }

   deleteVisible(index: number) {
      this.model.visibleDataSources.splice(index, 1);
      this.emitModel();
   }

   deleteHidden(index: number) {
      this.model.hiddenDataSources.splice(index, 1);
      this.emitModel();
   }

   private openDataSourceTypeDialog(visibleDataSources: boolean): Observable<string> {
      let types = visibleDataSources ? this.model.visibleDataSources : this.model.hiddenDataSources;
      let title = visibleDataSources ? "_#(js:Add Visible Data Source Type)" : "_#(js:Add Hidden Data Source Type)";

      return this.dialog.open(AddDataSourceTypeDialogComponent, {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {currTypes: types, listings: this.model.dataSourceListings, title: title}
      }).afterClosed();
   }

   emitModel() {
      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.DATA_SOURCE_VISIBILITY_SETTINGS_MODEL,
         valid: true
      });
   }
}
