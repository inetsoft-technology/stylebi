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
import { ChangeDetectorRef, Component, EventEmitter, Input, Optional, Output, ViewChild } from "@angular/core";
import { VirtualScrollService } from "../virtual-scroll.service";
import { TreeNodeModel } from "../tree-node-model";
import { DataRef } from "../../../common/data/data-ref";
import { VirtualScrollTreeDatasource } from "../virtual-scroll-tree-datasource";
import { Observable } from "rxjs";
import { TreeComponent } from "../tree.component";

@Component({
  selector: "virtual-scroll-tree",
  templateUrl: "./virtual-scroll-tree.component.html",
})
export class VirtualScrollTreeComponent {
  @Input() root: TreeNodeModel;
  @Input() showRoot: boolean = true;
  @Input() draggable: boolean = false;
  @Input() droppable: boolean = false;
  @Input() multiSelect: boolean = false;
  @Input() nodeSelectable: boolean = true;
  @Input() initExpanded: boolean = false;
  @Input() initSelectedNodesExpanded: boolean = false;
  @Input() iconFunction: (node: TreeNodeModel) => string;
  /** If true, select node on click; if false, select on mousedown. */
  @Input() selectOnClick: boolean = false;
  @Input() grayedOutFields: DataRef[];
  @Input() selectedNodes: TreeNodeModel[] = [];
  @Input() contextmenu: boolean = false;
  @Input() showIcon: boolean = true;
  @Input() disabled: boolean = false;
  @Input() showTooltip: boolean = false;
  @Input() showFavoriteIcon: boolean = false;
  @Input() fillHeight: boolean = false;
  @Input() isGrayFunction: (node: TreeNodeModel) => boolean;
  @Input() hasMenuFunction: (node: TreeNodeModel) => boolean;
  @Input() isRejectFunction: (nodes: TreeNodeModel[]) => boolean;
  @Input() isRepositoryTree: boolean = false;
  @Input() isPortalDataSourcesTree: boolean = false;
  @Input() checkboxEnable: boolean = false;
  @Input() isMobile: boolean = false;
  @Input() ellipsisOverflowText: boolean = false;
  @Input() hoverShowScroll: boolean = false;
  @Input() inputFocus: boolean = false;
  @Input() showOriginalName: boolean = false;
  @Input() helpURL: string = "";
  @Input() useVirtualScroll: boolean;
  @Input() searchStr: string;
  @Input() searchEnabled: boolean;
  @Input() recentEnabled: boolean;
  @Input() getRecentTreeFun: () => Observable<TreeNodeModel[]>;
  @Input() nodeEqualsFun: (node1: TreeNodeModel, node2: TreeNodeModel) => boolean;
  @Output() nodeExpanded = new EventEmitter<TreeNodeModel>();
  @Output() nodeCollapsed = new EventEmitter<TreeNodeModel>();
  @Output() nodesSelected = new EventEmitter<TreeNodeModel[]>();
  @Output() nodeDrag = new EventEmitter<any>();
  @Output() nodeDrop = new EventEmitter<TreeNodeModel>();
  @Output() dblclickNode = new EventEmitter<TreeNodeModel>();
  @Output() nodeClicked = new EventEmitter<TreeNodeModel>();
  @Output() onContextmenu = new EventEmitter<[MouseEvent | any, TreeNodeModel, TreeNodeModel[]]>();
  @Output() searchStart = new EventEmitter<boolean>();
  @ViewChild(TreeComponent) tree: TreeComponent;
  dataSource: VirtualScrollTreeDatasource = new VirtualScrollTreeDatasource();
  private _searchEndNode: (node: TreeNodeModel) => boolean;

  @Input()
  set searchEndNode(searchEndNode: (node: TreeNodeModel) => boolean) {
    this._searchEndNode = searchEndNode;
    this.dataSource.setSearchEndNode(searchEndNode);
  }

  get searchEndNode() {
    return this._searchEndNode;
  }

  onNodeExpanded(node: TreeNodeModel): void {
    this.nodeExpanded.emit(node);
    this.nodeStateChanged(node, true);
  }

  public getParentNode(childNode: TreeNodeModel, parentNode?: TreeNodeModel): TreeNodeModel {
    return this.tree.getParentNode(childNode, parentNode);
  }

  public getNodeByData(compareType: string, data: any, parentNode?: TreeNodeModel): TreeNodeModel {
    return this.tree.getNodeByData(compareType, data, parentNode);
  }

  onNodeCollapsed(node: TreeNodeModel): void {
    this.nodeCollapsed.emit(node);
    this.nodeStateChanged(node, false);
  }

  nodeStateChanged(node: TreeNodeModel, expand: boolean): void {
    if(expand) {
      this.dataSource.nodeExpanded(this.root, node);
    }
    else {
      this.dataSource.nodeCollapsed(this.root, node);
    }
  }

  private resetDataSource(): void {
    this.dataSource.refreshByRoot(this.root);
  }
}
