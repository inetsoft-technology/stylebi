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
package inetsoft.sree.internal.cluster.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.services.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.ignite.IgniteQueue;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Ignite singleton service that consumes tasks from a distributed {@link org.apache.ignite.IgniteQueue}
 * and sends results back to the originating node via Ignite messaging.
 *
 * <p>Tasks are submitted non-blocking by the caller side ({@link IgniteCluster#submit(String,
 * inetsoft.sree.internal.cluster.SingletonCallableTask)}) and survive the failure of any single
 * node because the queue is backed by the distributed Ignite data grid. Uses
 * {@link IgniteQueue#poll(long, java.util.concurrent.TimeUnit)} so the executor thread wakes
 * immediately when a task is enqueued rather than sleeping for up to 1 second between tasks.
 */
public class ServiceTaskExecutorImpl implements Service {
   public ServiceTaskExecutorImpl(String serviceId) {
      this.serviceId = serviceId;
   }

   @Override
   public void init() {
      // The queue is created by IgniteCluster.ensureServiceDeployed() before this service is
      // started, so passing null here retrieves the existing distributed queue.
      this.queue = ignite.queue(QUEUE_PREFIX + serviceId, 0, null);
   }

   @Override
   public void execute() {
      running = true;
      Thread.currentThread().setName("cluster-service-" + serviceId);
      ClassLoader loader = getClass().getClassLoader();

      while(running) {
         try {
            // poll() blocks until a task arrives or 1 second elapses, then returns
            // immediately — no artificial sleep between consecutive tasks.
            ServiceTaskRequest request = queue.poll(1L, TimeUnit.SECONDS);

            if(request != null) {
               processRequest(request, loader);
            }
         }
         catch(Exception e) {
            LOG.error("Error processing service task for {}", serviceId, e);

            // Backoff to avoid tight retry loop under cluster instability
            try {
               Thread.sleep(1000L);
            }
            catch(InterruptedException ie) {
               Thread.currentThread().interrupt();
               break;
            }
         }
      }
   }

   @Override
   public void cancel() {
      running = false;
   }

   private void processRequest(ServiceTaskRequest request, ClassLoader loader) {
      ClassLoader prev = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(loader);

      try {
         Serializable result = null;

         if(request.isCallable()) {
            result = (Serializable) request.getCallableTask().call();
         }
         else {
            request.getRunnableTask().run();
         }

         sendResult(request, ServiceTaskResult.success(request.getTaskId(), result));
      }
      catch(Exception e) {
         sendResult(request, ServiceTaskResult.failure(request.getTaskId(), e));
      }
      finally {
         Thread.currentThread().setContextClassLoader(prev);
      }
   }

   private void sendResult(ServiceTaskRequest request, ServiceTaskResult result) {
      try {
         ignite.message(ignite.cluster().forNodeId(request.getCallerNodeId()))
            .send(RESULT_TOPIC, result);
      }
      catch(Exception e) {
         LOG.warn("Failed to send task result for task {}, caller node {} may have left the cluster",
                  request.getTaskId(), request.getCallerNodeId(), e);
      }
   }

   // Prefix for the distributed queue name for each service.
   static final String QUEUE_PREFIX = "inetsoft.service.task.queue.";

   // Ignite messaging topic used to deliver task results back to the caller node.
   static final String RESULT_TOPIC = IgniteCluster.class.getName() + ".serviceTaskResult";

   @IgniteInstanceResource
   private transient Ignite ignite;

   private volatile boolean running;
   private transient IgniteQueue<ServiceTaskRequest> queue;
   private final String serviceId;

   private static final Logger LOG = LoggerFactory.getLogger(ServiceTaskExecutorImpl.class);
}
