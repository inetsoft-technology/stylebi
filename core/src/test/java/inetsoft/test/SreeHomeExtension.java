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
package inetsoft.test;

import inetsoft.mv.trans.UserInfo;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.KeyValueConfig;
import inetsoft.web.admin.content.repository.MVSupportService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.*;

import java.io.*;
import java.lang.management.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

public class SreeHomeExtension implements BeforeAllCallback, AfterAllCallback {
   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      if(deadlockThreadDump != null) {
         throw new Exception(
            "SingletonManager.reset() is stalled. Thread dump:\n" + deadlockThreadDump);
      }

      ExtensionContext.Store store = context.getStore(NAMESPACE);

      System.setProperty(
         "inetsoft.sree.internal.cluster.implementation", TestCluster.class.getName());
      Path clusterDir = Files.createTempDirectory("ignite-test");
      System.setProperty("inetsoftClusterDir", clusterDir.toAbsolutePath().toString());
      store.put(CLUSTER_DIR, clusterDir);

      SreeHome annotation = context.getRequiredTestClass().getAnnotation(SreeHome.class);
      String home = null;

      if(annotation != null) {
         home = annotation.value();
      }

      if(home == null || home.isEmpty()) {
         home = System.getProperty("sree.home", ".");
      }

      home = new File(home).getCanonicalPath();
      Path homePath = Paths.get(home);
      Files.createDirectories(homePath);
      writeConfig(homePath);
      ConfigurationContext.getContext().setHome(home);
      Tool.setServer(true);

      if(annotation != null) {
         for(SreeProperty prop : annotation.properties()) {
            SreeEnv.setProperty(prop.name(), prop.value());
         }

         if(annotation.properties().length > 0) {
            SreeEnv.save();
         }

         for(DataSpaceFile spaceFile : annotation.dataSpace()) {
            int index = spaceFile.path().lastIndexOf('/');
            String dir = index < 0 ? null : spaceFile.path().substring(0, index);
            String file = index < 0 ? spaceFile.path() : spaceFile.path().substring(index + 1);
            DataSpace space = DataSpace.getDataSpace();

            if(dir != null) {
               space.makeDirectories(dir);
            }

            try(InputStream in = getClass().getResourceAsStream(spaceFile.resource())) {
               space.withOutputStream(dir, file, out ->
                  IOUtils.copy(Objects.requireNonNull(in), out));
            }
         }

         if(annotation.security()) {
            SecurityEngine.getSecurity().init();
         }

         for(String url : annotation.importUrls()) {
            if(!url.isEmpty()) {
               importAssets(URI.create(url).toURL());
            }
         }

         for(String resource : annotation.importResources()) {
            if(!resource.isEmpty()) {
               importAssets(context.getRequiredTestClass().getResource(resource));
            }
         }

         Set<String> mvNames = new HashSet<>();
         MVSupportService mvSupport = null;

         for(String assetId : annotation.materialize()) {
            if(!assetId.isEmpty()) {
               mvSupport = MVSupportService.getInstance();
               materialize(assetId, mvSupport, mvNames);
            }
         }

         if(mvSupport != null) {
            store.put(MV_SUPPORT, mvSupport);
            store.put(MV_NAMES, new ArrayList<>(mvNames));
         }
      }

      SreeEnv.init();
   }

   @SuppressWarnings("unchecked")
   @Override
   public void afterAll(ExtensionContext context) throws Exception {
      ExtensionContext.Store store = context.getStore(NAMESPACE);
      MVSupportService mvSupport = store.remove(MV_SUPPORT, MVSupportService.class);
      List<String> mvNames = store.remove(MV_NAMES, List.class);
      Path clusterDir = store.remove(CLUSTER_DIR, Path.class);

      if(mvSupport != null && mvNames != null) {
         mvSupport.dispose(mvNames);
      }

      Thread thread = new Thread(SingletonManager::reset);
      thread.setDaemon(true);
      thread.start();
      thread.join(30000L);

      try {
         FileUtils.deleteDirectory(clusterDir.toFile());
      }
      catch(Exception ignore) {
      }

      if(thread.isAlive()) {
         deadlockThreadDump = getThreadDump();
         thread.interrupt();
         throw new Exception(
            "SingletonManager.reset() is stalled. Thread dump:\n" + deadlockThreadDump);
      }
   }

   private void importAssets(URL url) throws Exception {
      System.err.println("Importing assets from " + url);
      AnalyticRepository repository = SUtil.getRepletRepository();

      if(repository instanceof RepletEngine) {
         try(InputStream input = url.openStream()) {
            XPrincipal testPrincipal = SUtil.getPrincipal(
               new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()), null, false);
            testPrincipal.setOrgId(Organization.getDefaultOrganizationID());
            ThreadContext.setPrincipal(testPrincipal);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Tool.copyTo(input, buffer);
            ((RepletEngine) repository).importAssets(buffer.toByteArray(), true);
         }
      }
      else {
         throw new IllegalStateException("Repository is not RepletEngine, cannot import");
      }
   }

   private void materialize(String assetId, MVSupportService support, Set<String> mvNames) throws Exception {
      Principal user = SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()), null, false);
      List<String> identifiers = new ArrayList<>();
      identifiers.add(assetId);

      MVSupportService.AnalysisResult analysisResult =
         support.analyze(identifiers, false, true, true, user, false, false);

      analysisResult.waitFor();

      if(!analysisResult.getExceptions().isEmpty()) {
         String msg = analysisResult.getExceptions().stream()
            .map(UserInfo::getMessage)
            .collect(Collectors.joining("\n"));
         throw new Exception(msg);
      }

      List<MVSupportService.MVStatus> statuses = analysisResult.getStatus();
      List<String> names = statuses.stream()
         .map(s -> s.getDefinition().getName()).collect(Collectors.toList());

      try {
         String msg = support.createMV(names, statuses, false, false, user);

         if(msg != null) {
            throw new Exception(msg);
         }
      }
      catch(Exception e) {
         throw e;
      }
      catch(Throwable e) {
         throw new Exception("Failed to create MV", e);
      }

      mvNames.addAll(names);
   }

   private String getThreadDump() {
      String result = null;

      try {
         ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
         StringWriter buffer = new StringWriter();
         PrintWriter writer = new PrintWriter(buffer);

         writer.format(
            "%1$tF %1$tT %1$tZ\r\n\r\n", new Date(System.currentTimeMillis()));
         Set<Thread> allThreads = Thread.getAllStackTraces().keySet();

         for(ThreadInfo thread : threadBean.dumpAllThreads(true, true)) {
            writer.format(
               "\"%s\" Id=%d %s", thread.getThreadName(), thread.getThreadId(),
               thread.getThreadState());

            if(thread.getLockName() != null) {
               writer.format(" on %s", thread.getLockName());
            }

            if(thread.getLockOwnerName() != null) {
               writer.format(
                  " owned by \"%s\" Id=%d", thread.getLockOwnerName(),
                  thread.getLockOwnerId());
            }

            if(thread.isSuspended()) {
               writer.print(" (suspended)");
            }

            if(thread.isInNative()) {
               writer.print(" (in native)");
            }

            writer.print("\r\n");
            printStackTrace(thread, writer);

            for(Thread actualThread : allThreads) {
               if(actualThread.threadId() == thread.getThreadId()) {
                  if(actualThread instanceof GroupedThread groupedThread) {

                     if(groupedThread.getPrincipal() != null || groupedThread.hasRecords()) {
                        writer.print("\r\n\tThread context:\r\n");
                        writer.print("\t===============\r\n");

                        if(groupedThread.getPrincipal() != null) {
                           writer.format(
                              "\tUser: %s\r\n",
                              groupedThread.getPrincipal().getName());
                        }

                        for(Object record : groupedThread.getRecords()) {
                           writer.format("\t%s\r\n", record);
                        }
                     }
                  }

                  break;
               }
            }

            LockInfo[] locks = thread.getLockedSynchronizers();

            if(locks.length > 0) {
               writer.format(
                  "\r\n\tNumber of locked synchronizers = %d\r\n",
                  locks.length);

               for(LockInfo li : locks) {
                  writer.format("\t- %s\r\n", li);
               }
            }

            writer.print("\r\n");
         }

         long[] deadlocks = threadBean.findDeadlockedThreads();

         if(deadlocks != null && deadlocks.length > 0) {
            writer.print("Found Java-level deadlocks:\r\n");
            writer.print("===========================\r\n");

            ThreadInfo[] threads = threadBean.getThreadInfo(deadlocks);

            for(ThreadInfo thread : threads) {
               writer.format(
                  "\"%s\":\r\n  waiting to lock %s,\r\n  which is held by \"%s\"\r\n",
                  thread.getThreadName(), thread.getLockName(),
                  thread.getLockOwnerName());
            }

            writer.print("\r\n");
            writer.print(
               "Java stack information for the threads listed above:\r\n");
            writer.print(
               "====================================================\r\n");

            for(ThreadInfo thread : threads) {
               writer.format("\"%s\":\r\n", thread.getThreadName());
               printStackTrace(thread, writer);
            }
         }

         writer.close();
         result = buffer.toString();
      }
      catch(Exception e) {
         //noinspection CallToPrintStackTrace
         e.printStackTrace();
      }

      return result;
   }

   private void printStackTrace(ThreadInfo thread, PrintWriter writer) {
      StackTraceElement[] stackTrace = thread.getStackTrace();
      int i = 0;

      for(; i < stackTrace.length; i++) {
         StackTraceElement ste = stackTrace[i];
         writer.format("\tat %s\r\n", ste.toString());

         if(i == 0 && thread.getLockInfo() != null) {
            Thread.State ts = thread.getThreadState();

            switch(ts) {
            case BLOCKED:
               writer.format("\t-  blocked on %s\r\n", thread.getLockInfo());
               break;
            case WAITING:
            case TIMED_WAITING:
               writer.format("\t-  waiting on %s\r\n", thread.getLockInfo());
               break;
            default:
            }
         }

         for(MonitorInfo mi : thread.getLockedMonitors()) {
            if(mi.getLockedStackDepth() == i) {
               writer.format("\t-  locked %s\r\n", mi);
            }
         }
      }
   }

   private void writeConfig(Path home) {
      InetsoftConfig config = InetsoftConfig.createDefault(home);
      KeyValueConfig keyValue = new KeyValueConfig();
      keyValue.setType("test");
      config.setKeyValue(keyValue);

      InetsoftConfig.save(config, home.resolve("inetsoft.yaml"));
   }

   private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(SreeHomeExtension.class.getName());
   private static final String MV_SUPPORT = "MVSupportService";
   private static final String MV_NAMES = "MVNames";
   private static final String CLUSTER_DIR = "ClusterDir";
   private static String deadlockThreadDump;
}
