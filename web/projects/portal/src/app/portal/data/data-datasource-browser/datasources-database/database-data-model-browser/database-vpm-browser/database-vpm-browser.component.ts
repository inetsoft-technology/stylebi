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
import { Component, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { HttpClient, HttpParams } from "@angular/common/http";
import { FolderChangeService } from "../../../../services/folder-change.service";
import { SortOptions } from "../../../../../../../../../shared/util/sort/sort-options";
import { SortTypes } from "../../../../../../../../../shared/util/sort/sort-types";
import { ValidatorFn, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../../../../shared/util/form-validators";
import { Observable, of, Subscription } from "rxjs";
import { Tool } from "../../../../../../../../../shared/util/tool";
import { VPMBrowserInfo } from "../../../../model/datasources/database/vpm/vpm-browser-info";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { NotificationsComponent } from "../../../../../../widget/notifications/notifications.component";
import { ExpandStringDirective } from "../../../../../../widget/expand-string/expand-string.directive";
import { RenameModelEvent } from "../../../../model/datasources/database/events/rename-model-event";
import { InputNameDescDialog, NameDescResult } from "../../../../input-name-desc-dialog/input-name-desc-dialog.component";
import { ListColumn } from "../../../../asset-item-list-view/asset-item-list-view.component";
import { DropdownOptions } from "../../../../../../widget/fixed-dropdown/dropdown-options";
import { Point } from "../../../../../../common/data/point";
import { ActionsContextmenuComponent } from "../../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AssetItem } from "../../../../model/datasources/database/asset-item";
import { AssemblyActionGroup } from "../../../../../../common/action/assembly-action-group";
import { RemoveDataModelEvent } from "../../../../model/datasources/database/events/remove-data-model-event";
import { AssetListBrowseModel } from "../../../../model/datasources/database/asset-list-browse-model";
import { SearchCommand } from "../../../../commands/search-command";

const VPM_URI: string = "../api/data/vpm/browse/";
const RENAME_VPM_URI: string = "../api/data/vpm/rename";
const REMOVE_VPM_URI: string = "../api/data/vpm/remove";
const VPM_CHECK_DUPLICATE_URI: string = "../api/data/vpm/checkDuplicate";
const SEARCH_VPM_URI: string = "../api/data/vpm/search";

@Component({
   templateUrl: "./database-vpm-browser.component.html",
   styleUrls: ["database-vpm-browser.component.scss"]
})
export class DatabaseVPMBrowserComponent implements OnDestroy, OnInit {
   @ViewChild("notifications") notifications: NotificationsComponent;
   model: AssetListBrowseModel;
   models: VPMBrowserInfo[] = [];
   databaseName: string;
   physicalModelName: string;
   selectedItems: VPMBrowserInfo[] = [];
   searchVisible: boolean = false;
   searchQuery: string = "";
   searchView: boolean = false;
   currentSearchQuery: string = "";
   currentFolderPathString: string = "/";
   rootLabel: string = "_#(js:Virtual Private Models)";

   sortOptions: SortOptions = {
      keys: ["name"],
      type: SortTypes.ASCENDING,
      caseSensitive: false
   };
   nameValidators: ValidatorFn[] = [
      Validators.required,
      FormValidators.matchReservedModelName,
      FormValidators.invalidDataModelName
   ];
   SortTypes = SortTypes;
   private routeParamSubscription: Subscription = new Subscription();
   showDetailsItem: VPMBrowserInfo;
   selectionOn: boolean = false;
   listColumns: ListColumn[] = [
      {
         label: "_#(js:Name)",
         widthPercentage: 40,
         sortKey: "name",
         visible: true,
         value: (item): string => item.name
      },
      {
         label: "_#(js:data.datasources.createdBy)",
         widthPercentage: 30,
         sortKey: "createdBy",
         visible: true,
         value: (item): string => item.createdBy
      },
      {
         label: "_#(js:data.datasources.creationDate)",
         widthPercentage: 30,
         sortKey: "createdDateLabel",
         visible: true,
         value: (item): string => item.createdDateLabel
      }

   ];
   iconFun = (item): string => "vpm-icon";
   duplicateCheck: (value: string) => Observable<boolean> =
      (value: string) => {
         let params: HttpParams = new HttpParams()
            .set("database", this.databaseName)
            .set("name", value);
         return this.httpClient.get<boolean>(VPM_CHECK_DUPLICATE_URI, { params: params });
      };

   constructor(private folderChangeService: FolderChangeService,
               private dropdownService: FixedDropdownService,
               private httpClient: HttpClient,
               private modalService: NgbModal,
               private route: ActivatedRoute,
               private router: Router)
   {
   }

   ngOnInit(): void {
      // subscribe to route parameters and refresh models based on current params
      this.routeParamSubscription.add(this.route.paramMap
         .subscribe((params: ParamMap) => {
            this.databaseName = Tool.byteDecode(params.get("databaseName"));
            this.physicalModelName = Tool.byteDecode(params.get("physicalModelName"));
            this.refreshModels();
         }));

      this.routeParamSubscription.add(this.route.queryParamMap.subscribe((params: ParamMap) => {
         this.refreshModels();
      }));
   }

   ngOnDestroy(): void {
      if(this.routeParamSubscription) {
         this.routeParamSubscription.unsubscribe();
         this.routeParamSubscription = null;
      }
   }

   get editable(): boolean {
      return !!this.model ? this.model.editable : false;
   }

   get deletable(): boolean {
      return !!this.model ? this.model.deletable : false;
   }

   get crrentSearchFolderLabel(): string {
      return !this.currentFolderPathString || this.currentFolderPathString === "/" ?
         this.rootLabel : this.currentFolderPathString;
   }

   /**
    * Send request to search vpms.
    */
   search(query: string = null): void {
      this.currentSearchQuery = query;

      if(!query) {
         return;
      }
      this.searchView = true;
      this.httpClient.post(SEARCH_VPM_URI, new SearchCommand(query, "/", 0, this.databaseName))
         .subscribe(
            (data: AssetListBrowseModel) => {
               this.model = data;
               this.models = Tool.sortObjects(data?.items, this.sortOptions);
            },
            () => {
               this.models = [];
               this.notifications.danger("_#(js:data.datasets.searchError)");
            }
         );
   }

   reSearch(): void {
      this.search(this.searchQuery = this.currentSearchQuery);
   }

   clearSearch(): void {
      this.searchQuery = null;
      this.searchVisible = false;
      this.searchView = false;
      this.refreshModels();
   }

   /**
    * Update current sort options and sort view.
    * @param key  the key to sort on
    */
   sortOptionsChanged(): void {
      this.models = Tool.sortObjects(this.models, this.sortOptions);
   }

   /**
    * Turn selection state on or off.
    */
   toggleSelectionState(): void {
      this.selectionOn = !this.selectionOn;
   }

   /**
    * Navigate to the edit vpm page for the selected data model.
    * @param model   the data model to edit
    */
   editModel(model: VPMBrowserInfo): void {
      this.router.navigate(["/portal/tab/data/datasources/database/vpm", model.path],
         {relativeTo: this.route});
   }

   /**
    * Send request to refresh vpms.
    */
   protected refreshModels(): Promise<void> {
      let promise = Promise.resolve(null);

      promise = promise.then(() => this.httpClient.get<AssetListBrowseModel>(
         VPM_URI + Tool.encodeURIComponentExceptSlash(this.databaseName)).toPromise());

      promise = promise.then((data) => {
         this.model = data;
         this.models = Tool.sortObjects(data?.items, this.sortOptions);

         return Promise.resolve();
      }, err => {});

      return promise;
   }

   /**
    * Open dialog to add a new vpm.
    */
   addModel(): void {
      const onCommit: (value: any) => any =
         (result: NameDescResult) => {
            // navigate to vpm page with resulting name and create parameter set to true
            this.router.navigate(["/portal/tab/data/datasources/database/vpm",
               this.databaseName + "/" + result.name, { create: true, desc: result.description }],
               { relativeTo: this.route });
         };

      const dialog = ComponentTool.showDialog(this.modalService, InputNameDescDialog, onCommit,
                                     { backdrop: "static" });
      dialog.title = "_#(js:data.datasources.newVPM)";
      dialog.label = "_#(js:data.vpm.modelName)";
      dialog.helpLinkKey = "CreateVPM";
      dialog.validators = this.nameValidators;
      dialog.validatorMessages = [
         {
            validatorName: "required",
            message: "_#(js:data.model.nameRequired)"
         },
         {
            validatorName: "invalidDataModelName",
            message: "_#(js:data.model.nameInvalid)"
         },
         {
            validatorName: "matchReservedModelName",
            message: "_#(js:common.datasource.reservedWords)"
         }
      ];
      dialog.hasDuplicateCheck = this.duplicateCheck;
      dialog.duplicateMessage = "_#(js:data.vpm.modelNameDuplicate)";
   }

   /**
    * Open dialog to rename the vpm.
    * @param model   the model to rename
    */
   renameModel(model: VPMBrowserInfo): void {
      const originalName: string = model.name;
      const desc: string = model.description;
      const hasDuplicateCheck: (value: string) => Observable<boolean> =
         (value: string) => {
            if(value != originalName) {
               return this.duplicateCheck(value);
            }
            else {
               return of(false);
            }
         };
      const onCommit: (value: any) => any =
         (result: NameDescResult) => {
            if(result.name != originalName || result.description != desc) {
               let event: RenameModelEvent = new RenameModelEvent(this.databaseName, null,
                  originalName, result.name, result.description);

               this.httpClient.put(RENAME_VPM_URI, event)
                  .subscribe(
                     data => {
                        model.name = result.name;
                        this.refreshModels().then(() => this.reSearch());
                        this.folderChangeService.emitFolderChange();
                        this.notifications.success("_#(js:data.vpm.renameModelSuccess)");
                     },
                     err => {
                        this.notifications.danger("_#(js:data.vpm.modelNameDuplicate)");
                     }
                  );
            }
         };

      const dialog = ComponentTool.showDialog(this.modalService, InputNameDescDialog, onCommit,
                                     { backdrop: "static" });
      dialog.value = originalName;
      dialog.title = "_#(js:data.datasources.renameVPM)";
      dialog.label = "_#(js:data.vpm.modelName)";
      dialog.description = desc;
      dialog.validators = this.nameValidators;
      dialog.validatorMessages = [
         {
            validatorName: "required",
            message: "_#(js:data.model.nameRequired)"
         },
         {
            validatorName: "invalidDataModelName",
            message: "_#(js:data.model.nameInvalid)"
         },
      ];
      dialog.hasDuplicateCheck = hasDuplicateCheck;
      dialog.duplicateMessage = "_#(js:data.vpm.modelNameDuplicate)";
   }

   /**
    * Confirm then send request to delete a vpm.
    * @param model   the data model to delete
    * @param index   the data model index in models array
    */
   deleteModel(items: VPMBrowserInfo[], callback?: Function): void {
      let names = items.map(model => model.name).join(",");
      let msg = ExpandStringDirective.expandString(
         "_#(js:data.vpm.confirmRemoveModel)", [names]);
      let database;

      if(items.length == 0) {
         return;
      }
      else {
         database = items[0].databaseName;
      }

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:data.vpm.removeModel)", msg)
         .then((buttonClicked) => {
            if(buttonClicked === "ok") {
               let removeEvent: RemoveDataModelEvent = {
                  database: database,
                  items: items
               };

               // TODO: remove responseType when bug with void response causes err callback is fixed
               this.httpClient.post(REMOVE_VPM_URI, removeEvent)
                  .subscribe(
                     data => {
                        items.forEach(item => {
                           let index = this.models.indexOf(item);

                           if(index >= 0) {
                              this.models.splice(index, 1);
                           }
                        });

                        if(callback) {
                           callback();
                        }

                        this.folderChangeService.emitFolderChange();
                        this.notifications.success("_#(js:data.vpm.removeSuccess)");
                     },
                     err => {
                        this.notifications.danger("_#(js:data.vpm.removeError)");
                     }
                  );
            }
         });
   }

   setShowDetailsItem(item: VPMBrowserInfo): void {
      if(this.showDetailsItem == item) {
         this.showDetailsItem = null;
      }
      else {
         this.showDetailsItem = item;
      }
   }

   openTreeContextmenu(event: [AssetItem, MouseEvent | TouchEvent]) {
      let options: DropdownOptions = {
         position : new Point(),
         contextmenu: true
      };

      if(event[1] instanceof MouseEvent) {
         options.position = {x: (<MouseEvent> event[1]).clientX + 1,
            y: (<MouseEvent> event[1]).clientY};
      }
      else if(event[1] instanceof TouchEvent) {
         options.position = {x: (<TouchEvent> event[1]).targetTouches[0].pageX,
            y: (<TouchEvent> event[1]).targetTouches[0].pageY};
      }

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[1];
      contextmenu.actions = this.createActions(<VPMBrowserInfo> event[0]);
   }

   private createActions(vpm: VPMBrowserInfo): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup();
      let groups: AssemblyActionGroup[] = [group];
      group.actions = [
         {
            id: () => "vpm browser rename",
            label: () => "_#(js:Rename)",
            icon: () => "",
            enabled: () => true,
            visible: () => this.editable,
            action: () => this.renameModel(vpm)
         },
         {
            id: () => "vpm browser delete",
            label: () => "_#(js:Delete)",
            icon: () => "",
            visible: () => this.deletable,
            enabled: () => true,
            action: () => this.deleteModel([vpm])
         },
         {
            id: () => "vpm browser details",
            label: () => " _#(js:Details)",
            icon: () => "",
            visible: () => true,
            enabled: () => true,
            action: () => this.setShowDetailsItem(vpm)
         }
      ];


      return groups;
   }

   deleteSelected() {
      this.deleteModel(this.selectedItems, () => { this.selectedItems = []; });
   }
}
