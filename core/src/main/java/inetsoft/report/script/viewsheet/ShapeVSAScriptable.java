/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.GradientColor;
import inetsoft.uql.viewsheet.internal.ShapeVSAssemblyInfo;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * The shape viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ShapeVSAScriptable extends VSAScriptable {
   /**
    * Create a shape viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public ShapeVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "ShapeVSA";
   }

   // Check whether fill color supported.
   protected boolean supportsFill() {
      return true;
   }

   protected boolean hasActions() {
      return false;
   }

   public void setGradientColor(GradientColor gradientColor) {
      getUserDefinedFormat().setGradientColor(gradientColor);
   }

   public GradientColor getGradientColor() {
      return getVSCompositeFormat().getGradientColor();
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      ShapeVSAssemblyInfo info = (ShapeVSAssemblyInfo) getVSAssemblyInfo();

      if(supportsFill()) {
         addProperty("gradientColor", "getGradientColor", "setGradientColor",
                     GradientColor.class, getClass(), this);
         addProperty("shadow", "isShadow", "setShadow",
                     boolean.class, info.getClass(), info);
      }

      addProperty("lineStyle", "getLineStyle", "setLineStyle",
                  int.class, info.getClass(), info);
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof ShapeVSAssemblyInfo)) {
         return Undefined.instance;
      }

      return super.get(name, start);
   }

   /**
    * Get the assembly info of current shape.
    */
   private ShapeVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof ShapeVSAssemblyInfo) {
         return (ShapeVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new ShapeVSAssemblyInfo();
   }
}
