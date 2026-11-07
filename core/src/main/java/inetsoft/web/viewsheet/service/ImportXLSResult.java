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

import java.util.List;
import java.util.Set;

/**
 * Per-table outcome of {@link ImportXLSService#importWorkbook}. The Viewer flow only gets this as
 * messages through the dispatcher; the wiz agent endpoint needs the counts.
 *
 * @param tables     one entry per table assembly the import looked at
 * @param notFound   the names of the visible table assemblies the file has no named range for, in
 *                   assembly order. Only tables are ever listed: the importer's existence check
 *                   treats every other kind of assembly as present.
 * @param notInRange the tables skipped because a cell of the file breaks a column option
 */
public record ImportXLSResult(List<TableResult> tables, List<String> notFound,
                              Set<String> notInRange)
{
   public enum Outcome {
      /** The table's rows were replaced by the file's. */
      IMPORTED,
      /** A cell of the file breaks a column option; the table was left as it was. */
      NOT_IN_RANGE,
      /** A Form table the file has no named range for; left as it was. */
      NOT_IN_FILE,
      /** The file has named ranges for it but the runtime has no Form table lens for it. */
      NOT_A_FORM_TABLE,
      /** A Form table that is not visible; the importer skips every invisible assembly. */
      SKIPPED_INVISIBLE
   }

   /**
    * @param added     rows the file has that are new
    * @param changed   rows whose cells differ from the backup
    * @param unchanged rows equal to their backup
    * @param deleted   rows in the backup that the file no longer has
    * @param rowCount  data rows the table holds after the import (deleted rows excluded)
    */
   public record TableResult(String assembly, Outcome outcome, int added, int changed,
                             int unchanged, int deleted, int rowCount)
   {
      public static TableResult of(String assembly, Outcome outcome) {
         return new TableResult(assembly, outcome, 0, 0, 0, 0, 0);
      }
   }
}
