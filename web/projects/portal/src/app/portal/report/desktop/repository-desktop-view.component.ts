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
import {
   AfterViewInit,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { RepositoryEntry } from "../../../../../../shared/data/repository-entry";
import { SplitPane } from "../../../widget/split-pane/split-pane.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ReportTabModel } from "../report-tab-model";

@Component({
   selector: "p-repository-desktop-view",
   templateUrl: "./repository-desktop-view.component.html",
   styleUrls: ["./repository-desktop-view.component.scss"]
})
export class RepositoryDesktopViewComponent implements OnInit, AfterViewInit, OnChanges {
   @Input() model: ReportTabModel;
   @Input() rootNode: TreeNodeModel;
   @Input() selectedEntry: RepositoryEntry;
   @Input() treePaneCollapsed: boolean = false;
   @Input() openedEntrys: RepositoryEntry[] = [];
   @Output() entryOpened = new EventEmitter<RepositoryEntry>();
   @Output() entryDeleted = new EventEmitter<RepositoryEntry>();
   @Output() editViewsheet = new EventEmitter<RepositoryEntry>();
   @Output() collapseTree = new EventEmitter<boolean>();
   @ViewChild(SplitPane) splitPane: SplitPane;

   readonly INIT_TREE_PANE_SIZE = 25;
   treePaneSize: number = this.INIT_TREE_PANE_SIZE;
   private inited = false;

   ngOnInit(): void {
   }

   ngAfterViewInit() {
      this.inited = true;
      this.updateRepositoryTreePane();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inited && changes["treePaneCollapsed"]) {
         this.updateRepositoryTreePane();
      }
   }

   splitPaneDragEnd(): void {
      this.treePaneSize = this.splitPane.getSizes()[0];

      if(this.treePaneSize > 1) {
         this.treePaneCollapsed = false;
      }
      else {
         this.treePaneCollapsed = true;
         this.treePaneSize = this.INIT_TREE_PANE_SIZE;
      }
   }

   toggleRepositoryTreePane(): void {
      this.treePaneCollapsed = !this.treePaneCollapsed;
      this.updateRepositoryTreePane();
      this.collapseTree.emit(this.treePaneCollapsed);
   }

   updateRepositoryTreePane(): void {
      if(!this.treePaneCollapsed) {
         this.splitPane.setSizes([this.treePaneSize, 100 - this.treePaneSize]);
      }
      else {
         this.splitPane.collapse(0);
      }
   }
}
