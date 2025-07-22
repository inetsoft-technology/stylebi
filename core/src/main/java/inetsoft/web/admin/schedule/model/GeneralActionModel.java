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
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
/**
 * Data transfer object that represents the {@link GeneralActionModel} for the
 * schedule dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableGeneralActionModel.class)
@JsonDeserialize(as = ImmutableGeneralActionModel.class)
public abstract class GeneralActionModel extends ScheduleActionModel {
   @Nullable
   public abstract Boolean notificationEnabled();

   @Nullable
   public abstract Boolean deliverEmailsEnabled();

   @Nullable
   public abstract Boolean printOnServerEnabled();

   @Nullable
   public abstract Boolean saveToServerEnabled();

   @Nullable
   public abstract String sheet();

   @Nullable
   public abstract List<VSBookmarkInfoModel> bookmarks();

   @Nullable
   public abstract String notifications();

   @Nullable
   public abstract Boolean notifyIfFailed();

   @Nullable
   public abstract String fromEmail();

   @Nullable
   public abstract String to();

   @Nullable
   public abstract String subject();

   @Nullable
   public abstract String format();

   @Nullable
   public abstract CSVConfigModel csvExportModel();

   @Nullable
   public abstract CSVConfigModel csvSaveModel();

   @Nullable
   public abstract Boolean bundledAsZip();

   @Nullable
   public abstract Boolean useCredential();

   @Nullable
   public abstract String secretId();

   @Nullable
   public abstract String password();

   @Nullable
   public abstract String attachmentName();

   @Nullable
   public abstract Boolean htmlMessage();

   @Nullable
   public abstract String message();

   @Nullable
   public abstract Boolean forceToRegenerateReport();

   @Nullable
   public abstract Boolean link();

   @Nullable
   public abstract Boolean deliverLink();

   @Nullable
   public abstract String[] printers();

   @Nullable
   public abstract Boolean highlightsSelected();

   @Nullable
   public abstract List<String> highlightAssemblies();

   @Nullable
   public abstract List<String> highlightNames();

   @Value.Default
   public List<String> filePaths() {
      return new ArrayList<>();
   }

   @Value.Default
   public List<ServerPathInfoModel> serverFilePaths() {
      return new ArrayList<>();
   }

   @Value.Default
   public String[] saveFormats() {
      return new String[0];
   }

   @Nullable
   public abstract Boolean emailMatchLayout();

   @Nullable
   public abstract Boolean emailExpandSelections();

   @Value.Default
   public Boolean exportAllTabbedTables() {
      return false;
   }

   @Nullable
   public abstract Boolean emailOnlyDataComponents();

   @Nullable
   public abstract Boolean saveMatchLayout();

   @Nullable
   public abstract Boolean saveExpandSelections();
   
   @Nullable
   public abstract Boolean saveOnlyDataComponents();

   @Value.Default
   public Boolean saveExportAllTabbedTables() {
      return false;
   }

   @Value.Default
   public List<AddParameterDialogModel> parameters() {
      return new ArrayList<>();
   }

   @Nullable
   public abstract Boolean folderPermission();

   @Nullable
   public abstract String ccAddress();

   @Nullable
   public abstract String bccAddress();

   @Nullable
   public abstract String sheetAlias();

   public static GeneralActionModel.Builder builder() {
      return new GeneralActionModel.Builder();
   }

   public static class Builder extends ImmutableGeneralActionModel.Builder {
   }
}
