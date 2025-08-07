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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableViewsheetModel.class)
@JsonDeserialize(as = ImmutableViewsheetModel.class)
public interface ViewsheetModel extends Serializable {
   String id();
   State state();
   @Nullable String name();
   IdentityID user();
   IdentityID monitorUser();
   @Nullable String task();
   long dateCreated();
   long dateAccessed();
   List<ViewsheetThreadModel> threads();

   static Builder builder() {
      return new Builder();
   }

   enum State {
      EXECUTING, OPEN
   }

   final class Builder extends ImmutableViewsheetModel.Builder {
      public Builder from(RuntimeViewsheet rvs) {
         Principal principal = rvs.getUser();
         IdentityID user;

         if(principal == null) {
            principal = ThreadContext.getPrincipal();
         }

         if(principal instanceof XPrincipal) {
            task(((XPrincipal) principal).getProperty("__TASK_NAME__"));
            user = IdentityID.getIdentityIDFromKey(principal.getName());
         }
         else {
            task(null);
            user = new IdentityID(XPrincipal.SYSTEM, rvs.getEntry().getOrgID());
         }

         id(rvs.getID());
         user(user);
         monitorUser(user);
         dateCreated(rvs.getDateCreated());
         dateAccessed(rvs.getLastAccessed());
         String sheet = null;

         if(SUtil.isDefaultVSGloballyVisible() &&
            !Tool.equals(user.getOrgID(), OrganizationManager.getInstance().getCurrentOrgID()) &&
            Tool.equals(user.getOrgID(), Organization.getDefaultOrganizationID()))
         {
            sheet = rvs.getEntry().getSheetName(true);
         }
         else {
            sheet = rvs.getEntry().getSheetName();
         }

         String prefix = "Viewsheet: ";
         int idx = sheet == null ? -1 : sheet.indexOf(prefix);

         if(idx == 0) {
            sheet = sheet.substring(prefix.length());
         }

         name(sheet);
         return this;
      }
   }
}
