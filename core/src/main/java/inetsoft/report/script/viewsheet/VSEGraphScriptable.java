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

import java.util.HashSet;
import java.util.Set;

/**
 * The Scriptable For EGraph, to getIds.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class VSEGraphScriptable {
   /**
    * Create an instance of EGraph.
    */
   public VSEGraphScriptable() {
      super();
   }

   /**
    * Get ids for auto complite.
    */
   public Object[] getIds() {
      Set ids = new HashSet();
      ids.add("addElement()");
      ids.add("addForm()");
      ids.add("clearForms()");
      ids.add("clearElements()");
      ids.add("getCoordinate()");
      ids.add("getElement()");
      ids.add("getElementCount()");
      ids.add("getForm()");
      ids.add("getFormCount()");
      ids.add("getLegendLayout()");
      ids.add("getLegendPreferredSize()");
      ids.add("getScale()");
      ids.add("getVisualFrames()");
      ids.add("getXTitleSpec()");
      ids.add("getX2TitleSpec()");
      ids.add("getYTitleSpec()");
      ids.add("getY2TitleSpec()");
      ids.add("removeElement()");
      ids.add("removeForm()");
      ids.add("setCoordinate()");
      ids.add("setLegendLayout()");
      ids.add("setLegendPreferredSize()");
      ids.add("setScale()");
      ids.add("setXTitleSpec()");
      ids.add("setX2TitleSpec()");
      ids.add("setYTitleSpec()");
      ids.add("setY2TitleSpec()");
      ids.add("XTitleSpec");
      ids.add("x2TitleSpec");
      ids.add("YTitleSpec");
      ids.add("y2TitleSpec");
      ids.add("coordinate");
      ids.add("legendLayout");
      ids.add("legendPreferredSize");
      return ids.toArray();
   }
}
