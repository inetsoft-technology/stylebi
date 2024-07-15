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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.composer.ws.event.AssetEvent;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Value.Immutable

@JsonSerialize(as = ImmutableImportCSVDialogModel.class)
@JsonDeserialize(as = ImmutableImportCSVDialogModel.class)
public abstract class ImportCSVDialogModel implements AssetEvent {
   @Nullable
   public abstract String tableName();

   @Nullable
   public abstract String newTableName();

   @Value.Default
   public String encodingSelected() {
      return "";
   }

   @Nullable
   public abstract String[] sheetsList();

   @Nullable
   @Value.Default
   public String sheetSelected() {
      return "";
   }

   public abstract boolean unpivotCB();

   @Value.Default
   public int headerCols() {
      return 1;
   }

   public abstract boolean firstRowCB();

   public abstract char delimiter();

   @Value.Default
   public boolean delimiterTab() {
      return false;
   }

   @Value.Default
   public boolean detectType() {
      return true;
   }

   @Value.Default
   @Override
   public boolean confirmed() {
      return false;
   }

   @Nullable
   public abstract String fileType();

   @Nullable
   public abstract List<Integer> ignoreTypeColumns();

   public abstract boolean removeQuotesCB();

   @Nullable
   public abstract Map<Integer, String> headerNames();

   @Value.Default
   public boolean mashUpData() {
      return false;
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableImportCSVDialogModel.Builder {
   }
}
