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
package inetsoft.web.admin.viewsheet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Date;

/**
 * Data transfer object that represents the {@link ViewsheetMonitoringTableModel} for the
 * Viewsheet Monitoring page
 */
@Value.Immutable
@JsonSerialize(as = ImmutableViewsheetMonitoringTableModel.class)
@JsonDeserialize(as = ImmutableViewsheetMonitoringTableModel.class)
public interface ViewsheetMonitoringTableModel {
   String id();
   @Nullable String name();
   String user();
   String age();
   @Nullable String thread();
   @Nullable String dateAccessed();
   @Nullable String task();

   static ViewsheetMonitoringTableModel.Builder builder() {
      return new ViewsheetMonitoringTableModel.Builder();
   }

   final class Builder extends ImmutableViewsheetMonitoringTableModel.Builder {
      public Builder from(ViewsheetModel viewsheet) {
         id(viewsheet.id());
         name(viewsheet.name());
         user(viewsheet.monitorUser() != null ? viewsheet.monitorUser().getName() : "");
         age(Util.formatAge(new Date(viewsheet.dateCreated()), false));
         dateAccessed(Util.formatAge(new Date(viewsheet.dateAccessed()), false));
         String taskName = viewsheet.task();

         if(taskName != null && taskName.indexOf(":") > 0) {
            String identity = taskName.substring(0, taskName.indexOf(":"));
            taskName = taskName.substring(taskName.indexOf(":") + 1);
            taskName = IdentityID.getIdentityIDFromKey(identity).name + ":" + taskName;
         }

         task(taskName);
         return this;
      }
   }
}
