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
import {
   Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output,
   SimpleChanges
} from "@angular/core";
import { UntypedFormBuilder, FormGroup, Validators } from "@angular/forms";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { FormValidators } from "../../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../../shared/util/tool";
import { RepositoryDashboardSettingsModel } from "../repository-dashboard-settings-page/repository-dashboard-settings-model";
import { FlatTreeControl } from "@angular/cdk/tree";
import { ViewsheetDataSource } from "../viewsheet-tree-node/viewsheet-data-source";
import { ViewsheetFlatNode } from "../viewsheet-tree-node/viewsheet-flat-node";
import { MatSnackBar } from "@angular/material/snack-bar";
import { HttpClient } from "@angular/common/http";
import { RepositoryFolderDashboardSettingsModel } from "../repository-dashboard-folder-settings-page/repository-folder-dashboard-settings-model";

export interface DashboardOption {
   name: string;
   arrange?: boolean;
}

@Component({
   selector: "em-repository-folder-dashboard-settings-view",
   templateUrl: "./repository-folder-dashboard-settings-view.component.html",
   styleUrls: ["./repository-folder-dashboard-settings-view.component.scss"]
})
export class RepositoryFolderDashboardSettingsViewComponent implements OnChanges {
   @Input() model: RepositoryFolderDashboardSettingsModel;
   @Input() selectedTab = 0;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() dashboardFoldertSettingsChanged = new EventEmitter<RepositoryFolderDashboardSettingsModel>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   dataSource: DashboardOption[];
   dashboardChanged: boolean = false;
   columnNames: string[] = ["name", "arrange"];
   _oldModel: RepositoryFolderDashboardSettingsModel;

   constructor(private fb: UntypedFormBuilder, private snackBar: MatSnackBar, private http: HttpClient) {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model && this.model != null) {
         this.init();
      }
   }

   init() {
      this.dataSource = [];
      this._oldModel = Tool.clone(this.model);

      for(let dashboard of this.model.dashboards) {
         this.dataSource.push({name: dashboard, arrange: true});
      }
   }

   get disabled(): boolean {
      const oldModel = Tool.clone(this.dataSource);
      const currModel = Tool.clone(this.model);
      return Tool.isEquals(oldModel, currModel) || !this.dashboardChanged;
   }

   getDashboardLabel(dashboard: string) {
      return dashboard.replace("__GLOBAL", " _#(js:dashboard.globalLabel)");
   }

   upButtonEnabled(index: number): boolean {
      return index > 0;
   }

   downButtonEnabled(index: number): boolean {
      return index >= 0 && index < this.dataSource.length - 1;
   }

   moveUp(index: number) {
      const array = this.dataSource.slice(0);
      const element = array.splice(index, 1);
      array.splice(index - 1, 0, ...element);
      this.dataSource = array;
      this.dashboardChanged = true;
   }

   moveDown(index: number) {
      const array = this.dataSource.slice(0);
      const element = array.splice(index, 1);
      array.splice(index + 1, 0, ...element);
      this.dataSource = array;
      this.dashboardChanged = true;
   }

   reset() {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.model = this._oldModel;
      this.init();
      this.dashboardChanged = false;
   }

   apply() {
      this.dashboardChanged = false;
      this.model.dashboards = this.dataSource.map(row => row.name);
      this.dashboardFoldertSettingsChanged.emit(this.model);
   }
}
