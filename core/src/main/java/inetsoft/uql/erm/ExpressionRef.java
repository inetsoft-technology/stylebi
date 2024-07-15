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
package inetsoft.uql.erm;

import inetsoft.uql.asset.AggregateFormula;
import inetsoft.util.Tool;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;

/**
 * Class holding a reference to a formula expression that can be incorporated
 * in a JDBC SQL query.
 *
 * @author  InetSoft Technology Corp.
 * @since   5.1
 */
public class ExpressionRef extends AbstractDataRef {
   /**
    * Get the script expression. For a sql expression, script expression might
    * be embedded as commented out segment.
    */
   public static String getScriptExpression(boolean sql, String exp) {
      if(!sql || exp == null) {
         return exp;
      }

      String prefix = "/*script";
      String suffix = "script*/";
      String lowexp = exp.toLowerCase();
      int from = lowexp.indexOf(prefix);
      int to = lowexp.indexOf(suffix);

      if(from < 0 || to < from) {
         return exp;
      }

      return exp.substring(from + prefix.length(), to).trim();
   }

   /**
    * Get the sql expression. For a sql expression, script expression might
    * be embedded as commented out segment.
    */
   public static final String getSQLExpression(boolean sql, String exp) {
      if(!sql || exp == null) {
         return exp;
      }

      String prefix = "/*script";
      String suffix = "script*/";
      String lowexp = exp.toLowerCase();
      int from = lowexp.indexOf(prefix);
      int to = lowexp.indexOf(suffix);

      if(from < 0 || to < from) {
         return exp;
      }

      return exp.substring(0, from).trim() + exp.substring(to + suffix.length()).trim();
   }

   /**
    * Create a new instance of ExpressionRef. This constructor is for internal
    * use only.
    */
   public ExpressionRef() {
      super();
      dbtype = "";
      this.name = "";
   }

   /**
    * Create a new instance of ExpressionRef.
    *
    * @param datasource the name of the JDBC data source to which the object
    *                   refers
    * @param name       the name of the reference
    */
   public ExpressionRef(String datasource, String name) {
      this();
      this.datasource = datasource;
      this.name = name;
   }

   /**
    * Create a new instance of ExpressionRef.
    *
    * @param datasource the name of the JDBC data source to which the object
    *                   refers
    * @param entity     the entity
    * @param name       the name of the reference
    */
   public ExpressionRef(String datasource, String entity, String name) {
      this();
      this.datasource = datasource;
      this.entity = entity;
      this.name = name;
   }

   /**
    * Get the database type.
    *
    * @return the database type.
    */
   public String getDBType() {
      return dbtype;
   }

   /**
    * Set the database type.
    *
    * @param dbtype the specified database type.
    */
   public void setDBType(String dbtype) {
      this.dbtype = dbtype == null ? "" : dbtype;
   }

   /**
    * Setter of ref type.
    *
    * @param rtype the type of the ref, NONE, DIMENSION or MEASURE.
    */
   public void setRefType(int rtype) {
      this.refType = (byte) rtype;
   }

   /**
    * Getter of ref type.
    */
   @Override
   public int getRefType() {
      return refType;
   }

   /**
    * Check if the attribute is an expression.
    *
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise
    */
   @Override
   public boolean isExpression() {
      return true;
   }

   /**
    * Get the name of the JDBC data source to which this reference refers.
    *
    * @return the name of a JDBC data source
    */
   public String getDataSource() {
      return datasource;
   }

   /**
    * Set the name of the JDBC data source to which this reference refers.
    *
    * @param datasource the name of a JDBC data source
    */
   public void setDataSource(String datasource) {
      this.datasource = datasource;
   }

   /**
    * Get the SQL expression of this reference.
    *
    * @return a SQL expression
    */
   public String getExpression() {
      return expression;
   }

   /**
    * Get the script expression of this reference.
    *
    * @return a script expression
    */
   public String getScriptExpression() {
      return getExpression();
   }

   /**
    * Set the SQL expression of this reference.
    *
    * @param expression a SQL expression
    */
   public void setExpression(String expression) {
      this.expression = expression == null ? "" : expression;
   }

   /**
    * Get the name of the field.
    *
    * @return the name of the field
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of the field.
    *
    * @param name the name of the field
    */
   public void setName(String name) {
      this.name = name;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Check if the expression is a virtual expression.
    */
   public boolean isVirtual() {
      return virtual;
   }

   /**
    * Set whether the expression is a vritual expression.
    */
   public void setVirtual(boolean virtual) {
      this.virtual = virtual;
   }

   /**
    * Check if the expression is calculate on aggregate value.
    */
   public boolean isOnAggregate() {
      return onAggregate;
   }

   /**
    * Set if the expression is calculate on aggregate value.
    */
   public void setOnAggregate(boolean onAggregate) {
      this.onAggregate = onAggregate;
   }

   /**
    * Check if this expression is sql expression.
    *
    * @return true if is, false otherwise
    */
   public boolean isSQL() {
      return false;
   }

   /**
    * Check if expression is editable.
    *
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise
    */
   public boolean isExpressionEditable() {
      return true;
   }

   /**
    * Get the attribute's parent entity.
    *
    * @return the name of the entity
    */
   @Override
   public String getEntity() {
      return entity;
   }

   /**
    * Set the attribute's parent entity.
    *
    * @param entity the name of the entity
    */
   public void setEntity(String entity) {
      this.entity = entity;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Get a list of all the entities referenced by this object.
    *
    * @return an Enumeration containing the entity names
    */
   @Override
   public Enumeration getEntities() {
      return new EntityEnumeration();
   }

   /**
    * Get the referenced attribute.
    *
    * @return the name of the attribute
    */
   @Override
   public String getAttribute() {
      return name;
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    *
    * @return an Enumeration containing AttributeRef objects
    */
   @Override
   public Enumeration getAttributes() {
      return new AttributeEnumeration(false);
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    *
    * @return an Enumeration containing AttributeRef objects
    */
   public Enumeration getCalcAttributes() {
      return new AttributeEnumeration(true);
   }

   @Override
   public String getDataType() {
      return (dtype != null) ? dtype : super.getDataType();
   }

   @Override
   public boolean isDataTypeSet() {
      return dtype != null;
   }

   /**
    * Set the column data type.
    */
   public void setDataType(String dtype) {
      this.dtype = dtype;
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      if(getDataSource() != null) {
         writer.print("datasource=\"" + Tool.escape(getDataSource()) + "\" ");
      }

      if(getEntity() != null) {
         writer.print("entity=\"" + Tool.escape(getEntity()) + "\" ");
      }

      if(getName() != null) {
         writer.print("name=\"" + Tool.escape(getName()) + "\" ");
      }

      if(dtype != null) {
         writer.print("dataType=\"" + dtype + "\" ");
      }

      writer.print("refType=\"" + refType + "\" ");
   }

   /**
    * Write the attributes of this object.
    *
    * @param dos the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      try {
         dos.writeBoolean(getDataSource() == null);

         if(getDataSource() != null) {
            dos.writeUTF(getDataSource());
         }

         dos.writeBoolean(getName() == null);

         if(getName() != null) {
            dos.writeUTF(getName());
         }

         dos.writeBoolean(dtype == null);

         if(dtype != null) {
            dos.writeUTF(dtype);
         }
      }
      catch(IOException e) {
      }
   }

   /**
    * Write the CDATA of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeCDATA(PrintWriter writer) {
      writer.println("<![CDATA[" + getExpression() + "]]>");
   }

   /**
    * Write the CDATA of this object.
    *
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeCDATA2(DataOutputStream dos) {
      try {
         if(getExpression() != null) {
            dos.writeUTF(getExpression());
         }

         dos.writeInt(refType);
      }
      catch(IOException e) {
      }
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      String val = null;

      if((val = Tool.getAttribute(tag, "datasource")) != null) {
         setDataSource(val);
      }

      if((val = Tool.getAttribute(tag, "entity")) != null) {
         entity = val;
      }

      if((val = Tool.getAttribute(tag, "name")) != null) {
         name = val;
      }

      if((val = Tool.getAttribute(tag, "refType")) != null) {
         refType = (byte) Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(tag, "dataType")) != null) {
         dtype = val;
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    *
    * @param tag the XML Element representing this object
    */
   @Override
   protected void parseCDATA(Element tag) throws DOMException {
      // fix bug1399879788141, expression support multi line
      String val = Tool.getValue(tag, true);
      setExpression(val);
   }

   /**
    * Get a String representation of this object.
    *
    * @return a String representation of this object
    */
   public String toString() {
      return "[" + getName() + "][" + expression + "]";
   }

   /**
    * Get the view representation of this field.
    *
    * @return the view representation of this field.
    */
   @Override
   public String toView() {
      return getName();
   }

   /**
    * Enumeration of AttributeRef objects built from the field names in the
    * expression string. The list of field names is build as the enumeration
    * is iterated, so it avoids multiple loops to create the enumeration.
    */
   public class AttributeEnumeration implements Enumeration<AttributeRef> {
      AttributeEnumeration(boolean detailCalc) {
         this.detailCalc = detailCalc;
         e2c = new HashMap<>();
      }

      @Override
      public boolean hasMoreElements() {
         return second != null || (getExpression() != null && idx >= 0 &&
            getExpression().indexOf("field['", idx) >= 0);
      }

      @Override
      public AttributeRef nextElement() {
         AttributeRef result = null;

         if(second != null) {
            result = second;
            second = null;
            return result;
         }

         if(hasMoreElements()) {
            // @by jasons 2003-09-23
            // parse the next field name as it is requested
            idx = getExpression().indexOf("field['", idx) + 7;

            if(idx < getExpression().length() - 2) {
               int end = getExpression().indexOf("']", idx);

               if(end > idx) {
                  String fname = getExpression().substring(idx, end).trim();
                  String oname = fname;
                  String formulaName = null;
                  boolean existsFormula = false;

                  if(!detailCalc) {
                     // SUM(int) Sum([customers:customer_id])
                     if(fname.endsWith(")") && onAggregate) {
                        int smallstart = fname.indexOf('(');
                        int smallend = fname.lastIndexOf(')');

                        if(smallstart > 0) {
                           formulaName = fname.substring(0, smallstart);
                           fname = fname.substring(smallstart + 1, smallend);
                        }
                     }
                     // sum[price]
                     else if(fname.endsWith("]")) {
                        int midstart = fname.indexOf('[');
                        int midend = fname.indexOf(']');

                        if(midstart > 0) {
                           fname = fname.substring(midstart + 1, midend);
                        }
                     }

                     existsFormula = !oname.equals(fname);
                     String key = "field['" + oname + "']";
                     e2c.put(key, oname);
                  }

                  int dotidx = fname.indexOf(',');

                  if(existsFormula && dotidx > 0 &&
                     dotidx == fname.lastIndexOf(','))
                  {
                     String[] names = fname.split(",");
                     result = createRef(names[0]);
                     boolean secondIsInt = false;

                     try {
                        Integer.parseInt(names[1].trim(), 10);
                        secondIsInt = true;
                     }
                     catch(Exception ignore) {
                     }

                     if(formulaName == null || !isNthFormula(formulaName) || !secondIsInt) {
                        second = createRef(names[1].trim());
                     }
                  }
                  else {
                     result = createRef(fname);
                  }

                  idx = end + 2;
               }
               else {
                  idx = -1;
               }
            }
         }

         return result;
      }

      private static boolean isNthFormula(String formulaName) {
         return formulaName == null || formulaName.isEmpty() ? false :
            N_FORMULAS.contains(formulaName.toLowerCase());
      }

      /**
       * Create attributeref from string.
       */
      private AttributeRef createRef(String fname) {
         fname = trimParenthesis(fname);

         // e.a
         if(fname.length() >= 3) {
            // @by mikec, for table names with schema such as
            // HR.Employee.salary
            // column name should be the last seg.
            int dot = fname.lastIndexOf('.');
            String entity = null;
            String attr = null;

            if(dot >= 0) {
               entity = trimParenthesis(fname.substring(0, dot));
               attr = trimParenthesis(fname.substring(dot + 1));
            }
            else {
               attr = trimParenthesis(fname);
            }

            return new AttributeRef(entity, attr);
         }
         else {
            return new AttributeRef(null, trimParenthesis(fname));
         }
      }

      /**
       * Get expression to column mapping.
       */
      public Map<String, String> getExpToColMapping() {
         return e2c;
      }

      public AttributeRef second;
      int idx = 0;
      private boolean detailCalc;
      private Map<String, String> e2c; // expression to column mapping
   }

   /**
    * Trim the "[customer]" to "customer".
    */
   private String trimParenthesis(String src) {
      int len = src.length();

      if(len > 2 && src.charAt(0) == '[' && src.charAt(len - 1) == ']') {
         return src.substring(1, len - 1);
      }

      return src;
   }

   /**
    * Enumeration of entity names created from an AttributeEnumeration. Uses a
    * look-ahead mechanism to avoid looping more than once to create the
    * enumeration.
    */
   private class EntityEnumeration implements Enumeration {
      public EntityEnumeration() {
         attrs = getAttributes();
         entities = new HashSet();

         // @by jasons 2003-09-23
         // look-ahead to the first value
         if(attrs.hasMoreElements()) {
            current = attrs.nextElement();
            entities.add(current);
         }
      }

      @Override
      public boolean hasMoreElements() {
         return current != null;
      }

      @Override
      public Object nextElement() {
         if(current == null) {
            return null;
         }

         Object result = current;
         current = null;

         // @by jasons 2003-09-23
         // look-ahead for the next entity that has not already been returned.
         // if all remaining entities have already been return, set current to
         // null
         while(attrs.hasMoreElements()) {
            current = attrs.nextElement();

            if(!entities.contains(current)) {
               entities.add(current);
               break;
            }

            current = null;
         }

         return result;
      }

      private Object current = null;
      private Enumeration attrs = null;
      private HashSet entities = null;
   }

   /**
    * Check if equals another object content.
    *
    * @param obj the specified object.
    *
    * @return true if equals, false otherwise.
    */
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof ExpressionRef) || !super.equals(obj)) {
         return false;
      }

      if(this == obj) {
         return true;
      }

      ExpressionRef ref = (ExpressionRef) obj;

      return Tool.equals(datasource, ref.datasource) &&
         Tool.equals(entity, ref.entity) &&
         Tool.equals(name, ref.name) &&
         Tool.equals(expression, ref.expression) &&
         Tool.equals(dtype, ref.dtype) &&
         Tool.equals(refType, ref.refType);
   }

   private static final Set<String> N_FORMULAS = new HashSet<>();
   private static void addFormulaMapping(AggregateFormula formula) {
      if(formula == null) {
         return;
      }

      N_FORMULAS.add(formula.getName().toLowerCase());
      N_FORMULAS.add(formula.getLabel().toLowerCase());
      N_FORMULAS.add(formula.getFormulaName().toLowerCase());
   }

   static {
      addFormulaMapping(AggregateFormula.NTH_LARGEST);
      addFormulaMapping(AggregateFormula.NTH_MOST_FREQUENT);
      addFormulaMapping(AggregateFormula.NTH_SMALLEST);
   }

   private String datasource;
   private String entity;
   private String name;
   private String expression = "";
   private byte refType = NONE;
   private String dtype;
   private transient boolean virtual;
   private transient String dbtype;
   private transient boolean onAggregate;
}
