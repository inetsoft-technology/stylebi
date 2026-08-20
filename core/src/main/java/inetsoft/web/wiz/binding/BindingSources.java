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

import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.SourceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Assigning a source table, shared by the chart and table binding services.
 *
 * <p>Both sit on {@link BindingModel}, which is where {@code getTables()} and {@code setSource()}
 * live, so the operation is identical for a chart and for a crosstab — only the shelves that a
 * repoint invalidates differ, and those stay with the service that knows them. Kept in one place
 * rather than copied: the table half shipped first, and a second copy would drift the moment either
 * learned something about how a source is addressed.
 */
final class BindingSources {
   private BindingSources() {
   }

   /**
    * Matches a requested table against what the assembly can actually bind to.
    *
    * @return the table's name as the assembly spells it, so a case-insensitive request is stored in
    *         the canonical form rather than as typed
    * @throws IllegalArgumentException naming what is available, when it cannot
    */
   static String resolve(BindingModel model, String table, String assemblyName) {
      List<BindingModel.SourceTable> tables = model == null ? null : model.getTables();
      List<String> names = new ArrayList<>();

      if(tables != null) {
         for(BindingModel.SourceTable candidate : tables) {
            if(candidate.getName() != null) {
               names.add(candidate.getName());

               if(candidate.getName().equalsIgnoreCase(table)) {
                  return candidate.getName();
               }
            }
         }
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' cannot bind to '" + table + "'. Available: " + names + ". " +
         "A source the assembly cannot see binds nothing and renders an empty assembly.");
   }

   /**
    * The source to store for a worksheet table.
    *
    * <p>Only type, prefix and source survive the trip back — {@code VSBindingService.updateSourceInfo}
    * calls {@code SourceInfo.toSourceAttr}, which rebuilds the asset source from exactly those three.
    * Set directly rather than through the {@code SourceInfo(uql.SourceInfo)} convenience
    * constructor, which also avoids that constructor's {@code toView()} call and the {@code VSUtil}
    * it drags in for a display string nothing here reads.
    */
   static SourceInfo assetSource(String table) {
      SourceInfo source = new SourceInfo();
      source.setType(inetsoft.uql.asset.SourceInfo.ASSET);
      source.setSource(table);
      source.setView(table);

      return source;
   }

   /** Whether the assembly is already pointed at this table, in which case nothing is discarded. */
   static boolean alreadyPointedAt(SourceInfo current, String table) {
      return current != null && table.equalsIgnoreCase(current.getSource());
   }
}
