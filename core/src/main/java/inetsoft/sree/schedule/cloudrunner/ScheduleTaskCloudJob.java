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
package inetsoft.sree.schedule.cloudrunner;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Tool;
import inetsoft.util.config.*;
import org.apache.commons.io.IOUtils;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@DisallowConcurrentExecution
public class ScheduleTaskCloudJob implements InterruptableJob {
   public ScheduleTaskCloudJob() {
      cluster = Cluster.getInstance();
      timeout = Long.parseLong(SreeEnv.getProperty("schedule.task.timeout"));
   }

   @Override
   public void execute(JobExecutionContext context) throws JobExecutionException
   {
      createCloudRunnerConfig();
      taskName = context.getJobDetail().getKey().getName();

      MessageListener listener = event -> {
         if(event.getMessage() instanceof CloudJobResult jobResult) {
            if(Tool.equals(taskName, jobResult.getTaskName())) {
               this.result = jobResult;
               latch.countDown();
            }
         }
      };

      cluster.addMessageListener(listener);

      try {
         CloudRunnerConfig cloudRunnerConfig = InetsoftConfig.getInstance().getCloudRunner();

         for(CloudJobFactory factory : ServiceLoader.load(CloudJobFactory.class)) {
            if(factory != null && factory.getType().equalsIgnoreCase(cloudRunnerConfig.getType())) {
               ScheduleTask task = (ScheduleTask) context.getJobDetail().getJobDataMap()
                  .get(ScheduleTask.class.getName());
               String cycle = null;
               String orgID = null;

               if(task.getCycleInfo() != null) {
                  cycle = task.getCycleInfo().getName();

                  if(cycle != null) {
                     cycle = UriUtils.encode(cycle, StandardCharsets.UTF_8);
                  }

                  orgID = task.getCycleInfo().getOrgId();

                  if(orgID != null) {
                     orgID = UriUtils.encode(orgID, StandardCharsets.UTF_8);
                  }
               }

               job = factory.createCloudJob(
                  UriUtils.encode(taskName, StandardCharsets.UTF_8), cycle, orgID);
               job.start();
               LOG.debug("Started cloud job: " + job.getClass().getName());
               break;
            }
         }

         // timeout value might need to be configurable
         if(!latch.await(timeout, TimeUnit.MILLISECONDS)) {
            if(job != null) {
               job.stop();
            }

            throw new JobExecutionException("Scheduled task '" + taskName + "' timed out");
         }

         if(result != null && !result.isSuccess()) {
            throw new JobExecutionException("Scheduled task '" + SUtil.getTaskNameWithoutOrg(taskName)
               + "' failed to complete: " + result.getMessage());
         }
      }
      catch(InterruptedException e) {
         if(!interrupted) {
            throw new JobExecutionException(
               "Scheduled task '" + SUtil.getTaskNameWithoutOrg(taskName) +
                  "' was interrupted unexpectedly", e);
         }
      }
      catch(Throwable e) {
         throw new JobExecutionException(
            "Scheduled task '" + SUtil.getTaskNameWithoutOrg(taskName) + "' failed to complete", e);
      }
      finally {
         if(interrupted) {
            context.getJobDetail().getJobDataMap().put("inetsoft.cancelled", true);
         }
      }
   }

   @Override
   public void interrupt() {
      interrupted = true;
      latch.countDown();

      if(taskName != null) {
         try {
            cluster.sendMessage(new CancelCloudJob(taskName));
         }
         catch(Exception e) {
            LOG.error("Failed to send a cancel cloud job message", e);
         }

         if(job != null) {
            job.stop();
         }
      }
   }

   private void createCloudRunnerConfig() {
      Lock lock = cluster.getLock("cloud.runner.configLock");
      lock.lock();

      try {
         DistributedMap<String, InetsoftConfig> configMap =
            cluster.getMap("cloud.runner.config");
         InetsoftConfig config = InetsoftConfig.getInstance();

         if(!configMap.containsKey("config")) {
            InetsoftConfig runnerConfig = new InetsoftConfig();
            runnerConfig.setVersion(config.getVersion());
            runnerConfig.setPluginDirectory("/var/lib/inetsoft/plugins");
            runnerConfig.setKeyValue(config.getKeyValue());
            runnerConfig.setSecrets((SecretsConfig) config.getSecrets().clone());
            runnerConfig.setAdditionalProperties(config.getAdditionalProperties());

            BlobConfig blobConfig = new BlobConfig();
            blobConfig.setType(config.getBlob().getType());
            blobConfig.setCacheDirectory("/var/lib/inetsoft/blob-cache");
            blobConfig.setAzure(config.getBlob().getAzure());
            blobConfig.setFilesystem(config.getBlob().getFilesystem());
            blobConfig.setGcs(config.getBlob().getGcs());
            blobConfig.setS3(config.getBlob().getS3());
            runnerConfig.setBlob(blobConfig);

            ClusterConfig clusterConfig = new ClusterConfig();
            updateClusterConfig(config, clusterConfig);
            runnerConfig.setCluster(clusterConfig);

            ExternalStorageConfig externalConfig = new ExternalStorageConfig();
            externalConfig.setType(config.getExternalStorage().getType());
            externalConfig.setAzure(config.getExternalStorage().getAzure());
            externalConfig.setFilesystem(config.getExternalStorage().getFilesystem());
            externalConfig.setGcs(config.getExternalStorage().getGcs());
            externalConfig.setS3(config.getExternalStorage().getS3());
            runnerConfig.setExternalStorage(externalConfig);

            AuditConfig auditConfig = new AuditConfig();
            auditConfig.setType(config.getAudit().getType());
            auditConfig.setCosmosdb(config.getAudit().getCosmosdb());
            auditConfig.setDatabase(config.getAudit().getDatabase());
            auditConfig.setDynamodb(config.getAudit().getDynamodb());
            auditConfig.setFirestore(config.getAudit().getFirestore());
            auditConfig.setMongo(config.getAudit().getMongo());
            runnerConfig.setAudit(auditConfig);

            // add to distributed map
            configMap.put("config", runnerConfig);
         }
         else {
            InetsoftConfig oldConfig = configMap.get("config");
            ClusterConfig clusterConfig = oldConfig.getCluster();
            updateClusterConfig(config, clusterConfig);
            configMap.put("config", oldConfig);
         }
      }
      finally {
         lock.unlock();
      }
   }

   private void updateClusterConfig(InetsoftConfig config, ClusterConfig clusterConfig) {
      IpFinderConfig ipFinder = config.getCluster().getIpFinder();

      // do not user ip finder for aws, because do not determine the port of server node,
      // registered addresses from aws.elb ip finder, it just use default port.
      if(ipFinder != null && !"aws.elb".equals(ipFinder.getType())) {
         clusterConfig.setIpFinder(config.getCluster().getIpFinder());
      }

      clusterConfig.setTcpEnabled(true);
      clusterConfig.setMulticastEnabled(false);
      clusterConfig.setTcpMembers(cluster.getClusterAddresses().toArray(new String[0]));
      clusterConfig.setClientMode(true);
      copyRootCA(config.getCluster(), clusterConfig);
   }

   private void copyRootCA(ClusterConfig inConfig, ClusterConfig outConfig) {
      if(inConfig != null) {
         if(inConfig.getCaKey() != null) {
            outConfig.setCaKey(inConfig.getCaKey());
         }
         else if(inConfig.getCaKeyFile() != null) {
            try(Reader reader = Files.newBufferedReader(
               Paths.get(inConfig.getCaKeyFile()), StandardCharsets.US_ASCII))
            {
               outConfig.setCaKey(IOUtils.toString(reader));
            }
            catch(IOException e) {
               throw new RuntimeException("Failed to read CA key file", e);
            }
         }

         if(inConfig.getCaKeyPassword() != null) {
            outConfig.setCaKeyPassword(inConfig.getCaKeyPassword());
         }

         if(inConfig.getCaCertificate() != null) {
            outConfig.setCaCertificate(inConfig.getCaCertificate());
         }
         else if(inConfig.getCaCertificateFile() != null) {
            try(Reader reader = Files.newBufferedReader(
               Paths.get(inConfig.getCaCertificateFile()), StandardCharsets.US_ASCII))
            {
               outConfig.setCaCertificate(IOUtils.toString(reader));
            }
            catch(IOException e) {
               throw new RuntimeException("Failed to read CA certificate file", e);
            }
         }
      }
   }

   private CloudJob job;
   private final Cluster cluster;
   private String taskName;
   private boolean interrupted = false;
   private CloudJobResult result;
   private final CountDownLatch latch = new CountDownLatch(1);
   private final long timeout;
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskCloudJob.class);
}
