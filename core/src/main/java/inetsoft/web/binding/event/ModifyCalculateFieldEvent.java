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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.viewsheet.event.ViewsheetEvent;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Class that encapsulates the parameters for change chart type event.
 *
 * @since 12.3
 */
@Value.Immutable
@JsonSerialize(as = ImmutableModifyCalculateFieldEvent.class)
@JsonDeserialize(as = ImmutableModifyCalculateFieldEvent.class)
public interface ModifyCalculateFieldEvent extends ViewsheetEvent {
   /**
    * Get the calculateRef.
    * @return calculateRef.
    */
   @Nullable
   CalculateRefModel calculateRef();

   /**
    * Get the table name.
    * @return table name.
    */
   String tableName();

   /**
    * Check if is remove calculate field.
    * @return <tt>true</tt> if remove calculate field, <tt>false</tt> otherwise.
    */
   boolean remove();

   /**
    * Check if is create calculate field.
    * @return <tt>true</tt> if create calculate field, <tt>false</tt> otherwise.
    */
   boolean create();

   /**
    * Get the ref name.
    * @return ref name.
    */
   String refName();

   /**
    * Get the dimension type.
    * @return dimension type.
    */
   @Nullable
   String dimType();

   /**
    * Check if need check trap.
    * @return <tt>true</tt> if need check trap, <tt>false</tt> otherwise.
    */
   boolean checkTrap();

   /**
    * Check if is wizard.
    * @return <tt>true</tt> if is wizard binding tree, <tt>false</tt> otherwise.
    */
   boolean wizard();

   /**
    * for vs wizard, get the object wizard original mode.
    * @return one of VsWizardEditModes.
    */
   @Nullable
   String wizardOriginalMode();
}
