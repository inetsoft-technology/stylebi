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
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { Component, Inject } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";

export enum MessageDialogType {
   CONFIRMATION, DELETE, DEPENDENCY, ERROR, INFO, REPLACE, WARNING
}

@Component({
   selector: "em-message-dialog",
   templateUrl: "message-dialog.html"
})
export class MessageDialog {
   // IMPORT MatDialogModule AND INCLUDE MessageDialog IN ENTRY COMPONENTS FOR MODULES USING THIS
   // TO USE THIS, instantiate in component constructor (ie: constructor(public dialog: MatDialog) <-- you will have to import this module)
   // insert this snippet where you want a dialog:
   // this.dialog.open(MessageDialog, {
   //    width: '350px',
   //    data: {
   //       title: TITLE_HERE, content: CONTENT_HERE, type: TYPE_HERE
   //    }
   // });
   // They're all strings. HTMl currently supports
   // data.type === 'Info', 'Delete', 'Error', 'Dependency', 'Confirmation', 'Replace'

   MessageDialogType = MessageDialogType;

   constructor(public dialogRef: MatDialogRef<MessageDialog>,
               @Inject(MAT_DIALOG_DATA) public data: any) {


   }
}
