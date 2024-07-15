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
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { RepositoryEntry } from "../../../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { RepositoryTreeComponent } from "../../../../widget/repository-tree/repository-tree.component";
import { RepositoryTreeService } from "../../../../widget/repository-tree/repository-tree.service";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";

@Component({
   selector: "select-dashboard-dialog",
   templateUrl: "select-dashboard-dialog.component.html"
})
export class SelectDashboardDialog implements OnInit {
   @Input() path: string;
   @Input() actualPath: string;
   @Input() isReport: boolean;
   @Input() showVS: boolean;
   @Input() title: string;
   @Input() legend: string;
   @Input() selector: number = RepositoryEntryType.FOLDER | RepositoryEntryType.VIEWSHEET;
   @Output() onCommit = new EventEmitter<RepositoryEntry>();
   @Output() onCancel = new EventEmitter<string>();
   @ViewChild(RepositoryTreeComponent) tree: RepositoryTreeComponent;
   rootNode: TreeNodeModel;
   private selectedEntry: RepositoryEntry;

   constructor(private repositoryTreeService: RepositoryTreeService,
               private changeRef: ChangeDetectorRef)
   {
   }

   ngOnInit(): void {
      this.repositoryTreeService.getRootFolder(null, this.selector, null, false, true, true, false, false).subscribe(
         (data) => {
            this.rootNode = data;

            if(this.actualPath) {
               this.changeRef.detectChanges();
               Promise.resolve(null).then(() => {
                  this.tree.selectAndExpandToPath(this.actualPath);
               });
            }
         });
   }

   nodeSelected(node: TreeNodeModel) {
      const entry: RepositoryEntry = node.data;

      if(entry.type !== RepositoryEntryType.FOLDER) {
         this.path = entry.path;
         this.selectedEntry = entry;
      }
      else {
         this.clear();
      }
   }

   clear(): void {
      this.path = null;
      this.selectedEntry = null;
      this.tree.selectedNode = null;
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      if(this.selectedEntry) {
         this.onCommit.emit(this.selectedEntry);
      }
      else {
         // User did not manually select a new node.
         this.cancelChanges();
      }
   }
}
