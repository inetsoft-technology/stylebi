/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

@Controller
public class SelectionTreePropertyDialogController {
   /**
    * Creates a new instance of <tt>SelectionTreePropertyController</tt>.
    *  @param vsObjectPropertyService VSObjectPropertyService instance
    * @param vsOutputService         VSOutputService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public SelectionTreePropertyDialogController(VSObjectPropertyService vsObjectPropertyService,
                                                VSOutputService vsOutputService,
                                                RuntimeViewsheetRef runtimeViewsheetRef,
                                                ViewsheetService viewsheetService,
                                                VSTrapService trapService,
                                                VSDialogService dialogService,
                                                VSSelectionService vsSelectionService,
                                                SelectionDialogService selectionDialogService,
                                                VSAssemblyInfoHandler assemblyInfoHandler,
                                                DataRefModelFactoryService dataRefService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.trapService = trapService;
      this.dialogService = dialogService;
      this.vsSelectionService = vsSelectionService;
      this.selectionDialogService = selectionDialogService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.dataRefService = dataRefService;
   }

   /**
    * Gets the selection tree property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the selection tree id
    * @return the selection tree property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/selection-tree-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SelectionTreePropertyDialogModel getSelectionTreePropertyModel(
      @PathVariable("objectId") String objectId,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      SelectionTreeVSAssembly selectionTreeAssembly;
      SelectionTreeVSAssemblyInfo selectionTreeAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         selectionTreeAssembly = (SelectionTreeVSAssembly) vs.getAssembly(objectId);
         selectionTreeAssemblyInfo = (SelectionTreeVSAssemblyInfo) selectionTreeAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      SelectionTreePropertyDialogModel result = new SelectionTreePropertyDialogModel();
      SelectionGeneralPaneModel selectionGeneralPane = result.getSelectionGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = selectionGeneralPane.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = selectionGeneralPane.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         selectionGeneralPane.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SelectionTreePaneModel selectionTreePaneModel = result.getSelectionTreePaneModel();
      SelectionMeasurePaneModel selectionMeasurePaneModel = selectionTreePaneModel.getSelectionMeasurePaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel = VSAssemblyScriptPaneModel.builder();

      titlePropPaneModel.setVisible(selectionTreeAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(selectionTreeAssemblyInfo.getTitleValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(selectionTreeAssemblyInfo.getEnabledValue());

      Point pos = dialogService.getAssemblyPosition(selectionTreeAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(selectionTreeAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(selectionTreeAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(selectionTreeAssembly.getContainer() != null);

      int cellHeight = selectionTreeAssemblyInfo.getCellHeight();
      sizePositionPaneModel.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);

      basicGeneralPaneModel.setName(selectionTreeAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(selectionTreeAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(selectionTreeAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, selectionTreeAssemblyInfo.getAbsoluteName()));

      selectionGeneralPane.setShowType(selectionTreeAssemblyInfo.getShowTypeValue());
      selectionGeneralPane.setListHeight(selectionTreeAssemblyInfo.getListHeight());
      selectionGeneralPane.setSortType(selectionTreeAssemblyInfo.getSortTypeValue());
      selectionGeneralPane.setSubmitOnChange(selectionTreeAssemblyInfo.getSubmitOnChangeValue());
      selectionGeneralPane.setSingleSelection(selectionTreeAssemblyInfo.getSingleSelectionValue());
      selectionGeneralPane.setSuppressBlank(selectionTreeAssemblyInfo.isSuppressBlankValue());
      selectionGeneralPane.setSelectFirstItem(selectionTreeAssemblyInfo.getSelectFirstItemValue());

      if(selectionTreeAssemblyInfo.getSingleSelectionLevels() != null) {
         selectionGeneralPane.setSingleSelectionLevels(
            selectionTreeAssemblyInfo.getSingleSelectionLevelNames());
      }

      String parentId = selectionTreeAssemblyInfo.getParentIDValue();
      String id = selectionTreeAssemblyInfo.getIDValue();
      String label = selectionTreeAssemblyInfo.getLabelValue();
      final String selectedTableName = selectionTreeAssemblyInfo.getFirstTableName();
      selectionTreePaneModel.setMode(selectionTreeAssemblyInfo.getMode());
      selectionTreePaneModel.setSelectChildren(selectionTreeAssemblyInfo.isSelectChildren());
      selectionTreePaneModel.setParentId(parentId);
      selectionTreePaneModel.setId(id);
      selectionTreePaneModel.setLabel(label);
      selectionTreePaneModel.setSelectedTable(selectedTableName);
      selectionTreePaneModel.setAdditionalTables(selectionTreeAssemblyInfo.getAdditionalTableNames());
      selectionTreePaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));
      final TreeNodeModel tree = vsOutputService.getSelectionTablesTree(rvs, principal);
      selectionTreePaneModel.setTargetTree(tree);
      DataRef[] dataRefs = selectionTreeAssemblyInfo.getDataRefs();

      if(dataRefs != null && dataRefs.length > 0) {
         List<OutputColumnRefModel> columns = new ArrayList<>();

         for(DataRef dataRef : dataRefs) {
            ColumnRef columnRef = (ColumnRef) dataRef;
            final Optional<OutputColumnRefModel> refModel = selectionDialogService
               .findSelectedOutputColumnRefModel(tree, selectedTableName, columnRef);

            if(refModel.isPresent()) {
               final OutputColumnRefModel columnModel = refModel.get();
               columns.add(columnModel);

               if(selectionTreeAssemblyInfo.getMode() == SelectionTreeVSAssemblyInfo.ID) {
                  if(parentId != null && columnRef.getName().equals(parentId)) {
                     selectionTreePaneModel.setParentIdRef(columnModel);
                  }

                  if(id != null && columnRef.getName().equals(id)) {
                     selectionTreePaneModel.setIdRef(columnModel);
                  }

                  if(label != null && columnRef.getName().equals(label)) {
                     selectionTreePaneModel.setLabelRef(columnModel);
                  }
               }
            }
         }

         if(selectionTreeAssemblyInfo.getMode() == SelectionTreeVSAssemblyInfo.COLUMN) {
            selectionTreePaneModel.setSelectedColumns(columns.toArray(new OutputColumnRefModel[0]));
         }
      }

      selectionMeasurePaneModel.setMeasure(selectionTreeAssemblyInfo.getMeasureValue());
      selectionMeasurePaneModel.setFormula(selectionTreeAssemblyInfo.getFormulaValue());
      selectionMeasurePaneModel.setShowText(selectionTreeAssemblyInfo.isShowTextValue());
      selectionMeasurePaneModel.setShowBar(selectionTreeAssemblyInfo.isShowBarValue());

      vsAssemblyScriptPaneModel.scriptEnabled(selectionTreeAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(selectionTreeAssemblyInfo.getScript() == null ?
                                              "" : selectionTreeAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the specified selection tree assembly info.
    *
    * @param objectId   the selection tree id
    * @param value the selection tree property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/selection-tree-property-dialog-model/{objectId}")
   public void setSelectionTreePropertyModel(@DestinationVariable("objectId") String objectId,
                                             @Payload SelectionTreePropertyDialogModel value,
                                             @LinkUri String linkUri,
                                             Principal principal,
                                             CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      SelectionTreeVSAssemblyInfo streeInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         SelectionTreeVSAssembly selectionTreeAssembly = (SelectionTreeVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         streeInfo = (SelectionTreeVSAssemblyInfo) Tool.clone(selectionTreeAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      boolean osingleSelection = streeInfo.isSingleSelection();
      List<Integer> osingleLevels = streeInfo.getSingleSelectionLevels();
      SelectionGeneralPaneModel selectionGeneralPane = value.getSelectionGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = selectionGeneralPane.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = selectionGeneralPane.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         selectionGeneralPane.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SelectionTreePaneModel selectionTreePaneModel = value.getSelectionTreePaneModel();
      SelectionMeasurePaneModel selectionMeasurePaneModel = selectionTreePaneModel.getSelectionMeasurePaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      streeInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      streeInfo.setTitleValue(titlePropPaneModel.getTitle());

      dialogService.setAssemblySize(streeInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(streeInfo, sizePositionPaneModel);
      streeInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
      streeInfo.setCellHeight(sizePositionPaneModel.getCellHeight());

      streeInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      streeInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      streeInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      streeInfo.setShowTypeValue(selectionGeneralPane.getShowType());

      Dimension size = viewsheet.getViewsheet().getPixelSize(streeInfo);

      if(streeInfo.getShowTypeValue() == SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         size.height = streeInfo.getTitleHeight();
      }
      else {
         int minListHeight = streeInfo.getListHeight() * AssetUtil.defh;

         if(streeInfo.isTitleVisible()) {
            minListHeight += AssetUtil.defh;
         }

         if(minListHeight > size.height) {
            size.height = minListHeight;
         }
      }

      streeInfo.setListHeight(selectionGeneralPane.getListHeight());
      streeInfo.setSortTypeValue(selectionGeneralPane.getSortType());
      streeInfo.setSubmitOnChangeValue(selectionGeneralPane.isSubmitOnChange());
      streeInfo.setSuppressBlankValue(selectionGeneralPane.isSuppressBlank());
      streeInfo.setSelectFirstItemValue(selectionGeneralPane.isSelectFirstItem());

      setAssemblyInfoTables(streeInfo, selectionTreePaneModel);
      setAssemblyInfoDataRefs(streeInfo, selectionTreePaneModel);
      setAssemblyInfoMeasure(streeInfo, selectionMeasurePaneModel);

      streeInfo.setSingleSelectionValue(selectionGeneralPane.isSingleSelection());
      streeInfo.setSingleSelectionLevelNames(selectionGeneralPane.getSingleSelectionLevels());

      streeInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      streeInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, streeInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      Viewsheet vs = viewsheet.getViewsheet();
      SelectionTreeVSAssembly selectionTreeAssembly =
         (SelectionTreeVSAssembly) vs.getAssembly(basicGeneralPaneModel.getName());
      streeInfo = (SelectionTreeVSAssemblyInfo) selectionTreeAssembly.getVSAssemblyInfo();
      updateSelection(osingleSelection, osingleLevels, streeInfo, linkUri, principal, commandDispatcher);
   }

   /**
    * If value is selected by setting the single selection property to true in the property
    * dialog, update other selection lists/trees to stay in sync with the selected value
    */
   private void updateSelection(boolean osingleSelection, List<Integer> osingleLevels,
                                SelectionTreeVSAssemblyInfo streeInfo,
                                String linkUri, Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      if((!osingleSelection || !Tool.equals(osingleLevels, streeInfo.getSingleSelectionLevels())) &&
         streeInfo.isSingleSelection())
      {
         List<String> list = new ArrayList<>();
         collectSelectedValues(streeInfo.getCompositeSelectionValue(), list);
         ApplySelectionListEvent.Value value = new ApplySelectionListEvent.Value();
         value.setValue(list.toArray(new String[list.size()]));
         value.setSelected(true);

         ApplySelectionListEvent event = new ApplySelectionListEvent();
         event.setEventSource(streeInfo.getAbsoluteName());
         event.setType(ApplySelectionListEvent.Type.APPLY);
         List<ApplySelectionListEvent.Value> values = new ArrayList<ApplySelectionListEvent.Value>();
         values.add(value);
         event.setValues(values);

         Context context = vsSelectionService.createContext(runtimeViewsheetRef.getRuntimeId(),
            principal, dispatcher, linkUri);
         vsSelectionService.applySelection(streeInfo.getAbsoluteName(), event, context);
      }
   }

   private void collectSelectedValues(CompositeSelectionValue value, List<String> values) {
      if(value == null) {
         return;
      }

      SelectionList list = value.getSelectionList();

      if(list == null || list.getSelectionValueCount() == 0) {
         return;
      }

      SelectionValue selectionValue = list.getSelectionValue(0);

      if(selectionValue != null && selectionValue.isSelected()) {
         values.add(selectionValue.getValue());
      }

      if(selectionValue instanceof CompositeSelectionValue) {
         collectSelectedValues((CompositeSelectionValue) selectionValue, values);
      }
   }


   /**
    * Check whether the parameters set for the selection list will cause a trap.
    *
    * @param value     the selection tree property dialog model
    * @param objectId  the object id
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @PostMapping("api/composer/vs/selection-tree-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkVSTrap(@RequestBody SelectionTreePaneModel value,
                                       @PathVariable("objectId") String objectId,
                                       @RemainingPath String runtimeId,
                                       Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         SelectionTreeVSAssembly assembly =
            (SelectionTreeVSAssembly) rvs.getViewsheet().getAssembly(objectId);

         if(assembly == null) {
            return null;
         }

         SelectionTreeVSAssemblyInfo oldAssemblyInfo =
            (SelectionTreeVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());
         SelectionTreeVSAssemblyInfo newAssemblyInfo =
            (SelectionTreeVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());

         SelectionMeasurePaneModel selectionMeasurePaneModel = value.getSelectionMeasurePaneModel();

         setAssemblyInfoTables(newAssemblyInfo, value);
         setAssemblyInfoDataRefs(newAssemblyInfo, value);
         setAssemblyInfoMeasure(newAssemblyInfo, selectionMeasurePaneModel);

         return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
      }
      finally {
         box.unlockRead();
      }
   }

   @PostMapping("api/composer/vs/selection-tree-property-dialog-model/getGrayedOutFields/{objectId}/**")
   @ResponseBody
   public List<DataRefModel> getGrayedOutFields(@RequestBody SelectionTreePaneModel value,
                                                @PathVariable("objectId") String objectId,
                                                @RemainingPath String runtimeId,
                                                Principal principal)
                                                throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         SelectionTreeVSAssembly assembly =
            (SelectionTreeVSAssembly) rvs.getViewsheet().getAssembly(objectId);

         if(assembly == null) {
            return null;
         }

         SelectionTreeVSAssemblyInfo oldAssemblyInfo =
            (SelectionTreeVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());
         SelectionTreeVSAssemblyInfo newAssemblyInfo =
            (SelectionTreeVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());

         SelectionMeasurePaneModel selectionMeasurePaneModel = value.getSelectionMeasurePaneModel();

         setAssemblyInfoTables(newAssemblyInfo, value);
         setAssemblyInfoDataRefs(newAssemblyInfo, value);
         setAssemblyInfoMeasure(newAssemblyInfo, selectionMeasurePaneModel);

         return getGrayedFields(rvs, oldAssemblyInfo, newAssemblyInfo);
      }
      finally {
         box.unlockRead();
      }
   }

   private List<DataRefModel> getGrayedFields(RuntimeViewsheet rvs,
                                              SelectionTreeVSAssemblyInfo oinfo,
                                              SelectionTreeVSAssemblyInfo ninfo)
   {
      VSModelTrapContext context = new VSModelTrapContext(rvs, true);
      context.checkTrap(oinfo, ninfo);
      DataRef[] refs = context.getGrayedFields();;
      List<DataRefModel> fields = new ArrayList<>();

      for(DataRef ref : refs) {
         fields.add(dataRefService.createDataRefModel(ref));
      }

      return fields;
   }

   private ColumnRef createColumnRefFromModel(OutputColumnRefModel outputColumnRefModel) {
      AttributeRef aRef = new AttributeRef(outputColumnRefModel.getEntity(), outputColumnRefModel.getAttribute());
      aRef.setRefType(outputColumnRefModel.getRefType());
      ColumnRef cRef = new ColumnRef(aRef);
      cRef.setDataType(outputColumnRefModel.getDataType() == null ? XSchema.STRING : outputColumnRefModel.getDataType());
      cRef.setAlias(outputColumnRefModel.getAlias());
      return cRef;
   }

   private void setAssemblyInfoTables(SelectionTreeVSAssemblyInfo info,
                                      SelectionTreePaneModel model)
   {
      info.setFirstTableName(model.getSelectedTable());
      info.setAdditionalTableNames(model.getAdditionalTables());
   }

   private void setAssemblyInfoDataRefs(SelectionTreeVSAssemblyInfo info,
                                        SelectionTreePaneModel model) {
      int mode = model.getMode();
      info.setMode(mode);
      List<ColumnRef> columnRefs = new ArrayList<>();

      if(mode == SelectionTreeVSAssemblyInfo.ID) {
         info.setSelectChildren(model.isSelectChildren());
         info.setParentIDValue(model.getParentId());
         info.setIDValue(model.getId());
         info.setLabelValue(model.getLabel());

         OutputColumnRefModel parentIdRef = model.getParentIdRef();
         OutputColumnRefModel idRef = model.getIdRef();
         OutputColumnRefModel labelRef = model.getLabelRef();

         if(parentIdRef != null) {
            columnRefs.add(createColumnRefFromModel(parentIdRef));
         }

         if(idRef != null) {
            columnRefs.add(createColumnRefFromModel(idRef));
         }

         if(labelRef != null) {
            columnRefs.add(createColumnRefFromModel(labelRef));
         }
      }
      else {
         info.setSelectChildren(false);
         info.setParentIDValue(null);
         info.setIDValue(null);
         info.setLabel(null);
         OutputColumnRefModel[] columnRefModels = model.getSelectedColumns();

         if(columnRefModels != null && columnRefModels.length > 0) {
            for(OutputColumnRefModel columnRefModel : columnRefModels) {
               columnRefs.add(createColumnRefFromModel(columnRefModel));
            }
         }
      }

      info.setDataRefs(columnRefs.toArray(new ColumnRef[0]));
   }

   private void setAssemblyInfoMeasure(SelectionTreeVSAssemblyInfo info,
                                       SelectionMeasurePaneModel model)
   {
      String measure = model.getMeasure();
      measure = measure == null || measure.isEmpty() ? null : measure;
      info.setMeasureValue(measure);

      if(measure != null) {
         info.setShowTextValue(model.isShowText());
         info.setShowBarValue(model.isShowBar());

         if(info.getTableNames().size() > 0 &&
            info.getTableNames().stream().noneMatch((t) -> t.contains(Assembly.CUBE_VS)))
         {
            info.setFormulaValue(model.getFormula());
         }
         else {
            info.setFormulaValue("none");
         }
      }
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final VSTrapService trapService;
   private final VSDialogService dialogService;
   private final VSSelectionService vsSelectionService;
   private final SelectionDialogService selectionDialogService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final DataRefModelFactoryService dataRefService;
}
