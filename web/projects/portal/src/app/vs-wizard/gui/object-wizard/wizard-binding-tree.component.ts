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
import { Component, Input, NgZone, OnDestroy, OnInit, Optional, ViewChild } from "@angular/core";
import { Subscription } from "rxjs";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { NotificationsComponent } from "../../../widget/notifications/notifications.component";
import { Tool } from "../../../../../../shared/util/tool";
import {
   CommandProcessor,
   ViewsheetClientService,
   ViewsheetCommand
} from "../../../common/viewsheet-client";
import { ReplaceColumnCommand } from "../../model/command/replace-column-command";
import { GetBindingTreeEvent } from "../../model/event/get-binding-tree-event";
import { RefreshBindingFieldsEvent } from "../../model/event/refresh-binding-fields-event";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { ContextMenuActions } from "../../../widget/context-menu/context-menu-actions";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import { BindingTreeService } from "../../../binding/widget/binding-tree/binding-tree.service";
import { GuiTool } from "../../../common/util/gui-tool";
import { ModelService } from "../../../widget/services/model.service";
import { VSWizardTreeInfoModel } from "../../model/vs-wizard-tree-info-model";
import { RefreshWizardTreeCommand } from "../../model/command/refresh-wizard-tree-command";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { HttpParams } from "@angular/common/http";
import { OpenEditGeographicCommand } from "../../../binding/command/open-edit-geographic-command";
import { VSGeoProvider } from "../../../binding/editor/vs-geo-provider";
import { GeoProvider } from "../../../common/data/geo-provider";
import { EditGeographicDialog } from "../../../binding/widget/binding-tree/edit-geographic-dialog.component";
import { ComponentTool } from "../../../common/util/component-tool";
import { ChartGeoRef } from "../../../binding/data/chart/chart-geo-ref";
import { ChangeChartRefEvent } from "../../../binding/event/change-chart-ref-event";
import { RefreshWizardTreeTriggerCommand } from "../../model/command/refresh-wizard-tree-trigger-command";
import { MessageCommand } from "../../../common/viewsheet-client/message-command";
import { SetGrayedOutFieldsCommand } from "../../../binding/command/set-grayed-out-fields-command";
import { SetWizardBindingTreeNodesCommand } from "../../model/command/set-wizard-binding-tree-nodes-command";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { VSBindingTrapCommand } from "../../../binding/command/vs-binding-trap-command";
import { FireRecommandCommand } from "../../model/command/fire-recommand-command";
import { FeatureMappingInfo } from "../../../binding/data/chart/feature-mapping-info";
import { InitWizardBindingTreeCommand } from "../../model/command/init-wizard-binding-tree-command";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { VirtualScrollService } from "../../../widget/tree/virtual-scroll.service";
import { VirtualScrollTreeDatasource } from "../../../widget/tree/virtual-scroll-tree-datasource";

const GET_BINDING_TREE_URI = "/events/vswizard/binding/tree";
const REFRESH_BINDING_FIELDS_URI = "/events/vswizard/binding/tree/refresh-fields";
const REFRESH_BINDING_NODES_CHANGED_URI = "/events/vswizard/binding/tree/node-changed";
const OBJECT_WIZARD_REFRESH = "/events/vswizard/object-wizard/refresh";

@Component({
   selector: "wizard-binding-tree",
   templateUrl: "./wizard-binding-tree.component.html",
   styleUrls: ["./wizard-binding-tree.component.scss"]
})
export class WizardBindingTree extends CommandProcessor implements OnInit, OnDestroy {
   @Input() runtimeId: string;
   @Input() temporarySheet: boolean;
   @Input() originalMode: VsWizardEditModes;
   @ViewChild(TreeComponent) tree: TreeComponent;
   @ViewChild("notifications") notifications: NotificationsComponent;

   activeRoot: TreeNodeModel;
   bindingTreeSubscription: Subscription = new Subscription();
   toRecommend: boolean = true;

   constructor(private bindingTreeService: VSWizardBindingTreeService,
               protected viewsheetClient: ViewsheetClientService,
               private dropdownService: FixedDropdownService,
               private treeService: BindingTreeService,
               private modelService: ModelService,
               private dialogService: NgbModal,
               protected zone: NgZone)
   {
      super(viewsheetClient, zone, true);
   }

   ngOnInit() {
      this.bindingTreeSubscription.add(this.bindingTreeService.refreshSubject.subscribe((reload) => {
         this.initBindingTree(reload);
      }));

      this.bindingTreeSubscription.add(
         this.bindingTreeService.recommenderSubject.subscribe((reload) => {
            if(this.toRecommend) {
               this.recommender(null, reload);
            }
            else {
               this.viewsheetClient.sendEvent(OBJECT_WIZARD_REFRESH);
            }

            this.toRecommend = true;
         })
      );
   }

   ngOnDestroy(): void {
      if(!!this.bindingTreeSubscription) {
         this.bindingTreeSubscription.unsubscribe();
      }

      this.cleanup();
   }

   get virtualScrollDatasource(): VirtualScrollTreeDatasource  {
      return this.bindingTreeService?.virtualScrollDataSource;
   }

   private initBindingTree(reload: boolean): void {
      let event: GetBindingTreeEvent =
         new GetBindingTreeEvent(false, null, reload);
      this.viewsheetClient.sendEvent(GET_BINDING_TREE_URI, event);
   }

   private processRefreshWizardTreeCommand(command: RefreshWizardTreeCommand): void {
      if(this.bindingTreeService == null) {
         return;
      }

      if(command.forceRefresh && this.noNeedReloadTree()) {
         return;
      }

      this.flatternTree(command.treeModel);
      this.bindingTreeService.resetTreeModel(command.treeModel);
      this.bindingTreeService.reloadSelectedBinding(command.reload);
      this.treeInfo = command.treeInfo;
      this.activeRoot = this.wizardBindingTree;

      if(!!this.activeRoot && !!this.activeRoot.children
         && this.activeRoot.children.length == 1)
      {
         this.bindingTreeService.expandAllChildren(this.activeRoot);
      }
      else if(!!this.tree) {
         this.tree.root = this.activeRoot;
         this.selectedNodes.forEach((selectedNode) => {
            this.tree.expandToNode(selectedNode);
         });
      }

      if(this.bindingTreeService.virtualScrollDataSource) {
         this.bindingTreeService.virtualScrollDataSource.refreshByRoot(this.activeRoot);
      }
   }

   /**
    * flattern dimension and measure
    */
   private flatternTree(root: TreeNodeModel): void {
      if(!!!root) {
         return;
      }

      if(root.children[0] != null && root.children[0].label == "Dimensions" &&
         root.children[1] != null && root.children[1].label == "Measures")
      {
         let calcs = root.children[0].children.concat(root.children[1].children);
         calcs.sort((calc1, calc2) =>
            calc1.label.toLocaleLowerCase().localeCompare(calc2.label.toLocaleLowerCase()));
         root.children.splice(0, 2);
         root.children = calcs.concat(root.children);
      }

      root.children.forEach(tbl => {
         if(tbl.children[0] == null || tbl.children[1] == null) {
            return;
         }

         // all dimensions and measures
         const all = tbl.children[0].children.concat(tbl.children[1].children);
         tbl.children = all.sort((n1, n2) => {
            return n1.label.toLocaleLowerCase().localeCompare(n2.label.toLocaleLowerCase());
         });
      });
   }

   processVSTrapCommand(command: MessageCommand): void {
      ComponentTool.showTrapAlert(this.dialogService, false)
         .then((result: string) => {
            if(result == "yes") {
               for(let key in command.events) {
                  if(command.events.hasOwnProperty(key)) {
                     let evt: any = command.events[key];
                     evt.confirmed = true;
                     this.viewsheetClient.sendEvent(key, evt);
                  }
               }
            }
            else {
               this.bindingTreeService.selectedNodes.pop();
            }
      });
   }

   private noNeedReloadTree() {
      let opaths: string[] = this.bindingTreeService.getSelectedBindingNodePaths();
      let npaths: string[] = this.bindingTreeService.selectedPaths;

      if(opaths == null || npaths == null) {
         return false;
      }

      if(opaths.length == 0 && npaths.length == 0) {
         return false;
      }

      if(opaths.length != npaths.length) {
         return false;
      }

      return opaths.every(function(path) { return npaths.indexOf(path) != -1; });
   }

   private processSetGrayedOutFieldsCommand(command: SetGrayedOutFieldsCommand): void {
      if(this.treeInfo == null) {
         return;
      }

      this.treeInfo.grayedOutFields = command.fields;
   }

   get treeInfo(): VSWizardTreeInfoModel {
      return this.bindingTreeService.treeInfo;
   }

   set treeInfo(treeInfo: VSWizardTreeInfoModel) {
      this.bindingTreeService.treeInfo = treeInfo;
   }


   get wizardBindingTree(): TreeNodeModel {
      return this.bindingTreeService.bindingTreeModel;
   }

   get selectedNodes(): TreeNodeModel[] {
      return this.bindingTreeService.selectedNodes;
   }

   set selectedNodes(nodes: TreeNodeModel[]) {
      this.bindingTreeService.selectedNodes = nodes;
   }

   get needUseVirtualScroll(): boolean {
      return this.bindingTreeService?.needUseVirtualScroll;
   }

   expandNode(node: TreeNodeModel): void {
      this.bindingTreeService.expandNode(node);

      if(this.bindingTreeService.virtualScrollDataSource) {
         this.bindingTreeService.virtualScrollDataSource.nodeExpanded(this.activeRoot, node);
      }
   }

   collapsedNode(node: TreeNodeModel): void {
      if(this.bindingTreeService.virtualScrollDataSource) {
         this.bindingTreeService.virtualScrollDataSource.nodeCollapsed(this.activeRoot, node);
      }
   }

   selectNodes(selectedNodes: TreeNodeModel[]) {
      this.selectedNodes = selectedNodes;
      let tableName: string = null;

      if(selectedNodes.length > 0) {
         let lastNode: AssetEntry = <AssetEntry> selectedNodes[selectedNodes.length - 1].data;
         tableName = this.treeService.getTableName(lastNode);

         if(!tableName && !!lastNode.properties["assembly"]) {
            tableName = lastNode.properties["assembly"];
         }
      }

      this.recommender(tableName);
   }

   public recommender(tableName?: string, reload?: boolean): void {
      let event = new RefreshBindingFieldsEvent(
         this.selectedNodes
            .filter(node => !!node && !!node.data)
            .map((node) => node.data),
         false, tableName, null, !!reload);
      this.viewsheetClient.sendEvent(REFRESH_BINDING_NODES_CHANGED_URI, event);
   }

   /**
    * Method for determining the css class of an entry by its AssetType
    */
   public getCSSIcon(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   searchStart(): void {
      this.activeRoot = this.wizardBindingTree;

      if(this.bindingTreeService.virtualScrollDataSource) {
         this.bindingTreeService.virtualScrollDataSource.refreshByRoot(this.activeRoot);
      }
   }

   getAssemblyName(): string {
      return null;
   }

   hasMenuFunction(): any {
      return (node) => this.hasMenu(node);
   }

   hasMenu(node: TreeNodeModel): boolean {
      const actions: ContextMenuActions = this.createWizardActions([null, node, [node]]);
      return actions && actions.actions.some(group => group.visible);
   }

   openWizardTreeContextmenu(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): void {
      if(!event || event.length < 2) {
         return;
      }

      let options: DropdownOptions = {
         position: {x: event[0].clientX + 2, y: event[0].clientY + 2},
         contextmenu: true,
      };

      let contextmenu: ActionsContextmenuComponent = this.dropdownService
         .open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];

      let actions: ContextMenuActions = this.createWizardActions(event);

      if(!!actions) {
         contextmenu.actions = actions.actions;
      }
   }

   getURLParams(): HttpParams {
      let parameters = new HttpParams().set("vsId", this.runtimeId)
         .set("assemblyName", this.treeInfo ? this.treeInfo.tempAssemblyName : null);

      return parameters;
   }

   createWizardActions(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): ContextMenuActions {
      const selectNode = event[1];
      const selectedNodes: TreeNodeModel[] = event[2];

      const bindingInfo: any = {
         "bindingModel": this.treeInfo ? this.treeInfo.tempBinding : null,
         "assemblyName": this.treeInfo ? this.treeInfo.tempAssemblyName : null,
         "urlParams": this.getURLParams(),
         "objectType": "VSChart",
         "grayedOutFields": this.treeInfo ? this.treeInfo.grayedOutFields : []
      };

      return this.bindingTreeService.getBindingTreeActions(selectNode, selectedNodes,
         this.dialogService, this.modelService,
         this.viewsheetClient, bindingInfo, this.treeInfo, this.originalMode);
   }

   private get params(): HttpParams {
      return new HttpParams()
         .set("vsId", this.runtimeId)
         .set("assemblyName", this.treeInfo.tempAssemblyName)
         .set("temporarySheet", "" + !!this.temporarySheet)
         .set("viewer", "false");
   }

   processVSWizardSourceChangeCommand(command: ViewsheetCommand): void {
      this.bindingTreeService.showSourceChangedDialog(this.dialogService)
         .then((result: string) =>  {
            let val: boolean = result === "ok";

            if(val) {
               this.selectedNodes = [this.selectedNodes.pop()];
               this.refreshBindingFields();
            }
            else {
               this.selectedNodes.pop();
            }

            return val;
         });
   }

   private refreshBindingFields(): void {
      let tableName: string =
         this.treeService.getTableName(<AssetEntry> this.selectedNodes[this.selectedNodes.length - 1].data);
      let event = new RefreshBindingFieldsEvent(
         this.selectedNodes
            .filter(node => !!node && !!node.data)
            .map((node) => node.data),
         false, tableName, null, false);
      this.viewsheetClient.sendEvent(REFRESH_BINDING_FIELDS_URI, event);
   }

   private processOpenEditGeographicCommand(command: OpenEditGeographicCommand): void {
      if(command.measureName) {
         const refName = command.measureName;
         const ref: any = Object.assign({},
            command.bindingModel.geoCols.find(col => col.name === refName));

         if(!ref.option) {
            ref.option = {
               layerValue: "",
               mapping: <FeatureMappingInfo> {}
            };
         }

         const dialog: EditGeographicDialog = ComponentTool.showDialog(this.dialogService,
            EditGeographicDialog, () => {
               const name = this.treeInfo.tempAssemblyName;
               const idx = command.bindingModel.geoCols.findIndex(col => col.name === refName);
               command.bindingModel.geoCols[idx] = ref;
               ref.option = 0;
               const event = new ChangeChartRefEvent(name, null, command.bindingModel);
               this.viewsheetClient.sendEvent("/events/vs/chart/changeChartRef", event);
            });

         let geoProvider: GeoProvider = new VSGeoProvider(
            command.bindingModel, this.params,
            ref, this.modelService, refName);
         dialog.refName = refName;
         dialog.provider = geoProvider;
      }
      else {
         let refName = command.chartGeoRefModel.fullName;
         let ref = command.chartGeoRefModel;
         let geoProvider: GeoProvider = new VSGeoProvider(
            command.bindingModel, this.params,
            ref, this.modelService, refName);
         let dialog: EditGeographicDialog = ComponentTool.showDialog(this.dialogService,
            EditGeographicDialog, (result: any) => {
               let name: string = this.treeInfo.tempAssemblyName;
               let idx = this.treeInfo.tempBinding.geoCols
                  .findIndex((col) => (<ChartGeoRef> col).fullName === refName);
               command.bindingModel.geoCols[idx] = ref;
               let event: ChangeChartRefEvent = new ChangeChartRefEvent(name,
                  null, command.bindingModel);
               this.viewsheetClient.sendEvent("/events/vs/chart/changeChartRef", event);
            }, {
               backdrop: "static"
            });

         dialog.refName = refName;
         dialog.provider = geoProvider;
      }
   }

   /**
    * Refresh tree model.
    */
   private processRefreshWizardTreeTriggerCommand(command: RefreshWizardTreeTriggerCommand): void {
      this.bindingTreeService.refresh(false);

      if(!this.bindingTreeService.selectedPaths || this.bindingTreeService.selectedPaths.length == 0) {
         this.bindingTreeService.selectedPaths = this.bindingTreeService.getSelectedBindingNodePaths();
      }
   }

   private processReplaceColumnCommand(command: ReplaceColumnCommand) {
      let oldConvertPaths: string[] = command.oldPaths;
      let convertColPaths: string[] = command.paths;
      let selectNodePaths: string[] = this.bindingTreeService.getSelectedBindingNodePaths();

      [...selectNodePaths].forEach((nodePath: string, idx: number) => {
         let index = oldConvertPaths.findIndex((convertPath) => convertPath == nodePath);

         if(!!convertColPaths[index]) {
            selectNodePaths[idx] = convertColPaths[index];
         }
      });

      this.bindingTreeService.selectedPaths = selectNodePaths;
   }

   /*
   private processMessageCommand(command: MessageCommand): void {
      if(command.message && command.type == "INFO") {
         this.notifications.info(command.message);
      }
      else {
         this.processMessageCommand0(command, this.dialogService, this.viewsheetClient);
      }
   }
   */

   private processSetWizardBindingTreeNodesCommand(command: SetWizardBindingTreeNodesCommand) {
      this.bindingTreeService.selectedPaths = command.selectedPaths;
      this.toRecommend = command.toRecommand;
   }

   private processInitWizardBindingTreeCommand(command: InitWizardBindingTreeCommand) {
      this.processSetWizardBindingTreeNodesCommand(command.setWizardBindingTreeNodesCommand);
      this.processRefreshWizardTreeCommand(command.refreshWizardTreeCommand);
   }
}
