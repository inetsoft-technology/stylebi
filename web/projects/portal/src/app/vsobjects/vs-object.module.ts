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
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import {
   NgbDatepickerModule,
   NgbDropdownModule,
   NgbModal,
   NgbNavModule,
   NgbProgressbarModule,
   NgbTooltipModule,
   NgbTypeaheadModule
} from "@ng-bootstrap/ng-bootstrap";
import { ScrollingModule } from "@angular/cdk/scrolling";
import { CkeditorWrapperModule } from "../../../../shared/ckeditor-wrapper/ckeditor-wrapper.module";
import { FeatureFlagsModule } from "../../../../shared/feature-flags/feature-flags.module";
import { AngularResizeEventModule } from "../../../../shared/resize-event/angular-resize-event.module";
import { BindingModule } from "../binding/binding.module";
import { BindingService } from "../binding/services/binding.service";
import { ChartEditorService } from "../binding/services/chart/chart-editor.service";
import { VSChartEditorService } from "../binding/services/chart/vs-chart-editor.service";
import { VSBindingService } from "../binding/services/vs-binding.service";
import { DndService } from "../common/dnd/dnd.service";
import { VSDndService } from "../common/dnd/vs-dnd.service";
import { FileUploadService } from "../common/services/file-upload.service";
import { FullScreenService } from "../common/services/full-screen.service";
import { ComposerRecentService } from "../composer/gui/composer-recent.service";
import { ScriptService } from "../composer/gui/script/script.service";
import { FormatModule } from "../format/format.module";
import { GraphModule } from "../graph/graph.module";
import { StatusBarModule } from "../status-bar/status-bar.module";
import { AutoCompleteModule } from "../widget/auto-complete/auto-complete.module";
import { ColorPickerModule } from "../widget/color-picker/color-picker.module";
import { ConditionModule } from "../widget/condition/condition.module";
import { DateTypeEditorModule } from "../widget/date-type-editor/date-type-editor.module";
import { AdditionalTableSelectionPaneModule } from "../widget/dialog/additional-table-selection-pane/additional-table-selection-pane.module";
import { AutoDrillDialogModule } from "../widget/dialog/auto-drill-dialog/auto-drill-dialog.module";
import { TipCustomizeDialogModule } from "../widget/dialog/tip-customize-dialog/tip-customize-dialog.module";
import { VariableInputDialogModule } from "../widget/dialog/variable-input-dialog/variable-input-dialog.module";
import { VariableListDialogModule } from "../widget/dialog/variable-list-dialog/variable-list-dialog.module";
import { VSAssemblyScriptPaneModule } from "../widget/dialog/vsassembly-script-pane/vsassembly-script-pane.module";
import { WidgetDirectivesModule } from "../widget/directive/widget-directives.module";
import { DropdownViewModule } from "../widget/dropdown-view/dropdown-view.module";
import { DynamicComboBoxModule } from "../widget/dynamic-combo-box/dynamic-combo-box.module";
import { EmailDialogModule } from "../widget/email-dialog/email-dialog.module";
import { FixedDropdownModule } from "../widget/fixed-dropdown/fixed-dropdown.module";
import { FontPaneModule } from "../widget/font-pane/font-pane.module";
import { WidgetFormatModule } from "../widget/format/widget-format.module";
import { GenericSelectableListModule } from "../widget/generic-selectable-list/generic-selectable-list.module";
import { HelpLinkModule } from "../widget/help-link/help-link.module";
import { HighlightModule } from "../widget/highlight/highlight.module";
import { InteractModule } from "../widget/interact/interact.module";
import { LargeFormFieldModule } from "../widget/large-form-field/large-form-field.module";
import { ModalHeaderModule } from "../widget/modal-header/modal-header.module";
import { MouseEventModule } from "../widget/mouse-event/mouse-event.module";
import { NotificationsModule } from "../widget/notifications/notifications.module";
import { PipeModule } from "../widget/pipe/pipe.module";
import { PlaceholderDragElementModule } from "../widget/placeholder-drag-element/placeholder-drag-element.module";
import { PresenterModule } from "../widget/presenter/presenter.module";
import { RepositoryTreeModule } from "../widget/repository-tree/repository-tree.module";
import { WidgetScheduleModule } from "../widget/schedule/widget-schedule.module";
import { ScrollModule } from "../widget/scroll/scroll.module";
import { FontService } from "../widget/services/font.service";
import { ModelService } from "../widget/services/model.service";
import { ShareModule } from "../widget/share/share.module";
import { SimpleTableModule } from "../widget/simple-table/simple-table.module";
import { SlideOutModule } from "../widget/slide-out/slide-out.module";
import { TableStyleModule } from "../widget/table-style/table-style.module";
import { TooltipModule } from "../widget/tooltip/tooltip.module";
import { TreeModule } from "../widget/tree/tree.module";
import { VsBookmarkPaneComponent } from "./bookmark/vs-bookmark-pane.component";
import { AddFilterDialog } from "./dialog/add-filter-dialog.component";
import { AlignmentPane } from "./dialog/alignment-pane.component";
import { AnnotationFormatDialog } from "./dialog/annotation/annotation-format-dialog.component";
import { BaseResizeableDialogComponent } from "./dialog/base-resizeable-dialog.component";
import { BasicGeneralPane } from "./dialog/basic-general-pane.component";
import { BookmarkPropertyDialog } from "./dialog/bookmark-property-dialog.component";
import { CalcTableAdvancedPane } from "./dialog/calc-table-advanced-pane.component";
import { CalcTablePropertyDialog } from "./dialog/calc-table-property-dialog.component";
import { ColumnOptionDialog } from "./dialog/column-option-dialog.component";
import { ComboBoxEditor } from "./dialog/combo-box-editor.component";
import { CrosstabAdvancedPane } from "./dialog/crosstab-advanced-pane.component";
import { CrosstabPropertyDialog } from "./dialog/crosstab-property-dialog.component";
import { CSSPane } from "./dialog/css-pane.component";
import { HierarchyContentPane } from "./dialog/cube/hierarchy-content-pane.component";
import { HierarchyEditor } from "./dialog/cube/hierarchy-editor.component";
import { HierarchyPropertyPane } from "./dialog/cube/hierarchy-property-pane.component";
import { DataTreeValidatorService } from "./dialog/data-tree-validator.service";
import { DateComparisonCustomPeriodsComponent } from "./dialog/date-comparison-dialog/date-comparison-custom-periods.component";
import { DateComparisonDialog } from "./dialog/date-comparison-dialog/date-comparison-dialog.component";
import { DateComparisonIntervalPaneComponent } from "./dialog/date-comparison-dialog/date-comparison-interval-pane.component";
import { DateComparisonPaneComponent } from "./dialog/date-comparison-dialog/date-comparison-pane.component";
import { DateComparisonPeriodsPaneComponent } from "./dialog/date-comparison-dialog/date-comparison-periods-pane.component";
import { DateComparisonSharePaneComponent } from "./dialog/date-comparison-dialog/date-comparison-share-pane.component";
import { DateComparisonStandardPeriodsComponent } from "./dialog/date-comparison-dialog/date-comparison-standard-periods.component";
import { DateEditor } from "./dialog/date-editor.component";
import { EmailDialog } from "./dialog/email/email-dialog.component";
import { ExportDialog } from "./dialog/export-dialog.component";
import { FileFormatPane } from "./dialog/file-format-pane.component";
import { FloatEditor } from "./dialog/float-editor.component";
import { GeneralPropPane } from "./dialog/general-prop-pane.component";
import { ChartAdvancedPane } from "./dialog/graph/chart-advanced-pane.component";
import { ChartGeneralPane } from "./dialog/graph/chart-general-pane.component";
import { ChartPropertyDialog } from "./dialog/graph/chart-property-dialog.component";
import { HyperlinkDialog } from "./dialog/graph/hyperlink-dialog.component";
import { TipPane } from "./dialog/graph/tip-pane.component";
import { HideColumnsDialog } from "./dialog/hide-columns-dialog.component";
import { HighlightDialog } from "./dialog/highlight-dialog.component";
import { ImageFormatSelectComponent } from "./dialog/image-format-select.component";
import { InputParameterDialog } from "./dialog/input-parameter-dialog.component";
import { IntegerEditor } from "./dialog/integer-editor.component";
import { PaddingPane } from "./dialog/padding-pane.component";
import { ProfilingDataPaneComponent } from "./dialog/profiling-data-pane.component";
import { ProfilingDialog } from "./dialog/profiling-dialog.component";
import { RangeSliderAdvancedPane } from "./dialog/range-slider-advanced-pane.component";
import { RangeSliderDataPane } from "./dialog/range-slider-data-pane.component";
import { RangeSliderEditDialog } from "./dialog/range-slider-edit-dialog.component";
import { RangeSliderGeneralPane } from "./dialog/range-slider-general-pane.component";
import { RangeSliderPropertyDialog } from "./dialog/range-slider-property-dialog.component";
import { RangeSliderSizePane } from "./dialog/range-slider-size-pane.component";
import { RemoveBookmarksDialog } from "./dialog/remove-bookmarks-dialog.component";
import { CKEditorRichTextService } from "./dialog/rich-text-dialog/ckeditor-rich-text.service";
import { RichTextDialog } from "./dialog/rich-text-dialog/rich-text-dialog.component";
import { RichTextService } from "./dialog/rich-text-dialog/rich-text.service";
import { ScheduleDialog } from "./dialog/schedule-dialog.component";
import { SelectionListDialog } from "./dialog/selection-list-dialog.component";
import { SelectionListEditor } from "./dialog/selection-list-editor.component";
import { SizePositionPane } from "./dialog/size-position-pane.component";
import { SliderLabelPane } from "./dialog/slider-label-pane.component";
import { TableAdvancedPane } from "./dialog/table-advanced-pane.component";
import { TableViewGeneralPane } from "./dialog/table-view-general-pane.component";
import { TableViewPropertyDialog } from "./dialog/table-view-property-dialog.component";
import { TextEditor } from "./dialog/text-editor.component";
import { TitlePropPane } from "./dialog/title-prop-pane.component";
import { VSConditionDialog } from "./dialog/vs-condition-dialog.component";
import { VSFormatPane } from "./dialog/vs-format-pane.component";
import { VSObjectDirectivesModule } from "./directives/vs-object-directives.module";
import { VSFormatsPane } from "./format/vs-formats-pane.component";
import { AppErrorMessage } from "./objects/app-error-message.component";
import { MonthCalendar } from "./objects/calendar/month-calendar.component";
import { VSCalendar } from "./objects/calendar/vs-calendar.component";
import { YearCalendar } from "./objects/calendar/year-calendar.component";
import { VSChartModule } from "./objects/chart/vs-chart.module";
import { VSCheckBox } from "./objects/check-box/vs-check-box.component";
import { VSComboBox } from "./objects/combo-box/vs-combo-box.component";
import { VSCylinder } from "./objects/cylinder/vs-cylinder.component";
import { DataTipDirectivesModule } from "./objects/data-tip/data-tip-directives.module";
import { VSGroupContainer } from "./objects/group/vs-group-container.component";
import { MiniToolbarModule } from "./objects/mini-toolbar/mini-toolbar.module";
import { MiniToolbarService } from "./objects/mini-toolbar/mini-toolbar.service";
import { VSGauge } from "./objects/output/gauge/vs-gauge.component";
import { VSImage } from "./objects/output/image/vs-image.component";
import { VSText } from "./objects/output/text/vs-text.component";
import { VSPageBreak } from "./objects/page-break/vs-page-break.component";
import { VSRadioButton } from "./objects/radio-button/vs-radio-button.component";
import { VSRangeSlider } from "./objects/range-slider/vs-range-slider.component";
import { CollapseToggleButton } from "./objects/selection/collapse-toggle-button.component";
import { CurrentSelection } from "./objects/selection/current-selection.component";
import { SelectionListCell } from "./objects/selection/selection-list-cell.component";
import { SelectionMobileService } from "./objects/selection/services/selection-mobile.service";
import { VSSelectionContainerChildren } from "./objects/selection/vs-selection-container-children.component";
import { VSSelectionContainer } from "./objects/selection/vs-selection-container.component";
import { VSSelection } from "./objects/selection/vs-selection.component";
import { VSLineModule } from "./objects/shape/vs-line.module";
import { VSOval } from "./objects/shape/vs-oval.component";
import { VSRectangle } from "./objects/shape/vs-rectangle.component";
import { VSSlider } from "./objects/slider/vs-slider.component";
import { VSSlidingScale } from "./objects/sliding-scale/vs-sliding-scale.component";
import { VSSpinner } from "./objects/spinner/vs-spinner.component";
import { VSSubmit } from "./objects/submit/vs-submit.component";
import { VSTab } from "./objects/tab/vs-tab.component";
import { PreviewTableModule } from "./objects/table/preview-table.module";
import { TableCellResizeDialogComponent } from "./objects/table/table-cell-resize-dialog/table-cell-resize-dialog.component";
import { VSCalcTable } from "./objects/table/vs-calctable.component";
import { VSCrosstab } from "./objects/table/vs-crosstab.component";
import { VSSimpleCell } from "./objects/table/vs-simple-cell.component";
import { VSTableCellCalendar } from "./objects/table/vs-table-cell-calendar.component";
import { VSTableCell } from "./objects/table/vs-table-cell.component";
import { VSTable } from "./objects/table/vs-table.component";
import { VSTextInput } from "./objects/text-input/vs-text-input.component";
import { VSThermometer } from "./objects/thermometer/vs-thermometer.component";
import { TitleCell } from "./objects/title-cell/title-cell.component";
import { VSTitleModule } from "./objects/title/vs-title.module";
import { ViewerFormatPane } from "./objects/viewer-format-pane.component";
import { ViewerMobileToolbarComponent } from "./objects/viewer-mobile-toolbar/viewer-mobile-toolbar.component";
import { VSViewsheet } from "./objects/viewsheet/vs-viewsheet.component";
import { VSLoadingDisplayModule } from "./objects/vs-loading-display/vs-loading-display.module";
import { VSObjectContainer } from "./objects/vs-object-container.component";
import { ShowHyperlinkService } from "./show-hyperlink.service";
import { ToolbarActionsHandler } from "./toolbar-actions-handler";
import { CheckFormDataService } from "./util/check-form-data.service";
import { DateComparisonService } from "./util/date-comparison.service";
import { FormInputService } from "./util/form-input.service";
import { GlobalSubmitService } from "./util/global-submit.service";
import { PropertyDialogService } from "./util/property-dialog.service";
import { VSTabService } from "./util/vs-tab.service";
import { ViewerAppComponent } from "./viewer-app.component";
import { VsToolbarButtonDirective } from "./vs-toolbar-button.directive";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      AutoDrillDialogModule,
      BindingModule,
      GraphModule,
      FormatModule,
      StatusBarModule,
      FeatureFlagsModule,
      AngularResizeEventModule,
      VSChartModule,
      VSTitleModule,
      PreviewTableModule,
      VSLoadingDisplayModule,
      VSLineModule,
      VSObjectDirectivesModule,
      DataTipDirectivesModule,
      WidgetDirectivesModule,
      TooltipModule,
      InteractModule,
      WidgetFormatModule,
      FixedDropdownModule,
      ColorPickerModule,
      DynamicComboBoxModule,
      TreeModule,
      ModalHeaderModule,
      NotificationsModule,
      SlideOutModule,
      TableStyleModule,
      ScrollModule,
      AutoCompleteModule,
      MouseEventModule,
      DropdownViewModule,
      FontPaneModule,
      PresenterModule,
      HelpLinkModule,
      ShareModule,
      VariableInputDialogModule,
      WidgetScheduleModule,
      VSAssemblyScriptPaneModule,
      RepositoryTreeModule,
      ConditionModule,
      GenericSelectableListModule,
      LargeFormFieldModule,
      DateTypeEditorModule,
      AdditionalTableSelectionPaneModule,
      TipCustomizeDialogModule,
      HighlightModule,
      PipeModule,
      VariableListDialogModule,
      EmailDialogModule,
      PlaceholderDragElementModule,
      NgbProgressbarModule,
      NgbDatepickerModule,
      NgbTooltipModule,
      NgbTypeaheadModule,
      NgbNavModule,
      NgbDropdownModule,
      MiniToolbarModule,
      SimpleTableModule,
      CkeditorWrapperModule,
      ScrollingModule
   ],
   declarations: [
      VsToolbarButtonDirective,
      AddFilterDialog,
      AlignmentPane,
      AnnotationFormatDialog,
      AppErrorMessage,
      BaseResizeableDialogComponent,
      BasicGeneralPane,
      BookmarkPropertyDialog,
      CalcTableAdvancedPane,
      CalcTablePropertyDialog,
      ChartAdvancedPane,
      ChartGeneralPane,
      ChartPropertyDialog,
      CollapseToggleButton,
      ColumnOptionDialog,
      ComboBoxEditor,
      CrosstabAdvancedPane,
      CrosstabPropertyDialog,
      CSSPane,
      CurrentSelection,
      DateEditor,
      EmailDialog,
      ExportDialog,
      FileFormatPane,
      FloatEditor,
      GeneralPropPane,
      HideColumnsDialog,
      HierarchyContentPane,
      HierarchyEditor,
      HierarchyPropertyPane,
      HighlightDialog,
      HyperlinkDialog,
      ImageFormatSelectComponent,
      InputParameterDialog,
      IntegerEditor,
      MonthCalendar,
      PaddingPane,
      ProfilingDataPaneComponent,
      ProfilingDialog,
      RangeSliderAdvancedPane,
      RangeSliderDataPane,
      RangeSliderEditDialog,
      RangeSliderGeneralPane,
      RangeSliderPropertyDialog,
      RangeSliderSizePane,
      RichTextDialog,
      ScheduleDialog,
      SelectionListCell,
      SelectionListDialog,
      SelectionListEditor,
      SizePositionPane,
      SliderLabelPane,
      TableAdvancedPane,
      TableViewGeneralPane,
      TableViewPropertyDialog,
      TextEditor,
      TipPane,
      TitleCell,
      TitlePropPane,
      ViewerAppComponent,
      ViewerFormatPane,
      ViewerMobileToolbarComponent,
      VSCalcTable,
      VSCalendar,
      VSCheckBox,
      VSComboBox,
      VSConditionDialog,
      VSCrosstab,
      VSCylinder,
      VSFormatPane,
      VSFormatsPane,
      VSGauge,
      VSGroupContainer,
      VSImage,
      VSObjectContainer,
      VSOval,
      VSPageBreak,
      VSRadioButton,
      VSRangeSlider,
      VSRectangle,
      VSSelection,
      VSSelectionContainer,
      VSSelectionContainerChildren,
      VSSimpleCell,
      VSSlider,
      VSSlidingScale,
      VSSpinner,
      VSSubmit,
      VSTab,
      VSTable,
      VSTableCell,
      VSTableCellCalendar,
      VSText,
      VSTextInput,
      VSThermometer,
      VSViewsheet,
      YearCalendar,
      TableCellResizeDialogComponent,
      DateComparisonDialog,
      DateComparisonPaneComponent,
      DateComparisonPeriodsPaneComponent,
      DateComparisonIntervalPaneComponent,
      DateComparisonStandardPeriodsComponent,
      DateComparisonCustomPeriodsComponent,
      DateComparisonSharePaneComponent,
      VsBookmarkPaneComponent,
      RemoveBookmarksDialog,
   ],
   exports: [
      VsToolbarButtonDirective,
      AddFilterDialog,
      AlignmentPane,
      AnnotationFormatDialog,
      AppErrorMessage,
      BaseResizeableDialogComponent,
      BasicGeneralPane,
      BookmarkPropertyDialog,
      CalcTableAdvancedPane,
      CalcTablePropertyDialog,
      ChartAdvancedPane,
      ChartGeneralPane,
      ChartPropertyDialog,
      CollapseToggleButton,
      ColumnOptionDialog,
      ComboBoxEditor,
      CrosstabAdvancedPane,
      CrosstabPropertyDialog,
      CSSPane,
      CurrentSelection,
      DateEditor,
      DateComparisonPaneComponent,
      DateComparisonPeriodsPaneComponent,
      DateComparisonIntervalPaneComponent,
      EmailDialog,
      ExportDialog,
      FileFormatPane,
      FloatEditor,
      GeneralPropPane,
      HideColumnsDialog,
      HierarchyContentPane,
      HierarchyEditor,
      HierarchyPropertyPane,
      HighlightDialog,
      HyperlinkDialog,
      ImageFormatSelectComponent,
      InputParameterDialog,
      IntegerEditor,
      MonthCalendar,
      PaddingPane,
      RangeSliderAdvancedPane,
      RangeSliderDataPane,
      RangeSliderEditDialog,
      RangeSliderGeneralPane,
      RangeSliderPropertyDialog,
      RangeSliderSizePane,
      RichTextDialog,
      ScheduleDialog,
      SelectionListCell,
      SelectionListDialog,
      SelectionListEditor,
      SizePositionPane,
      SliderLabelPane,
      TableAdvancedPane,
      TableViewGeneralPane,
      TableViewPropertyDialog,
      TextEditor,
      TipPane,
      TitleCell,
      TitlePropPane,
      ViewerAppComponent,
      ViewerFormatPane,
      ViewerMobileToolbarComponent,
      VSCalcTable,
      VSCalendar,
      VSCheckBox,
      VSComboBox,
      VSConditionDialog,
      VSCrosstab,
      VSCylinder,
      VSFormatPane,
      VSFormatsPane,
      VSGauge,
      VSGroupContainer,
      VSImage,
      VSObjectContainer,
      VSOval,
      VSPageBreak,
      VSRadioButton,
      VSRangeSlider,
      VSRectangle,
      VSSelection,
      VSSelectionContainer,
      VSSelectionContainerChildren,
      VSSimpleCell,
      VSSlider,
      VSSlidingScale,
      VSSpinner,
      VSSubmit,
      VSTab,
      VSTable,
      VSTableCell,
      VSTableCellCalendar,
      VSText,
      VSTextInput,
      VSThermometer,
      VSViewsheet,
      YearCalendar
   ],
   providers: [
      CheckFormDataService,
      DataTreeValidatorService,
      FormInputService,
      GlobalSubmitService,
      VSTabService,
      FileUploadService,
      MiniToolbarService,
      ShowHyperlinkService,
      {
         provide: RichTextService,
         useClass: CKEditorRichTextService,
         deps: [FontService, NgbModal, HttpClient]
      },
      PropertyDialogService,
      FullScreenService,
      {
         provide: DndService,
         useClass: VSDndService,
         deps: [HttpClient]
      },
      DateComparisonService,
      {
         provide: ChartEditorService,
         useClass: VSChartEditorService,
         deps: [BindingService, ModelService]
      },
      {
         provide: BindingService,
         useClass: VSBindingService,
         deps: [ModelService, HttpClient]
      },
      ToolbarActionsHandler,
      ComposerRecentService,
      ScriptService,
      SelectionMobileService,
      NgbModal
   ]
})
export class VSObjectModule {
}
