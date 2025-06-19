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
import { Component, ElementRef, Input, OnDestroy, OnInit } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Rectangle } from "../../../../../../common/data/rectangle";
import { GuiTool } from "../../../../../../common/util/gui-tool";
import { Subscription } from "rxjs";
import { DataQueryModelService } from "../data-query-model.service";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

const LOAD_QUERY_DATA_URL = "../api/data/datasource/query/load/data";

@Component({
   selector: "sql-query-preview-pane",
   templateUrl: "./sql-query-preview-pane.component.html"
})
export class SqlQueryPreviewPaneComponent implements OnDestroy, OnInit {
   @Input() runtimeId: string;
   @Input() sqlString: string;
   @Input() tableCount: number;
   @Input() sqlEdited: boolean;
   previewPending: boolean = false;
   tableData: string[][];
   scrollbarWidth: number;
   private previewSub: Subscription;
   constructor(private http: HttpClient,
               private modalService: NgbModal,
               private queryService: DataQueryModelService,
               private elementRef: ElementRef)
   {
   }

   ngOnInit() {
      this.loadQueryData();
   }

   ngOnDestroy() {
      if(this.previewSub && !this.previewSub.closed) {
         this.previewSub.unsubscribe();
      }
   }

   loadQueryData(): void {
      // sql has no sql string
      if(!this.sqlString && (!this.tableCount || this.tableCount == 0)) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               "_#(js:designer.qb.jdbc.selectOneTable)");
         return;
      }

      this.previewPending = true;

      this.queryService.getVariables(this.runtimeId, null, () => {
         let params = new HttpParams().set("runtimeId", this.runtimeId);

         if(this.sqlEdited) {
            params = params.set("sqlString", this.sqlString);
         }

         this.previewSub = this.http.get<string[][]>(LOAD_QUERY_DATA_URL, { params: params })
            .subscribe(
               (res) => {
                  this.previewPending = false;
                  this.tableData = res;
                  this.scrollbarWidth = GuiTool.measureScrollbars();
               },
               (error) => {
                  this.previewPending = false;
                  let errorMsg: string = error.error;

                  if(errorMsg.startsWith("java.sql.SQLSyntaxErrorException:")) {
                     errorMsg = "_#(js:common.sqlquery.syntaxError)\n" + errorMsg;
                  }

                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", errorMsg);
               }
            );
      },
      () => {
         this.previewPending = false;
      });
   }

   getPreviewContainerMaxWidth(): string {
      return "calc(100% - " + this.scrollbarWidth + "px)";
   }

   getContainerSize(): Rectangle {
      return new Rectangle(0, 0,
         this.elementRef.nativeElement.offsetWidth, this.elementRef.nativeElement.offsetHeight);
   }
}
