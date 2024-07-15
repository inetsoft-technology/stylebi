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
package inetsoft.uql.schema;

import inetsoft.uql.*;
import inetsoft.uql.path.XNodePath;
import inetsoft.uql.path.expr.Expr;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Date;

/**
 * A QueryVariable is a variable that derives its value from the result
 * of a query. The result is normally a summarization of query results.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class QueryVariable extends XVariable {
   /**
    * Calculate the sum of data.
    */
   public static final String SUM = "sum";
   /**
    * Calculate the average of data.
    */
   public static final String AVG = "avg";
   /**
    * Calculate the minimum of data.
    */
   public static final String MIN = "min";
   /**
    * Calculate the maximum of data.
    */
   public static final String MAX = "max";
   /**
    * Calculate the number of occurrence.
    */
   public static final String COUNT = "count";
   /**
    * Calculate the unique number of occurrence.
    */
   public static final String DISTINCT_COUNT = "distinct_count";

   /**
    * Set the query to extract result from.
    * @param query query name.
    */
   public void setQuery(String query) {
      this.query = query;
   }

   /**
    * Get the query name.
    */
   public String getQuery() {
      return query;
   }

   /**
    * Set the aggregate function to apply to the selection result.
    * @param aggregate function name, one of SUM, AVG, MIN, MAX, and COUNT.
    */
   public void setAggregate(String aggregate) {
      this.aggregate = aggregate;
   }

   /**
    * Get the aggregate function name.
    */
   public String getAggregate() {
      return aggregate;
   }

   /**
    * Evaulate the XVariable.  Will execute and return XNode value.
    */
   @Override
   public Object evaluate(VariableTable vars) {
      try {
         XNode val = execute(vars);

         if(val != null) {
            return val.getValue();
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to evaluate variable: " + vars, e);
      }

      return null;
   }

   /**
    * Execute the query defined in this variable, and apply any path
    * selection if defined.
    * @return query result.
    */
   public XNode execute(VariableTable vars) throws Exception {
      XNode root = XFactory.getDataService().execute(vars.getSession(), query,
                                                  vars, null, false, null);

      if(xpath != null) {
         root = xpath.select(root, vars);
      }

      if(aggregate != null) {
         Object val = Expr.aggregate(aggregate, Expr.toArray(root));

         if(val instanceof String) {
            return new StringValue(aggregate, val);
         }
         else if(val instanceof Date) {
            return new DateValue(aggregate, val);
         }
         else {
            return new DoubleValue(aggregate, val);
         }
      }

      return root;
   }

   /**
    * Parse the XML element that contains information on this variable.
    */
   @Override
   public void parseXML(Element root) throws Exception {
      super.parseAttributes(root);

      query = Tool.getAttribute(root, "query");
      aggregate = Tool.getAttribute(root, "aggregate");

      String path = Tool.getValue(root);

      if(path != null) {
         xpath = XNodePath.parse(path);
      }
   }

   /**
    * Write the variable XML representation.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<variable name=\"" + Tool.escape(getName()) +
              "\" type=\"query\"");

      if(getSource() != null) {
         writer.print(" source=\"" + Tool.escape(getSource()) + "\"");
      }

      if(query != null) {
         writer.print(" query=\"" + Tool.escape(query) + "\"");
      }

      if(aggregate != null) {
         writer.print(" aggregate=\"" + Tool.escape(aggregate) + "\"");
      }

      writer.println(">");

      if(xpath != null) {
         writer.println("<![CDATA[" + xpath + "]]>");
      }

      writer.println("</variable>");
   }

   /**
    * Returns a clone of this object.
    */
   @Override
   public Object clone() {
      QueryVariable xvar = (QueryVariable) super.clone();

      if(xpath != null) {
         xvar.xpath = (XNodePath) xpath.clone();
      }

      xvar.query = query;
      xvar.aggregate = aggregate;

      return xvar;
   }

   private String query;
   private XNodePath xpath;
   private String aggregate;
   private transient XQueryRepository repository; // the local query repository

   private static final Logger LOG =
      LoggerFactory.getLogger(QueryVariable.class);
}

