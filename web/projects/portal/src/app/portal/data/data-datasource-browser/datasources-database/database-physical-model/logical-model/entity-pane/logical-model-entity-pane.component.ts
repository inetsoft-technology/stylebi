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
import {
   AfterViewInit,
   Component, ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   Output, ViewChild
} from "@angular/core";
import { UntypedFormGroup, Validators, UntypedFormControl, AbstractControl, ValidatorFn } from "@angular/forms";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { FormValidators } from "../../../../../../../../../../shared/util/form-validators";
import { Subscription } from "rxjs";

@Component({
   selector: "logical-model-entity-pane",
   templateUrl: "logical-model-entity-pane.component.html",
   styleUrls: ["logical-model-entity-pane.component.scss"]
})
export class LogicalModelEntityPane implements AfterViewInit, OnChanges, OnDestroy {
   @Input() existNames: string[] = [];
   @Input() logicalModelParent: string;
   @Output() onEntityChanged: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild("nameInput") nameInput: ElementRef;
   private _form: UntypedFormGroup;
   private _entiry: EntityModel;
   private inited: boolean = false;
   private editable: boolean = true;
   private subscription: Subscription;

   @Input() set form(value: UntypedFormGroup) {
      this._form = value;

      if(this.inited) {
         setTimeout(() => this.resetFormControl(), 0);
      }
   }

   get form(): UntypedFormGroup {
      return this._form;
   }

   @Input() set entity(entity: EntityModel) {
      this._entiry = entity;

      if(entity) {
         this.editable = !entity.baseElement;
      }
   }

   get entity(): EntityModel {
      return this._entiry;
   }

   ngAfterViewInit() {
      this.nameInput.nativeElement.focus();
   }

   /**
    * Get the name form control
    * @returns {AbstractControl|null} the form control
    */
   get nameControl(): AbstractControl {
      return this.form.get("name");
   }

   /**
    * Initialize the form controls.
    */
   private resetFormControl() {
      this.unsubscribeForm();
      this.form.removeControl("name");
      this.form.removeControl("description");
      this.form.addControl("name",
         new UntypedFormControl(this.entity.name, [
            Validators.required, FormValidators.exists(this.existNames)
         ]));
      this.form.addControl("description", new UntypedFormControl(this.entity.description));

      this.subscription = this.form.valueChanges.subscribe(() => {
         this.onEntityChanged.emit();
      });

      if(!this.editable) {
         this.form.disable();
      }
      else {
         this.form.enable();
      }
   }

   ngOnChanges(): void {
      this.inited = true;
      this.resetFormControl();
   }

   ngOnDestroy(): void {
      this.unsubscribeForm();
   }

   /**
    * Unsubscribe form value change.
    */
   private unsubscribeForm(): void {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }
}