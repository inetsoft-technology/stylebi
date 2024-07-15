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
package inetsoft.uql.viewsheet;

/**
 * CompoundVSAssembly represents one compound assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface CompoundVSAssembly extends CompositeVSAssembly {
   /**
    * Get the group title.
    * @return the group title.
    */
   @Override
   public String getTitle();

   /**
    * If the group title is visible.
    * @return visibility of group title.
    */
   public boolean isTitleVisible();

   /**
    * Set the visibility of group title.
    * @param visible the visibility of group title.
    */
   public void setTitleVisible(boolean visible);
}
