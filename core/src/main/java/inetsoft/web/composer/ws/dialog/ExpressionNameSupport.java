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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.internal.AssetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The name rules a NEW worksheet expression column must satisfy, in one place so the Create
 * Expression dialog's suggested name, the agent-pairing mint check and
 * {@link ExpressionDialogService#setModel} (the OK path) cannot disagree.
 *
 * <p>A name is taken when it collides, case-insensitively, with the alias (or, when there is no
 * alias, the attribute) of any column of the table's column selection or of its bound source
 * -- the two checks the OK path applies.
 */
public final class ExpressionNameSupport {
   private ExpressionNameSupport() {
   }

   /** The stem of a suggested name: {@code Expression1}, {@code Expression2}, ... */
   public static final String BASE_NAME = "Expression";

   /**
    * True when {@code name} may not be used for a new expression column.
    *
    * @param tableColumns  the table's column selection
    * @param sourceColumns the bound source's columns, or {@code null} when there is none
    */
   public static boolean isTaken(String name, ColumnSelection tableColumns,
                                 ColumnSelection sourceColumns)
   {
      return AssetUtil.findColumnConflictingWithAlias(tableColumns, null, name, false) != null ||
         sourceColumns != null &&
            AssetUtil.findColumnConflictingWithAlias(sourceColumns, null, name, false) != null;
   }

   /** The first of {@code Expression1}, {@code Expression2}, ... that is not taken. */
   public static String suggest(ColumnSelection tableColumns, ColumnSelection sourceColumns) {
      for(int i = 1; ; i++) {
         String name = BASE_NAME + i;

         if(!isTaken(name, tableColumns, sourceColumns)) {
            return name;
         }
      }
   }

   /**
    * The columns of {@code table}'s bound source, or {@code null} when {@code table} is not bound
    * or they cannot be read. Failing to read them must not block opening the dialog or minting a
    * pairing; the OK path re-checks the same names and refuses a conflict there.
    */
   public static ColumnSelection sourceColumns(WorksheetService engine, RuntimeWorksheet rws,
                                               TableAssembly table)
   {
      if(engine == null || !(table instanceof BoundTableAssembly bound)) {
         return null;
      }

      try {
         SourceInfo sourceInfo = (SourceInfo) bound.getSourceInfo().clone();
         AssetEntry wsEntry = rws.getEntry();

         if(wsEntry != null) {
            sourceInfo.setProperty("fileName", wsEntry.getProperty("fileName"));
         }

         return AssetEventUtil.getAttributesBySource(engine, rws.getUser(), sourceInfo);
      }
      catch(Exception ex) {
         LOG.warn("Could not read the source columns of table {} for expression name check",
                  table.getName(), ex);
         return null;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ExpressionNameSupport.class);
}
