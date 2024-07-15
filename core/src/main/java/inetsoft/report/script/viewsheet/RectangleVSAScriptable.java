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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

/**
 * The rectangle viewsheet assembly scriptable in viewsheet scope.
 *
 * @author InetSoft Technology Corp
 * @version 10.3
 */
public class RectangleVSAScriptable extends ShapeVSAScriptable {
   /**
    * Create rectangle viewsheet assembly scriptable.
    *
    * @param box the specified viewsheet sandbox.
    */
   public RectangleVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "RectangleVSA";
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();
      addProperty("roundCorner", "getRoundCorner", "setRoundCorner",
                  int.class, getClass(), this);
   }

   /**
    * Set the round corner.
    *
    * @param roundCorner the specified round corner.
    */
   public void setRoundCorner(int roundCorner) {
      getUserDefinedFormat().setRoundCorner(roundCorner);
      VSCompositeFormat cellfmt = getVSAssemblyInfo().
         getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      getVSAssemblyInfo().getFormatInfo().applyFormat(cellfmt);
   }

   /**
    * Get the round corner.
    *
    * @return the round corner of this format.
    */
   public int getRoundCorner() {
      return getVSCompositeFormat().getRoundCorner();
   }
}
