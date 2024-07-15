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
import inetsoft.web.admin.content.repository.model.ExportFormatModel;
import org.immutables.value.Value;

import java.util.*;

/**
 * Data transfer object that represents the {@link TaskActionPaneModel} for the
 * image property dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableTaskActionPaneModel.class)
@JsonDeserialize(as = ImmutableTaskActionPaneModel.class)
public interface TaskActionPaneModel {
   boolean emailButtonVisible();
   boolean notificationEmailEnabled();
   boolean saveToDiskEnabled();
   boolean emailDeliveryEnabled();
   boolean cvsEnabled();
   boolean securityEnabled();
   boolean administrator();
   String defaultFromEmail();
   boolean fromEmailEnabled();
   boolean viewsheetEnabled();
   boolean expandEnabled();

   @Value.Default
   default boolean adminVisible() {
      return false;
   }

   List<ScheduleActionModel> actions();
   List<String> userDefinedClasses();
   List<String> userDefinedClassLabels();

   @Value.Default
   default Map<String, String> dashboardMap() {
      return new HashMap<>();
   }
   List<String> folderPaths();
   List<String> folderLabels();
   List<ExportFormatModel> mailFormats();
   List<ExportFormatModel> vsMailFormats();
   List<ExportFormatModel> saveFileFormats();
   List<ExportFormatModel> vsSaveFileFormats();
   List<ServerLocation> serverLocations();

   @Value.Default
   default boolean mailHistoryEnabled() {
      return false;
   }

   @Value.Default
   default boolean fipsMode() {
      return false;
   }

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableTaskActionPaneModel.Builder {
   }
}