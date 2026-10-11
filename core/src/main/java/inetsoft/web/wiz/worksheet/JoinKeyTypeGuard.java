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
package inetsoft.web.wiz.worksheet;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.WorksheetControllerService.JoinKeyTypeConflict;
import inetsoft.web.wiz.pairing.PairingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bug #78258 / #78346: the join-key type guard shared by every entry point that can change the
 * type of a column a join is keyed on. The detection itself is
 * {@link WorksheetControllerService#findJoinKeyTypeConflicts}; this class owns the refusal text
 * and the helpers that turn a "before/after" description of a retype into a refusal. Only a
 * mergeable-to-unmergeable transition is ever reported, and there is deliberately no override.
 */
public final class JoinKeyTypeGuard {
   private JoinKeyTypeGuard() {
   }

   /** One column whose type changes in a way that breaks at least one join. */
   public record Break(String column, String oldType, String newType,
                       List<JoinKeyTypeConflict> conflicts)
   {
   }

   /** The text shared by every refusal: which joins break and why. */
   public static String breakClause(String newType, List<JoinKeyTypeConflict> conflicts) {
      StringBuilder msg = new StringBuilder("it would break ")
         .append(conflicts.size() == 1 ? "a join" : "joins").append(" -- ");

      for(int i = 0; i < conflicts.size(); i++) {
         JoinKeyTypeConflict c = conflicts.get(i);

         if(i > 0) {
            msg.append("; ");
         }

         msg.append("join \"").append(c.join()).append("\" keys it against \"")
            .append(c.otherTable()).append("\".\"").append(c.otherKey()).append("\" (")
            .append(c.otherType()).append(")");
      }

      return msg.append(", and a join between ").append(newType)
         .append(" and those key types returns no rows.").toString();
   }

   /** The refusal for an explicit retype of one column (change_column_type, edit_expression). */
   public static String describe(String table, String col, String oldType, String newType,
                                 List<JoinKeyTypeConflict> conflicts)
   {
      return "Cannot change the type of \"" + col + "\" on \"" + table + "\" from " + oldType +
         " to " + newType + ": " + breakClause(newType, conflicts) +
         " Remove or edit the join first.";
   }

   /** Refuses an explicit retype of one column that would break a join. */
   public static void requireNoBreak(Worksheet ws, TableAssembly t, String table, String col,
                                     String oldType, String newType, String extra)
      throws PairingException
   {
      List<JoinKeyTypeConflict> conflicts =
         WorksheetControllerService.findJoinKeyTypeConflicts(ws, t, col, oldType, newType);

      if(!conflicts.isEmpty()) {
         throw new PairingException(
            describe(table, col, oldType, newType, conflicts) + (extra == null ? "" : extra));
      }
   }

   /** Adds a {@link Break} for {@code col} to {@code out} when its retype breaks a join. */
   public static void collect(Worksheet ws, TableAssembly t, String col, String oldType,
                              String newType, List<Break> out)
   {
      List<JoinKeyTypeConflict> conflicts =
         WorksheetControllerService.findJoinKeyTypeConflicts(ws, t, col, oldType, newType);

      if(!conflicts.isEmpty()) {
         out.add(new Break(col, oldType, newType, conflicts));
      }
   }

   /**
    * Collects the breaks caused by replacing {@code oldCols} with {@code newCols}: every old
    * column that survives (same entity and attribute) with a different data type.
    */
   public static List<Break> findSelectionBreaks(Worksheet ws, TableAssembly t,
                                                 ColumnSelection oldCols, ColumnSelection newCols)
   {
      List<Break> breaks = new ArrayList<>();

      if(ws == null || t == null || oldCols == null || newCols == null) {
         return breaks;
      }

      for(int i = 0; i < oldCols.getAttributeCount(); i++) {
         DataRef old = oldCols.getAttribute(i);
         DataRef fresh = newCols.findAttribute(old);

         if(old instanceof ColumnRef oldRef && fresh != null &&
            !Objects.equals(oldRef.getDataType(), fresh.getDataType()))
         {
            collect(ws, t, oldRef.getName(), oldRef.getDataType(), fresh.getDataType(), breaks);
         }
      }

      return breaks;
   }

   /**
    * Throws when {@code breaks} is non-empty. {@code lead} introduces each column
    * ("Replacing the data of \"T\" would change the type of"), {@code remedy} ends the message.
    */
   public static void throwIfAny(List<Break> breaks, String table, String lead, String remedy)
      throws PairingException
   {
      if(breaks.isEmpty()) {
         return;
      }

      StringBuilder msg = new StringBuilder();

      for(Break b : breaks) {
         if(msg.length() > 0) {
            msg.append(" ");
         }

         msg.append(lead).append(" \"").append(b.column()).append("\" on \"").append(table)
            .append("\" from ").append(b.oldType()).append(" to ").append(b.newType())
            .append(": ").append(breakClause(b.newType(), b.conflicts()));
      }

      throw new PairingException(msg.append(" ").append(remedy).toString());
   }

   /** Remedy for a SQL edit; the query's types are re-detected on every edit. */
   public static String sqlRemedy(List<Break> breaks) {
      StringBuilder cast = new StringBuilder();

      for(Break b : breaks) {
         cast.append(cast.length() > 0 ? ", " : "").append("CAST \"").append(b.column())
            .append("\" back to ").append(b.oldType());
      }

      return "The query's column types are re-detected on every edit, so an earlier " +
         "change_column_type on a column of this table is discarded and the detected type " +
         "applies. In the SELECT, " + cast + ", or remove or edit the join first.";
   }

   /** Remedy for a tabular edit; the connector's types are re-detected on every edit. */
   public static String tabularRemedy() {
      return "The connector's column types are re-detected on every edit, so an earlier " +
         "change_column_type on a column of this table is discarded and the detected type " +
         "applies. Keep the endpoint returning the previous type, or remove or edit the join " +
         "first.";
   }

   /** Remedy for a replace-import; the importer re-detects every column's type from the file. */
   public static String importRemedy(List<Break> breaks) {
      boolean fromString = breaks.stream().anyMatch(b -> XSchema.STRING.equals(b.oldType()));
      return "The import re-detects every column's type from the file, so an earlier " +
         "change_column_type on a column of this table is discarded. Fix the values in the " +
         "file" + (fromString ? ", pass stringColumns to keep a text key as text," : "") +
         " or remove or edit the join first.";
   }
}
