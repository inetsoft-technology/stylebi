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
import { HttpErrorResponse } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Observable, throwError } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../message-dialog";

@Injectable()
export class ErrorHandlerService {
   constructor(private snackBar: MatSnackBar, private dialog: MatDialog) {
   }

   showSnackBar(error: HttpErrorResponse, defaultMessage: string = null, resultProducer: () => Observable<any> = null) {
      const type = this.getType(error);
      const message = this.getMessage(error, defaultMessage);

      if(message) {
         this.snackBar.open(type + ": " + message, "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
      }

      if(resultProducer) {
         return resultProducer();
      }

      return throwError(error);
   }

   showDialog(error: HttpErrorResponse, defaultMessage: string = null) {
      const message = this.getMessage(error, defaultMessage);

      if(!!message) {
         this.dialog.open(MessageDialog, <MatDialogConfig>{
            width: "350px",
            data: {
               title: "_#(js:Error)",
               content: message,
               type: MessageDialogType.ERROR
            }
         });
      }

      return throwError(error);
   }

   showErrorDialog(message: string) {
      this.dialog.open(MessageDialog, <MatDialogConfig>{
         width: "350px",
         data: {
            title: "_#(js:Error)",
            content: message,
            type: MessageDialogType.ERROR
         }
      });
   }

   getType(error: HttpErrorResponse) {
      return error && error.error.type && error.error.type !== "MessageException" ?
         error.error.type : "_#(js:Error)";
   }

   getMessage(error: HttpErrorResponse, defaultMessage: string): string {
      if(!!error) {
         if(error.status === 502 || error.status === 503) {
            return "_#(js:login.error.gateway)";
         }
         else if(!!error?.error?.message) {
            return error.error.message;
         }
      }

      return defaultMessage;
   }
}
