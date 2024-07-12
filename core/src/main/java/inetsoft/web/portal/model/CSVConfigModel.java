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
package inetsoft.web.portal.model;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.io.csv.CSVConfig;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Data transfer object that represents the {@link CSVConfigModel} for the
 * schedule dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableCSVConfigModel.class)
@JsonDeserialize(as = ImmutableCSVConfigModel.class)
public abstract class CSVConfigModel {
   @Value.Default
   public String delimiter() {
      return ",";
   }

   @Nullable
   public abstract String quote();

   @Value.Default
   public boolean keepHeader() {
      return true;
   }

   @Nullable
   public abstract Boolean tabDelimited();

   @Nullable
   public abstract List<String> selectedAssemblies();

   public static CSVConfigModel.Builder builder() {
      return new CSVConfigModel.Builder();
   }

   public static class Builder extends ImmutableCSVConfigModel.Builder {
      public Builder from(CSVConfig config) {
         if(config != null) {
            String quote = config.getQuote();
            delimiter(config.getDelimiter());
            quote(quote == null ? "" : quote);
            keepHeader(config.isKeepHeader());
            tabDelimited(config.isTabDelimited());

            if(config.getExportAssemblies() != null) {
               selectedAssemblies(config.getExportAssemblies());
            }
         }

         return this;
      }
   }
}
