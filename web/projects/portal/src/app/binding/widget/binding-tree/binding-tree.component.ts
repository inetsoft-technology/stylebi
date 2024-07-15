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
import { HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   Optional,
   Output,
   SimpleChanges
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry, createAssetEntry } from "../../../../../../shared/data/asset-entry";
import { Tool } from "../../../../../../shared/util/tool";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { DataRef } from "../../../common/data/data-ref";
import { DragEvent } from "../../../common/data/drag-event";
import { DndService } from "../../../common/dnd/dnd.service";
import { CollectParametersOverEvent } from "../../../common/event/collect-parameters-over-event";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { MessageCommand } from "../../../common/viewsheet-client/message-command";
import { LoadAssetTreeNodesValidator } from "../../../widget/asset-tree/load-asset-tree-nodes-validator";
import { VariableInputDialogModel } from "../../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { VariableInputDialog } from "../../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { DomService } from "../../../widget/dom-service/dom.service";
import { ModelService } from "../../../widget/services/model.service";
import { SlideOutOptions } from "../../../widget/slide-out/slide-out-options";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { BindingTreeService } from "./binding-tree.service";
import { VirtualScrollTreeDatasource } from "../../../widget/tree/virtual-scroll-tree-datasource";

const GET_PARAMETERS_URI = "../api/vs/bindingtree/getConnectionParameters";
const SET_CONNECTION_VARIABLES = "../api/composer/asset_tree/set-connection-variables";

@Component({
   selector: "binding-tree",
   templateUrl: "binding-tree.component.html",
})
export class BindingTreeComponent implements OnChanges {
   @Input() selectedNodes: TreeNodeModel[] = [];
   @Input() draggable: boolean = true;
   @Input() multiSelect: boolean = false;
   @Input() filterTreeModel: TreeNodeModel;
   @Input() grayedOutFields: DataRef[];
   @Input() showTooltip: boolean = true;
   @Input() fillHeight: boolean = false;
   @Input() searchEnable: boolean = true;
   @Input() searchStr: string = "";
   @Input() rid: string = "";
   @Input() binding: boolean = true;
   @Input() contextmenu: boolean = false;
   @Input() hasMenuFunction: (node: TreeNodeModel) => boolean;
   @Input() hoverShowScroll: boolean = true;
   @Input() useVirtualScroll: boolean = this.uiContextService.isVS();
   @Input() composer: boolean;
   @Input() virtualScrollTreeDatasource: VirtualScrollTreeDatasource;
   @Output() nodeSelected = new EventEmitter<TreeNodeModel>();
   @Output() onContextmenu = new EventEmitter<[MouseEvent, TreeNodeModel, TreeNodeModel[]]>();
   @Output() onNodeDrop = new EventEmitter<DragEvent>();
   @Output() nodeExpanded: EventEmitter<TreeNodeModel> = new EventEmitter<TreeNodeModel>();
   @Output() nodeCollapsed: EventEmitter<TreeNodeModel> = new EventEmitter<TreeNodeModel>();
   variableInputDialogModel: VariableInputDialogModel;
   private loadFullTree: boolean = false;

   constructor(private bindingTreeService: BindingTreeService,
               @Optional() private dndService: DndService,
               private modelService: ModelService,
               private modalService: NgbModal,
               private zone: NgZone,
               private uiContextService: UIContextService,
               private domService: DomService)
   {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.selectedNodes &&
         this.selectedNodes != null &&
         this.selectedNodes.length > 0)
      {
         if(this.bindingTreeService.virtualScrollDataSource) {
            this.bindingTreeService.virtualScrollDataSource.restoreScrollTop();
         }
      }
   }

   get bindingTreeModel(): TreeNodeModel {
      return this.filterTreeModel ? this.filterTreeModel :
         this.bindingTreeService.bindingTreeModel;
   }

   get virtualDataSource(): VirtualScrollTreeDatasource {
      return this.virtualScrollTreeDatasource || this.bindingTreeService.virtualScrollDataSource;
   }

   get needUseVirtualScroll(): boolean {
      return this.useVirtualScroll && this.bindingTreeService.needUseVirtualScroll;
   }

   selectNode(node: TreeNodeModel): void {
      this.nodeSelected.emit(node);
   }

   public dragNode(event: any) {
      const srcData = JSON.parse(event.dataTransfer.getData("text"));
      let entriesValue: any = srcData.column;
      const dragName: string[] = this.binding ? ["binding"] : srcData.dragName;

      if(this.multiSelect && entriesValue != null && entriesValue) {
         let entries: AssetEntry[] = <AssetEntry[]> entriesValue;
         let sourceInfo: any = this.bindingTreeService.getSourceInfo(entries[0]);

         if(sourceInfo) {
            Tool.setTransferData(event.dataTransfer, {
               source: sourceInfo.source,
               prefix: sourceInfo.prefix,
               type: sourceInfo.type
            });
         }

         const labels: string[] = entries.map(e => AssetEntryHelper.getEntryName(e));
         const elem = GuiTool.createDragImage(labels, dragName);
         GuiTool.setDragImage(event, elem, this.zone, this.domService);

         if(this.dndService) {
            this.dndService.setDragStartStyle(event, labels.join(","));
         }
      }
      else if(srcData.table) {
         let cols: number = 0;
         cols = srcData.table
            .filter(t => t && t.identifier)
            .map(t => t.identifier)
            .map(t => this.bindingTreeService.getNode(AssetEntryHelper.toIdentifier(
               createAssetEntry(t))))
            .map(n => n && n.children ? n.children.length : 0)
            .reduce((a, b) => Math.max(a, b), 0);
         const labels: string[] = !!srcData.table[0].path && srcData.table[0].path.lastIndexOf("/") < 0
            ? [srcData.table[0].path] : [srcData.table[0].path.split("/").pop()];
         const elem = GuiTool.createDragImage(labels, dragName, cols);
         GuiTool.setDragImage(event, elem, this.zone, this.domService);
      }
   }

   contextmenuTreeNode(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]) {
      this.onContextmenu.emit(event);
   }

   public nodeDrop(event: any) {
      const evt = <DragEvent> event.evt;

      if(evt) {
         // Bug #21779, prevent open blank page on firefox
         evt.preventDefault();
         evt.stopPropagation();
      }

      this.onNodeDrop.emit(evt);
   }

   searchStart(start: boolean): void {
      this.bindingTreeService.expandNodesCollapseOthersByRecord();

      if(!this.loadFullTree && start) {
         this.bindingTreeService.loadFullTree().then(success => {
            if(success) {
               this.loadFullTree = true;
            }
         });
      }

      this.bindingTreeService.changeSearchState(start);
   }

   /**
    * Method for determining the css class of an entry by its AssetType
    */
   public getCSSIcon(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   public expandNode(node: TreeNodeModel) {
      if((this.uiContextService.isVS()) && !this.composer) {
         this.bindingTreeService.expandNode(node);
      }
      else {
         this.validateBindingTree(node);
      }

      this.nodeExpanded.emit(node);
   }

   public isLoadingTree(): boolean {
      return this.bindingTreeService.treeLoading();
   }

   public collapseNode(node: TreeNodeModel) {
      if(!this.composer) {
         this.bindingTreeService.collapseNode(node);
      }

      this.nodeCollapsed.emit(node);
   }

   private validateBindingTree(root: TreeNodeModel): void {
      if(!root.data) {
         this.bindingTreeService.expandNode(root);
         return;
      }

      let entry: AssetEntry = root.data;
      const params = new HttpParams().set("rid", this.rid);
      this.modelService.sendModel<LoadAssetTreeNodesValidator>(GET_PARAMETERS_URI, entry, params).subscribe((res) => {
         const nodeValidator: LoadAssetTreeNodesValidator = res.body;

         if(nodeValidator && nodeValidator.parameters && nodeValidator.parameters.length) {
            this.variableInputDialogModel =
               <VariableInputDialogModel> {varInfos: nodeValidator.parameters};
            this.openVariableInputDialog();
         }
      });
   }

   private openVariableInputDialog() {
      let options: SlideOutOptions = {backdrop: "static"};

      const dialog = ComponentTool.showDialog(this.modalService, VariableInputDialog,
         (model: VariableInputDialogModel) => {
            let event: CollectParametersOverEvent =
               new CollectParametersOverEvent(model.varInfos);

            this.modelService.sendModel<MessageCommand>(SET_CONNECTION_VARIABLES, event).subscribe(
               (res) => {
                  const messageCommand: MessageCommand = res.body;

                  if(!!messageCommand.message) {
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                        messageCommand.message);
                  }
               },
               () => this.openVariableInputDialog()
            );
         }, options);
      dialog.model = this.variableInputDialogModel;
   }
}
