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
import { Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { AnalyzeMvPageComponent } from "../analyze-mv-page/analyze-mv-page.component";
import { RepositorySheetSettingsChange } from "../repository-sheet-settings-view/repository-sheet-settings-view.component";
import { RepositorySheetSettingsModel } from "../repository-worksheet-settings-page/repository-sheet-settings.model";
import { Tool } from "../../../../../../../shared/util/tool";
import { RepositoryTreeNode } from "../repository-tree-node";
import { MatTab, MatTabChangeEvent } from "@angular/material/tabs";

@Component({
   selector: "em-repository-viewsheet-settings-view",
   templateUrl: "./repository-viewsheet-settings-view.component.html",
   styleUrls: ["./repository-viewsheet-settings-view.component.scss"]
})
export class RepositoryViewsheetSettingsViewComponent {
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
   _infoValid: boolean = true;
   _viewsheetChanged: boolean = false;
   selectedTabLabel: string;

   @Input()
   set sheetModel(model: RepositorySheetSettingsModel) {
      this.origSheetModel = model;
      this._sheetModel = Tool.clone(model);
   }

   get sheetModel() {
      return this._sheetModel;
   }

   get valid(): boolean {
      const currModel = Tool.clone(this._sheetModel);
      currModel.oname = "";
      const origModel = Tool.clone(this.origSheetModel);
      origModel.oname = "";
      return this._infoValid && (this._viewsheetChanged || !Tool.isEquals(currModel, origModel));
   }

   get mvActive(): boolean {
      return !!this.mvTab && this.mvTab.isActive;
   }

   get mvAnalyzed(): boolean {
      return !!this.mvPage?.analyzed;
   }

   get showMVPlanDisabled(): boolean {
      return !!this.mvPage?.showPlanDisabled;
   }

   get mvSelectionLength(): number {
      return this.mvPage?.selection.length ?? 0;
   }

   changeViewsheetSettings(info: RepositorySheetSettingsChange) {
      info.model.permissionTableModel = this._sheetModel.permissionTableModel;
      this._sheetModel = info.model;
      this._infoValid = info.valid;
      this._viewsheetChanged = true;
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

      this._viewsheetChanged = false;
      this._sheetModel = Tool.clone(this.origSheetModel);
   }

   editSheet() {
      this._viewsheetChanged = false;
      this.sheetSettingsChanged.emit(this._sheetModel);
   }

   analyzeMV(): void {
      if(!!this.mvPage) {
         this.mvPage.analyzeMV();
      }
   }

   createMV(): void {
      if(!!this.mvPage) {
         this.mvPage.create();
      }
   }

   deleteMVSelected(): void {
      if(!!this.mvPage) {
         this.mvPage.deleteSelected();
      }
   }

   showMVPlan(): void {
      if(!!this.mvPage) {
         this.mvPage.showPlan();
      }
   }

   clearMVAnalysis(): void {
      if(!!this.mvPage) {
         this.mvPage.clearAnalysis();
      }
   }
}
