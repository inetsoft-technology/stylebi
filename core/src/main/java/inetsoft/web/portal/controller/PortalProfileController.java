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
package inetsoft.web.portal.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.scale.Scale;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.lens.DataSetTable;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Catalog;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.log.LogContext;
import inetsoft.util.profile.Profile;
import inetsoft.util.profile.ProfileInfo;
import inetsoft.web.reportviewer.HandleExceptions;
import inetsoft.web.reportviewer.model.ProfileTableDataEvent;
import inetsoft.web.viewsheet.model.PreviewTableCellModel;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

@RestController
public class PortalProfileController {
   @Autowired
   public PortalProfileController(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @GetMapping("/api/portal/profile/group-by")
   @HandleExceptions
   public GroupByFieldList getGroupByFields(@RequestParam("name") String name,
                                            @RequestParam("isViewsheet") boolean isViewsheet,
                                            Principal principal) throws Exception
   {
      String recordsKey = getRecordsKey(name, isViewsheet, principal);
      Catalog catalog = Catalog.getCatalog(principal);
      ProfileInfo pinfo = Profile.getInstance().getProfileInfo();

      GroupByFieldList list = new GroupByFieldList();
      list.getFields().add(new GroupByField(catalog.getString("Cycle Name"), "cycle"));

      List<ExecutionBreakDownRecord> records = pinfo.getProfileRecords(recordsKey);

      if(records != null) {
         list.getFields().addAll(records.stream()
            .flatMap(r -> r.getContexts().stream())
            .distinct()
            .sorted()
            .map(c -> new GroupByField(getContextLabel(c, catalog), c.name()))
            .collect(Collectors.toList()));
      }

      return list;
   }

   @GetMapping(value = "/api/image/portal/profile/image")
   @HandleExceptions
   public void profileImage(
      @RequestParam("name") String name,
      @RequestParam("showValue") boolean showValue,
      @RequestParam("isViewsheet") boolean isViewsheet,
      @RequestParam(name = "groupBy", required = false, defaultValue = "cycle") String groupBy,
      HttpServletRequest request, HttpServletResponse response, Principal principal)
      throws Exception
   {
      Object[][] data =
         prepareChartData(getRecordsKey(name, isViewsheet, principal), groupBy, principal);
      Catalog catalog = Catalog.getCatalog(principal);
      String xTitle = "cycle".equals(groupBy) ? catalog.getString(CHART_X_TITLE) :
         getContextLabel(LogContext.valueOf(groupBy), catalog);
      String yTitle = catalog.getString("Time") + " (ms)";
      Graphics2D image = createProfileImage(data, xTitle, yTitle, showValue);

      if(image != null) {
         response.setContentType("image/svg+xml");
         final ByteArrayOutputStream out = new ByteArrayOutputStream();
         final OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
         SVGSupport.getInstance().writeSVG(image, writer, true);
         byte[] buf = out.toByteArray();

         final String encodingTypes = request.getHeader("Accept-Encoding");
         final ServletOutputStream outputStream = response.getOutputStream();

         try {
            if(encodingTypes != null && encodingTypes.contains("gzip")) {
               try(final GZIPOutputStream gzipOut = new GZIPOutputStream(outputStream)) {
                  response.addHeader("Content-Encoding", "gzip");
                  gzipOut.write(buf);
               }
            }
            else {
               outputStream.write(buf);
            }
         }
         catch(EOFException e) {
            LOG.debug("Broken connection while writing image", e);
         }
      }
   }

   @PutMapping(value = "/api/portal/profile/table")
   @HandleExceptions
   public PreviewTableCellModel[][] profileTable(
      @RequestParam(value = "showSummarize", required = false) boolean showSummarize,
      @RequestParam("isViewsheet") boolean isViewsheet,
      @RequestBody ProfileTableDataEvent event,
      Principal principal) throws Exception
   {
      Object[][] data = getTableData(
         showSummarize, getRecordsKey(event.getObjectName(), isViewsheet, principal), principal);

      if(data == null) {
         return null;
      }

      TableLens table = new DataSetTable(new DefaultDataSet(data));

      if(event.getSortValue() != 0) {
         table = new SortFilter(table, new int[] {event.getSortCol()},
                 event.getSortValue() == XConstants.SORT_ASC);
         table.moreRows(Integer.MAX_VALUE);
      }

      int rowCount = table.getRowCount();
      int colCount = table.getColCount();
      PreviewTableCellModel[][] model = new PreviewTableCellModel[rowCount][colCount];

      for(int row = 0; row < rowCount; row++) {
         for(int col = 0; col < colCount; col++) {
            model[row][col] = BaseTableCellModel.createPreviewCell(table, row, col, false, null);
         }
      }

      return model;
   }

   @GetMapping("/api/portal/profile/table-export")
   @HandleExceptions
   public void downloadTable(@RequestParam("name") String name,
                             @RequestParam("isViewsheet") boolean isViewsheet,
                             HttpServletResponse response, Principal principal) throws Exception
   {
      Object[][] data =
         getTableData(false, getRecordsKey(name, isViewsheet, principal), principal);

      if(data == null) {
         data = new Object[][] {
            { "No data" }
         };
      }

      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Disposition", "attachment; filename*=utf-8''profile.csv");
      response.setHeader("extension", "csv");
      response.setHeader("Cache-Control", "");
      response.setHeader("Pragma", "");
      response.setContentType("text/csv");

      try(PrintWriter writer = response.getWriter()) {
         for(Object[] row : data) {
            writer.println(Arrays.stream(row)
                              .map(c -> c == null ? "" : String.valueOf(c))
                              .collect(Collectors.joining(",")));
         }
      }
   }

   private Object[][] getTableData(boolean showSummarize, String name, Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      ProfileInfo pinfo = Profile.getInstance().getProfileInfo();
      List<ExecutionBreakDownRecord> records = pinfo.getProfileRecords(name);

      if(records == null) {
         return null;
      }

      if(showSummarize) {
         long postCycle = pinfo.getCyclePost(name);
         long queryCycle = pinfo.getCycleQuery(name);
         long jsCycle = pinfo.getCycleJavaScript(name);
         long uiCycle = pinfo.getCycleUI(name);

         Map<String, Long> map = new HashMap<>();
         addSummaryData(map, ProfileInfo.QUERY_EXECUTION_CYCLE, queryCycle);
         addSummaryData(map, ProfileInfo.POST_PROCESSING_CYCLE, postCycle);
         addSummaryData(map, ProfileInfo.JAVASCRIPT_PROCESSING_CYCLE, jsCycle);
         addSummaryData(map, ProfileInfo.UI_PROCESSING_CYCLE, uiCycle);

         Object[][] data = new Object[map.size() + 2][2];
         data[0][0] = catalog.getString("Cycle Name");
         data[0][1] = catalog.getString("Spend Time(ms)");

         int count = 1;

         for(Map.Entry<String, Long> e : map.entrySet()) {
            data[count++] = new Object[] { e.getKey(), e.getValue() };
         }

         data[count] = new Object[] {
            catalog.getString("Total"), (postCycle + queryCycle + jsCycle + uiCycle)
         };

         return data;
      }

      List<LogContext> contexts = records.stream()
         .flatMap(r -> r.getContexts().stream())
         .distinct()
         .sorted()
         .collect(Collectors.toList());

      SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
      Object[][] data = new Object[records.size() + 1][contexts.size() + 4];
      data[0][0] = catalog.getString("Cycle Name");

      for(int i = 0; i < contexts.size(); i++) {
         data[0][i + 1] = getContextLabel(contexts.get(i), catalog);
      }

      data[0][contexts.size() + 1] = catalog.getString("Start Timestamp");
      data[0][contexts.size() + 2] = catalog.getString("End Timestamp");
      data[0][contexts.size() + 3] = catalog.getString("Spend Time(ms)");

      for(int row = 0; row < records.size(); row++) {
         ExecutionBreakDownRecord record = records.get(row);
         int r = row + 1;
         data[r][0] = record.getCycleName();

         for(int i = 0; i < contexts.size(); i++) {
            data[r][i + 1] = record.getContext(contexts.get(i));
         }

         data[r][contexts.size() + 1] = formatter.format(record.getStartTimestamp());
         data[r][contexts.size() + 2] = formatter.format(record.getEndTimestamp());
         data[r][contexts.size() + 3] = getSpendTime(record.getEndTimestamp(), record.getStartTimestamp());
      }

      return data;
   }

   private String getContextLabel(LogContext context, Catalog catalog) {
      switch(context) {
      case DASHBOARD:
         return catalog.getString("asset.type.VIEWSHEET");
      case QUERY:
         return catalog.getString("asset.type.XQUERY");
      case MODEL:
         return catalog.getString("asset.type.XLOGICALMODEL");
      case WORKSHEET:
         return catalog.getString("asset.type.WORKSHEET");
      case SCHEDULE_TASK:
         return catalog.getString("asset.type.SCHEDULETASK");
      case ASSEMBLY:
         return catalog.getString("Component");
      case TABLE:
         return catalog.getString("Table");
      default:
         return context.name();
      }
   }

   private void addSummaryData(Map<String, Long> map, String cycle, long spendTime) {
      if(spendTime == 0) {
         return;
      }

      map.put(cycle, spendTime);
   }

   private long getSpendTime(long end, long start) {
      return end - start;
   }

   private Object[][] prepareChartData(String name, String groupBy, Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      ProfileInfo pinfo = Profile.getInstance().getProfileInfo();
      Object[] header;
      Stream<Object[]> values;

      if("cycle".equals(groupBy)) {
         header = new Object[] { "cycle", "time (ms)" };
         values = prepareCycleChartData(name, pinfo, catalog);
      }
      else {
         LogContext context = LogContext.valueOf(groupBy);
         header = new Object[] { context.name(), "time (ms)" };
         values = prepareContextChartData(name, context, pinfo);
      }

      values = values
         .sorted((r1, r2) -> Comparator.<Long>reverseOrder().compare((Long) r1[1], (Long) r2[1]));

      return Stream.concat(Stream.<Object[]>of(header), values).toArray(Object[][]::new);
   }

   private Stream<Object[]> prepareCycleChartData(String name, ProfileInfo pinfo, Catalog catalog) {
      return Stream.of(
         new Object[] { catalog.getString(ProfileInfo.QUERY_EXECUTION_CYCLE), pinfo.getCycleQuery(name) },
         new Object[] { catalog.getString(ProfileInfo.POST_PROCESSING_CYCLE), pinfo.getCyclePost(name) },
         new Object[] { ProfileInfo.JAVASCRIPT_PROCESSING_CYCLE, pinfo.getCycleJavaScript(name) },
         new Object[] { ProfileInfo.UI_PROCESSING_CYCLE, pinfo.getCycleUI(name) }
      );
   }

   private Stream<Object[]> prepareContextChartData(String name, LogContext context, ProfileInfo pinfo) {
      List<ExecutionBreakDownRecord> records = pinfo.getProfileRecords(name);

      if(records == null) {
         return Stream.empty();
      }

      return records.stream()
         .filter(r -> r.getContext(context) != null)
         .collect(Collectors.toMap(
            r -> r.getContext(context),
            r -> getSpendTime(r.getEndTimestamp(), r.getStartTimestamp()),
            Long::sum))
         .entrySet().stream()
         .map(e -> new Object[] { e.getKey(), e.getValue() });
   }

   /**
    * Create profile chart image.
    * @param data     the breakdown records data.
    * @param showValue true if show plot data value, else not.
    */
   private Graphics2D createProfileImage(Object[][] data, String xTitle, String yTitle,
                                            boolean showValue)
   {
      if(data == null || data.length == 0) {
         return null;
      }

      boolean dark = CustomThemesManager.getManager().isEMDarkTheme();
      Color fgColor = dark ? Color.lightGray : GDefaults.DEFAULT_TEXT_COLOR;
      Color bgColor = dark ? new Color(0x424242) : Color.WHITE;
      Color titleColor = dark ? Color.lightGray : GDefaults.DEFAULT_TITLE_COLOR;
      DataSet dataset = new DefaultDataSet(data);

      ChartDescriptor desc = new ChartDescriptor();
      desc.setPreferredSize(new Dimension(CHART_WIDTH, CHART_HEIGHT));
      desc.getPlotDescriptor().setValuesVisible(showValue);

      TitleDescriptor ydesc = new TitleDescriptor();
      ydesc.setTitleValue(yTitle);
      ydesc.getTextFormat().setRotation(90);
      ydesc.getTextFormat().setColor(titleColor);
      ydesc.getTextFormat().setBackground(bgColor);
      ydesc.getTextFormat().setFont(GDefaults.DEFAULT_TITLE_FONT);
      desc.getTitlesDescriptor().setYTitleDescriptor(ydesc);

      TitleDescriptor xdesc = new TitleDescriptor();
      xdesc.setTitleValue(xTitle);
      xdesc.getTextFormat().setColor(titleColor);
      xdesc.getTextFormat().setBackground(bgColor);
      xdesc.getTextFormat().setFont(GDefaults.DEFAULT_TITLE_FONT);
      desc.getTitlesDescriptor().setXTitleDescriptor(xdesc);

      LegendsDescriptor ldesc = desc.getLegendsDescriptor();
      ldesc.getColorLegendDescriptor().setVisible(false);
      ldesc.getShapeLegendDescriptor().setVisible(false);
      ldesc.getSizeLegendDescriptor().setVisible(false);

      VSChartInfo info = new DefaultVSChartInfo();
      info.setSeparatedGraph(false);
      info.setChartType(GraphTypes.CHART_BAR);

      desc.getPlotDescriptor().setYGridColor(dark ? bgColor.darker() : GDefaults.DEFAULT_GRIDLINE_COLOR);
      desc.getPlotDescriptor().setXGridColor(dark ? bgColor.darker() : GDefaults.DEFAULT_GRIDLINE_COLOR);
      desc.getLegendsDescriptor().getTitleTextFormat().setColor(fgColor);
      desc.getLegendsDescriptor().getTitleTextFormat().setBackground(bgColor);
      desc.getLegendsDescriptor().getColorLegendDescriptor().setColor(fgColor);
      desc.getLegendsDescriptor().getColorLegendDescriptor().getContentTextFormat().setColor(fgColor);
      desc.getLegendsDescriptor().getColorLegendDescriptor().getContentTextFormat().setBackground(bgColor);
      desc.getLegendsDescriptor().setBorderColor(dark ? bgColor.darker() : GDefaults.DEFAULT_LINE_COLOR);

      // To-Do, should set proper number format for y.
//      XFormatInfo xfinfo = new XFormatInfo();
//      info.getAxisDescriptor().getAxisLabelTextFormat().setFormat(xfinfo);

      BaseField field1 = new BaseField((String) data[0][0]);
      info.addXField(new VSChartDimensionRef(field1));

      for(int i = 1; i < data[0].length; i++) {
         BaseField field2 = new BaseField((String) data[0][i]);
         VSChartAggregateRef aref = new VSChartAggregateRef();
         aref.getTextFormat().setColor(fgColor);
         aref.getTextFormat().setBackground(null);
         aref.getTextFormat().setFont(GDefaults.DEFAULT_TEXT_FONT);
         aref.setDataRef(field2);
         info.addYField(aref);
      }

      GraphGenerator gen = GraphGenerator.getGenerator(info, desc, null, null,
                                                       dataset, new VariableTable(), XSourceInfo.NONE, null);
      EGraph egraph = gen.createEGraph();

      Coordinate coord = egraph.getCoordinate();
      Scale[] scales = coord.getScales();

      for(Scale scale : scales) {
         AxisSpec spec = scale.getAxisSpec();
         spec.getTextSpec().setColor(fgColor);
         spec.getTextSpec().setBackground(bgColor);
         spec.getTextSpec().setFont(GDefaults.DEFAULT_TEXT_FONT);
         spec.getTextSpec().setRotation(0);
         spec.setLineColor(dark ? bgColor.darker() : GDefaults.DEFAULT_LINE_COLOR);
      }

      dataset = gen.getData();
      VGraph vgraph = Plotter.getPlotter((EGraph) egraph.clone()).plotAndLayout(dataset, 0, 0, CHART_WIDTH, CHART_HEIGHT);

      SVGSupport svgSupport = SVGSupport.getInstance();
      Graphics2D g = svgSupport.createSVGGraphics();
      svgSupport.setCanvasSize(g, new Dimension(CHART_WIDTH, CHART_HEIGHT));
      g.setColor(bgColor);
      g.fillRect(0, 0, CHART_WIDTH, CHART_HEIGHT);

      vgraph.paintGraph(g, true);
      g.dispose();

      return g;
   }

   private String getRecordsKey(String name, Boolean isViewsheet, Principal principal) throws Exception {
      String key = name;

      if(isViewsheet) {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(name, principal);

         if(rvs != null) {
            key = rvs.getViewsheetSandbox().getID();
         }
      }

      return key;
   }

   private static final int CHART_WIDTH = 650;
   private static final int CHART_HEIGHT = 330;
   private static final String CHART_X_TITLE = "Record Cycle Name";
   private static final Logger LOG = LoggerFactory.getLogger(PortalProfileController.class);

   private final ViewsheetService viewsheetService;

   public static final class GroupByField {
      @SuppressWarnings("unused")
      public GroupByField() {
      }

      public GroupByField(String label, String value) {
         this.label = label;
         this.value = value;
      }

      public String getLabel() {
         return label;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }

      private String label;
      private String value;
   }

   public static final class GroupByFieldList {
      public List<GroupByField> getFields() {
         if(fields == null) {
            fields = new ArrayList<>();
         }

         return fields;
      }

      public void setFields(List<GroupByField> fields) {
         this.fields = fields;
      }

      private List<GroupByField> fields;
   }
}
