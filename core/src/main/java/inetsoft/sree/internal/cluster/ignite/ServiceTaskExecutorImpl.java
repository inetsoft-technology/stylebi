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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.SingletonCallableTask;
import inetsoft.sree.internal.cluster.SingletonRunnableTask;
import inetsoft.util.GroupedThread;
import org.apache.ignite.services.Service;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServiceTaskExecutorImpl implements ServiceTaskExecutor, Service {
   public ServiceTaskExecutorImpl(String serviceId) {
      this.serviceId = serviceId;
   }


   @Override
   public void init() throws Exception {
      this.executor = Executors.newSingleThreadExecutor(
         runnable -> new GroupedThread(runnable, "cluster-service-" + serviceId));
   }

   @Override
   public void execute() throws Exception {

   }

   @Override
   public void cancel() {
      this.executor.shutdown();
   }

   @Override
   public <T extends Serializable> T submitTask(SingletonCallableTask<T> task) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();

      try {
         Thread.currentThread().setContextClassLoader(ServiceTaskExecutorImpl.class.getClassLoader());
         return executor.submit(task).get();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
      finally {
         Thread.currentThread().setContextClassLoader(loader);
      }
   }

   @Override
   public void submitTask(SingletonRunnableTask task) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();

      try {
         Thread.currentThread().setContextClassLoader(ServiceTaskExecutorImpl.class.getClassLoader());
         executor.submit(task).get();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
      finally {
         Thread.currentThread().setContextClassLoader(loader);
      }
   }

   private ExecutorService executor;
   private final String serviceId;
}
