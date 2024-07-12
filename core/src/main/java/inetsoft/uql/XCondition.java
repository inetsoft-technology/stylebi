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
package inetsoft.uql;

import inetsoft.uql.schema.UserVariable;
import inetsoft.util.ContentObject;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * XCondition defines the condition methods.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface XCondition extends Serializable, Cloneable, XMLSerializable, ContentObject {
   /**
    * Condition operation definition that represents a not defined condition.
    */
   int NONE = 0;

   /**
    * Condition operation definition that compares two objects using the
    * <code>DefaultComparer</code> and returns <code>true</code> if and only
    * if the two objects are equals. This flag can be combined with the
    * <code>LESS_THAN</code> and <code>GREATER_THAN</code> flags.
    */
   int EQUAL_TO = 1;

   /**
    * Condition operation definition that determines if an object is equals
    * an object in a predefined set.
    */
   int ONE_OF = 2;

   /**
    * Condition operation definition that compares two objects using the
    * <code>DefaultComparer</code> and returns <code>true</code> if and only
    * if the first object is less than the second. This flag can be combined
    * with the <code>EQUAL_TO</code> flag.
    */
   int LESS_THAN = 3;

   /**
    * Condition operation definition that compares two objects using the
    * <code>DefaultComparer</code> and returns <code>true</code> if and only
    * if the first object is greater than the second. This flag can be combined
    * with the <code>EQUAL_TO</code> flag.
    */
   int GREATER_THAN = 4;

   /**
    * Condition operation definition that compares two objects using the
    * <code>DefaultComparer</code> and returns <code>true</code> if and only
    * if the if an object is greater than the first limiting object and less
    * than the second.
    */
   int BETWEEN = 5;

   /**
    * Condition operation definition that determines if a String object starts
    * with a specified substring.
    */
   int STARTING_WITH = 6;

   /**
    * Condition operation definition that determines if a specified substring
    * can be found in a String object.
    */
   int CONTAINS = 7;

   /**
    * Condition operation definition that determines
    * if a String object is null.
    */
   int NULL = 8;

   /**
    * Top n operation definition that determines if only favor
    * top n rows.
    */
   int TOP_N = 9;

   /**
    * Bottom n operation definition that determines if only favor
    * bottom n rows.
    */
   int BOTTOM_N = 10;

   /**
    * Date in operation definition that determines if a date object in the
    * specified date range.
    */
   int DATE_IN = 11;

   /**
    * Pseudo operation definition that determines if the condition is a
    * pseudo condition, which should be only used for analysis.
    */
   int PSEUDO = 12;

   /**
    * Condition operation definition that determines if a String object matches
    * a SQL LIKE pattern.
    */
   int LIKE = 13;

   /**
    * Correlated condition.
    */
   int CORRELATED = 1024;

   /**
    * Get the condition value data type.
    * @return the data type of this condition. The type will be one of the
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    */
   String getType();

   /**
    * Check if type is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   boolean isTypeChangeable();

   /**
    * Set the condition value data type.
    * @param type the data type of the condition. Must be one of the data type
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    */
   void setType(String type);

   /**
    * Get the comparison operation of this condition.
    * @return one of the operation constant, one of the operation constants
    * defined in this class.
    * @see #EQUAL_TO
    * @see #ONE_OF
    * @see #LESS_THAN
    * @see #GREATER_THAN
    * @see #BETWEEN
    * @see #STARTING_WITH
    * @see #LIKE
    * @see #CONTAINS
    * @see #NULL
    * @see #TOP_N
    * @see #DATE_IN
    */
   int getOperation();

   /**
    * Check if operation is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   boolean isOperationChangeable();

   /**
    * Set the comparison operation of this condition.
    * @param op one of the operation constants defined in this class.
    */
   void setOperation(int op);

   /**
    * Determine whether equivalence will be tested in addition to the
    * defined comparison operation.
    * @return <code>true</code> if equivalence will be tested
    */
   boolean isEqual();

   /**
    * Check if equal is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   boolean isEqualChangeable();

   /**
    * Set the equal to option when the comparison operation is
    * <code>LESS_THAN</code> or <code>GREATER_THAN</code>, i.e.
    * <code><i>a</i> &gt;= <i>b</i></code>.
    * @param equal <code>true</code> if equivalence should be tested
    */
   void setEqual(boolean equal);

   /**
    * Set whether this condition result should be negated. A negated condition
    * will evaluate as <code>true</code> if the if its condition definition(s)
    * are <b>not</b> met.
    * @return <code>true</code> if this condition is negated.
    */
   boolean isNegated();

   /**
    * Check if negated is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   boolean isNegatedChangeable();

   /**
    * Determine whether this condition result should be negated. A negated
    * condition will evaluate as <code>true</code> if the if its condition
    * definition(s) are <b>not</b> met.
    * @param negated <code>true</code> if this condition is negated.
    */
   void setNegated(boolean negated);

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   void replaceVariable(VariableTable vars);

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   UserVariable[] getAllVariables();

   /**
    * Evaluate this condition against the specified value object.
    * @param value the value object this condition should be compared with.
    * @return <code>true</code> if the value object meets this condition.
    */
   boolean evaluate(Object value);

   /**
    * Check if the condition is a valid condition.
    * @return true if is valid, false otherwise.
    */
   boolean isValid();

   /**
    * Write the contents.
    * @param writer the specified print writer.
    */
   void writeContents(PrintWriter writer);

   /**
    * Parse the contents.
    * @param elem the specified xml element.
    */
   void parseContents(Element elem) throws Exception;

   /**
    * Writer the attributes.
    * @param writer the specified print writer.
    */
   void writeAttributes(PrintWriter writer);

   /**
    * Parse the attributes.
    * @param elem the specified xml element.
    */
   void parseAttributes(Element elem) throws Exception;

   /**
    * Clone the object.
    * @return the cloned object.
    */
   Object clone();
}
