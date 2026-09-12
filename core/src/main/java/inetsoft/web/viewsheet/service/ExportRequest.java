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
package inetsoft.web.viewsheet.service;

import inetsoft.report.io.csv.CSVConfig;

import java.security.Principal;
import java.util.Map;

/**
 * Value object that bundles all parameters for a path-based viewsheet export request.
 * Using this object instead of a long parameter list makes the private {@code doExport}
 * method easier to unit-test: a test subclass only needs to construct one object rather
 * than matching a 17-parameter signature.
 */
class ExportRequest {
   final String path;
   final int format;
   final boolean matchLayout;
   final boolean expandSelections;
   final boolean current;
   final boolean previewPrintLayout;
   final boolean print;
   final String[] bookmarks;
   final String type;
   final boolean onlyDataComponents;
   final CSVConfig csvConfig;
   final boolean exportAllTabbedTables;
   final ExportResponse response;
   final Map<String, String[]> parameters;
   final String sessionId;
   final String userAgent;
   final Principal principal;

   ExportRequest(String path, int format, boolean matchLayout, boolean expandSelections,
                 boolean current, boolean previewPrintLayout, boolean print, String[] bookmarks,
                 String type, boolean onlyDataComponents, CSVConfig csvConfig,
                 boolean exportAllTabbedTables, ExportResponse response,
                 Map<String, String[]> parameters, String sessionId, String userAgent,
                 Principal principal)
   {
      this.path = path;
      this.format = format;
      this.matchLayout = matchLayout;
      this.expandSelections = expandSelections;
      this.current = current;
      this.previewPrintLayout = previewPrintLayout;
      this.print = print;
      this.bookmarks = bookmarks;
      this.type = type;
      this.onlyDataComponents = onlyDataComponents;
      this.csvConfig = csvConfig;
      this.exportAllTabbedTables = exportAllTabbedTables;
      this.response = response;
      this.parameters = parameters;
      this.sessionId = sessionId;
      this.userAgent = userAgent;
      this.principal = principal;
   }
}
