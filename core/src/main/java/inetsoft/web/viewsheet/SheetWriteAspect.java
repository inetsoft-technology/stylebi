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

package inetsoft.web.viewsheet;

import inetsoft.report.composition.WorksheetService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Serializes the RuntimeSheet to the distributed Ignite cache after genuine mutation methods.
 * Only fires on methods annotated with {@link ClusterWriteMethod}, keeping read-only paths at
 * zero serialization overhead.
 */
@Component
@Aspect
public class SheetWriteAspect {
   public SheetWriteAspect(WorksheetService worksheetService) {
      this.worksheetService = worksheetService;
   }

   @AfterReturning("@annotation(inetsoft.cluster.ClusterWriteMethod)")
   public void writeSheet(JoinPoint joinPoint) {
      Object[] args = joinPoint.getArgs();

      if(args.length > 0 && args[0] instanceof String id) {
         worksheetService.writeSheet(id);
      }
      else if(args.length > 0) {
         LOG.warn("@ClusterWriteMethod on {} has a non-String first argument {}; " +
                  "distributed cache write skipped. The annotated method must have " +
                  "@ClusterProxyKey String as its first parameter.",
                  joinPoint.getSignature(), args[0] == null ? "null" : args[0].getClass().getName());
      }
   }

   private final WorksheetService worksheetService;
   private static final Logger LOG = LoggerFactory.getLogger(SheetWriteAspect.class);
}
