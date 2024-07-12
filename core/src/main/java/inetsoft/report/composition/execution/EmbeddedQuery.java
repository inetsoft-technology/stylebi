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
package inetsoft.report.composition.execution;

import inetsoft.mv.DFWrapper;
import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedded query executes an embedded table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class EmbeddedQuery extends AssetQuery {
   /**
    * Create an asset query.
    */
   public EmbeddedQuery(int mode, AssetQuerySandbox box, EmbeddedTableAssembly table,
                        boolean stable, boolean metadata)
   {
      super(mode, box, stable, metadata);

      this.table = table;
      this.table.update();
   }

   /**
    * Get the design mode table lens.
    * @param vars the specified variable table.
    */
   @Override
   protected TableLens getDesignTableLens(VariableTable vars) throws Exception {
      if(!AssetQuerySandbox.isEditMode(mode)) {
         return super.getDesignTableLens(vars);
      }

      ColumnSelection columns = getTable().getColumnSelection();
      TableLens base = new XSnapshotLens(getEmbeddedData(vars));
      return new DesignEmbeddedTableFilter(base, columns);
   }

   /**
    * Get the post process base table lens.
    * @param vars the specified variable table.
    * @return the post process base table lens of the query.
    */
   @Override
   protected TableLens getPostBaseTableLens(VariableTable vars) {
      XTableLens lens = new XSnapshotLens(getEmbeddedData(vars));
      LOG.debug("Finished loading embedded data: " + lens.getRowCount() + " row(s)");
      return lens;
   }

   /**
    * Get the table assembly to be executed.
    * @return the table assembly.
    */
   @Override
   protected TableAssembly getTable() {
      return table;
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) {
      // do nothing
      return true;
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      return null;
   }

   /**
    * Validate the column selection.
    */
   @Override
   public void validateColumnSelection() {
      ColumnSelection ncolumns = getDefaultColumnSelection();
      ColumnSelection columns = getTable().getColumnSelection();
      validateColumnSelection(ncolumns, columns, true, true, false, true);

      getTable().setColumnSelection(columns);
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      if(table instanceof SnapshotEmbeddedTableAssembly) {
         return ((SnapshotEmbeddedTableAssembly) table).getDefaultColumnSelection();
      }

      ColumnSelection columns = new ColumnSelection();
      XEmbeddedTable data = table.getEmbeddedData();

      for(int i = 0; i < data.getColCount(); i++) {
         String header = AssetUtil.format(XUtil.getHeader(data, i));
         String type = data.getDataType(i);
         DataRef attr = new AttributeRef(null, header);
         ColumnRef column = new ColumnRef(attr);

         if(type == null) {
            type = Tool.getDataType(data.getColType(i));
         }

         column.setDataType(type);
         columns.addAttribute(column);
      }

      return columns;
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() {
      return false;
   }

   /**
    * Get the string representation of an attribute column.
    * @param column the specified attribute column.
    * @return the string representation of the attribute column.
    */
   @Override
   protected String getAttributeColumn(AttributeRef column) {
      return null;
   }

   /**
    * Get the alias of a column.
    * @param attr the specified column.
    * @return the alias of the column, <tt>null</tt> if not exists.
    */
   @Override
   protected String getAlias(ColumnRef attr) {
      return null;
   }

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   @Override
   public JDBCQuery getQuery0() {
      return null;
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   @Override
   protected UniformSQL getUniformSQL() {
      return null;
   }

   /**
    * Get a text description of how the query will be executed.
    * @return the text description.
    */
   @Override
   public QueryNode getQueryPlan() throws Exception {
      boolean snapshot = table instanceof SnapshotEmbeddedTableAssembly;
      StringBuilder sb = new StringBuilder();
      sb.append(snapshot ? catalog.getString("Snapshot Embedded Data") :
                   catalog.getString("Embedded Data"));
      sb.append(getVariablesString(null));
      sb.append(".");

      return getQueryNode(sb.toString());
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      return null;
   }

   /**
    * Get query icon.
    */
   @Override
   protected String getIconPath() {
      return "/inetsoft/report/gui/composition/images/embedded.png";
   }

   /**
    * Get embedded data.
    */
   private XTable getEmbeddedData(VariableTable vars) {
      if(!(table instanceof SnapshotEmbeddedTableAssembly)) {
         return table.getEmbeddedData();
      }

      SnapshotEmbeddedTableAssembly setable = (SnapshotEmbeddedTableAssembly) table;
      XTable stable = setable.getTable();
      ColumnSelection columns = setable.getDefaultColumnSelection();
      int fcnt = columns.getAttributeCount(); // filter
      List<String> headerList = new ArrayList<>();
      List<String> formulaList = new ArrayList<>();
      List<String> dtypeList = new ArrayList<>();

      for(int i = 0; i < fcnt; i++) {
         ColumnRef ref = (ColumnRef) columns.getAttribute(i);
         ExpressionRef eref = getExpressionRef(ref);
         boolean sql = ref.isSQL();

         if(eref != null && !sql) {
            headerList.add(ref.getName());
            formulaList.add(eref.getExpression());
            dtypeList.add(getExpressionDataType(ref));
         }
      }

      if(headerList.size() > 0) {
         XTableLens lens = new XSnapshotLens(stable);
         ScriptEnv env = box.getScriptEnv();
         AssetQueryScope scope = box.getScope();
         scope.setVariableTable(vars);
         scope.setMode(mode);
         String[] headers = new String[headerList.size()];
         String[] formulas = new String[formulaList.size()];
         headerList.toArray(headers);
         formulaList.toArray(formulas);
         FormulaTableLens flens = new FormulaTableLens(lens, headers, formulas, env, box.getScope());

         for(int i = 0; i < dtypeList.size(); i++) {
            flens.setColType(i, dtypeList.get(i).getClass());
         }
      }

      return stable;
   }

   /**
    * Get expression column data type.
    */
   private String getExpressionDataType(ColumnRef column) {
      String dtype = column.getDataType();

      if(!(column.getDataRef() instanceof ExpressionRef)) {
         return dtype;
      }

      ExpressionRef exp = (ExpressionRef) column.getDataRef();

      if(exp instanceof DateRangeRef) {
         dtype = exp.getDataType();
      }
      else if(column.getDataRef() instanceof ExpressionRef && column.getDataRef().isDataTypeSet()) {
         // column.getDataRef() is the expression ref so it's type should be used. (50644)
         dtype = column.getDataRef().getDataType();
      }

      return dtype;
   }

   /**
    * Get expression ref from wrapper.
    */
   private ExpressionRef getExpressionRef(DataRef ref) {
      if(ref instanceof DataRefWrapper) {
         DataRef eref = ((DataRefWrapper) ref).getDataRef();

         if(eref instanceof ExpressionRef) {
            return (ExpressionRef) eref;
         }
      }

      return null;
   }

   private static class DesignEmbeddedTableFilter extends AbstractTableLens
      implements TableFilter
   {
      public DesignEmbeddedTableFilter(TableLens table, ColumnSelection columns)
      {
         super();
         this.table = table;
         init(table, columns);
      }

      @Override
      public int getRowCount() {
         return table.getRowCount();
      }

      @Override
      public int getColCount() {
         return col;
      }

      @Override
      public boolean isNull(int r, int c) {
         if(expcolumn[c]) {
            return expressions[c] == null;
         }
         else {
            return table.isNull(r, mapping[c]);
         }
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 0) {
            return header[c];
         }

         return expcolumn[c] ? expressions[c] : table.getObject(r, mapping[c]);
      }

      @Override
      public Class getColType(int col) {
         return types[col];
      }

      @Override
      public int getBaseRowIndex(int row) {
         return row;
      }

      @Override
      public int getBaseColIndex(int col) {
         // not really for expression column
         return mapping[col];
      }

      @Override
      public TableLens getTable() {
         return table;
      }

      @Override
      public void setTable(TableLens table) {
         // do nothing
      }

      @Override
      public void invalidate() {
         // do nothing
      }

      @Override
      public void dispose() {
         table.dispose();
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      private void init(TableLens table, ColumnSelection columns) {
         mapping = new int[columns.getAttributeCount()];
         col = columns.getAttributeCount();
         expressions = new String[col];
         header = new String[col];
         expcolumn = new boolean[col];
         types = new Class[col];

         for(int i = 0; i < col; i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);
            int bcol = AssetUtil.findColumn(table, column);
            mapping[i] = i;
            expcolumn[i] = column.isExpression();
            types[i] = bcol < 0 ? String.class : table.getColType(bcol);

            if(expcolumn[i]) {
               ExpressionRef ref = (ExpressionRef) column.getDataRef();

               if(ref.isExpressionEditable()) {
                  String expr = ref.getExpression();
                  expressions[i] = expr;
               }
            }
            else {
               mapping[i] = bcol;
            }

            String alias = column.getAlias();
            String id = getAttributeString(column);

            if(alias != null) {
               header[i] = alias;
            }
            else {
               header[i] = column.getName();
            }

            setColumnIdentifier(i, id);
         }
      }

      // column mapping
      private int[] mapping;
      // expression column?
      private boolean[] expcolumn;
      // expression column data
      private String[] expressions;
      // header
      private String[] header;
      // types
      private Class[] types;
      // column count
      private int col;
      private TableLens table;
   }

   private static class XSnapshotLens extends XTableLens implements DFWrapper {
      /**
       * Constructor.
       * @param table the specified xtable.
       */
      public XSnapshotLens(XTable table) {
         super(table);
         this.table = table;
         this.checkNull = new boolean[table.getColCount()];

         if(table instanceof XEmbeddedTable) {
            for(int i = 0; i < checkNull.length; i++) {
               checkNull[i] = !String.class.equals(table.getColType(i));
            }
         }
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 0) {
            return super.getObject(r, c);
         }
         else {
            Object obj = super.getObject(r, c);

            // null may be stored as empty string in embedded table. for spark
            // the type must match so returning string for number would cause exception
            if(checkNull[c] && "".equals(obj)) {
               return null;
            }

            return obj;
         }
      }

      /**
       * Dispose the table to clear up temporary resources.
       */
      @Override
      public void dispose() {
         // do nothing so that we may reuse the contained data
      }

      /**
       * RDD delegate methods.
       */
      @Override
      public long dataId() {
         DFWrapper wrapper = getDFWrapper();
         return (wrapper != null) ? wrapper.dataId() : 0;
      }

      @Override
      public Object getDF() {
         DFWrapper wrapper = getDFWrapper();
         return (wrapper != null) ? wrapper.getDF() : null;
      }

      /**
       * RDD delegate methods.
       */
      @Override
      public Object getRDD() {
         DFWrapper wrapper = getDFWrapper();
         return (wrapper != null) ? wrapper.getRDD() : null;
      }

      /**
       * RDD delegate methods.
       */
      @Override
      public DFWrapper getBaseDFWrapper() {
         return getDFWrapper();
      }

      /**
       * RDD delegate methods.
       */
      @Override
      public String[] getHeaders() {
         DFWrapper wrapper = getDFWrapper();
         return (wrapper != null) ? wrapper.getHeaders() : null;
      }

      /**
       * RDD delegate methods.
       */
      @Override
      public void setXMetaInfos(XSwappableTable lens) {
         DFWrapper wrapper = getDFWrapper();

         if(wrapper != null) {
            wrapper.setXMetaInfos(lens);
         }
      }

      /**
       * RDD delegate methods.
       */
      @Override
      public void completed() {
         DFWrapper wrapper = getDFWrapper();

         if(wrapper != null) {
            wrapper.completed();
         }
      }

      // get the DFWrapper nested in this XNode
      private DFWrapper getDFWrapper() {
         return (table instanceof DFWrapper) ? (DFWrapper) table : null;
      }

      @Override
      public boolean isSnapshot() {
         return true;
      }

      private XTable table;
      private boolean[] checkNull;
   }

   private EmbeddedTableAssembly table;
   private static final Logger LOG = LoggerFactory.getLogger(EmbeddedQuery.class);
}
