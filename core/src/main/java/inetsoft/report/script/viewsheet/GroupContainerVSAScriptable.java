/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.GroupContainerVSAssemblyInfo;

import java.awt.*;

/**
 * The group container viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class GroupContainerVSAScriptable extends VSAScriptable {
   /**
    * Create a selection tree viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public GroupContainerVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "GroupContainerVSA";
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      GroupContainerVSAssemblyInfo info = getInfo();

      addProperty("image", "getBackgroundImage", "setBackgroundImage",
                  String.class, info.getClass(), info);
      addProperty("maintainAspectRatio", "isMaintainAspectRatio",
                  "setMaintainAspectRatio", boolean.class,
                  info.getClass(), info);
      addProperty("scaleImage", "isScaleImage", "setScaleImage",
                  boolean.class, info.getClass(), info);
      addProperty("scale9", "getScale9", "setScale9",
                  Insets.class, info.getClass(), info);
      addProperty("animate", "isAnimateGIF", "setAnimateGIF",
                  boolean.class, info.getClass(), info);
      addProperty("imageAlpha", "getImageAlpha", "setImageAlpha",
                  String.class, info.getClass(), info);
      addProperty("tile", "isTile", "setTile",
                  boolean.class, info.getClass(), info);
   }

   /**
    * Get the suffix of a property, may be "" or [].
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("scale9".equals(prop)) {
         return "[]";
      }

      return super.getSuffix(prop);
   }

   /**
    * Get the assembly info of current image.
    */
   private GroupContainerVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof GroupContainerVSAssemblyInfo) {
         return (GroupContainerVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new GroupContainerVSAssemblyInfo();
   }
}
