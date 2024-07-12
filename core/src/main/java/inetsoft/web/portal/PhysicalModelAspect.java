/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.portal;

import inetsoft.web.portal.controller.database.RuntimePartitionService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class PhysicalModelAspect {
   public PhysicalModelAspect(RuntimePartitionService runtimePartitionService) {
      this.runtimePartitionService = runtimePartitionService;
   }

   @Before("(@annotation(org.springframework.web.bind.annotation.RequestMapping)" +
      " || @annotation(org.springframework.web.bind.annotation.GetMapping)" +
      " || @annotation(org.springframework.web.bind.annotation.PostMapping))" +
      " || @annotation(org.springframework.web.bind.annotation.DeleteMapping))" +
      " || @annotation(org.springframework.web.bind.annotation.PutMapping))" +
      " && within(inetsoft.web.portal.controller.database.*)")
   public void checkTimeout() {
      runtimePartitionService.checkTimeout();
   }

   private final RuntimePartitionService runtimePartitionService;
}
