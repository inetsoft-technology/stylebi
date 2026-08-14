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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/viewsheet/export}, replacing what used to be 13
 * individual {@code @RequestParam}-annotated primitives on the controller method — matches the
 * established {@code inetsoft.web.wiz.model.*Request} convention used elsewhere in this package
 * (see {@link GeoApplyRequest}, {@link ChartFormatRequest}).
 *
 * <p>The field initializers below reproduce the {@code defaultValue} each replaced
 * {@code @RequestParam} used to declare: Jackson only overwrites a property when the
 * corresponding JSON key is actually present, so an absent field keeps the value initialized
 * here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportViewsheetRequest {
   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public int getFormat() {
      return format;
   }

   public void setFormat(int format) {
      this.format = format;
   }

   public boolean isMatch() {
      return match;
   }

   public void setMatch(boolean match) {
      this.match = match;
   }

   public boolean isExpandSelections() {
      return expandSelections;
   }

   public void setExpandSelections(boolean expandSelections) {
      this.expandSelections = expandSelections;
   }

   public boolean isCurrent() {
      return current;
   }

   public void setCurrent(boolean current) {
      this.current = current;
   }

   public String getBookmarks() {
      return bookmarks;
   }

   public void setBookmarks(String bookmarks) {
      this.bookmarks = bookmarks;
   }

   public boolean isOnlyDataComponents() {
      return onlyDataComponents;
   }

   public void setOnlyDataComponents(boolean onlyDataComponents) {
      this.onlyDataComponents = onlyDataComponents;
   }

   public boolean isExportAllTabbedTables() {
      return exportAllTabbedTables;
   }

   public void setExportAllTabbedTables(boolean exportAllTabbedTables) {
      this.exportAllTabbedTables = exportAllTabbedTables;
   }

   public String getDelimiter() {
      return delimiter;
   }

   public void setDelimiter(String delimiter) {
      this.delimiter = delimiter;
   }

   public String getQuote() {
      return quote;
   }

   public void setQuote(String quote) {
      this.quote = quote;
   }

   public boolean isKeepHeader() {
      return keepHeader;
   }

   public void setKeepHeader(boolean keepHeader) {
      this.keepHeader = keepHeader;
   }

   public boolean isTabDelimited() {
      return tabDelimited;
   }

   public void setTabDelimited(boolean tabDelimited) {
      this.tabDelimited = tabDelimited;
   }

   public String getTableAssemblies() {
      return tableAssemblies;
   }

   public void setTableAssemblies(String tableAssemblies) {
      this.tableAssemblies = tableAssemblies;
   }

   private String runtimeId;
   private int format;
   private boolean match = true;
   private boolean expandSelections = false;
   private boolean current = true;
   private String bookmarks = "";
   private boolean onlyDataComponents = false;
   private boolean exportAllTabbedTables = false;
   private String delimiter;
   private String quote;
   private boolean keepHeader = true;
   private boolean tabDelimited = false;
   private String tableAssemblies = "";
}
