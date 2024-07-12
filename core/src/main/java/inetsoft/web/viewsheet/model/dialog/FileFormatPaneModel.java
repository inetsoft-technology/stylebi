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
package inetsoft.web.viewsheet.model.dialog;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.web.portal.model.CSVConfigModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link FileFormatPaneModel} for the
 * export dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableFileFormatPaneModel.class)
@JsonDeserialize(as = ImmutableFileFormatPaneModel.class)
public abstract class FileFormatPaneModel {
   @Value.Default
   public int formatType() {
      return FileFormatInfo.EXPORT_TYPE_EXCEL;
   }

   @Value.Default
   public boolean expandSelections() {
      return false;
   }

   @Value.Default
   public boolean includeCurrent() {
      return true;
   }

   @Nullable
   public abstract Boolean matchLayout();

   @Nullable
   public abstract Boolean linkVisible();

   @Nullable
   public abstract Boolean sendLink();

   public abstract boolean expandEnabled();

   public abstract boolean hasPrintLayout();

   @Value.Default
   public String[] selectedBookmarks() {
      return new String[0];
   }

   @Nullable
   public abstract String[] allBookmarks();

   @Value.Default
   public boolean onlyDataComponents() {
      return false;
   }

   @Nullable
   public abstract CSVConfigModel csvConfig();

   @Value.Default
   public boolean exportAllTabbedTables() {
      return false;
   }

   @Nullable
   public abstract String[] tableDataAssemblies();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableFileFormatPaneModel.Builder {
   }
}
