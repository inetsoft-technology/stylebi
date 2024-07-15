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

import inetsoft.report.Comparer;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Vector;

/**
 * Sub query value contains sub query information in an <tt>AssetCondition</tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class SubQueryValue implements AssetObject {
   /**
    * Constructor.
    */
   public SubQueryValue() {
      super();
   }

   /**
    * Replace all embedded user variables.
    * @param vars the specified variable table.
    */
   public void replaceVariables(VariableTable vars) {
      if(table != null) {
         table.replaceVariables(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   public UserVariable[] getAllVariables() {
      if(table == null) {
         return new UserVariable[0];
      }

      return table.getAllVariables();
   }

   /**
    * Check if the condition is a valid condition.
    * @return <tt>true</tt> if is valid, <tt>false</tt> otherwise.
    */
   public boolean checkValidity() {
      return checkValidity(null, null);
   }

   /**
    * Check if the condition is a valid condition.
    * @param scolumns the specified sub column selection.
    * @param mcolumns the specified main column selection.
    * @return <tt>true</tt> if is valid, <tt>false</tt> otherwise.
    */
   public boolean checkValidity(ColumnSelection scolumns,
                                ColumnSelection mcolumns) {
      if(query == null || ref == null) {
         return false;
      }

      if((mref == null) != (sref == null)) {
         return false;
      }

      if(scolumns != null) {
         if(!scolumns.containsAttribute(ref)) {
            return false;
         }

         if(sref != null && !scolumns.containsAttribute(sref)) {
            return false;
         }
      }

      return mref == null || mcolumns == null ||
             mcolumns.containsAttribute(mref);
   }

   /**
    * Check if is correlated.
    * @return <tt>true</tt> if correlated, <tt>false</tt> otherwise.
    */
   public boolean isCorrelated() {
      return mref != null && sref != null;
   }

    /**
    * Set the attribute.
    * @param ref the specified data ref.
    */
   public void setAttribute(DataRef ref) {
      this.ref = ref;
   }

   /**
    * Get the attribute.
    * @return the data ref.
    */
   public DataRef getAttribute() {
      return ref;
   }

   /**
    * Set the sub attribute.
    * @param sref the specified sub data ref.
    */
   public void setSubAttribute(DataRef sref) {
      this.sref = sref;
   }

   /**
    * Get the sub attribute.
    * @return the sub data ref.
    */
   public DataRef getSubAttribute() {
      return sref;
   }

   /**
    * Set the main attribute.
    * @param mref the specified main attribute.
    */
   public void setMainAttribute(DataRef mref) {
      this.mref = mref;
   }

   /**
    * Get the main attribute.
    * @return the main attribute.
    */
   public DataRef getMainAttribute() {
      return mref;
   }

   /**
    * Set the query.
    * @param query the specified query.
    */
   public void setQuery(String query) {
      this.query = query;
   }

   /**
    * Get the query.
    * @return the query.
    */
   public String getQuery() {
      return query;
   }

   /**
    * Get the sub query table assembly.
    * @return the sub query table assembly.
    */
   public TableAssembly getTable() {
      return table;
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    * @param ws the specified worksheet.
    */
   public void renameDepended(String oname, String nname, Worksheet ws) {
      if(oname.equals(query)) {
         query = nname;
      }

      Assembly assembly = ws.getAssembly(nname);

      if(ref instanceof ColumnRef) {
         ColumnRef.renameColumn((ColumnRef) ref, oname, nname);
      }

      if(sref instanceof ColumnRef) {
         ColumnRef.renameColumn((ColumnRef) sref, oname, nname);
      }
   }

   /**
    * Update the assembly.
    * @param ws the associated worksheet.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean update(Worksheet ws) {
      WSAssembly assembly = ws == null ? null : (WSAssembly) ws.getAssembly(query);

      if(assembly == null) {
         // this method will be called during parsing of a ws, and may be called before
         // the dependent assembly is added to ths ws. so it's harmless in that case.
         LOG.info("No available assembly: " + query);
         return false;
      }

      if(!assembly.isTable()) {
         LOG.warn("Invalid assembly found: " + assembly);
         return false;
      }

      assembly.update();
      this.table = (TableAssembly) assembly.clone();
      return true;
   }

   /**
    * Initialize the main table runtime environment.
    * @param mtable the specified main table.
    * @param mcol the specified main attribute column index.
    */
   public void initMainTable(XTable mtable, int mcol) throws Exception {
      this.mcol = mcol < 0 ? AssetUtil.findColumn(mtable, mref) : mcol;

      if(isCorrelated() && this.mcol < 0) {
         LOG.warn("Column not found: " + mref);
         MessageException ex = new MessageException(Catalog.getCatalog().
            getString("common.invalidTableColumn", mref));
         throw ex;
      }

      values = null;
      this.mtable = mtable;
   }

   /**
    * Initialize the sub table runtime environment.
    * @param stable the specified sub table.
    */
   public void initSubTable(XTable stable) throws Exception {
      col = AssetUtil.findColumn(stable, ref);
      scol = AssetUtil.findColumn(stable, sref);

      if(isCorrelated() && scol < 0) {
         LOG.warn("Column not found: " + sref);
         MessageException ex = new MessageException(Catalog.getCatalog().
            getString("common.invalidTableColumn", sref));
         throw ex;
      }

      if(isCorrelated()) {
         comp = DataComparer.getDataComparer(stable.getColType(scol));
      }

      values = null;
      this.stable = stable;
   }

   /**
    * Set current row.
    * @param row the specified row index.
    */
   public void setCurrentRow(int row) {
      this.row = row;
   }

   /**
    * Get current row.
    * @return current row index.
    */
   public int getCurrentRow() {
      return row;
   }

   /**
    * Get the values of the sub query.
    * @return the values of the sub query.
    */
   public Vector<Object> getValues() {
      if(!isCorrelated()) {
         if(values == null) {
            values = new Vector<>();

            if(stable != null && col > -1) {
               for(int i = stable.getHeaderRowCount(); stable.moreRows(i); i++) {
                  values.add(stable.getObject(i, col));
               }
            }
         }

         return values;
      }
      else {
         Object mobj = mtable.getObject(row, mcol);

         if(Tool.equals(mobj, lmobj) && values != null) {
            return values;
         }

         lmobj = mobj;

         if(values == null) {
            values = new Vector<>();
         }

         values.clear();

         for(int i = stable.getHeaderRowCount(); stable.moreRows(i); i++) {
            Object sobj = stable.getObject(i, scol);
            int result;

            try {
               result = comp.compare(mobj, sobj);
            }
            catch(Exception ex) {
               LOG.debug("Failed to compare values " + mobj + " and " + sobj, ex);
               continue;
            }

            if(result == 0) {
               values.add(stable.getObject(i, col));

               // for performance reason, at present we do not linger for
               // more values, but we may support the feature if requried
               if(op != XCondition.ONE_OF) {
                  return values;
               }
            }
         }

         return values;
      }
   }

   /**
    * Check if the values are available.
    */
   public boolean isEvaluated() {
      return !isCorrelated() && stable != null && col > -1;
   }

   /**
    * Write the xml.
    * @param writer the specified print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<subQueryValue>");

      if(query != null) {
         writer.print("<query>");
         writer.print("<![CDATA[" + query + "]]>");
         writer.println("</query>");
      }

      if(ref != null) {
         ref.writeXML(writer);
      }

      if(mref != null) {
         writer.println("<mainRef>");
         mref.writeXML(writer);
         writer.println("</mainRef>");
      }

      if(sref != null) {
         writer.println("<subRef>");
         sref.writeXML(writer);
         writer.println("</subRef>");
      }

      writer.println("</subQueryValue>");
   }

   /**
    * Parse the xml.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      query = Tool.getChildValueByTagName(elem, "query");

      Element rnode = Tool.getChildNodeByTagName(elem, "dataRef");

      if(rnode != null) {
         ref = AbstractDataRef.createDataRef(rnode);
      }

      Element mnode = Tool.getChildNodeByTagName(elem, "mainRef");

      if(mnode != null) {
         Element dnode = Tool.getChildNodeByTagName(mnode, "dataRef");
         mref = AbstractDataRef.createDataRef(dnode);
      }

      Element snode = Tool.getChildNodeByTagName(elem, "subRef");

      if(snode != null) {
         Element dnode = Tool.getChildNodeByTagName(snode, "dataRef");
         sref = AbstractDataRef.createDataRef(dnode);
      }
   }

   /**
    * Check if equqls another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof SubQueryValue)) {
         return false;
      }

      SubQueryValue sub = (SubQueryValue) obj;

      if(table == null || sub.table == null) {
         if(table != sub.table) {
            return false;
         }
      }
      else {
         if(!table.equalsContent(sub.table)) {
            return false;
         }
      }

      return ConditionUtil.equalsDataRef(mref, sub.mref) &&
         ConditionUtil.equalsDataRef(sref, sub.sref) &&
         ConditionUtil.equalsDataRef(ref, sub.ref);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "Query[" + query + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         SubQueryValue sval = (SubQueryValue) super.clone();
         sval.ref = ref == null ? null : (DataRef) ref.clone();
         sval.mref = mref == null ? null : (DataRef) mref.clone();
         sval.sref = sref == null ? null : (DataRef) sref.clone();
         sval.table = table == null ? null : (TableAssembly) table.clone();

         return sval;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Reset the cached value.
    */
   public void reset() {
      lmobj = Tool.NULL;
      mtable = null;
      stable = null;
      values = null;
   }

   /**
    * Get the comparison operation of this condition.
    * @return one of the operation constant, one of the operation constants
    * defined in this class.
    */
   public int getOperation() {
      return op;
   }

   /**
    * Set the comparison operation of this condition.
    * @param op one of the operation constants defined in this class.
    */
   public void setOperation(int op) {
      this.op = op;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("SQ");
      writer.print("[");

      if(ref != null) {
         writer.print(ref.getName());
      }

      if(mref != null) {
         writer.print(",");
         writer.print(mref.getName());
      }

      if(sref != null) {
         writer.print(",");
         writer.print(sref.getName());
      }

      if(query != null) {
         writer.print(",");
         writer.print(query);
      }

      if(table != null) {
         writer.print(",");

         if(!table.printKey(writer)) {
            return false;
         }
      }

      writer.print("]");
      return true;
   }

   private DataRef ref = null;
   private DataRef mref = null;
   private DataRef sref = null;
   private String query;

   private transient TableAssembly table;
   private transient Comparer comp;
   private transient int col;
   private transient XTable mtable;
   private transient XTable stable;
   private transient int mcol;
   private transient int row;
   private transient int scol;
   private transient Object lmobj;
   private transient Vector<Object> values;
   private transient int op;

   private static final Logger LOG =
      LoggerFactory.getLogger(SubQueryValue.class);
}
