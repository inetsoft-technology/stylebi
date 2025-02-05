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
import { Injectable, OnDestroy } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";
import { BindingTreeService } from "../../binding/widget/binding-tree/binding-tree.service";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModelService } from "../../widget/services/model.service";
import { ContextMenuActions } from "../../widget/context-menu/context-menu-actions";
import { Observable, Subject } from "rxjs";
import { VSWizardBindingTreeActions } from "../model/vs-wizard-binding-tree-actions";
import { VSWizardTreeInfoModel } from "../model/vs-wizard-tree-info-model";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../common/data/asset-entry-helper";
import { HttpClient, HttpParams } from "@angular/common/http";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { RefreshBindingFieldsEvent } from "../model/event/refresh-binding-fields-event";
import { UpdateVsWizardBindingEvent } from "../model/event/update-vs-wizard-binding-event";
import { VsWizardEditModes } from "../model/vs-wizard-edit-modes";

const VS_WIZARD_SOURCE_CHANGE = "../api/vswizard/binding/sourcechange";
const WIZARD_TREE_CHECKTRAP = "../api/vswizard/binding/tree/checktrap";
const WIZARD_AGG_CHECKTRAP = "../api/vswizard/binding/aggregate/checktrap";

@Injectable()
export class VSWizardBindingTreeService extends BindingTreeService implements OnDestroy {
   refreshSubject = new Subject<any>();
   recommenderSubject = new Subject<boolean>();
   private _selectedNodes: TreeNodeModel[];
   // The treeInfo.tempBinding should always is temp chart binding. Don't change it to other assembly.
   treeInfo: VSWizardTreeInfoModel;
   selectedPaths: string[]; // for reload selected nodes.

   constructor(private http: HttpClient) {
      super();
      this.reset();
   }

   get selectedNodes(): TreeNodeModel[] {
      return this._selectedNodes;
   }

   set selectedNodes(nodes: TreeNodeModel[]) {
      this._selectedNodes = nodes;
      this.selectedPaths = this.getSelectedBindingNodePaths();
   }

   resetTreeModel(bindingTreeModel: TreeNodeModel): void {
      if(this._bindingTreeModel) {
         this.copyExpanded(this._bindingTreeModel, bindingTreeModel);
      }

      super.resetTreeModel(bindingTreeModel);
   }

   reset() {
      this.treeInfo = null;
      this.selectedNodes = [];
      this.selectedPaths = [];
   }

   ngOnDestroy(): void {
      if(!!this.refreshSubject) {
         this.refreshSubject.unsubscribe();
         this.refreshSubject = null;
      }

      if(!!this.recommenderSubject) {
         this.recommenderSubject.unsubscribe();
         this.recommenderSubject = null;
      }
   }

   expandNode(root: TreeNodeModel): void {
      if(this._bindingTreeModel) {
         this.copyExpanded(this._bindingTreeModel, root);
      }

      let sinfo = !!this.treeInfo?.tempBinding ?
         this.treeInfo.tempBinding.source : null;
      let tableName: string = sinfo ? sinfo.source : null;

      for(let child of root.children) {
         let entry: AssetEntry = child.data;
         let tname: string = AssetEntryHelper.getEntryName(entry);

         if(tableName == tname) {
            this.expandAllChildren(child);
            break;
         }
      }
   }

   getBindingTreeActions(selectedNode: TreeNodeModel, selectedNodes: TreeNodeModel[],
                         dialogService: NgbModal, modelService: ModelService,
                         service: any, bindingInfo?: any, treeInfo?: VSWizardTreeInfoModel,
                         wizardOriginalMode?: VsWizardEditModes):
      ContextMenuActions
   {
      return new VSWizardBindingTreeActions(selectedNode, selectedNodes, dialogService,
         this, modelService, service, bindingInfo, treeInfo, wizardOriginalMode);
   }

   /**
    * Refresh tree
    */
   public refresh: (reload: boolean) => any =
      (reload: boolean = false) => this.refreshSubject.next(reload);

   /**
    * Recommender by current selected binding nodes.
    */
   public recommender = (reload: boolean = false) => this.recommenderSubject.next(reload);

   public reloadSelectedBinding(reload: boolean): void {
      if(!!this.selectedPaths) {
         let nodes: TreeNodeModel[] = [];
         this.selectedPaths
            .forEach(path => {
               let node = GuiTool.getNodeByPath(path, this.bindingTreeModel);

               if(!!node) {
                  nodes.push(node);
               }
            });

         if(!Tool.isEquals(nodes, this.selectedNodes) || this.containsGeo(nodes) ||
            (nodes.length == 0 && this.selectedNodes.length == 0))
         {
            this.selectedNodes = nodes;
            this.recommender(reload);
         }
      }
   }

   private nodesEquals(nodes: TreeNodeModel[], snodes: TreeNodeModel[]): boolean {
      if(nodes.length != snodes.length) {
         return false;
      }

      for(let i = 0; i < nodes.length; i++) {
         if(!Tool.isEquals(nodes[i].data, snodes[i].data)) {
            return false;
         }
      }

      return true;
   }

   private containsGeo(nodes: TreeNodeModel[]): boolean {
      if(!nodes) {
         return false;
      }

      for(let i = 0; i < nodes.length; i++) {
         let node: TreeNodeModel = nodes[i];

         if(!!node && !!node.data && node.data.properties &&
            node.data.properties["isGeo"] == "true")
         {
            return true;
         }
      }

      return false;
   }

   getSelectedBindingNodePaths(): string[] {
      let selectNodePaths: string[] = [];

      this.selectedNodes.forEach((node: TreeNodeModel) => {
         selectNodePaths.push(node.data.path);
      });

      return selectNodePaths;
   }

   public changeSource(runtimeId: string, tableName: string): Observable<any> {
      const params = new HttpParams()
         .set("runtimeId", runtimeId)
         .set("tableName", tableName);

      return this.http.get(VS_WIZARD_SOURCE_CHANGE, {params: params});
   }
   public checkAggTrap(runtimeId: string, evt: UpdateVsWizardBindingEvent): Observable<any> {
      const params = new HttpParams()
         .set("runtimeId", runtimeId);

      return this.http.post(WIZARD_AGG_CHECKTRAP, evt, {params: params});
   }

   public showSourceChangedDialog(dialogService: NgbModal): Promise<any> {
      const msg = "_#(js:viewer.viewsheet.chart.sourceChanged)";
      return ComponentTool.showConfirmDialog(dialogService, "_#(js:Confirm)", msg);
   }

   public unSelectNode(nodeName: string): void {
      this.selectedNodes =
         this.selectedNodes.filter(item => item.data.properties.attribute != nodeName);
      this.recommender();
   }

   public findSelectedNode(colName: string): TreeNodeModel {
      return this.selectedNodes.filter(n => !!n)
         .find(n => n.label == colName);
   }

   public unSupportedException(dialogService: NgbModal): void {
      ComponentTool.showMessageDialog(dialogService, "_#(js:Error)",
         "_#(js:common.report.binding.unsupportedOperation)");
   }
}
