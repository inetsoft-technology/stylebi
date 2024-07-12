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
import { FlatTreeControl } from "@angular/cdk/tree";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { of, Observable, throwError } from "rxjs";
import { catchError, map, tap } from "rxjs/operators";
import { QueryColumnsModel } from "../../../../../../../../portal/src/app/widget/email-dialog/query-columns-model";
import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../../shared/data/asset-type";
import { FlatTreeDataSource } from "../../../../../common/util/tree/flat-tree-data-source";
import { FlatTreeNode, TreeDataModel } from "../../../../../common/util/tree/flat-tree-model";
import { Tool } from "../../../../../../../../shared/util/tool";
import { BurstEmailDialogData } from "../burst-email-dialog.component";
import {
   LabeledAssetEntries,
   LabeledAssetEntryModel
} from "../../../model/labeled-asset-entry-list-model";

const BURST_ASSET_TREE = "../api/em/schedule/task/action/burst/asset-tree";
const BURST_QUERY_COLUMNS = "../api/em/schedule/task/action/burst/query-columns";

export class AssetFlatNode extends FlatTreeNode<LabeledAssetEntryModel> {
   constructor(public expandable: boolean, public entry: AssetEntry, public name: string,
               public level: number, public data: LabeledAssetEntryModel,
               public loading: boolean = false)
   {
      super(name, level, expandable, data, loading);
   }
}

export class AssetDataSource extends FlatTreeDataSource<AssetFlatNode, LabeledAssetEntryModel> {
   constructor(treeControl: FlatTreeControl<AssetFlatNode>)
   {
      super(treeControl);

   }

   protected getChildren(node: AssetFlatNode): Observable<TreeDataModel<LabeledAssetEntryModel>> {
      return of({
         nodes: node.data.children
      });
   }

   transform(model: TreeDataModel<LabeledAssetEntryModel>, level: number): AssetFlatNode[] {
      if(model.nodes) {
         return model.nodes.map((entryModel: LabeledAssetEntryModel) => {
            const entry = entryModel.entry;
            const expandable = entry.folder && !this.isEntrySelectable(entry);
            return new AssetFlatNode(expandable, entry, entryModel.label, level, entryModel);
         });
      }

      return [];
   }

   public isEntrySelectable(entry: AssetEntry): boolean {
      return entry.type === AssetType.LOGIC_MODEL || entry.type === AssetType.QUERY;
   }
}

@Component({
   selector: "em-burst-email-query-pane",
   templateUrl: "./burst-email-query-pane.component.html",
   styleUrls: ["./burst-email-query-pane.component.scss"]
})
export class BurstEmailQueryPaneComponent implements OnInit {
   @Input() queryData: BurstEmailDialogData;
   @Output() emailsChange = new EventEmitter<BurstEmailDialogData>();
   columns: string[] = [];
   columnLabels: string[] = [];
   query: string = null;
   user: string = null;
   email: string = null;
   dataSourcePath: string = "";

   treeControl: FlatTreeControl<AssetFlatNode>;
   treeDataSource: AssetDataSource;
   selectedEntry: AssetEntry;

   constructor(private http: HttpClient, private snackBar: MatSnackBar) {
      this.treeControl = new FlatTreeControl<AssetFlatNode>(this.getLevel, this.isExpandable);
      this.treeDataSource = new AssetDataSource(this.treeControl);
   }

   ngOnInit() {
      this.http.get<LabeledAssetEntries>(BURST_ASSET_TREE)
         .pipe(
            catchError(error => this.handleQueriesError(error)),
            map((entries) => ({ nodes: entries.entries })),
            map((model) => this.treeDataSource.transform(model, 0)),
            tap(nodes => this.treeDataSource.data = nodes)
         )
         .subscribe(() => this.initializeSelections());
   }

   private getLevel = (node: AssetFlatNode) => node.level;
   private isExpandable = (node: AssetFlatNode) => node.expandable;
   hasChild = (n: number, nodeData: AssetFlatNode) => nodeData.expandable;

   isSelectable(entry: AssetEntry): boolean {
      return this.treeDataSource.isEntrySelectable(entry);
   }

   selectedEntryChanged() {
      const query: string = this.selectedEntry ?
         this.selectedEntry.properties.prefix + "." + this.selectedEntry.properties.source :
         null;

      if(this.query != query) {
         this.query = query;
         this.user = null;
         this.email = null;
         this.resultChanged();
      }

      this.updateColumns();
   }

   resultChanged() {
      let result: string;
      let type: string = this.selectedEntry ? this.selectedEntry.properties.type : null;

      if(!this.query || !this.user) {
         result = "query: ";
      }
      else {
         result = "query: " + this.query + ", user: " + this.user
            + (!!this.email ? (", email: " + this.email) : "");
      }

      this.emailsChange.emit({emails: result, type:  type});
   }

   private initializeSelections(): void {
      if(!!this.queryData.emails) {
         const items: string[] = this.queryData.emails.split(",");

         if(items[0].substring(0, 7) === "query: ") {
            this.query = items[0].trim().substring(6).trim();
            this.dataSourcePath = this.query.replace(".", "/");
            this.user = !!items[1] ? items[1].trim().substring(5).trim() : null;
            this.email = !!items[2] ? items[2].trim().substring(6).trim() : null;

            let prefix: string;
            let source: string;
            const idx = this.query.indexOf(".");

            if(idx < 0) {
               source = this.query;
            }
            else {
               prefix = this.query.substring(0, idx);
               source = this.query.substring(idx + 1);
            }

            this.selectedEntry = this.findSelectedEntry(prefix, source);
            this.queryData.type = this.selectedEntry ? this.selectedEntry.properties.type : null;
            this.updateColumns();
         }
      }
   }

   private findSelectedEntry(prefix: string, source: string): AssetEntry {
      const search = (path: AssetFlatNode[]) => {
         const last = path[path.length - 1];
         const entry = last.data.entry;

         if(entry && entry.properties &&
            entry.properties.prefix === prefix &&
            entry.properties.source === source &&
            (entry.type === "QUERY" || entry.type === "LOGIC_MODEL"))
         {
            return entry;
         }

         if(last.data.children && last.data.children.length) {
            this.treeControl.toggle(last);
            const children = this.treeDataSource.data.filter(n => n.level === last.level + 1);

            for(let child of children) {
               path.push(child);
               const found = search(path);

               if(found) {
                  return found;
               }
            }

            this.treeControl.toggle(last);
         }

         return null;
      };

      for(let node of this.treeDataSource.data.slice()) {
         let entry = search([node]);

         if(entry) {
            return entry;
         }
      }

      return null;
   }

   private updateColumns(): void {
      if(this.selectedEntry) {
         this.http.post<QueryColumnsModel>(BURST_QUERY_COLUMNS, this.selectedEntry)
            .pipe(catchError(error => this.handleColumnsError(error)))
            .subscribe((model) => {
               this.columnLabels = model.columnLabels;
               this.columns = model.columns;
            });
      }
      else {
         this.columnLabels = [];
         this.columns = [];
      }
   }

   private handleQueriesError(error: HttpErrorResponse): Observable<LabeledAssetEntries> {
      this.snackBar.open("_#(js:em.schedule.burst.listQueriesError))", null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list queries: ", error);
      return throwError(error);
   }

   private handleColumnsError(error: HttpErrorResponse): Observable<QueryColumnsModel> {
      this.snackBar.open("_#(js:em.schedule.burst.listColumnsError))", null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list query columns: ", error);
      return throwError(error);
   }
}
