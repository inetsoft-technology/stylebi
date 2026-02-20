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
import { Component, EventEmitter, Input } from "@angular/core";
import { Output } from "@angular/core";
import { TableInfo } from "../../../common/util/table/table-info";
import { TableAction } from "../../../common/util/table/table-view.component";
import { MatDialog } from "@angular/material/dialog";
import { LogLevelDTO } from "../LogLevelDTO";
import { AddLoggingLevelDialogComponent } from "../add-logging-level-dialog/add-logging-level-dialog.component";

@Component({
   selector: "em-logging-level-table",
   templateUrl: "./logging-level-table.component.html",
   styleUrls: ["./logging-level-table.component.scss"]
})
export class LoggingLevelTableComponent {
   @Input() loggingLevels: LogLevelDTO[] = [];
   @Input() enterprise: boolean;
   @Input() isMultiTenant: boolean;
   @Input() organizations: string[] = [];
   @Output() loggingLevelsChange = new EventEmitter<LogLevelDTO[]>();

   get tableInfo(): TableInfo {
      return {
         title: "_#(js:Custom Log Levels)",
         selectionEnabled: true,
         columns: this.getTableColumns(),
         actions: [TableAction.EDIT, TableAction.ADD, TableAction.DELETE]
      };
   }
   private getTableColumns() {
      if(!this.enterprise || !this.isMultiTenant) {
         return [
            {header: "_#(js:Type)", field: "context", render: (v: string) => this.getContextLabel(v)},
            {header: "_#(js:Name)", field: "name"},
            {header: "_#(js:Level)", field: "level", render: (v: string) => this.getLevelLabel(v)}
         ];
      }
      else {
         return [
            {header: "_#(js:Type)", field: "context", render: (v: string) => this.getContextLabel(v)},
            {header: "_#(js:Name)", field: "name"},
            {header: "_#(js:Organization)", field: "orgName"},
            {header: "_#(js:Level)", field: "level", render: (v: string) => this.getLevelLabel(v)}
         ];
      }
   }

   getContextLabel(value: string): string {
      switch(value) {
         case "DASHBOARD": return "_#(js:Viewsheet)";
         case "QUERY": return "_#(js:Query)";
         case "MODEL": return "_#(js:Model)";
         case "WORKSHEET": return "_#(js:Worksheet)";
         case "USER": return "_#(js:User)";
         case "GROUP": return "_#(js:Group)";
         case "ROLE": return "_#(js:Role)";
         case "SCHEDULE_TASK": return "_#(js:Schedule Task)";
         case "CATEGORY": return "_#(js:Log Category)";
         case "ORGANIZATION": return "_#(js:Organization)";
         default: return value;
      }
   }

   getLevelLabel(value: string): string {
      switch(value) {
         case "debug": return "_#(js:Debug)";
         case "info": return "_#(js:Info)";
         case "warn": return "_#(js:Warning)";
         case "error": return "_#(js:Error)";
         default: return value;
      }
   }
   constructor(private dialog: MatDialog) {
   }

   public openLoggingLevelDialog(index: number = -1): void {
      let dialogRef = this.dialog.open(AddLoggingLevelDialogComponent, {
         role: "dialog",
         width: "70vw",
         height: "75vh",
         data: {
            index: index,
            loggingLevels: this.loggingLevels,
            enterprise: this.enterprise,
            isMultiTenant: this.isMultiTenant,
            organizations: this.organizations
         }
      });

      dialogRef.afterClosed().subscribe((result: LogLevelDTO[]) => {
         if(result) {
            result.forEach((model, idx) => {
               if(this.loggingLevels[idx]) {
                  this.loggingLevels[idx] = model;
               }
               else {
                  this.loggingLevels.push(model);
               }
            });

            this.loggingLevelsChange.emit(this.loggingLevels);
         }
      });
   }

   addLevel(): void {
      this.openLoggingLevelDialog();
   }

   editLevel(loggingLevels: LogLevelDTO): void {
      const index = this.loggingLevels.indexOf(loggingLevels);
      this.openLoggingLevelDialog(index);
   }

   removeLevels(loggingLevels: LogLevelDTO[]) {
      this.loggingLevels = this.loggingLevels.filter(
         (loggingLevel) => !loggingLevels.some((level) =>
            level.name === loggingLevel.name &&
            level.context === loggingLevel.context));
      this.loggingLevelsChange.emit(this.loggingLevels);
   }
}
