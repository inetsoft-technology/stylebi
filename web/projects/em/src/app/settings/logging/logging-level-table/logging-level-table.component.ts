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
   @Output() loggingLevelsChange = new EventEmitter<LogLevelDTO[]>();
   tableInfo: TableInfo = {
      title: "_#(js:Custom Log Levels)",
      selectionEnabled: true,
      columns: [
         {header: "_#(js:Type)", field: "context"},
         {header: "_#(js:Name)", field: "name"},
         {header: "_#(js:Level)", field: "level"}
      ],
      actions: [TableAction.EDIT, TableAction.ADD, TableAction.DELETE]
   };

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
