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
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, ValidationErrors, Validators } from "@angular/forms";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { MatTableDataSource } from "@angular/material/table";
import { Numberify, RGBA, TinyColor } from "@ctrl/tinycolor";
import { interval, Subject } from "rxjs";
import { debounce, takeUntil } from "rxjs/operators";
import { Tool } from "../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { CustomThemeModel } from "../custom-theme-model";
import { ThemeCssModel } from "../theme-css-model";
import { ThemeCssVariableModel } from "../theme-css-variable-model";
import { ThemeCssEditorModel } from "./theme-css-editor-model";

const EM_DARK_VAR_NAME = "--inet-em-dark";
const EM_PRIMARY_PREFIX = "--inet-em-primary-";
const EM_ACCENT_PREFIX = "--inet-em-accent-";
const EM_CONTRAST_PREFIX = "contrast-";
const EM_LIGHT_TEXT_VAR = "var(--inet-em-light-primary-text)";
const EM_DARK_TEXT_VAR = "var(--inet-em-dark-primary-text)";

@Component({
   selector: "em-theme-css-view",
   templateUrl: "./theme-css-view.component.html",
   styleUrls: ["./theme-css-view.component.scss"]
})
export class ThemeCssViewComponent implements OnInit, OnDestroy, OnChanges {
   @Input() get theme(): CustomThemeModel {
      return this._theme;
   }

   set theme(value: CustomThemeModel) {
      this._theme = value;
      this.portalCss = value?.portalCss;
      this.emCss = value?.emCss;

      if(!this.isSiteAdmin && this.theme.global) {
         this.portalForm.disable({ emitEvent: false });
         this.emForm.disable({ emitEvent: false });
         this.disabled = true;
      }
      else {
         this.portalForm.enable({ emitEvent: false });
         this.emForm.enable({ emitEvent: false });
         this.disabled = false
      }
   }

   @Input() isSiteAdmin = false;
   @Output() themeCssChanged = new EventEmitter<ThemeCssEditorModel>();
   @ViewChild("portalSearch", {static: false}) portalSearchInput: ElementRef;
   @ViewChild("emSearch", {static: false}) emSearchInput: ElementRef;

   displayedColumns = ["name", "value", "actions"];
   portalCssDataSource = new MatTableDataSource<ThemeCssVariableModel>([]);
   emCssDataSource = new MatTableDataSource<ThemeCssVariableModel>([]);
   portalCss: ThemeCssModel;
   emCss: ThemeCssModel;
   emPrimaryColor: string;
   emAccentColor: string;
   portalForm: UntypedFormGroup;
   emForm: UntypedFormGroup;
   private _theme: CustomThemeModel;
   private destroy$ = new Subject<void>();
   presetColors = [];
   disabled = false;

   readonly portalCssData = <ThemeCssVariableModel[]> [
      // font
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.font)", heading: true},
      {name: "--inet-font-size-base"},
      {name: "--inet-font-family"},
      {name: "--inet-text-color", color: true},
      {name: "--inet-icon-color", color: true},

      // general colors
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.genColors)", heading: true},
      {name: "--inet-primary-color", color: true, autoGenLightDark: true},
      {name: "--inet-secondary-color", color: true, autoGenLightDark: true},
      {name: "--inet-third-color", color: true, autoGenLightDark: true},
      {name: "--inet-fourth-color", color: true, autoGenLightDark: true},
      {name: "--inet-danger-color", color: true, autoGenLightDark: true},
      {name: "--inet-success-color", color: true, autoGenLightDark: true},
      {name: "--inet-warning-color", color: true, autoGenLightDark: true},
      {name: "--inet-info-color", color: true, autoGenLightDark: true},
      {name: "--inet-light-color", color: true, autoGenLightDark: true},
      {name: "--inet-dark-color", color: true, autoGenLightDark: true},

      // panels
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.panels)", heading: true},
      {name: "--inet-main-panel-bg-color", color: true},
      {name: "--inet-side-panel-bg-color", color: true},
      {name: "--inet-panel-border"},

      {name: "", heading: true},
      {name: "--inet-portal-main-panel-bg-color", color: true},
      {name: "--inet-portal-side-panel-bg-color", color: true},

      {name: "", heading: true},
      {name: "--inet-composer-main-panel-bg-color", color: true},
      {name: "--inet-composer-side-panel-bg-color", color: true},

      // hover
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.hover)", heading: true},
      {name: "--inet-hover-primary-bg-color", color: true},
      {name: "--inet-hover-secondary-bg-color", color: true},
      {name: "--inet-hover-text-color", color: true},

      // selected items
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.selItems)", heading: true},
      {name: "--inet-selected-item-bg-color", color: true},
      {name: "--inet-selected-item-text-color", color: true},

      // navbar
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.navbar)", heading: true},
      {name: "--inet-navbar-text-color", color: true},
      {name: "--inet-navbar-bg-color", color: true},
      {name: "--inet-navbar-selected-border-color", color: true},
      {name: "--inet-navbar-logo-color", color: true},
      {name: "--inet-navbar-home-icon-color", color: true},
      {name: "--inet-navbar-home-bg-color", color: true},
      {name: "--inet-navbar-hover-text-color", color: true},
      {name: "--inet-navbar-hover-bg-color", color: true},

      {name: "", heading: true},
      {name: "--inet-portal-navbar-text-color", color: true},
      {name: "--inet-portal-navbar-bg-color", color: true},
      {name: "--inet-portal-navbar-hover-text-color", color: true},
      {name: "--inet-portal-navbar-hover-bg-color", color: true},

      {name: "", heading: true},
      {name: "--inet-composer-navbar-text-color", color: true},
      {name: "--inet-composer-navbar-bg-color", color: true},
      {name: "--inet-composer-navbar-hover-text-color", color: true},
      {name: "--inet-composer-navbar-hover-bg-color", color: true},

      // navigation tabs
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.navTabs)", heading: true},
      {name: "--inet-nav-tabs-text-color", color: true},
      {name: "--inet-nav-tabs-bg-color", color: true},
      {name: "--inet-nav-tabs-selected-border-color", color: true},

      // toolbars
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.toolbars)", heading: true},
      {name: "--inet-toolbar-bg-color", color: true},
      {name: "--inet-vs-toolbar-bg-color", color: true},
      {name: "--inet-repository-tree-toolbar-bg-color", color: true},

      // worksheet
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.worksheet)", heading: true},
      {name: "--inet-graph-assembly-bg-color", color: true},
      {name: "--inet-graph-assembly-text-color", color: true},
      {name: "--inet-graph-assembly-header-text-color", color: true},
      {name: "--inet-graph-assembly-header-selected-text-color", color: true},
      {name: "--inet-graph-assembly-header-selected-bg-color", color: true},
      {name: "--inet-graph-assembly-error-text-color", color: true},
      {name: "--inet-graph-connection-color", color: true},
      {name: "--inet-graph-connection-warning-color", color: true},
      {name: "--inet-schema-connection-color", color: true},
      {name: "--inet-schema-column-connected-bg-color", color: true},
      {name: "--inet-schema-column-compatible-bg-color", color: true},
      {name: "--inet-schema-column-incompatible-bg-color", color: true},
      {name: "--inet-ws-header-primary-bg-color", color: true},
      {name: "--inet-ws-header-secondary-bg-color", color: true},
      {name: "--inet-ws-table-odd-row-bg-color", color: true},
      {name: "--inet-ws-table-even-row-bg-color", color: true},

      // other
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.other)", heading: true},
      {name: "--inet-default-border-color", color: true},
      {name: "--inet-drag-drop-highlight-color", color: true},
      {name: "--inet-link-color", color: true},
      {name: "--inet-link-hover-color", color: true},
      {name: "--inet-ruler-text-color", color: true},
      {name: "--inet-ruler-bg-color", color: true},
      {name: "--inet-ruler-border-color", color: true},

      // bootstrap components
      // -> dialogs/slide outs
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.dialogs)", heading: true},
      {name: "--inet-dialog-bg-color", color: true},
      {name: "--inet-dialog-title-text-transform"},
      {name: "--inet-dialog-title-font-size"},

      // -> inputs
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.inputs)", heading: true},
      {name: "--inet-input-bg-color", color: true},
      {name: "--inet-input-text-color", color: true},
      {name: "--inet-input-border-color", color: true},
      {name: "--inet-input-focus-text-color", color: true},
      {name: "--inet-input-focus-bg-color", color: true},
      {name: "--inet-input-disabled-bg-color", color: true},
      {name: "--inet-input-disabled-opacity", color: true},

      // -> poppups/dropdowns
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.dropdowns)", heading: true},
      {name: "--inet-dropdown-bg-color", color: true},
      {name: "--inet-dropdown-toggle-color", color: true},

      // -> cards
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.cards)", heading: true},
      {name: "--inet-card-bg-color", color: true},

      // -> tables
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.tables)", heading: true},
      {name: "--inet-table-text-color", color: true},
      {name: "--inet-table-border-color", color: true},
      {name: "--inet-table-heading-text-color", color: true},
      {name: "--inet-table-heading-bg-color", color: true},

      // -> buttons
      // --> primary
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.primButton)", heading: true},
      {name: "--inet-button-primary-bg-color", color: true},
      {name: "--inet-button-primary-text-color", color: true},
      {name: "--inet-button-primary-border-color", color: true},
      {name: "--inet-button-primary-hover-bg-color", color: true},
      {name: "--inet-button-primary-hover-text-color", color: true},
      {name: "--inet-button-primary-hover-border-color", color: true},

      // --> secondary
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.secButton)", heading: true},
      {name: "--inet-button-secondary-bg-color", color: true},
      {name: "--inet-button-secondary-text-color", color: true},
      {name: "--inet-button-secondary-border-color", color: true},
      {name: "--inet-button-secondary-hover-bg-color", color: true},
      {name: "--inet-button-secondary-hover-text-color", color: true},
      {name: "--inet-button-secondary-hover-border-color", color: true},

      // --> default
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.defButton)", heading: true},
      {name: "--inet-button-default-bg-color", color: true},
      {name: "--inet-button-default-text-color", color: true},
      {name: "--inet-button-default-border-color", color: true},
      {name: "--inet-button-default-hover-bg-color", color: true},
      {name: "--inet-button-default-hover-text-color", color: true},
      {name: "--inet-button-default-hover-border-color", color: true},

      // --> light
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.lightButton)", heading: true},
      {name: "--inet-button-light-bg-color", color: true},
      {name: "--inet-button-light-text-color", color: true},
      {name: "--inet-button-light-border-color", color: true},
      {name: "--inet-button-light-hover-bg-color", color: true},
      {name: "--inet-button-light-hover-text-color", color: true},
      {name: "--inet-button-light-hover-border-color", color: true},

      // script editor
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.scriptTheme)", heading: true},
      {name: "--inet-script-theme"}
   ];

   readonly emCssData = <ThemeCssVariableModel[]> [
      // font
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.font)", heading: true},
      {name: "--inet-em-font-family"},

      // navbar
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.navbar)", heading: true},
      {name: "--inet-em-navbar-text-color", color: true},
      {name: "--inet-em-navbar-bg-color", color: true},
      {name: "--inet-em-navbar-selected-border-color", color: true},
      {name: "--inet-em-navbar-logo-color", color: true},
      {name: "--inet-em-navbar-home-icon-color", color: true},
      {name: "--inet-em-navbar-home-bg-color", color: true},

      // script editor
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.scriptTheme)", heading: true},
      {name: "--inet-em-script-theme"}
   ];

   constructor(private dialog: MatDialog, formBuilder: UntypedFormBuilder) {
      this.portalForm = formBuilder.group(this.getFormControls(this.portalCssData));

      // add em forms
      const emFormControls = this.getFormControls(this.emCssData);
      emFormControls["--inet-em-dark"] = new UntypedFormControl("");
      this.emForm = formBuilder.group(emFormControls);
   }

   ngOnInit(): void {
      setTimeout(() => {
         this.portalForm.valueChanges
            .pipe(
               takeUntil(this.destroy$),
               debounce(() => interval(500))
            )
            .subscribe(() => this.portalFormValueChanged());
      }, 200);

      setTimeout(() => {
         this.emForm.valueChanges
            .pipe(
               takeUntil(this.destroy$),
               debounce(() => interval(500))
            )
            .subscribe(() => this.emFormValueChanged());
      }, 200);

      if(!this.isSiteAdmin && this.theme.global) {
         this.portalForm.disable();
         this.emForm.disable();
         this.disabled = true;
      }
      else {
         this.portalForm.enable();
         this.emForm.enable();
         this.disabled = false;
      }
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   ngOnChanges(changes: SimpleChanges): void {
      let sameTheme = false;

      if(changes["theme"]) {
         sameTheme = changes["theme"]?.previousValue?.id === changes["theme"]?.currentValue?.id;
      }

      if(!sameTheme && this.portalSearchInput && this.emSearchInput) {
         this.portalSearchInput.nativeElement.value = null;
         this.emSearchInput.nativeElement.value = null;
      }

      this.portalCssDataSource =
         this.initTableDataSource(sameTheme ? this.portalCssDataSource.data : this.portalCssData,
            this.portalCss, this.portalForm, sameTheme ? this.portalCssDataSource : null);

      if(this.emCss && this.emCss.variables) {
         let primaryVariable = this.emCss.variables
            .find((v) => v.name.indexOf(EM_PRIMARY_PREFIX + "500") >= 0);
         let accentVariable = this.emCss.variables
            .find((v) => v.name.indexOf(EM_ACCENT_PREFIX + "500") >= 0);

         this.emPrimaryColor = primaryVariable ? primaryVariable.value : null;
         this.emAccentColor = accentVariable ? accentVariable.value : null;

         // keeping the dark toggle outside the table so need to reset the value here
         let emDark = this.emCss.variables
            .find((v) => v.name.indexOf("--inet-em-dark") >= 0);
         this.emForm.get("--inet-em-dark").setValue(emDark ? emDark.value : null,
            {emitEvent: false});
      }

      this.emCssDataSource = this.initTableDataSource(
         sameTheme ? this.emCssDataSource.data : this.emCssData, this.emCss, this.emForm,
         sameTheme ? this.emCssDataSource : null);
   }

   private getFormControls(tableData: ThemeCssVariableModel[]) {
      const formControls = {};
      const patternValidator = Validators.pattern(/^[^:;{}]*$/);

      for(let cssVar of tableData) {
         if(cssVar.heading) {
            continue;
         }

         const validators = [];
         validators.push(patternValidator);

         // if auto gen colors then the value needs to be a valid color
         if(cssVar.autoGenLightDark) {
            validators.push(this.isColor);
         }

         formControls[cssVar.name] = new UntypedFormControl(cssVar.value, validators);
      }

      return formControls;
   }

   private initTableDataSource(tableData: ThemeCssVariableModel[], css: ThemeCssModel,
                               form: UntypedFormGroup,
                               oldDataSource: MatTableDataSource<ThemeCssVariableModel>): MatTableDataSource<ThemeCssVariableModel>
   {
      const oldFilter = !!oldDataSource ? oldDataSource.filter : null;
      const tableCssDataSource = new MatTableDataSource(Tool.clone(tableData));
      tableCssDataSource.filterPredicate = (data: ThemeCssVariableModel, filter: string) => {
         if(!filter) {
            return true;
         }

         return !data.heading && data.name.toLowerCase().indexOf(filter.toLowerCase()) >= 0;
      };
      tableCssDataSource.filter = oldFilter;

      if(css && css.variables) {
         for(let tableCssVar of tableCssDataSource.data) {
            let value = null;

            for(let cssVar of css.variables) {
               // if names are same then copy the css value
               if(!tableCssVar.heading && tableCssVar.name == cssVar.name) {
                  value = cssVar.value;
                  break;
               }
            }

            // always reset the value in case the variable is no longer there as can be the case
            // when switching between different themes
            if(!tableCssVar.heading) {
               if(this.isScript(tableCssVar.name) && !value) {
                  value = "ECLIPSE";
               }

               tableCssVar.value = value;
               form.get(tableCssVar.name).setValue(value, {emitEvent: false});
            }

            if(tableCssVar.color && !oldDataSource) {
               if(!tableCssVar.value || tableCssVar.autoGenLightDark) {
                  tableCssVar.colorPickerActive = true;
               }
               else {
                  tableCssVar.colorPickerActive = new TinyColor(tableCssVar.value).isValid;
               }
            }
         }
      }

      return tableCssDataSource;
   }

   updateEMPrimaryPalette(color: string) {
      this.emPrimaryColor = color;
      const palette = this.computePalette(this.emPrimaryColor);
      this.setEMCssVariables(palette, EM_PRIMARY_PREFIX);
   }

   updateEMAccentPalette(color: string) {
      this.emAccentColor = color;
      const palette = this.computePalette(this.emAccentColor);
      this.setEMCssVariables(palette, EM_ACCENT_PREFIX);
   }

   private computePalette(hex: string): { [key: string]: TinyColor } {
      if(!hex || hex == "none") {
         return null;
      }

      const baseLight = new TinyColor("#ffffff");
      const baseDark = this.multiply(new TinyColor(hex).toRgb(), new TinyColor(hex).toRgb());
      const baseTriad = new TinyColor(hex).tetrad();
      let palette = {};
      palette["50"] = baseLight.mix(hex, 12);
      palette["100"] = baseLight.mix(hex, 30);
      palette["200"] = baseLight.mix(hex, 50);
      palette["300"] = baseLight.mix(hex, 70);
      palette["400"] = baseLight.mix(hex, 85);
      palette["500"] = baseLight.mix(hex, 100);
      palette["600"] = baseDark.mix(hex, 87);
      palette["700"] = baseDark.mix(hex, 70);
      palette["800"] = baseDark.mix(hex, 54);
      palette["900"] = baseDark.mix(hex, 25);
      palette["A100"] = baseDark.mix(baseTriad[4], 15).saturate(80).lighten(65);
      palette["A200"] = baseDark.mix(baseTriad[4], 15).saturate(80).lighten(55);
      palette["A400"] = baseDark.mix(baseTriad[4], 15).saturate(100).lighten(45);
      palette["A700"] = baseDark.mix(baseTriad[4], 15).saturate(100).lighten(40);
      return palette;
   }

   /**
    * Contrast color palette
    *
    * Iterate over the keys in the palette and check if color is light or
    * dark and then use the appropriate contrast color
    */
   private computeContrastPaletteVariables(palette: { [key: string]: TinyColor }): { [key: string]: string } {
      let contrastPalette = {};

      for(let key in palette) {
         if(palette.hasOwnProperty(key)) {
            let color = palette[key];

            if(color) {
               contrastPalette[key] = color.isDark() ? EM_LIGHT_TEXT_VAR :
                  EM_DARK_TEXT_VAR;
            }
         }
      }

      return contrastPalette;
   }

   private multiply(rgb1: Numberify<RGBA>, rgb2: Numberify<RGBA>): TinyColor {
      rgb1.b = Math.floor(rgb1.b * rgb2.b / 255);
      rgb1.g = Math.floor(rgb1.g * rgb2.g / 255);
      rgb1.r = Math.floor(rgb1.r * rgb2.r / 255);
      return new TinyColor("rgb " + rgb1.r + " " + rgb1.g + " " + rgb1.b);
   }

   private setEMCssVariables(palette: { [key: string]: TinyColor }, prefix: string) {
      if(!!this.emCss && !!this.emCss.variables) {
         // clear any existing variables that start with this prefix
         this.emCss.variables = this.emCss.variables.filter((variable) =>
            !!variable.name && variable.name.indexOf(prefix) < 0);
      }
      else if(!!palette) {
         this.emCss = {variables: []};
      }

      // if palette is set to null then there is nothing else to do here
      if(!palette) {
         this.fireThemeCssChanged();
         return;
      }

      let contrastPaletteVariables = this.computeContrastPaletteVariables(palette);

      for(let key in palette) {
         if(palette.hasOwnProperty(key)) {
            // normal
            this.emCss.variables.push(<ThemeCssVariableModel>{
               name: prefix + key,
               value: palette[key].toHexString()
            });

            // contrast
            this.emCss.variables.push(<ThemeCssVariableModel>{
               name: prefix + EM_CONTRAST_PREFIX + key,
               value: contrastPaletteVariables[key]
            });
         }
      }

      this.fireThemeCssChanged();
   }

   emFormValueChanged() {
      if(!this.emCss || !this.emCss.variables) {
         this.emCss = {variables: []};
      }

      // remove the form variables first
      this.emCss.variables = this.emCss.variables.filter((variable) =>
         !!variable.name && !this.emForm.contains(variable.name));

      // add the form variables that have values set
      for(let name in this.emForm.controls) {
         if(this.emForm.controls.hasOwnProperty(name)) {
            const value = this.emForm.get(name).value;

            if(!!value) {
               this.emCss.variables.push(<ThemeCssVariableModel> {
                  name: name,
                  value: value
               });
            }
         }
      }

      this.fireThemeCssChanged();
   }

   portalFormValueChanged() {
      this.portalCss.variables = [];

      for(let tableCssVar of this.portalCssDataSource.data) {
         if(!tableCssVar.heading) {
            let cssValue = this.portalForm.controls[tableCssVar.name].value;

            if(!!cssValue) {
               this.portalCss.variables.push({
                  name: tableCssVar.name,
                  value: cssValue
               });

               if(tableCssVar.autoGenLightDark) {
                  this.portalCss.variables.push({
                     name: tableCssVar.name + "-light",
                     value: this.lighten(cssValue)
                  });

                  this.portalCss.variables.push({
                     name: tableCssVar.name + "-dark",
                     value: this.darken(cssValue)
                  });
               }
               else if(tableCssVar.name == "--inet-panel-border") {
                  this.portalCss.variables.push({
                     name: "--inet-panel-border-zero",
                     value: "0"
                  });
               }
            }
         }
      }

      this.fireThemeCssChanged();
   }

   toggleEditor(cssVar: ThemeCssVariableModel) {
      cssVar.colorPickerActive = !cssVar.colorPickerActive;
   }

   clearEditorValue(cssVar: ThemeCssVariableModel, form: UntypedFormGroup) {
      form.controls[cssVar.name].setValue(null);
   }

   updateCssColorValue(cssVar: ThemeCssVariableModel, color: string, form: UntypedFormGroup) {
      if(!color || color == "none") {
         form.controls[cssVar.name].setValue(null);
         return;
      }

      let tinyColor = new TinyColor(color);

      // hex8 not supported in css
      if(tinyColor.getAlpha() != 1) {
         color = tinyColor.toRgbString();
      }

      form.controls[cssVar.name].setValue(color);
   }

   lighten(color: string): string {
      return new TinyColor(color).tint(10).lighten(10).toRgbString();
   }

   darken(color: string): string {
      return new TinyColor(color).shade(10).darken(10).toRgbString();
   }

   private isColor(control: UntypedFormControl): ValidationErrors {
      if(!control.value) {
         return null;
      }

      const color = new TinyColor(control.value);

      if(!color.isValid) {
         return {notColor: true};
      }

      return null;
   }

   isScript(name: string) {
      return name == "--inet-script-theme" || name == "--inet-em-script-theme";
   }

   clear() {
      let content = "_#(js:em.presentation.lookAndFeel.css.clearPrompt)";

      this.dialog.open(MessageDialog, <MatDialogConfig>{
         width: "350px",
         data: {
            title: "_#(js:Confirm)",
            content: content,
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            for(let tableCssVar of this.portalCssDataSource.data) {
               if(!tableCssVar.heading) {
                  if(tableCssVar.name == "--inet-script-theme") {
                     this.portalForm.get(tableCssVar.name).setValue("ECLIPSE");
                  }
                  else {
                     this.portalForm.get(tableCssVar.name).setValue(null);
                  }
               }
            }

            this.portalCss.variables = [];
            this.emCss.variables = [];
            this.emPrimaryColor = null;
            this.emAccentColor = null;

            for(let name in this.emForm.controls) {
               if(this.emForm.controls.hasOwnProperty(name)) {
                  if(name == "--inet-em-script-theme") {
                     this.emForm.get(name).setValue("ECLIPSE");
                  }
                  else {
                     this.emForm.get(name).setValue(null);
                  }
               }
            }

            this.fireThemeCssChanged();
         }
      });
   }

   filterTable(filter: string, portal: boolean) {
      if(portal) {
         this.portalCssDataSource.filter = filter;
      }
      else {
         this.emCssDataSource.filter = filter;
      }
   }

   private fireThemeCssChanged() {
      this.themeCssChanged.emit({
         portalCss: this.portalCss,
         emCss: this.emCss,
         valid: this.portalForm.valid && this.emForm.valid
      });
   }

   trackByFn(index, node: ThemeCssVariableModel) {
      return index;
   }
}
