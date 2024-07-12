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
import { Component, Input} from "@angular/core";
import { UntypedFormGroup, AbstractControl } from "@angular/forms";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";

@Component({
   selector: "logical-model-column-editor",
   templateUrl: "logical-model-column-editor.component.html",
   styleUrls: ["../../database-model-pane.scss", "logical-model-column-editor.component.scss"]
})
export class LogicalModelColumnEditor {
   @Input() databaseName: string;
   @Input() physicalModelName: string;
   @Input() additional: string;
   @Input() logicalModelName: string;
   @Input() logicalModelParent: string;
   @Input() existNames: string[] = [];
   @Input() entities: EntityModel;
   @Input() form: UntypedFormGroup;
   _attribute: AttributeModel;

   @Input() set attribute(value: AttributeModel) {
      this._attribute = value;
   }

   /**
    * Get the current attribute being edited.
    * @returns {AttributeModel}
    */
   get attribute(): AttributeModel {
      return this._attribute;
   }

   /**
    * Get the name form control
    * @returns {AbstractControl|null} the form control
    */
   get nameControl(): AbstractControl {
      return this.form.get("name");
   }

   /**
    * Apply the form control value to model.
    * @param formControlName
    * @param fieldName
    */
   applyFromValue(formControlName: string, fieldName: string) {
      if(this.form && this.form.get(formControlName) && this.form.get(formControlName).valid &&
         this.attribute)
      {
         let value = this.form.get(formControlName).value;
         this.attribute[fieldName] = "name" === formControlName && !!value ? value.trim() : value;
      }
   }

   /**
    * Apply changes.
    */
   apply(): void {
      this.applyFromValue("name", "name");
      this.applyFromValue("refType", "refType");
      this.applyFromValue("dataType", "dataType");
   }
}
