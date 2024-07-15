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
package inetsoft.web.admin.server;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.web.HttpServiceRequest;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.web.admin.cache.CacheService;
import inetsoft.web.admin.monitoring.*;
import inetsoft.web.admin.query.QueryHistory;
import inetsoft.web.admin.query.QueryService;
import inetsoft.web.admin.schedule.ScheduleMetrics;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import inetsoft.web.admin.viewsheet.ViewsheetHistory;
import inetsoft.web.admin.viewsheet.ViewsheetService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.reportviewer.service.HttpServletRequestWrapper;
import inetsoft.web.security.DeniedMultiTenancyOrgUser;

import java.awt.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.*;
import java.util.zip.GZIPOutputStream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

@RestController
public class ServerMonitoringController {
   @Autowired
   public ServerMonitoringController(ServerService serverService,
                                     MonitoringDataService monitoringDataService,
                                     CacheService cacheService,
                                     ViewsheetService viewsheetService,
                                     QueryService queryService,
                                     SchedulerMonitoringService schedulerMonitoringService,
                                     ServerClusterClient client,
                                     UsageHistoryService usageHistoryService)
   {
      this.serverService = serverService;
      this.monitoringDataService = monitoringDataService;
      this.cacheService = cacheService;
      this.viewsheetService = viewsheetService;
      this.queryService = queryService;
      this.schedulerMonitoringService = schedulerMonitoringService;
      this.client = client;
      this.usageHistoryService = usageHistoryService;
   }

   @SubscribeMapping("/monitoring/server/charts")
   public ServerModel subscribeServerCharts(StompHeaderAccessor stompHeaderAccessor) {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         Map<String, String> serverUpTimeMap = new HashMap<>();
         Map<String, String> serverDateTimeMap = new HashMap<>();
         Map<String, String> schedulerUpTimeMap = new HashMap<>();
         final SimpleDateFormat format = new SimpleDateFormat(
            SreeEnv.getProperty("format.date.time"));
         final long timestamp = System.currentTimeMillis();
         Set<String> clusterNodes = getServerClusterNodes();
         boolean clusterEnabled = "server_cluster".equals(SreeEnv.getProperty("server.type"));

         if(clusterEnabled) {
            for(String node : clusterNodes) {
               final ServerMetrics serverMetrics = client.getMetrics(StatusMetricsType.SERVER_METRICS, node);
               ScheduleMetrics schedule = client.getMetrics(StatusMetricsType.SCHEDULE_METRICS, node);

               if(serverMetrics != null) {
                  serverUpTimeMap.put(node,
                     formatAge(serverMetrics.upTime()));

                  final Date date = Date.from(
                     Instant.ofEpochMilli(timestamp).atOffset(serverMetrics.timeZone()).toInstant());
                  serverDateTimeMap.put(node, format.format(date));
               }

               if(schedule != null) {
                  schedulerUpTimeMap.put(node, formatAge(schedule.getUpTime()));
               }
            }
         }
         else {
            serverUpTimeMap.put("local", formatAge(serverService.getUpTime()));
            final Date date = Date.from(
               Instant.ofEpochMilli(timestamp).atOffset(OffsetDateTime.now().getOffset()).toInstant());
            serverDateTimeMap.put("local", format.format(date));
            schedulerUpTimeMap.put("local", formatAge(schedulerMonitoringService.getUpTime()));
         }

         return ServerModel.builder()
            .serverUpTimeMap(serverUpTimeMap)
            .serverDateTimeMap(serverDateTimeMap)
            .schedulerUpTimeMap(schedulerUpTimeMap)
            .timestamp(timestamp)
            .build();
      });
   }

   /**
    * @param id        id of the image
    * @param response  The response which will be returned to the browser, into
    *                  which the requested image data is to be returned.
    */
   @DeniedMultiTenancyOrgUser
   @GetMapping("/em/getSummaryImage/{id}/{width}/{height}")
   public void processSummaryImage(@PathVariable("id") String id,
                                   @PathVariable("width") double width,
                                   @PathVariable("height") double height,
                                   @RequestParam(value = "clusterNode", required = false) String clusterNode,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws Exception
   {
      Graphics2D image = generateImage(id, clusterNode, (int) width, (int) height);

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

   @GetMapping("/em/monitoring/scheduler/get-thread-dump")
   public void getSchedulerThreadDump(@RequestParam(value = "clusterNode", required = false) String clusterNode,
                                      HttpServletResponse response)
   {
      writeSchedulerThreadDump(clusterNode, response);
   }

   private boolean writeSchedulerThreadDump(String clusterNode, HttpServletResponse response) {
      final Cluster cluster = Cluster.getInstance();

      for(String node : cluster.getClusterNodes()) {
         boolean isScheduleNode = Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler"));
         String nodeIp = node != null && node.indexOf(":") != -1 ? node.substring(0, node.indexOf(":")) : node;

         if((Tool.isEmptyString(clusterNode) || clusterNode.equals(nodeIp)) &&  isScheduleNode) {
            try {
               final String threadDump = serverService.getThreadDump(node);
               writeThreadDumpResponse(node, response, threadDump);
               return true;
            }
            catch(Exception e) {
               LOG.error("Failed to generate scheduler thread dump", e);
            }
         }
      }

      return false;
   }

   @GetMapping("/em/monitoring/server/get-thread-dump")
   public void getThreadDump(@RequestParam(value = "clusterNode", required = false) String clusterNode,
                             HttpServletResponse response) throws Exception
   {
      String node = clusterNode == null ? Cluster.getInstance().getLocalMember() :
         SUtil.computeServerClusterNode(clusterNode);

      // try the scheduler if there is no such server
      if(node == null && clusterNode != null) {
         if(writeSchedulerThreadDump(clusterNode, response)) {
            return;
         }
      }

      String threadDump = serverService.getThreadDump(node);
      writeThreadDumpResponse(node, response, threadDump);
   }

   public void writeThreadDumpResponse(String clusterNode,
                                       HttpServletResponse response,
                                       String threadDump)
   {
      try {
         String fileName = getClusterFileName(clusterNode, "ThreadDump", ".txt");
         String header = "attachment; filename=\"" + fileName + "\"";

         if(SUtil.isHttpHeadersValid(header)) {
            response.setHeader("Content-Disposition", StringUtils.normalizeSpace(header));
         }

         response.setContentType("text/plain; charset=\"UTF-8\"");

         try(PrintWriter writer = response.getWriter()) {
            writer.print(threadDump);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get thread dump", e);
      }
   }

   @GetMapping("/em/monitoring/scheduler/get-heap-dump")
   public void getSchedulerHeapDump(@RequestParam(value = "clusterNode", required = false) String clusterNode,
                                    HttpServletResponse response)
      throws Exception
   {
      writeSchedulerHeapDump(clusterNode, response);
   }

   private boolean writeSchedulerHeapDump(String clusterNode, HttpServletResponse response)
      throws Exception
   {
      final Cluster cluster = Cluster.getInstance();

      for(String node : cluster.getClusterNodes()) {
         boolean isScheduleNode = Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler"));
         String nodeIp = node != null && node.contains(":") ? node.substring(0, node.indexOf(":")) : node;

         if((Tool.isEmptyString(clusterNode) || clusterNode.equals(nodeIp)) &&  isScheduleNode) {
            writeHeapDump(node, response);
            return true;
         }
      }

      return false;
   }

   @GetMapping("/em/monitoring/server/get-heap-dump")
   public void getHeapDump(@RequestParam(value = "clusterNode", required = false) String clusterNode,
                           HttpServletResponse response) throws Exception
   {
      String node = clusterNode == null ? Cluster.getInstance().getLocalMember() :
         SUtil.computeServerClusterNode(clusterNode);

      // try the scheduler if there is no such server
      if(node == null && clusterNode != null) {
         if(writeSchedulerHeapDump(clusterNode, response)) {
            return;
         }
      }

      writeHeapDump(node, response);
   }

   private void writeHeapDump(String clusterNode, HttpServletResponse response) throws Exception {
      String heapId = null;

      try {
         heapId = writeHeapDumpResponse(clusterNode, response);
      }
      catch(IOException ex) {
         LOG.debug("Failed to write response: " + ex, ex);
      }
      catch(Exception ex) {
         LOG.error("Failed to get heap dump", ex);
      }
      finally {
         if(heapId != null) {
            serverService.disposeHeapDump(heapId, clusterNode);
         }
      }
   }

   private String writeHeapDumpResponse(String clusterNode, HttpServletResponse response) throws Exception {
      String heapId;
      String fileName = getClusterFileName(clusterNode, "HeapDump", ".hprof.gz");
      String header = "attachment; filename=\"" + fileName + "\"";

      if(SUtil.isHttpHeadersValid(header)) {
         response.setHeader("Content-Disposition", StringUtils.normalizeSpace(header));
      }

      response.setContentType("application/octet-stream");

      heapId = serverService.createHeapDump(clusterNode);

      while(!serverService.isHeapDumpComplete(heapId, clusterNode)) {
         Thread.sleep(1000L);
      }

      int length = serverService.getHeapDumpLength(heapId, clusterNode);

      if(length > 0) {
         int bufferSize = 1024 * 1024 * 1024;
         int offset = 0;

         try(OutputStream output = response.getOutputStream()) {
            while(offset < length) {
               int toRead = Math.min(bufferSize, length - offset);
               byte[] buffer =
                  serverService.getHeapDumpContent(heapId, offset, toRead, clusterNode);
               output.write(buffer);
               offset += buffer.length;
            }
         }
      }

      return heapId;
   }

   @GetMapping("/em/monitoring/server/get-usage-history")
   public void getUsageHistory(
      @RequestParam(value = "clusterNode", required = false) String clusterNode,
      Principal principal, HttpServletResponse response)
   {
      try {
         response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"CpuMemoryHistory.csv\"");
         response.setContentType("text/csv; charset=\"UTF-8\"");

         try(PrintWriter writer = response.getWriter()) {
            writer.println("Time,Host,CPU Usage (%),Memory Usage (B),GC Count,GC Time (ms)," +
                              "Executing Viewsheets,Executing Queries");

            for(ServerUsage usage : usageHistoryService.getUsageHistory(clusterNode, principal)) {
               Instant instant = Instant.ofEpochMilli(usage.timestamp());
               OffsetDateTime offset = instant
                  .atOffset(ZoneId.systemDefault().getRules().getOffset(instant))
                  .truncatedTo(ChronoUnit.SECONDS);
               String time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offset);

               writer.format(
                  "%s,%s,%.2f,%d,%d,%d,%d,%d%n",
                  time,
                  usage.host(),
                  usage.cpuUsage(),
                  usage.memoryUsage(),
                  usage.gcCount(),
                  usage.gcTime(),
                  usage.executingViewsheets(),
                  usage.executingQueries());
            }
         }
      }
      catch(IOException exc) {
         LOG.error("Failed to write CPU and memory history file", exc);
      }
   }

   @GetMapping("/em/monitoring/server/summary")
   public ServerSummaryModel getServerSummaryModel(
      @RequestParam(value = "clusterNode", required = false) String clusterNode,
      @HttpServletRequestWrapper HttpServiceRequest srequest)
   {
      return ServerSummaryModel.builder()
         .versionNumber(Tool.getReportVersion())
         .buildNumber(Tool.getBuildNumber())
         .currentNode(getCurrentNode())
         .jvmModel(getJvmModel())
         .legends(getMonitoringChartLegends(clusterNode))
         .reverseProxyModel(getReverseProxyModel(srequest))
         .build();
   }

   private String getCurrentNode() {
      if(SUtil.isCluster()) {
         String node = Cluster.getInstance().getLocalMember();
         int index = node.indexOf(':');

         if(index >= 0) {
            node = node.substring(0, index);
         }

         return node;
      }

      return null;
   }

   /**
    * Get jvm info model.
    */
   private JvmModel getJvmModel() {
      return JvmModel.builder()
                     .version(System.getProperty("java.version"))
                     .javaHome(System.getProperty("java.home"))
                     .classPath(parsePathSeparator(System.getProperty("java.class.path")))
                     .cores(Tool.getAvailableCPUCores())
                     .build();
   }

   private ReverseProxyModel getReverseProxyModel(HttpServiceRequest srequest) {
      Set<String> reverseProxyHeaders =
         Arrays.stream(new String[]{ "X-Forwarded-For", "X-Real-IP", "X-Forwarded-Host",
                                     "X-Forwarded-Proto", "X-Inetsoft-Remote-Uri" })
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

      boolean active = false;
      Map<String, String> requestHeadersMap = new LinkedHashMap<>();
      Enumeration<String> headerNames = srequest.getHeaderNames();

      while(headerNames.hasMoreElements()) {
         String header = headerNames.nextElement();

         if(header != null && reverseProxyHeaders.contains(header.toLowerCase())) {
            active = true;
            requestHeadersMap.put(header, srequest.getHeader(header));
         }
      }

      return ReverseProxyModel.builder()
         .active(active)
         .requestHeaders(requestHeadersMap)
         .build();
   }

   /**
    * Parse the path list using OS-dependent file path seperator.
    * @param s The string you want to parse.
    * @return String you want to get.
    */
   private String parsePathSeparator(String s) {
      return s.replaceAll("[\\r\\n]", "").replace(File.pathSeparator, "<br>");
   }

   @GetMapping("/em/monitoring/server/get-monitoring-summary-chart-legends")
   public SummaryChartLegends getMonitoringChartLegends(
      @RequestParam(value = "clusterNode", required = false) String clusterNode)
   {
      List<QueryHistory> queryExe = queryService.getHistory(clusterNode);
      SummaryChartLegends.Builder legends = SummaryChartLegends.builder();
      int counter = 0;

      List<ViewsheetHistory> vsExe = viewsheetService.getHistory(clusterNode);

      if(!vsExe.isEmpty()) {
         legends.addExecution(SummaryChartLegend.builder()
                                 .text("Viewsheets")
                                 .color(COLOR_PALETTE[counter++])
                                 .link("viewsheets")
                                 .build());
      }

      if(!queryExe.isEmpty()) {
         legends.addExecution(SummaryChartLegend.builder()
                                 .text("Queries")
                                 .color(COLOR_PALETTE[counter])
                                 .link("queries")
                                 .build());
      }

      legends.addSwapping(SummaryChartLegend.builder()
                             .text("Read")
                             .color(COLOR_PALETTE[0])
                             .link("cache")
                             .build());
      legends.addSwapping(SummaryChartLegend.builder()
                             .text("Written")
                             .color(COLOR_PALETTE[1])
                             .link("cache")
                             .build());

      legends.addDiskCache(SummaryChartLegend.builder()
                              .text("Data (objects)")
                              .color(COLOR_PALETTE[1])
                              .link("cache")
                              .build());

      legends.addMemCache(SummaryChartLegend.builder()
                             .text("Data (objects)")
                             .color(COLOR_PALETTE[1])
                             .link("cache")
                             .build());

      boolean clusterEnabled = "server_cluster".equals(SreeEnv.getProperty("server.type"));
      Set<String> clusterNodes = getServerClusterNodes();
      counter = 0;

      if(!clusterEnabled || clusterNodes.isEmpty()) {
         clusterNodes = Collections.singleton(Cluster.getInstance().getLocalMember());
      }

      for(String node: clusterNodes) {
         String host = node;
         int index = node.indexOf(':');

         if(index >= 0) {
            host = host.substring(0, index);
         }

         legends.addMemUsage(SummaryChartLegend.builder()
                                 .text(host + " (Bytes)")
                                 .color(COLOR_PALETTE[counter])
                                 .link(null)
                                 .build());
         legends.addCpuUsage(SummaryChartLegend.builder()
                                 .text(host)
                                 .color(COLOR_PALETTE[counter])
                                 .link(null)
                                 .build());
         legends.addGcCount(SummaryChartLegend.builder()
                               .text(host)
                               .color(COLOR_PALETTE[counter])
                               .link(null)
                               .build());
         legends.addGcTime(SummaryChartLegend.builder()
                               .text(host + " (ms)")
                               .color(COLOR_PALETTE[counter++])
                               .link(null)
                               .build());
      }

      String[] scheduleServers = ScheduleClient.getScheduleClient().getScheduleServers();

      if(scheduleServers == null) {
         scheduleServers = new String[0];
      }

      for(String scheduleServer : scheduleServers) {
         if(ScheduleClient.getScheduleClient().isReady(scheduleServer)) {
            legends.addMemUsage(SummaryChartLegend.builder()
               .text("Scheduler(" + scheduleServer + ")/(Bytes)")
               .color(COLOR_PALETTE[counter])
               .link(null)
               .build());
            legends.addCpuUsage(SummaryChartLegend.builder()
               .text("Scheduler(" + scheduleServer + ")")
               .color(COLOR_PALETTE[counter])
               .link(null)
               .build());
            legends.addGcCount(SummaryChartLegend.builder()
               .text("Scheduler(" + scheduleServer + ")")
               .color(COLOR_PALETTE[counter])
               .link(null)
               .build());
            legends.addGcTime(SummaryChartLegend.builder()
               .text("Scheduler(" + scheduleServer + ")/(ms)")
               .color(COLOR_PALETTE[counter++])
               .link(null)
               .build());
         }
      }

      return legends.build();
   }

   @GetMapping("/em/monitoring/server/threads/{id}")
   public ThreadStackTrace getThreadInfo(@PathVariable("id") long id) {
      String stackTrace = serverService.getStackTrace(id);
      return ThreadStackTrace.builder().stackTrace(stackTrace).build();
   }

   /**
    * Generate the an image.
    */
   private Graphics2D generateImage(String imageId, String clusterNode, int width, int height) {
      Object[][] data = null;
      String format = null;
      String title = null;
      Catalog catalog = Catalog.getCatalog();
      boolean clusterEnabled = "server_cluster".equals(SreeEnv.getProperty("server.type"));
      Set<String> clusterNodes = getServerClusterNodes();
      String[] scheduleServers = ScheduleClient.getScheduleClient().getScheduleServers();

      if(scheduleServers == null) {
         scheduleServers = new String[0];
      }

      MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
      long max = 0L;

      if("memUsage".equals(imageId)) {
         max = memoryBean.getHeapMemoryUsage().getMax();

         if(clusterEnabled) {
            for(String node : clusterNodes) {
               Object[][] grid = createHistoryGrid(
                  node, serverService.getMemoryHistory(node),
                  h -> new Object[] { new Time(h.timestamp()), h.usedMemory() });

               if(data == null) {
                  data = grid;
               }
               else {
                  data = MonitorUtil.mergeGridData(data, grid);
               }

               max = Math.max(max, serverService.getMaxHeapSize(node));
            }
         }
         else {
            data = createHistoryGrid(
               null, serverService.getMemoryHistory(null),
               h -> new Object[] { new Time(h.timestamp()), h.usedMemory() });
         }

         for(String scheduleServer : scheduleServers) {
            if(ScheduleClient.getScheduleClient().isReady(scheduleServer)) {
               Object[][] grid = createHistoryGrid(
                  "Scheduler(" + scheduleServer + ")",
                  schedulerMonitoringService.getMemoryHistory(scheduleServer),
                  h -> new Object[]{ new Time(h.timestamp()), h.usedMemory() });
               data = MonitorUtil.mergeGridData(data, grid);

               try {
                  max = Math.max(max, schedulerMonitoringService.getMaxHeapSize(scheduleServer));
               }
               catch(RemoteException e) {
                  LOG.warn("Failed to get max heap size: " + e, e);
               }
            }
         }

         format = "million";
         title = catalog.getString("Memory Usage");
      }
      else if("cpuUsage".equals(imageId)) {
         if(clusterEnabled) {
            for(String node : clusterNodes) {
               Object[][] grid = createHistoryGrid(
                  node, serverService.getCpuHistory(node),
                  h -> new Object[] { new Time(h.timestamp()), h.cpuPercent() });

               if(data == null) {
                  data = grid;
               }
               else {
                  data = MonitorUtil.mergeGridData(data, grid);
               }
            }
         }
         else {
            data = createHistoryGrid(
               null, serverService.getCpuHistory(null),
               h -> new Object[] { new Time(h.timestamp()), h.cpuPercent() });
         }

         for(String scheduleServer : scheduleServers) {
            if(ScheduleClient.getScheduleClient().isReady(scheduleServer)) {
               Object[][] grid = createHistoryGrid(
                  "Scheduler(" + scheduleServer + ")",
                  schedulerMonitoringService.getCpuHistory(scheduleServer),
                  h -> new Object[]{ new Time(h.timestamp()), h.cpuPercent() });
               data = MonitorUtil.mergeGridData(data, grid);
            }
         }

         format = "percent";
         title = catalog.getString("CPU Usage");
         max = 1L;
      }
      else if("gcCount".equals(imageId)) {
         if(clusterEnabled) {
            for(String node : clusterNodes) {
               Object[][] grid = createHistoryGrid(
                  node, serverService.getGcHistory(node),
                  h -> new Object[] { new Time(h.timestamp()), h.collectionCount() });

               if(data == null) {
                  data = grid;
               }
               else {
                  data = MonitorUtil.mergeGridData(data, grid);
               }
            }
         }
         else {
            data = createHistoryGrid(
               null, serverService.getGcHistory(null),
               h -> new Object[] { new Time(h.timestamp()), h.collectionCount() });
         }

         for(String scheduleServer : scheduleServers) {
            if(ScheduleClient.getScheduleClient().isReady(scheduleServer)) {
               Object[][] grid = createHistoryGrid(
                  "Scheduler(" + scheduleServer + ")",
                  schedulerMonitoringService.getGcHistory(scheduleServer),
                  h -> new Object[]{new Time(h.timestamp()), h.collectionCount()});
               data = MonitorUtil.mergeGridData(data, grid);
            }
         }

         title = catalog.getString("GC Count");

         for(int i = 1; i < data[0].length; i++) {
            final int index = i;
            long max0 = Arrays.stream(data)
               .skip(1L)
               .mapToLong(r -> safeMapToLong(r[index]))
               .max()
               .orElse(1L);

            max = Math.max(max, max0);
         }
      }
      else if("gcTime".equals(imageId)) {
         if(clusterEnabled) {
            for(String node : clusterNodes) {
               Object[][] grid = createHistoryGrid(
                  node, serverService.getGcHistory(node),
                  h -> new Object[] { new Time(h.timestamp()), h.collectionTime() });

               if(data == null) {
                  data = grid;
               }
               else {
                  data = MonitorUtil.mergeGridData(data, grid);
               }
            }
         }
         else {
            data = createHistoryGrid(
               null, serverService.getGcHistory(null),
               h -> new Object[] { new Time(h.timestamp()), h.collectionTime() });
         }

         for(String scheduleServer : scheduleServers) {
            if(ScheduleClient.getScheduleClient().isReady(scheduleServer)) {
               Object[][] grid = createHistoryGrid(
                  "Scheduler(" + scheduleServer + ")",
                  schedulerMonitoringService.getGcHistory(scheduleServer),
                  h -> new Object[]{new Time(h.timestamp()), h.collectionTime()});
               data = MonitorUtil.mergeGridData(data, grid);
            }
         }

         title = catalog.getString("GC Time");

         for(int i = 1; i < data[0].length; i++) {
            final int index = i;
            long max0 = Arrays.stream(data)
               .skip(1L)
               .mapToLong(r -> safeMapToLong(r[index]))
               .max()
               .orElse(1L);

            max = Math.max(max, max0);
         }
      }
      else if("memCache".equals(imageId)) {
         data = cacheService.getCacheHistory(clusterNode).stream()
            .map(s -> new Object[] { new Time(s.time()).toString(),
                                     s.reportMemoryCount(),
                                     s.dataMemoryCount() })
            .toArray(Object[][]::new);
         data = addTitleToChartData("cache", data);
         title = catalog.getString("Memory Cache Size");
         max = Arrays.stream(data)
            .skip(1L)
            .flatMapToInt(r -> IntStream.of(safeMapToInt(r[1]), safeMapToInt(r[2])))
            .max()
            .orElse(1);
      }
      else if("diskCache".equals(imageId)) {
         data = cacheService.getCacheHistory(clusterNode).stream()
            .map(s -> new Object[] { new Time(s.time()).toString(),
                                     s.reportDiskCount(),
                                     s.dataDiskCount() })
            .toArray(Object[][]::new);
         data = addTitleToChartData("cache", data);
         title = catalog.getString("Disk Cache Size");
         max = Arrays.stream(data)
            .skip(1L)
            .flatMapToInt(r -> IntStream.of(safeMapToInt(r[1]), safeMapToInt(r[2])))
            .max()
            .orElse(1);
      }
      else if("swapping".equals(imageId)) {
         data = cacheService.getCacheHistory(clusterNode).stream()
            .map(s -> new Object[] {
               new Time(s.time()).toString(),
               s.reportBytesRead() + s.dataBytesRead(),
               s.reportBytesWritten(),
               s.dataBytesWritten()
            })
            .toArray(Object[][]::new);
         format = "million";
         data = addTitleToChartData("swapping", data);
         title = catalog.getString("Swapping Size");
         max = Arrays.stream(data)
            .skip(1L)
            .flatMapToLong(r -> LongStream.of(safeMapToLong(r[1]), safeMapToLong(r[2]), safeMapToLong(r[3])))
            .max()
            .orElse(1L);
      }
      else if("execution".equals(imageId)) {
         return generateExecutionImage(clusterNode, width, height);
      }

      if(isShowImage(data)) {
         return MonitorUtil.createMonitorImage(data, width, height, GraphTypes.CHART_LINE,
                                               format, title, max);
      }

      return null;
   }

   private <T> Object[][] createHistoryGrid(String node, List<T> history,
                                            Function<T, Object[]> mapper)
   {
      Object[][] grid = new Object[history.size() + 1][];
      grid[0] = new Object[] {
         "Time", node == null ? Cluster.getInstance().getLocalMember() : node
      };

      for(int i = 0; i < history.size(); i++) {
         grid[i + 1] = mapper.apply(history.get(i));
      }

      return grid;
   }

   private Object[][] addTitleToChartData(String chartId, Object[][] dataArr) {
      Object[][] result = new Object[dataArr.length + 1][];
      Catalog catalog = Catalog.getCatalog();

      if("cache".equals(chartId)) {
         String time = catalog.getString("Time");
         String data = catalog.getString("Data");
         String pages = catalog.getString("Reports");
         result[0] = new String[] { time, pages, data };
      }
      else if("swapping".equals(chartId)){
         String time = catalog.getString("Time");
         String read = catalog.getString("Read");
         String written = catalog.getString("Written");
         result[0] =  new String[] { time, read, written };
      }

      System.arraycopy(dataArr, 0, result, 1, dataArr.length);

      return result;
   }

   /**
    * If the source data does not contains any useful datum, image will not be
    * shown.
    */
   private boolean isShowImage(Object[][] data) {
      return data != null && data.length >= 2 && data[0].length >= 2;
   }

   /**
    * Generate execution images.
    */
   private Graphics2D generateExecutionImage(String clusterNode, int width, int height) {
      Catalog catalog = Catalog.getCatalog();

      List<QueryHistory> queryExe = queryService.getHistory(clusterNode);
      List<Time> timeline = new ArrayList<>();
      List<Object> headers = new ArrayList<>();
      List<Map<Time, Integer>> maps = new ArrayList<>();

      headers.add("Time");

      List<ViewsheetHistory> vsExe = viewsheetService.getHistory(clusterNode);

      if(!vsExe.isEmpty()) {
         Map<Time, Integer> map = new HashMap<>();
         maps.add(map);
         headers.add("Viewsheets");

         for(ViewsheetHistory vs : vsExe) {
            Time time = new Time(vs.timestamp());
            timeline.add(time);
            map.put(time, vs.executingViewsheets());
         }
      }

      if(!queryExe.isEmpty()) {
         Map<Time, Integer> map = new HashMap<>();
         maps.add(map);
         headers.add("Queries");

         for(QueryHistory query : queryExe) {
            Time time = new Time(query.timestamp());
            timeline.add(time);
            map.put(time, query.queryCount());
         }
      }

      if(timeline.isEmpty()) {
         return null;
      }

      timeline.sort(Comparator.naturalOrder());

      Object[][] result = new Object[timeline.size() + 1][maps.size() + 1];
      result[0] = headers.toArray(new Object[0]);

      for(int i = 0; i < timeline.size(); i++) {
         List<Object> row = new ArrayList<>();
         Time time = timeline.get(i);
         row.add(time);

         for(Map<Time, Integer> map : maps) {
            Object count = map.get(time);
            row.add(count);
         }

         result[i + 1] = row.toArray();
      }

      return MonitorUtil.createMonitorImage(result, width, height,
                                            GraphTypes.CHART_LINE, null,
                                            catalog.getString("Execution Count"), 0);
   }

   /**
    * Format the age.
    */
   private String formatAge(Long date) {
      long diff = date;
      long day = diff / (1000 * 60 * 60 * 24);
      long hour = (diff - day * 1000 * 60 * 60 * 24) / (1000 * 60 * 60);
      long min = (diff - day * 1000 * 60 * 60 * 24 - hour * 1000 * 60 * 60) / 60000;
      return day + "d " + hour + "h " + min + "m";
   }

   private static String getClusterFileName(String clusterNode, String prefix, String suffix) {
      String node = clusterNode;
      int index = node.indexOf(':');

      if(index >= 0) {
         node = node.substring(0, index);
      }

      String fileName = String.format("%s-%s%s", prefix, node.replace('.', '_'), suffix);

      if(!Tool.isFilePathValid(fileName)) {
         fileName = "invalid";
      }

      return fileName;
   }

   private Set<String> getServerClusterNodes() {
      Set<String> clusterNodes = new ServerClusterClient().getConfiguredServers();
      Set<String> result = new HashSet<>();

      for(String node : clusterNodes) {
         String host = node;
         int index = node.indexOf(':');

         if(index >= 0) {
            host = host.substring(0, index);
         }

         result.add(host);
      }

      return result;
   }

   private long safeMapToLong(Object n) {
      return n instanceof Number ? ((Number) n).longValue() : 0L;
   }

   private int safeMapToInt(Object n) {
      return n == null ? 0 : (Integer) n;
   }

   private final ServerService serverService;
   private final MonitoringDataService monitoringDataService;
   private final CacheService cacheService;
   private final ViewsheetService viewsheetService;
   private final QueryService queryService;
   private final SchedulerMonitoringService schedulerMonitoringService;
   private final ServerClusterClient client;
   private final UsageHistoryService usageHistoryService;
   private static final String[] COLOR_PALETTE = {
      "#5a9bd4", "#f15a60", "#7ac36a",
      "#737373", "#faa75b", "#9e67ab",
      "#ce7058", "#d77fb4", "#9368be",
      "#be90d4", "#95a5a6", "#dadfe1",
      "#19b5fe", "#c5eff7", "#869530",
      "#c8d96f", "#a88637", "#d2b267",
      "#019875", "#68c3a3",
      "#99CCFF", "#999933", "#CC9933",
      "#006666", "#993300", "#666666",
      "#663366", "#CCCCCC", "#669999",
      "#CCCC66", "#CC6600", "#9999FF",
      "#0066CC", "#FFCC00", "#009999",
      "#99CC33", "#FF9900", "#66CCCC",
      "#339966", "#CCCC33"};
   private static final Logger LOG = LoggerFactory.getLogger(ServerMonitoringController.class);
}
