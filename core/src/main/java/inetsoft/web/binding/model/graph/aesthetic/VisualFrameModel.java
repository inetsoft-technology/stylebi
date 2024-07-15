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
package inetsoft.web.binding.model.graph.aesthetic;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.CLASS,
   include = JsonTypeInfo.As.PROPERTY,
   property = "clazz")
public abstract class VisualFrameModel {
   /**
    * Constructor.
    */
   public VisualFrameModel() {
   }

   public VisualFrameModel(VisualFrameWrapper wrapper) {
      setField(wrapper.getVisualFrame().getField());
   }

   /**
    * Set the model name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the model name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the AggregateRef fullname.
    */
   public void setField(String fname) {
      this.field = fname;
   }

   /**
    * Get the AggregateRef fullname.
    */
   public String getField() {
      return field;
   }

   /**
    * Return if summary for waterfall.
    */
   public boolean isSummary() {
      return summary;
   }

   /**
    * Set if summary for waterfall.
    */
   public void setSummary(boolean summary) {
      this.summary = summary;
   }

   /**
    * Check whether the frame has been changed in view side.
    */
   public boolean isChanged() {
      return changed;
   }

   /**
    * Set whether the frame has been changed in view side.
    */
   public void setChanged(boolean changed) {
      this.changed = changed;
   }

   public abstract VisualFrame createVisualFrame();

   private String name; // used for palettes to recode palette name.
   private String field;
   private boolean summary;
   private boolean changed;
}
