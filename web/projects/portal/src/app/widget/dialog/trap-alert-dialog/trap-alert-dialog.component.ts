/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, ViewChild, TemplateRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

@Component({
   selector: "trap-alert-dialog",
   templateUrl: "trap-alert-dialog.component.html",
})
export class TrapAlert {
   undoable: boolean = true;
   message: string = null;
   @ViewChild("dialog") dialog: TemplateRef<any>;

   constructor(private modalService: NgbModal) {
   }

   private getMessage(msg: string): string {
      return this.undoable ? "designer.binding.trapFound" :
         "designer.binding.continueTrap" +
         (msg == null ? "" : "\n" + msg);
   }

   showTrapAlert(undoable: boolean = true, msg: string = null): Promise<any> {
      this.undoable = undoable;
      this.message = this.getMessage(msg);

      return this.modalService.open(this.dialog, {backdrop: "static"}).result;
   }
}
