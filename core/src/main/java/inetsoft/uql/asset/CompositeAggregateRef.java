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
package inetsoft.uql.asset;

import inetsoft.mv.formula.*;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * An aggregate that can be calculated from sub-components of a combinable
 * aggregate. The sub-components are in the child table.
 * At runtime, the composite aggregate should be calculated by using the
 * sub-aggregate, which is an AggregateRef.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CompositeAggregateRef extends AggregateRef {
   /**
    * Create a composite aggregate ref.
    */
   public CompositeAggregateRef() {
      super();

      aggregates = new ArrayList<>();
   }

   /**
    * Create a composite aggregate ref.
    * @param ref original aggregate column.
    */
   public CompositeAggregateRef(AggregateRef ref, List<AggregateRef> aggregates) {
      super(ref.getDataRef(), ref.getSecondaryColumn(), ref.getFormula());

      aref = ref;
      this.aggregates = aggregates;
   }

   /**
    * Get the original aggregate ref.
    */
   public AggregateRef getAggregateRef() {
      return aref;
   }

   /**
    * Return a formula for post process application.
    * @param cols column indexes from table.
    * @return formula object.
    */
   public Formula getCompositeFormula(int[] cols) {
      if("COUNT DISTINCT".equals(getFormula().getName())) {
         return new SumFormula();
      }
      else if("COUNT ALL".equals(getFormula().getName())) {
         return new SumFormula();
      }
      else if("SUMSQ".equals(getFormula().getName())) {
         return new SumFormula();
      }
      else if("AVG".equals(getFormula().getName())) {
         return new CompositeAverageFormula(cols[cols.length - 1]);
      }
      else if("WEIGHTED AVG".equals(getFormula().getName())) {
         return new CompositeAverageFormula(cols[cols.length - 1]);
      }
      else {
         int[] ncols = new int[cols.length - 1];
         System.arraycopy(cols, 1, ncols, 0, ncols.length);

         if("COVARIANCE".equals(getFormula().getName())) {
            return new CompositeCovarianceFormula(ncols);
         }
         else if("VARIANCE".equals(getFormula().getName())) {
            return new CompositeVarianceFormula(ncols);
         }
         else if("POPULATION VARIANCE".equals(getFormula().getName())) {
            return new CompositePopulationVarianceFormula(ncols);
         }
         else if("POPULATION STANDARD DEVIATION".equals(getFormula().getName()))
         {
            return new CompositePopulationStandardDeviationFormula(ncols);
         }
         else if("STANDARD DEVIATION".equals(getFormula().getName())) {
            return new CompositeStandardDeviationFormula(ncols);
         }
         else if("CORRELATION".equals(getFormula().getName())) {
            return new CompositeCorrelationFormula(ncols);
         }
      }

      return null;
   }

   /**
    * Get the data ref for expression.
    */
   private DataRef getDataRef(DataRef ref) {
      if(ref instanceof ColumnRef) {
         ColumnRef column = (ColumnRef) ref;
         column = (ColumnRef) column.clone();
         DataRef iref = column.getDataRef();

         if(iref instanceof AliasDataRef) {
            iref = ((AliasDataRef) iref).getDataRef();
            column.setDataRef(iref);
            ref = column;
         }
      }

      return ref;
   }

   /**
    * Get the SQL expression for this aggregate.
    */
   @Override
   public String getExpression(AggregateHelper helper) {
      DataRef ref = getDataRef();
      ref = getDataRef(ref);
      DataRef ref2 = getAggregateRef().getSecondaryColumn();
      ref2 = getDataRef(ref2);
      String col = ref.getName();
      String col2 = ref2 == null ? null : ref2.getName();
      return getFormula().getExpressionSub(col, col2, helper);
   }

   /**
    * Get the number of sub aggregates.
    */
   public int getChildAggregateCount() {
      return aggregates.size();
   }

   /**
    * Get the sub-aggregates used to compute this composite value.
    */
   public List<AggregateRef> getChildAggregates() {
      return aggregates;
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      CompositeAggregateRef cref = (CompositeAggregateRef) super.clone();
      cref.aref = aref == null ? null : (AggregateRef) aref.clone();
      return cref;
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      return "CompositeAggregateRef: " + super.toString();
   }

   /**
    * Get an unique id for the formula that can be used to identify an aggregate
    * column.
    */
   @Override
   public String getUID() {
      DataRef ref = getDataRef();
      ref = getDataRef(ref);
      DataRef ref2 = getAggregateRef().getSecondaryColumn();
      ref2 = getDataRef(ref2);
      String col = ref.getName();
      String col2 = ref2 == null ? null : ref2.getName();
      return getFormula().getUID(col, col2);
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<aggregateRef>");
      aref.writeXML(writer);
      writer.println("</aggregateRef>");
      writer.println("<aggregates>");

      for(AggregateRef ref : aggregates) {
         ref.writeXML(writer);
      }

      writer.println("</aggregates>");
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element anode = Tool.getChildNodeByTagName(tag, "aggregateRef");
      anode = Tool.getChildNodeByTagName(anode, "dataRef");
      aref = (AggregateRef) createDataRef(anode);

      Element asnode = Tool.getChildNodeByTagName(tag, "aggregates");
      NodeList anodes = Tool.getChildNodesByTagName(asnode, "dataRef");

      for(int i = 0; i < anodes.getLength(); i++) {
         anode = (Element) anodes.item(i);
         AggregateRef ref = (AggregateRef) createDataRef(anode);
         aggregates.add(ref);
      }
   }

   private List<AggregateRef> aggregates;
   private AggregateRef aref;
}
