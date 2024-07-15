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
import { Component, OnDestroy, OnInit, QueryList, ViewChild, ViewChildren, } from "@angular/core";
import {
   DatasourceXmlaDefinitionModel
} from "../../model/datasources/database/cube/xmla/datasource-xmla-definition-model";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { Tool } from "../../../../../../../shared/util/tool";
import { Observable, of, Subscription } from "rxjs";
import { HttpClient, HttpParams } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DataNotificationsComponent } from "../../data-notifications.component";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { AssetConstants } from "../../../../common/data/asset-constants";
import {
   GettingStartedService,
   StepIndex
} from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { CubeMetaModel } from "../../model/datasources/database/cube/xmla/cube-meta-model";
import { CubeItemDataModel } from "../../model/datasources/database/cube/xmla/cube-item-data-model";
import { CubeItemType } from "../../model/datasources/database/cube/xmla/cube-item-type";
import { XMetaInfoModel } from "../../model/datasources/database/cube/metaInfo-model";
import {
   AutoDrillInfoModel
} from "../../model/datasources/database/physical-model/logical-model/auto-drill-info-model";
import {
   AutoDrillDialog
} from "../datasources-database/database-physical-model/logical-model/attribute-editor/auto-drill-dialog/data-auto-drill-dialog.component";
import {
   AutoDrillPathModel
} from "../../model/datasources/database/physical-model/logical-model/auto-drill-path-model";
import { ComboMode, ValueMode } from "../../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { ViewSampleDataDialog } from "./view-sample-data-dialog/view-sample-data-dialog.component";
import { ViewSampleDataRequest } from "../../model/datasources/database/cube/xmla/view-sample-data-request";
import { CubeDimMemberModel } from "../../model/datasources/database/cube/xmla/cube-dim-member-model";
import { SampleDataModel } from "../../model/datasources/database/cube/xmla/sample-data-model";
import { CubeDimensionModel } from "../../model/datasources/database/cube/xmla/cube-dimension-model";
import {
   ConnectionStatus
} from "../../../../../../../em/src/app/settings/security/security-provider/security-provider-model/connection-status";
import { catchError } from "rxjs/operators";
import { SortOptions } from "../../../../../../../shared/util/sort/sort-options";
import { SortTypes } from "../../../../../../../shared/util/sort/sort-types";
import { PortalDataType } from "../../data-navigation-tree/portal-data-type";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DomainModel } from "../../model/datasources/database/cube/xmla/domain-model";
import { CubeHierDimensionModel } from "../../model/datasources/database/cube/xmla/cube-hier-dimension-model";
import { CubeModel } from "../../model/datasources/database/cube/xmla/cube-model";
import { XCubeMemberModel } from "../../model/datasources/database/cube/xcube-member-model";
import { CanComponentDeactivate } from "../../../../../../../shared/util/guard/can-component-deactivate";

const XMLA_DATASOURCE_URI: string = "../api/portal/data/datasource/xmla/";
const FORMAT_STRING_URI: string = "../api/data/logicalModel/attribute/format";

interface CubeNode {
   cube?: CubeModel;
   dimension?: CubeDimensionModel;
   member?: XCubeMemberModel;
}

@Component({
   selector: "datasources-xmla",
   templateUrl: "datasources-xmla.component.html",
   styleUrls: ["datasources-xmla.component.scss"]
})
export class DatasourcesXmlaComponent implements OnInit, OnDestroy, CanComponentDeactivate {
   @ViewChild("dataNotifications") dataNotifications: DataNotificationsComponent;
   @ViewChildren(FixedDropdownDirective) dropDowns: QueryList<FixedDropdownDirective>;
   datasourcePath: string;
   parentPath = "";
   editing = false;
   form: UntypedFormGroup;
   cubeTree: TreeNodeModel;
   catalogs: string[];
   loadingCatalogs: boolean = false;
   loadingCatalogsFailed: boolean = false;
   loadingMeta: boolean = false;
   formatString: string;
   selectedCubeNode: CubeNode;
   private routeParamSubscription: Subscription;
   private _model: DatasourceXmlaDefinitionModel;
   private originalModel: DatasourceXmlaDefinitionModel;
   public ComboMode = ComboMode;
   public ValueMode = ValueMode;

   defaultLocals = [
      { label: "_#(js:English)" + "(en)", value: "en" },
      { label: "_#(js:French)" + "(fr)", value: "fr" },
      { label: "_#(js:German)" + "(de)", value: "de" },
      { label: "_#(js:Italian)" + "(it)", value: "it" },
      { label: "_#(js:Japanese)" + "(jp)", value: "jp" },
      { label: "_#(js:Korean)" + "(ko)", value: "ko" },
      { label: "_#(js:Chinese)" + "(zh)", value: "zh" },
      { label: "_#(js:US)" + "(en_US)", value: "en_US" },
      { label: "_#(js:Canada)" + "(en_CA)", value: "en_CA" }
   ];

   get refreshMetaLabel(): string {
      return this.model?.domain?.cubes ? "_#(js:Refresh Metadata)" : "_#(js:Retrieve Metadata)";
   }

   get selectedDimensionMember(): boolean {
      return this.selectedCubeNode?.member?.classType == "CubeDimMemberModel";
   }

   get model() {
      return this._model;
   }

   set model(value: DatasourceXmlaDefinitionModel) {
      this._model = value;
      this.initForm();
   }

   /**
    * The drill description shown in the input.
    * @returns {any}
    */
   get drillString(): string {
      if(this.selectedCubeNode?.member?.metaInfo?.drillInfo) {
         if(!this.selectedCubeNode?.member?.metaInfo?.drillInfo ||
            this.selectedCubeNode?.member?.metaInfo?.drillInfo.paths.length == 0)
         {
            return "None";
         }

         const paths: string[] = this.selectedCubeNode?.member.metaInfo?.drillInfo.paths
            .map((path: AutoDrillPathModel) => path.name);

         return paths.join(", ");
      }

      return "None";
   }

   get selectedDimension(): CubeDimensionModel {
      return this.selectedCubeNode?.dimension ? <CubeDimensionModel> this.selectedCubeNode.dimension : null;
   }

   constructor(private httpClient: HttpClient,
               private modalService: NgbModal,
               private route: ActivatedRoute,
               private router: Router,
               private gettingStartedService: GettingStartedService)
   {
      this.initForm();
      this.defaultLocals = Tool.sortObjects(this.defaultLocals, new SortOptions(["label"], SortTypes.ASCENDING));
   }

   ngOnInit(): void {
      this.routeParamSubscription = this.route.paramMap
         .subscribe((routeParams: ParamMap) => {
            this.datasourcePath = Tool.byteDecode(routeParams.get("datasourcePath"));
            this.parentPath = routeParams.get("parentPath");

            if(!this.datasourcePath) {
               this.editing = false;
               let params = new HttpParams().set("parentPath", this.parentPath);
               this.httpClient.get<DatasourceXmlaDefinitionModel>(XMLA_DATASOURCE_URI + "new", {params: params})
                  .subscribe(data => {
                     this.model = data;
                     this.originalModel = Tool.clone(this.model);
                  });
            }
            else {
               this.editing = true;
               this.refreshSourceModel();
            }
         });
   }

   ngOnDestroy() {
      if(this.routeParamSubscription) {
         this.routeParamSubscription.unsubscribe();
         this.routeParamSubscription = null;
      }
   }

   private initForm() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this._model?.name, [
            Validators.required, Validators.pattern(FormValidators.DATASOURCE_NAME_REGEXP)
         ]),
         description: new UntypedFormControl(this._model?.description, []),
         url: new UntypedFormControl(this._model?.url, [
            Validators.required
         ]),
         datasourceInfo: new UntypedFormControl(this._model?.datasourceInfo, []),
         catalog: new UntypedFormControl(this._model?.catalogName, [
            Validators.required
         ]),
         requiresLogin: new UntypedFormControl(this._model?.login, []),
         saveUserPassword: new UntypedFormControl(!!this._model?.user, []),
         user: new UntypedFormControl(this._model?.user, []),
         password: new UntypedFormControl(this._model?.password, []),
      });

      this.updateEnable();

      this.form.valueChanges.subscribe(() => {
         this.updateEnable();
      });
   }

   private updateEnable() {
      if(this.form.get("requiresLogin").value) {
         this.form.get("saveUserPassword").enable({emitEvent: false});
      }
      else {
         this.form.get("saveUserPassword").disable({emitEvent: false});
      }

      if(!this.form.get("saveUserPassword").value || this.form.get("saveUserPassword").disabled) {
         this.form.get("user").disable({emitEvent: false});
         this.form.get("password").disable({emitEvent: false});
      }
      else {
         this.form.get("user").enable({emitEvent: false});
         this.form.get("password").enable({emitEvent: false});
      }
   }

   private setFormToModel() {
      this._model.name = this.form.get("name").value;
      this._model.description = this.form.get("description").value;
      this._model.url = this.form.get("url").value;
      this._model.datasourceInfo = this.form.get("datasourceInfo").value;
      this._model.catalogName = this.form.get("catalog").value;
      this._model.login = this.form.get("requiresLogin").value;

      if(this.form.get("saveUserPassword").value) {
         this._model.user = this.form.get("user").value;
         this._model.password = this.form.get("password").value;
      }
      else {
         this._model.user = null;
         this._model.password = null;
      }
   }

   /**
    * Send request to get the data source definition for the original data source name.
    */
   private refreshSourceModel(): void {
      this.httpClient.get<DatasourceXmlaDefinitionModel>(XMLA_DATASOURCE_URI + "edit/"
         + Tool.encodeURIComponentExceptSlash(this.datasourcePath))
         .subscribe(
            data => {
               this.model = data;
               this.originalModel = Tool.clone(this.model);
            },
            (error) => {
               if(error.status == 403) {
                  ComponentTool.showMessageDialog(
                     this.modalService, "Unauthorized",
                     "_#(js:data.databases.noEditPermissionError)");
               }
               else {
                  this.dataNotifications.notifications.danger("_#(js:data.datasources.getDataSourceError)");
               }
            }
         );

      this.httpClient.get<TreeNodeModel>(XMLA_DATASOURCE_URI + "metadataTree/"
         + Tool.encodeURIComponentExceptSlash(this.datasourcePath))
         .subscribe(root => {
            this.cubeTree = root;
         });
   }

   selectCatalog(catalog: string) {
      this.form.get("catalog").setValue(catalog);
   }

   loadCatalogs() {
      this.catalogs = [];
      this.loadingCatalogs = true;
      this.loadingCatalogsFailed = false;
      this.setFormToModel();

      this.httpClient.post(XMLA_DATASOURCE_URI + "catalogs", this.model).subscribe(
         (data: string[]) => {
            this.catalogs = data;
            this.loadingCatalogs = false;
         },
         (error) => {
            this.loadingCatalogs = false;
            this.loadingCatalogsFailed = true;
            this.dataNotifications.notifications.danger(error.error.message);
         });
   }

   loadMetadata() {
      this.loadingMeta = true;
      this.setFormToModel();
      let oldDomain = Tool.clone(this.model.domain);

      this.httpClient.post<CubeMetaModel>(XMLA_DATASOURCE_URI + "metadata/refresh", this.model).subscribe(
         (data) => {
            this.loadingMeta = false;
            this.model.domain = data.domain;
            this.syncDomain(this.model.domain, oldDomain);
            this.expandSelectedTreeNode(data.cubeTree);
            this.cubeTree = data.cubeTree;
         },
         (error) => {
            this.loadingMeta = false;
            this.dataNotifications.notifications.danger(error.error.message);
         });
   }

   private expandSelectedTreeNode(tree: TreeNodeModel): void {
      if(!this.selectedCubeNode?.member && !this.selectedCubeNode?.dimension) {
         return;
      }

      this.expandSelectedTreeNode0(tree, this.selectedCubeNode);
   }

   private expandSelectedTreeNode0(node: TreeNodeModel, selected: CubeNode): boolean {
      node.expanded = false;
      let expandParent = false;

      if(node.data) {
         let cubeItemDataModel: CubeItemDataModel = <CubeItemDataModel> node.data;

         if(cubeItemDataModel.type == CubeItemType.DIMENSION) {
            let dimensionName = selected.dimension?.classType == "HierDimensionModel" ?
               (<CubeHierDimensionModel> selected.dimension)?.hierarchyUniqueName : selected.dimension?.uniqueName;
            expandParent = cubeItemDataModel.cubeName == selected?.cube?.name &&
               cubeItemDataModel.uniqueName == dimensionName;
         }
         else if(cubeItemDataModel.type == CubeItemType.MEASURE || cubeItemDataModel.type == CubeItemType.LEVEL) {
            expandParent = cubeItemDataModel.cubeName == selected?.cube?.name &&
               cubeItemDataModel.uniqueName == (<any> selected.member)?.uniqueName;
         }
      }

      if(!node.children) {
         return false;
      }

      let childrenSelected: boolean = false;

      for(let cubeNode of node.children) {
         childrenSelected = this.expandSelectedTreeNode0(cubeNode, selected) || childrenSelected;
      }

      node.expanded = childrenSelected;

      return expandParent || childrenSelected;
   }

   private syncDomain(current: DomainModel, old: DomainModel): void {
      if(!old || !current) {
         return;
      }

      let keepOrderDimensions: Set<string> = new Set<string>();
      let memberMetaInfoMap: Map<string, XMetaInfoModel> = new Map<string, XMetaInfoModel>();

      if(!old.cubes) {
         return;
      }

      for(let cube of old.cubes) {
         if(!cube) {
            continue;
         }

         if(cube.dimensions) {
            for(let dimension of cube.dimensions) {
               let dimensionName = dimension.dimensionName;

               if(dimension.classType == "HierDimensionModel") {
                  dimensionName = (<CubeHierDimensionModel> dimension).hierarchyUniqueName;
               }

               if(dimension?.originalOrder) {
                  keepOrderDimensions.add(this.getDimensionKey(cube, dimension));
               }

               if(!dimension.members) {
                  continue;
               }

               for(let level of dimension.members) {
                  if(level.metaInfo) {
                     memberMetaInfoMap.set(this.getMemberKey(cube, level), level.metaInfo);
                  }
               }
            }
         }

         if(cube.measures) {
            for(let measure of cube.measures) {
               if(measure.metaInfo) {
                  memberMetaInfoMap.set(this.getMemberKey(cube, measure), measure.metaInfo);
               }
            }
         }
      }

      if(!current.cubes) {
         return;
      }

      let selectedMemberKey = this.getMemberKey(this.selectedCubeNode?.cube, this.selectedCubeNode?.member);
      let selectedDimKey = this.getDimensionKey(this.selectedCubeNode?.cube, this.selectedCubeNode?.dimension);
      this.selectedCubeNode = {};

      for(let cube of current.cubes) {
         if(!cube) {
            continue;
         }

         if(cube.dimensions) {
            for(let dimension of cube.dimensions) {
               if(!dimension) {
                  continue;
               }

               let dimensionKey = this.getDimensionKey(cube, dimension);

               if(keepOrderDimensions.has(dimensionKey)) {
                  dimension.originalOrder = true;
               }

               if(dimensionKey == selectedDimKey) {
                  this.selectedCubeNode.cube = cube;
                  this.selectedCubeNode.dimension = dimension;
               }

               if(!dimension.members) {
                  continue;
               }

               for(let level of dimension.members) {
                  let memberKey = this.getMemberKey(cube, level);
                  let meta = memberMetaInfoMap.get(memberKey);

                  if(meta) {
                     level.metaInfo = meta;
                  }

                  if(selectedMemberKey == memberKey) {
                     this.selectedCubeNode.cube = cube;
                     this.selectedCubeNode.member = level;
                  }
               }
            }
         }

         if(cube.measures) {
            for(let measure of cube.measures) {
               let memberKey = this.getMemberKey(cube, measure);
               let meta = memberMetaInfoMap.get(memberKey);

               if(meta) {
                  measure.metaInfo = meta;
               }

               if(selectedMemberKey == memberKey) {
                  this.selectedCubeNode.cube = cube;
                  this.selectedCubeNode.member = measure;
               }
            }
         }
      }
   }

   private getMemberKey(cube: CubeModel, member: XCubeMemberModel): string {
      if(!cube || !member) {
         return null;
      }

      let memberName = member.name;

      if(member.classType == "CubeDimMemberModel") {
         memberName = (<CubeDimMemberModel> member).uniqueName;
      }

      return (cube.caption || cube.name) + ":" + memberName;
   }

   private getDimensionKey(cube: CubeModel, dimension: CubeDimensionModel): string {
      if(!dimension) {
         return null;
      }

      let dimensionName = dimension.dimensionName;

      if(dimension.classType == "HierDimensionModel") {
         dimensionName = (<CubeHierDimensionModel> dimension).hierarchyUniqueName;
      }

      return (cube.caption || cube.name) + ":" + dimensionName;
   }

   public getCSSIcon(node: TreeNodeModel): string {
      let data = <CubeItemDataModel> node.data;

      switch(data?.type) {
         case CubeItemType.CUBE:
            return "cube-icon";
         case CubeItemType.DIMENSION:
            if(data.hierarchy) {
               return data.userDefined ? "user-group-icon" : "column-icon";
            }

            return "dimension-icon";
         case CubeItemType.MEASURE:
            return "measure-icon";
         case CubeItemType.LEVEL:
            return "shape-circle-icon";
         case CubeItemType.DIMENSION_FOLDER:
            return "dimension-icon";
      default:
            return "folder-icon";
      }
   }

   selectedNode(nodes: TreeNodeModel[]) {
      this.selectedCubeNode = {};

      if(!nodes || nodes.length == 0 || !nodes[0].data || !this.model.domain) {
         return;
      }

      let selectedData = <CubeItemDataModel> nodes[0].data;
      let cubeModel = this.model.domain.cubes.find(cube => cube.name == selectedData.cubeName);
      this.selectedCubeNode.cube = cubeModel;

      if(selectedData.type == CubeItemType.DIMENSION) {
         this.selectedCubeNode.dimension = cubeModel?.dimensions.find(d =>
            (d.classType == "HierDimensionModel" ? (<CubeHierDimensionModel> d).hierarchyUniqueName : d.uniqueName) ==
            selectedData.uniqueName);
      }
      else if(selectedData.type == CubeItemType.MEASURE) {
         this.selectedCubeNode.member = cubeModel.measures
            .find(measure => measure?.uniqueName == selectedData.uniqueName);
      }
      else if(selectedData.type == CubeItemType.LEVEL) {
         this.selectedCubeNode.member = cubeModel.dimensions.flatMap(d => d.members)
            .filter(item => item.classType == "CubeDimMemberModel")
            .map(item => <CubeDimMemberModel> item)
            .find(item => item?.uniqueName == selectedData.uniqueName);
      }

      if(this.selectedCubeNode?.member && !this.selectedCubeNode.member.metaInfo) {
         this.selectedCubeNode.member.metaInfo = <XMetaInfoModel> {
            drillInfo: {
               paths: []
            },
            formatInfo: {},
            asDate: false,
            datePattern: null,
            locale: null
         };
      }

      this.updateFormatString();
   }

   changeAsDate() {
      if(!this.selectedDimensionMember) {
         return;
      }

      this.selectedCubeNode.member.metaInfo.asDate = !this.selectedCubeNode?.member?.metaInfo?.asDate;
   }

   changeLocal(value: string) {
      if(!this.selectedDimensionMember) {
         return;
      }

      this.selectedCubeNode.member.metaInfo.locale = value;
   }

   changeDatePattern(pattern: string) {
      if(!this.selectedDimensionMember) {
         return;
      }

      this.selectedCubeNode.member.metaInfo.datePattern = pattern;
   }

   changeOriginalOrder() {
      if(!this.selectedCubeNode?.dimension) {
         return;
      }

      this.selectedCubeNode.dimension.originalOrder = !this.selectedCubeNode?.dimension.originalOrder;
   }

   /**
    * Open auto drill dialog.
    */
   openAutoDrillDialog(): void {
      let dialog = ComponentTool.showDialog(this.modalService, AutoDrillDialog,
         (data: AutoDrillInfoModel) => {
            this.selectedCubeNode.member.metaInfo.drillInfo = data;
         }, {size: "lg", windowClass: "data-auto-drill-dialog"});

      dialog.autoDrillModel = this.selectedCubeNode.member.metaInfo.drillInfo;
   }

   /**
    * Get sample format string from the server.
    */
   updateFormatString(): void {
      if(!this.selectedCubeNode?.member?.metaInfo?.formatInfo) {
         this.formatString = null;
         return;
      }

      this.httpClient
         .post(FORMAT_STRING_URI, this.selectedCubeNode.member.metaInfo.formatInfo, { responseType: "json" })
         .subscribe(
            (data: string) => {
               this.formatString = data;
            },
            err => {}
         );
   }

   viewSampleData() {
      if(!this.selectedDimensionMember) {
         return;
      }

      let modelClone = Tool.clone(this._model);
      let cube = this.model.domain.cubes
         .find(c => c.name == this.selectedCubeNode?.cube?.name);
      modelClone.domain = null;

      let request: ViewSampleDataRequest = {
         datasource: modelClone,
         cube: cube,
         member: <CubeDimMemberModel> this.selectedCubeNode?.member
      };

      this.httpClient.post<SampleDataModel>(XMLA_DATASOURCE_URI + "viewSampleData", request)
         .subscribe((data) => {
               let dialog = ComponentTool.showDialog(this.modalService, ViewSampleDataDialog,
                  null, {size: "lg", backdrop: "static"});
               dialog.tableData = data.tableCells;
            },
            err => {
               this.dataNotifications.notifications.danger(err.error.message);
            }
         );
   }

   testDatabase() {
      this.setFormToModel();

      this.httpClient.post<ConnectionStatus>(XMLA_DATASOURCE_URI + "test", this.model).pipe(
         catchError(() => {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               "_#(js:em.data.databases.error)").then();

            return of(null);
         })
      ).subscribe((connection: ConnectionStatus) => {
         if(connection?.connected) {
            this.dataNotifications.notifications.success(connection.status);
         }
         else {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", connection.status).then();
         }
      });
   }

   canDeactivate(): Observable<boolean> | Promise<boolean> | boolean {
      if(JSON.stringify(this._model) == JSON.stringify(this.originalModel)) {
         return true;
      }
      const message = "_#(js:unsave.changes.message)";
      return ComponentTool.showMessageDialog(this.modalService, "_#(js:Confirm)", message, {
         "Yes": "_#(js:Yes)",
         "No": "_#(js:No)"
      }).then((value) => {
         return Promise.resolve(value === "Yes");
      });
   }



   public ok(): void {
      this.setFormToModel();
      let submitRequest: Observable<any>;

      if(this.editing) {
         let params = new HttpParams();

         if(this.editing && this.originalModel) {
            params = params.set("path", this.originalModel.name);
         }

         submitRequest = this.httpClient.post(XMLA_DATASOURCE_URI + "update", this._model, {params: params});
      }
      else {
         submitRequest = this.httpClient.post(XMLA_DATASOURCE_URI + "new", this._model);
      }

      submitRequest.subscribe(() => {
            if(!this.editing && this.gettingStartedService.isConnectTo()) {
               let sourcePath;

               if(this.model.parentPath && this.model.parentPath != "" && this.model.parentPath != "/") {
                  sourcePath = this.parentPath + "/" + this.model.name;
               }
               else {
                  sourcePath = this.model.name;
               }

               this.gettingStartedService.setDataSourcePath(sourcePath, PortalDataType.XMLA_SOURCE);
               this.gettingStartedService.continue(StepIndex.CREATE_QUERY_EMPTY_WS);
            }

            this.close(true);
         },
         (error) => {
            ComponentTool.showMessageDialog(
               this.modalService, "_#(js:Error)", error.error.message);
         });
   }

   close(saved: boolean = false): void {
      const index = this.datasourcePath ? this.datasourcePath.lastIndexOf("/") : -1;
      let parentPath = null;

      if(this.parentPath) {
         parentPath = this.parentPath;
      }
      else if(this.datasourcePath != "/" && index > -1) {
         parentPath = this.datasourcePath.substr(0, index);
      }

      const extras = {
         queryParams: {
            path: parentPath,
            scope: AssetConstants.QUERY_SCOPE
         }
      };

      if(!saved && this.gettingStartedService.isConnectTo()) {
         this.gettingStartedService.continue();
      }

      if(saved) {
         this.originalModel = Tool.clone(this._model);
      }

      this.router.navigate(["/portal/tab/data/datasources"], extras);
   }

   clickLocaleListBtn(event: MouseEvent) {
      if(!this.dropDowns) {
         return;
      }

      this.dropDowns
         .filter(dropDown => dropDown?.id == "localList")
         .forEach(dropDown => {
            if(!dropDown.closed) {
               return;
            }

            dropDown.toggleDropdown(event);
         });
   }
}
