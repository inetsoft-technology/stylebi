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
import { CommonModule } from "@angular/common";
import { ModuleWithProviders, NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { FormatModule } from "../format/format.module";
import { ConsoleDialogModule } from "../widget/console-dialog/console-dialog.module";
import { DynamicComboBoxModule } from "../widget/dynamic-combo-box/dynamic-combo-box.module";
import { ModalHeaderModule } from "../widget/modal-header/modal-header.module";
import { NotificationsModule } from "../widget/notifications/notifications.module";
import { SlideOutModule } from "../widget/slide-out/slide-out.module";
import { TreeModule } from "../widget/tree/tree.module";
import { BindingEditor } from "./editor/binding-editor.component";
import { AestheticPane } from "./editor/chart/aesthetic/aesthetic-pane.component";
import { BindingSizePane } from "./editor/chart/aesthetic/binding-size-pane.component";
import { CategoricalColorPane } from "./editor/chart/aesthetic/categorical-color-pane.component";
import { CategoricalShapePane } from "./editor/chart/aesthetic/categorical-shape-pane.component";
import { ChartAestheticMc } from "./editor/chart/aesthetic/chart-aesthetic-mc.component";
import { ColorCell } from "./editor/chart/aesthetic/color-cell.component";
import { ColorFieldMc } from "./editor/chart/aesthetic/color-field-mc.component";
import { CombinedColorPane } from "./editor/chart/aesthetic/combined-color-pane.component";
import { CombinedShapePane } from "./editor/chart/aesthetic/combined-shape-pane.component";
import { CombinedSizePane } from "./editor/chart/aesthetic/combined-size-pane.component";
import { GradientColorEditor } from "./editor/chart/aesthetic/gradient-color-editor.component";
import { HslColorEditor } from "./editor/chart/aesthetic/hsl-color-editor.component";
import { LineComboBox } from "./editor/chart/aesthetic/line-combo-box.component";
import { LineItem } from "./editor/chart/aesthetic/line-item.component";
import { LinearColorDropdown } from "./editor/chart/aesthetic/linear-color-dropdown.component";
import { LinearColorPane } from "./editor/chart/aesthetic/linear-color-pane.component";
import { LinearLinePane } from "./editor/chart/aesthetic/linear-line-pane.component";
import { LinearShapePane } from "./editor/chart/aesthetic/linear-shape-pane.component";
import { LinearTexturePane } from "./editor/chart/aesthetic/linear-texture-pane.component";
import { ShapeCell } from "./editor/chart/aesthetic/shape-cell.component";
import { ShapeComboBox } from "./editor/chart/aesthetic/shape-combo-box.component";
import { ShapeFieldMc } from "./editor/chart/aesthetic/shape-field-mc.component";
import { ShapeItem } from "./editor/chart/aesthetic/shape-item.component";
import { SizeCell } from "./editor/chart/aesthetic/size-cell.component";
import { SizeFieldMc } from "./editor/chart/aesthetic/size-field-mc.component";
import { StaticColorEditor } from "./editor/chart/aesthetic/static-color-editor.component";
import { StaticColorPane } from "./editor/chart/aesthetic/static-color-pane.component";
import { StaticLineEditor } from "./editor/chart/aesthetic/static-line-editor.component";
import { StaticLinePane } from "./editor/chart/aesthetic/static-line-pane.component";
import { StaticShapeEditor } from "./editor/chart/aesthetic/static-shape-editor.component";
import { StaticShapePane } from "./editor/chart/aesthetic/static-shape-pane.component";
import { StaticSizeEditor } from "./editor/chart/aesthetic/static-size-editor.component";
import { StaticSizePane } from "./editor/chart/aesthetic/static-size-pane.component";
import { StaticTextureEditor } from "./editor/chart/aesthetic/static-texture-editor.component";
import { StaticTexturePane } from "./editor/chart/aesthetic/static-texture-pane.component";
import { TextFieldMc } from "./editor/chart/aesthetic/text-field-mc.component";
import { TextureComboBox } from "./editor/chart/aesthetic/texture-combo-box.component";
import { TextureItem } from "./editor/chart/aesthetic/texture-item.component";
import { VisualDropdownPane } from "./editor/chart/aesthetic/visual-dropdown-pane.component";
import { ChartDataEditor } from "./editor/chart/chart-data-editor.component";
import { ChartDataPane } from "./editor/chart/chart-data-pane.component";
import { ChartEditorToolbar } from "./editor/chart/chart-editor-toolbar.component";
import { ChartHighLowPane } from "./editor/chart/chart-high-low-pane.component";
import { ColorMappingDialog } from "./editor/chart/color-mapping-dialog.component";
import { FieldPane } from "./editor/chart/field-pane.component";
import { AggregateEditor } from "./editor/chart/field/aggregate-editor.component";
import { CalculatePaneDialog } from "./editor/chart/field/calculate-pane-dialog.component";
import { CalculatePane } from "./editor/chart/field/calculate-pane.component";
import { ChartFieldmc } from "./editor/chart/field/chart-fieldmc.component";
import { DimensionEditor } from "./editor/chart/field/dimension-editor.component";
import { GeoMappingDialog } from "./editor/chart/field/geo-mapping-dialog.component";
import { GeoOptionPane } from "./editor/chart/field/geo-option-pane.component";
import { LonLatFieldmc } from "./editor/chart/lon-lat-fieldmc.component";
import { PaletteDialog } from "./editor/chart/palette-dialog.component";
import { EditorTitleBar } from "./editor/editor-title-bar.component";
import { FormatsPane } from "./editor/formats-pane.component";
import { FormulaOption } from "./editor/formula-option.component";
import { ManualOrderingDialog } from "./editor/manual-ordering-dialog.component";
import { NameInputDialog } from "./editor/name-input-dialog.component";
import { NamedGroupPane } from "./editor/named-group-pane.component";
import { NamedGroupView } from "./editor/named-group-view-dialog.component";
import { SortOption } from "./editor/sort-option.component";
import { AggregateOption } from "./editor/table/aggregate-option.component";
import { CalcAggregateOption } from "./editor/table/calc-aggregate-option.component";
import { CalcDataPane } from "./editor/table/calc-data-pane.component";
import { CalcGroupOption } from "./editor/table/calc-group-option.component";
import { CalcNamedGroupDialog } from "./editor/table/calc-named-group-dialog.component";
import { CalcOptionPane } from "./editor/table/calc-option-pane.component";
import { CrosstabDataPane } from "./editor/table/crosstab-data-pane.component";
import { CrosstabOption } from "./editor/table/crosstab-option.component";
import { DetailOption } from "./editor/table/detail-option.component";
import { FieldOption } from "./editor/table/field-option-dialog.component";
import { GroupOption } from "./editor/table/group-option.component";
import { InsertRowColDialog } from "./editor/table/insert-row-col-dialog.component";
import { TableDataEditor } from "./editor/table/table-data-editor.component";
import { TableDataPane } from "./editor/table/table-data-pane.component";
import { TableFieldmc } from "./editor/table/table-fieldmc.component";
import { TableFormatOption } from "./editor/table/table-format-option.component";
import { TableOption } from "./editor/table/table-option.component";
import { BindingTreeComponent } from "./widget/binding-tree/binding-tree.component";
import { DataEditorBindingTree } from "./widget/binding-tree/data-editor-binding-tree.component";
import { DataEditorTabPane } from "./widget/binding-tree/data-editor-tab-pane.component";
import { EditGeographicDialog } from "./widget/binding-tree/edit-geographic-dialog.component";
import { CreateCalcDialog } from "./widget/calculate-dialog/create-calc-dialog.component";
import { CreateMeasureDialog } from "./widget/calculate-dialog/create-measure-dialog.component";
import { ChartStylePane } from "./widget/chart-style-pane.component";
import { ChartTypeButton } from "./widget/chart-type-button.component";
import { ColorFieldPane } from "./widget/color-field-pane.component";
import { DraggableDirective } from "./widget/draggable.directive";
import { DropHighlightDirective } from "./widget/drophighlight.directive";
import { RangeSlider } from "./widget/range-slider.component";
import { Slider } from "./widget/slider.component";
import { ExpertNamedGroupDialog } from "./editor/table/expert-named-group-dialog.component";
import { StatusBarModule } from "../status-bar/status-bar.module";
import { WidgetDirectivesModule } from "../widget/directive/widget-directives.module";
import { ColorPickerModule } from "../widget/color-picker/color-picker.module";
import { SplitPaneModule } from "../widget/split-pane/split-pane.module";
import { ToolbarGroupModule } from "../widget/toolbar/toolbar-group/toolbar-group.module";
import { HelpLinkModule } from "../widget/help-link/help-link.module";
import { MouseEventModule } from "../widget/mouse-event/mouse-event.module";
import { LargeFormFieldModule } from "../widget/large-form-field/large-form-field.module";
import { FixedDropdownModule } from "../widget/fixed-dropdown/fixed-dropdown.module";
import {
   VSAssemblyScriptPaneModule
} from "../widget/dialog/vsassembly-script-pane/vsassembly-script-pane.module";
import { FormulaEditorModule } from "../widget/formula-editor/formula-editor.module";
import { DropdownViewModule } from "../widget/dropdown-view/dropdown-view.module";
import { FontPaneModule } from "../widget/font-pane/font-pane.module";
import { ConditionModule } from "../widget/condition/condition.module";
import { NgbDropdownModule } from "@ng-bootstrap/ng-bootstrap";
import { ScrollableTableModule } from "../widget/scrollable-table/scrollable-table.module";
import { TooltipModule } from "../widget/tooltip/tooltip.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      FormatModule,
      WidgetDirectivesModule,
      ColorPickerModule,
      ModalHeaderModule,
      TreeModule,
      DynamicComboBoxModule,
      SlideOutModule,
      NotificationsModule,
      SplitPaneModule,
      ToolbarGroupModule,
      HelpLinkModule,
      MouseEventModule,
      LargeFormFieldModule,
      FixedDropdownModule,
      VSAssemblyScriptPaneModule,
      FormulaEditorModule,
      DropdownViewModule,
      FontPaneModule,
      ConditionModule,
      NgbDropdownModule,
      ScrollableTableModule,
      TooltipModule,
      StatusBarModule,
      ConsoleDialogModule
   ],
   declarations: [
      AestheticPane,
      AggregateEditor,
      AggregateOption,
      BindingEditor,
      BindingSizePane,
      BindingTreeComponent,
      CalcAggregateOption,
      CalcDataPane,
      CalcGroupOption,
      CalcNamedGroupDialog,
      CalcOptionPane,
      CalculatePane,
      CalculatePaneDialog,
      CategoricalColorPane,
      CategoricalShapePane,
      ChartAestheticMc,
      ChartDataEditor,
      ChartDataPane,
      ChartEditorToolbar,
      ChartFieldmc,
      ChartHighLowPane,
      ChartStylePane,
      ChartTypeButton,
      ColorCell,
      ColorFieldMc,
      ColorFieldPane,
      ColorMappingDialog,
      CombinedColorPane,
      CombinedShapePane,
      CombinedSizePane,
      CreateCalcDialog,
      CreateMeasureDialog,
      CrosstabDataPane,
      CrosstabOption,
      DataEditorBindingTree,
      DataEditorTabPane,
      DetailOption,
      DimensionEditor,
      EditGeographicDialog,
      EditorTitleBar,
      ExpertNamedGroupDialog,
      FieldOption,
      FieldPane,
      FormatsPane,
      FormulaOption,
      GeoMappingDialog,
      GeoOptionPane,
      GradientColorEditor,
      GroupOption,
      HslColorEditor,
      InsertRowColDialog,
      LinearColorDropdown,
      LinearColorPane,
      LinearLinePane,
      LinearShapePane,
      LinearTexturePane,
      LineComboBox,
      LineItem,
      LonLatFieldmc,
      ManualOrderingDialog,
      NamedGroupPane,
      NamedGroupView,
      NameInputDialog,
      PaletteDialog,
      RangeSlider,
      ShapeCell,
      ShapeComboBox,
      ShapeFieldMc,
      ShapeItem,
      SizeCell,
      SizeFieldMc,
      Slider,
      SortOption,
      StaticColorEditor,
      StaticColorPane,
      StaticLineEditor,
      StaticLinePane,
      StaticShapeEditor,
      StaticShapePane,
      StaticSizeEditor,
      StaticSizePane,
      StaticTextureEditor,
      StaticTexturePane,
      TableDataEditor,
      TableDataPane,
      TableFieldmc,
      TableFormatOption,
      TableOption,
      TextFieldMc,
      TextureComboBox,
      TextureItem,
      VisualDropdownPane,
      DraggableDirective,
      DropHighlightDirective
   ],
   exports: [
      AestheticPane,
      AggregateEditor,
      AggregateOption,
      BindingEditor,
      BindingSizePane,
      BindingTreeComponent,
      CalcAggregateOption,
      CalcDataPane,
      CalcGroupOption,
      CalcNamedGroupDialog,
      CalcOptionPane,
      CalculatePane,
      CalculatePaneDialog,
      CategoricalColorPane,
      CategoricalShapePane,
      ChartAestheticMc,
      ChartDataEditor,
      ChartDataPane,
      ChartEditorToolbar,
      ChartFieldmc,
      ChartHighLowPane,
      ChartStylePane,
      ChartTypeButton,
      ColorCell,
      ColorFieldMc,
      ColorFieldPane,
      ColorMappingDialog,
      CombinedColorPane,
      CombinedShapePane,
      CombinedSizePane,
      CreateCalcDialog,
      CreateMeasureDialog,
      CrosstabDataPane,
      CrosstabOption,
      DataEditorBindingTree,
      DataEditorTabPane,
      DetailOption,
      DimensionEditor,
      EditGeographicDialog,
      EditorTitleBar,
      ExpertNamedGroupDialog,
      FieldOption,
      FieldPane,
      FormatsPane,
      FormulaOption,
      GeoMappingDialog,
      GeoOptionPane,
      GradientColorEditor,
      GroupOption,
      HslColorEditor,
      InsertRowColDialog,
      LinearColorDropdown,
      LinearColorPane,
      LinearLinePane,
      LinearShapePane,
      LinearTexturePane,
      LineComboBox,
      LineItem,
      LonLatFieldmc,
      ManualOrderingDialog,
      NamedGroupPane,
      NamedGroupView,
      NameInputDialog,
      PaletteDialog,
      RangeSlider,
      ShapeCell,
      ShapeComboBox,
      ShapeFieldMc,
      ShapeItem,
      SizeCell,
      SizeFieldMc,
      Slider,
      SortOption,
      StaticColorEditor,
      StaticColorPane,
      StaticLineEditor,
      StaticLinePane,
      StaticShapeEditor,
      StaticShapePane,
      StaticSizeEditor,
      StaticSizePane,
      StaticTextureEditor,
      StaticTexturePane,
      TableDataEditor,
      TableDataPane,
      TableFieldmc,
      TableFormatOption,
      TableOption,
      TextFieldMc,
      TextureComboBox,
      TextureItem,
      VisualDropdownPane,
      DraggableDirective,
      DropHighlightDirective
   ]
})
export class BindingModule {
   static forRoot(concreteProviders: any): ModuleWithProviders<BindingModule> {
      return {
         ngModule: BindingModule,
         providers: concreteProviders
      };
   }
}