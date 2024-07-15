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
package inetsoft.web;

import inetsoft.uql.XPrincipal;
import inetsoft.util.GroupedThread;
import inetsoft.util.ThreadPool;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GroupedThreadAspect {
   @Around("@annotation(inetsoft.web.viewsheet.InGroupedThread) && within(inetsoft.web..*)")
   public Object runInGroupedThread(ProceedingJoinPoint pjp) throws Throwable {
      return withGroupedThread(pjp, pjp::proceed);
   }

   public static Object withGroupedThread(ProceedingJoinPoint pjp, GroupedThreadAdvice advice)
      throws Throwable
   {
      if(Thread.currentThread() instanceof GroupedThread) {
         return advice.run();
      }

      final AtomicReference<Object> result = new AtomicReference<>(null);
      final AtomicReference<Throwable> thrown = new AtomicReference<>(null);
      final AtomicBoolean done = new AtomicBoolean(false);

      ThreadPool.AbstractContextRunnable runnable = new ThreadPool.AbstractContextRunnable() {
         @Override
         public void run() {
            try {
               result.set(advice.run());
            }
            catch(Throwable e) {
               thrown.set(e);
            }
            finally {
               synchronized(done) {
                  done.set(true);
                  done.notifyAll();
               }
            }
         }
      };

      for(Object arg : pjp.getArgs()) {
         if(arg instanceof XPrincipal) {
            runnable.setPrincipal((XPrincipal) arg);
         }
      }

      ThreadPool.addOnDemand(runnable);

      synchronized(done) {
         while(!done.get()) {
            done.wait();
         }
      }

      if(thrown.get() != null) {
         throw thrown.get();
      }

      return result.get();
   }

   @FunctionalInterface
   public interface GroupedThreadAdvice {
      Object run() throws Throwable;
   }
}
