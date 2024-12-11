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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import {
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   FormGroupDirective,
   NgForm,
   ValidationErrors
} from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { ContextHelp } from "../../../context-help";
import { Searchable } from "../../../searchable";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { CustomShapeDialogComponent } from "./custom-shape-dialog.component";
import { EditFontsDialogComponent } from "./edit-fonts-dialog/edit-fonts-dialog.component";
import { LookAndFeelSettingsModel } from "./look-and-feel-settings-model";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { Tool } from "../../../../../../shared/util/tool";
import { HttpClient, HttpParams } from "@angular/common/http";
import { DataSpaceTreeDataSource } from "../../content/data-space/data-space-tree-data-source";
import { GuiTool } from "../../../../../../portal/src/app/common/util/gui-tool";

@Searchable({
   route: "/settings/presentation/settings#look-and-feel",
   title: "Look & Feel",
   keywords: [
      "em.settings", "em.settings.presentation", "em.settings.look",
      "em.settings.feel"
   ]
})
@ContextHelp({
   route: "/settings/presentation/settings#look-and-feel",
   link: "EMPresentationLookandFeel"
})
@Component({
   selector: "em-look-and-feel-settings-view",
   templateUrl: "./look-and-feel-settings-view.component.html",
   styleUrls: ["./look-and-feel-settings-view.component.scss"],
   providers: [DataSpaceTreeDataSource]
})
export class LookAndFeelSettingsViewComponent implements OnInit, OnDestroy {
   @Input() securityEnabled: boolean = false;
   @Input() isSysAdmin: boolean = false;
   @Input() orgId: string;
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();
   isMultiTenant: boolean = false;
   snackBarConfig: MatSnackBarConfig;
   dataSource: DataSpaceTreeDataSource;

   @Input()
   get model(): LookAndFeelSettingsModel {
      return this._model;
   }

   set model(model: LookAndFeelSettingsModel) {
      this._model = model;

      if(model) {
         this.form.get("ascending").setValue(model.ascending, {emitEvent: false});
         this.form.get("expand").setValue(model.expand, {emitEvent: false});
         this.updateFormFile("Logo", model);
         this.updateFormFile("Favicon", model);
         this.updateFormFile("Viewsheet", model);

         if(model.userformatFile) {
            this.form.get("userformatFile").setValue(model.userformatFile, {emitEvent: false});
         }

         this.form.get("defaultFonts").setValue(model.defaultFont, {emitEvent: false});
      }
      else {
         this.form.get("ascending").setValue(true, {emitEvent: false});
         this.form.get("expand").setValue(true, {emitEvent: false});
         this.form.get("defaultLogo").setValue(true, {emitEvent: false});
         this.form.get("logoFile").setValue(null, {emitEvent: false});
         this.form.get("defaultFavicon").setValue(true, {emitEvent: false});
         this.form.get("faviconFile").setValue(null, {emitEvent: false});
         this.form.get("defaultViewsheet").setValue(true, {emitEvent: false});
         this.form.get("viewsheetFile").setValue(null, {emitEvent: false});
         this.form.get("userformatFile").setValue(null, {emitEvent: false});
         this.form.get("defaultFonts").setValue(true, {emitEvent: false});
      }
   }

   form: UntypedFormGroup;
   logoErrorMatcher: ErrorStateMatcher;
   faviconErrorMatcher: ErrorStateMatcher;
   viewsheetErrorMatcher: ErrorStateMatcher;
   private _model: LookAndFeelSettingsModel;
   private destroy$ = new Subject<void>();

   constructor(private dialog: MatDialog, fb: UntypedFormBuilder, defaultErrorMatcher: ErrorStateMatcher,
               private snackbar: MatSnackBar, private http: HttpClient)
   {
      this.form = fb.group(
         {
            ascending: [true],
            expand: [true],
            defaultLogo: [true],
            logoFile: [null],
            defaultFavicon: [true],
            faviconFile: [null],
            defaultViewsheet: [true],
            viewsheetFile: [null],
            userformatFile: [null],
            defaultFonts: [true],
            selectedTheme: ["default"]
         },
         {
            validator: [
               this.fileRequired("Logo"),
               this.fileRequired("Favicon"),
               this.fileRequired("Viewsheet"),
               this.fileType("Logo"),
               this.fileType("Favicon")
            ]
         }
      );

      this.logoErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && (!!this.form.errors.logoFileRequired || !!this.form.errors.logoFileType) ||
            defaultErrorMatcher.isErrorState(control, form)
      };

      this.faviconErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && (!!this.form.errors.faviconFileRequired || !!this.form.errors.faviconFileType) ||
            defaultErrorMatcher.isErrorState(control, form)
      };

      this.viewsheetErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && !!this.form.errors.viewsheetFileRequired ||
            defaultErrorMatcher.isErrorState(control, form)
      };

      this.http.get("../api/em/navbar/isMultiTenant").subscribe((isMultiTenant: boolean) =>
      {
         this.isMultiTenant = isMultiTenant;
      });
   }

   ngOnInit(): void {
      // IE may trigger a change event immediately on populating the form
      setTimeout(() => {
         this.form.valueChanges
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => this.emitModel());
      }, 200);

      if(this.isSysAdmin) {
         this.dataSource = new DataSpaceTreeDataSource(this.http);
      }

      this.snackBarConfig = new MatSnackBarConfig();
      this.snackBarConfig.duration = Tool.SNACKBAR_DURATION;
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   editUserFonts() {
      let dialogRef = this.dialog.open(EditFontsDialogComponent, {
         role: "dialog",
         width: "70vw",
         height: "75vh",
         disableClose: true,
         data: {
            userFonts: this.model.userFonts || [],
            fontFaces: this.model.fontFaces || [],
            deleteFontFaces: this.model.deleteFontFaces || [],
            newFontFaces: this.model.newFontFaces || [],
         }
      });

      dialogRef.afterClosed().subscribe((result: any) => {
         if(!!result) {
            this.model.userFonts = result.userFonts;
            this.model.fontFaces = result.fontFaces;
            this.model.deleteFontFaces = result.deleteFontFaces;
            this.model.newFontFaces = result.newFontFaces;
            this.emitModel();
         }
      });
   }

   private emitModel() {
      this.model.ascending = this.form.get("ascending").value;
      this.model.repositoryTree = GuiTool.isMobileDevice() ? false : true;
      this.model.expand = this.form.get("expand").value;
      this.model.defaultFont = this.form.get("defaultFonts").value;
      this.updateModelFile("Logo");
      this.updateModelFile("Favicon");
      this.updateModelFile("Viewsheet");

      if(this.form.get("userformatFile").value) {
         this.model.userformatFile = this.form.get("userformatFile").value[0];
      }

      if(this.model.defaultFont) {
         this.model.userFonts = [];
         this.model.fontFaces = [];
         this.model.deleteFontFaces = [];
         this.model.newFontFaces = [];
      }

      this.modelChanged.emit(<PresentationSettingsChanges>{
         model: this.model,
         modelType: PresentationSettingsType.LOOK_AND_FEEL_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }

   private updateFormFile(type: string, model: LookAndFeelSettingsModel) {
      const prefix = type.toLowerCase();
      const useDefault = model[`default${type}`];
      const name = model[`${prefix}Name`];
      const file = model[`${prefix}File`];

      this.form.get(`default${type}`).setValue(useDefault, {emitEvent: false});

      if(!useDefault && file) {
         this.form.get(`${prefix}File`).setValue([file], {emitEvent: false});
      }
      else if(!useDefault && name) {
         this.form.get(`${prefix}File`).setValue([{ name: name, content: null }], {emitEvent: false});
      }
      else {
         this.form.get(`${prefix}File`).setValue(null, {emitEvent: false});
      }
   }

   private updateModelFile(type: string): void {
      const prefix = type.toLowerCase();
      const useDefault = this.form.get(`default${type}`).value;
      const control = this.form.get(`${prefix}File`);
      const file = control.value && control.value.length && control.value[0] ? control.value[0] : null;
      this.model[`default${type}`] = useDefault;

      if(useDefault) {
         this.model[`${prefix}Name`] = null;
         this.model[`${prefix}File`] = null;
      }
      else if(file && file.content) {
         this.model[`${prefix}Name`] = file.name;
         this.model[`${prefix}File`] = file;
      }
      else {
         this.model[`${prefix}File`] = null;
      }
   }

   private fileRequired(type: string): (FormGroup) => ValidationErrors | null {
      const prefix = type.toLowerCase();

      return (group) => {
         if(group) {
            const toggle = group.get(`default${type}`);
            const chooser = group.get(`${prefix}File`);

            if(toggle && chooser && !toggle.value) {
               if (!chooser.value || !chooser.value.length || !chooser.value[0]) {
                  const error = {};
                  error[`${prefix}FileRequired`] = true;
                  return error;
               }
            }
         }

         return null;
      };
   }

   fileType(type: string): (FormGroup) => ValidationErrors | null {
      const prefix = type.toLowerCase();
      const accept = ["jpg", "jpeg", "png", "bmp"];

      return (group) => {
         if(group) {
            const toggle = group.get(`default${type}`);
            const chooser = group.get(`${prefix}File`);

            if(toggle && chooser && !toggle.value && chooser.value) {
               const fileName = chooser.value[0].name;
               const fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);

               if(!accept.includes(fileExtension)) {
                  const error = {};
                  error[`${prefix}FileType`] = true;
                  return error;
               }
            }
         }

         return null;
      };
   }

   customShapes() {
      this.dialog.open(CustomShapeDialogComponent, <MatDialogConfig>{
         data: {
            orgId: this.orgId
         }
      });
   }
}
