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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import inetsoft.web.AspectTask;
import inetsoft.web.ServiceProxyContext;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
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
      RecommendedAspectTask task = null;

      if(principal != null && runtimeId != null) {
         task = new RecommendedAspectTask(runtimeId, new Date());
         ServiceProxyContext.aspectTasks.get().add(task);
      }

      try {
         return pjp.proceed();
      }
      finally {
         if(task != null) {
            ServiceProxyContext.aspectTasks.get().remove(task);
         }
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;

   public static final class RecommendedAspectTask implements AspectTask {
      public RecommendedAspectTask(String id, Date startTime) {
         this.id = id;
         this.startTime = startTime;
      }

      @Override
      public void preprocess(CommandDispatcher dispatcher, Principal contextPrincipal) {
         try {
            ViewsheetService viewsheetService = ConfigurationContext.getContext()
               .getSpringBean(ViewsheetService.class);
            RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, contextPrincipal);

            if(rvs != null) {
               VSWizardTemporaryInfoService temporaryInfoService =
                  ConfigurationContext.getContext().getSpringBean(VSWizardTemporaryInfoService.class);
               VSTemporaryInfo temporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

               if(temporaryInfo != null) {
                  Date oldTime = temporaryInfo.getRecommendLatestTime();

                  if(oldTime != null) {
                     temporaryInfo.setRecommendLatestTime(startTime.after(oldTime) ?
                                                             startTime : oldTime);
                  }
                  else {
                     temporaryInfo.setRecommendLatestTime(startTime);
                  }
               }

               RecommendSequentialContext.setStartTime(startTime);
            }
         }
         catch(RuntimeException e) {
            throw e;
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to set recommender context", e);
         }
      }

      private final String id;
      private final Date startTime;
   }
}
