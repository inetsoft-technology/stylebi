/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.test.schedule;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.cloudrunner.*;

import java.util.Map;
import java.util.concurrent.*;

/**
 * In-process {@link CloudJobFactory} for Layer 3 cloud-protocol tests.
 *
 * <p>Registered via ServiceLoader so {@link inetsoft.sree.schedule.cloudrunner.ScheduleTaskCloudJob}
 * can discover it when the cloud-runner type is set to {@code "fake"}. Tests can also
 * instantiate it directly.
 *
 * <p>Usage pattern:
 * <pre>
 *   FakeCloudJobFactory.registerTask(task);          // register before start
 *   CloudJob job = new FakeCloudJobFactory()
 *                      .createCloudJob(task.getTaskId(), null, null);
 *   job.start();
 *   CloudJobResult result = (CloudJobResult) clusterMessages.poll(5, SECONDS);
 * </pre>
 *
 * <p>After {@code start()}, the inner {@code FakeCloudJob} runs {@code task.run(null)} on a
 * daemon thread and delivers a {@link CloudJobResult} via
 * {@code Cluster.getInstance().sendMessage()} — which the {@link SchedulerTestHarness} mock
 * captures in its {@code clusterMessages} queue.
 *
 * <p>Call {@link #clearRegistry()} in {@code @AfterEach} to prevent cross-test task pollution.
 */
public class FakeCloudJobFactory implements CloudJobFactory {

   /** Task registry keyed by task ID, shared across all factory instances in one test JVM. */
   private static final Map<String, ScheduleTask> TASK_REGISTRY = new ConcurrentHashMap<>();

   /** Register a task so {@code FakeCloudJob.start()} can look it up by task ID. */
   public static void registerTask(ScheduleTask task) {
      TASK_REGISTRY.put(task.getTaskId(), task);
   }

   /** Remove all registered tasks. Call in {@code @AfterEach}. */
   public static void clearRegistry() {
      TASK_REGISTRY.clear();
   }

   @Override
   public String getType() {
      return "fake";
   }

   @Override
   public CloudJob createCloudJob(String taskName, String cycle, String orgID) {
      return new FakeCloudJob(taskName);
   }

   // ---------------------------------------------------------------------------

   /**
    * In-process CloudJob that runs the registered task in a daemon thread and reports
    * the outcome as a {@link CloudJobResult} via {@code Cluster.sendMessage()}.
    */
   public static class FakeCloudJob implements CloudJob {

      FakeCloudJob(String taskName) {
         this.taskName = taskName;
         // Daemon thread factory constructed here so taskName is already assigned.
         this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FakeCloudJob-" + taskName);
            t.setDaemon(true);
            return t;
         });
      }

      @Override
      public void start() {
         try {
         executor.submit(() -> {
            boolean success = true;
            String message = "ok";

            try {
               ScheduleTask task = TASK_REGISTRY.get(taskName);

               if(task != null) {
                  task.run(null);
               }
            }
            catch(Throwable e) {
               success = false;
               message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }

            try {
               // Cluster mock in SchedulerTestHarness captures this in clusterMessages queue.
               Cluster.getInstance().sendMessage(new CloudJobResult(taskName, success, message));
            }
            catch(Exception ignored) {
               // If cluster is unavailable the result is lost; the test will time out.
            }

            executor.shutdown();
         });
         }
         catch(java.util.concurrent.RejectedExecutionException ignored) {
            // stop() was called before start() — treat as a no-op.
         }
      }

      /** Interrupt the running task thread and prevent further execution. */
      @Override
      public void stop() {
         executor.shutdownNow();
      }

      private final String taskName;
      private final ExecutorService executor;
   }
}
