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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { LogSettingsModel } from "../log-settings-model";
import { LogLevelDTO } from "../LogLevelDTO";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";

export interface LogSettingsChanges {
   changes: LogSettingsModel;
   valid: boolean;
}

@Component({
   selector: "em-logging-settings-view",
   templateUrl: "./logging-settings-view.component.html",
   styleUrls: ["./logging-settings-view.component.scss"]
})
export class LoggingSettingsViewComponent {
   @Output() logSettingsChanged = new EventEmitter<LogSettingsChanges>();
   private _model: LogSettingsModel;
   form: UntypedFormGroup;
   enterprise: boolean;

   @Input()
   set model(value: LogSettingsModel) {
      this._model = value;

      if(!!value) {
         this.form.patchValue(value, {emitEvent: false});
         this.updateEnabled();
      }
   }

   get model(): LogSettingsModel {
      //handle boolean not setting when !securityEnabled
      if(this._model && this._model.fluentdSettings) {
         this._model.fluentdSettings.userAuthenticationEnabled =
            this._model.fluentdSettings.userAuthenticationEnabled == null ? false :
               this._model.fluentdSettings.userAuthenticationEnabled;
      }

      return this._model;
   }

   constructor(formBuilder: UntypedFormBuilder,
               private appInfoService: AppInfoService) {
      this.form = formBuilder.group({
         provider: ["", [Validators.required]],
         outputToStd: [false],
         detailLevel: [""],
         fileSettings: formBuilder.group({
            file: [""],
            maxLogSize: [1, [Validators.required, Validators.min(1)]],
            count: [1, [Validators.required, Validators.min(1)]]
         }),
         fluentdSettings: formBuilder.group({
            host: ["", [Validators.required]],
            port: [24224, [Validators.required, Validators.min(1), Validators.max(65536)]],
            connectTimeout: [10000, [Validators.required, Validators.min(0)]],
            securityEnabled: [false],
            userAuthenticationEnabled: [false],
            orgAdminAccess: [false],
            sharedKey: ["", [Validators.required]],
            username: ["", [Validators.required]],
            password: ["", [Validators.required]],
            tlsEnabled: [false],
            caCertificateFile: [""],
            logViewUrl: [""],
            auditViewUrl: [""]
         })
      });

      this.form.valueChanges.subscribe((value) => {
         if(value.customDir) {
            value.customDir = value.customDir.trim();
         }

         Object.assign(this.model, value);
         this.updateEnabled();
         this.emitChanges();
      });

      appInfoService.isEnterprise().subscribe(info => this.enterprise = info);
   }

   changeLoggingLevels(logLevels: LogLevelDTO[]) {
      this.model.logLevels = logLevels.slice(0);
      this.emitChanges();
   }

   emitChanges() {
      this.logSettingsChanged.emit(<LogSettingsChanges>{
         changes: this.model,
         valid: this.form.valid
      });
   }

   private updateEnabled(): void {
      if(!!this.form) {
         const provider = this.form.get("provider").value;

         this.form.get("outputToStd").enable({emitEvent: false});
         this.form.get("detailLevel").enable({emitEvent: false});

         if(provider === "file") {
            this.form.get("fileSettings").enable({emitEvent: false});
            this.form.get("fluentdSettings").disable({emitEvent: false});
         }
         else { // fluentd
            this.form.get("fileSettings").disable({emitEvent: false});
            this.form.get("fluentdSettings").enable({emitEvent: false});
            this.form.get("fluentdSettings").get("orgAdminAccess").enable({emitEvent: false});

            const securityEnabled = this.form.get("fluentdSettings").get("securityEnabled").value;

            if(securityEnabled) {
               this.form.get("fluentdSettings").get("sharedKey").enable({emitEvent: false});
               this.form.get("fluentdSettings").get("userAuthenticationEnabled").enable({emitEvent: false});

               const userAuthenticationEnabled = this.form.get("fluentdSettings").get("userAuthenticationEnabled").value;

               if(userAuthenticationEnabled) {
                  this.form.get("fluentdSettings").get("username").enable({emitEvent: false});
                  this.form.get("fluentdSettings").get("password").enable({emitEvent: false});
               }
               else {
                  this.form.get("fluentdSettings").get("username").disable({emitEvent: false});
                  this.form.get("fluentdSettings").get("password").disable({emitEvent: false});
               }
            }
            else {
               this.form.get("fluentdSettings").get("sharedKey").disable({emitEvent: false});
               this.form.get("fluentdSettings").get("userAuthenticationEnabled").disable({emitEvent: false});
               this.form.get("fluentdSettings").get("username").disable({emitEvent: false});
               this.form.get("fluentdSettings").get("password").disable({emitEvent: false});
            }

            const tlsEnabled = this.form.get("fluentdSettings").get("tlsEnabled").value;

            if(tlsEnabled) {
               this.form.get("fluentdSettings").get("caCertificateFile").enable({emitEvent: false});
            }
            else {
               this.form.get("fluentdSettings").get("caCertificateFile").disable({emitEvent: false});
            }
         }
      }
   }
}
