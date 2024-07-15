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
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.graph.data.SumDataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.controller.RepositoryTreeService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller that provides the endpoints for the hyperlink dialog.
 *
 * @since 12.3
 */
@Controller
public class HyperlinkDialogController {
   /**
    * Creates a new instance of <tt>HyperlinkDialogController</tt>.
    *
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param placeholderService      PlaceholderService instance
    * @param vsObjectPropertyService VSObjectPropertyService instance
    */
   @Autowired
   public HyperlinkDialogController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      VSObjectPropertyService vsObjectPropertyService,
      ViewsheetService viewsheetService,
      DataRefModelFactoryService dataRefModelService,
      VSTrapService trapService,
      AssetRepository assetRepository,
      RepositoryTreeService repositoryTreeService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.viewsheetService = viewsheetService;
      this.dataRefModelService = dataRefModelService;
      this.trapService = trapService;
      this.assetRepository = assetRepository;
      this.repositoryTreeService = repositoryTreeService;
   }

   /**
    * Gets the model for the hyperlink dialog.
    *
    * @param objectId  the object identifier.
    * @param row       the row of the selected cell.
    * @param col       the column of the selected cell.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    *
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/hyperlink-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public HyperlinkDialogModel getHyperlinkDialogModel(
      @RequestParam("objectId") String objectId,
      @RequestParam(value = "row", required = false, defaultValue = "0") Integer row,
      @RequestParam(value = "col", required = false, defaultValue = "0") Integer col,
      @RequestParam(value = "colName", required = false) String colName,
      @RequestParam(value = "isAxis", required = false) boolean isAxis,
      @RequestParam(value = "isText", required = false) boolean isText,
      @RequestParam("runtimeId") String runtimeId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(objectId);
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      Hyperlink hyperlink = null;

      if(assembly instanceof TableDataVSAssembly) {
         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getInfo();

         VSTableLens lens = box.getVSTableLens(objectId, false);
         boolean isRowHeader = row < lens.getHeaderRowCount();
         TableDataPath dataPath = lens.getTableDataPath(row, col);

         if(!isRowHeader && info.getRowHyperlink() != null) {
            hyperlink = info.getRowHyperlink();
         }

         if(hyperlink == null && info.getHyperlinkAttr() != null) {
            hyperlink = info.getHyperlinkAttr().getHyperlink(dataPath);
         }

         model.setFields(getFields(rvs, assembly, row, col, dataPath, null));
         model.setRow(row);
         model.setCol(col);
         model.setTable(true);
         model.setShowRow(assembly instanceof TableVSAssembly &&
            !(assembly instanceof EmbeddedTableVSAssembly) && !isRowHeader);
      }
      else if(assembly instanceof ChartVSAssembly) {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
         VSChartInfo chartInfo = info.getVSChartInfo();
         ChartRef ref = getMeasure(chartInfo, colName, true, isAxis, isText);

         if(chartInfo instanceof MergedVSChartInfo &&
            !(GraphUtil.isDimension(ref) && !(ref instanceof VSChartGeoRef)))
         {
            hyperlink = chartInfo.getHyperlink();
         }
         else {
            ChartRef[] chartRefs = chartInfo.getFields(colName,
               DateComparisonUtil.appliedDateComparison(info));
            ChartRef chartRef = chartRefs.length == 0 ? ref : chartRefs[0];

            hyperlink = chartRef instanceof HyperlinkRef ?
               ((HyperlinkRef) chartRef).getHyperlink() : null;
         }

         model.setColName(colName);
         model.setFields(getFields(rvs, assembly, row, col, null, colName));
         model.setTable(false);
      }
      else {
         VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
         hyperlink = info.getHyperlinkValue();
         model.setFields(getFields(rvs, assembly, row, col, null, null));
         model.setTable(false);
      }

      model.setAxis(isAxis);

      if(hyperlink == null) {
         model.setLinkType(NONE);
         model.setSelf(true);
         model.setSendViewsheetParameters(true);

         return model;
      }

      model.setBookmark(hyperlink.getBookmarkName());
      model.setTargetFrame("SELF".equals(hyperlink.getTargetFrame()) ?
                              "" : hyperlink.getTargetFrame());
      model.setSelf("SELF".equals(hyperlink.getTargetFrame()));
      model.setTooltip(hyperlink.getToolTip());
      model.setLinkType(hyperlink.getLinkType());

      int type = hyperlink.getLinkType();

      if(type == Hyperlink.WEB_LINK || type == Hyperlink.MESSAGE_LINK) {
         model.setWebLink(hyperlink.getLinkValue());
      }
      else {
         AssetEntry entry = AssetEntry.createAssetEntry(hyperlink.getLink());

         if(entry != null) {
            String path = entry.getScope() == AssetRepository.USER_SCOPE &&
               principal.getName().equals(entry.getUser().convertToKey()) ?
               Tool.MY_DASHBOARD + "/" + entry.getPath() : entry.getPath();
            model.setAssetLinkId(hyperlink.getLink());
            model.setAssetLinkPath(path);
         }
         else {
            model.setAssetLinkId(hyperlink.getLink());
            model.setAssetLinkPath(hyperlink.getLink());
         }
      }

      model.setDisableParameterPrompt(hyperlink.isDisablePrompting());
      model.setSendSelectionsAsParameters(hyperlink.isSendSelectionParameters());
      model.setSendViewsheetParameters(hyperlink.isSendReportParameters());
      model.setApplyToRow(hyperlink.isApplyToRow());

      List<InputParameterDialogModel> parameters = new ArrayList<>();

      for(String name : hyperlink.getParameterNames()) {
         InputParameterDialogModel paramModel = new InputParameterDialogModel();
         paramModel.setName(name);
         paramModel.setValue(fixParameterValue(hyperlink.getParameterType(name),
            hyperlink.getParameterField(name)));
         paramModel.setType(hyperlink.getParameterType(name));
         paramModel.setValueSource(paramModel.getType() == null ? "field": "constant");
         parameters.add(paramModel);
      }

      model.setParamList(parameters);

      return model;
   }

   @RequestMapping(
      value = "/api/composer/vs/hyperlink-parameters",
      method = RequestMethod.GET
   )
   @ResponseBody
   public String[] getViewsheetParameters(
      @RequestParam("assetId") String assetId, Principal principal)
      throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(assetId);
      Viewsheet vs = (Viewsheet)
         assetRepository.getSheet(entry, principal, false, AssetContent.NO_DATA);

      if(vs == null) {
         return new String[0];
      }

      return Arrays.stream(vs.getAllVariables())
         .map(v -> v.getName())
         .toArray(String[]::new);
   }

   @RequestMapping(
      value = "/api/composer/report/hyperlink-parameters",
      method = RequestMethod.GET
   )
   @ResponseBody
   public String[] getReportParameters(
      @RequestParam("report") String report, Principal principal)
      throws Exception
   {
      return VSEventUtil.getReportParameters(report, principal);
   }

   @RequestMapping(
      value = "/api/composer/vs/hyperlink-dialog-model/tree",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TreeNodeModel getFolder(
      @RequestParam(value = "path", defaultValue = "/") String path,
      @RequestParam(value = "isOnPortal", defaultValue = "false") String isOnPortal,
      Principal principal) throws Exception
   {
      int selector = RepositoryEntry.VIEWSHEET | RepositoryEntry.FOLDER;
      TreeNodeModel root = repositoryTreeService.getRootFolder("/", ResourceAction.READ, selector,
         "", principal, "true".equals(isOnPortal));
      return root;
   }

   private String fixParameterValue(String type, String value) {
      if(value == null) {
         return value;
      }

      int idx = value.indexOf("'");

      if(idx < 0) {
         return value;
      }

      int idx0 = value.lastIndexOf("'");

      if(XSchema.DATE.equals(type) || XSchema.TIME.equals(type) ||
         XSchema.TIME_INSTANT.equals(type)) {
         return value.substring(idx + 1, idx0);
      }

      return value;
   }

   /**
    * Get the bookmarks of a specific sheet.
    *
    * @param id        the id of the viewsheet.
    * @param principal the user information.
    *
    * @return the list of bookmarks.
    */
   @GetMapping("/api/composer/vs/hyperlink-dialog-model/bookmarks/**")
   @ResponseBody
   public List<String> getBookmarks(@RemainingPath String id, Principal principal) throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      VSBookmarkInfo[] bookmarks = VSUtil.getBookmarks(id, pId);
      List<String> bookmarkNames = new ArrayList<>();

      for(VSBookmarkInfo bookmark: bookmarks) {
         bookmarkNames.add(bookmark.getName() + "(" + VSUtil.getUserAlias(bookmark.getOwner()) + ")");
      }

      return bookmarkNames;
   }

   /**
    * Sets information gathered from the hyperlink dialog.
    *
    * @param objectId   the object id
    * @param model      the hyperlink dialog model.
    * @param principal  the user information.
    * @param dispatcher the command dispatcher.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/hyperlink-dialog-model/{objectId}")
   public void setHyperlinkDialogModel(@DestinationVariable("objectId") String objectId,
                                       @Payload HyperlinkDialogModel model,
                                       @LinkUri String linkUri,
                                       Principal principal,
                                       CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(),
                                                           principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(objectId);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      Hyperlink hyperlink = getHyperlink(model, pId);

      if(assembly instanceof TableDataVSAssembly) {
         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getInfo();

         if(!model.isApplyToRow()) {
            VSTableLens lens = box.getVSTableLens(objectId, false);
            TableDataPath dataPath = lens.getTableDataPath(model.getRow(), model.getCol());

            if(info.getHyperlinkAttr() == null) {
               info.setHyperlinkAttr(new TableHyperlinkAttr());
            }

            info.getHyperlinkAttr().setHyperlink(dataPath, hyperlink);
            info.setRowHyperlink(null);
         }
         else {
            info.setHyperlinkAttr(new TableHyperlinkAttr());
            info.setRowHyperlink(hyperlink);
         }

         int hint = assembly.setVSAssemblyInfo(info);

         this.placeholderService.execute(rvs, assembly.getAbsoluteName(), linkUri, hint,
                                         dispatcher);
      }
      else if(assembly instanceof ChartVSAssembly) {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());
         VSChartInfo chartInfo = info.getVSChartInfo();
         ChartRef ref = getMeasure(chartInfo, model.getColName(), true, model.isAxis(), model.isText());
         DataRef dcRef = info.getDCBIndingRef(model.getColName());
         ChartRef[] chartRefs = chartInfo.getFields(model.getColName(), dcRef != null);

         if(chartInfo instanceof MergedVSChartInfo &&
            !(GraphUtil.isDimension(ref) && !(ref instanceof VSChartGeoRef)))
         {
            ((MergedVSChartInfo) chartInfo).setHyperlink(hyperlink);
         }
         else {
            if(dcRef instanceof ChartRef) {
               chartRefs = (ChartRef[]) ArrayUtils.add(chartRefs, dcRef);
            }

            if(chartRefs.length == 0 && ref != null) {
               if(ref instanceof HyperlinkRef) {
                  ((HyperlinkRef) ref).setHyperlink(hyperlink);
               }
            }
            else {
               for(ChartRef chartRef : chartRefs) {
                  if(chartRef instanceof HyperlinkRef) {
                     ((HyperlinkRef) chartRef).setHyperlink(hyperlink);
                  }
               }
            }
         }

         this.vsObjectPropertyService.editObjectProperty(
            rvs, info, objectId, objectId, linkUri, principal, dispatcher);
      }
      else {
         VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();

         info.setHyperlinkValue(hyperlink);
         int hint = assembly.setVSAssemblyInfo(info);

         this.placeholderService.execute(rvs, assembly.getAbsoluteName(), linkUri, hint,
                                         dispatcher);
      }
   }

   public List<DataRefModel> getFields(RuntimeViewsheet rvs, VSAssembly assembly,
                                 int row, int col, TableDataPath dpath,
                                 String colName) throws Exception
   {
      if(assembly instanceof OutputVSAssembly &&
         ((OutputVSAssembly) assembly).getScalarBindingInfo() != null) {
         DataRef column = ((OutputVSAssembly) assembly).getScalarBindingInfo().getColumn();

         if(column != null) {
            if(column instanceof ColumnRef) {
               column = VSUtil.getVSColumnRef((ColumnRef) column);
            }

            DataRefModel ref = this.dataRefModelService.createDataRefModel(column);

            if(ref.getEntity() != null && ref.getName().startsWith(ref.getEntity() + ".")) {
               ref.setName(ref.getName().substring(ref.getEntity().length() + 1));
            }

            return Collections.singletonList(ref);
         }
      }

      List<DataRef> refs = this.placeholderService.getRefsForVSAssembly(
         rvs, assembly, row, col, dpath, colName, false, true);

      if(assembly instanceof ChartVSAssembly) {
         ChartVSAssemblyInfo assemblyInfo = (ChartVSAssemblyInfo) assembly.getInfo();
         ChartInfo cinfo = assemblyInfo.getVSChartInfo();

         if(colName != null && colName.startsWith(BoxDataSet.MAX_PREFIX)) {
            processBoxplot(cinfo, refs);
         }
      }

      List<DataRefModel> models =  this.getAvailableRefs(assembly, refs, colName)
         .stream()
         .distinct()
         .map(this.dataRefModelService::createDataRefModel)
         .collect(Collectors.toList());

      for(int i = 0; i < models.size(); i ++) {
         if(models.get(i).getAttribute().isEmpty()) {
            models.get(i).setName("Column [" + i + "]");
         }
      }

      return models;
   }

   private static void processBoxplot(ChartInfo info, List<DataRef> refs) {
      if(GraphTypes.isBoxplot(info.getRTChartType())) {
         for(int i = refs.size() - 1; i >= 0; i--) {
            ChartRef ref = info.getFieldByName(refs.get(i).getName(), false);

            if(!(ref instanceof VSChartDimensionRef)) {
               refs.remove(i);
            }
         }

         List<XAggregateRef> yrefs = GraphUtil.getMeasures(info.getYFields());

         if(yrefs.isEmpty()) {
            yrefs = GraphUtil.getMeasures(info.getXFields());
         }

         for(XAggregateRef yref : yrefs) {
            String name = yref.getName();
            refs.add(new ColumnRef(new AttributeRef(name)));
            refs.add(new ColumnRef(new AttributeRef(BoxDataSet.MAX_PREFIX + name)));
            refs.add(new ColumnRef(new AttributeRef(BoxDataSet.MEDIUM_PREFIX + name)));
            refs.add(new ColumnRef(new AttributeRef(BoxDataSet.MIN_PREFIX + name)));
            refs.add(new ColumnRef(new AttributeRef(BoxDataSet.Q25_PREFIX + name)));
            refs.add(new ColumnRef(new AttributeRef(BoxDataSet.Q75_PREFIX + name)));
         }
      }
   }

   /**
    * Get avaliable data refs for hyperlink parameter, for a measure data ref,
    * all refs is avaliable, for a dimension ref, only the dimension refs binding
    * in the same axis, and should be outer of the specifield field is avaliable.
   */
   private List<DataRef> getAvailableRefs(VSAssembly assembly, List<DataRef> refs,
      String fieldName)
   {
      if(!(assembly instanceof ChartVSAssembly)) {
         return refs;
      }

      VSChartInfo vschartInfo = ((ChartVSAssembly) assembly).getVSChartInfo();
      return getAvailableRefs(vschartInfo, refs, fieldName);
   }

   /**
    * Get field for hyperlink parameters.
    */
   public static List<DataRef> getAvailableRefs(ChartInfo chartInfo, List<DataRef> refs,
                                                String fieldName)
   {
      ChartRef href = chartInfo.getFieldByName(fieldName, true);

      // aggregate and geo ref support all binding
      if(!isAbsoluteDim(href)) {
         return refs;
      }

      ChartRef[] xrefs = chartInfo.getRTXFields();
      ChartRef[] brefs = null;

      for(int i = 0; i < xrefs.length; i++) {
         if(xrefs[i] != null && Tool.equals(fieldName, xrefs[i].getFullName())) {
            brefs = xrefs;
            break;
         }
      }

      if(brefs == null) {
         ChartRef[] yrefs = getChartFields(chartInfo, fieldName);

         for(int i = 0; i < yrefs.length; i++) {
            if(yrefs[i] != null && Tool.equals(fieldName, yrefs[i].getFullName())) {
               brefs = yrefs;
               break;
            }
         }
      }

      if(GraphTypeUtil.isBreakByRadar(chartInfo))  {
         ChartRef[] grefs = chartInfo.getRTGroupFields();
         List<ChartRef> list = new ArrayList<>();

         for(int i = 0; i < grefs.length; i++) {
            if(GraphUtil.isDimension(grefs[i])) {
               list.add(grefs[i]);
            }

            if(Tool.equals(GraphUtil.getBaseName(grefs[i]), fieldName)) {
               break;
            }
         }

         brefs = list.toArray(new ChartRef[list.size()]);
      }

      boolean ganttField = false;

      if(brefs == null && chartInfo instanceof GanttChartInfo) {
         ChartRef field = ((GanttChartInfo) chartInfo).getRTStartField();

         if(field == null) {
            field = ((GanttChartInfo) chartInfo).getRTEndField();
         }

         if(field == null) {
            field = ((GanttChartInfo) chartInfo).getRTMilestoneField();
         }

         if(field != null && Tool.equals(fieldName, field.getFullName())) {
            brefs = new ChartRef[1];
            brefs[0] = field;
            ganttField = true;
         }
      }

      if(brefs == null || brefs.length <= 0) {
         return refs;
      }

      List<DataRef> nrefs = new ArrayList<>();

      for(int i = 0; i < brefs.length; i++) {
         if(GraphUtil.isDimension(brefs[i]) || ganttField) {
            boolean found = false;

            for(int j = 0; j < refs.size(); j++) {
               if(brefs[i] != null && refs.get(j) != null &&
                  Tool.equals(brefs[i].getFullName(), refs.get(j).getName()))
               {
                  nrefs.add(refs.get(j));
                  found = true;
                  break;
               }
            }

            if(!found) {
               for(int j = 0; j < refs.size(); j++) {
                  if(brefs[i] != null && refs.get(j) != null &&
                     Tool.equals(brefs[i].getName(), refs.get(j).getName()))
                  {
                     nrefs.add(refs.get(j));
                     break;
                  }
               }
            }

            if(Tool.equals(brefs[i].getFullName(), fieldName)) {
               break;
            }
         }
      }

      return nrefs;
   }

   private static ChartRef[] getChartFields(ChartInfo info, String fieldName) {
      ChartRef[] chartRefs = info.getRTYFields();

      if(info instanceof VSChartInfo) {
         VSChartInfo vschartInfo = (VSChartInfo) info;

         if(vschartInfo.isPeriodRef(fieldName)) {
            chartRefs = (ChartRef[]) ArrayUtils.add(chartRefs, vschartInfo.getPeriodField());
         }
      }

      return chartRefs;
   }

   /**
    * Check the ref is dimension ref but not geo ref.
    */
   private static boolean isAbsoluteDim(ChartRef ref) {
      return GraphUtil.isDimension(ref) && !(ref instanceof GeoRef);
   }

   /**
    * Check whether the parameters set for the table hyperlink will cause a trap.
    *
    * @param model     the model containing the hyperlink model
    * @param objectId  the object id
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @PostMapping("/api/composer/viewsheet/check-hyperlink-dialog-trap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkVSTableTrap(
      @RequestBody() HyperlinkDialogModel model,
      @PathVariable("objectId") String objectId,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(objectId);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(!(assembly instanceof TableVSAssembly)) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      TableVSAssemblyInfo oinfo = (TableVSAssemblyInfo) assembly.getInfo().clone();
      TableVSAssemblyInfo ninfo = (TableVSAssemblyInfo) assembly.getInfo().clone();
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      Hyperlink hyperlink = getHyperlink(model, pId);
      VSTableLens lens = box.getVSTableLens(objectId, false);
      TableDataPath dataPath = lens.getTableDataPath(model.getRow(), model.getCol());

      if(ninfo.getHyperlinkAttr() == null) {
         ninfo.setHyperlinkAttr(new TableHyperlinkAttr());
      }

      ninfo.getHyperlinkAttr().setHyperlink(dataPath, hyperlink);
      assembly.setVSAssemblyInfo(ninfo);

      VSTableTrapModel result = trapService.checkTrap(rvs, oinfo, ninfo);
      assembly.setVSAssemblyInfo(oinfo);

      return result;
   }

   private Hyperlink getHyperlink(HyperlinkDialogModel model, IdentityID currentUser) {
      Hyperlink hyperlink;

      if(model.getLinkType() == NONE) {
         hyperlink = null;
      }
      else {
         hyperlink = new Hyperlink();

         hyperlink.setLinkType(model.getLinkType());
         hyperlink.setLink((model.getLinkType() == Hyperlink.WEB_LINK ||
                            model.getLinkType() == Hyperlink.MESSAGE_LINK) ?
                           model.getWebLink() : model.getAssetLinkId());

         if(model.getLinkType() == Hyperlink.VIEWSHEET_LINK) {
            VSBookmarkInfo[] bookmarks = VSUtil.getBookmarks(model.getAssetLinkId(), currentUser);
            String bm = model.getBookmark();

            if(bm != null) {
               int userIdx = bm.lastIndexOf('(') > 0 ? bm.lastIndexOf('(') : bm.length();
               String bookmarkName = bm.substring(0, userIdx);

               for(VSBookmarkInfo bookmark : bookmarks) {
                  if(bookmark.getName().equals(bookmarkName)) {
                     hyperlink.setBookmarkName(bookmark.getName());
                     hyperlink.setBookmarkUser(bookmark.getOwner().convertToKey());
                     break;
                  }
               }
            }
         }

         hyperlink.setTargetFrame(model.getTargetFrame());
         hyperlink.setToolTip(model.getTooltip());
         hyperlink.setDisablePrompting(model.isDisableParameterPrompt());
         hyperlink.setSendReportParameters(model.isSendViewsheetParameters());
         hyperlink.setSendSelectionParameters(model.isSendSelectionsAsParameters());
         hyperlink.setApplyToRow(model.isApplyToRow());
         hyperlink.removeAllParameterFields();

         for(InputParameterDialogModel param : model.getParamList()) {
            if("constant".equals(param.getValueSource())) {
               hyperlink.setParameterType(param.getName(), param.getType());
            }

            hyperlink.setParameterField(param.getName(), GTool.toString(param.getValue()));
         }
      }

      return hyperlink;
   }

   // get the measure associated with this hyperlink dialog
   public static ChartRef getMeasure(ChartInfo chartInfo, String colName, boolean getPeriodRef,
                                     boolean isAxis, boolean isText)
   {
      if(colName == null) {
         return null;
      }

      if(colName.startsWith(SumDataSet.ALL_HEADER_PREFIX)) {
         colName = colName.substring(SumDataSet.ALL_HEADER_PREFIX.length());
      }

      colName = BoxDataSet.getBaseName(colName);

      ChartRef ref = null;

      if(chartInfo instanceof VSChartInfo) {
         ChartRef[] refs = ((VSChartInfo) chartInfo).getRuntimeDateComparisonRefs();

         final String column = colName;
         ref = Arrays.stream(refs)
            .filter(chartRef -> chartRef.getFullName().equals(column))
            .findAny()
            .orElse(null);
      }

      if(ref == null) {
         // make sure it's axis and not a node. (56974)
         if(isAxis) {
            List<VSDataRef> fields = new ArrayList<>(Arrays.asList(chartInfo.getXFields()));
            fields.addAll(Arrays.asList(chartInfo.getYFields()));

            String finalColName = colName;
            ref = (ChartRef) fields.stream()
               .filter(f -> f.getFullName().equals(finalColName))
               .findFirst().orElse(null);
         }
         else {
            ref = chartInfo.getFieldByName(colName, false);
         }
      }

      // relation highlights defined on source/target instead of textfield, which is
      // shared by source and target.
      if(GraphTypeUtil.isWordCloud(chartInfo) || isText) {
         AestheticRef aref = null;
         boolean noTextBinding = chartInfo instanceof RelationChartInfo &&
            ref.equals(((RelationChartInfo) chartInfo).getSourceField());

         if(!noTextBinding) {
            aref = chartInfo.isMultiAesthetic() && ref instanceof ChartBindable
               ? ((ChartBindable) ref).getTextField() : chartInfo.getTextField();
         }

         if(aref != null && (aref.getFullName().equals(colName) || isText)) {
            ref = (ChartRef) aref.getDataRef();
         }
      }

      if(chartInfo instanceof VSChartInfo && ref == null) {
         boolean periodPartRef = ((VSChartInfo) chartInfo).isPeriodPartRef(colName);
         ChartRef periodRef = ((VSChartInfo) chartInfo).getPeriodField();

         if(periodPartRef) {
            // for period part ref load the runtime field and set for design field.
            if(getPeriodRef) {
               ref = chartInfo.getFieldByName(colName, true);
            }
            else if(periodRef != null) {
               ChartRef periodField = (ChartRef) periodRef.clone();

               if(periodField instanceof VSDimensionRef) {
                  ((VSDimensionRef) periodField).setDates(null);
               }

               ref = chartInfo.getFieldByName(periodField.getFullName(), false);
            }
         }

         if(ref == null && ((VSChartInfo) chartInfo).isPeriodRef(colName)) {
            ref = periodRef;
         }
      }

      if(ref == null) {
         // get the runtime field first and then find the design time ref based
         // on this runtime field
         ref = chartInfo.getFieldByName(colName, true);

         if(ref instanceof VSChartDimensionRef) {
            VSChartDimensionRef rtDim = (VSChartDimensionRef) ref;
            VSDataRef[] fields = chartInfo.getFields();

            if(GraphTypes.isRadarOne(chartInfo)) {
               ChartRef groupField = chartInfo.getGroupField(0);

               // ensure priority is given to matching group fields rather than aesthetic fields.
               if(groupField instanceof VSChartDimensionRef) {
                  VSDataRef[] arr = new VSDataRef[fields.length + 1];
                  arr[0] = groupField;
                  System.arraycopy(fields, 0, arr, 1, fields.length);
                  fields = arr;
               }
            }

            for(VSDataRef vsDataRef : fields) {
               if(vsDataRef instanceof VSChartDimensionRef) {
                  VSChartDimensionRef dim = (VSChartDimensionRef) vsDataRef;

                  if(Tool.equals(dim.getGroupColumnValue(), rtDim.getGroupColumnValue())) {
                     // in case of dynamic field, multiple dimensions (e.g. state, city) may be
                     // generated from the design time ref, we set the base ref back to
                     // the runtime ref. (61582)
                     dim.setDataRef(rtDim.getDataRef());
                     ref = (ChartRef) vsDataRef;
                     break;
                  }
               }
            }
         }
      }

      return ref;
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final ViewsheetService viewsheetService;
   private final VSTrapService trapService;
   private final DataRefModelFactoryService dataRefModelService;
   private final RepositoryTreeService repositoryTreeService;
   private final AssetRepository assetRepository;
   private final int NONE = 9;
}
