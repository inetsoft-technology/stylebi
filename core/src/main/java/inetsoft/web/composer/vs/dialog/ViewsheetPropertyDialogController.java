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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.internal.DimensionD;
import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.PaperSize;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

@RestController
public class ViewsheetPropertyDialogController {

   /**
    * Creates a new instance of <tt>ViewsheetPropertyDialogController</tt>.
    *
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public ViewsheetPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                            PlaceholderService placeholderService,
                                            ViewsheetService viewsheetService,
                                            VSLayoutService layoutService,
                                            ViewsheetSettingsService viewsheetSettingsService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.layoutService = layoutService;
      this.viewsheetSettingsService = viewsheetSettingsService;
   }

   /**
    * Gets the top-level descriptor of the viewsheet.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the principal.
    *
    * @return the viewsheet descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/viewsheet-property-dialog-model/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ViewsheetPropertyDialogModel getViewsheetInfo(@RemainingPath String runtimeId,
                                                        Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = this.viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetInfo info = viewsheet.getViewsheetInfo();

      ViewsheetPropertyDialogModel.Builder vsModel = ViewsheetPropertyDialogModel.builder();

      VSOptionsPaneModel vsOptionsPaneModel = new VSOptionsPaneModel();
      vsOptionsPaneModel.setUseMetaData(info.isMetadata());
      vsOptionsPaneModel.setPromptForParams(!info.isDisableParameterSheet());
      vsOptionsPaneModel.setSelectionAssociation(info.isAssociationEnabled());
      vsOptionsPaneModel.setMaxRowsWarning(info.isMaxRowsWarning());
      vsOptionsPaneModel.setCreateMv(info.isMVOnDemand());
      vsOptionsPaneModel.setOnDemandMvEnabled("true".equals(SreeEnv.getProperty("mv.ondemand")));
      vsOptionsPaneModel.setDesc(info.getDescription());
      vsOptionsPaneModel.setServerSideUpdate(info.isUpdateEnabled());
      vsOptionsPaneModel.setTouchInterval(info.getTouchInterval());
      vsOptionsPaneModel.setMaxRows(info.getDesignMaxRows());
      vsOptionsPaneModel.setSnapGrid(info.getSnapGrid());
      vsOptionsPaneModel.setListOnPortalTree(info.isOnReport());
      vsOptionsPaneModel.setAlias(viewsheet.getRuntimeEntry() == null ? null:
                                     viewsheet.getRuntimeEntry().getAlias());
      vsOptionsPaneModel.setAutoRefreshEnabled(
         !"false".equals(SreeEnv.getProperty("vs.auto.refresh.enabled")));

      SelectDataSourceDialogModel newVSDialogModel = new SelectDataSourceDialogModel();
      newVSDialogModel.setDataSource(viewsheet.getBaseEntry());
      vsOptionsPaneModel.setWorksheet(viewsheet.getBaseEntry() != null &&
                                      viewsheet.getBaseEntry().isWorksheet());
      vsOptionsPaneModel.setSelectDataSourceDialogModel(newVSDialogModel);

      vsModel.vsOptionsPane(vsOptionsPaneModel);

      vsOptionsPaneModel.setViewsheetParametersDialogModel(
         viewsheetSettingsService.getViewsheetParameterInfo(rvs));

      // Filter Pane
      FiltersPaneModel filtersPane = new FiltersPaneModel();
      List<FilterModel> filters = new ArrayList<>();

      for(Assembly assembly : viewsheet.getAssemblies()) {
         if(!(assembly instanceof SelectionVSAssembly ||
            assembly instanceof InputVSAssembly)) {
            continue;
         }

         FilterModel model = new FilterModel();
         model.setColumn(assembly.getName());
         model.setFilterId(info.getFilterID(assembly.getName()));
         filters.add(model);
      }

      filters.sort(Comparator.comparing(FilterModel::getColumn));
      filtersPane.setFilters(filters);

      List<FilterModel> filterCols = Arrays.stream(info.getFilterColumns())
         .filter(filterColumn ->
            Arrays.stream(viewsheet.getAssemblies())
               .anyMatch(assembly -> assembly.getName().equals(filterColumn)))
         .map(filter -> {
            FilterModel model = new FilterModel();
            model.setColumn(filter);
            model.setFilterId(info.getFilterID(filter));
            return model;
         })
         .collect(Collectors.toList());

      filtersPane.setSharedFilters(filterCols);

      vsModel.filtersPane(filtersPane);

      ScreensPaneModel screensPane = new ScreensPaneModel();
      screensPane.setTargetScreen(info.isTemplateEnabled());
      screensPane.setScaleToScreen(info.isScaleToScreen());
      screensPane.setFitToWidth(info.isFitToWidth());
      screensPane.setTemplateHeight(info.getTemplateHeight());
      screensPane.setTemplateWidth(info.getTemplateWidth());
      screensPane.setBalancePadding(info.isBalancePadding());

      AssetRepository assetRepository = viewsheetService.getAssetRepository();
      boolean enterprise = LicenseManager.getInstance().isEnterprise();
      screensPane.setEditDevicesAllowed((!enterprise || OrganizationManager.getInstance().isSiteAdmin(principal))
         && assetRepository.checkPermission(
         principal, ResourceType.DEVICE, "*", EnumSet.of(ResourceAction.ACCESS)));

      DeviceRegistry registry = DeviceRegistry.getRegistry();
      List<DeviceInfo> devices = Arrays.asList(registry.getDevices());

      for(DeviceInfo device : devices) {
         ScreenSizeDialogModel screenSizeDialog = new ScreenSizeDialogModel();
         screenSizeDialog.setLabel(device.getName());
         screenSizeDialog.setDescription(device.getDescription());
         screenSizeDialog.setMinWidth(device.getMinWidth());
         screenSizeDialog.setMaxWidth(device.getMaxWidth());
         screenSizeDialog.setId(device.getId());
         screensPane.getDevices().add(screenSizeDialog);
      }

      LayoutInfo layoutInfo = viewsheet.getLayoutInfo();
      List<ViewsheetLayout> layouts = layoutInfo.getViewsheetLayouts();

      for(ViewsheetLayout layout: layouts) {
         VSDeviceLayoutDialogModel vsLayoutDialog = new VSDeviceLayoutDialogModel();
         vsLayoutDialog.setMobileOnly(layout.isMobileOnly());
         vsLayoutDialog.setName(layout.getName());
//         vsLayoutDialog.setScaleFont(layout.getScaleFont());
         vsLayoutDialog.setSelectedDevices(Arrays.asList(layout.getDeviceIds()));
         vsLayoutDialog.setId(layout.getID());

         screensPane.getDeviceLayouts().add(vsLayoutDialog);
      }

      PrintLayout printLayout = layoutInfo.getPrintLayout();

      if(printLayout != null) {
         VSPrintLayoutDialogModel vsLayoutDialog = new VSPrintLayoutDialogModel();
         vsLayoutDialog.setPaperSize(printLayout.getPrintInfo().getPaperType());
         vsLayoutDialog.setLandscape(printLayout.isHorizontalScreen());
         vsLayoutDialog.setScaleFont(printLayout.getScaleFont());
         vsLayoutDialog.setNumberingStart(printLayout.getPrintInfo().getPageNumberingStart());
         vsLayoutDialog.setFooterFromEdge(printLayout.getPrintInfo().getFooterFromEdge());
         vsLayoutDialog.setHeaderFromEdge(printLayout.getPrintInfo().getHeaderFromEdge());
         PrintInfo pinfo = printLayout.getPrintInfo();
         String unit = pinfo.getUnit();
         double ratio = 1 / PrintInfo.getUnitRatio(unit); // convert inches to current unit
         Margin margin = pinfo.getMargin();
         vsLayoutDialog.setMarginTop(getDisplayPageSize(margin.top, ratio));
         vsLayoutDialog.setMarginBottom(getDisplayPageSize(margin.bottom, ratio));
         vsLayoutDialog.setMarginLeft(getDisplayPageSize(margin.left, ratio));
         vsLayoutDialog.setMarginRight(getDisplayPageSize(margin.right, ratio));

         DimensionD size = printLayout.getPrintInfo().getSize();
         vsLayoutDialog.setCustomHeight(getDisplayPageSize(size.getHeight(), 1));
         vsLayoutDialog.setCustomWidth(getDisplayPageSize(size.getWidth(), 1));
         vsLayoutDialog.setUnits(printLayout.getPrintInfo().getUnit());
         screensPane.setPrintLayout(vsLayoutDialog);
      }

      vsModel.screensPane(screensPane);

      if(OrganizationManager.getInstance().isSiteAdmin(principal) ||
         !SecurityEngine.getSecurity().isSecurityEnabled() || !SUtil.isMultiTenant())
      {
         LocalizationPaneModel localizationPane = new LocalizationPaneModel();

         localizationPane.setComponents(getTree(viewsheet));

         List<LocalizationComponent> localized = Arrays.stream(info.getLocalComponents())
            .map(component -> {
               LocalizationComponent locale = new LocalizationComponent();
               locale.setName(component);
               locale.setTextId(info.getLocalID(component) == null ?
                  component : info.getLocalID(component));
               return locale;
            })
            .collect(Collectors.toList());

         localizationPane.setLocalized(localized);
         vsModel.localizationPane(localizationPane);
      }

      VSScriptPaneModel.Builder vsScriptPane = VSScriptPaneModel.builder();
      vsScriptPane.onInit(info.getOnInit());
      vsScriptPane.onLoad(info.getOnLoad());
      vsScriptPane.enableAutocomplete(true);
      vsScriptPane.enableScript(info.isScriptEnabled());
      vsModel.vsScriptPane(vsScriptPane.build());

      return vsModel.build();
   }

   // show two decimal places at most.
   private double getDisplayPageSize(double num, double ratio) {
      return (double) Math.round(num * ratio * 100) / 100;
   }

   /**
    * Sets the top-level descriptor of the specified viewsheet.
    *
    * @param value the viewsheet descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/viewsheet-property-dialog-model")
   public void setViewsheetInfo(@Payload ViewsheetPropertyDialogModel value,
                                Principal principal, CommandDispatcher commandDispatcher,
                                @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = this.viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetInfo info = viewsheet.getViewsheetInfo();
      VSOptionsPaneModel vsOptionsPaneModel = value.vsOptionsPane();

      boolean reset = info.isMetadata() != vsOptionsPaneModel.isUseMetaData() ||
         info.getDesignMaxRows() != vsOptionsPaneModel.getMaxRows();

      info.setMetadata(vsOptionsPaneModel.isUseMetaData());
      info.setDisableParameterSheet(!vsOptionsPaneModel.isPromptForParams());
      info.setAssociationEnabled(vsOptionsPaneModel.isSelectionAssociation());
      info.setMaxRowsWarning(vsOptionsPaneModel.isMaxRowsWarning());

      if(info.isMVOnDemand() != vsOptionsPaneModel.isCreateMv()) {
         info.setMVOnDemand(vsOptionsPaneModel.isCreateMv());
         reset = true;
      }

      info.setDescription(vsOptionsPaneModel.getDesc());
      info.setUpdateEnabled(vsOptionsPaneModel.isServerSideUpdate());
      info.setTouchInterval(vsOptionsPaneModel.getTouchInterval());
      info.setDesignMaxRows(vsOptionsPaneModel.getMaxRows());
      info.setSnapGrid(vsOptionsPaneModel.getSnapGrid());
      info.setOnReport(vsOptionsPaneModel.isListOnPortalTree());

      if(viewsheet.getRuntimeEntry() != null) {
         AssetEntry runtimeEntry = viewsheet.getRuntimeEntry();
         runtimeEntry.setAlias(vsOptionsPaneModel.getAlias());
         String desc = runtimeEntry.getDescription();
         desc = desc.substring(0, desc.indexOf("/") + 1);
         desc += SUtil.localizeAssetEntry(runtimeEntry.getPath(), principal, true,
                                          runtimeEntry, runtimeEntry.getScope() == AssetRepository.USER_SCOPE);
         runtimeEntry.setProperty("_description_", desc);
      }

      SelectDataSourceDialogModel newVSDialogModel =
         vsOptionsPaneModel.getSelectDataSourceDialogModel();

      if(newVSDialogModel.getDataSource() != null) {
         AssetEntry datasource = newVSDialogModel.getDataSource();

         if(!datasource.equals(viewsheet.getBaseEntry())) {
            AssetEntry oldDatasource = viewsheet.getBaseEntry();
            Worksheet ows = viewsheet.getBaseWorksheet();
            viewsheet.setBaseEntry(datasource);
            viewsheet.update(viewsheetService.getAssetRepository(), datasource, principal);
            updateBoundAssemblies(oldDatasource, ows, viewsheet);
            rvs.getViewsheetSandbox().resetRuntime();
            commandDispatcher.sendCommand(new VSDependencyChangedCommand(false));
         }
      }
      else {
         if(viewsheet.getBaseEntry() != null) {
            clearOldBinding(viewsheet);
         }

         viewsheet.setBaseEntry(null);
         rvs.getViewsheetSandbox().resetRuntime();
      }

      ViewsheetParametersDialogModel vsParametersDialogModel =
         vsOptionsPaneModel.getViewsheetParametersDialogModel();
      viewsheetSettingsService.setViewsheetParameterInfo(info, vsParametersDialogModel);

      FiltersPaneModel filtersPane = value.filtersPane();

      for(FilterModel filter: filtersPane.getFilters()) {
         info.setFilterID(filter.getColumn(), null);
      }

      for(FilterModel filter: filtersPane.getSharedFilters()) {
         info.setFilterID(filter.getColumn(), filter.getFilterId());
      }

      boolean oScaleToScreen = info.isScaleToScreen();
      ScreensPaneModel screensPane = value.screensPane();
      info.setTemplateEnabled(screensPane.isTargetScreen());
      info.setScaleToScreen(screensPane.isScaleToScreen());
      info.setFitToWidth(screensPane.isFitToWidth());
      info.setTemplateWidth(screensPane.getTemplateWidth());
      info.setTemplateHeight(screensPane.getTemplateHeight());
      info.setBalancePadding(screensPane.isBalancePadding());

      DeviceRegistry registry = DeviceRegistry.getRegistry();
      List<DeviceInfo> devices = new ArrayList<>();
      Map<String, String> tempToId = new HashMap<>();

      //The Dependenc of Deivce is the ry stored according to id
      if(registry.getDevices().length > screensPane.getDevices().size()) {
         List<DeviceInfo> oDevices = Arrays.stream((registry.getDevices())).toList();
         List<String> deleteId = getDeleteDevices(oDevices, screensPane.getDevices());

         for(String id : deleteId) {
            AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
               AssetEntry.Type.DEVICE, id, null);
            DependencyHandler.getInstance().deleteDependenciesKey(entry);
         }
      }

      for(ScreenSizeDialogModel device: screensPane.getDevices()) {
         DeviceInfo deviceInfo = new DeviceInfo();
         deviceInfo.setName(device.getLabel());
         deviceInfo.setDescription(device.getDescription());
         deviceInfo.setMinWidth(device.getMinWidth());
         deviceInfo.setMaxWidth(device.getMaxWidth());
         deviceInfo.setLastModified(System.currentTimeMillis());
         deviceInfo.setLastModifiedBy(principal.getName());

         if(device.getId() == null || registry.getDevice(device.getId()) == null) {
            String uuid = UUID.randomUUID().toString();
            deviceInfo.setId(uuid);
            deviceInfo.setCreated(System.currentTimeMillis());
            deviceInfo.setCreatedBy(principal.getName());
            tempToId.put(device.getTempId(), uuid);
         }
         else {
            deviceInfo.setId(device.getId());
         }

         devices.add(deviceInfo);
      }

      registry.setDevices(devices);

      VSPrintLayoutDialogModel vsPrintLayoutDialog = screensPane.getPrintLayout();

      LayoutInfo layoutInfo = viewsheet.getLayoutInfo();
      Dimension oldPageSize = null;

      if(vsPrintLayoutDialog != null) {
         PrintLayout printLayout;
         PrintInfo printInfo;

         if(layoutInfo.getPrintLayout() != null) {
            printLayout = layoutInfo.getPrintLayout();
            printInfo = printLayout.getPrintInfo();
            oldPageSize = VSLayoutService.getPrintPageSize(printLayout);
         }
         else {
            printLayout = new PrintLayout();
            printLayout.setHeaderLayouts(new ArrayList<>());
            printLayout.setFooterLayouts(new ArrayList<>());
            printLayout.setVSAssemblyLayouts(new ArrayList<>());
            printInfo = new PrintInfo();
         }

         printInfo.setPaperType(vsPrintLayoutDialog.getPaperSize());
         printLayout.setScaleFont(vsPrintLayoutDialog.getScaleFont());
         printInfo.setPageNumberingStart(vsPrintLayoutDialog.getNumberingStart());
         printInfo.setFooterFromEdge(vsPrintLayoutDialog.getFooterFromEdge());
         printInfo.setHeaderFromEdge(vsPrintLayoutDialog.getHeaderFromEdge());

         double radio = PrintInfo.getUnitRatio(vsPrintLayoutDialog.getUnits()); // convert to inch
         Size size = PaperSize.getSize(vsPrintLayoutDialog.getPaperSize());
         DimensionD dimensions;

         if(size == null) {
            dimensions = new DimensionD(vsPrintLayoutDialog.getCustomWidth() * radio,
                                        vsPrintLayoutDialog.getCustomHeight() * radio);
         }
         else {
            dimensions = new DimensionD(size.width, size.height);
         }

         printInfo.setSize(dimensions);
         printInfo.setUnit(vsPrintLayoutDialog.getUnits());
         Margin margin = new Margin(vsPrintLayoutDialog.getMarginTop() * radio,
            vsPrintLayoutDialog.getMarginLeft() * radio,
            vsPrintLayoutDialog.getMarginBottom() * radio,
            vsPrintLayoutDialog.getMarginRight() * radio) ;
         printInfo.setMargin(margin);

         printLayout.setPrintInfo(printInfo);
         printLayout.setHorizontalScreen(vsPrintLayoutDialog.isLandscape());
         layoutInfo.setPrintLayout(printLayout);
         Dimension newPageSize = VSLayoutService.getPrintPageSize(printLayout);

         if(oldPageSize != null && oldPageSize.height != newPageSize.height) {
            layoutService.refrestPrintLayoutObjs(viewsheet, printLayout);
         }
      }
      else {
         layoutInfo.setPrintLayout(null);
      }

      List<String> selectedDevices;
      List<ViewsheetLayout> oldLayouts = layoutInfo.getViewsheetLayouts();
      List<ViewsheetLayout> newLayouts = new ArrayList<>();

      for(VSDeviceLayoutDialogModel layout : screensPane.getDeviceLayouts()) {
         ViewsheetLayout vsLayout = oldLayouts.stream()
            .filter(l -> l.getID().equals(layout.getId()))
            .findFirst()
            .orElse(null);
         String oldName = vsLayout == null ? null : vsLayout.getName();

         if(vsLayout == null) {
            vsLayout = new ViewsheetLayout();
            vsLayout.setID(layout.getId());
            vsLayout.setVSAssemblyLayouts(new ArrayList<>());
         }

         vsLayout.setName(layout.getName());
//         vsLayout.setScaleFont(layout.getScaleFont());
         vsLayout.setMobileOnly(layout.isMobileOnly());

         selectedDevices = layout.getSelectedDevices()
            .stream()
            .map(deviceId -> {
               if(tempToId.get(deviceId) != null) {
                  return tempToId.get(deviceId);
               }

               return deviceId;
            })
            .collect(Collectors.toList());
         vsLayout.setDeviceIds(selectedDevices.toArray(new String[0]));
         newLayouts.add(vsLayout);

         //If layout pane is focused, update the layout pane based on new layout configuration.
         if(oldName != null && oldName.equals(runtimeViewsheetRef.getFocusedLayoutName())) {
            UpdateLayoutCommand updateLayoutCommand = UpdateLayoutCommand.builder()
               .layoutName(vsLayout.getName())
               .build();
            runtimeViewsheetRef.setFocusedLayoutName(vsLayout.getName());
            commandDispatcher.sendCommand(updateLayoutCommand);
         }
      }

      layoutInfo.setViewsheetLayouts(newLayouts);

      LocalizationPaneModel localizationPane = value.localizationPane();

      for(String component: info.getLocalComponents()) {
         info.removeLocalID(component, true);
      }

      if(localizationPane != null) {
         for(LocalizationComponent component: localizationPane.getLocalized()) {
            info.setLocalID(component.getName(), component.getTextId());
         }
      }

      VSScriptPaneModel vsScriptPane = value.vsScriptPane();
      info.setOnInit(vsScriptPane.onInit());
      info.setOnLoad(vsScriptPane.onLoad());
      info.setScriptEnabled(vsScriptPane.enableScript());

      if(reset) {
         rvs.resetRuntime();
      }

      try {
         ChangedAssemblyList clist =
            this.placeholderService.createList(false, commandDispatcher, rvs, linkUri);

         int width = value.preview() && info.isScaleToScreen() ? value.width() : 0;
         int height = value.preview() && info.isScaleToScreen() ? value.height() : 0;

         if(value.preview()) {
            VSEventUtil.clearScale(rvs.getViewsheet());
         }

         if(!oScaleToScreen && screensPane.isScaleToScreen()) {
            commandDispatcher.sendCommand(new ClearScrollCommand());
         }

         this.placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, width, height,
            false, null, commandDispatcher, false, true, true, clist);
      }
      // TODO the confirm exception stuff should be replaced b/c it depends on the old event API
      catch(ConfirmException e) {
         if(!this.placeholderService.waitForMV(e, rvs, commandDispatcher)) {
            throw e;
         }
      }
   }

   @PostMapping(
      value = "/api/composer/vs/viewsheet-property-dialog-model/test-script")
   public StringWrapper testVsScript(@RequestParam("runtimeId") String runtimeId,
                                     @RequestBody ViewsheetPropertyDialogModel model,
                                     Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = this.viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSScriptPaneModel scriptPane = model.vsScriptPane();
      StringWrapper result = new StringWrapper();
      Catalog catalog = Catalog.getCatalog();
      String scriptable = null;

      try {
         box.getScope().execute(scriptPane.onInit(), scriptable);
      }
      catch(Exception ex) {
         result.setBody(
            catalog.getString("viewer.viewsheet.onInitScriptFailed", ex.getMessage()));
         return result;
      }

      try {
         box.getScope().execute(scriptPane.onLoad(), scriptable);
      }
      catch(Exception ex) {
         result.setBody(
            catalog.getString("viewer.viewsheet.onLoadScriptFailed", ex.getMessage()));
         return result;
      }

      return null;
   }

   @GetMapping("/api/composer/vs/viewsheet-property-dialog-model/convert-to-worksheet/**")
   @ResponseBody
   public ConvertToWorksheetResponseModel convertLogicModelToWorksheet(@RemainingPath String runtimeId,
                                                                       Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || vs.getBaseEntry() == null || !vs.getBaseEntry().isLogicModel()) {
         throw new Exception("Invalid data source type. Data source needs to be a logic model");
      }

      AssetEntry vsEntry = vs.getRuntimeEntry();

      if(vsEntry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         throw new MessageException(Catalog.getCatalog().getString("common.viewsheet.saveViewsheetDependence"));
      }

      Worksheet ws = getLogicModelWorksheet(assetRepository, vs, principal);
      String wsPath = vsEntry.getName() + " Worksheet";
      AssetEntry entry = new AssetEntry(vsEntry.getScope(), AssetEntry.Type.WORKSHEET,
                                        wsPath, vsEntry.getUser());

      // make sure the name is unique
      for(int n = 2; SUtil.isDuplicatedEntry(assetRepository, entry); n++) {
         entry = new AssetEntry(entry.getScope(), entry.getType(), wsPath + " " + n,
                                entry.getUser());
      }

      // save the worksheet
      viewsheetService.setWorksheet(ws, entry, principal, true, true);
      final MVManager manager = MVManager.getManager();
      final MVDef[] mvs = manager.list(false, def -> rvs.getEntry().toIdentifier().equals(def.getVsId()));
      SelectDataSourceDialogModel model = new SelectDataSourceDialogModel();
      model.setDataSource(entry);
      return new ConvertToWorksheetResponseModel(model, mvs.length > 0);
   }

   private List<String> getDeleteDevices(List<DeviceInfo> oDevices, List<ScreenSizeDialogModel> nDevices) {
      List<String> deleteList = new ArrayList<>();

      Set<String> newIdSet = nDevices.stream()
         .map(ScreenSizeDialogModel::getId)
         .collect(Collectors.toSet());

      for(DeviceInfo deviceInfo : oDevices) {
         if(!newIdSet.contains(deviceInfo.getId())) {
            deleteList.add(deviceInfo.getId());
         }
      }

      return deleteList;
   }

   private void updateBoundAssemblies(AssetEntry oldDatasource, Worksheet ows, Viewsheet viewsheet)
   {
      AssetEntry datasource = viewsheet.getBaseEntry();

      // if old source is null, there is no need to update data, for data will not binding for null.
      if(oldDatasource == null) {
         return;
      }

      // when change source to null, clear old binding to avoid popup errors.
      if(datasource == null) {
         clearOldBinding(viewsheet);
         return;
      }

      // If name and type do not change, do nothing.
      if(oldDatasource.getType() == datasource.getType() &&
         oldDatasource.getName() == datasource.getName())
      {
         return;
      }

      // query-query  ws-ws  query-ws  ws-query
      // ws-ws lm-lm lm-ws ws-lm
      // these 8 types will have same column after change, so to update binding columns, other
      // types should only clear bindings.
      if(supportUpdateOldBinding(oldDatasource.getType(), datasource.getType())) {
         updateOldBinding(viewsheet, ows, oldDatasource, datasource);
      }
      else {
         clearOldBinding(viewsheet);
      }

      updateAggregateInfo(viewsheet);
   }

   private boolean supportUpdateOldBinding(AssetEntry.Type otype, AssetEntry.Type ntype) {
      return (otype == AssetEntry.Type.QUERY || otype == AssetEntry.Type.WORKSHEET) &&
         (ntype == AssetEntry.Type.QUERY || ntype == AssetEntry.Type.WORKSHEET) ||
         (otype == AssetEntry.Type.LOGIC_MODEL || otype == AssetEntry.Type.WORKSHEET) &&
         (ntype == AssetEntry.Type.WORKSHEET || ntype == AssetEntry.Type.LOGIC_MODEL);
   }

   // After update binding info, update aggregate according new source table.
   private void updateAggregateInfo(Viewsheet vs) {
      Assembly[] arr = vs.getAssemblies();
      Worksheet ws = vs.getBaseWorksheet();

      if(arr != null) {
         for(Assembly assembly : arr) {
            if(assembly instanceof DataVSAssembly) {
               SourceInfo sourceInfo = ((DataVSAssembly) assembly).getSourceInfo();

               if(sourceInfo == null) {
                  continue;
               }

               String tableName = sourceInfo.getSource();
               AggregateInfo ainfo = new AggregateInfo();

               if(ws.getAssembly(tableName) == null) {
                  continue;
               }

               TableAssembly tbl = (TableAssembly) ws.getAssembly(tableName);

               if(tbl != null) {
                  VSEventUtil.createAggregateInfo(tbl, ainfo, null, vs, true);
               }

               if(assembly instanceof CrosstabVSAssembly) {
                  CrosstabVSAssembly cross = (CrosstabVSAssembly) assembly;
                  AggregateInfo oinfo = cross.getVSCrosstabInfo().getAggregateInfo();
                  cross.getVSCrosstabInfo().setAggregateInfo(updateAggregateRefs(oinfo, ainfo));
               }
               else if(assembly instanceof ChartVSAssembly) {
                  ChartVSAssembly chart = (ChartVSAssembly) assembly;
                  AggregateInfo oinfo = chart.getVSChartInfo().getAggregateInfo();
                  chart.getVSChartInfo().setAggregateInfo(updateAggregateRefs(oinfo, ainfo));
               }
               else if(assembly instanceof CalcTableVSAssembly) {
                  CalcTableVSAssembly calc = (CalcTableVSAssembly) assembly;
                  CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) calc.getVSAssemblyInfo();
                  AggregateInfo oinfo = info.getAggregateInfo();
                  info.setAggregateInfo(updateAggregateRefs(oinfo, ainfo));
               }
            }
         }
      }
   }

   // clear binding will clear column not existed, update aggregate info should change right column
   // type(dimension or measure), then the old binding can work.
   // Update old aggregate info to new info, if column existed, keep it, if not existed, clear it.
   private AggregateInfo updateAggregateRefs(AggregateInfo oinfo, AggregateInfo ninfo) {
      if(oinfo == null) {
         return ninfo;
      }

      for(int i = 0; i < oinfo.getGroupCount(); i++) {
         GroupRef groupRef = oinfo.getGroup(i);
         String gname = groupRef.getName();

         // group in new aggregate info, do nothing.
         if(ninfo.getGroup(gname) != null) {
            continue;
         }

         AggregateRef agg = getAggregateRef(ninfo, gname);

         // if group in aggregate, move it to group, delete agg, add group.
         if(agg != null) {
            ninfo.removeAggregate(agg);
            ninfo.addGroup(groupRef);
         }
      }

      for(int i = 0; i < oinfo.getAggregateCount(); i++) {
         AggregateRef aggRef = oinfo.getAggregate(i);
         String aname = aggRef.getName();

         // if aggregate in aggregate, do thing.
         if(getAggregateRef(ninfo, aname) != null) {
            continue;
         }

         GroupRef groupRef = ninfo.getGroup(aname);

         // agg in new group info, move to aggregate.
         if(groupRef != null) {
            ninfo.removeGroup(groupRef);
            ninfo.addAggregate(aggRef);
         }
      }

      return ninfo;
   }

   private AggregateRef getAggregateRef(AggregateInfo ainfo, String gname) {
      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         if(Tool.equals(gname, ainfo.getAggregate(i).getName())) {
            return ainfo.getAggregate(i);
         }
      }

      return null;
   }

   private void clearOldBinding(Viewsheet vs) {
      Assembly[] arr = vs.getAssemblies();

      if(arr != null) {
         for(Assembly assembly : arr) {
            if(assembly instanceof DataVSAssembly) {
               SourceInfo oldSourceInfo = ((DataVSAssembly) assembly).getSourceInfo();

               if(oldSourceInfo == null || oldSourceInfo.getType() == XSourceInfo.CUBE) {
                  continue;
               }

               ((DataVSAssembly) assembly).setSourceInfo(null);

               // for calc table, remove its layout's cell bindings.
               if(assembly instanceof CalcTableVSAssembly) {
                  CalcTableVSAssembly calc = (CalcTableVSAssembly) assembly;
                  removeCalcBinding(calc);
               }
               else if(assembly.getInfo() instanceof DataVSAssemblyInfo) {
                  ((DataVSAssemblyInfo) assembly.getInfo()).clearBinding();
               }
            }
            else if(assembly instanceof OutputVSAssembly &&
               shouldChangeSource(((OutputVSAssembly) assembly).getTableName()))
            {
               ((OutputVSAssembly) assembly).setTableName(null);
            }
            else if(assembly instanceof SelectionVSAssembly &&
               shouldChangeSource(((SelectionVSAssembly) assembly).getTableName()))
            {
               ((SelectionVSAssembly) assembly).setTableName(null);
            }
         }
      }
   }

   private void removeCalcBinding(CalcTableVSAssembly calc) {
      TableLayout tlayout = calc.getTableLayout();
      BaseLayout.Region[] regions = tlayout.getRegions();

      for(int i = 0; i < regions.length; i++) {
         BaseLayout.Region region = regions[i];

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               CellBinding cellBinding = region.getCellBinding(r, c);

               if(cellBinding != null && (cellBinding.getType() == TableCellBinding.BIND_COLUMN ||
                  cellBinding.getType() == TableCellBinding.BIND_FORMULA))
               {
                  region.setCellBinding(r, c, new TableCellBinding());
               }
            }
         }
      }
   }

   // 1. if change to query, change all assemblies to new query source.
   // 2. if change query to ws, check current source is existed in base ws, if existed, change
   // column selections. If do not existed, clear binding.ws only have one table, change all
   // assemblies to new table
   // 3. if change to ws and ws, only change the same table to update binding, other clear binding.
   private void updateOldBinding(Viewsheet vs, Worksheet ows, AssetEntry odatasource,
                                 AssetEntry datasource)
   {
      updateToNewTable(vs, ows, odatasource, datasource);
   }

   // udpate assembly to new ws datasource
   // if assembly's table is existed in new ws, update columns.
   // if assembly's table is not existed in new ws, clear bindings.
   private void updateToNewTable(Viewsheet vs, Worksheet ows, AssetEntry odatasource,
                                 AssetEntry datasource)
   {
      Worksheet ws = vs.getBaseWorksheet();

      // if ws do not have table, clear all bindings.
      if(ws == null || ws.getAssemblies().length == 0) {
         clearOldBinding(vs);

         return;
      }

      // 1. query to query. change to new table.
      if(odatasource.getType() == AssetEntry.Type.QUERY &&
         datasource.getType() == AssetEntry.Type.QUERY)
      {
         if(ws.getAssembly(datasource.getName()) instanceof TableAssembly) {
            changeSourceTable(vs, ws, datasource.getName());
         }

         return;
      }

      changeTableColumns(vs, ws);
   }

   private void changeTableColumns(Viewsheet vs, Worksheet ws) {
      Assembly[] arr = vs.getAssemblies();
      ArrayList<String> tableNames = new ArrayList<String>();
      Assembly[] assemblies = ws.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         tableNames.add(assemblies[i].getName());
      }

      if(arr == null) {
         return;
      }

      int type = SourceInfo.ASSET;

      for(Assembly assembly : arr) {
         String otable = null;

         if(assembly instanceof DataVSAssembly) {
            SourceInfo oldSourceInfo = ((DataVSAssembly) assembly).getSourceInfo();

            if(oldSourceInfo == null || VSUtil.isCubeSource(oldSourceInfo.getSource())) {
               continue;
            }

            if(VSUtil.isVSAssemblyBinding((DataVSAssembly) assembly)) {
               String source = VSUtil.getVSAssemblyBinding(oldSourceInfo.getSource());

               if(vs.getAssembly(source) == null) {
                  clearDataAssemblyBindings((DataVSAssembly) assembly);
               }

               continue;
            }

            otable = oldSourceInfo.getSource();

            if(tableNames.contains(otable)) {
               ((DataVSAssembly) assembly).setSourceInfo(new SourceInfo(type, null, otable));
            }
            else {
               clearDataAssemblyBindings((DataVSAssembly) assembly);
               continue;
            }
         }
         else if(assembly instanceof OutputVSAssembly &&
                 ((OutputVSAssembly) assembly).getTableName() != null)
         {
            otable = ((OutputVSAssembly) assembly).getTableName();

            if(!tableNames.contains(otable)) {
               ((OutputVSAssembly) assembly).setTableName(null);
               continue;
            }
         }
         else if(assembly instanceof SelectionVSAssembly &&
                 ((SelectionVSAssembly) assembly).getTableName() != null)
         {
            otable = ((SelectionVSAssembly) assembly).getTableName();

            // time slider, if assembly binding and assembly source not exist, clear it, if assembly
            // binding and assembly exist, keep it. if not assembly binding, using the same logic
            // as others.
            if(assembly instanceof TimeSliderVSAssembly) {
               int stype = ((TimeSliderVSAssembly) assembly).getSourceType();

               if(stype == XSourceInfo.VS_ASSEMBLY) {
                  if(vs.getAssembly(otable) == null) {
                     ((TimeSliderVSAssembly) assembly).setTableName(null);
                  }

                  continue;
               }
            }

            if(!tableNames.contains(otable)) {
               ((SelectionVSAssembly) assembly).setTableName(null);
               continue;
            }
         }

         if(ws.getAssembly(otable) instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) ws.getAssembly(
               vs.getBaseEntry().getType() == AssetEntry.Type.QUERY ? otable + "_O" : otable);
            clearInvalidBindings(assembly, table, vs, otable);
         }
      }
   }

   private void changeSourceTable(Viewsheet vs, Worksheet ws, String tableName) {
      Assembly[] assemblies = vs.getAssemblies();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table == null) {
         return;
      }

      for(Assembly assembly : assemblies) {
         if(assembly instanceof DataVSAssembly) {
            SourceInfo oldSourceInfo = ((DataVSAssembly) assembly).getSourceInfo();

            if(oldSourceInfo == null || oldSourceInfo.getType() == XSourceInfo.CUBE) {
               continue;
            }

            ((DataVSAssembly) assembly).setSourceInfo(
               new SourceInfo(XSourceInfo.ASSET, null, tableName));
         }
         else if(assembly instanceof OutputVSAssembly) {
            if(!shouldChangeSource(((OutputVSAssembly) assembly).getTableName())) {
               continue;
            }

            ((OutputVSAssembly) assembly).setTableName(tableName);
         }
         else if(assembly instanceof SelectionVSAssembly) {
            if(!shouldChangeSource(((SelectionVSAssembly) assembly).getTableName())) {
               continue;
            }

            ((SelectionVSAssembly) assembly).setTableName(tableName);
         }

         clearInvalidBindings(assembly, table, vs, tableName);
      }
   }

   private boolean shouldChangeSource(String tableName) {
      return tableName != null && !tableName.startsWith(Assembly.CUBE_VS);
   }

   // clear no existed columns in binding if source is changed to avoid data issue.
   private void clearInvalidBindings(Assembly assembly, TableAssembly table, Viewsheet vs,
                                     String tname)
   {
      ColumnSelection cols = table.getColumnSelection();

      if(assembly instanceof TableVSAssembly) {
         ((TableVSAssembly) assembly).setColumnSelection(
            getExistedColumnSelection(((TableVSAssembly) assembly).getColumnSelection(),
            cols, vs, tname));

         return;
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         CrosstabVSAssembly cross = (CrosstabVSAssembly) assembly;
         VSCrosstabInfo vinfo = cross.getVSCrosstabInfo();
         vinfo.setDesignRowHeaders(getExistedColumn(vinfo.getDesignRowHeaders(), cols, vs, tname));
         vinfo.setDesignColHeaders(getExistedColumn(vinfo.getDesignColHeaders(), cols, vs, tname));
         vinfo.setDesignAggregates(getExistedColumn(vinfo.getDesignAggregates(), cols, vs, tname));

         return;
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         clearInvalidCellBindings((CalcTableVSAssembly) assembly, cols, vs, tname);

         return;
      }
      else if(assembly instanceof ChartVSAssembly) {
         fixChartInfo((ChartVSAssembly) assembly, cols, vs, tname);
      }
      else if(assembly instanceof OutputVSAssembly) {
         OutputVSAssembly output = (OutputVSAssembly) assembly;
         BindingInfo binding = output.getBindingInfo();

         if(!(binding instanceof ScalarBindingInfo)) {
            return;
         }

         ScalarBindingInfo scalar = (ScalarBindingInfo) binding;
         String ref = scalar.getColumnValue();

         if(cols.getAttribute(ref) == null &&
            vs.getCalcField(scalar.getTableName(), ref) == null)
         {
             scalar.setColumnValue(null);
             scalar.setColumn2Value(null);

             return;
         }

         String ref2 = scalar.getColumn2Value();

         // If have second columns ref and change source no this ref, clear the formula.
         if(ref2 != null && cols.getAttribute(ref2) == null &&
            vs.getCalcField(scalar.getTableName(), ref) == null)
         {
            scalar.setColumn2Value(null);

            return;
         }
      }
      else if(assembly instanceof AbstractSelectionVSAssembly) {
         VSAssemblyInfo vsAssemblyInfo =
            ((AbstractSelectionVSAssembly) assembly).getVSAssemblyInfo();

         if(vsAssemblyInfo instanceof SelectionTreeVSAssemblyInfo) {
            SelectionTreeVSAssemblyInfo treeInfo = (SelectionTreeVSAssemblyInfo) vsAssemblyInfo;
            DataRef[] oldDataRefs = treeInfo.getDataRefs();

            if(oldDataRefs == null || oldDataRefs.length == 0) {
               return;
            }

            List<DataRef> newDataRefs = new ArrayList<>();

            for(DataRef ref : oldDataRefs) {
               if(ref == null) {
                  continue;
               }

               DataRef attribute = cols.getAttribute(ref.getAttribute());

               if(attribute instanceof ColumnRef) {
                  AttributeRef aRef = new AttributeRef(null, attribute.getAttribute());
                  aRef.setRefType(attribute.getRefType());
                  ColumnRef cRef = new ColumnRef(aRef);
                  cRef.setDataType(attribute.getDataType() == null ?
                     XSchema.STRING : attribute.getDataType());
                  newDataRefs.add(cRef);
                  treeInfo.setTableName(table.getName());
               }
            }

            if(cols.getAttribute(treeInfo.getMeasure()) == null) {
               treeInfo.setMeasureValue(null);
            }

            treeInfo.setDataRefs(newDataRefs.toArray(new DataRef[0]));
            treeInfo.setCompositeSelectionValue(new CompositeSelectionValue());
         }
      }
   }

   private void clearInvalidCellBindings(CalcTableVSAssembly calc, ColumnSelection cols,
                                         Viewsheet vs, String tname)
   {
      TableLayout tlayout = calc.getTableLayout();
      BaseLayout.Region[] regions = tlayout.getRegions();

      for(int i = 0; i < regions.length; i++) {
         BaseLayout.Region region = regions[i];

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               CellBinding cellBinding = region.getCellBinding(r, c);

               if(cellBinding == null) {
                  continue;
               }

               if(cellBinding.getType() == TableCellBinding.BIND_COLUMN &&
                  cols.getAttribute(cellBinding.getValue()) == null &&
                  vs.getCalcField(tname, cellBinding.getValue()) == null)
               {
                  region.setCellBinding(r, c, new TableCellBinding());
               }
            }
         }
      }
   }

   private ColumnSelection getExistedColumnSelection(ColumnSelection ocols, ColumnSelection ncols,
                                                     Viewsheet vs, String tname)
   {
      ColumnSelection cols = new ColumnSelection();

      for(int i = 0; i < ocols.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) ocols.getAttribute(i);

         for(int j = 0; j < ncols.getAttributeCount(); j++) {
            ColumnRef nref = (ColumnRef) ncols.getAttribute(j);

            if(Tool.equals(ref.getAttribute(), nref.getAttribute())) {
               cols.addAttribute(ref);
               break;
            }
         }

         if(vs.getCalcField(tname, ref.getName()) != null)
         {
            cols.addAttribute(ref);
         }
      }

      return cols;
   }

   private DataRef[] getExistedColumn(DataRef[] orefs, ColumnSelection cols, Viewsheet vs,
                                      String tname)
   {
      List<DataRef> list = new ArrayList<DataRef>();

      for(int i = 0; i < orefs.length; i++) {
         if(isRefExisted(orefs[i], cols, vs, tname)) {
            list.add(orefs[i]);
         }
      }

      return list.toArray(new DataRef[list.size()]);
   }

   private ChartRef[] getExistedChartRef(ChartRef[] orefs, ColumnSelection cols, Viewsheet vs,
                                         String tname)
   {
      List<ChartRef> list = new ArrayList<>();

      for(int i = 0; i < orefs.length; i++) {
         if(isRefExisted(orefs[i], cols, vs, tname)) {
            list.add(orefs[i]);
         }
      }

      return list.toArray(new ChartRef[list.size()]);
   }

   private void fixChartInfo(ChartVSAssembly chart, ColumnSelection cols, Viewsheet vs,
                             String tname)
   {
      VSChartInfo vinfo = chart.getVSChartInfo();
      vinfo.setRTXFields(getExistedChartRef(vinfo.getRTXFields(), cols, vs, tname));
      vinfo.setRTYFields(getExistedChartRef(vinfo.getRTYFields(), cols, vs, tname));

      for(int i = vinfo.getXFieldCount() - 1; i >= 0; i--) {
         if(!isRefExisted(vinfo.getXField(i), cols, vs, tname)) {
            vinfo.removeXField(i);
         }
      }

      for(int i = vinfo.getYFieldCount() - 1; i >= 0; i--) {
         if(!isRefExisted(vinfo.getYField(i), cols, vs, tname)) {
            vinfo.removeYField(i);
         }
      }

      for(int i = vinfo.getGroupFieldCount() - 1; i >= 0; i--) {
         if(!isRefExisted(vinfo.getGroupField(i), cols, vs, tname)) {
            vinfo.removeGroupField(i);
         }
      }

      if(vinfo.getColorField() != null && vinfo.getColorField().getDataRef() != null) {
         if(!isRefExisted(vinfo.getColorField().getDataRef(), cols, vs, tname)) {
            vinfo.setColorField(null);
         }
      }

      if(vinfo.getShapeField() != null && vinfo.getShapeField().getDataRef() != null) {
         if(!isRefExisted(vinfo.getShapeField().getDataRef(), cols, vs, tname)) {
            vinfo.setShapeField(null);
         }
      }

      if(vinfo.getTextField() != null && vinfo.getTextField().getDataRef() != null) {
         if(!isRefExisted(vinfo.getTextField().getDataRef(), cols, vs, tname)) {
            vinfo.setTextField(null);
         }
      }

      if(vinfo.getSizeField() != null && vinfo.getSizeField().getDataRef() != null) {
         if(!isRefExisted(vinfo.getSizeField().getDataRef(), cols, vs, tname)) {
            vinfo.setSizeField(null);
         }
      }

      if(vinfo instanceof RelationChartInfo) {
         RelationChartInfo info2 = (RelationChartInfo) vinfo;

         if(info2.getNodeColorField() != null && info2.getNodeColorField().getDataRef() != null) {
            if(!isRefExisted(info2.getNodeColorField().getDataRef(), cols, vs, tname)) {
               info2.setNodeColorField(null);
            }
         }

         if(info2.getNodeSizeField() != null && info2.getNodeSizeField().getDataRef() != null) {
            if(!isRefExisted(info2.getNodeSizeField().getDataRef(), cols, vs, tname)) {
               info2.setNodeSizeField(null);
            }
         }
      }
   }

   private boolean isRefExisted(DataRef ref, ColumnSelection cols, Viewsheet vs, String tname) {
      String col = ref.getAttribute();

      if(ref instanceof VSDimensionRef) {
         col = ((VSDimensionRef) ref).getGroupColumnValue();
      }
      else if(ref instanceof VSAggregateRef) {
         col = ((VSAggregateRef) ref).getColumnValue();
      }

      if(col == null) {
         col = ref.getName();
      }

      if(col == null) {
         return true;
      }

      // For variable and expression, it is runtime values, so when update binding we can not check
      // if it is exist or not, try our best to keep current binding, so keep it, if do not exited,
      // it will show right warning for user about the column.
      if(col.startsWith("=") || col.startsWith("$")) {
         return true;
      }

      if(cols.getAttribute(col) != null) {
         return true;
      }

      if(vs.getCalcField(tname, col) != null) {
         return true;
      }

      return false;
   }

   private void clearDataAssemblyBindings(DataVSAssembly data) {
      data.setSourceInfo(null);

      if(data instanceof TableVSAssembly) {
         TableVSAssembly table = (TableVSAssembly) data;
         table.getColumnSelection().clear();
      }
      else if(data instanceof CalcTableVSAssembly) {
         removeCalcBinding((CalcTableVSAssembly) data);
      }
   }

   /**
    * Update the DataVSAssembly after viewsheet source changed.
    * @param assembly the target DataVSAssembly.
    * @param vs       the viewsheet which source have already be changed.
    */
   private void updateDataVSAssembly(DataVSAssembly assembly, Viewsheet vs) {
      if(!(assembly instanceof TableVSAssembly)) {
         return;
      }

      TableVSAssembly table = (TableVSAssembly) assembly;
      Worksheet ws = vs.getBaseWorksheet();
      SourceInfo source = table.getSourceInfo();
      TableAssembly tassembly = (TableAssembly) ws.getAssembly(source.getSource());

      if(tassembly != null) {
         table.setColumnSelection(tassembly.getColumnSelection(true));
      }
   }

   private TreeNodeModel getTree(Viewsheet vs) {
      List<TreeNodeModel> treeChildren = new ArrayList<>();

      Assembly[] assemblies = vs.getAssemblies();
      Arrays.sort(assemblies, Comparator.comparing(Assembly::getName));

      for(Assembly assembly : assemblies) {
         int type = assembly.getAssemblyType();

         if(!isNeedLocalize(type)) {
            continue;
         }

         String[] children = placeholderService.getChildNodes(assembly);
         List<TreeNodeModel> nodeChildren = new ArrayList<>();

         if(children != null) {
            for(String child : children) {
               nodeChildren.add(TreeNodeModel.builder()
                  .label(child)
                  .icon("composer-toolbox-image composer-component-tree-file")
                  .leaf(true)
                  .data(assembly.getName() + "^_^" + child)
                  .build());
            }
         }

         treeChildren.add(TreeNodeModel.builder()
            .label(assembly.getName())
            .icon("composer-toolbox-image " + getIcon(type))
            .leaf(nodeChildren.isEmpty())
            .children(nodeChildren)
            .build());
      }

      Catalog catalog = Catalog.getCatalog();

      return TreeNodeModel.builder()
         .label(catalog.getString("Components"))
         .icon("composer-toolbox-image composer-component-tree-file")
         .children(treeChildren)
         .build();
   }

   private boolean isNeedLocalize(int type) {
      return type != Viewsheet.SLIDING_SCALE_ASSET &&
         type != Viewsheet.TIME_SLIDER_ASSET && type != Viewsheet.GAUGE_ASSET &&
         type != Viewsheet.IMAGE_ASSET && type != Viewsheet.THERMOMETER_ASSET &&
         type != Viewsheet.CYLINDER_ASSET && type != Viewsheet.SLIDER_ASSET &&
         type != Viewsheet.SPINNER_ASSET && type != Viewsheet.COMBOBOX_ASSET &&
         type != Viewsheet.TEXTINPUT_ASSET && type != Viewsheet.LINE_ASSET &&
         type != Viewsheet.RECTANGLE_ASSET && type != Viewsheet.OVAL_ASSET &&
         type != Viewsheet.TAB_ASSET &&
         type != Viewsheet.GROUPCONTAINER_ASSET &&
         type != Viewsheet.VIEWSHEET_ASSET &&
         type != Viewsheet.UPLOAD_ASSET;
   }

   private String getIcon(int type) {
      switch(type) {
         case Viewsheet.CHART_ASSET:
            return "chart-icon";
         case Viewsheet.CROSSTAB_ASSET:
            return "crosstab-icon";
         case Viewsheet.TABLE_ASSET:
            return "table-icon";
         case Viewsheet.FORMULA_TABLE_ASSET:
            return "formula-table-icon";
         case Viewsheet.SELECTION_LIST_ASSET:
            return "selection-list-icon";
         case Viewsheet.SELECTION_TREE_ASSET:
            return "selection-tree-icon";
         case Viewsheet.TIME_SLIDER_ASSET:
            return "range-slider-icon";
         case Viewsheet.CALENDAR_ASSET:
            return "calendar-icon";
         case Viewsheet.CURRENTSELECTION_ASSET:
            return "selection-container-icon";
         case Viewsheet.TEXT_ASSET:
            return "text-box-icon";
         case Viewsheet.IMAGE_ASSET:
            return "image-icon";
         case Viewsheet.GAUGE_ASSET:
            return "gauge-icon";
         case Viewsheet.SLIDER_ASSET:
            return "slider-icon";
         case Viewsheet.SPINNER_ASSET:
            return "spinner-icon";
         case Viewsheet.CHECKBOX_ASSET:
            return "checkbox-icon";
         case Viewsheet.RADIOBUTTON_ASSET:
            return "radio-button-icon";
         case Viewsheet.COMBOBOX_ASSET:
            return "dropdown-box-icon";
         case Viewsheet.TEXTINPUT_ASSET:
            return "text-input-icon";
         case Viewsheet.SUBMIT_ASSET:
            return "submit-icon";
         case Viewsheet.UPLOAD_ASSET:
            return "upload-icon";
         case Viewsheet.LINE_ASSET:
            return "line-icon";
         case Viewsheet.RECTANGLE_ASSET:
            return "rectangle-icon";
         case Viewsheet.OVAL_ASSET:
            return "oval-icon";
         case Viewsheet.TABLE_VIEW_ASSET:
            return "table-icon";
         default:
            return "";
      }
   }

   private Worksheet getLogicModelWorksheet(AssetRepository rep, Viewsheet vs, Principal user) throws Exception
   {
      AssetEntry baseEntry = vs.getBaseEntry();
      ViewsheetInfo vinfo = vs.getViewsheetInfo();
      Worksheet ws = vs.getBaseWorksheet();
      ws = new WorksheetWrapper(ws);
      VSUtil.shrinkTable(vs, ws);
      Assembly[] arr = ws.getAssemblies();
      ColumnSelection cols = null;
      BoundTableAssembly btable = null;

      for(Assembly assembly : arr) {
         if(assembly instanceof BoundTableAssembly) {
            btable = (BoundTableAssembly) assembly;
            break;
         }
      }

      if(btable != null) {
         cols = btable.getColumnSelection(false);
      }

      cols = cols == null ? new ColumnSelection() : cols;

      String prefix = baseEntry.getProperty("prefix");
      String source = baseEntry.getProperty("source");
      String type = baseEntry.getProperty("type");
      String folderDesc = baseEntry.getProperty("folder_description");
      SourceInfo sinfo = new SourceInfo(Integer.parseInt(type), prefix, source);

      if(folderDesc != null) {
         sinfo.setProperty(SourceInfo.QUERY_FOLDER, folderDesc);
      }

      Worksheet newWS = new Worksheet();
      sinfo.setProperty("direct", "true");

      // fix bug1313184971713, if is direct source,
      // set maximum rows of detail table for design mode
      newWS.getWorksheetInfo().setDesignMaxRows(vinfo.getDesignMaxRows());

      // bypass vpm?
      if(vinfo.isBypassVPM() && user instanceof XPrincipal) {
         XPrincipal xuser = (XPrincipal) user;
         xuser = (XPrincipal) xuser.clone();
         xuser.setProperty("bypassVPM", "true");
         user = xuser;
      }

      List<AssetEntry> lentries = new ArrayList<>();
      AssetEntry[] entries = rep.getEntries(baseEntry, user, ResourceAction.READ);

      for(AssetEntry childEntry : entries) {
         lentries.addAll(Arrays.asList(rep.getEntries(childEntry, user, ResourceAction.READ)));
      }

      entries = lentries.toArray(new AssetEntry[]{});
      ColumnSelection columns = new ColumnSelection();

      for(AssetEntry col : entries) {
         String entity = col.getProperty("entity");
         String attr = col.getProperty("attribute");
         final String description = col.getProperty("description");
         String alias = null;

         if(attr.startsWith(entity + ":")) {
            alias = attr;
            attr = attr.substring(entity.length() + 1);
         }

         AttributeRef attributeRef = new AttributeRef(entity, attr);
         ColumnRef ref = new ColumnRef(attributeRef);
         ref.setDescription(description);

         if(attr.contains(".") && alias == null) {
            alias = AssetUtil.findAlias(columns, ref);
         }

         ref.setDataType(col.getProperty("dtype"));

         if(alias != null) {
            ref.setAlias(alias);
         }

         if(col.getProperty("refType") != null) {
            attributeRef.setRefType(
               Integer.parseInt(col.getProperty("refType")));
         }

         if(col.getProperty("formula") != null) {
            attributeRef.setDefaultFormula(col.getProperty("formula"));
         }

         if(col.getProperty("sqltype") != null) {
            int sqlType = Integer.parseInt(col.getProperty("sqltype"));
            ref.setSqlType(sqlType);
            attributeRef.setSqlType(sqlType);
         }

         if(cols.isEmpty() || cols.containsAttribute(ref)) {
            columns.addAttribute(ref);
         }
      }

      String tableName = VSUtil.getTableName(baseEntry.getName());
      BoundTableAssembly table = new BoundTableAssembly(newWS, tableName);
      table.setSourceInfo(sinfo);
      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);
      newWS.addAssembly(table);

      return newWS;
   }

   private static double getInchToUnitRatio(String units) {
      final double ratio;

      switch(units) {
         case "mm":
            ratio = RATIO_INCH_MM;
            break;
         case "points":
            ratio = RATIO_INCH_POINT;
            break;
         default:
            ratio = 1;
      }

      return ratio;
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final VSLayoutService layoutService;
   private final ViewsheetSettingsService viewsheetSettingsService;

   private static final double RATIO_INCH_MM = 25.4;
   private static final double RATIO_INCH_POINT = 72;
}
