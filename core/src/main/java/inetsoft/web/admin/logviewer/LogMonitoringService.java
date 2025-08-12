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
package inetsoft.web.admin.logviewer;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Tool;
import inetsoft.util.log.LogManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

@Service
public class LogMonitoringService implements MessageListener {
   public LogMonitoringService() {
      this.logManager = LogManager.getInstance();
      this.cluster = Cluster.getInstance();
   }

   @PostConstruct
   public void addListener() {
      cluster.addMessageListener(this);
   }

   @PreDestroy
   public void removeListener() {
      if(cluster != null) {
         cluster.removeMessageListener(this);
      }
   }

   /**
    * Handles a request to get the log content.
    *
    * @param logFileName the name of the selected log file
    */
   public List<String> getLog(String clusterNode, String logFileName, int offset, int length) {
      if(SUtil.isCluster() && !cluster.getLocalMember().equals(clusterNode)) {
         try {
            GetLogRequest request = new GetLogRequest(logFileName, offset, length);
            GetLogResponse response =
               cluster.exchangeMessages(clusterNode, request, GetLogResponse.class);
            return response.getContent();
         }
         catch(Exception e) {
            LOG.error("Failed to get log file {} from {}", logFileName, clusterNode, e);
         }
      }
      else {
         return getLocalLog(logFileName, offset, length);
      }

      return null;
   }

   private List<String> getLocalLog(String logFileName, int offset, int length) {
      try {
         File file = logManager.findLogFile(logFileName);

         if(file == null) {
            LOG.warn("Log file does not exist: {}", logFileName);
         }
         else {
            return logManager.getLog(file, offset, length);
         }
      }
      catch(IOException exc) {
         LOG.error("Failed to read log file: {}", logFileName, exc);
      }

      return null;
   }

   public LogMonitoringModel getLogs() {
      List<LogFileModel> logFiles = getLocalLogs();

      LogFileModel selectedLog = null;
      String selectedLogFile =
         logManager.getCurrentLog(logFiles.stream().map(LogFileModel::getLogFile).toList());

      if(selectedLogFile != null) {
         selectedLog = logFiles.stream()
            .filter(f -> selectedLogFile.equals(f.getLogFile()))
            .findFirst()
            .orElse(null);
      }

      if(selectedLog == null) {
         selectedLog = logFiles.isEmpty() ? null : logFiles.getFirst();
      }

      for(String clusterNode : cluster.getClusterNodes(false)) {
         if(!clusterNode.equals(cluster.getLocalMember())) {
            try {
               GetLogFilesResponse response = cluster.exchangeMessages(
                  clusterNode, new GetLogFilesRequest(), GetLogFilesResponse.class);

               if(LicenseManager.getInstance().isEnterprise()) {
                  for(LogFileModel logfile : response.getLogFiles()) {
                     boolean exists = logFiles.stream().anyMatch(f -> f.getLogFile().equals(logfile.getLogFile()));

                     if(!exists) {
                        logFiles.add(logfile);
                     }
                  }
               }
            }
            catch(Exception e) {
               LOG.error("Failed to get log files from {}", clusterNode, e);
            }
         }
      }

      return new LogMonitoringModel(selectedLog, logFiles, true, true, 500);
   }

   private List<LogFileModel> getLocalLogs() {
      return new ArrayList<>(logManager.getLogFiles().stream()
                                .map(File::getName)
                                .map(f -> new LogFileModel(getClusterNode(f), f, logManager.isRotateSupported(f)))
                                .toList());
   }

   private String getClusterNode(String logFile) {
      if(SUtil.isCluster()) {
         return cluster.getLocalMember();
      }

      for(String node : cluster.getClusterNodes()) {
         if(Tool.equals(LogManager.extractIPFromLogName(logFile),
                        LogManager.extractIpFromNodeName(node)))
         {
            return node;
         }
      }

      return cluster.getLocalMember();
   }

   /**
    * Handles a request to download all log files.
    *
    * @param response the HTTP response object.
    */
   public void downloadLogs(HttpServletResponse response, String clusterNode) {
      ZipOutputStream output = null;

      try {
         output = createLogZip(response);
         logManager.zipLogs(output, clusterNode);
      }
      catch(Exception exc) {
         LOG.error("Failed to create zip file of all log files", exc);
      }
      finally {
         IOUtils.closeQuietly(output);
      }
   }

   /**
    * Rolls over a log file so that the primary log file is empty.
    *
    * @param logFileName log file name
    *
    * @throws Exception if the response could not be encoded.
    */
   public LogMonitoringModel rotateLogFile(String clusterNode, String logFileName) throws Exception {
      if(SUtil.isCluster() && !cluster.getLocalMember().equals(clusterNode)) {
         RotateLogFileRequest request = new RotateLogFileRequest(logFileName);
         cluster.exchangeMessages(clusterNode, request, RotateLogFileResponse.class);
      }
      else {
         logManager.rotateLogFile(logFileName);
      }

      return getLogs();
   }

   /**
    * Sets the response headers and creates an output stream to create a Zip
    * file containing the log files.
    *
    * @param response the HTTP response object.
    *
    * @return the output stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   private ZipOutputStream createLogZip(HttpServletResponse response)
      throws IOException {
      response.setHeader(
         "Content-Disposition", "attachment; filename=\"inetsoft_logs.zip\"");
      response.setContentType("application/octet-stream");
      return new ZipOutputStream(response.getOutputStream());
   }

   public LogViewLinks getLinks(Principal principal) {
      boolean fluentdLogging = "fluentd".equals(SreeEnv.getProperty("log.provider"));
      String logViewUrl = null;

      if(fluentdLogging) {
         logViewUrl = SreeEnv.getProperty("log.fluentd.logViewUrl");

         if(logViewUrl != null && logViewUrl.contains("{organizationId}")) {
            logViewUrl = addOrganizationId(logViewUrl);
         }

         if(logViewUrl != null && logViewUrl.trim().isEmpty()) {
            logViewUrl = null;
         }
      }

      return LogViewLinks.builder()
         .fluentdLogging(fluentdLogging)
         .logViewUrl(logViewUrl)
         .build();
   }

   private String addOrganizationId(String url) {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      return url.replace("{organizationId}",orgId);
   }

   @Override
   public void messageReceived(MessageEvent event) {
      String sender = event.getSender();

      if(event.getMessage() instanceof GetLogRequest request) {
         handleGetLogRequest(sender, request);
      }
      else if(event.getMessage() instanceof GetLogFilesRequest request) {
         handleGetLogFilesRequest(sender, request);
      }
      else if(event.getMessage() instanceof RotateLogFileRequest request) {
         handleRotateLogFileRequest(sender, request);
      }
   }

   private void handleGetLogRequest(String address, GetLogRequest request) {
      try {
         GetLogResponse response = new GetLogResponse(
            getLocalLog(request.getLogFileName(), request.getOffset(), request.getLength()));
         cluster.sendMessage(address, response);
      }
      catch(Exception e) {
         LOG.warn("Failed to send get log response", e);
      }
   }

   private void handleGetLogFilesRequest(String address,
                                         @SuppressWarnings("unused") GetLogFilesRequest request)
   {
      try {
         GetLogFilesResponse response = new GetLogFilesResponse(getLocalLogs());
         cluster.sendMessage(address, response);
      }
      catch(Exception e) {
         LOG.warn("Failed to send get log files response", e);
      }
   }

   private void handleRotateLogFileRequest(String address, RotateLogFileRequest request) {
      try {
         logManager.rotateLogFile(request.getLogFileName());
         cluster.sendMessage(address, new RotateLogFileResponse());
      }
      catch(Exception e) {
         LOG.warn("Failed to send rotate log response", e);
      }
   }

   private final LogManager logManager;
   private final Cluster cluster;
   private static final Logger LOG = LoggerFactory.getLogger(LogMonitoringService.class);
}
