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
package inetsoft.web.admin.schedule;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.scale.Scale;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.composition.region.*;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalTime;
import java.util.*;

@RestController
public class EMScheduleController {
   @Autowired
   public EMScheduleController(ScheduleManager scheduleManager, ScheduleService scheduleService,
                               ScheduleTaskService taskService, ScheduleTaskFolderService taskFolderService)
   {
      this.scheduleService = scheduleService;
      this.scheduleManager = scheduleManager;
      this.taskService = taskService;
      this.taskFolderService = taskFolderService;
   }

   /**
    * Gets a table of scheduled tasks.
    *
    * @param selectStr the selection string
    * @param filter    filter
    *
    * @return table of tasks
    *
    * @throws Exception if could not get tasks
    */
   @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/scheduled-tasks")
   public ScheduleTaskList getScheduledTasks(
      @RequestParam("selectString") Optional<String> selectStr,
      @RequestParam("filter") Optional<String> filter,
      @PermissionUser Principal principal) throws Exception
   {
      return scheduleService.getScheduleTaskList(selectStr.orElse(""),
                                                 filter.orElse(""), principal);
   }

   @PostMapping("/api/em/schedule/scheduled-tasks")
   public ScheduleTaskList getScheduledTasksByFolder(
           @RequestParam("selectString") Optional<String> selectStr,
           @RequestParam("filter") Optional<String> filter,
           @RequestBody(required = false) ContentRepositoryTreeNode parentInfo,
           @PermissionUser Principal principal) throws Exception
   {
      AssetEntry parentEntry = null;

      if(parentInfo != null) {
         String path = parentInfo.path();
         IdentityID user = parentInfo.owner();
         int scope = user == null ? AssetRepository.GLOBAL_SCOPE :
                 AssetRepository.USER_SCOPE;

         parentEntry =  new AssetEntry(scope, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, user);
      }

      return scheduleService.getScheduleTaskList(selectStr.orElse(""),
              filter.orElse(""), parentEntry, principal);
   }

   /**
    * Remove scheduled tasks.
    *
    * @param taskList the model for the list of all the tasks
    *
    * @throws Exception if could not get task
    */
   @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
   @PostMapping("/api/em/schedule/remove")
   public ScheduleTaskList removeScheduledTasks(
      @RequestParam("selectString") Optional<String> selectStr,
      @RequestParam("filter") Optional<String> filter,
      @RequestBody ScheduleTaskModel[] taskList,
      Principal principal) throws Exception
   {
      this.scheduleService.removeScheduleItems(taskList, selectStr, filter, principal);

      return getScheduledTasksByFolder(Optional.empty(), Optional.empty(), null, principal);
   }

   /**
    * Checks for dependencies with the tasks
    *
    * @param taskList the model for the list of all the tasks
    *
    * @throws Exception if could not get task
    */
   @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
   @PostMapping("/api/em/schedule/check-dependency")
   public TaskListModel checkScheduledTaskDependency(
           @RequestParam("selectString") Optional<String> selectStr,
           @RequestParam("filter") Optional<String> filter,
           @RequestBody ScheduleTaskModel[] taskList, Principal principal) throws Exception
   {
      return this.scheduleService.checkScheduledTaskDependency(taskList, selectStr.orElse(""),
         filter.orElse(""), principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/run-tasks")
   public void runTasks(@RequestBody TaskListModel list, Principal principal) {
      for(String taskName : list.taskNames()) {
         try {
            this.scheduleService.runScheduledTask(taskName, principal);
         }
         catch(Exception e) {
            throw new MessageException(e.getMessage());
         }
      }
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/stop-tasks")
   public void stopTasks(@RequestBody TaskListModel list, Principal principal) {
      for(String taskName : list.taskNames()) {
         try {
            this.scheduleService.stopScheduledTask(taskName, principal);
         }
         catch(Exception e) {
            throw new MessageException(e.getMessage());
         }
      }
   }

   @GetMapping("/api/em/schedule/users-model")
   public UsersModel getUsersModel(@PermissionUser Principal principal) throws Exception
   {
      return scheduleService.getUsersModel(principal, true);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/distribution",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/distribution/chart")
   public DistributionChart getWeekDistributionChart(
      @RequestParam(value = "width", required = false, defaultValue = "400") int width,
      @RequestParam(value = "height", required = false, defaultValue = "400") int height,
      @PermissionUser Principal principal) throws Exception
   {
      DistributionModel model = getWeekDistribution(principal);
      Catalog catalog = Catalog.getCatalog(principal);
      return getDistributionChart("Day", model, catalog, width, height, -1);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/distribution",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/distribution/chart/{weekday}")
   public DistributionChart getDayDistributionChart(
      @RequestParam(value = "width", required = false, defaultValue = "400") int width,
      @RequestParam(value = "height", required = false, defaultValue = "400") int height,
      @PathVariable("weekday") int weekday, @PermissionUser Principal principal) throws Exception
   {
      DistributionModel model = getDayDistribution(weekday, principal);
      Catalog catalog = Catalog.getCatalog(principal);
      return getDistributionChart("Hour", model, catalog, width, height, -1);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/distribution",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/distribution/chart/{weekday}/{hour}")
   public DistributionChart getHourDistributionChart(
      @RequestParam(value = "width", required = false, defaultValue = "400") int width,
      @RequestParam(value = "height", required = false, defaultValue = "400") int height,
      @RequestParam(value = "highlight", required = false, defaultValue = "-1") int highlight,
      @PathVariable("weekday") int weekday, @PathVariable("hour") int hour,
      @PermissionUser Principal principal) throws Exception
   {
      DistributionModel model = getHourDistribution(weekday, hour, principal);
      Catalog catalog = Catalog.getCatalog(principal);
      return getDistributionChart("Minute", model, catalog, width, height, highlight);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/distribution",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/distribution/data")
   public DistributionModel getWeekDistribution(@PermissionUser Principal principal)
      throws Exception
   {
      return taskService.getWeekDistribution(principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/distribution",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/distribution/data/{weekday}")
   public DistributionModel getDayDistribution(@PathVariable("weekday") int weekday,
                                               @PermissionUser Principal principal) throws Exception
   {
      return taskService.getDayDistribution(weekday, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/distribution",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/distribution/data/{weekday}/{hour}")
   public DistributionModel getHourDistribution(@PathVariable("weekday") int weekday,
                                                @PathVariable("hour") int hour,
                                                @PermissionUser Principal principal)
      throws Exception
   {
      return taskService.getHourDistribution(weekday, hour, principal);
   }

   @PostMapping("/api/em/schedule/distribution/redistribute")
   public ScheduleTaskList redistributeTasks(@RequestBody RedistributeTasksRequest request,
                                             Principal principal) throws Exception
   {
      LocalTime startTime = LocalTime.of(request.startHour(), request.startMinute());
      LocalTime endTime = LocalTime.of(request.endHour(), request.endMinute());
      return taskService.redistributeTasks(
         startTime, endTime, request.maxConcurrency(), request.tasks(), principal);
   }

   private DistributionChart getDistributionChart(String type, DistributionModel model,
                                                  Catalog catalog, int width, int height,
                                                  int highlight)
      throws IOException
   {
      int max = 0;
      Object[][] data = new Object[model.data().size() * 2 + 1][];
      data[0] = new Object[] { type, "Index", catalog.getString("Type"), "Tasks" };

      for(int i = 1, j = model.data().size() - 1; i < data.length; i += 2, j--) {
         DistributionData point = model.data().get(j);
         data[i] = new Object[] {
            point.label(), point.index(), catalog.getString("Hard Count"), point.hardCount()
         };
         data[i + 1] = new Object[] {
            point.label(), point.index(), catalog.getString("Soft Count"), point.softCount()
         };
         max = Math.max(max, point.hardCount() + point.softCount());
      }

      boolean isDarkEM = CustomThemesManager.getManager().isEMDarkTheme();
      Color fgColor = isDarkEM ? Color.lightGray : GDefaults.DEFAULT_TEXT_COLOR;
      Color bgColor = isDarkEM ? new Color(0x424242) : Color.WHITE;
      DataSet dataSet = new DefaultDataSet(data);

      TitleDescriptor xTitle = new TitleDescriptor();
      xTitle.setTitleValue(catalog.getString("Tasks"));
      xTitle.getTextFormat().setColor(fgColor);

      TitleDescriptor yTitle = new TitleDescriptor();
      yTitle.setTitleValue(catalog.getString(type));
      yTitle.getTextFormat().setRotation(90);
      yTitle.getTextFormat().setColor(fgColor);

      ChartDescriptor chart = new ChartDescriptor();
      chart.setPreferredSize(new Dimension(width, height));
      chart.getTitlesDescriptor().setXTitleDescriptor(xTitle);
      chart.getTitlesDescriptor().setYTitleDescriptor(yTitle);

      if(isDarkEM) {
         chart.getPlotDescriptor().setYGridColor(bgColor.darker());
         chart.getLegendsDescriptor().getTitleTextFormat().setColor(fgColor);
         chart.getLegendsDescriptor().getTitleTextFormat().setBackground(bgColor);
         chart.getLegendsDescriptor().getColorLegendDescriptor().setColor(fgColor);
         chart.getLegendsDescriptor().getColorLegendDescriptor().getContentTextFormat().setColor(fgColor);
         chart.getLegendsDescriptor().getColorLegendDescriptor().getContentTextFormat().setBackground(bgColor);
         chart.getLegendsDescriptor().setBorderColor(bgColor.darker());
      }

      VSChartInfo info = new DefaultVSChartInfo();
      info.setSeparatedGraph(false);
      info.setMultiStyles(false);
      info.setRTChartType(GraphTypes.CHART_BAR_STACK);

      BaseField field = new BaseField((String) data[0][0]);
      VSChartDimensionRef dimension = new VSChartDimensionRef(field);
      info.addYField(dimension);

      field = new BaseField((String) data[0][2]);
      int sourceType = XSourceInfo.NONE;

      if(field != null) {
         sourceType = field.getSourceType();
      }
      dimension = new VSChartDimensionRef(field);
      VSAestheticRef aesthetic = new VSAestheticRef();
      aesthetic.setDataRef(dimension);

      if(highlight < 0) {
         aesthetic.setVisualFrame(new CategoricalColorFrame());
      }
      else {
         VisualFrame frame = new CategoricalColorFrame() {
            @Override
            public Color getColor(DataSet data, String col, int row) {
               if(highlight == (Integer) data.getData(1, row)) {
                  return super.getColor(2);
               }

               return super.getColor(data, col, row);
            }
         };
         CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
         wrapper.setVisualFrame(frame);
         aesthetic.setVisualFrameWrapper(wrapper);
      }

      info.setColorField(aesthetic);

      field = new BaseField((String) data[0][3]);
      VSChartAggregateRef aggregate = new VSChartAggregateRef();
      aggregate.setDataRef(field);
      info.addXField(aggregate);

      if(max < 10) {
         info.getAxisDescriptor().setMaximum(10);
      }

      StaticSizeFrameWrapper wrapper = (StaticSizeFrameWrapper) info.getSizeFrameWrapper();
      wrapper.setSize(15);
      wrapper.setChanged(true);

      info.updateChartType(false);

      GraphGenerator generator = GraphGenerator.getGenerator(
         info, chart, null, null, dataSet, new VariableTable(), sourceType, null);

      EGraph egraph = generator.createEGraph();
      egraph.getCoordinate().getMaxWidth();
      RectCoord coord = (RectCoord) egraph.getCoordinate();
      Scale[] scales = coord.getScales();

      for(Scale scale : scales) {
         AxisSpec spec = scale.getAxisSpec();
         spec.setTruncate(true);
         spec.getTextSpec().setColor(fgColor);

         if(isDarkEM) {
            spec.setLineColor(bgColor.darker());
         }
      }

      dataSet = generator.getData();
      VGraph vgraph = Plotter.getPlotter((EGraph) egraph.clone()).plotAndLayout(dataSet, 0, 0, width, height);

      SVGSupport svgSupport = SVGSupport.getInstance();
      Graphics2D image = svgSupport.createSVGGraphics();
      svgSupport.setCanvasSize(image, new Dimension(width, height));
      image.setColor(bgColor);
      image.fillRect(0, 0, width, height);
      vgraph.paintGraph(image, true);
      image.dispose();

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      try(OutputStreamWriter writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
         svgSupport.writeSVG(image, writer, true);
      }

      String imageData =
         "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(buffer.toByteArray());

      ChartArea chartArea = new ChartArea(vgraph, egraph, dataSet, null, info);
      PlotArea plotArea = chartArea.getPlotArea();

      DistributionChart.Builder builder = DistributionChart.builder()
         .width(width)
         .height(height)
         .image(imageData);
      addAreas(builder, plotArea, dataSet);
      return builder.build();
   }

   private void addAreas(DistributionChart.Builder builder, PlotArea plotArea, DataSet dataSet) {
      Rectangle plotBounds = plotArea.getRegion().getBounds();
      Arrays.stream(plotArea.getAllAreas())
         .filter(a -> a instanceof VisualObjectArea)
         .map(a -> (VisualObjectArea) a)
         .forEach(a -> this.addArea(builder, a, plotBounds, dataSet));
   }

   private void addArea(DistributionChart.Builder builder, VisualObjectArea area,
                        Rectangle plotBounds, DataSet dataSet)
   {
      Arrays.stream(area.getRegions())
         .map(r -> DistributionChartValue.builder()
            .from(r, plotBounds)
            .index((Integer) dataSet.getData(1, area.getRowIndex()))
            .build())
         .forEach(builder::addValues);
   }

   private final ScheduleService scheduleService;
   private final ScheduleManager scheduleManager;
   private final ScheduleTaskService taskService;
   private final ScheduleTaskFolderService taskFolderService;
}
