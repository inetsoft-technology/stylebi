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
import { Injectable } from "@angular/core";
import { VSAnnotationModel } from "../model/annotation/vs-annotation-model";
import { VSCalendarModel } from "../model/calendar/vs-calendar-model";
import { VSCalcTableModel } from "../model/vs-calctable-model";
import { VSChartModel } from "../model/vs-chart-model";
import { VSCheckBoxModel } from "../model/vs-check-box-model";
import { VSComboBoxModel } from "../model/vs-combo-box-model";
import { VSCrosstabModel } from "../model/vs-crosstab-model";
import { VSGaugeModel } from "../model/output/vs-gauge-model";
import { VSGroupContainerModel } from "../model/vs-group-container-model";
import { VSImageModel } from "../model/output/vs-image-model";
import { VSLineModel } from "../model/vs-line-model";
import { VSObjectModel } from "../model/vs-object-model";
import { VSOvalModel } from "../model/vs-oval-model";
import { VSRadioButtonModel } from "../model/vs-radio-button-model";
import { VSRangeSliderModel } from "../model/vs-range-slider-model";
import { VSRectangleModel } from "../model/vs-rectangle-model";
import { VSSelectionContainerModel } from "../model/vs-selection-container-model";
import { VSSelectionListModel } from "../model/vs-selection-list-model";
import { VSSelectionTreeModel } from "../model/vs-selection-tree-model";
import { VSSliderModel } from "../model/vs-slider-model";
import { VSSpinnerModel } from "../model/vs-spinner-model";
import { VSSubmitModel } from "../model/output/vs-submit-model";
import { VSTabModel } from "../model/vs-tab-model";
import { VSTableModel } from "../model/vs-table-model";
import { VSTextInputModel } from "../model/vs-text-input-model";
import { VSTextModel } from "../model/output/vs-text-model";
import { VSUploadModel } from "../model/vs-upload-model";
import { VSViewsheetModel } from "../model/vs-viewsheet-model";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { AnnotationActions } from "./annotation-actions";
import { CalcTableActions } from "./calc-table-actions";
import { CalendarActions } from "./calendar-actions";
import { ChartActions } from "./chart-actions";
import { CheckBoxActions } from "./check-box-actions";
import { ComboBoxActions } from "./combo-box-actions";
import { CrosstabActions } from "./crosstab-actions";
import { CurrentSelectionActions } from "./current-selection-actions";
import { GaugeActions } from "./gauge-actions";
import { GroupContainerActions } from "./group-container-actions";
import { ImageActions } from "./image-actions";
import { LineActions } from "./line-actions";
import { OvalActions } from "./oval-actions";
import { RadioButtonActions } from "./radio-button-actions";
import { RangeSliderActions } from "./range-slider-actions";
import { RectangleActions } from "./rectangle-actions";
import { SelectionContainerActions } from "./selection-container-actions";
import { SelectionListActions } from "./selection-list-actions";
import { SelectionTreeActions } from "./selection-tree-actions";
import { SliderActions } from "./slider-actions";
import { SpinnerActions } from "./spinner-actions";
import { SubmitActions } from "./submit-actions";
import { TabActions } from "./tab-actions";
import { TableActions } from "./table-actions";
import { TextActions } from "./text-actions";
import { TextInputActions } from "./text-input-actions";
import { UploadActions } from "./upload-actions";
import { ViewsheetActions } from "./viewsheet-actions";
import { ContextProvider } from "../context-provider.service";
import { CylinderActions } from "./cylinder-actions";
import { VSCylinderModel } from "../model/output/vs-cylinder-model";
import { SlidingScaleActions } from "./sliding-scale-actions";
import { VSSlidingScaleModel } from "../model/output/vs-sliding-scale-model";
import { ThermometerActions } from "./thermometer-actions";
import { VSThermometerModel } from "../model/output/vs-thermometer-model";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { FeatureFlagsService } from "../../../../../shared/feature-flags/feature-flags.service";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

@Injectable()
export class AssemblyActionFactory {
   get viewer(): boolean {
      return this.contextProvider.viewer;
   }

   get composer(): boolean {
      return this.contextProvider.composer;
   }

   get binding(): boolean {
      return this.contextProvider.binding;
   }

   get preview(): boolean {
      return this.contextProvider.preview;
   }

   get composerBinding(): boolean {
      return this.contextProvider.composerBinding;
   }

   securityEnabled: boolean = false;
   stateProvider: ActionStateProvider = null;

   constructor(private contextProvider: ContextProvider,
               private dataTipService: DataTipService,
               private popService: PopComponentService,
               private miniToolbarService: MiniToolbarService) {
   }

   createActions(model: VSObjectModel): AbstractVSActions<VSObjectModel> {
      if(!model) {
         return null;
      }

      switch(model.objectType) {
      case "VSAnnotation":
         return new AnnotationActions(
            <VSAnnotationModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSCalcTable":
         return new CalcTableActions(
            <VSCalcTableModel> model, this.contextProvider, this.securityEnabled,
            this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSCalendar":
         return new CalendarActions(
            <VSCalendarModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSChart":
         return new ChartActions(
            <VSChartModel> model, this.popService, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.miniToolbarService);
      case "VSCheckBox":
         return new CheckBoxActions(
            <VSCheckBoxModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSComboBox":
         return new ComboBoxActions(
            <VSComboBoxModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSCrosstab":
         return new CrosstabActions(
            <VSCrosstabModel> model, this.contextProvider, this.securityEnabled,
            this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSCylinder":
         return new CylinderActions(
            <VSCylinderModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSGauge":
         return new GaugeActions(
            <VSGaugeModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSGroupContainer":
         return new GroupContainerActions(
            <VSGroupContainerModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSImage":
         return new ImageActions(
            <VSImageModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSLine":
         return new LineActions(
            <VSLineModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSOval":
         return new OvalActions(
            <VSOvalModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSRadioButton":
         return new RadioButtonActions(
            <VSRadioButtonModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSRangeSlider":
         return new RangeSliderActions(
            <VSRangeSliderModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSRectangle":
         return new RectangleActions(
            <VSRectangleModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSSelectionContainer":
         return new SelectionContainerActions(
               <VSSelectionContainerModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSSelectionList":
         return new SelectionListActions(
            <VSSelectionListModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSSelectionTree":
         return new SelectionTreeActions(
            <VSSelectionTreeModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSSlider":
         return new SliderActions(
            <VSSliderModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSSlidingScale":
         return new SlidingScaleActions(
            <VSSlidingScaleModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSSpinner":
         return new SpinnerActions(
            <VSSpinnerModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSSubmit":
         return new SubmitActions(
            <VSSubmitModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSTab":
         return new TabActions(
            <VSTabModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSTable":
         return new TableActions(
            <VSTableModel> model, this.contextProvider, this.securityEnabled, this.stateProvider,
            this.dataTipService, this.popService, this.miniToolbarService);
      case "VSText":
         return new TextActions(
            <VSTextModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSTextInput":
         return new TextInputActions(
            <VSTextInputModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSThermometer":
         return new ThermometerActions(
            <VSThermometerModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSUpload":
         return new UploadActions(
            <VSUploadModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      case "VSViewsheet":
         return new ViewsheetActions(
            <VSViewsheetModel> model, this.contextProvider,
            this.securityEnabled, this.stateProvider, this.dataTipService, this.popService, this.miniToolbarService);
      default:
         console.warn("Unsupported assembly type: " + model.objectType);
      }

      return null;
   }

   createCurrentSelectionActions(model: VSSelectionContainerModel): CurrentSelectionActions {
      return new CurrentSelectionActions(
         model, this.contextProvider,
         this.securityEnabled, this.stateProvider, this.dataTipService, this.popService);
   }
}
