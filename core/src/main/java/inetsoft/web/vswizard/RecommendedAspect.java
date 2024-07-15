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
package inetsoft.web.vswizard;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Date;

@Component
@Aspect
public class RecommendedAspect {
   public RecommendedAspect(VSWizardTemporaryInfoService temporaryInfoService,
                            RuntimeViewsheetRef runtimeViewsheetRef,
                            ViewsheetService viewsheetService)
   {
      this.temporaryInfoService = temporaryInfoService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
   }

   @Before("@annotation(org.springframework.messaging.handler.annotation.MessageMapping) " +
      "&& within(inetsoft.web.vswizard..*)")
   public void clearUserMessage(JoinPoint joinPoint) {
      // clear the user message, the current thread may be get the message of another old thread.
      // because Spring has thread pool so the thread may be reused.
      Tool.getUserMessage();
   }

   @Before("@annotation(Recommend) && within(inetsoft.web.vswizard..*)")
   public void updateLatestTime(JoinPoint joinPoint) throws Throwable {
      Date recommendTime = new Date();
      Object[] args = joinPoint.getArgs();
      Principal principal = null;

      for(Object arg : args) {
         if(arg instanceof Principal) {
            principal = (Principal) arg;
            break;
         }
      }

      if(principal == null) {
         return;
      }

      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);

      if(rvs == null) {
         return;
      }

      VSTemporaryInfo temporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

      if(temporaryInfo != null) {
         Date oldTime = temporaryInfo.getRecommendLatestTime();

         if(oldTime != null) {
            temporaryInfo.setRecommendLatestTime(recommendTime.after(oldTime) ?
               recommendTime : oldTime);
         }
         else {
            temporaryInfo.setRecommendLatestTime(recommendTime);
         }
      }

      RecommendSequentialContext.setStartTime(recommendTime);
   }

   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
}
