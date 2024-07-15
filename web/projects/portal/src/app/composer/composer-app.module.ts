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
import { DownloadModule } from "../../../../shared/download/download.module";
import { AngularResizeEventModule } from "../../../../shared/resize-event/angular-resize-event.module";
import { BindingModule } from "../binding/binding.module";
import { BindingService } from "../binding/services/binding.service";
import { VSBindingService } from "../binding/services/vs-binding.service";
import { BindingTreeService } from "../binding/widget/binding-tree/binding-tree.service";
import { VSBindingTreeService } from "../binding/widget/binding-tree/vs-binding-tree.service";
import { ChatModule } from "../common/chat/chat.module";
import { FileUploadService } from "../common/services/file-upload.service";
import { UIContextService } from "../common/services/ui-context.service";
import { FormatModule } from "../format/format.module";
import { VsWizardModule } from "../vs-wizard/vs-wizard.module";
import { ComposerToken } from "../vsobjects/context-provider.service";
import { DataTreeValidatorService } from "../vsobjects/dialog/data-tree-validator.service";
import { VSChartModule } from "../vsobjects/objects/chart/vs-chart.module";
import {
   SelectionContainerChildrenService
} from "../vsobjects/objects/selection/services/selection-container-children.service";
import { VSLineModule } from "../vsobjects/objects/shape/vs-line.module";
import { PreviewTableModule } from "../vsobjects/objects/table/preview-table.module";
import { VSTrapService } from "../vsobjects/util/vs-trap.service";
import { PropertyDialogService } from "../vsobjects/util/property-dialog.service";
import { VSObjectModule } from "../vsobjects/vs-object.module";
import { VSViewModule } from "../vsview/vs-view.module";
import { ColorPickerModule } from "../widget/color-picker/color-picker.module";
import { DynamicComboBoxModule } from "../widget/dynamic-combo-box/dynamic-combo-box.module";
import { FixedDropdownModule } from "../widget/fixed-dropdown/fixed-dropdown.module";
import { WidgetFormatModule } from "../widget/format/widget-format.module";
import { InteractModule } from "../widget/interact/interact.module";
import { NotificationsModule } from "../widget/notifications/notifications.module";
import { SlideOutModule } from "../widget/slide-out/slide-out.module";
import { TooltipModule } from "../widget/tooltip/tooltip.module";
import { TreeModule } from "../widget/tree/tree.module";
import { ComposerRoutingModule } from "./app-routing.module";
import { ComposerAppComponent } from "./app.component";
import { CalendarAdvancedPane } from "./dialog/vs/calendar-advanced-pane.component";
import { CalendarDataPane } from "./dialog/vs/calendar-data-pane.component";
import { CalendarGeneralPane } from "./dialog/vs/calendar-general-pane.component";
import { CalendarPropertyDialog } from "./dialog/vs/calendar-property-dialog.component";
import { CheckboxGeneralPane } from "./dialog/vs/checkbox-general-pane.component";
import { CheckboxPropertyDialog } from "./dialog/vs/checkbox-property-dialog.component";
import { ClickableScriptPane } from "./dialog/vs/clickable-script-pane.component";
import { ComboBoxEditorValidationService } from "./dialog/vs/combo-box-editor-validation.service";
import { ComboboxGeneralPane } from "./dialog/vs/combobox-general-pane.component";
import { ComboBoxPropertyDialog } from "./dialog/vs/combobox-property-dialog.component";
import { DataInputPane } from "./dialog/vs/data-input-pane.component";
import { DataOutputPane } from "./dialog/vs/data-output-pane.component";
import { DeleteCellDialog } from "./dialog/vs/delete-cell-dialog.component";
import { DynamicImagePane } from "./dialog/vs/dynamic-image-pane.component";
import { FacePane } from "./dialog/vs/face-pane.component";
import { FillPropPane } from "./dialog/vs/fill-prop-pane.component";
import { FiltersPane } from "./dialog/vs/filters-pane.component";
import { GaugeAdvancedPane } from "./dialog/vs/gauge-advanced-pane.component";
import { GaugeGeneralPane } from "./dialog/vs/gauge-general-pane.component";
import { GaugePropertyDialog } from "./dialog/vs/gauge-property-dialog.component";
import { GroupContainerGeneralPane } from "./dialog/vs/group-container-general-pane.component";
import {
   GroupContainerPropertyDialog
} from "./dialog/vs/group-container-property-dialog.component";
import { ImageAdvancedPane } from "./dialog/vs/image-advanced-pane.component";
import { ImageGeneralPane } from "./dialog/vs/image-general-pane.component";
import { ImagePropertyDialog } from "./dialog/vs/image-property-dialog.component";
import { ImageScalePane } from "./dialog/vs/image-scale-pane.component";
import { InsertCellDialog } from "./dialog/vs/insert-cell-dialog.component";
import { LabelPropPane } from "./dialog/vs/label-prop-pane.component";
import { LayoutOptionDialog } from "./dialog/vs/layout-option-dialog.component";
import { LinePropPane } from "./dialog/vs/line-prop-pane.component";
import { LinePropertyDialog } from "./dialog/vs/line-property-dialog.component";
import { LinePropertyPane } from "./dialog/vs/line-property-pane.component";
import { ListValuesPane } from "./dialog/vs/list-values-pane.component";
import { LocalizationPane } from "./dialog/vs/localization-pane.component";
import { NumberRangePane } from "./dialog/vs/number-range-pane.component";
import { NumericRangePane } from "./dialog/vs/numeric-range-pane.component";
import { OutputGeneralPane } from "./dialog/vs/output-general-pane.component";
import { OvalPropertyDialog } from "./dialog/vs/oval-property-dialog.component";
import { OvalPropertyPane } from "./dialog/vs/oval-property-pane.component";
import { PresenterPane } from "./dialog/vs/presenter-pane.component";
import { RadioButtonGeneralPane } from "./dialog/vs/radiobutton-general-pane.component";
import { RadioButtonPropertyDialog } from "./dialog/vs/radiobutton-property-dialog.component";
import { RangePane } from "./dialog/vs/range-pane.component";
import { RectanglePropertyDialog } from "./dialog/vs/rectangle-property-dialog.component";
import { RectanglePropertyPane } from "./dialog/vs/rectangle-property-pane.component";
import { SaveViewsheetDialog } from "./dialog/vs/save-viewsheet-dialog.component";
import { ScreenSizeDialog } from "./dialog/vs/screen-size-dialog.component";
import { ScreensPane } from "./dialog/vs/screens-pane.component";
import { SelectDataSourceDialog } from "./dialog/vs/select-data-source-dialog.component";
import {
   SelectionContainerGeneralPane
} from "./dialog/vs/selection-container-general-pane.component";
import {
   SelectionContainerPropertyDialog
} from "./dialog/vs/selection-container-property-dialog.component";
import { SelectionGeneralPane } from "./dialog/vs/selection-general-pane.component";
import { SelectionListPane } from "./dialog/vs/selection-list-pane.component";
import { SelectionListPropertyDialog } from "./dialog/vs/selection-list-property-dialog.component";
import { SelectionMeasurePane } from "./dialog/vs/selection-measure-pane.component";
import { SelectionTreeColumnsPane } from "./dialog/vs/selection-tree-columns-pane.component";
import { SelectionTreeIdPane } from "./dialog/vs/selection-tree-id-pane.component";
import { SelectionTreePane } from "./dialog/vs/selection-tree-pane.component";
import { SelectionTreePropertyDialog } from "./dialog/vs/selection-tree-property-dialog.component";
import { ShapeGeneralPane } from "./dialog/vs/shape-general-pane.component";
import { SliderAdvancedPane } from "./dialog/vs/slider-advanced-pane.component";
import { SliderGeneralPane } from "./dialog/vs/slider-general-pane.component";
import { SliderPropertyDialog } from "./dialog/vs/slider-property-dialog.component";
import { SpinnerGeneralPane } from "./dialog/vs/spinner-general-pane.component";
import { SpinnerPropertyDialog } from "./dialog/vs/spinner-property-dialog.component";
import { StaticImagePane } from "./dialog/vs/static-image-pane.component";
import { SubmitGeneralPane } from "./dialog/vs/submit-general-pane.component";
import { SubmitPropertyDialog } from "./dialog/vs/submit-property-dialog.component";
import { TabGeneralPane } from "./dialog/vs/tab-general-pane.component";
import { TabListPane } from "./dialog/vs/tab-list-pane.component";
import { TabPropertyDialog } from "./dialog/vs/tab-property-dialog.component";
import { TextFormatPane } from "./dialog/vs/text-format-pane.component";
import { TextGeneralPane } from "./dialog/vs/text-general-pane.component";
import { TextPane } from "./dialog/vs/text-pane.component";
import { TextPropertyDialog } from "./dialog/vs/text-property-dialog.component";
import { TextInputColumnOptionPane } from "./dialog/vs/textinput-column-option-pane.component";
import { TextInputGeneralPane } from "./dialog/vs/textinput-general-pane.component";
import { TextInputPropertyDialog } from "./dialog/vs/textinput-property-dialog.component";
import { UploadGeneralPane } from "./dialog/vs/upload-general-pane.component";
import { UploadPropertyDialog } from "./dialog/vs/upload-property-dialog.component";
import { ViewsheetDeviceLayoutDialog } from "./dialog/vs/viewsheet-device-layout-dialog.component";
import {
   ViewsheetObjectPropertyDialog
} from "./dialog/vs/viewsheet-object-property-dialog.component";
import { ViewsheetOptionsPane } from "./dialog/vs/viewsheet-options-pane.component";
import { ViewsheetParametersDialog } from "./dialog/vs/viewsheet-parameters-dialog.component";
import { ViewsheetPrintLayoutDialog } from "./dialog/vs/viewsheet-print-layout-dialog.component";
import { ViewsheetPropertyDialog } from "./dialog/vs/viewsheet-property-dialog.component";
import { ViewsheetScriptPane } from "./dialog/vs/viewsheet-script-pane.component";
import { VSSortingDialog } from "./dialog/vs/vs-sorting-dialog.component";
import { VSSortingPane } from "./dialog/vs/vs-sorting-pane.component";
import { AdvancedConditionPane } from "./dialog/ws/advanced-condition-pane.component";
import { AggregateDialog } from "./dialog/ws/aggregate-dialog.component";
import { AggregatePane } from "./dialog/ws/aggregate-pane.component";
import { AssemblyConditionDialog } from "./dialog/ws/assembly-condition-dialog.component";
import { AssetRepositoryPane } from "./dialog/ws/asset-repository-pane.component";
import { ColumnDescriptionDialog } from "./dialog/ws/column-description-dialog.component";
import { ColumnTypeDialog } from "./dialog/ws/column-type-dialog.component";
import { ConcatenateTablesDialog } from "./dialog/ws/concatenate-tables-dialog.component";
import { ConcatenationTypeDialog } from "./dialog/ws/concatenation-type-dialog.component";
import { CrosstabPane } from "./dialog/ws/crosstab-pane.component";
import { DateRangeOptionDialog } from "./dialog/ws/date-range-option-dialog.component";
import { EmbeddedTableDialog } from "./dialog/ws/embedded-table-dialog.component";
import { GroupingConditionDialog } from "./dialog/ws/grouping-condition-dialog.component";
import { GroupingDialog } from "./dialog/ws/grouping-dialog.component";
import { ImportCSVDialog } from "./dialog/ws/import-csv-dialog.component";
import { MVConditionPane } from "./dialog/ws/mv-condition-pane.component";
import { NewWorksheetDialog } from "./dialog/ws/new-worksheet-dialog.component";
import { NumericRangeOptionDialog } from "./dialog/ws/numeric-range-option-dialog.component";
import { QueryPlanDialog } from "./dialog/ws/query-plan-dialog.component";
import { ReorderColumnsDialog } from "./dialog/ws/reorder-columns-dialog.component";
import { ReorderSubtablesDialogComponent } from "./dialog/ws/reorder-subtables-dialog.component";
import { SaveWorksheetDialog } from "./dialog/ws/save-worksheet-dialog.component";
import { SortColumnDialog } from "./dialog/ws/sort-column-dialog.component";
import { SortColumnEditor } from "./dialog/ws/sort-column-editor.component";
import { TablePropertyDialog } from "./dialog/ws/table-property-dialog.component";
import { TableUnpivotDialog } from "./dialog/ws/table-unpivot-dialog.component";
import { TabularQueryDialog } from "./dialog/ws/tabular-query-dialog.component";
import { VariableAssemblyDialog } from "./dialog/ws/variable-assembly-dialog.component";
import { VariableTableListDialog } from "./dialog/ws/variable-table-list-dialog.component";
import { VPMPrincipalDialogComponent } from "./dialog/ws/vpm-principal-dialog.component";
import { WorksheetOptionPane } from "./dialog/ws/worksheet-option-pane.component";
import { WorksheetPropertyDialog } from "./dialog/ws/worksheet-property-dialog.component";
import { AssetTreePane } from "./gui/asset-pane/asset-tree-pane.component";
import { ClipboardService } from "./gui/clipboard.service";
import { ComponentsPane } from "./gui/components-pane/components-pane.component";
import { ComponentTree } from "./gui/components-pane/tree/component-tree.component";
import { ComposerMainComponent } from "./gui/composer-main.component";
import { ComposerToolbarService } from "./gui/composer-toolbar.service";
import { ComposerEmptyEditor } from "./gui/empty-editor/composer-empty-editor.component";
import { ResizeHandlerService } from "./gui/resize-handler.service";
import { SheetTabSelectorComponent } from "./gui/tab-selector/sheet-tab-selector.component";
import { ComposerToolbarComponent } from "./gui/toolbar/composer-toolbar.component";
import { ComposerBindingTree } from "./gui/toolbox/composer-binding-tree.component";
import { ToolboxPane } from "./gui/toolbox/toolbox-pane.component";
import {
   CalcTableActionHandlerDirective
} from "./gui/vs/action/calc-table-action-handler.directive";
import { CalendarActionHandlerDirective } from "./gui/vs/action/calendar-action-handler.directive";
import { ChartActionHandlerDirective } from "./gui/vs/action/chart-action-handler.directive";
import { CheckBoxActionHandlerDirective } from "./gui/vs/action/check-box-action-handler.directive";
import { ComboBoxActionHandlerDirective } from "./gui/vs/action/combo-box-action-handler.directive";
import { CrosstabActionHandlerDirective } from "./gui/vs/action/crosstab-action-handler.directive";
import { GaugeActionHandlerDirective } from "./gui/vs/action/gauge-action-handler.directive";
import {
   GroupContainerActionHandlerDirective
} from "./gui/vs/action/group-container-action-handler.directive";
import { ImageActionHandlerDirective } from "./gui/vs/action/image-action-handler.directive";
import { LineActionHandlerDirective } from "./gui/vs/action/line-action-handler.directive";
import { OvalActionHandlerDirective } from "./gui/vs/action/oval-action-handler.directive";
import {
   RadioButtonActionHandlerDirective
} from "./gui/vs/action/radio-button-action-handler.directive";
import {
   RangeSliderActionHandlerDirective
} from "./gui/vs/action/range-slider-action-handler.directive";
import {
   RectangleActionHandlerDirective
} from "./gui/vs/action/rectangle-action-handler.directive";
import {
   SelectionContainerActionHandlerDirective
} from "./gui/vs/action/selection-container-action-handler.directive";
import {
   SelectionListActionHandlerDirective
} from "./gui/vs/action/selection-list-action-handler.directive";
import {
   SelectionTreeActionHandlerDirective
} from "./gui/vs/action/selection-tree-action-handler.directive";
import { SliderActionHandlerDirective } from "./gui/vs/action/slider-action-handler.directive";
import { SpinnerActionHandlerDirective } from "./gui/vs/action/spinner-action-handler.directive";
import { SubmitActionHandlerDirective } from "./gui/vs/action/submit-action-handler.directive";
import { TabActionHandlerDirective } from "./gui/vs/action/tab-action-handler.directive";
import { TableActionHandlerDirective } from "./gui/vs/action/table-action-handler.directive";
import { TextActionHandlerDirective } from "./gui/vs/action/text-action-handler.directive";
import {
   TextInputActionHandlerDirective
} from "./gui/vs/action/text-input-action-handler.directive";
import { UploadActionHandlerDirective } from "./gui/vs/action/upload-action-handler.directive";
import {
   ViewsheetActionHandlerDirective
} from "./gui/vs/action/viewsheet-action-handler.directive";
import { ComposerObjectService } from "./gui/vs/composer-object.service";
import {
   AssemblyContextMenuItemsComponent
} from "./gui/vs/editor/assembly-context-menu-items.component";
import { EditableObjectContainer } from "./gui/vs/editor/editable-object-container.component";
import { MobileToolbarComponent } from "./gui/vs/editor/mobile-toolbar.component";
import { VSPane } from "./gui/vs/editor/viewsheet-pane.component";
import { EventQueueService } from "./gui/vs/event-queue.service";
import { LayoutObject } from "./gui/vs/layouts/layout-object.component";
import { LayoutPane } from "./gui/vs/layouts/layout-pane.component";
import {
   ComposerSelectionContainerChildren
} from "./gui/vs/objects/selection/composer-selection-container-children.component";
import {
   ConcatRelationConnectorComponent
} from "./gui/ws/editor/concatenation/concat-relation-connector.component";
import {
   ConcatRelationDescriptorComponent
} from "./gui/ws/editor/concatenation/concat-relation-descriptor.component";
import {
   ConcatenatedTableThumbnailComponent
} from "./gui/ws/editor/concatenation/concatenated-table-thumbnail.component";
import {
   ConcatenationPaneDropTargetComponent
} from "./gui/ws/editor/concatenation/concatenation-pane-drop-target.component";
import {
   WSConcatenationEditorPane
} from "./gui/ws/editor/concatenation/ws-concatenation-editor-pane.component";
import {
   DataBlockStatusIndicatorComponent
} from "./gui/ws/editor/data-block-status-indicator.component";
import { GroupingThumbnail } from "./gui/ws/editor/grouping-thumbnail.component";
import { MergeJoinSubtableComponent } from "./gui/ws/editor/merge/merge-join-subtable.component";
import {
   WSMergeJoinEditorPaneComponent
} from "./gui/ws/editor/merge/ws-merge-join-editor-pane.component";
import { SchemaColumnComponent } from "./gui/ws/editor/schema/schema-column.component";
import {
   SchemaTableThumbnailComponent
} from "./gui/ws/editor/schema/schema-table-thumbnail.component";
import { SubtableListComponent } from "./gui/ws/editor/schema/sidebar-pane/subtable-list.component";
import {
   WSRelationalJoinEditorPaneComponent
} from "./gui/ws/editor/schema/ws-relational-join-editor-pane.component";
import { TableThumbnailComponent } from "./gui/ws/editor/table-thumbnail.component";
import { VariableThumbnail } from "./gui/ws/editor/variable-thumbnail.component";
import { WSAssemblyGraphPaneComponent } from "./gui/ws/editor/ws-assembly-graph-pane.component";
import {
   WSAssemblyThumbnailTitleComponent
} from "./gui/ws/editor/ws-assembly-thumbnail-title.component";
import {
   WSCompositeTableBreadcrumbComponent
} from "./gui/ws/editor/ws-composite-table-breadcrumb.component";
import {
   WSCompositeTableFocusPaneComponent
} from "./gui/ws/editor/ws-composite-table-focus-pane.component";
import {
   WSCompositeTableSidebarPane
} from "./gui/ws/editor/ws-composite-table-sidebar-pane.component";
import { WSDetailsPaneComponent } from "./gui/ws/editor/ws-details-pane.component";
import { WSDetailsTableDataComponent } from "./gui/ws/editor/ws-details-table-data.component";
import { WSHeaderCell } from "./gui/ws/editor/ws-header-cell.component";
import { WSPaneComponent } from "./gui/ws/editor/ws-pane.component";
import { ComposerResolver } from "./services/composer-resolver.service";
import { LineAnchorService } from "./services/line-anchor.service";
import { WsChangeService } from "./gui/ws/editor/ws-change.service";
import { TableLayoutPropertyDialog } from "./dialog/vs/table-layout-property-dialog.component";
import { ModelService } from "../widget/services/model.service";
import { FeatureFlagsModule } from "../../../../shared/feature-flags/feature-flags.module";
import { ComposerRecentService } from "./gui/composer-recent.service";
import { ShowHideColumnsDialogComponent } from "./dialog/ws/show-hide-columns-dialog.component";
import { StatusBarModule } from "../status-bar/status-bar.module";
import {
   VSLoadingDisplayModule
} from "../vsobjects/objects/vs-loading-display/vs-loading-display.module";
import { WidgetDirectivesModule } from "../widget/directive/widget-directives.module";
import { ModalHeaderModule } from "../widget/modal-header/modal-header.module";
import { SplitPaneModule } from "../widget/split-pane/split-pane.module";
import {
   VariableListDialogModule
} from "../widget/dialog/variable-list-dialog/variable-list-dialog.module";
import { ConditionModule } from "../widget/condition/condition.module";
import {
   ValueRangeSelectableListModule
} from "../widget/dialog/value-range-selectable-list/value-range-selectable-list.module";
import { AssetTreeModule } from "../widget/asset-tree/asset-tree.module";
import { ConsoleDialogModule } from "../widget/console-dialog/console-dialog.module";
import {
   VariableInputDialogModule
} from "../widget/dialog/variable-input-dialog/variable-input-dialog.module";
import {
   PlaceholderDragElementModule
} from "../widget/placeholder-drag-element/placeholder-drag-element.module";
import { RulersModule } from "../widget/rulers/rulers.module";
import {
   AdditionalTableSelectionPaneModule
} from "../widget/dialog/additional-table-selection-pane/additional-table-selection-pane.module";
import { ScriptPaneModule } from "../widget/dialog/script-pane/script-pane.module";
import { TabularModule } from "../widget/tabular/tabular.module";
import { HelpLinkModule } from "../widget/help-link/help-link.module";
import { SQLQueryDialogModule } from "../widget/dialog/sql-query-dialog/sql-query-dialog.module";
import { MouseEventModule } from "../widget/mouse-event/mouse-event.module";
import { MessageDialogModule } from "../widget/dialog/message-dialog/message-dialog.module";
import { PipeModule } from "../widget/pipe/pipe.module";
import { ToolbarGroupModule } from "../widget/toolbar/toolbar-group/toolbar-group.module";
import { LargeFormFieldModule } from "../widget/large-form-field/large-form-field.module";
import { StandardDialogModule } from "../widget/standard-dialog/standard-dialog.module";
import {
   GenericSelectableListModule
} from "../widget/generic-selectable-list/generic-selectable-list.module";
import {
   VSAssemblyScriptPaneModule
} from "../widget/dialog/vsassembly-script-pane/vsassembly-script-pane.module";
import { DateTypeEditorModule } from "../widget/date-type-editor/date-type-editor.module";
import { ImageEditorModule } from "../widget/image-editor/image-editor.module";
import { ExpandStringModule } from "../widget/expand-string/expand-string.module";
import { ShuffleListModule } from "../widget/shuffle-list/shuffle-list.module";
import { ScrollableTableModule } from "../widget/scrollable-table/scrollable-table.module";
import { ElidedCellModule } from "../widget/elided-cell/elided-cell.module";
import {
   NgbDropdownModule,
   NgbModal,
   NgbNavModule,
   NgbProgressbarModule,
   NgbTooltipModule
} from "@ng-bootstrap/ng-bootstrap";
import { ScrollModule } from "../widget/scroll/scroll.module";
import { MiniToolbarModule } from "../vsobjects/objects/mini-toolbar/mini-toolbar.module";
import { CodemirrorService } from "../../../../shared/util/codemirror/codemirror.service";
import {
   DefaultCodemirrorService
} from "../../../../shared/util/codemirror/default-codemirror.service";
import { ScriptEditPaneComponent } from "./gui/script/editor/script-edit-pane.component";
import { StylePaneComponent } from "./gui/tablestyle/editor/style-pane.component";
import { TableStylePreviewPaneComponent } from "./gui/tablestyle/editor/table-style-preview-pane.component";
import { TableStyleFormatPaneComponent } from "./gui/tablestyle/editor/table-style-format-pane.component";
import { StyleBorderPaneComponent } from "./gui/tablestyle/editor/style-border-pane.component";
import { DropdownViewModule } from "../widget/dropdown-view/dropdown-view.module";
import { FontPaneModule } from "../widget/font-pane/font-pane.module";
import { ScriptModule } from "./gui/script/tree/script-pane.module";
import { StyleTreePane } from "./gui/tablestyle/style-tree/style-tree-pane.component";
import { VSObjectDirectivesModule } from "../vsobjects/directives/vs-object-directives.module";
import { CodemirrorModule } from "../widget/codemirror/codemirror.module";
import { SaveTableStyleDialog } from "./gui/tablestyle/editor/save-table-style-dialog.component";
import { SaveScriptDialog } from "./dialog/script/save-script-dialog.component";
import { EditCustomPatternsDialog } from "./gui/tablestyle/editor/edit-custom-patterns-dialog.component";
import { TableStyleBorderRegionComponent } from "./gui/tablestyle/editor/table-style-border-region.component";
import { ScriptPropertyDialogComponent } from "./dialog/script/script-property-dialog.component";

@NgModule({
   imports: [
      CommonModule,
      ChatModule.forRoot("5ba3fdd5c666d426648af5c9/default"),
      FormsModule,
      ReactiveFormsModule,
      VsWizardModule,
      BindingModule,
      VSObjectModule,
      VSViewModule,
      FormatModule,
      ComposerRoutingModule,
      DownloadModule,
      AngularResizeEventModule,
      FeatureFlagsModule,
      VSLoadingDisplayModule,
      VSChartModule,
      VSLineModule,
      PreviewTableModule,
      WidgetDirectivesModule,
      TooltipModule,
      InteractModule,
      ModalHeaderModule,
      SlideOutModule,
      NotificationsModule,
      FixedDropdownModule,
      TreeModule,
      DynamicComboBoxModule,
      WidgetFormatModule,
      ColorPickerModule,
      SplitPaneModule,
      VariableListDialogModule,
      ConditionModule,
      ValueRangeSelectableListModule,
      AssetTreeModule,
      ConsoleDialogModule,
      VariableInputDialogModule,
      PlaceholderDragElementModule,
      RulersModule,
      AdditionalTableSelectionPaneModule,
      ScriptPaneModule,
      TabularModule,
      HelpLinkModule,
      SQLQueryDialogModule,
      MouseEventModule,
      MessageDialogModule,
      PipeModule,
      ToolbarGroupModule,
      LargeFormFieldModule,
      StandardDialogModule,
      GenericSelectableListModule,
      VSAssemblyScriptPaneModule,
      DateTypeEditorModule,
      ImageEditorModule,
      ExpandStringModule,
      ShuffleListModule,
      ScrollableTableModule,
      ElidedCellModule,
      NgbDropdownModule,
      NgbTooltipModule,
      NgbNavModule,
      NgbProgressbarModule,
      ScrollModule,
      MiniToolbarModule,
      StatusBarModule,
      DropdownViewModule,
      FontPaneModule,
      ScriptModule,
      VSObjectDirectivesModule,
      CodemirrorModule,
   ],
   declarations: [
      ComposerAppComponent,
      AdvancedConditionPane,
      AggregateDialog,
      AggregatePane,
      AssemblyConditionDialog,
      AssemblyContextMenuItemsComponent,
      AssetRepositoryPane,
      AssetTreePane,
      CalendarAdvancedPane,
      CalendarDataPane,
      CalendarGeneralPane,
      CalendarPropertyDialog,
      CheckboxGeneralPane,
      CheckboxPropertyDialog,
      ClickableScriptPane,
      ColumnDescriptionDialog,
      ColumnTypeDialog,
      ComboboxGeneralPane,
      ComboBoxPropertyDialog,
      ComponentsPane,
      ComponentTree,
      ComposerBindingTree,
      ComposerEmptyEditor,
      ComposerMainComponent,
      ComposerSelectionContainerChildren,
      ComposerToolbarComponent,
      ConcatenatedTableThumbnailComponent,
      ConcatenateTablesDialog,
      ConcatenationPaneDropTargetComponent,
      ConcatenationTypeDialog,
      ConcatRelationConnectorComponent,
      ConcatRelationDescriptorComponent,
      CrosstabPane,
      DataBlockStatusIndicatorComponent,
      DataInputPane,
      DataOutputPane,
      DateRangeOptionDialog,
      DeleteCellDialog,
      DynamicImagePane,
      EditableObjectContainer,
      EditCustomPatternsDialog,
      EmbeddedTableDialog,
      FacePane,
      FillPropPane,
      FiltersPane,
      GaugeAdvancedPane,
      GaugeGeneralPane,
      GaugePropertyDialog,
      GroupContainerGeneralPane,
      GroupContainerPropertyDialog,
      GroupingConditionDialog,
      GroupingDialog,
      GroupingThumbnail,
      ImageAdvancedPane,
      ImageGeneralPane,
      ImagePropertyDialog,
      ImageScalePane,
      ImportCSVDialog,
      InsertCellDialog,
      LabelPropPane,
      LayoutObject,
      LayoutOptionDialog,
      LayoutPane,
      LinePropertyDialog,
      LinePropertyPane,
      LinePropPane,
      ListValuesPane,
      LocalizationPane,
      MergeJoinSubtableComponent,
      MobileToolbarComponent,
      MVConditionPane,
      NewWorksheetDialog,
      NumberRangePane,
      NumericRangeOptionDialog,
      NumericRangePane,
      OutputGeneralPane,
      OvalPropertyDialog,
      OvalPropertyPane,
      PresenterPane,
      QueryPlanDialog,
      RadioButtonGeneralPane,
      RadioButtonPropertyDialog,
      RangePane,
      RectanglePropertyDialog,
      RectanglePropertyPane,
      ReorderColumnsDialog,
      ReorderSubtablesDialogComponent,
      SaveTableStyleDialog,
      SaveViewsheetDialog,
      SaveWorksheetDialog,
      SchemaColumnComponent,
      SchemaTableThumbnailComponent,
      ScreenSizeDialog,
      ScreensPane,
      ScriptPropertyDialogComponent,
      SelectDataSourceDialog,
      SelectionContainerGeneralPane,
      SelectionContainerPropertyDialog,
      SelectionGeneralPane,
      SelectionListPane,
      SelectionListPropertyDialog,
      SelectionMeasurePane,
      SelectionTreeColumnsPane,
      SelectionTreeIdPane,
      SelectionTreePane,
      SelectionTreePropertyDialog,
      ShapeGeneralPane,
      SheetTabSelectorComponent,
      SliderAdvancedPane,
      SliderGeneralPane,
      SliderPropertyDialog,
      SortColumnDialog,
      SortColumnEditor,
      SpinnerGeneralPane,
      SpinnerPropertyDialog,
      StaticImagePane,
      StyleBorderPaneComponent,
      StylePaneComponent,
      StyleTreePane,
      SubmitGeneralPane,
      SubmitPropertyDialog,
      SubtableListComponent,
      TabGeneralPane,
      TableStyleBorderRegionComponent,
      TableStyleFormatPaneComponent,
      TableStylePreviewPaneComponent,
      TableLayoutPropertyDialog,
      TablePropertyDialog,
      TableThumbnailComponent,
      TableUnpivotDialog,
      TabListPane,
      TabPropertyDialog,
      TabularQueryDialog,
      TextFormatPane,
      TextGeneralPane,
      TextInputColumnOptionPane,
      TextInputGeneralPane,
      TextInputPropertyDialog,
      TextPane,
      TextPropertyDialog,
      ToolboxPane,
      UploadGeneralPane,
      UploadPropertyDialog,
      VariableAssemblyDialog,
      VariableTableListDialog,
      VariableThumbnail,
      ViewsheetDeviceLayoutDialog,
      ViewsheetObjectPropertyDialog,
      ViewsheetOptionsPane,
      ViewsheetParametersDialog,
      ViewsheetPrintLayoutDialog,
      ViewsheetPropertyDialog,
      ViewsheetScriptPane,
      VPMPrincipalDialogComponent,
      VSPane,
      VSSortingDialog,
      VSSortingPane,
      WorksheetOptionPane,
      WorksheetPropertyDialog,
      WSAssemblyGraphPaneComponent,
      WSAssemblyThumbnailTitleComponent,
      WSCompositeTableBreadcrumbComponent,
      WSCompositeTableFocusPaneComponent,
      WSCompositeTableSidebarPane,
      WSConcatenationEditorPane,
      WSDetailsPaneComponent,
      WSDetailsTableDataComponent,
      WSHeaderCell,
      WSMergeJoinEditorPaneComponent,
      WSPaneComponent,
      WSRelationalJoinEditorPaneComponent,
      CalcTableActionHandlerDirective,
      CalendarActionHandlerDirective,
      ChartActionHandlerDirective,
      CheckBoxActionHandlerDirective,
      ComboBoxActionHandlerDirective,
      CrosstabActionHandlerDirective,
      GaugeActionHandlerDirective,
      GroupContainerActionHandlerDirective,
      ImageActionHandlerDirective,
      LineActionHandlerDirective,
      OvalActionHandlerDirective,
      RadioButtonActionHandlerDirective,
      RangeSliderActionHandlerDirective,
      RectangleActionHandlerDirective,
      SelectionContainerActionHandlerDirective,
      SelectionListActionHandlerDirective,
      SelectionTreeActionHandlerDirective,
      SliderActionHandlerDirective,
      SpinnerActionHandlerDirective,
      SubmitActionHandlerDirective,
      TabActionHandlerDirective,
      TableActionHandlerDirective,
      TextActionHandlerDirective,
      TextInputActionHandlerDirective,
      UploadActionHandlerDirective,
      ViewsheetActionHandlerDirective,
      ShowHideColumnsDialogComponent,
      ScriptEditPaneComponent,
      SaveScriptDialog
   ],
   providers: [
      ClipboardService,
      ComposerObjectService,
      ComposerToolbarService,
      DataTreeValidatorService,
      EventQueueService,
      ResizeHandlerService,
      SelectionContainerChildrenService,
      LineAnchorService,
      FileUploadService,
      UIContextService,
      VSTrapService,
      PropertyDialogService,
      VSBindingTreeService,
      ComboBoxEditorValidationService,
      ComposerResolver,
      {
         provide: ComposerToken,
         useValue: true
      },
      {
         provide: BindingService,
         useClass: VSBindingService,
         deps: [ModelService, HttpClient, UIContextService]
      },
      {
         provide: BindingTreeService,
         useExisting: VSBindingTreeService
      },
      WsChangeService,
      ComposerRecentService,
      NgbModal,
      {
         provide: CodemirrorService,
         useClass: DefaultCodemirrorService
      },
   ],
   exports: [],
   bootstrap: [ComposerAppComponent]
})
export class ComposerAppModule {
}
