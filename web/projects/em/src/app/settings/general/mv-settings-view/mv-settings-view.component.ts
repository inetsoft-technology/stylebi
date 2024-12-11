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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import {
   FormGroupDirective,
   NgForm,
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   Validators
} from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { ContextHelp } from "../../../context-help";
import { Searchable } from "../../../searchable";
import { GeneralSettingsChanges } from "../general-settings-page/general-settings-page.component";
import { GeneralSettingsType } from "../general-settings-page/general-settings-type.enum";
import { MVSettingsModel, MVType } from "./mv-settings-model";

@Searchable({
   route: "/settings/general#mv",
   title: "Materialized View",
   keywords: [
      "em.settings", "em.settings.general", "em.settings.materialized",
      "em.settings.view"
   ]
})
@ContextHelp({
   route: "/settings/general#mv",
   link: "EMGeneralMaterializedViewOptions"
})
@Component({
   selector: "em-mv-settings-view",
   templateUrl: "./mv-settings-view.component.html",
   styleUrls: ["./mv-settings-view.component.scss"]
})
export class MVSettingsViewComponent {
   @Output() modelChanged = new EventEmitter<GeneralSettingsChanges>();
   MVType = MVType;
   private _model: MVSettingsModel;
   form: UntypedFormGroup;
   groupErrorMatcher: ErrorStateMatcher;

   @Input() set model(model: MVSettingsModel) {
      this._model = model;

      if(this.model) {
         this.initForm();
      }
   }

   get model(): MVSettingsModel {
      return this._model;
   }

   constructor(private formBuilder: UntypedFormBuilder, private http: HttpClient,
               private snackBar: MatSnackBar, private defaultErrorMatcher: ErrorStateMatcher)
   {
   }

   initForm() {
      this.form = this.formBuilder.group({
         onDemand: [this.model.onDemand],
         onDemandDefault: [this.model.onDemandDefault],
         defaultCycle: [this.model.defaultCycle],
         metadata: [this.model.metadata],
         required: [this.model.required],
      });

      this.groupErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            control.invalid
      };

      if(this.model.onDemand) {
         this.form.controls["onDemandDefault"].enable();
      }
      else {
         this.form.controls["onDemandDefault"].disable();
      }

      this.form.controls["onDemand"].valueChanges.subscribe((value) => {
         this.model.onDemand = value;

         if(value) {
            this.form.controls["onDemandDefault"].enable();
         }
         else {
            this.form.controls["onDemandDefault"].disable();
         }

         this.onModelChanged();
      });

      this.form.controls["onDemandDefault"].valueChanges.subscribe((value) => {
         this.model.onDemandDefault = value;
         this.onModelChanged();
      });

      this.form.controls["defaultCycle"].valueChanges.subscribe((value) => {
         this.model.defaultCycle = value;
         this.onModelChanged();
      });

      this.form.controls["metadata"].valueChanges.subscribe((value) => {
         this.model.metadata = value;
         this.onModelChanged();
      });

      this.form.controls["required"].valueChanges.subscribe((value) => {
         this.model.required = value;
         this.onModelChanged();
      });
   }

   onModelChanged() {
      this.form.updateValueAndValidity();
      this.modelChanged.emit({
         model: this.model,
         modelType: GeneralSettingsType.MV_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }
}
