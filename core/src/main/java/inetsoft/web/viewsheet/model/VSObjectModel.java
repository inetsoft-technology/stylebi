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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.*;
import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo.PopLocation;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.ComposerAdhocFilterController;
import inetsoft.web.viewsheet.model.annotation.VSAnnotationModel;
import inetsoft.web.viewsheet.model.calendar.VSCalendarModel;
import inetsoft.web.viewsheet.model.chart.VSChartModel;
import inetsoft.web.viewsheet.model.table.*;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@JsonSubTypes({
   @JsonSubTypes.Type(value = VSAnnotationModel.class, name = "VSAnnotation"),
   @JsonSubTypes.Type(value = VSCalcTableModel.class, name = "VSCalcTable"),
   @JsonSubTypes.Type(value = VSCalendarModel.class, name = "VSCalendar"),
   @JsonSubTypes.Type(value = VSChartModel.class, name = "VSChart"),
   @JsonSubTypes.Type(value = VSCheckBoxModel.class, name = "VSCheckBox"),
   @JsonSubTypes.Type(value = VSComboBoxModel.class, name = "VSComboBox"),
   @JsonSubTypes.Type(value = VSCrosstabModel.class, name = "VSCrosstab"),
   @JsonSubTypes.Type(value = VSCylinderModel.class, name = "VSCylinder"),
   @JsonSubTypes.Type(value = VSEmbeddedTableModel.class, name = "VSTable"),
   @JsonSubTypes.Type(value = VSGaugeModel.class, name = "VSGauge"),
   @JsonSubTypes.Type(value = VSGroupContainerModel.class, name = "VSGroupContainer"),
   @JsonSubTypes.Type(value = VSImageModel.class, name = "VSImage"),
   @JsonSubTypes.Type(value = VSLineModel.class, name = "VSLine"),
   @JsonSubTypes.Type(value = VSOvalModel.class, name = "VSOval"),
   @JsonSubTypes.Type(value = VSPageBreakModel.class, name = "VSPageBreak"),
   @JsonSubTypes.Type(value = VSRadioButtonModel.class, name = "VSRadioButton"),
   @JsonSubTypes.Type(value = VSRangeSliderModel.class, name = "VSRangeSlider"),
   @JsonSubTypes.Type(value = VSRectangleModel.class, name = "VSRectangle"),
   @JsonSubTypes.Type(value = VSSelectionListModel.class, name = "VSSelectionList"),
   @JsonSubTypes.Type(value = VSSelectionTreeModel.class, name = "VSSelectionTree"),
   @JsonSubTypes.Type(value = VSSliderModel.class, name = "VSSlider"),
   @JsonSubTypes.Type(value = VSSlidingScaleModel.class, name = "VSSlidingScale"),
   @JsonSubTypes.Type(value = VSSpinnerModel.class, name = "VSSpinner"),
   @JsonSubTypes.Type(value = VSSubmitModel.class, name = "VSSubmit"),
   @JsonSubTypes.Type(value = VSTabModel.class, name = "VSTab"),
   @JsonSubTypes.Type(value = VSTableModel.class, name = "VSTable"),
   @JsonSubTypes.Type(value = VSTextInputModel.class, name = "VSTextInput"),
   @JsonSubTypes.Type(value = VSTextModel.class, name = "VSText"),
   @JsonSubTypes.Type(value = VSThermometerModel.class, name = "VSThermometer"),
   @JsonSubTypes.Type(value = VSViewsheetModel.class, name = "VSViewsheet"),
   @JsonSubTypes.Type(value = VSSelectionContainerModel.class, name = "VSSelectionContainer")
})
@JsonTypeInfo(
   include = JsonTypeInfo.As.PROPERTY,
   use = JsonTypeInfo.Id.NAME,
   property = "objectType"
)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class VSObjectModel<T extends VSAssembly> {
   public VSObjectModel(T assembly, RuntimeViewsheet rvs) {
      this(assembly, (VSAssemblyInfo) assembly.getInfo(), rvs);
   }

   protected VSObjectModel(T assembly, VSAssemblyInfo assemblyInfo, RuntimeViewsheet rvs) {
      VSCompositeFormat compositeFormat = assemblyInfo.getFormat();
      Viewsheet vs = assembly.getViewsheet();
      enabled = assembly.isEnabled();
      visible = vs.isVisible(assembly, Viewsheet.SHEET_RUNTIME_MODE);
      description = assemblyInfo.getDescription();
      script = assemblyInfo.getScript();
      scriptEnabled = assemblyInfo.isScriptEnabled();
      absoluteName = assemblyInfo.getAbsoluteName();
      objectFormat = createFormatModel(compositeFormat, assemblyInfo);
      inEmbeddedViewsheet = vs.getViewsheet() != null;
      boolean binding = rvs != null && rvs.isBinding();
      boolean wizard = rvs != null && rvs.getVSTemporaryInfo() != null;

      if(assemblyInfo instanceof DataVSAssemblyInfo) {
         hasCondition = ((DataVSAssemblyInfo) assemblyInfo).getPreConditionList() != null;
      }
      else if(assemblyInfo instanceof OutputVSAssemblyInfo) {
         hasCondition = ((OutputVSAssemblyInfo) assemblyInfo).getPreConditionList() != null;
      }

      // sheetMaxMode is true if the (topmost) viewsheet is in max mode
      for(Viewsheet p = vs; p != null; p = p.getViewsheet()) {
         sheetMaxMode = p.isMaxMode();
      }

      if(sheetMaxMode && !(assembly instanceof Viewsheet)) {
         visible = assemblyInfo instanceof DataVSAssemblyInfo &&
            ((DataVSAssemblyInfo) assemblyInfo).getMaxSize() != null ||
            assemblyInfo instanceof MaxModeSupportAssemblyInfo &&
               ((MaxModeSupportAssemblyInfo) assemblyInfo).getMaxSize() != null;
      }

      // in editing mode, if chart is white on dark, keep the dark background
      if((sheetMaxMode || binding || wizard) &&
         StringUtils.isEmpty(objectFormat.getBackground()))
      {
         FormatInfo vsFmtInfo = vs.getFormatInfo();
         VSCompositeFormat vsFmt = vsFmtInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

         if(vsFmt != null && vsFmt.getBackground() != null) {
            objectFormat.setBackground(VSCSSUtil.getBackgroundColor(vsFmt.getBackground()));
         }
         else {
            objectFormat.setBackground(VSCSSUtil.getBackgroundColor(Color.white));
         }
      }

      // get the absolute tip/pop name and alpha
      if(assemblyInfo instanceof TipVSAssemblyInfo) {
         final String tipName = ((TipVSAssemblyInfo) assemblyInfo).getTipView();

         if(tipName != null && VSEventUtil.checkTipViewValid(assembly, vs)) {
            VSAssembly tipAssembly = vs.getAssembly(tipName);
            this.dataTip = tipAssembly.getAbsoluteName();
            this.dataTipAlpha = ((TipVSAssemblyInfo) assemblyInfo).getAlpha();
         }
      }
      else if(assemblyInfo instanceof PopVSAssemblyInfo) {
         final String popName = ((PopVSAssemblyInfo) assemblyInfo).getPopComponent();

         if(popName != null && VSEventUtil.checkPopComponentValid(assembly, vs)) {
            VSAssembly popAssembly = vs.getAssembly(popName);
            this.popComponent = popAssembly.getAbsoluteName();
            this.popAlpha = ((PopVSAssemblyInfo) assemblyInfo).getAlpha();
            this.popLocation = ((PopVSAssemblyInfo) assemblyInfo).getPopLocation();
         }
      }

      if(assembly instanceof DrillFilterVSAssembly && !DateComparisonUtil.appliedDateComparison(assemblyInfo)) {
         drillTip = ((DrillFilterVSAssembly) assembly).getDrillDescription();
      }

      // if it's a data tip view, the layout position may not be meaningful. it could
      // cause problem if the container is added to layout but children were not
      Point pos = assemblyInfo.getLayoutPosition() != null ? assemblyInfo.getLayoutPosition() :
         vs.getPixelPosition(assemblyInfo);
      Dimension size = assemblyInfo.getLayoutSize();

      // in case the assembly is no longer available for layout (e.g. grouped into a
      // group container), we don't want to set it's size to 0 otherwise it may
      // generate an error and will not be visible
      if(size == null || size.width == 0 || size.height == 0) {
         size = vs.getPixelSize(assemblyInfo);
      }

      objectFormat.setPositions(pos, size);
      objectFormat.setzIndex(assemblyInfo.getZIndex());
      final VSAssembly objContainer = assembly.getContainer();
      final boolean visibleInTab = VSUtil.isVisibleInTab(assembly, vs, rvs != null && rvs.isRuntime());

      if(objContainer != null) {
         container = objContainer.getAbsoluteName();

         if(objContainer instanceof TabVSAssembly) {
            active = visibleInTab;
            containerType = "VSTab";
         }
         else if(objContainer instanceof CurrentSelectionVSAssembly) {
            active = vs.isVisible(objContainer, Viewsheet.SHEET_RUNTIME_MODE);
            containerType = "VSSelectionContainer";
         }
         else if(objContainer instanceof GroupContainerVSAssembly) {
            grouped = true;

            if(VSUtil.isInTab(objContainer)) {
               active = visibleInTab;
               containerType = "VSTab";
            }
            else {
               active = true;
               containerType = "VSGroupContainer";
            }
         }
      }
      else {
         active = true;
         grouped = false;
      }

      // create models for non-table type annotations
      if(assemblyInfo instanceof BaseAnnotationVSAssemblyInfo) {
         final Map<Integer, List<VSAnnotationModel>> annotationModels = getAnnotationModels(
            (VSAssemblyInfo & BaseAnnotationVSAssemblyInfo) assemblyInfo);
         assemblyAnnotationModels = annotationModels.get(AnnotationVSAssemblyInfo.ASSEMBLY);
         dataAnnotationModels = annotationModels.get(AnnotationVSAssemblyInfo.DATA);
      }

      actionNames = assemblyInfo.getActionNames().toArray(new String[0]);
      advancedStatus = computeAdvancedStatus(assemblyInfo);
      cubeType = VSUtil.getCubeType(assembly);
      wsCube = VSUtil.isWorksheetCube(assembly);
      genTime = System.currentTimeMillis();
      adhocFilterEnabled = !ComposerAdhocFilterController.findContainers(vs, assembly).isEmpty();

      if(assemblyInfo instanceof DataVSAssemblyInfo) {
         SourceInfo sourceInfo = ((DataVSAssemblyInfo) assemblyInfo).getSourceInfo();
         sourceType = sourceInfo != null ? sourceInfo.getType() : SourceInfo.NONE;

         if(assemblyInfo instanceof ChartVSAssemblyInfo) {
            ChartInfo cinfo = ((ChartVSAssemblyInfo) assemblyInfo).getVSChartInfo();

            if(GraphTypeUtil.isScatterMatrix(cinfo)) {
               adhocFilterEnabled = false;
            }
         }
      }
      else if(assemblyInfo instanceof SelectionVSAssemblyInfo) {
         sourceType = ((SelectionVSAssemblyInfo) assemblyInfo).getSourceType();
      }
   }

   protected VSFormatModel createFormatModel(VSCompositeFormat compositeFormat,
                                             VSAssemblyInfo assemblyInfo)
   {
      return new VSFormatModel(compositeFormat, assemblyInfo);
   }

   private <U extends VSAssemblyInfo & BaseAnnotationVSAssemblyInfo>
   Map<Integer, List<VSAnnotationModel>> getAnnotationModels(U assemblyInfo)
   {
      if(!enabled || assemblyInfo.getAnnotations().isEmpty()) {
         return Collections.emptyMap();
      }

      // Get the names of any child annotations and create their models
      // Get the annotation assemblies from the top viewsheet.
      final Viewsheet viewsheet = VSUtil.getTopViewsheet(assemblyInfo.getViewsheet());

      return assemblyInfo.getAnnotations()
         .stream()
         .map(viewsheet::getAssembly)
         .filter(AnnotationVSAssembly.class::isInstance)
         .map(AnnotationVSAssembly.class::cast)
         .collect(Collectors.groupingBy(annotation -> {
            AnnotationVSAssemblyInfo info =
               (AnnotationVSAssemblyInfo) annotation.getVSAssemblyInfo();
            return info.getType();
         }, Collectors.mapping(VSAnnotationModel::create, Collectors.toList())));
   }

   private String computeAdvancedStatus(VSAssemblyInfo info) {
      if(info instanceof CalendarVSAssemblyInfo ||
         info instanceof SelectionTreeVSAssemblyInfo ||
         info instanceof SelectionListVSAssemblyInfo ||
         info instanceof TimeSliderVSAssemblyInfo)
      {
         final SelectionVSAssemblyInfo sInfo = (SelectionVSAssemblyInfo) info;

         if(sInfo.getTableName() != null) {
            final String tablesPrefix = sInfo.getTableNames().stream()
               .map(name -> getDisplayTableName(name))
               .collect(Collectors.joining(", ", "[", ": "));
            return Arrays.stream(sInfo.getDataRefs())
               .map(Object::toString).collect(Collectors.joining(", ", tablesPrefix, "]"));
         }
      }
      else if(info instanceof DataVSAssemblyInfo) {
         DataVSAssemblyInfo cInfo = (DataVSAssemblyInfo) info;

         if(cInfo.getSourceInfo() != null && cInfo.getSourceInfo().getSource() != null) {
            return "[" + getDisplayTableName(cInfo.getSourceInfo().getSource()) + "]";
         }
      }
      else if(info instanceof InputVSAssemblyInfo) {
         InputVSAssemblyInfo cInfo = (InputVSAssemblyInfo) info;
         StringBuilder str = new StringBuilder();

         if(cInfo instanceof ListInputVSAssemblyInfo) {
            BindingInfo binding = ((ListInputVSAssemblyInfo) cInfo).getBindingInfo();

            if(binding instanceof ScalarBindingInfo) {
               str.append(getDisplayTableName(binding.getTableName())).append(": ")
                  .append(((ScalarBindingInfo) binding).getColumnValue());
            }
         }

         if(cInfo.getTableName() != null && !cInfo.getTableName().isEmpty()) {
            if(cInfo.isVariable()) {
               String tableName = getDisplayTableName(cInfo.getTableName());

               if(!tableName.startsWith("$(")) {
                  tableName = "$(" + tableName + ")";
               }

               str.append(tableName);
            }
            else {
               str.append(' ').append(getDisplayTableName(cInfo.getTableName())).append(' ')
                  .append(cInfo.getColumnValue()).append(":").append(cInfo.getRowValue());
            }
         }

         String result = str.toString().trim();
         return result.isEmpty() ? result : "[" + str + "]";
      }
      else if(info instanceof OutputVSAssemblyInfo) {
         OutputVSAssemblyInfo cInfo = (OutputVSAssemblyInfo) info;
         BindingInfo binding = cInfo.getBindingInfo();

         if(binding != null && binding.getTableName() != null) {
            String str;

            if(binding instanceof ScalarBindingInfo) {
               String aggregate = ((ScalarBindingInfo) binding).getAggregateValue();

               if(aggregate == null || "null".equals(aggregate)) {
                  aggregate = "None";
               }

               str = getDisplayTableName(binding.getTableName()) + ": " +
                  Catalog.getCatalog().getString(aggregate) + "(" +
                  ((ScalarBindingInfo) binding).getColumnValue() + ")";
            }
            else {
               str = getDisplayTableName(binding.getTableName());
            }

            Hyperlink.Ref[] hlinks = cInfo.getHyperlinks();
            StringBuilder linkStr = new StringBuilder();

            for(Hyperlink.Ref ref : hlinks) {
               if(linkStr.length() > 0) {
                  linkStr.append(", ");
               }

               String link = ref.getLink();

               if(ref.getLinkType() == Hyperlink.VIEWSHEET_LINK && !Tool.isEmptyString(link) &&
                  link.startsWith("1^128^__NULL__^"))
               {
                  link = link.substring("1^128^__NULL__^".length());
               }

               linkStr.append(link);
            }

            if(linkStr.length() > 0) {
               str = "[" + str + "-" + linkStr + "]";
            }
            else {
               str = str.isEmpty() ? str : "[" + str + "]";
            }

            return str;
         }
      }
      else if(info instanceof ViewsheetVSAssemblyInfo) {
         ViewsheetVSAssemblyInfo cInfo = (ViewsheetVSAssemblyInfo) info;
         AssetEntry entry = cInfo.getEntry();

         if(entry != null && !entry.getDescription().isEmpty()) {
            return "[" + entry.getDescription() + "]";
         }
      }

      return "";
   }

   private String getDisplayTableName(String tableName) {
      if(tableName == null) {
         return null;
      }

      if(tableName.startsWith("___inetsoft_cube_")) {
         return tableName.substring(17);
      }
      else if(VSUtil.isVSAssemblyBinding(tableName)) {
         return VSUtil.getVSAssemblyBinding(tableName);
      }

      return VSUtil.stripOuter(tableName);
   }

   public VSFormatModel getObjectFormat() {
      return objectFormat;
   }

   public boolean isEnabled() {
      return enabled;
   }

   public boolean isScriptEnabled() {
      return scriptEnabled;
   }

   public boolean isVisible() {
      return visible;
   }

   public String getDescription() {
      return description;
   }

   public String getScript() {
      return script;
   }

   public boolean isHasCondition() {
      return hasCondition;
   }

   public String getAbsoluteName() {
      return absoluteName;
   }

   public boolean isActive() {
      return active;
   }

   public String getContainer() {
      return container;
   }

   public String getContainerType() {
      return containerType;
   }

   public boolean isGrouped() {
      return grouped;
   }

   public String getDataTip() {
      return dataTip;
   }

   public String getDataTipAlpha() {
      return dataTipAlpha;
   }

   public String getPopComponent() {
      return popComponent;
   }

   public String getPopAlpha() {
      return popAlpha;
   }

   public boolean isInEmbeddedViewsheet() {
      return inEmbeddedViewsheet;
   }

   public List<VSAnnotationModel> getAssemblyAnnotationModels() {
      return assemblyAnnotationModels;
   }

   public final void setAssemblyAnnotationModels(List<VSAnnotationModel> assemblyAnnotationModels) {
      this.assemblyAnnotationModels = assemblyAnnotationModels;
   }

   public List<VSAnnotationModel> getDataAnnotationModels() {
      return dataAnnotationModels;
   }

   public final void setDataAnnotationModels(List<VSAnnotationModel> dataAnnotationModels) {
      this.dataAnnotationModels = dataAnnotationModels;
   }

   public String[] getActionNames() {
      return actionNames;
   }

   public String getAdvancedStatus() {
      return advancedStatus;
   }

   public long getGenTime() {
      return genTime;
   }

   public void setGenTime(long genTime) {
      this.genTime = genTime;
   }

   public String getCubeType() {
      return cubeType;
   }

   public boolean isWorksheetCube() {
      return wsCube;
   }

   /**
    * Check if an adhoc filter is present
    */
   public boolean isAdhocFilterEnabled() {
      return adhocFilterEnabled;
   }

   public int getSourceType() {
      return sourceType;
   }

   public boolean isSheetMaxMode() {
      return this.sheetMaxMode;
   }

   public String getDrillTip() {
      return drillTip;
   }

   public String toString() {
      return "{objectFormat:" + objectFormat + " " +
         "enabled:" + enabled + " " +
         "visible:" + visible + " " +
         "description:" + description + " " +
         "script:" + script + " " +
         "scriptEnabled:" + scriptEnabled + " " +
         "hasCondition:" + hasCondition + " " +
         "absoluteName:" + absoluteName + " " +
         "selected:" + active + "} ";
   }

   public boolean isHasDynamic() {
      return hasDynamic;
   }

   /**
    * if assembly has dynamic field, we don't support open object-wizard-pane;
    * @param hasDynamic
    */
   public void setHasDynamic(boolean hasDynamic) {
      this.hasDynamic = hasDynamic;
   }

   public void setPopLocation(PopLocation popLocation) {this.popLocation = popLocation;}

   public PopLocation getPopLocation() { return popLocation;}

   private VSFormatModel objectFormat;
   private boolean enabled;
   private boolean visible;
   private String description;
   private String script;
   private boolean hasCondition;
   private boolean scriptEnabled;
   private String absoluteName;
   private boolean active;
   private String container;
   private String containerType;
   private boolean grouped;
   private String dataTip;
   private String dataTipAlpha;
   private String popComponent;
   private PopLocation popLocation;
   private String popAlpha;
   private boolean inEmbeddedViewsheet;
   // Names of annotations affixed to the assembly
   private List<VSAnnotationModel> assemblyAnnotationModels;
   // Names of annotation affixed to the assembly data
   private List<VSAnnotationModel> dataAnnotationModels;
   private String[] actionNames;
   private String advancedStatus;
   private long genTime;
   private boolean adhocFilterEnabled;
   private String cubeType;
   private boolean wsCube;
   private int sourceType;
   private boolean sheetMaxMode;
   private boolean hasDynamic;
   private String drillTip;
}
