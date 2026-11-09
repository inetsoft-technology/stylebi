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
package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.report.filter.DefaultTableFilter;

import java.util.Set;

/**
 * A pass-through view of a table lens that records the header text of the columns an export
 * actually writes, so a cell cap warning can skip columns the export leaves out (hidden columns
 * or columns clipped by the match-layout width). Bug #80500.
 */
public class ExportedColumnsTableLens extends DefaultTableFilter {
   public ExportedColumnsTableLens(TableLens table, Set<String> exportedHeaders) {
      super(table);
      this.exportedHeaders = exportedHeaders;
   }

   /**
    * Gets the header text of the exported columns.
    */
   public Set<String> getExportedHeaders() {
      return exportedHeaders;
   }

   private final Set<String> exportedHeaders;
}
