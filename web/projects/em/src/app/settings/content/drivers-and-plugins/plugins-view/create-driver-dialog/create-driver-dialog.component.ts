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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   Inject,
   OnInit,
   ViewChild,
   ViewEncapsulation
} from "@angular/core";
import {
   AbstractControl,
   FormGroup,
   UntypedFormBuilder,
   ValidationErrors,
   ValidatorFn,
   Validators
} from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MatStepper, StepState } from "@angular/material/stepper";
import { config, Observable, throwError } from "rxjs";
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
import { Tool } from "../../../../../../../../shared/util/tool";
import { DriverList } from "./driver-list";

interface UploadFilesResponse {
   identifier: string;
   files: string[]
}

interface MavenSearchResponse {
   results: string[];
}

@Component({
   selector: "em-create-driver-dialog",
   templateUrl: "./create-driver-dialog.component.html",
   styleUrls: ["./create-driver-dialog.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class CreateDriverDialogComponent implements OnInit {
   @ViewChild("stepper") stepper: MatStepper;
   loading = false;
   searching = false;
   drivers: string[] = [];
   filteredCoords: string[] = [];
   uploadForm: FormGroup;
   driverForm: FormGroup;
   pluginForm: FormGroup;
   private uploadId: string = null;

   get uploadFilesStepState(): StepState {
      if(!!this.stepper && this.stepper.selectedIndex === 0) {
         return "number";
      }
      else if(!!this.stepper && this.uploadForm.valid && !this.uploadForm.pristine) {
         return "done";
      }
      else {
         return "number";
      }
   }

   get selectDriversStepState(): StepState {
      if(!!this.stepper && this.stepper.selectedIndex === 1) {
         return "number";
      }
      else if(!!this.stepper && this.driverForm.valid && !this.driverForm.pristine) {
         return "done";
      }
      else {
         return "number";
      }
   }

   get configPluginStepState(): StepState {
      if(!!this.stepper && this.stepper.selectedIndex === 2) {
         return "number";
      }
      else if(!!this.stepper && this.pluginForm.valid && !this.pluginForm.pristine) {
         return "done";
      }
      else {
         return "number";
      }
   }

   constructor(public dialogRef: MatDialogRef<CreateDriverDialogComponent>,
               @Inject(MAT_DIALOG_DATA) private dialogData: {plugins: string[]},
               private http: HttpClient, private snackBar: MatSnackBar,
               fb: UntypedFormBuilder)
   {
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
      this.uploadForm.get("mavenCoord").valueChanges
         .pipe(
            filter(res => res !== null && res.length >= 4),
            distinctUntilChanged(),
            debounceTime(1000),
            tap(() => {
               this.filteredCoords = [];
               this.searching = true;
            }),
            switchMap(value => this.searchMaven(value).pipe(finalize(() => this.searching = false))),
         )
         .subscribe(coords => this.filteredCoords = coords);
   }

   ngOnInit(): void {
   }

   selectionChange(event: any) {
      if(event.selectedIndex == 1) {
         this.uploadDrivers();
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

   removeDriverFile(file: any): void {
      const control = this.uploadForm.get("uploadFiles");
      const values = (control.value as any[]).filter(f => f.name !== file.name);
      control.setValue(values);
      control.markAsDirty();
   }

   uploadDrivers(): void {
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

         params = params.set("uploadType", "driver");
         const options = { params };
         id$ = this.http.post<UploadFilesResponse>("../api/em/upload", data, options)
            .pipe(map(res => res.identifier));
      }

      id$.pipe(
         tap(id => this.uploadId = id),
         mergeMap(id => this.scanDrivers(id)),
         finalize(() => this.loading = false),
         catchError(error => {
            const message = error.error?.message?.includes("unresolved dependency") ?
               "_#(js:em.data.databases.driver.mavenCoordMissing)" :
               "_#(js:em.data.databases.driverUploadError)";
            this.snackBar.open(message, null, {duration: Tool.SNACKBAR_DURATION});
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
         this.stepper.next();
      });
   }

   createDriver(): void {
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
               this.snackBar.open("_#(js:em.data.databases.driverCreateError)", null, {duration: Tool.SNACKBAR_DURATION});
               console.error("Failed to upload driver(s): ", error);
               return throwError(error);
            })
         ).subscribe(() => this.dialogRef.close(true));
   }

   private scanDrivers(id: string): Observable<string[]> {
      return this.http
         .get<DriverList>(`../api/em/settings/content/plugins/drivers/scan/${id}`)
         .pipe(map(drivers => drivers.drivers));
   }

   private searchMaven(query: string): Observable<string[]> {
      const params = new HttpParams().set("q", query);
      const options = { params };
      return this.http
         .get<MavenSearchResponse>("../api/em/upload/maven-search", options)
         .pipe(map(res => res.results));
   }

   private pluginExists(): ValidatorFn {
      return (control: AbstractControl): ValidationErrors | null => {
         if(!!this.pluginForm) {
            const pluginId = this.pluginForm.get("pluginId").value;

            if(!!pluginId && !!this.dialogData && !!this.dialogData.plugins &&
               this.dialogData.plugins.indexOf(pluginId) >= 0)
            {
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

   protected readonly config = config;
}
