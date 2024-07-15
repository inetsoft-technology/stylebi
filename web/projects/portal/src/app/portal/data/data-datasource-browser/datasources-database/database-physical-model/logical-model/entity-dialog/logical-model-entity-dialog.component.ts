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
import { Component, OnInit, Input, Output, EventEmitter } from "@angular/core";
import { UntypedFormGroup, AbstractControl } from "@angular/forms";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";

@Component({
   selector: "logical-model-entity-dialog",
   templateUrl: "logical-model-entity-dialog.component.html",
   styleUrls: ["logical-model-entity-dialog.component.scss"]
})
export class LogicalModelEntityDialog implements OnInit {
   @Input() entity: EntityModel;
   @Input() existNames: string[] = [];
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   private originalName: string;
   private originalDescription: string;
   form: UntypedFormGroup = new UntypedFormGroup({});

   ngOnInit() {
      if(!this.entity) {
         this.entity = {
            attributes: [],
            name: null,
            oldName: null,
            description: "",
            type: "entity",
            errorMessage: "",
            leaf: false,
            baseElement: false,
            elementType: "entityElement",
            visible: true
         };
      }

      this.originalName = this.entity.name;
      this.originalDescription = this.entity.description;
   }

   /**
    * Move on to attribute dialog.
    */
   next(): void {
      this.entity.name = this.nameControl.value;
      this.entity.description = this.descriptionControl.value;
      this.onCommit.emit({entity: this.entity, next: true});
   }

   /**
    * Submit entity modifications.
    */
   ok(): void {
      if(this.originalName && this.originalName === this.entity.name &&
         this.originalDescription === this.entity.description)
      {
         this.cancel();
      }
      else {
         this.entity.name = this.nameControl.value;
         this.entity.description = this.descriptionControl.value;
         this.onCommit.emit({entity: this.entity, next: false});
      }
   }

   /**
    * Cancel changes.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }

   /**
    * Get the name form control
    * @returns {AbstractControl|null} the form control
    */
   get nameControl(): AbstractControl {
      return this.form.get("name");
   }

   get descriptionControl(): AbstractControl {
      return this.form.get("description");
   }
}
