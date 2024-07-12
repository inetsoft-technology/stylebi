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
import { HttpClient } from "@angular/common/http";
import { Component, Inject } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { PropertyModel } from "../property-table-view/property-model";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { DataSpaceTreeNode } from "../../content/data-space/data-space-tree-node";

@Component({
  selector: "em-organization-property-dialog",
  templateUrl: "./organization-property-dialog.component.html",
  styleUrls: ["./organization-property-dialog.component.scss"]
})
export class OrganizationPropertyDialogComponent {
   propertyName: string;
   propertyValue: string;
   form: UntypedFormGroup;

   constructor( private http: HttpClient, fb: UntypedFormBuilder,
               private dialogRef: MatDialogRef<OrganizationPropertyDialogComponent>,
               @Inject(MAT_DIALOG_DATA) public model: PropertyModel)
   {
      this.form = fb.group({
         propertyName: [null, [Validators.required]],
         propertyValue: [null, [Validators.required]]
      });

      if(model != null) {
         this.propertyName = model.name;
         this.form.get("propertyName").setValue(model.name);
         this.form.get("propertyValue").setValue(model.value);
         this.propertyValueChange();
      }
   }

   addProperty() {
      let data: PropertyModel = {name: this.propertyName, value: this.propertyValue};
      this.dialogRef.close(data);
   }

   propertyChange(): void {
      if(this.propertyName == "max.col.count") {
         this.form.get("propertyValue").setValue(200);
      }
      else if(this.propertyName == "max.cell.size") {
         this.form.get("propertyValue").setValue(500);
      }
      else {
         this.form.get("propertyValue").setValue("");
      }

      this.propertyValueChange();
   }

   propertyValueChange(): void {
      this.propertyValue = this.form.get("propertyValue").value;
   }

   addEnabled(): boolean {
      return this.form.valid;
   }

   cancel() {
      this.dialogRef.close(null);
   }
}
