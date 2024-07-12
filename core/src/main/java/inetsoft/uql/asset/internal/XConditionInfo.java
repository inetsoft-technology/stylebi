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
package inetsoft.uql.asset.internal;

import inetsoft.uql.AbstractCondition;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * XCondition info stores basic condition information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class XConditionInfo extends AbstractCondition {
   /**
    * Constructor.
    */
   public XConditionInfo() {
      super();
   }

   /**
    * Replace all embedded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariable(VariableTable vars) {
      // do nothing
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return new UserVariable[0];
   }

   /**
    * Evaluate this condition against the specified value object.
    * @param value the value object this condition should be compared with.
    * @return <code>true</code> if the value object meets this condition.
    */
   @Override
   public boolean evaluate(Object value) {
      return true;
   }

   /**
    * Check if the condition is a valid condition.
    * @return true if is valid, false otherwise.
    */
   @Override
   public boolean isValid() {
      return true;
   }

   /**
    * Check if negated is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNegatedChangeable() {
      return true;
   }

   /**
    * Check if equal is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEqualChangeable() {
      return true;
   }

   /**
    * Check if operation is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOperationChangeable() {
      return true;
   }

   /**
    * Check if type is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isTypeChangeable() {
      return true;
   }

   /**
    * Write the contents.
    * @param writer the specified print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      // do nothing
   }

   /**
    * Parse the contents.
    * @param elem the specified xml element.
    */
   @Override
   public void parseContents(Element elem) throws Exception {
      // do nothing
   }
}
