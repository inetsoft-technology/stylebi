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
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;

import java.util.ArrayList;
import java.util.List;

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

      Assembly bound = ws.getAssembly(sinfo.getSource());
      SourceInfo boundSource =
         bound instanceof AbstractTableAssembly table ? table.getSourceInfo() : null;

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
         boolean resolvedMatch = boundSource != null && boundSource.equals(attachedSource);

         if(!directMatch && !resolvedMatch) {
            continue;
         }

         matches.add(ngAssembly);
      }

      return matches;
   }
}
