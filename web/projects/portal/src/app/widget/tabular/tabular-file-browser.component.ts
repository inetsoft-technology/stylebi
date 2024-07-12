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
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   Output,
   ViewChild
} from "@angular/core";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TabularFile } from "../../common/data/tabular/tabular-file";
import { Observable } from "rxjs";
import { TreeComponent } from "../tree/tree.component";

@Component({
   selector: "tabular-file-browser",
   templateUrl: "tabular-file-browser.component.html",
})
export class TabularFileBrowser implements AfterViewInit {
   @Input() path: string;
   @Input() property: string;
   @Input() pattern: string[];
   @Input() browseFunction: (path: string, property: string, all: boolean) => Observable<TreeNodeModel>;
   @Output() onCommit: EventEmitter<TabularFile> = new EventEmitter<TabularFile>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(TreeComponent) tree: TreeComponent;
   selectedFile: TabularFile;
   root: TreeNodeModel;
   showAll: boolean = false;

   constructor(private changeDetectorRef: ChangeDetectorRef) {
   }

   ngAfterViewInit(): void {
      this.initRoot();
   }

   initRoot() {
      this.browseFunction("/", this.property, this.showAll).subscribe((data) => {
         this.root = data;

         if(this.path != null) {
            setTimeout(() => {
               this.selectInitialPath(this.root);
            });
         }
      });
   }

   ok(): void {
      if(this.selectedFile) {
         this.onCommit.emit(this.selectedFile);
      }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   nodeSelected(node: TreeNodeModel): void {
      this.selectedFile = node.data;
   }

   nodeExpanded(node: TreeNodeModel): void {
      if(node.data && node.data.path !== "/") {
         this.browseFunction((<TabularFile> node.data).path, this.property, this.showAll)
            .subscribe((data) => {
               node.children = data.children;
            });
      }
   }

   iconFunction(node: TreeNodeModel): string {
      let file: TabularFile = <TabularFile> node.data;

      if(file.folder) {
         if(node.expanded) {
            return "folder-expanded-icon";
         }
         else {
            return "folder-collapsed-icon";
         }
      }
      else {
         return "file-inverted-icon";
      }
   }

   getPatternMessage(): string {
      if(!this.pattern || this.pattern.length == 0 || !this.selectedFile || this.showAll) {
         return null;
      }

      for(let i = 0; i < this.pattern.length; i++) {
         // pattern for folders
         if(i == 0 && this.selectedFile.folder &&
            !this.selectedFile.absolutePath.match(this.pattern[i]))
         {
            return "Folder needs to match the following pattern: " + this.pattern[i];
         }
         // pattern for files
         else if(i == 1 && !this.selectedFile.folder &&
            !this.selectedFile.absolutePath.match(this.pattern[i]))
         {
            return "File needs to match the following pattern: " + this.pattern[i];
         }
      }

      return null;
   }

   selectInitialPath(node: TreeNodeModel) {
      let index: number = -1;

      // if root
      if(node.data.path === "/") {
         index = this.path.indexOf("/");
      }
      else {
         index = this.path.indexOf("/", node.data.path.length + 1);
      }

      let path = index != -1 ? this.path.substring(0, index) : this.path;

      for(let child of node.children) {
         if(child.data.path === path) {
            if(path === this.path) {
               this.tree.selectAndExpandToNode(child);
               this.selectedFile = child.data;
               this.changeDetectorRef.detectChanges();
            }
            else {
               this.browseFunction(path, this.property, this.showAll).subscribe((data) => {
                  child.children = data.children;
                  this.selectInitialPath(child);
               });
            }

            break;
         }
      }
   }
}
