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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import {
   AbstractControl,
   FormGroup,
   UntypedFormBuilder,
   ValidationErrors,
   ValidatorFn,
   Validators
} from "@angular/forms";
import { Observable, throwError } from "rxjs";
import {
   catchError,
   debounceTime,
   distinctUntilChanged,
   filter,
   finalize,
   map,
   mergeMap,
   switchMap,
   tap
} from "rxjs/operators";
import { DriverList } from "../../../../../../../../em/src/app/settings/content/drivers-and-plugins/plugins-view/create-driver-dialog/driver-list";
import { PluginsModel } from "../../../../../../../../shared/util/model/plugins-model";
import { DataNotificationsComponent } from "../../../data-notifications.component";

interface UploadFilesResponse {
   identifier: string;
   files: string[]
}

interface MavenSearchResponse {
   results: string[];
}

@Component({
   selector: "driver-wizard",
   templateUrl: "./driver-wizard.component.html",
   styleUrls: ["./driver-wizard.component.scss"]
})
export class DriverWizardComponent implements OnInit {
   @Input() plugins: string[] = [];
   @Output() onCommit = new EventEmitter<string>();
   @Output() onCancel = new EventEmitter<string>();
   @ViewChild("dataNotifications") dataNotifications: DataNotificationsComponent;
   loading = false;
   step = "upload";
   drivers: string[] = [];
   selectedDrivers: boolean[] = [];
   selectedDriverFiles: number[] = [];
   uploadForm: FormGroup;
   driverForm: FormGroup;
   pluginForm: FormGroup;
   private uploadId: string = null;

   constructor(private http: HttpClient, fb: UntypedFormBuilder) {
      this.uploadForm = fb.group({
         uploadType: ["upload", [Validators.required]],
         mavenCoord: ["", [this.mavenCoordRequired(), Validators.pattern(/^[^:]+:[^:]+:[^:]+/)]],
         uploadFiles: [null]
      }, {
         validators: [this.uploadFilesRequired()]
      });
      this.driverForm = fb.group({
         drivers: [null, [Validators.required]]
      });
      this.pluginForm = fb.group({
         pluginId: ["", [Validators.required, this.pluginExists()]],
         pluginName: ["", [Validators.required]],
         pluginVersion: ["", [Validators.required, Validators.pattern(/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/)]]
      });

      this.uploadForm.get("mavenCoord").disable();
      this.uploadForm.get("uploadType").valueChanges.subscribe(value => {
         if(value === "upload") {
            this.uploadForm.get("mavenCoord").disable();
         }
         else {
            this.uploadForm.get("mavenCoord").enable();
         }
      });
   }

   ngOnInit(): void {
      if(!this.plugins || this.plugins.length === 0) {
         this.http.get<PluginsModel>("../api/data/plugins")
            .subscribe(model => this.plugins = model.plugins.map(plugin => plugin.id));
      }
   }

   mavenSearch = (text: Observable<string>) => {
      return text.pipe(
         filter(res => res !== null && res.length >= 4),
         distinctUntilChanged(),
         debounceTime(1000),
         switchMap((term: string) => this.searchMaven(term))
      );
   };

   private searchMaven(query: string): Observable<string[]> {
      const params = new HttpParams().set("q", query);
      const options = { params };
      return this.http
         .get<MavenSearchResponse>("../api/em/upload/maven-search", options)
         .pipe(map(res => res.results));
   }

   selectDriverFile(event: MouseEvent, index: number): void {
      const posInSelected = this.selectedDriverFiles.indexOf(index);

      if(!event.ctrlKey && !event.metaKey && !event.shiftKey) {
         this.selectedDriverFiles = [];
      }

      if(event.shiftKey) {
         if(this.selectedDriverFiles == null || this.selectedDriverFiles.length == 0) {
            this.selectedDriverFiles = [index];
            return;
         }

         const last = this.selectedDriverFiles[this.selectedDriverFiles.length - 1];
         this.selectedDriverFiles = [];

         // First add the new selected index
         this.selectedDriverFiles.push(index);

         // Then add all values between new selected index and last
         for (let i = Math.min(index, last) + 1; i < Math.max(index, last); i++) {
            this.selectedDriverFiles.push(i);
         }

         // Keep the last index unchanged by pushing it in the end
         if(last != index) {
            this.selectedDriverFiles.push(last);
         }
      }
      else if(event.ctrlKey) {
         if(posInSelected >= 0) {
            this.selectedDriverFiles.splice(posInSelected, 1);
         }
         else {
            this.selectedDriverFiles.push(index);
         }
      }
      else {
         this.selectedDriverFiles.push(index);
      }
   }

   addDriverFiles(event: any): void {
      if(event && event.target && event.target.files) {
         const control = this.uploadForm.get("uploadFiles");
         const values = ((control.value as any[]) || []).slice();

         for(let i = 0; i < event.target.files.length; i++) {
            const file = event.target.files[i];

            if(!values.some(f => f.name === file.name)) {
               values.push(file);
            }
         }

         control.setValue(values);
         control.markAsDirty();
      }
   }

   removeDriverFiles() {
      const control = this.uploadForm.get("uploadFiles");
      const values = (control.value as any[]).filter((_: any, index: number) => this.selectedDriverFiles.indexOf(index) < 0);
      control.setValue(values);
      control.markAsDirty();
      this.selectedDriverFiles = [];
   }

   isNextDisabled(): boolean {
      return this.step === "upload" && (this.uploadForm.invalid || this.uploadForm.pristine) ||
         this.step === "drivers" && (this.driverForm.invalid || this.driverForm.pristine) ||
         this.step === "plugin" && (this.pluginForm.invalid || this.pluginForm.pristine);
   }

   next(): void {
      if(this.step === "upload") {
         this.uploadDrivers();
      }
      else if(this.step === "drivers") {
         this.step = "plugin";
      }
      else if(this.step === "plugin") {
         this.createDriver();
      }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   private uploadDrivers(): void {
      this.loading = true;
      let id$: Observable<string>;

      if(this.uploadForm.get("uploadType").value === "maven") {
         id$ = this.http.post<UploadFilesResponse>("../api/em/upload/maven", {gav: this.uploadForm.get("mavenCoord").value})
            .pipe(map(res => res.identifier));
      }
      else {
         const data = new FormData();
         const values = this.uploadForm.get("uploadFiles").value as any[];
         values.forEach(f => data.append("uploadedFiles", f));

         let params = new HttpParams();

         if(!!this.uploadId) {
            params = params.set("id", this.uploadId);
         }

         const options = { params };
         id$ = this.http.post<UploadFilesResponse>("../api/em/upload", data, options)
            .pipe(map(res => res.identifier));
      }

      id$.pipe(
         tap(id => this.uploadId = id),
         mergeMap(id => this.scanDrivers(id)),
         finalize(() => this.loading = false),
         catchError(error => {
            if(!!this.dataNotifications) {
               const message = error.error?.message?.includes("unresolved dependency") ?
                  "_#(js:em.data.databases.driver.mavenCoordMissing)" :
                  "_#(js:em.data.databases.driverUploadError)";
               this.dataNotifications.notifications.danger(message);
            }

            console.error("Failed to upload driver(s): ", error);
            return throwError(error);
         })
      ).subscribe(drivers => {
         if(this.drivers.length > 0) {
            const unique = new Set(this.drivers);
            drivers.forEach(d => unique.add(d));
            this.drivers = Array.from(unique);
         }
         else {
            this.drivers = drivers;
         }
         this.step = "drivers";
      });
   }

   private scanDrivers(id: string): Observable<string[]> {
      return this.http
         .get<DriverList>(`../api/em/settings/content/plugins/drivers/scan/${id}`)
         .pipe(map(drivers => drivers.drivers));
   }

   private pluginExists(): ValidatorFn {
      return (control: AbstractControl): ValidationErrors | null => {
         if(!!this.pluginForm) {
            const pluginId = this.pluginForm.get("pluginId").value;

            if(!!pluginId && !!this.plugins && this.plugins.indexOf(pluginId) >= 0) {
               return { "pluginExists": true };
            }
         }

         return null;
      };
   }

   private uploadFilesRequired(): ValidatorFn {
      return (control: AbstractControl): ValidationErrors | null => {
         if(!!this.uploadForm && this.uploadForm.get("uploadType").value === "upload") {
            const values = (this.uploadForm.get("uploadFiles")?.value || []) as any[];

            if(!values || !values.length) {
               return { uploadFilesRequired: true };
            }
         }

         return null;
      };
   }

   private mavenCoordRequired(): ValidatorFn  {
      return (control: AbstractControl): ValidationErrors | null => {
         if(!!this.uploadForm && this.uploadForm.get("uploadType").value === "maven") {
            return Validators.required(control);
         }

         return null;
      };
   }

   trackByIdx(index: number): number {
      return index;
   }

   selectDriver(index: number): void {
      const control = this.driverForm.get("drivers");
      const value = this.drivers.filter((_: string, i: number) => this.selectedDrivers[i]);
      control.setValue(value);
      control.markAsDirty();
   }

   private createDriver(): void {
      const request = {
         uploadId: this.uploadId,
         pluginId: this.pluginForm.get("pluginId").value,
         pluginName: this.pluginForm.get("pluginName").value,
         pluginVersion: this.pluginForm.get("pluginVersion").value,
         drivers: this.driverForm.get("drivers").value
      };
      this.loading = true;
      this.http.post("../api/em/settings/content/plugins/drivers", request)
         .pipe(
            finalize(() => this.loading = false),
            catchError(error => {
               if(!!this.dataNotifications) {
                  this.dataNotifications.notifications.danger("_#(js:em.data.databases.driverCreateError)");
               }

               console.error("Failed to upload driver(s): ", error);
               return throwError(error);
            })
         ).subscribe(() => this.onCommit.emit("ok"));
   }
}
