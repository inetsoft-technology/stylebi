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
import { Component, Input, Output, EventEmitter } from "@angular/core";

import { ConcatenationTypeDialogModel } from "../../data/ws/concatenation-type-dialog-model";
import { XConstants } from "../../../common/util/xconstants";

const SOCKET_URI: string = "/events/composer/worksheet/concatenation-type-dialog";

@Component({
   selector: "concatenation-type-dialog",
   templateUrl: "concatenation-type-dialog.component.html"
})
export class ConcatenationTypeDialog {
   @Input() model: ConcatenationTypeDialogModel;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   concatenationTypes = {
      union: XConstants.UNION,
      intersect: XConstants.INTERSECT,
      minus: XConstants.MINUS
   };

   ok(): void {
      this.onCommit.emit({controller: SOCKET_URI, model: this.model});
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   updateOperation(value: string) {
      this.model.operator.operation = +value;

      if(this.keepDisabled()) {
         this.model.operator.distinct = true;
      }
   }

   keepDisabled(): boolean {
      return this.model.operator.operation !== this.concatenationTypes.union;
   }
}
