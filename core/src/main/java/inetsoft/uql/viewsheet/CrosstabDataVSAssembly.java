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

import inetsoft.uql.ConditionList;
import inetsoft.uql.viewsheet.internal.CrosstabTree;

/**
 * CrosstabDataVSAssembly defines the common API for assemblies that are based
 * on crosstab data.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface CrosstabDataVSAssembly extends CubeVSAssembly {
   /**
    * Set the crosstab info.
    * @param info the specified crosstab info.
    */
   public void setVSCrosstabInfo(VSCrosstabInfo info);

   /**
    * Get the crosstab info.
    * @return the crosstab info.
    */
   public VSCrosstabInfo getVSCrosstabInfo();

   /**
    * Get the crosstab hierarchical tree.
    */
   public CrosstabTree getCrosstabTree();

   /**
    * Get the range condition list.
    * @return the range condition list.
    */
   public ConditionList getRangeConditionList();

   /**
    * Set the range condition list.
    * @param range the specified range condition list.
    * @return the changed hint.
    */
   public int setRangeConditionList(ConditionList range);

   /**
    * Check if this viewsheet assembly has a cube.
    * @return <tt>true</tt> if has a cube, <tt>false</tt> otherwise.
    */
   public boolean hasCube();
}
