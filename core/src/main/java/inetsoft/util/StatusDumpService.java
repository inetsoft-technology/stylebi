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

package inetsoft.util;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.ExternalStorageService;
import inetsoft.uql.util.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.query.QueryModel;
import inetsoft.web.admin.server.ServerServiceMessageListener;
import inetsoft.web.admin.user.SessionModel;
import inetsoft.web.admin.viewsheet.ViewsheetModel;
import inetsoft.web.admin.viewsheet.ViewsheetThreadModel;
import inetsoft.web.session.IgniteSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class StatusDumpService {
   public StatusDumpService() {
      mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
   }

   public static StatusDumpService getInstance() {
      return SingletonManager.getInstance(StatusDumpService.class);
   }

   public void dumpStatus() {
      Path file = FileSystemService.getInstance().getCacheTempFile("status-dump", ".zip").toPath();

      try {
         try(ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            writeDashboards(zip);
            writeQueries(zip);
            writeUsers(zip);
            writeThreads(zip);
            writeProperties(zip);
            writeLogs(zip);
            writeMetrics(zip);
         }
         catch(Exception e) {
            LOG.error("Failed to dump status", e);
            return;
         }

         try {
            String externalPath = getDumpName();
            ExternalStorageService.getInstance().write(externalPath, file);
            LOG.info("Wrote status dump to {}", externalPath);
         }
         catch(Exception e) {
            LOG.error("Failed to dump status", e);
         }
      }
      finally {
         Tool.deleteFile(file.toFile());
      }
   }

   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   private void writeDashboards(ZipOutputStream zip) {
      try {
         ZipEntry entry = new ZipEntry("dashboards.jsonl");
         zip.putNextEntry(entry);
         PrintWriter writer = new PrintWriter(zip, true, StandardCharsets.UTF_8);

         for(ViewsheetModel vs : getDashboards()) {
            writer.println(mapper.writeValueAsString(vs));
         }

         writer.flush();
         zip.closeEntry();
      }
      catch(Exception e) {
         LOG.error("Failed to write dashboards", e);
      }
   }

   private List<ViewsheetModel> getDashboards() {
      ViewsheetService service = SingletonManager.getInstance(ViewsheetService.class);
      return Arrays.stream(service.getRuntimeViewsheets(null))
         .filter(rvs -> rvs.getID() != null && !rvs.getID().isEmpty())
         .map(rvs -> createModel(rvs, service))
         .toList();
   }

   private ViewsheetModel createModel(RuntimeViewsheet rvs, ViewsheetService service) {
      List<ViewsheetThreadModel> threads = getThreads(rvs, service);
      return ViewsheetModel.builder()
         .from(rvs)
         .threads(threads)
         .state(threads.isEmpty() ? ViewsheetModel.State.OPEN : ViewsheetModel.State.EXECUTING)
         .build();
   }

   private List<ViewsheetThreadModel> getThreads(RuntimeViewsheet rvs, ViewsheetService service) {
      Vector<?> threads = service.getExecutingThreads(rvs.getID());

      synchronized(threads) {
         return threads.stream()
            .filter(o -> o instanceof WorksheetEngine.ThreadDef)
            .map(o -> (WorksheetEngine.ThreadDef) o)
            .map(t -> ViewsheetThreadModel.builder().from(t).build())
            .collect(Collectors.toList());
      }
   }

   private void writeQueries(ZipOutputStream zip) {
      try {
         ZipEntry entry = new ZipEntry("queries.jsonl");
         zip.putNextEntry(entry);
         PrintWriter writer = new PrintWriter(zip, true, StandardCharsets.UTF_8);

         for(QueryModel query : getQueries()) {
            writer.println(mapper.writeValueAsString(query));
         }

         writer.flush();
         zip.closeEntry();
      }
      catch(Exception e) {
         LOG.error("Failed to write queries", e);
      }
   }

   private List<QueryModel> getQueries() {
      List<QueryModel> qlist = new ArrayList<>();

      for(Map.Entry<String, QueryInfo> entry : XNodeTable.queryMap.entrySet()) {
         if(entry.getValue() != null) {
            QueryInfo info = (QueryInfo) entry.getValue().clone();
            qlist.add(QueryModel.builder().from(info, true).build());
         }
      }

      for(Map.Entry<String, QueryInfo> entry : XUtil.queryMap.entrySet()) {
         if(entry.getValue() != null && !XNodeTable.queryMap.containsKey(entry.getKey())) {
            QueryInfo info = (QueryInfo) entry.getValue().clone();
            qlist.add(QueryModel.builder().from(info, true).build());
         }
      }

      return qlist;
   }

   private void writeUsers(ZipOutputStream zip) {
      try {
         ZipEntry entry = new ZipEntry("users.jsonl");
         zip.putNextEntry(entry);
         PrintWriter writer = new PrintWriter(zip, true, StandardCharsets.UTF_8);

         for(SessionModel query : getUsers()) {
            writer.println(mapper.writeValueAsString(query));
         }

         writer.flush();
         zip.closeEntry();
      }
      catch(Exception e) {
         LOG.error("Failed to write users", e);
      }
   }

   private List<SessionModel> getUsers() {
      List<SessionModel> infos = new ArrayList<>();
      IgniteSessionRepository sessionRepository = getSessionRepository();

      if(sessionRepository == null) {
         SRPrincipal principal = (SRPrincipal) ThreadContext.getContextPrincipal();
         return principal == null ? List.of() : List.of(createModel(principal));
      }

      List<SRPrincipal> principals = sessionRepository.getActiveSessions();

      for(SRPrincipal principal : principals) {
         if(principal == null) {
            continue;
         }

         IdentityID userID = principal.getClientUserID();

         SessionModel.Builder info = SessionModel.builder();
         info.address(Tool.getRealIP(principal.getUser().getIPAddress()));
         info.dateCreated(principal.getAge());
         info.dateAccessed(principal.getLastAccess());
         info.user(userID);
         info.id(principal.getSessionID());
         info.activeViewsheets(0);

         if(principal.getRoles() != null) {
            info.roles(Arrays.asList(principal.getRoles()));
         }

         if(principal.getGroups() != null) {
            info.groups(Arrays.asList(principal.getGroups()));
         }

         info.organization(principal.getOrgId());
         infos.add(info.build());
      }

      return infos;
   }

   private IgniteSessionRepository getSessionRepository() {
      if(applicationContext == null) {
         return null;
      }

      return applicationContext.getBean(IgniteSessionRepository.class);
   }

   private SessionModel createModel(SRPrincipal principal) {
      IdentityID userID = principal.getClientUserID();

      SessionModel.Builder info = SessionModel.builder();
      info.address(Tool.getRealIP(principal.getUser().getIPAddress()));
      info.dateCreated(principal.getAge());
      info.dateAccessed(principal.getLastAccess());
      info.user(userID);
      info.id(principal.getSessionID());
      info.activeViewsheets(0);

      if(principal.getRoles() != null) {
         info.roles(Arrays.asList(principal.getRoles()));
      }

      if(principal.getGroups() != null) {
         info.groups(Arrays.asList(principal.getGroups()));
      }

      info.organization(principal.getOrgId());
      return info.build();
   }

   private void writeThreads(ZipOutputStream zip) {
      try {
         ZipEntry entry = new ZipEntry("thread-dump.txt");
         zip.putNextEntry(entry);
         PrintWriter writer = new PrintWriter(zip, true, StandardCharsets.UTF_8);
         ServerServiceMessageListener.getThreadDump(writer);
         writer.flush();
         zip.closeEntry();
      }
      catch(Exception e) {
         LOG.error("Failed to write thread dump", e);
      }
   }

   private void writeProperties(ZipOutputStream zip) {
      try {
         Properties props = SreeEnv.getProperties();
         String comments = "Version " + Tool.getReportVersion() + ", build " + Tool.getBuildNumber();
         ZipEntry entry = new ZipEntry("sree.properties");
         zip.putNextEntry(entry);
         props.store(zip, comments);
         zip.closeEntry();
      }
      catch(Exception e) {
         LOG.error("Failed to write properties", e);
      }
   }

   private void writeLogs(ZipOutputStream zip) throws IOException {
      LogManager.getInstance().zipLogs(zip);
   }

   private void writeMetrics(ZipOutputStream zip) {
      Metrics metrics = getMetrics();

      try {
         ZipEntry entry = new ZipEntry("metrics.json");
         zip.putNextEntry(entry);
         PrintWriter writer = new PrintWriter(zip, true, StandardCharsets.UTF_8);
         writer.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics));
         writer.flush();
         zip.closeEntry();
      }
      catch(Exception e) {
         LOG.error("Failed to write thread dump", e);
      }
   }

   private Metrics getMetrics() {
      VMMetrics vm = new VMMetrics(ManagementFactory.getRuntimeMXBean());
      CPUMetrics cpu = getCPUMetrics();
      MemoryMetrics memory = getMemoryMetrics();
      return new Metrics(vm, cpu, memory);
   }

   private CPUMetrics getCPUMetrics() {
      OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
      double cpuLoad = 0D;
      double processCpuLoad = 0D;

      if(bean instanceof com.sun.management.OperatingSystemMXBean os) {
         cpuLoad = os.getCpuLoad();
         processCpuLoad = os.getProcessCpuLoad();
      }

      return new CPUMetrics(
         bean.getSystemLoadAverage(), cpuLoad, processCpuLoad, bean.getAvailableProcessors());
   }

   private MemoryMetrics getMemoryMetrics() {
      OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
      long totalMemorySize = 0L;

      if(os instanceof com.sun.management.OperatingSystemMXBean bean) {
         totalMemorySize = bean.getTotalMemorySize();
      }

      MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
      MemoryUsageMetrics heap = new MemoryUsageMetrics(memory.getHeapMemoryUsage());
      MemoryUsageMetrics nonHeap = new MemoryUsageMetrics(memory.getNonHeapMemoryUsage());
      return new MemoryMetrics(heap, nonHeap, totalMemorySize);
   }

   private String getDumpName() {
      StringBuilder name = new StringBuilder();

      if(System.getProperty("ScheduleTaskRunner") != null) {
         name.append("task-").append(System.getProperty("ScheduleTaskRunner")).append("-");
      }
      else if("true".equals(System.getProperty("ScheduleServer"))) {
         name.append("scheduler-");
      }
      else {
         name.append("server-");
      }

      name.append(Tool.getIP()).append('-')
         .append(OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
      return "status/" + name.toString().replace(':', '_').replace('/', '_')
         .replace('\\', '_') + ".zip";
   }

   private ApplicationContext applicationContext;
   private final ObjectMapper mapper;
   private static final Logger LOG = LoggerFactory.getLogger(StatusDumpService.class);

   public record VMMetrics(String name, String vendor, String version,
                           @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC") Instant startTime,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Duration uptime)
   {
      public VMMetrics(String name, String vendor, String version, long startTime, long uptime) {
         this(name, vendor, version, Instant.ofEpochMilli(startTime), Duration.ofMillis(uptime));
      }

      public VMMetrics(RuntimeMXBean bean) {
         this(bean.getVmName(), bean.getVmVendor(), bean.getVmVersion(), bean.getStartTime(), bean.getUptime());
      }
   }

   public record CPUMetrics(double systemLoadAverage, double cpuLoad, double processCpuLoad, int availableProcessors) {
   }

   public record MemoryUsageMetrics(long init, long used, long commited, long max) {
      public MemoryUsageMetrics(MemoryUsage usage) {
         this(usage.getInit(), usage.getUsed(), usage.getCommitted(), usage.getMax());
      }
   }

   public record MemoryMetrics(MemoryUsageMetrics heap, MemoryUsageMetrics nonHeap,
                               long totalMemorySize)
   {}

   public record Metrics(VMMetrics vm, CPUMetrics cpu, MemoryMetrics memory) {}
}
