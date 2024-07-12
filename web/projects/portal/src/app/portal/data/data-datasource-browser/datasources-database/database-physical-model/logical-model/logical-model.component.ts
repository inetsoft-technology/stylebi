/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   DoCheck,
   OnDestroy,
   OnInit,
   ViewChild
} from "@angular/core";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { NotificationData } from "../../../../../../widget/repository-tree/repository-tree.service";
import { LogicalModelDefinition } from "../../../../model/datasources/database/physical-model/logical-model/logical-model-definition";
import { Tool } from "../../../../../../../../../shared/util/tool";
import { LogicalModelSettings } from "../../../../model/datasources/database/physical-model/logical-model/logical-model-settings";
import { DataModelNameChangeService } from "../../../../services/data-model-name-change.service";
import { FolderChangeService } from "../../../../services/folder-change.service";
import { NameChangeModel } from "../../../../model/name-change-model";
import { EntityModel } from "../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { NotificationsComponent } from "../../../../../../widget/notifications/notifications.component";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { Observable, Subscription } from "rxjs";
import { FolderChangeModel } from "../../../../model/folder-change-model";
import { EditLogicalModelEvent } from "../../../../model/datasources/database/events/edit-logical-model-event";
import { AssetEntryHelper } from "../../../../../../common/data/asset-entry-helper";
import { LogicalModelService } from "./logical-model-service";
import { UntypedFormGroup } from "@angular/forms";

export interface SelectedItem {
   entity: number;
   attribute: number;
   dragData?: string;
}

const LOGICAL_MODEL_URI: string = "../api/data/logicalmodel/models";
const LOGICAL_MODEL_EXTENDED_MODEL_URI: string = "../api/data/logicalmodel/extended";
const LOGICAL_MODEL_SETTINGS_URI: string = "../api/data/logicalmodel/settings";

@Component({
   selector: "logical-model",
   templateUrl: "logical-model.component.html",
   styleUrls: ["../database-model-pane.scss", "logical-model.component.scss"],
   providers: [ LogicalModelService ]
})
export class LogicalModelComponent implements OnInit, DoCheck, OnDestroy {
   @ViewChild("notifications") notifications: NotificationsComponent;
   defaultModel: LogicalModelDefinition = {
      name: null,
      entities: [],
      partition: null,
      description: null,
      connection: null,
      parent: null
   };
   logicalModel: LogicalModelDefinition = Tool.clone(this.defaultModel);
   originalModel: LogicalModelDefinition;
   databaseName: string;
   physicalModelName: string;
   originalName: string;
   editing: boolean = false;
   isModified: boolean = false;
   initialized: boolean = false;
   loading = false;
   private expanded: EntityModel[] = []; // for multi-select with shift.
   private routeParamSubscription: Subscription;
   private dataModelNameChangeSubscription: Subscription;
   form: UntypedFormGroup = new UntypedFormGroup({});
   private subscription: Subscription;

   constructor(private dataModelNameChangeService: DataModelNameChangeService,
               private logicalModelService: LogicalModelService,
               private folderChangeService: FolderChangeService,
               public lmService: LogicalModelService,
               private httpClient: HttpClient,
               private modalService: NgbModal,
               private route: ActivatedRoute,
               private router: Router)
   {
      this.subscription = this.lmService.onNotification.subscribe(data => {
         this.notify(data);
      });
   }

   ngOnInit(): void {
      this.routeParamSubscription = this.route.paramMap
         .subscribe((params: ParamMap) => {
            const databaseStr = Tool.byteDecode(params.get("databasePath"));

            if(this.databaseName != databaseStr) {
               this.databaseName = databaseStr;
               this.getSettings();
            }

            this.physicalModelName = Tool.byteDecode(params.get("physicalModelName"));
            this.originalName = Tool.byteDecode(params.get("logicalModelName"));
            this.editing = !params.get("create");
            let parentName = Tool.byteDecode(params.get("parent"));
            this.parent = parentName;

            if(this.editing) {
               this.refreshModel();
            }
            else {
               this.logicalModel = Tool.clone(this.defaultModel);
               this.logicalModel.name = this.originalName;
               this.logicalModel.partition = this.physicalModelName;
               this.logicalModel.parent = parentName;
               this.logicalModel.description = params.get("desc") || "";
               this.logicalModel.folder = params.get("folder") || "";
               this.originalModel = Tool.clone(this.logicalModel);
               this.isModified = true;
               this.initialized = true;

               if(this.parent) {
                  this.logicalModel.connection = params.get("connection");
                  this.createExtendedModel();
               }
            }
         });

      this.dataModelNameChangeSubscription = this.dataModelNameChangeService.nameChangeObservable
         .subscribe(
            (data: NameChangeModel) => {
               if(!!data)  {
                  if(this.originalName === data.oldName) {
                     if(data.newName != null) {
                        this.originalName = data.newName;
                        this.logicalModel.name = data.newName;
                        this.originalModel.name = data.newName;
                     }
                     else {
                        // model was deleted, go to datasources
                        this.router.navigate(["/portal/tab/data/datasources"],
                           {queryParams: {path: "/", scope: AssetEntryHelper.QUERY_SCOPE}});
                     }
                  }
                  else if(this.physicalModelName === data.oldName) {
                     this.physicalModelName = data.newName;
                     this.logicalModel.partition = data.newName;
                     this.originalModel.partition = data.newName;
                  }
               }
            }
         );
   }

   ngOnDestroy(): void {
      if(this.routeParamSubscription) {
         this.routeParamSubscription.unsubscribe();
         this.routeParamSubscription = null;
      }

      if(this.dataModelNameChangeSubscription) {
         this.dataModelNameChangeSubscription.unsubscribe();
         this.dataModelNameChangeSubscription = null;
      }

      if(!!this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }

   ngDoCheck(): void {
      if(this.logicalModel && this.originalModel) {
         this.checkModified();
      }
   }

   get parent(): string {
      return this.logicalModel.parent;
   }

   set parent(parent: string) {
      this.logicalModel.parent = parent;
   }

   get displayTitle(): string {
      if(!this.logicalModel) {
         return "";
      }

      let name = this.logicalModel?.name + (this.isModified ? "*" : "");

      if(!!this.logicalModel?.parent && !!this.physicalModelName) {
         return name + " -> " + this.physicalModelName + "/" +
            (this.logicalModel.connection ? this.logicalModel.connection : "(Default Connection)");
      }
      else if(!!this.physicalModelName) {
         return name + " -> " + this.physicalModelName;
      }

      return name;
   }

   private notify(data: NotificationData): void {
      const type = data.type;
      const content = data.content;

      switch(type) {
         case "success":
            this.notifications.success(content);
            break;
         case "info":
            this.notifications.info(content);
            break;
         case "warning":
            this.notifications.warning(content);
            break;
         case "danger":
         case "error":
            this.notifications.danger(content);
            break;
         default:
            this.notifications.warning(content);
      }
   }

   /**
    * Retrieve logical model from the server.
    */
   private refreshModel(): void {
      let params: HttpParams = new HttpParams()
         .set("database", this.databaseName)
         .set("physicalModel", this.physicalModelName)
         .set("name", this.originalName);

      if(this.parent) {
         params = params.set("parent", this.parent);
      }

      this.loading = true;
      this.httpClient
         .get<LogicalModelDefinition>(LOGICAL_MODEL_URI, { params: params})
         .subscribe(
            data => {
               this.logicalModel = data;
               this.originalModel = Tool.clone(this.logicalModel);
               this.isModified = false;
               this.initialized = true;
               this.loading = false;
               this.expanded = [];
            },
            err => {}
         );
   }

   /**
    * Save logical model to the server.
    */
   save(): void {
      let request: Observable<LogicalModelDefinition>;
      let editEvent: EditLogicalModelEvent = new EditLogicalModelEvent(this.databaseName,
         this.logicalModel, this.physicalModelName, this.originalName, this.parent);

      if(this.editing) {
         request = this.httpClient.put<LogicalModelDefinition>(LOGICAL_MODEL_URI, editEvent);
      }
      else {
         request = this.httpClient.post<LogicalModelDefinition>(LOGICAL_MODEL_URI, editEvent);
      }

      request
         .subscribe(
            data => {
               this.logicalModel = data;
               this.originalModel = Tool.clone(this.logicalModel);
               this.originalName = this.originalModel.name;
               this.isModified = false;

               if(!this.editing) {
                  this.folderChangeService.emitFolderChange(new FolderChangeModel(true));
                  this.editing = true;
               }

               let msg = !!this.parent ? "_#(js:data.logicalmodel.extended.saveModelSuccess)"
                  : "_#(js:data.logicalmodel.saveModelSuccess)";
               this.notifications.success(msg);
            },
            err => {
               this.notifications.danger("_#(js:data.logicalmodel.saveModelError)");
            }
         );
   }

   /**
    * Check to make sure the model was modified.
    */
   checkModified(): void {
      if(this.initialized) {
         // check if logical model has changed
         if(!Tool.isEquals(this.logicalModel, this.originalModel)) {
            // if changed, set modified to true
            this.isModified = true;
         }
      }
   }

   /**
    * Check if any changes were made and unsaved, then confirm user navigation away without saving.
    */
   canDeactivate(): Promise<boolean> | boolean {
      if(!this.isModified) {
         return true;
      }
      else {
         let msg: string = !!this.parent ? "_#(js:data.extended.logicalmodel.confirmLeaving)"
            : "_#(js:data.logicalmodel.confirmLeaving)";
         return ComponentTool.showConfirmDialog(this.modalService, "_#(js:dialog.changedTitle)", msg)
            .then(
               (result) => result === "ok",
               () => false
            );
      }
   }

   /**
    * Crate a extended model.
    */
   private createExtendedModel(): void {
      let editEvent: EditLogicalModelEvent = new EditLogicalModelEvent(this.databaseName,
         this.logicalModel, this.physicalModelName, this.originalName, this.parent);
      this.loading = true;

      this.httpClient.post<LogicalModelDefinition>(LOGICAL_MODEL_EXTENDED_MODEL_URI, editEvent)
         .subscribe((model) =>
         {
            this.logicalModel = model;
            this.loading = false;
         });
   }

   get settings(): LogicalModelSettings {
      return this.logicalModelService.settings;
   }

   private getSettings(): void {
      let params = new HttpParams()
         .set("ds", this.databaseName);

      this.httpClient.get<LogicalModelSettings>(LOGICAL_MODEL_SETTINGS_URI,
         {params}).subscribe((val) =>
      {
         this.logicalModelService.settings = val;
      });
   }

   get lmContentHeight() {
      return "calc(100% - 40px)";
   }
}
