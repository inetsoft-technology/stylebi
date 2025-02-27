/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.analytic.composition.event;

import inetsoft.report.script.viewsheet.ScriptEvent;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.uql.viewsheet.*;

/**
 * Event for viewsheet script, it keeps event source, assembly and other
 * properties.
 *
 * @version 14.0
 * @author InetSoft Technology Corp
 */
public class InputScriptEvent implements ScriptEvent {
   /**
    * Constructor.
    */
   public InputScriptEvent(String name, VSAssembly assembly) {
      this.name = name;

      if(assembly instanceof TextInputVSAssembly) {
         this.type = "textinput";
      }
      else if(assembly instanceof CheckBoxVSAssembly) {
         this.type = "checkbox";
      }
      else if(assembly instanceof RadioButtonVSAssembly) {
         this.type = "radiobutton";
      }
      else if(assembly instanceof ComboBoxVSAssembly) {
         this.type = "combobox";
      }
      else if(assembly instanceof SelectionListVSAssembly) {
         this.type = "selectionlist";
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         this.type = "selectiontree";
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         this.type = "rangeslider";
      }
      else if(assembly instanceof CalendarVSAssembly) {
         this.type = "calendar";
      }
   }

   /**
    * Get source name.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set source assembly VSAScriptable object.
    */
   @Override
   public void setSource(VSAScriptable source) {
      this.source = source;
   }

   public VSAScriptable source;     // source scriptable object
   public String name;              // source assembly name
   public String type;              // assembly type
}
