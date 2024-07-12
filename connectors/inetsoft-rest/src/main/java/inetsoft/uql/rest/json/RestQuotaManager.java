/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.json;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XQuery;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * Manages execution of REST method calls within service-defined quotas.
 */
public class RestQuotaManager {
   /**
    * Executes a method with a quota.
    *
    * @param dataSource the data source defining the quota.
    *
    * @param fn the method to call.
    *
    * @param <T> the return type of the method.
    *
    * @return the result of the method.
    *
    * @throws Exception the exception thrown by the method.
    */
   public static <T> T withQuota(XDataSource dataSource, Callable<T> fn) throws Exception {
      return getQuota(dataSource).withQuota(fn);
   }

   /**
    * Executes a method with a quota.
    *
    * @param dataSource the data source defining the quota.
    *
    * @param fn the method to call.
    *
    * @throws Exception the exception thrown by the method.
    */
   public static void withQuota(XDataSource dataSource, QuotaConstrainedMethod fn) throws Exception
   {
      getQuota(dataSource).withQuota(fn);
   }

   /**
    * Executes a method with a quota.
    *
    * @param query the query defining the quota.
    *
    * @param fn the method to call.
    *
    * @param <T> the return type of the method.
    *
    * @return the result of the method.
    *
    * @throws Exception the exception thrown by the method.
    */
   public static <T> T withQuota(XQuery query, Callable<T> fn) throws Exception {
      return withQuota(query.getDataSource(), fn);
   }

   /**
    * Executes a method with a quota.
    *
    * @param query the query defining the quota.
    *
    * @param fn the method to call.
    *
    * @throws Exception the exception thrown by the method.
    */
   public static void withQuota(XQuery query, QuotaConstrainedMethod fn) throws Exception {
      withQuota(query.getDataSource(), fn);
   }

   private static RestQuota getQuota(XDataSource dataSource) {
      double requestsPerSecond;
      int maxConnections;

      if(dataSource instanceof EndpointJsonDataSource) {
         EndpointJsonDataSource ds = (EndpointJsonDataSource) dataSource;
         requestsPerSecond = ds.getRequestsPerSecond();
         maxConnections = ds.getMaxConnections();
      }
      else {
         requestsPerSecond = -1D;
         maxConnections = -1;
      }

      RestQuotaKey key =
         new RestQuotaKey(dataSource.getFullName(), requestsPerSecond, maxConnections);
      return Singleton.INSTANCE.cache.get(key);
   }

   enum Singleton {
      INSTANCE;

      final LoadingCache<RestQuotaKey, RestQuota> cache = Caffeine.newBuilder()
         .maximumSize(1000L)
         .expireAfterAccess(1L, TimeUnit.HOURS)
         .build(key -> new RestQuota(key.getRequestsPerSecond(), key.getMaxConnections()));
   }

   @FunctionalInterface
   public interface QuotaConstrainedMethod {
      void call() throws Exception;
   }

   private static final class RestQuotaKey {
      RestQuotaKey(String dataSource, double requestsPerSecond, int maxConnections) {
         this.dataSource = dataSource;
         this.requestsPerSecond = requestsPerSecond;
         this.maxConnections = maxConnections;
      }

      String getDataSource() {
         return dataSource;
      }

      double getRequestsPerSecond() {
         return requestsPerSecond;
      }

      int getMaxConnections() {
         return maxConnections;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) return true;
         if(!(o instanceof RestQuotaKey)) return false;
         RestQuotaKey that = (RestQuotaKey) o;
         return Objects.equals(dataSource, that.dataSource);
      }

      @Override
      public int hashCode() {
         return Objects.hash(dataSource);
      }

      private final String dataSource;
      private final double requestsPerSecond;
      private final int maxConnections;
   }

   @SuppressWarnings("UnstableApiUsage")
   private static final class RestQuota {
      RestQuota(double requestsPerSecond, int maxConnections) {
         this.limiter = requestsPerSecond < 0 ? null : RateLimiter.create(requestsPerSecond);
         this.connections = maxConnections < 0 ? null : new Semaphore(maxConnections, true);
      }

      <T> T withQuota(Callable<T> fn) throws Exception {
         if(connections != null) {
            connections.acquire();
         }

         try {
            if(limiter != null) {
               limiter.acquire();
            }

            return fn.call();
         }
         finally {
            if(connections != null) {
               connections.release();
            }
         }
      }

      void withQuota(QuotaConstrainedMethod fn) throws Exception {
         withQuota(() -> {
            fn.call();
            return null;
         });
      }

      private final RateLimiter limiter;
      private final Semaphore connections;
   }
}
