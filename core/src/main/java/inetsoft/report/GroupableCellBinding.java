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
package inetsoft.report;

import inetsoft.report.internal.binding.Field;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Defines the cell binding for groupable layout. A cell can be bound to
 * a static text, a column, a formula, it contains external infomation
 * for the cell is bound to group or aggregate or detail field.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class GroupableCellBinding extends CellBinding {
   /**
    * No expansion.
    */
   public static final int EXPAND_NONE = 0;
   /**
    * Expand horizontal.
    */
   public static final int EXPAND_H = 1;
   /**
    * Expand vertical.
    */
   public static final int EXPAND_V = 2;

   /**
    * Default constructor.
    */
   public GroupableCellBinding() {
   }

   /**
    * Create a binding with specified type.
    * @param type one of the binding types defined in TableLayout.
    * @param value the binding value. See getValue().
    */
   public GroupableCellBinding(int type, String value) {
      super(type, value);
   }

   /**
    * Get cell expansion type.
    */
   public int getExpansion() {
      return expansion;
   }

   /**
    * Set the cell expansion type. Use one of the expansion constants:
    * EXPAND_NONE, EXPAND_H, EXPAND_V.
    */
   public void setExpansion(int expansion) {
      this.expansion = expansion;
   }

   /**
    * Set cell binding field.
    */
   public void setField(Field cfield) {
      this.cfield = cfield;
   }

   /**
    * Get the cell binding field.
    */
   public Field getField() {
      return cfield;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" expansion=\"" + expansion + "\"");
   }

   /**
    * Parse contents.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val = null;

      if((val = Tool.getAttribute(tag, "expansion")) != null) {
         expansion = Integer.parseInt(val);
      }
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof GroupableCellBinding)) {
         return false;
      }

      GroupableCellBinding binding = (GroupableCellBinding) obj;
      return super.equals(binding) && binding.expansion == expansion;
   }

   public int hashCode() {
      int hash = super.hashCode();
      hash = hash + 7 * expansion;
      return hash;
   }

   private int expansion = EXPAND_NONE;
   // cell binding field, group, aggregate or detail, for calc table, it
   // may be persistent, for others, it is a transient property
   private Field cfield;
}