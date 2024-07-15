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
import { Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { RepositoryEntry } from "../../../../../../../shared/data/repository-entry";
import { Tool } from "../../../../../../../shared/util/tool";
import { RepositorySheetSettingsChange } from "../repository-sheet-settings-view/repository-sheet-settings-view.component";
import { RepositorySheetSettingsModel } from "../repository-worksheet-settings-page/repository-sheet-settings.model";
import { RepositoryTreeNode } from "../repository-tree-node";
import { MatTab, MatTabChangeEvent } from "@angular/material/tabs";
import { AnalyzeMvPageComponent } from "../analyze-mv-page/analyze-mv-page.component";

@Component({
   selector: "em-repository-worksheet-settings-view",
   templateUrl: "./repository-worksheet-settings-view.component.html",
   styleUrls: ["./repository-worksheet-settings-view.component.scss"]
})
export class RepositoryWorksheetSettingsViewComponent {
   @Input() entry: RepositoryEntry;
   @Input() selectedTab = 0;
   @Input() editingNode: RepositoryTreeNode;
   @Input() smallDevice: boolean;
   @Input() hasMVPermission: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() sheetSettingsChanged = new EventEmitter<RepositorySheetSettingsModel>();
   @Output() mvChanged = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   @ViewChild("mvTab") mvTab: MatTab;
   @ViewChild("mvPage") mvPage: AnalyzeMvPageComponent;
   origSheetModel: RepositorySheetSettingsModel;
   _sheetModel: RepositorySheetSettingsModel;
   valid: boolean = true;
   worksheetChanged: boolean = false;
   selectedTabLabel: string;

   @Input()
   set sheetModel(model: RepositorySheetSettingsModel) {
      this.origSheetModel = model;
      this._sheetModel = Tool.clone(model);
   }

   get sheetModel(): RepositorySheetSettingsModel {
      return this._sheetModel;
   }

   get disabled(): boolean {
      const oldModel = Tool.clone(this.origSheetModel);
      oldModel.oname = "";
      const currModel = Tool.clone(this.sheetModel);
      currModel.oname = "";

      return !this.valid || !this.worksheetChanged || Tool.isEquals(oldModel, currModel);
   }

   get mvActive(): boolean {
      return !!this.mvTab && this.mvTab.isActive;
   }

   changeViewsheetSettings(info: RepositorySheetSettingsChange) {
      this._sheetModel = info.model;
      this.valid = info.valid;
      this.worksheetChanged = true;
   }

   onSelectedTabChanged(event: MatTabChangeEvent) {
      this.selectedTab = event.index;
      this.selectedTabChanged.emit(event.index);
      this.selectedTabLabel = event.tab.textLabel;
   }

   reset() {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this._sheetModel = Tool.clone(this.origSheetModel);
      this.worksheetChanged = false;
   }

   editSheet() {
      this.worksheetChanged = false;
      this.sheetSettingsChanged.emit(this._sheetModel);
   }
}
