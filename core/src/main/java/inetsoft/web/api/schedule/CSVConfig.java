/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.api.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * CSVConfig contains configuration properties for a csv export.
 */
@Schema(description = "The configuration properties for a csv export.")
public class CSVConfig {

   public CSVConfig() {

   }

   public CSVConfig(inetsoft.report.io.csv.CSVConfig config) {
      this.delimiter = config.getDelimiter();
      this.quote = config.getQuote();
      this.keepHeader = config.isKeepHeader();
      this.tabDelimited = config.isTabDelimited();
      this.selectedAssemblies = config.getExportAssemblies();
   }

   /**
    * Gets the delimiter.
    *
    * @return the delimiter.
    */
   @NotNull
   @Schema(description = "The delimiter.", example = ",")
   public String getDelimiter() {
      return delimiter;
   }

   /**
    * Sets the delimiter.
    *
    * @param delimiter the delimiter.
    */
   public void setDelimiter(String delimiter) {
      this.delimiter = delimiter;
   }

   /**
    * Gets the quote string.
    *
    * @return the quote string.
    */
   @Schema(description = "The quote string.")
   public String getQuote() {
      return quote;
   }

   /**
    * Sets the quote string.
    *
    * @param quote the quote string.
    */
   public void setQuote(String quote) {
      this.quote = quote;
   }

   /**
    * Check if the header should be kept
    */
   @Schema(description = "The flag indicating that the header should be kept.", example = "true")
   public boolean isKeepHeader() {
      return keepHeader;
   }

   /**
    * Set if the header should be kept
    *
    * @param keepHeader the flag.
    */
   public void setKeepHeader(boolean keepHeader) {
      this.keepHeader = keepHeader;
   }

   /**
    * Check if the file should be tab delimited
    */
   @Schema(description = "The flag indicating that the file should be tab delimited.", example = "false")
   public boolean isTabDelimited() {
      return tabDelimited;
   }

   /**
    * Set if the file should be tab delimited
    *
    * @param tabDelimited the flag.
    */
   public void setTabDelimited(boolean tabDelimited) {
      this.tabDelimited = tabDelimited;
   }

   @Schema(description = "Select the table to export.", example = "null")
   public List<String> getSelectedAssemblies() {
      return selectedAssemblies;
   }

   public void setSelectedAssemblies(List<String> selectedAssemblies) {
      this.selectedAssemblies = selectedAssemblies;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      CSVConfig that = (CSVConfig) o;
      return Objects.equals(delimiter, that.delimiter) &&
         Objects.equals(quote, that.quote) &&
         Objects.equals(selectedAssemblies, that.selectedAssemblies) &&
         keepHeader == that.keepHeader &&
         tabDelimited == that.tabDelimited;
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), delimiter, quote, keepHeader, tabDelimited, selectedAssemblies);
   }

   @Override
   public String toString() {
      return "CSVConfig{" +
         "delimiter='" + delimiter + '\'' +
         ", quote='" + quote + '\'' +
         ", keepHeader=" + keepHeader +
         ", tabDelimited=" + tabDelimited +
         ", selectedAssemblies=" + selectedAssemblies +
         '}';
   }

   private String delimiter = ",";
   private String quote = null;
   private boolean keepHeader = true;
   private boolean tabDelimited = false;
   private List<String> selectedAssemblies;
}
