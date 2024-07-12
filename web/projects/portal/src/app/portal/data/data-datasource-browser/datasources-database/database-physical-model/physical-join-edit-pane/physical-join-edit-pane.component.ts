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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   AfterViewChecked,
   AfterViewInit,
   Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges,
   ViewChild
} from "@angular/core";
import { Subscription } from "rxjs";
import { TableJoinInfo } from "../../../../model/datasources/database/physical-model/graph/table-join-info";
import { JoinEditPaneModel } from "../../../../model/datasources/database/physical-model/graph/join-edit-pane-model";
import { GraphColumnInfo } from "../../../../model/datasources/database/physical-model/graph/graph-column-info";
import { JoinModel } from "../../../../model/datasources/database/physical-model/join-model";
import { JoinThumbnailService } from "../../common-components/join-thumbnail.service";

const HEARTBEAT_URI = "../api/data/physicalmodel/heartbeat";

@Component({
   selector: "physical-join-edit-pane",
   templateUrl: "physical-join-edit-pane.component.html",
   styleUrls: ["physical-join-edit-pane.component.scss",
      "../../../../../../composer/gui/ws/jsplumb/jsplumb-shared.scss"],
   providers: [ JoinThumbnailService ]
})
export class PhysicalJoinEditPane implements
   OnChanges, OnInit, AfterViewInit, AfterViewChecked, OnDestroy
{
   @Input() model: JoinEditPaneModel;
   @Output() onRefreshPhysicalGraph = new EventEmitter<TableJoinInfo>();
   @ViewChild("jspContainerMain") jspContainerMain: ElementRef<HTMLDivElement>;

   private populated: boolean = false;
   private subscription: Subscription;

   constructor(private thumbnailService: JoinThumbnailService,
               private readonly http: HttpClient)
   {
      this.subscription = this.thumbnailService.refreshGraph
         .subscribe((info: TableJoinInfo) => {
            this.onRefreshPhysicalGraph.emit(info);
      });
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("model")) {
         this.thumbnailService.setJoinEditPaneModel(this.model);
         this.reorderColumns();
         this.populated = true;
      }
   }

   ngOnInit(): void {
      this.subscription.add(this.thumbnailService.heartbeat.subscribe(() => {
         this.sendHeartBeat(this.model.runtimeID);
      }));
   }

   ngAfterViewInit(): void {
      this.thumbnailService.setContainer(this.jspContainerMain.nativeElement);
   }

   ngAfterViewChecked(): void {
      if(this.jsp.isSuspendDrawing()) {
         this.jsp.makeSource("edit-join-table-column");
         this.jsp.setSuspendDrawing(false, true);
      }

      if(this.populated) {
         this.populated = false;
         // Wait a tick for elements to get created, then connect them.
         setTimeout(() => {
            this.thumbnailService.clear();
            this.thumbnailService.initJoinConnections();
         }, 0);
      }
   }

   ngOnDestroy(): void {
      this.jsp.deleteEveryConnection({fireEvent: false});
      this.thumbnailService.cleanup();

      if(!!this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }

   private sendHeartBeat(runtimeID: string): void {
      if(!!runtimeID) {
         const params: HttpParams = new HttpParams()
            .set("id", runtimeID);

         this.http.get(HEARTBEAT_URI, {params}).subscribe(() => {});
      }
   }

   get jsp(): JSPlumb.JSPlumbInstance {
      return this.thumbnailService.jsPlumbInstance;
   }

   private reorderColumns(): void {
      const leftTable = this.model.tables[0];
      const rightTable = this.model.tables[1];
      let leftJoinIndex = 0;
      let rightJoinIndex = 0;

      const processedColumns = new Set<string>();

      leftTable.joins.forEach((join: JoinModel) => {
         if(join.foreignTable === rightTable.name) {
            const leftColumn = join.column;
            const rightColumn = join.foreignColumn;
            const leftKey = leftTable.name + leftColumn;
            const rightKey = rightTable.name + rightColumn;

            const leftIndex = !processedColumns.has(leftKey)
               ? this.findColumnIndex(leftTable.columns, leftColumn)
               : -1;
            const rightIndex = !processedColumns.has(rightKey)
               ? this.findColumnIndex(rightTable.columns, rightColumn)
               : -1;

            if(leftIndex >= 0) {
               this.movePosition(leftTable.columns, leftIndex, leftJoinIndex++);
               processedColumns.add(leftKey);
            }

            if(rightIndex >= 0) {
               this.movePosition(rightTable.columns, rightIndex, rightJoinIndex++);
               processedColumns.add(rightKey);
            }
         }
      });
   }

   private findColumnIndex(columns: GraphColumnInfo[], findName: string): number {
      let result = -1;

      columns.find((col, index) => {
         if(col.name === findName) {
            result = index;

            return true;
         }

         return false;
      });

      return result;
   }

   private movePosition(columns: GraphColumnInfo[], from: number, to: number): void {
      columns[to] = columns.splice(from, 1, columns[to])[0];
   }
}