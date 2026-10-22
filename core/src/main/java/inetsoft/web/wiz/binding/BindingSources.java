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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.DataVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSTreeHandler;
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
    * Whether a stored source string names another component of the viewsheet rather than a
    * worksheet table. The native Composer stores a component source under its
    * {@code Assembly.TABLE_VS_BOUND}-prefixed name (that prefix is what every engine-side test
    * keys on, e.g. {@code VSUtil.isVSAssemblyBinding}), so the prefix is the kind.
    */
   static boolean isComponent(String source) {
      return source != null && source.startsWith(Assembly.TABLE_VS_BOUND);
   }

   /** The name the user sees: a component source without its storage prefix. */
   static String displayName(String source) {
      return isComponent(source) ? source.substring(Assembly.TABLE_VS_BOUND.length()) : source;
   }

   /** The stored form of a component's name. */
   static String componentSource(String name) {
      return Assembly.TABLE_VS_BOUND + name;
   }

   /**
    * The component of this viewsheet that {@code table} names, when the assembly may bind to it.
    * The test is the one {@code list_bindable_fields} applies to build its Components folder
    * ({@link VSTreeHandler#isAssemblyBindable}): a visible Table/Crosstab/Calc Table-like data
    * assembly that is not the assembly itself, not a chart, and not already bound back to it.
    * Reusing it keeps what is accepted identical to what is offered, so a source the listing
    * would not show cannot be stored. A runtime-mode session's listing omits the folder, so such
    * a session finds no component here either.
    *
    * @return the component's name as the viewsheet spells it, or null
    */
   static String findComponent(RuntimeViewsheet rvs, String assemblyName, String table) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

      if(vs == null || table == null || rvs.isRuntime() && !isComponent(table)) {
         return null;
      }

      String name = displayName(table);
      VSAssembly target = vs.getAssembly(assemblyName);
      VSAssembly candidate = vs.getAssembly(name);

      // The cheap type test first: the shared bindable test below pulls in VSUtil.
      if(target == null || !(candidate instanceof DataVSAssembly) ||
         !VSTreeHandler.isAssemblyBindable(target, candidate, vs))
      {
         return null;
      }

      return candidate.getAbsoluteName();
   }

   /**
    * The source to store for a worksheet table, or for a component when {@code table} carries the
    * component prefix.
    *
    * <p>Only type, prefix and source survive the trip back — {@code VSBindingService.updateSourceInfo}
    * calls {@code SourceInfo.toSourceAttr}, which rebuilds the asset source from exactly those three.
    * Set directly rather than through the {@code SourceInfo(uql.SourceInfo)} convenience
    * constructor, which also avoids that constructor's {@code toView()} call and the {@code VSUtil}
    * it drags in for a display string nothing here reads.
    */
   static SourceInfo assetSource(String table) {
      SourceInfo source = new SourceInfo();
      boolean component = isComponent(table);
      source.setType(component ? inetsoft.uql.asset.SourceInfo.VS_ASSEMBLY
                               : inetsoft.uql.asset.SourceInfo.ASSET);
      source.setSource(table);
      source.setView(displayName(table));

      return source;
   }

   /** Whether the assembly is already pointed at this table, in which case nothing is discarded. */
   static boolean alreadyPointedAt(SourceInfo current, String table) {
      return current != null && isComponent(table) == isComponent(current.getSource()) &&
         displayName(table).equalsIgnoreCase(displayName(current.getSource()));
   }

   /**
    * The runtime source to resolve worksheet-local named groups against, for a write that may
    * establish the source in the same call. The assembly's own {@code SourceInfo} is still empty
    * until the write is applied, so a first call carrying both {@code table} and a
    * {@code namedGroup} would otherwise resolve against nothing.
    *
    * @param current the assembly's own source, or null
    * @param model the binding model's source, after {@code applySource}
    * @return {@code current} when it names a source, else an asset source for the model's table,
    *         else {@code current}
    */
   static inetsoft.uql.asset.SourceInfo effective(inetsoft.uql.asset.SourceInfo current,
                                                  SourceInfo model)
   {
      if(current != null && current.getSource() != null && !current.getSource().isEmpty() ||
         model == null || model.getSource() == null || model.getSource().isEmpty())
      {
         return current;
      }

      return new inetsoft.uql.asset.SourceInfo(
         isComponent(model.getSource()) ? inetsoft.uql.asset.SourceInfo.VS_ASSEMBLY
                                        : inetsoft.uql.asset.SourceInfo.ASSET,
         null, model.getSource());
   }
}
