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
import { Component, ElementRef } from "@angular/core";

@Component({
   selector: "app-error-message",
   templateUrl: "app-error-message.component.html"
})

export class AppErrorMessage {
   errorTitle: string;
   errorMsg: string;
   private elementRef: ElementRef;
   public errorMessageIsVisible: boolean;

   showErrorMessage(title: string, msg: string) {
      this.errorTitle = title;
      this.errorMsg = msg;
      this.errorMessageIsVisible = true;
   }

   showErrorMessageFocus(title: string, msg: string, eRef: ElementRef) {
      this.elementRef = eRef;
      this.showErrorMessage(title, msg);
   }

   hideErrorMsg() {
      this.errorMessageIsVisible = false;
      if(this.elementRef != undefined) {
         this.elementRef.nativeElement.focus();
      }
   }
}
