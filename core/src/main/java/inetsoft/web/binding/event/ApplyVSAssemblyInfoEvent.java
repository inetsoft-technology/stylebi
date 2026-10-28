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
package inetsoft.web.binding.event;

import inetsoft.web.binding.model.BindingModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that encapsulates the parameters for applying a selection.
 *
 * @since 12.3
 */
public class ApplyVSAssemblyInfoEvent {
   /**
    * Get the assembly name.
    * @return assembly name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the assembly name.
    * @param name assembly name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the bindingmodel.
    * @return bindingmodel.
    */
   public BindingModel getBinding() {
      return binding;
   }

   /**
    * Set the bindingmodel.
    * @param binding the bindingmodel.
    */
   public void setBinding(BindingModel binding) {
      this.binding = binding;
   }
   
   /**
    * Get the trap tag.
    * @return trap tag.
    */
   public boolean isCheckTrap() {
      return trap;
   }

   /**
    * Set trap target.
    * @param trap is trap or not.
    */
   public void setCheckTrap(boolean trap) {
      this.trap = trap;
   }

   /**
    * Whether the apply clears the header alias left on a crosstab header path that no longer
    * has a dimension -- the same {@code ClearTableHeaderAliasHandler.processClearAliasFormat}
    * the Composer's own drag-and-drop passes, except that a crosstab keeps the label of a still
    * bound measure's summary header path (bug #79331). Off by default, so the native setbinding
    * event is unchanged; the wiz table tools turn it on (bug #78530).
    * @return true to clear the aliases of vacated header paths.
    */
   public boolean isClearVacatedAliases() {
      return clearVacatedAliases;
   }

   /**
    * Set whether the apply clears the header alias of vacated header paths.
    * @param clearVacatedAliases true to clear them.
    */
   public void setClearVacatedAliases(boolean clearVacatedAliases) {
      this.clearVacatedAliases = clearVacatedAliases;
   }

   /**
    * Crosstab dimensions whose named group the apply removes instead of restoring from the live
    * assembly, as {@code "rows:<column>"} / {@code "cols:<column>"} entries. Empty by default, so
    * the native setbinding event is unchanged; the wiz table tools fill it for an explicit
    * ungroup (bug #79017).
    * @return the shelf-qualified columns to ungroup.
    */
   public List<String> getUngroupDimensions() {
      return ungroupDimensions;
   }

   /**
    * Set the dimensions whose named group is removed.
    * @param ungroupDimensions {@code "rows:<column>"} / {@code "cols:<column>"} entries.
    */
   public void setUngroupDimensions(List<String> ungroupDimensions) {
      this.ungroupDimensions = ungroupDimensions == null ? new ArrayList<>() : ungroupDimensions;
   }

   private String name;
   private List<String> ungroupDimensions = new ArrayList<>();
   private BindingModel binding;
   private boolean trap = true;
   private boolean clearVacatedAliases;
}
