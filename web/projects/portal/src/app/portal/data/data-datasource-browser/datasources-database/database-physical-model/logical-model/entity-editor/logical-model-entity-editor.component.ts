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
import { Component, Input, EventEmitter, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { Tool } from "../../../../../../../../../../shared/util/tool";

@Component({
   selector: "logical-model-entity-editor",
   templateUrl: "logical-model-entity-editor.component.html",
   styleUrls: ["../../database-model-pane.scss", "logical-model-entity-editor.component.scss"]
})
export class LogicalModelEntityEditor {
   @Input() set entity(value: EntityModel) {
      this._entity = value;
   }

   @Input() form: UntypedFormGroup = new UntypedFormGroup({});
   @Input() existNames: string[] = [];
   @Input() logicalModelParent: string;
   _entity: EntityModel;

   /**
    * Apply changes.
    */
   apply(): void {
      if(this.form.get("name") && this.form.get("name").valid) {
         this._entity.name =  (<string> this.form.get("name").value).trim();
      }

      if(this.form.get("description") && this.form.get("description").valid) {
         this._entity.description = this.form.get("description").value;
      }
   }
}