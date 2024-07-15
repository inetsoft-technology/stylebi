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
package inetsoft.web.viewsheet.model.dialog.schedule;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.web.portal.model.CSVConfigModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link EmailInfoModel} for the
 * schedule dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableEmailInfoModel.class)
@JsonDeserialize(as = ImmutableEmailInfoModel.class)
public abstract class EmailInfoModel {
   @Nullable
   public abstract String emails();

   @Nullable
   public abstract String fromAddress();

   @Value.Default
   public String formatStr() {
      return "";
   }

   @Value.Default
   public int formatType() {
      return FileFormatInfo.EXPORT_TYPE_EXCEL;
   }

   @Nullable
   public abstract String attachmentName();

   @Nullable
   public abstract String subject();

   @Nullable
   public abstract String message();

   @Value.Default
   public boolean matchLayout() {
      return true;
   }

   @Value.Default
   public boolean expandSelections() {
      return false;
   }
   
   @Value.Default
   public boolean onlyDataComponents() {
      return false;
   }

   @Nullable
   public abstract String ccAddresses();

   @Nullable
   public abstract String bccAddresses();

   @Value.Default
   public boolean exportAllTabbedTables() {
      return false;
   }

   @Value.Default
   public CSVConfigModel csvConfigModel() {
      return CSVConfigModel.builder().build();
   }

   public static EmailInfoModel.Builder builder() {
      return new EmailInfoModel.Builder();
   }

   public static class Builder extends ImmutableEmailInfoModel.Builder {
   }
}
