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
package inetsoft.web.viewsheet.model.dialog.schedule;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.data.CommonKVModel;
import inetsoft.web.admin.schedule.model.TimeZoneModel;
import inetsoft.web.viewsheet.model.dialog.EmailAddrDialogModel;
import inetsoft.web.viewsheet.model.dialog.ImmutableEmailAddrDialogModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object that represents the model for the schedule dialog.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableSimpleScheduleDialogModel.class)
@JsonDeserialize(as = ImmutableSimpleScheduleDialogModel.class)
public abstract class SimpleScheduleDialogModel {
   @Nullable
   public abstract Boolean userDialogEnabled();

   @Nullable
   public abstract String timeProp();

   @Nullable
   public abstract String taskName();

   @Nullable
   public abstract Boolean isSecurity();

   @Nullable
   public abstract Boolean expandEnabled();

   @Nullable
   public abstract Boolean emailButtonVisible();

   @Value.Default
   public Boolean emailDeliveryEnabled() {
      return true;
   };

   public abstract List<TimeRangeModel> timeRanges();

   @Nullable
   public abstract List<TimeZoneModel> timeZoneOptions();

   public abstract boolean startTimeEnabled();

   public abstract boolean timeRangeEnabled();

   @Value.Default
   public boolean twelveHourSystem() {
      return false;
   }

   @Value.Default
   public List<CommonKVModel> formatTypes() {
      return new ArrayList<>();
   }

   @Value.Default
   public TimeConditionModel timeConditionModel() {
      return TimeConditionModel.builder().build();
   }

   @Value.Default
   public ActionModel actionModel() {
      return ImmutableViewsheetActionModel.builder().build();
   }

   @Value.Default
   public EmailAddrDialogModel emailAddrDialogModel() {
      return ImmutableEmailAddrDialogModel.builder().build();
   }

   @Value.Default
   public List<String> users() {
      return new ArrayList<>(0);
   }

   @Value.Default
   public List<String> groups() {
      return new ArrayList<>(0);
   }

   @Value.Default
   public List<String> emailGroups() {
      return new ArrayList<>(0);
   }

   @Nullable
   public abstract String[] tableAssemblies();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableSimpleScheduleDialogModel.Builder {
   }
}
