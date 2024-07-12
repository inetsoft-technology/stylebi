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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { PerformanceSettingsModel } from "./performance-settings-model";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { GeneralSettingsChanges } from "../general-settings-page/general-settings-page.component";
import { Searchable } from "../../../searchable";
import { GeneralSettingsType } from "../general-settings-page/general-settings-type.enum";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/general#performance",
   title: "Performance",
   keywords: ["em.settings", "em.settings.general", "em.settings.performance"]
})
@ContextHelp({
   route: "/settings/general#performance",
   link: "EMGeneralPerformance"
})
@Component({
   selector: "em-performance-settings-view",
   templateUrl: "./performance-settings-view.component.html",
   styleUrls: ["./performance-settings-view.component.scss"]
})
export class PerformanceSettingsViewComponent {
   @Output() modelChanged = new EventEmitter<GeneralSettingsChanges>();

   private _model: PerformanceSettingsModel;
   form: UntypedFormGroup;

   @Input() set model(model: PerformanceSettingsModel) {
      this._model = model;

      if(this.model) {
         this.initForm();
      }
   }

   get model(): PerformanceSettingsModel {
      return this._model;
   }

   constructor(private formBuilder: UntypedFormBuilder) {
   }

   initForm() {
      this.form = this.formBuilder.group({
         queryTimeout: [this.model.queryTimeout, Validators.min(0)],
         maxQueryRowCount: [this.model.maxQueryRowCount, FormValidators.positiveIntegerInRange],
         queryPreviewTimeout: [this.model.queryPreviewTimeout, Validators.min(0)],
         maxQueryPreviewRowCount: [this.model.maxQueryPreviewRowCount, Validators.min(0)],
         maxTableRowCount: [this.model.maxTableRowCount, FormValidators.positiveIntegerInRange],
         dataSetCaching: [this.model.dataSetCaching, Validators.min(0)],
         dataCacheSize: [this.model.dataCacheSize, Validators.min(0)],
         dataCacheTimeout: [this.model.dataCacheTimeout, Validators.min(0)],
      });

      this.form.controls["queryTimeout"].valueChanges.subscribe((value) => {
         this.model.queryTimeout = value;
         this.onModelChanged();
      });

      this.form.controls["maxQueryRowCount"].valueChanges.subscribe((value) => {
         this.model.maxQueryRowCount = value;
         this.onModelChanged();
      });

      this.form.controls["maxTableRowCount"].valueChanges.subscribe((value) => {
         this.model.maxTableRowCount = value;
         this.onModelChanged();
      });

      this.form.controls["queryPreviewTimeout"].valueChanges.subscribe((value) => {
         this.model.queryPreviewTimeout = value;
         this.onModelChanged();
      });

      this.form.controls["maxQueryPreviewRowCount"].valueChanges.subscribe((value) => {
         this.model.maxQueryPreviewRowCount = value;
         this.onModelChanged();
      });

      this.form.controls["dataSetCaching"].valueChanges.subscribe((value) => {
         this.model.dataSetCaching = value;
         this.onModelChanged();
      });

      this.form.controls["dataCacheSize"].valueChanges.subscribe((value) => {
         this.model.dataCacheSize = value;
         this.onModelChanged();
      });

      this.form.controls["dataCacheTimeout"].valueChanges.subscribe((value) => {
         this.model.dataCacheTimeout = value;
         this.onModelChanged();
      });
   }

   onModelChanged() {
      this.form.updateValueAndValidity();
      this.modelChanged.emit({
         model: this.model,
         modelType: GeneralSettingsType.PERFORMANCE_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }
}
