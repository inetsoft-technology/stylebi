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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { WebMapSettingsModel } from "./webmap-settings-model";
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormControl } from "@angular/forms";
import { Searchable } from "../../../searchable";
import { ContextHelp } from "../../../context-help";
import { HttpClient, HttpParams } from "@angular/common/http";
import { MapboxStyle } from "../../../../../../portal/src/app/graph/model/dialog/mapbox-style";
import { Tool } from "../../../../../../shared/util/tool";

@Searchable({
   route: "/settings/presentation/settings#webmap",
   title: "Web Map Configuration",
   keywords: [
      "em.settings", "em.settings.general", "em.settings.webmap",
      "em.settings.configuration"
   ]
})
@ContextHelp({
   route: "/settings/presentation/settings#webmap",
   link: "EMPresentationDashboardSettingsWebMap"
})
@Component({
   selector: "em-webmap-settings-view",
   templateUrl: "./webmap-settings-view.component.html",
   styleUrls: ["./webmap-settings-view.component.scss"]
})
export class WebMapSettingsViewComponent {
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();
   private _model: WebMapSettingsModel;
   form: UntypedFormGroup;
   stylesError: string;

   @Input() set model(model: WebMapSettingsModel) {
      this._model = model;

      if(this.model) {
         this.initForm();
      }
   }

   get model(): WebMapSettingsModel {
      return this._model;
   }

   constructor(private formBuilder: UntypedFormBuilder,
               private http: HttpClient)
   {
   }

   initForm() {
      this.form = this.formBuilder.group({
         service: [this.model.service],
         defaultOn: [this.model.defaultOn],
         mapboxUser: [{value: this.model.mapboxUser, disabled: !this.isMapbox()}, [Validators.required]],
         mapboxToken: [{value: this.model.mapboxToken, disabled: !this.isMapbox()}, [Validators.required]],
         mapboxStyle: [{value: this.model.mapboxStyle, disabled: !this.isMapbox()}, [Validators.required]],
         googleKey: [{value: this.model.googleKey, disabled: !this.isGoogleMaps()}, [Validators.required]],
      });

      this.form.controls["service"].valueChanges.subscribe((value) => {
         this.model.service = value;

         if(this.isMapbox()) {
            this.form.controls["mapboxUser"].enable();
            this.form.controls["mapboxToken"].enable();
            this.form.controls["mapboxStyle"].enable();
            this.form.controls["googleKey"].disable();
         }
         else if(this.isGoogleMaps()) {
            this.form.controls["mapboxUser"].disable();
            this.form.controls["mapboxToken"].disable();
            this.form.controls["mapboxStyle"].disable();
            this.form.controls["googleKey"].enable();
         }

         this.onModelChanged();
      });

      this.form.controls["defaultOn"].valueChanges.subscribe((value) => {
         this.model.defaultOn = value;
         this.onModelChanged();
      });

      this.form.controls["mapboxUser"].valueChanges.subscribe((value) => {
         this.model.mapboxUser = value;

         if(!Tool.isEmpty(value) && !Tool.isEmpty(this.model.mapboxToken)) {
            this.loadMapStyles();
         }
         else {
            this.model.mapboxStyles = [];
         }

         this.onModelChanged();
      });

      this.form.controls["mapboxToken"].valueChanges.subscribe((value) => {
         this.model.mapboxToken = value;

         if(!Tool.isEmpty(value) && !Tool.isEmpty(this.model.mapboxUser)) {
            this.loadMapStyles();
         }
         else {
            this.model.mapboxStyles = [];
         }

         this.onModelChanged();
      });

      this.form.controls["mapboxStyle"].valueChanges.subscribe((value) => {
         this.model.mapboxStyle = value;
         this.onModelChanged();
      });

      this.form.controls["googleKey"].valueChanges.subscribe((value) => {
         this.model.googleKey = value;
         this.onModelChanged();
      });
   }

   private loadMapStyles() {
      this.http.get<MapboxStyle[]>("../api/em/presentation/settings/mapstyles/" +
         this.model.mapboxUser + "/" + this.model.mapboxToken, {}).subscribe(
            (styles: MapboxStyle[]) => {
               this.model.mapboxStyles = styles;
               this.stylesError = "";
            },
            (error) => this.stylesError = error?.error?.message
         );
   }

   onModelChanged() {
      this.form.updateValueAndValidity({onlySelf: false, emitEvent: true});
      ["mapboxUser", "mapboxToken", "mapboxStyle", "googleKey"]
         .forEach(c => this.form.controls[c].markAsTouched());

      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.WEBMAP_SETTINGS_MODEL,
         valid: !this.model.service || this.form.valid
      });
   }

   isGoogleMaps(): boolean {
      return this.model.service == "googlemaps";
   }

   isMapbox(): boolean {
      return this.model.service == "mapbox";
   }
}
