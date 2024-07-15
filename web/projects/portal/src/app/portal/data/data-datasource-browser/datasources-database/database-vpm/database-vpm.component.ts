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
import { Component, OnInit, OnDestroy, ViewChild } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { VPMDefinitionModel } from "../../../model/datasources/database/vpm/vpm-definition-model";
import { Tool } from "../../../../../../../../shared/util/tool";
import { TestDataModel } from "../../../model/datasources/database/vpm/test-data-model";
import { OperationModel } from "../../../model/datasources/database/vpm/condition/clause/operation-model";
import { Observable, Subscription } from "rxjs";
import { FolderChangeService } from "../../../services/folder-change.service";
import { ClauseOperationSymbols } from "../../../model/datasources/database/vpm/condition/clause/clause-operation-symbols";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { FolderChangeModel } from "../../../model/folder-change-model";
import { SaveVpmEvent } from "../../../model/datasources/database/events/sava-vpm-event";
import { ConditionModel } from "../../../model/datasources/database/vpm/condition/condition-model";
import { HiddenColumnsModel } from "../../../model/datasources/database/vpm/hidden-columns-model";
import { DataRef } from "../../../../../common/data/data-ref";
import { NameChangeModel } from "../../../model/name-change-model";
import { DataModelNameChangeService } from "../../../services/data-model-name-change.service";
import { AssetEntryHelper } from "../../../../../common/data/asset-entry-helper";

const VPM_URI: string = "../api/data/vpm/";
const VPM_MODELS_URI: string = "../api/data/vpm/models";
const USERS_ROLES_URI: string = VPM_URI + "usersRoles";
const VPM_OPERATIONS_URI: string = VPM_URI + "operations";

enum VPMTabs {
   CONDITIONS,
   HIDDEN_COLUMNS,
   LOOKUP,
   TEST
}

@Component({
   templateUrl: "database-vpm.component.html",
   styleUrls: ["../database-physical-model/database-model-pane.scss", "database-vpm.component.scss"]
})
export class DatabaseVPMComponent implements OnInit, OnDestroy {
   @ViewChild("conditionPane") conditionPane: any;
      //VPMConditionsComponent;
   defaultModel: VPMDefinitionModel = {
      name: "",
      conditions: [],
      hidden: null,
      lookup: "",
      description: ""
   };
   private _vpm: VPMDefinitionModel = Tool.clone(this.defaultModel);
   databaseName: string;
   originalName: string;
   editing: boolean = false;
   testData: TestDataModel = {
      users: [],
      roles: []
   };
   operations: OperationModel[] = [];
   sessionOperations: OperationModel[] = [null, null];
   resetModel: VPMDefinitionModel = this.defaultModel;
   navCollapsed: boolean = true;
   currentVPMTab: VPMTabs = VPMTabs.CONDITIONS;
   VPMTabs = VPMTabs;
   lookupList: string[] = [];
   private _isModified: boolean = false;
   private updateResetModel: boolean = false;
   private subscriptions: Subscription = new Subscription();

   constructor(private folderChangeService: FolderChangeService,
               private dataModelNameChangeService: DataModelNameChangeService,
               private httpClient: HttpClient,
               private modalService: NgbModal,
               private route: ActivatedRoute,
               private router: Router)
   {
   }

   ngOnInit(): void {
      this.refreshTestData();
      this.refreshOperations();

      // subscribe to route parameters and update current database model
      this.subscriptions.add(this.route.paramMap
         .subscribe((params: ParamMap) => {
            let path = Tool.byteDecode(params.get("vpmPath"));
            let idx = !path ? -1 : path.lastIndexOf("/");

            if(idx == -1 || idx >= path.length) {
               return;
            }

            this.databaseName = path.substring(0, idx);
            this.originalName = path.substring(idx + 1);
            this.editing = !params.get("create");

            if(this.editing) {
               this.refreshVPM();
            }
            else {
               this._isModified = true;
               this.defaultModel.description = params.get("desc") || "";
               this.defaultModel.name = this.originalName;
               this.resetModel = this.defaultModel;
               this.vpm = Tool.clone(this.resetModel);
            }
         }));

      this.subscriptions.add(this.dataModelNameChangeService.nameChangeObservable
         .subscribe(
            (data: NameChangeModel) => {
               if(!!data)  {
                  if(this.originalName === data.oldName) {
                     if(data.newName != null) {
                        this.originalName = data.newName;
                        this.vpm.name = data.newName;
                     }
                     else {
                        // model was deleted, go to datasources
                        this.router.navigate(["/portal/tab/data/datasources"],
                           {queryParams: {path: "/", scope: AssetEntryHelper.QUERY_SCOPE}});
                     }
                  }
               }
            }
         ));
   }

   ngOnDestroy(): void {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   set vpm(vpm: VPMDefinitionModel) {
      this._vpm = vpm;

      if(!vpm.hidden) {
         vpm.hidden = {
            hiddens: [],
            script: null,
            name: null,
            roles: []
         };
      }

      this.updateLookupList();
   }

   get vpm(): VPMDefinitionModel {
      return this._vpm;
   }

   changeHiddenExpression(expression: string): void {
      if(!this.vpm.hidden) {
         this.vpm.hidden = {
            roles: null,
            hiddens: null,
            name: null,
            script: expression
         };
      }
      else {
         this.vpm.hidden.script = expression;
      }
   }

   /**
    * Send request to get users and roles for test tab.
    */
   private refreshTestData(): void {
      this.httpClient.get<TestDataModel>(USERS_ROLES_URI)
         .subscribe(
            data => {
               this.testData = data;
            },
            err => {}
         );
   }

   /**
    * Send request to get available operators for conditions.
    */
   private refreshOperations(): void {
      this.httpClient.get<OperationModel[]>(VPM_OPERATIONS_URI)
         .subscribe(
            data => {
               this.operations = data;

               data.forEach(operation => {
                  if(operation.symbol == ClauseOperationSymbols.EQUAL_TO) {
                     this.sessionOperations[0] = operation;
                  }
                  else if(operation.symbol == ClauseOperationSymbols.IN) {
                     this.sessionOperations[1] = operation;
                  }
               });
            },
            err => {}
         );
   }

   /**
    * Send request to get the vpm definition for the given vpm name and database.
    */
   private refreshVPM(): void {
      let params: HttpParams = new HttpParams()
         .set("database", this.databaseName)
         .set("vpm", this.originalName);

      this.httpClient.get<VPMDefinitionModel>(VPM_MODELS_URI, { params: params })
         .subscribe(
            data => {
               this.vpm = data;
               this.resetModel = Tool.clone(data);
               this._isModified = false;
               this.currentVPMTab = VPMTabs.CONDITIONS;
            },
            err => {}
         );
   }

   /**
    * Check if the vpm has been modified from its saved state.
    * @returns {boolean}   true if the vpm has been modified and is unsaved
    */
   get isModified(): boolean {
      if(!this._isModified) {
         this._isModified = !Tool.isEquals(this.vpm, this.resetModel);
      }

      return this._isModified;
   }

   /**
    * Check that condition names are valid then send request to create/update the vpm on the server.
    */
   saveVPM(): void {
      // Checking if conditions are valid here rather than disabling the save button altogether so
      // if user is not on 'Conditions' tab and clicks save he will have feedback as to why save is
      // not valid.
      let missingConditionName: boolean = false;
      let duplicateConditionName: string = null;
      const conditionNames: string[] = this.vpm.conditions.map(condition => condition.name.trim());

      for(let i = 0; i < conditionNames.length; i++) {
         const name: string = conditionNames[i];

         if(name == null || name.length == 0) {
            missingConditionName = true;
            break;
         }

         if(conditionNames.indexOf(name) !== i) {
            duplicateConditionName = name;
            break;
         }
      }

      if(missingConditionName) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:data.vpm.conditionNoNameSaveError)");
         return;
      }
      else if(duplicateConditionName != null) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:data.vpm.conditionDuplicateNameSaveError)" + "_*" +
            duplicateConditionName);
         return;
      }

      let request: Observable<any>;

      if(this.editing) {
         // TODO: Remove responseType when angular adds fix for bug where void response
         // throws error when using HttpClient
         let event: SaveVpmEvent = new SaveVpmEvent(this.databaseName, this.originalName, this.vpm);
         request = this.httpClient.put(VPM_MODELS_URI, event, { responseType: "text" });
      }
      else {
         // TODO: Remove responseType when angular adds fix for bug where void response
         // throws error when using HttpClient
         let event: SaveVpmEvent = new SaveVpmEvent(this.databaseName, this.originalName, this.vpm);
         request = this.httpClient.post(VPM_MODELS_URI, event, { responseType: "text" });
      }

      request
         .subscribe(
            data => {
               this._isModified = false;
               this.originalName = this.vpm.name;
               this.resetModel = Tool.clone(this.vpm);

               if(!this.editing) {
                  this.folderChangeService.emitFolderChange(new FolderChangeModel(true));
                  this.editing = true;
               }
               //this.notifications.success("data.vpm.saveModelSuccess");
            },
            err => {
               //this.notifications.danger("data.vpm.saveModelError");
            }
         );
   }

   /**
    * Reset the vpm to its saved state on server or to a default new vpm.
    */
   resetVPM(): void {
      this.vpm = Tool.clone(this.resetModel);
      this.conditionPane.selectedCondition = null;

      if(this.editing) {
         this._isModified = false;
      }
   }

   /**
    * Select the vpm tab to currently show.
    * @param tab  the tab to show
    */
   selectVPMTab(tab: VPMTabs): void {
      this.currentVPMTab = tab;
   }

   /**
    * Notify that the vpm condition is refreshing its columns and we may need to refresh the reset
    * model when it is completed.
    * @param {boolean} refreshed true if the column refreshing is completed
    */
   refreshedColumns(refreshed: boolean): void {
      if(!refreshed) {
         this.updateResetModel = !this._isModified;
      }
      else if(this.updateResetModel) {
         this.resetModel = Tool.clone(this.vpm);
         this.updateResetModel = false;
      }
   }

   //Warn user of unsaved changes before leaving page.
   canDeactivate(): Promise<boolean> | boolean {
      if(!this.isModified) {
         return true;
      }

      return ComponentTool.showConfirmDialog(this.modalService, "_#(js:dialog.changedTitle)",
                                    "_#(js:data.vpm.confirmLeaving)")
         .then((buttonClicked) => buttonClicked === "ok", () => false);
   }

   updateLookupList(): void {
      this.lookupList = [];
      let conditions = this.vpm.conditions;
      let hidden = this.vpm.hidden;

      if(conditions) {
         conditions.forEach(condition => {
            if(!this.lookupList.includes(condition.tableName)) {
               this.lookupList.push(condition.tableName);
            }
         });
      }

      if(hidden && hidden.hiddens) {
         hidden.hiddens.forEach(h => {
            if(!this.lookupList.includes(h.entity)) {
               this.lookupList.push(h.entity);
            }
         });
      }
   }
}
