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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableScheduleConfigurationModel.class)
@JsonDeserialize(as = ImmutableScheduleConfigurationModel.class)
public abstract class ScheduleConfigurationModel {
   public abstract int concurrency();

   @Nullable
   public abstract String logFile();

   public abstract int rmiPort();

   public abstract String classpath();

   @Value.Default
   public String pathSeparator() {
      return String.valueOf(File.pathSeparatorChar);
   }

   public abstract boolean notificationEmail();

   public abstract boolean saveToDisk();

   public abstract boolean emailDelivery();

   public abstract boolean enableEmailBrowser();

   public abstract int maxMemory();

   public abstract int minMemory();

   @Value.Default
   public boolean isCluster() {
      return false;
   }

   public abstract String emailAddress();

   public abstract String emailSubject();

   public abstract String emailMessage();

   public abstract boolean notifyIfDown();

   public abstract boolean notifyIfTaskFailed();

   public abstract boolean shareTaskInSameGroup();

   public abstract boolean deleteTaskOnlyByOwner();

   public abstract List<TimeRangeModel> timeRanges();

   public abstract List<ServerLocation> serverLocations();

   @Nullable
   public abstract String saveAutoSuffix();

   @Value.Default
   public boolean securityEnable() {
      return false;
   }

   @Value.Default
   public boolean cloudSecrets() {
      return false;
   }

   public static ScheduleConfigurationModel.Builder builder() {
      return new ScheduleConfigurationModel.Builder();
   }

   public static class Builder extends ImmutableScheduleConfigurationModel.Builder {
   }
}
