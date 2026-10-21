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
package inetsoft.web.wiz.binding;

import inetsoft.uql.asset.AbstractTableAssembly;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the worksheet-local {@code DefaultNamedGroupAssembly}(s) {@code add_named_group}
 * attached to a given column of a given {@code SourceInfo}.
 *
 * <p>Shared by {@link CalcTableService} and {@link FieldRefFactory}, which each need this
 * lookup for a different caller shape (a bound {@code DataVSAssembly} vs. a bare
 * {@code SourceInfo}) but must apply identical matching -- previously hand-mirrored as two
 * separate copies, which is how one of the two creation modes below was fixed in only one
 * of the two consumers.
 *
 * <p>{@code add_named_group} attaches a created group's {@code SourceInfo} one of two ways:
 * <ul>
 *   <li>Plain worksheet-table+column mode: {@code attachedSource} is set to that worksheet
 *       table's own bound {@code SourceInfo} verbatim -- matches {@code sinfo} (the crosstab/
 *       table's own bound {@code SourceInfo}, always {@code SourceInfo.ASSET} with source =
 *       the worksheet table's assembly name) directly.</li>
 *   <li>Datasource-scoped (logical-model or physical-table attribute) mode: {@code
 *       attachedSource} is set to the datasource/logical-model/physical-table {@code
 *       SourceInfo} itself (e.g. {@code SourceInfo.MODEL}, source = the logical model name) --
 *       never equal to {@code sinfo}, which is always the worksheet table's own name. This only
 *       matches the worksheet table assembly's <em>own</em> {@code SourceInfo} (what a {@code
 *       BoundTableAssembly} created the same way carries), which {@code sinfo.getSource()}
 *       names but is not itself equal to.</li>
 * </ul>
 */
final class WorksheetNamedGroupMatcher {
   private WorksheetNamedGroupMatcher() {
   }

   static List<DefaultNamedGroupAssembly> worksheetNamedGroups(
      Worksheet ws, SourceInfo sinfo, String column)
   {
      List<DefaultNamedGroupAssembly> matches = new ArrayList<>();

      if(sinfo == null || sinfo.getSource() == null || ws == null) {
         return matches;
      }

      AbstractTableAssembly boundTable = resolveSourceTable(ws, sinfo.getSource());
      SourceInfo boundSource = boundTable == null ? null : boundTable.getSourceInfo();

      for(Assembly wsAssembly : ws.getAssemblies()) {
         if(!(wsAssembly instanceof DefaultNamedGroupAssembly ngAssembly) ||
            ngAssembly.getAttachedType() != AttachedAssembly.COLUMN_ATTACHED)
         {
            continue;
         }

         SourceInfo attachedSource = ngAssembly.getAttachedSource();
         DataRef attr = ngAssembly.getAttachedAttribute();

         if(attachedSource == null || attr == null || !column.equals(attr.getAttribute())) {
            continue;
         }

         boolean directMatch = sinfo.getSource().equals(attachedSource.getSource());
         boolean resolvedMatch = boundSource != null && boundSource.equals(attachedSource) &&
            exposesColumn(boundTable, column, attr);

         if(!directMatch && !resolvedMatch) {
            continue;
         }

         matches.add(ngAssembly);
      }

      return matches;
   }

   /**
    * Walks from the table a viewsheet assembly is bound to down through any
    * {@code MirrorTableAssembly} chain to the first table that carries its own
    * {@code SourceInfo}. A live viewsheet runtime always puts a mirror under the bound name
    * ({@code Viewsheet#createMirrorTable} renames the raw table to {@code <name>_O}), and a
    * mirror's {@code getSourceInfo()} is null. Returns null when the chain ends without a
    * source (join, concatenation, SQL, ...), is cyclic, or is deeper than {@link #MAX_DEPTH}.
    */
   private static AbstractTableAssembly resolveSourceTable(Worksheet ws, String name) {
      Assembly current = ws.getAssembly(name);
      Set<Assembly> visited = new HashSet<>();

      for(int depth = 0; depth < MAX_DEPTH && current != null && visited.add(current); depth++) {
         if(current instanceof MirrorTableAssembly mirror) {
            current = mirror.getTableAssembly();
         }
         else if(current instanceof AbstractTableAssembly table) {
            return table.getSourceInfo() == null ? null : table;
         }
         else {
            return null;
         }
      }

      return null;
   }

   /**
    * Whether {@code table} exposes {@code column}, and, when both the group's attribute and the
    * table's column are entity-qualified (logical model), the same entity. A logical model's
    * {@code SourceInfo} is shared by all of its entities, so without the entity check a
    * {@code Customer.State} group would match {@code Order.State}.
    */
   private static boolean exposesColumn(AbstractTableAssembly table, String column,
                                        DataRef attr)
   {
      ColumnSelection columns = table.getColumnSelection(true);

      if(columns == null) {
         return false;
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         if(!(columns.getAttribute(i) instanceof ColumnRef ref) ||
            !column.equals(ref.getAttribute()) && !column.equals(ref.getName()))
         {
            continue;
         }

         String entity = ref.getEntity();
         String attrEntity = attr.getEntity();

         if(entity == null || attrEntity == null || entity.equals(attrEntity)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Why a name that is a worksheet-local named group still did not resolve on a column, or
    * an empty string when the worksheet has no group of that name. Appended to the generic
    * refusal so the caller is told the actual blocker.
    */
   static String unmatchedReason(Worksheet ws, String namedGroup) {
      if(ws == null || namedGroup == null) {
         return "";
      }

      Assembly assembly = ws.getAssembly(namedGroup);

      if(!(assembly instanceof DefaultNamedGroupAssembly ng)) {
         return "";
      }

      if(ng.getAttachedType() != AttachedAssembly.COLUMN_ATTACHED) {
         return " Note: worksheet group '" + namedGroup + "' is attached by data type only " +
            "(add_named_group without a column), so it cannot bind to a column. Make it " +
            "predefined (set_primary_assembly on it, then save_worksheet) or recreate it " +
            "attached to a column.";
      }

      DataRef attr = ng.getAttachedAttribute();
      return " Note: worksheet group '" + namedGroup + "' is attached to column '" +
         (attr == null ? "?" : attr.getAttribute()) + "' of another source, not to this " +
         "assembly's bound table.";
   }

   private static final int MAX_DEPTH = 16;
}
