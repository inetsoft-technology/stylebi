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
package inetsoft.web.admin.query;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource
public class QueryManagerMBean {
   private final QueryService queryService;

   @Autowired()
   QueryManagerMBean(QueryService queryService) {
      this.queryService = queryService;
   }

   @ManagedAttribute(description="Composer Data Max Rows of the manager")
   public int getComposerDataMaxRows() {
      return queryService.getComposerDataMaxRows();
   }

   @ManagedAttribute
   public void setComposerDataMaxRows(int maxRows) throws Exception {
      queryService.setComposerDataMaxRows(maxRows);
   }

   @ManagedAttribute(description="Composer Data Timeout of the manager")
   public long getComposerDataTimeout() {
      return queryService.getComposerDataTimeout();
   }

   @ManagedAttribute
   public void setComposerDataTimeout(long timeout) throws Exception {
      queryService.setComposerDataTimeout(timeout);
   }
}
