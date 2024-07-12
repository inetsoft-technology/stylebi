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
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";
import { FormatTool } from "../../common/util/format-tool";
import { Tool } from "../../../../../shared/util/tool";
import { ChartSelection } from "../../graph/model/chart-selection";
import { ChartTool } from "../../graph/model/chart-tool";
import { DebounceService } from "../../widget/services/debounce.service";
import { BaseTableModel } from "../model/base-table-model";
import { PrintLayoutSection } from "../model/layout/print-layout-section";
import { VSTextModel } from "../model/output/vs-text-model";
import { VSChartModel } from "../model/vs-chart-model";
import { VSObjectModel } from "../model/vs-object-model";
import { VSGaugeModel } from "../model/output/vs-gauge-model";
import { ColorDropdown } from "../../widget/color-picker/color-dropdown.component";
import { ComboMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { PresenterPropertyDialogModel } from "../../widget/presenter/data/presenter-property-dialog-model";
import { FontService } from "../../widget/services/font.service";
import { ModelService } from "../../widget/services/model.service";
import { ComponentTool } from "../../common/util/component-tool";
import { TableDataPathTypes } from "../../common/data/table-data-path-types";
import { GraphTypes } from "../../common/graph-types";
import { ChartRegion } from "../../graph/model/chart-region";

const PRESENTER_PROPERTY_URI: string = "composer/vs/presenter-property-dialog-model/";
const COLOR_LABLE_MAP: Map<string, string> = new Map([
   ["Static", "_#(js:Static)"]
]);

@Component({
   selector: "vs-formats-pane",
   templateUrl: "vs-formats-pane.component.html",
   styleUrls: ["../../binding/editor/formats-pane.component.scss"]
})
export class VSFormatsPane implements OnInit, OnChanges {
   @Input() inactive: boolean;
   @Input() set focusedAssemblies(focusedAssemblies: VSObjectModel[]) {
      this._focusedAssemblies = focusedAssemblies ? focusedAssemblies.filter(a => !!a)
         : focusedAssemblies;
      this.updateProperties();
   }

   get focusedAssemblies(): VSObjectModel[] {
      return this._focusedAssemblies;
   }

   @Input() variableValues: string[] = [];
   @Input() vsId: string;
   @Input() layout: boolean = false;
   @Input() layoutRegion: PrintLayoutSection = PrintLayoutSection.HEADER;
   @Input() viewer: boolean;
   @Input() formatPaneDisabled = false;
   @Output() onPresenterPropertiesChange = new EventEmitter<[string, PresenterPropertyDialogModel]>();
   @Output() onChangeFormat = new EventEmitter<FormatInfoModel>();
   @Output() onCloseFormat = new EventEmitter<string>();
   @ViewChild("presenterPropertyDialog") presenterPropertyDialog: TemplateRef<any>;
   presenterPropertyDialogModel: PresenterPropertyDialogModel;
   fonts: string[];
   _format: FormatInfoModel;
   vsObjectFormat: VSObjectFormatInfoModel = <VSObjectFormatInfoModel> {};
   alphaInvalid: boolean = false;
   private _color: string;
   private _colorType: number = ComboMode.VALUE;
   private _backgroundColor: string;
   private _backgroundColorType: number = ComboMode.VALUE;
   _focusedAssemblies: VSObjectModel[];
   formatDisabled: boolean = false;
   fontDisabled: boolean = false;
   formattingDisabled: boolean = false;
   colorDisabled: boolean = false;
   backgroundDisabled: boolean = false;
   dynamicColorDisabled: boolean = false;
   alignDisabled: boolean = false;
   wrapTextDisabled: boolean = false;
   borderDisabled: boolean = false;
   roundCornerDisabled: boolean = false;
   roundTopCornersOnlyVisible: boolean = false;
   cssDisabled: boolean = false;
   vsSelected: boolean = false;
   public ColorDropdown = ColorDropdown;
   public Tool = Tool;

   get color(): string {
      return !!COLOR_LABLE_MAP.get(this._color) ?
         COLOR_LABLE_MAP.get(this._color) : this._color;
   }

   set color(value: string) {
      if(value !== this._color) {
         this._color = value;
         this.vsObjectFormat.colorType = value;
         this.updateFormat();
      }
   }

   get colorType(): number {
      return this._colorType;
   }

   set colorType(value: number) {
      if(value !== this._colorType) {
         this._colorType = value;

         if(value === ComboMode.VALUE) {
            this.color = ColorDropdown.STATIC;
         }
      }
   }

   get backgroundColor(): string {
      return !!COLOR_LABLE_MAP.get(this._backgroundColor) ?
         COLOR_LABLE_MAP.get(this._backgroundColor) : this._backgroundColor;
   }

   set backgroundColor(value: string) {
      if(value !== this._backgroundColor) {
         this._backgroundColor = value;
         this.vsObjectFormat.backgroundColorType = value;
         this.updateFormat();
      }
   }

   get backgroundColorType(): number {
      return this._backgroundColorType;
   }

   set backgroundColorType(value: number) {
      if(value !== this._backgroundColorType) {
         this._backgroundColorType = value;

         if(value === ComboMode.VALUE) {
            this.backgroundColor = ColorDropdown.STATIC;
         }
      }
   }

   @Input()
   set format(format: FormatInfoModel | VSObjectFormatInfoModel) {
      this._format = format;

      if(format && (format as any).type &&
         (format as any).type.indexOf("VSObjectFormatInfoModel") != -1)
      {
         this.vsObjectFormat = <VSObjectFormatInfoModel> format;
         this._color = this.vsObjectFormat.colorType;
         this._colorType = this.getComboMode(this._color);
         this._backgroundColor = this.vsObjectFormat.backgroundColorType;
         this._backgroundColorType = this.getComboMode(this._backgroundColor);
      }
      else {
         this._color = null;
         this._colorType = ComboMode.VALUE;
         this._backgroundColor = null;
         this._backgroundColorType = ComboMode.VALUE;
      }
   }

   constructor(private changeDetectorRef: ChangeDetectorRef,
               private fontService: FontService, private modelService: ModelService,
               private modalService: NgbModal, private debounceService: DebounceService)
   {
   }

   ngOnInit(): void {
      this.fontService.getAllFonts().subscribe(
         (fonts: string[]) => this.fonts = fonts
      );
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inactive) {
         this.changeDetectorRef.detach();
      }
      else {
         this.changeDetectorRef.reattach();
      }

      this.updateProperties();
   }

   getFont(): string {
      return FormatTool.getFontString(this._format);
   }

   getAlignment(): string {
      return FormatTool.getAlignmentString(this._format, this.isHAlignmentEnabled(),
                                           this.isVAlignmentEnabled());
   }

   getFormat(): string {
      return FormatTool.getFormatString(this._format);
   }

   getColorLabel(val: string): string {
      return Tool.isDynamic(val) ? val : "_#(js:Static)";
   }

   changeColor(color: string, colorType: string) {
      if(this.vsObjectFormat[colorType] != color) {
         this.vsObjectFormat[colorType] = color;
         this.updateFormat();
      }
   }

   closeFormat(event: MouseEvent) {
      this.onCloseFormat.emit("");
   }

   isFormatDisabled(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length > 0) {
         return !!this._focusedAssemblies.find((object) => {
            return object.objectType === "VSViewsheet" ||
               object.objectType === "VSThermometer" ||
               object.objectType === "VSSlidingScale" ||
               object.objectType === "VSCylinder";
         });
      }

      return this.viewer;
   }

   private isViewsheetSelected(): boolean {
      return !this.viewer && (!this._focusedAssemblies || !this._focusedAssemblies.length);
   }

   isValueFillVisible(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length > 0) {
         return !!this._focusedAssemblies.find(
            obj => obj.objectType == "VSGauge" &&
               ((<VSGaugeModel> obj).face == 10910 ||
                (<VSGaugeModel> obj).face == 10920 ||
                (<VSGaugeModel> obj).face == 90820));
      }

      return false;
   }

   private isNonEditableChartVOSelected(object: VSObjectModel): boolean {
      if(object.objectType === "VSChart") {
         const model = <VSChartModel> object;

         if(model.chartType == GraphTypes.CHART_CIRCLE_PACKING) {
            const chartSelection: ChartSelection = model.chartSelection;
            const regions: ChartRegion[] = chartSelection ?  chartSelection.regions : null;

            if(regions && regions.length > 0) {
               const vos = regions.filter(r => ChartTool.areaType(model, r) == "vo");

               if(model.chartType == GraphTypes.CHART_CIRCLE_PACKING) {
                  if(ChartTool.isRegionAreaTypePresent(model, model.chartSelection, "text")) {
                     return false;
                  }

                  // innermost circle is same as selecting the text label.
                  const innermost = model.axisFields[model.axisFields.length - 1];
                  const innermostSelected =
                     vos.some(r => model.stringDictionary[ChartTool.meaIdx(model, r)] == innermost);
                  return !innermostSelected;
               }

               return vos.length > 0;
            }

         }
         else {
            return ChartTool.isNonEditableVOSelected(model);
         }
      }

      return false;
   }

   updateFormat(): void {
      this.debounceService.debounce(`UpdateVSFormat.${this.vsId}`, () => {
         this.onChangeFormat.emit(this._format);
      }, 50);
   }

   getBorderLabel(): string {
      let isDefaultBorderStyle = this._format && this._format.borderTopStyle == null
         && this._format.borderLeftStyle == null
         && this._format.borderBottomStyle == null
         && this._format.borderRightStyle == null;

      return isDefaultBorderStyle ? "_#(js:Default)" : "_#(js:Custom)";
   }

   isFontDisabled(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         return !!this._focusedAssemblies.find((object) => {
            // disable if vo (circle packing)
            // disabled if image or shape is focused
            return object.objectType == "VSImage" || object.objectType == "VSLine" ||
               object.objectType == "VSRectangle" || object.objectType == "VSOval" ||
               this.measureBarSelected(object) || this.isNonEditableChartVOSelected(object);
         });
      }

      return !this.viewer;
   }

   private isEditDisabled(object: VSObjectModel, allowChart: boolean): boolean {
      if(object.objectType == "VSChart" && !(<VSChartModel> object).titleSelected) {
         const chart: VSChartModel = <VSChartModel> object;
         const chartSelection: ChartSelection = chart.chartSelection;
         const regions = chartSelection ?  chartSelection.regions : null;

         if(regions && regions.some(r => ChartTool.areaType(<VSChartModel> object, r) == "vo")) {
            return true;
         }

         if(chart.wordCloud) {
            return false;
         }

         return !allowChart && (!regions || regions.length == 0);
      }

      return false;
   }

   isFormattingDisabled(): boolean {
      if(this._format && !this._format.formatEnabled) {
         return true;
      }

      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         if(this.isChartEditableSelected()) {
            return false;
         }

         //disabled if textInput, calendar comboBox, or shape is focused
         return !!this._focusedAssemblies.find((object) => {
            return object.objectType == "VSTextInput" || object.objectType == "VSLine" ||
               object.objectType == "VSGroupContainer" || object.objectType == "VSTab" ||
               object.objectType == "VSRectangle" || object.objectType == "VSOval" ||
               this.measureBarSelected(object) || this.isEditDisabled(object, false);
         });
      }

      return !this.viewer;
   }

   private isChartEditableSelected(): boolean {
      // editing text/textField. (60545)
      if(this._format && (<VSObjectFormatInfoModel> this._format).cssType == "ChartPlotLabels") {
         return true;
      }

      return !!this._focusedAssemblies.find((object) =>
         object.objectType == "VSChart" && !this.isNonEditableChartVOSelected(object));
   }

   isAlignDisabled(): boolean {
      if(this.vsObjectFormat.alignEnabled) {
         return false;
      }

      if(!this.isHAlignmentEnabled() && !this.isVAlignmentEnabled()) {
         return true;
      }

      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         return this._focusedAssemblies.every((object) => {
            if(object.objectType == "VSLine" || object.objectType == "VSRectangle"
               || object.objectType == "VSOval")
            {
               return true;
            }

            return this.measureBarSelected(object) || this.isNonEditableChartVOSelected(object);
         });
      }

      return !this.viewer;
   }

   isHAlignmentEnabled(): boolean {
      return this._format ? this._format.halignmentEnabled : true;
   }

   isVAlignmentEnabled(): boolean {
      if(this._format && !this._format.valignmentEnabled) {
         return false;
      }

      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         //enabled if no textInput objects
         return !this._focusedAssemblies.find((object) => {
            return object.objectType == "VSTextInput" ||
               object.objectType == "VSComboBox";
         });
      }

      return this.viewer;
   }

   isWrapTextDisabled(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         return !!this._focusedAssemblies.find((object) => {
            if(object.objectType == "VSChart" && !(<VSChartModel> object).titleSelected ||
               object.objectType == "VSTab" || object.objectType == "VSTextInput")
            {
               return true;
            }

            return this.isAlignDisabled();
         });
      }

      return this.isAlignDisabled();
   }

   isBorderDisabled(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         //disabled if shape or chart legend/axis
         return !!this._focusedAssemblies.find((object) => {
            if(object.objectType == "VSLine" || object.objectType == "VSRectangle" ||
               object.objectType == "VSOval") {
               return true;
            }
            else if(object.objectType == "VSChart" && !(<VSChartModel> object).titleSelected) {
               const chart = <VSChartModel> object;
               const chartSelection: ChartSelection = chart.chartSelection;
               return this.vsObjectFormat.borderDisabled ? true :
                   chartSelection && chartSelection.chartObject &&
                  (chartSelection.chartObject.areaName != "plot_area" ||
                   ChartTool.isRegionAreaTypePresent(chart, chartSelection, "text") ||
                   ChartTool.isRegionAreaTypePresent(chart, chartSelection, "vo") ||
                   ChartTool.isRegionAreaTypePresent(chart, chartSelection, "label") ||
                   ChartTool.isRegionAreaTypePresent(chart, chartSelection, "axis"));
            }
            else if(object.objectType == "VSCalendar" &&
               this.vsObjectFormat.cssType == "CalendarDays")
            {
               return true;
            }

            return this.measureBarSelected(object);
         });
      }

      return !this.viewer;
   }

   isRoundCornerDisabled(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         const rect = this._focusedAssemblies.find((object) => {
            return object.objectType == "VSRectangle";
         });

         if(!!rect) {
            return false;
         }

         if(this._focusedAssemblies.length == 1) {
            let object: VSObjectModel = this._focusedAssemblies[0];

            if(object.objectType == "VSTable" || object.objectType == "VSCalcTable" ||
               object.objectType == "VSCrosstab")
            {
               return (<BaseTableModel> object).selectedData != null ||
                  (<BaseTableModel> object).selectedHeaders != null;
            }
            else if(this.selectedDetailCell(object)) {
               return true;
            }
         }
      }

      if(this.isBorderDisabled() || this.vsObjectFormat.cssType == "CalendarHeader") {
         return true;
      }

      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         return false;
      }

      return !this.viewer;
   }

   isColorDisabled(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         //disabled if image
         return this._focusedAssemblies.some(
            (object) => object.objectType === "VSImage" ||
               this.isNonEditableChartVOSelected(object));
      }

      return !this.viewer;
   }

   isBackgroundDisabled(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         return this._focusedAssemblies.some(
            (object) => object.objectType === "VSLine");
      }

      return this._format && (<any>this._format).type == null;
   }

   isDynamicColorDisabled(): boolean {
      if(this.vsObjectFormat.dynamicColorDisabled) {
         return true;
      }

      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         //disabled if chart legend/axis
         return !!this._focusedAssemblies.find((object) => {
            if(object.objectType == "VSChart") {
               let chart = <VSChartModel> object;

               if(chart.chartSelection && chart.chartSelection.chartObject &&
                  chart.chartSelection.chartObject.areaName !== "plot_area") {
                  return true;
               }
               else if(chart.chartSelection && chart.chartSelection.chartObject &&
                       chart.chartSelection.chartObject.areaName === "plot_area")
               {
                  if(chart.chartSelection.regions && chart.chartSelection.regions.length > 0) {
                     for(let region of chart.chartSelection.regions) {
                        if(ChartTool.areaType(chart, region) === "axis" ||
                           // Bug #18678, disable color for target and plot labels
                           ChartTool.areaType(chart, region) === "label" ||
                           // disable for circle packing circles
                           ChartTool.areaType(chart, region) === "vo" ||
                           ChartTool.areaType(chart, region) === "text")
                        {
                           return true;
                        }
                     }
                  }
                  // whole chart select, dynamic color doesn't work
                  else if(this.viewer) {
                     return true;
                  }
               }
            }

            return false;
         });
      }

      return !this.viewer;
   }

   getCSSLabel(): string {
      if(!this._format) {
         return "_#(js:None)";
      }

      let css: string = this.vsObjectFormat.cssID ? "#" + this.vsObjectFormat.cssID + " " : "";

      if(this.vsObjectFormat.cssClass) {
         css += "." + Tool.replaceStr(this.vsObjectFormat.cssClass, ",", ".");
      }

      css = css.length > 0 ? css : "_#(js:None)";
      return css;
   }

   isCSSDisabled(): boolean {
      if(this.viewer) {
         return true;
      }

      if(this._focusedAssemblies && this._focusedAssemblies.length) {
         if(this._focusedAssemblies.length == 1) {
            let object: VSObjectModel = this._focusedAssemblies[0];

            if(object.objectType == "VSChart" && this.isNonEditableChartVOSelected(object)) {
               return true;
            }

            if(object.objectType == "VSTable" || object.objectType == "VSCalcTable" ||
               object.objectType == "VSCrosstab")
            {
               return (<BaseTableModel> object).selectedData != null ||
                  (<BaseTableModel> object).selectedHeaders != null;
            }

            return false;
         }
         else if(this._focusedAssemblies.length == 2 &&
            this._focusedAssemblies[1].objectType == "VSSelectionContainer" &&
            this._focusedAssemblies[0].container == this._focusedAssemblies[1].absoluteName)
         {
            return false;
         }
         else {
            return true;
         }
      }

      return this._format && (<any>this._format).type == null;
   }

   updateCSS(value: string, isID: boolean) {
      if(!value || value == "") {
         value = null;
      }

      if(isID) {
         if(this.vsObjectFormat.cssID == value) {
            return;
         }

         this.vsObjectFormat.cssID = value;
      }
      else {
         if(this.vsObjectFormat.cssClass == value) {
            return;
         }

         this.vsObjectFormat.cssClass = value;
      }
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }

   showPresenter(): boolean {
      if(this._focusedAssemblies && this._focusedAssemblies.length == 1) {
         let object: VSObjectModel = this._focusedAssemblies[0];

         if(object.objectType == "VSTable" || object.objectType == "VSCalcTable" ||
            object.objectType == "VSCrosstab")
         {
            return !(<BaseTableModel> object).titleSelected &&
               ((<BaseTableModel> object).firstSelectedRow >= 0 ||
                (<BaseTableModel> object).firstSelectedColumn >= 0);
         }
         else if(object.objectType == "VSText") {
            return true;
         }

         return false;
      }

      return false;
   }

   updatePresenter(value: any): void {
      this.vsObjectFormat.presenterLabel = value.label;
      this.vsObjectFormat.presenter = value.presenter;
      this.vsObjectFormat.presenterHasDescriptors = value.hasDescriptors;
      this.updateFormat();
   }

   updatePresenterProperties(data: [string, PresenterPropertyDialogModel]): void {
      this.onPresenterPropertiesChange.emit(data);
   }

   private getComboMode(value: string): number {
      if(value) {
         if(value.startsWith("=")) {
            return ComboMode.EXPRESSION;
         }
         else if(value.startsWith("$")) {
            return ComboMode.VARIABLE;
         }
      }

      return ComboMode.VALUE;
   }

   private measureBarSelected(object: VSObjectModel): boolean {
      return (object.objectType === "VSSelectionList" || object.objectType === "VSSelectionTree") &&
             object.selectedRegions && object.selectedRegions.some(region =>
                region.path[0] == "Measure Bar" || region.path[0] == "Measure Bar(-)"
             );
   }

   private selectedDetailCell(object: VSObjectModel) {
      return (object.objectType === "VSSelectionList" || object.objectType === "VSSelectionTree" ||
            object.objectType === "VSCheckBox" || object.objectType === "VSRadioButton") &&
         object.selectedRegions && object.selectedRegions.some(region =>
            region.type == TableDataPathTypes.DETAIL);
   }

   updateProperties(): void {
      this.formatDisabled = this.formatPaneDisabled || this.isFormatDisabled();
      this.fontDisabled = this.isFontDisabled();
      this.formattingDisabled = this.isFormattingDisabled();
      this.colorDisabled = this.isColorDisabled();
      this.backgroundDisabled = this.isBackgroundDisabled();
      this.dynamicColorDisabled = this.isDynamicColorDisabled();
      this.alignDisabled = this.isAlignDisabled();
      this.wrapTextDisabled = this.isWrapTextDisabled();
      this.borderDisabled = this.isBorderDisabled();
      this.roundCornerDisabled = this.isRoundCornerDisabled();
      this.roundTopCornersOnlyVisible = this.isRoundTopCornersOnlyVisible();
      this.cssDisabled = this.isCSSDisabled();
      this.vsSelected = this.isViewsheetSelected();
   }

   // Reset current format, mimic of FormatDialog_Script.as => clearClicked()
   reset(): void {
      const message = "_#(js:viewer.viewsheet.format.resetConfirm)";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((buttonClicked) => {
            if(buttonClicked === "yes") {
               this.onChangeFormat.emit(null);
            }
         });
   }

   public openPresenterPropertyDialog(): void {
      let eventUri: string;
      let modelUri: string;

      if(this.tableSelected) {
         const table: BaseTableModel = <BaseTableModel> this.focusedAssemblies[0];
         modelUri = "../api/" + PRESENTER_PROPERTY_URI +
            Tool.encodeURIPath(table.absoluteName) + "/" +
            table.firstSelectedRow + "/" +
            table.firstSelectedColumn + "/" +
            this.vsObjectFormat.presenter + "/" + false + "/" + 0 + "/" +
            Tool.byteEncode(this.vsId);
         eventUri = "/events/" + PRESENTER_PROPERTY_URI + table.absoluteName
            + "/" + table.firstSelectedRow + "/" + table.firstSelectedColumn
            + "/" + false + "/" + 0;
      }
      else if(this.textSelected) {
         const text: VSTextModel = <VSTextModel> this.focusedAssemblies[0];
         modelUri = "../api/" + PRESENTER_PROPERTY_URI + Tool.encodeURIPath(text.absoluteName)
            + "/" + 0 + "/" + 0 + "/" + this.vsObjectFormat.presenter + "/"
            + this.layout + "/" + this.layoutRegion + "/"
            + this.vsId;
         eventUri = "/events/" + PRESENTER_PROPERTY_URI + text.absoluteName
            + "/" + 0 + "/" + 0 + "/" + this.layout + "/" + this.layoutRegion;
      }

      this.modelService.getModel(modelUri).toPromise().then(
         (data: any) => {
            this.presenterPropertyDialogModel = <PresenterPropertyDialogModel> data;
            const options: NgbModalOptions = {
               windowClass: "property-dialog-window"
            };

            this.modalService.open(this.presenterPropertyDialog, options).result.then(
               (result: PresenterPropertyDialogModel) => {
                  this.presenterPropertyDialogModel = null;
                  this.updatePresenterProperties([eventUri, result]);
               },
               (reason: string) => {
                  this.presenterPropertyDialogModel = null;
               }
            );
         },
         (error: any) => {
            console.error("Failed to load presenter property model: ", error);
         }
      );
   }

   get tableSelected(): boolean {
      return this.focusedAssemblies && this.focusedAssemblies.length > 0
         && (this.focusedAssemblies[0].objectType === "VSTable"
            || this.focusedAssemblies[0].objectType === "VSCalcTable"
            || this.focusedAssemblies[0].objectType === "VSCrosstab");
   }

   get textSelected(): boolean {
      return this.focusedAssemblies && this.focusedAssemblies.length > 0
         && this.focusedAssemblies[0].objectType === "VSText";
   }

   private get shapeSelected(): boolean {
      return this.focusedAssemblies && this.focusedAssemblies.length > 0
         && (this.focusedAssemblies[0].objectType === "VSLine"
            || this.focusedAssemblies[0].objectType === "VSRectangle"
            || this.focusedAssemblies[0].objectType === "VSOval");
   }

   get borderTooltip(): string {
      return this.shapeSelected ? "_#(js:vs.format.shapeBorder)" : null;
   }

   get roundCornerMax(): number {
      let min = Number.MAX_VALUE;

      if(this.focusedAssemblies && this.focusedAssemblies.length > 0) {
         for(let assembly of this.focusedAssemblies) {
            min = Math.min(min, assembly.objectFormat.height, assembly.objectFormat.width);
         }
      }

      return min == Number.MAX_VALUE ? 20 : min;
   }

   private isRoundTopCornersOnlyVisible(): boolean {
      // show only when non-active tab format is selected since this property will apply to
      // the whole tab component, and it may be confusing otherwise since you can't
      // set a separate value for active/non-active tabs
      if(this._focusedAssemblies && this._focusedAssemblies.length > 0) {
         return !!this._focusedAssemblies.find(obj => obj.objectType == "VSTab" &&
            (!obj.selectedRegions || obj.selectedRegions.length === 0));
      }

      return false;
   }
}
