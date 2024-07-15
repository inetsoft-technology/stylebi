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
   AfterContentChecked,
   Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output,
   SimpleChanges, ViewEncapsulation
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
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
import { AssetEntry, createAssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { AssetConstants } from "../../../../../../../../portal/src/app/common/data/asset-constants";
import { AssetType } from "../../../../../../../../shared/data/asset-type";

@Component({
   selector: "em-repository-dashboard-settings-view",
   templateUrl: "./repository-dashboard-settings-view.component.html",
   styleUrls: ["./repository-dashboard-settings-view.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class RepositoryDashboardSettingsViewComponent implements OnChanges, OnInit, OnDestroy, AfterContentChecked {
   @Input() model: RepositoryDashboardSettingsModel;
   @Input() selectedTab = 0;
   @Input() owner: string;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() dashboardSettingsChanged = new EventEmitter<RepositoryDashboardSettingsModel>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   form: UntypedFormGroup;
   private destroy$: Subject<void> = new Subject();
   private _oldModel: RepositoryDashboardSettingsModel;
   private _loaded: boolean = false;
   //tree control
   treeControl: FlatTreeControl<ViewsheetFlatNode>;
   dataSource: ViewsheetDataSource;
   private getLevel = (node: ViewsheetFlatNode) => node.level;
   private isExpandable = (node: ViewsheetFlatNode) => node.expandable;
   hasChild = (n: number, nodeData: ViewsheetFlatNode) => nodeData.expandable;

   dashboardChanged: boolean = false;

   get global(): boolean {
      return !(this.owner && this.owner.length > 0);
   }

   constructor(private fb: UntypedFormBuilder, private snackBar: MatSnackBar, private http: HttpClient) {
      this.form = this.fb.group({
         name: [""],
         description: [""],
         viewsheet: [""],
         enable: false
      });
   }

   ngOnInit() {
      this.form.get("name").setValidators([
         Validators.required,
         FormValidators.notWhiteSpace,
         FormValidators.containsDashboardSpecialCharsForName
      ]);
      this.form.get("viewsheet").setValidators([
         Validators.required
      ]);

      this._oldModel = Tool.clone(this.model);

      this.form.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(value => {
         this.model.name = this.form.value["name"];
         this.model.viewsheet = this.form.value["viewsheet"];
         this.model.description = this.form.value["description"];
         this.model.enable = this.form.value["enable"];
         this.dashboardChanged = true;
      });
      this.treeControl = new FlatTreeControl<ViewsheetFlatNode>(this.getLevel, this.isExpandable);
      this.dataSource = new ViewsheetDataSource(this.treeControl, this.http, this.snackBar,
         this.owner);
      this.dataSource.getDataChange().subscribe(() => {
         this.updateViewsheetTree();
      });
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model && changes.model.currentValue != null) {
         const name = this.model.name.endsWith("__GLOBAL") ?
            this.model.name.substr(0, this.model.name.indexOf("__GLOBAL")) : this.model.name;
         this.model.name = this.model.oname = name;
         this.form.patchValue(this.model, {emitEvent: false});
         this.updateViewsheetTree();
      }
   }

   ngOnDestroy() {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   ngAfterContentChecked() {
      let selectedViewsheet = this.form.value["viewsheet"];

      if (this.treeControl.dataNodes && !this._loaded && selectedViewsheet) {
         let parts: string[] = [];

         if(this.isPrivateVS(selectedViewsheet)) {
            parts.push(Tool.MY_REPORTS);
         }

         var parts0 = selectedViewsheet.split("^");
         var part = parts0[parts0.length - 2];
         parts = parts.concat(part.toString().split("/"));

         parts.forEach(part => {
            let folder = this.treeControl.dataNodes.find(node => node.label === part); // if no match, then it doesn't exist or it has an identifier (asset)

            if(folder && folder.expandable) {
               this.treeControl.expand(folder);
            }
         });

         this._loaded = true;
      }
   }

   private isPrivateVS(id: string): boolean {
      let entry: AssetEntry = createAssetEntry(id);

      return entry != null && entry.scope == AssetConstants.USER_SCOPE &&
         entry.type != AssetType.REPOSITORY_FOLDER;
   }

   get disabled(): boolean {
      const oldModel = Tool.clone(this._oldModel);
      oldModel.oname = "";
      const currModel = Tool.clone(this.model);
      currModel.oname = "";
      return this.form.invalid || !this.form.valid || Tool.isEquals(oldModel, currModel) || !this.dashboardChanged;
   }

   reset() {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.model = this._oldModel;
      this._oldModel = Tool.clone(this.model);
      this.form.patchValue(this.model, {emitEvent: false});
      this.dashboardChanged = false;
   }

   apply() {
      this.dashboardChanged = false;
      this.dashboardSettingsChanged.emit(this.model);
   }

   updateViewsheetTree() {
      if(this.model && this.model.path && this.dataSource && this.dataSource.treeControl &&
         this.dataSource.treeControl.dataNodes)
      {
         const vsElements = this.model.path.split("/");

         for(let i = 0; i < vsElements.length; i++) {
            const path = vsElements.slice(0, i + 1).join("/");
            const pathIndex = path.lastIndexOf("^") >= 0 ? path.lastIndexOf("^") + 1 : 0;
            const pathName = path.substr(pathIndex);
            const node = this.dataSource.treeControl.dataNodes.find(
               n => n.id === path || n.id === pathName);

            if(node && !this.dataSource.treeControl.isExpanded(node)) {
               this.dataSource.treeControl.toggle(node);
            }
         }
      }
   }
}
