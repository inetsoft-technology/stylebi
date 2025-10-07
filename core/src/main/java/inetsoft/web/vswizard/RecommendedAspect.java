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
package inetsoft.web.vswizard;

import inetsoft.util.Tool;
import inetsoft.web.ServiceProxyContext;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Date;

@Component
@Aspect
public class RecommendedAspect {
   public RecommendedAspect(RuntimeViewsheetRef runtimeViewsheetRef) {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Before("@annotation(org.springframework.messaging.handler.annotation.MessageMapping) " +
      "&& within(inetsoft.web.vswizard..*)")
   public void clearUserMessage(JoinPoint joinPoint) {
      // clear the user message, the current thread may be get the message of another old thread.
      // because Spring has thread pool so the thread may be reused.
      Tool.getUserMessage();
   }

   @Around("@annotation(Recommend) && within(inetsoft.web.vswizard..*)")
   public Object updateLatestTime(ProceedingJoinPoint pjp) throws Throwable {
      Object[] args = pjp.getArgs();
      Principal principal = null;

      for(Object arg : args) {
         if(arg instanceof Principal) {
            principal = (Principal) arg;
            break;
         }
      }

      String runtimeId = runtimeViewsheetRef.getRuntimeId();

      if(principal != null && runtimeId != null) {
         ServiceProxyContext.recommendedIdThreadLocal.set(runtimeId);
         ServiceProxyContext.recommendedStartTimeThreadLocal.set(new Date());
      }

      try {
         return pjp.proceed();
      }
      finally {
         ServiceProxyContext.recommendedIdThreadLocal.remove();
         ServiceProxyContext.recommendedStartTimeThreadLocal.remove();
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
