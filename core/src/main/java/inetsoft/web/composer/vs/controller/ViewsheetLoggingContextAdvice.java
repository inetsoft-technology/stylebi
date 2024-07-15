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
package inetsoft.web.composer.vs.controller;

import inetsoft.util.GroupedThread;
import inetsoft.util.log.LogContext;
import inetsoft.web.GroupedThreadAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

@Component
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ViewsheetLoggingContextAdvice {
   @Around("execution(@org.springframework.web.bind.annotation.* * *(..)) && within(inetsoft.web.composer.vs.controller..*)")
   public Object setLoggingContext(ProceedingJoinPoint pjp) throws Throwable {
      return GroupedThreadAspect.withGroupedThread(pjp, () -> doSetLoggingContext(pjp));
   }

   private Object doSetLoggingContext(ProceedingJoinPoint pjp) throws Throwable {
      GroupedThread thread = (GroupedThread) Thread.currentThread();
      MethodSignature signature = (MethodSignature) pjp.getSignature();
      Annotation[][] annotations = signature.getMethod().getParameterAnnotations();
      Object[] args = pjp.getArgs();

      for(int i = 0; i < annotations.length; i++) {
         boolean pathFound = false;

         for(int j = 0; j < annotations[i].length; j++) {
            if(annotations[i][j] instanceof ViewsheetPath) {
               String path = String.valueOf(args[i]);
               pathFound = true;
               thread.addRecord(LogContext.DASHBOARD, path);
               break;
            }
         }

         if(pathFound) {
            break;
         }
      }

      try {
         return pjp.proceed();
      }
      finally {
         thread.removeRecords(LogContext.DASHBOARD);
      }
   }
}
