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
package inetsoft.uql.tabular;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XQuery;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.tabular.impl.TabularHandler;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * This is the base class for defining a tabular query.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
public abstract class TabularQuery extends XQuery {
   public TabularQuery(String type) {
      super(type);
   }

   public void loadOutputColumns(VariableTable vtable) throws Exception {
      TabularHandler handler = new TabularHandler();
      int maxRows = getMaxRows();
      setMaxRows(100);

      try {
         // not need to get most up-to-date data for refreshing columns.
         vtable.put(XQuery.HINT_PREVIEW, "true");
         handler.execute(this, vtable, null, null);
      }
      finally {
         vtable.remove(XQuery.HINT_PREVIEW);
         setMaxRows(maxRows);
      }
   }

   /**
    * Return the output columns of the query. If the query implementation is
    * able to find the column information, it should return the columns
    * here. Otherwise, the column information will be captured when a query
    * is previewed.
    */
   public XTypeNode[] getOutputColumns() {
      return cols;
   }

   /**
    * Set the output columns.
    */
   public void setOutputColumns(XTypeNode[] cols) {
      this.cols = cols;
   }

   /**
    * Get the output meta data of query (pre-selection).
    */
   @Override
   public XTypeNode getOutputType(Object session, boolean full) {
      XTypeNode root = new XTypeNode("table");
      XTypeNode[] cols = getOutputColumns();

      if(cols != null) {
         for(XTypeNode node : getOutputColumns()) {
            root.addChild(node);
         }
      }

      return root;
   }

   @Override
   protected void findVariables(Map varmap) {
      for(UserVariable var: TabularUtil.findVariables(this)) {
         addVariable(var);
      }
   }

   /**
    * Get the XSelection object representing the selected columns.
    */
   @Override
   public XSelection getSelection() {
      return null;
   }

   /**
    * Set the column type to use for data conversion.
    * @param header column full header, e.g. path in json.
    * @param type data type in XSchema.
    */
   public void setColumnType(String header, String type) {
      if(type == null) {
         typemap.remove(header);
      }
      else {
         typemap.put(header, type);
      }
   }

   /**
    * Get the column type to use for data conversion.
    */
   public String getColumnType(String header) {
      return typemap.get(header);
   }

   /**
    * Get the names that have explicit types set through setColumnType().
    */
   public Collection<String> getTypedColumns() {
      return typemap.keySet();
   }

   /**
    * Set the format for the column used for type conversion.
    * @param header column full header.
    * @param format format used to TableFormat.getFormat() call.
    */
   public void setColumnFormat(String header, String format) {
      if(format == null) {
         fmtmap.remove(header);
      }
      else {
         fmtmap.put(header, format);
      }
   }

   /**
    * Get the format for the column used for type conversion.
    */
   public String getColumnFormat(String header) {
      return fmtmap.get(header);
   }

   /**
    * Set the formatExtent extent for the column used for type conversion.
    * @param header column full header.
    * @param formatExtent formatExtent extent used to TableFormat.getFormat() call.
    */
   public void setColumnFormatExtent(String header, String formatExtent) {
      if(formatExtent == null) {
         extentmap.remove(header);
      }
      else {
         extentmap.put(header, formatExtent);
      }
   }

   /**
    * Get the format extent for the column used for type conversion.
    */
   public String getColumnFormatExtent(String header) {
      return extentmap.get(header);
   }

   public VariableTable getVariableTable() {
      return variableTable;
   }

   public void setVariableTable(VariableTable variableTable) {
      this.variableTable = variableTable;
   }

   /**
    * Get the assets referenced by this query.
    * @param assets a list of all tables in same worksheet.
    */
   public String[] getDependedAssets(String[] assets) {
      return new String[0];
   }

   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<query_" + getType() + " ");
      writeAttributes(writer);
      writer.println(">");

      super.writeXML(writer);
      writeContents(writer);
      writer.println("</query_" + getType() + ">");
   }

   @Override
   public final void parseXML(Element root) throws Exception {
      super.parseXML(root);
      parseAttributes(root);
      parseContents(root);
   }

   /**
    * Write the attributes of the XML tag.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" class=\"" + this.getClass().getName() + "\"");
   }

   /**
    * Write the contents of the XML tag.
    */
   protected void writeContents(PrintWriter writer) {
      writer.println("<outputColumns>");

      if(cols != null) {
         for(XTypeNode col : cols) {
            if(col != null) {
               col.writeXML(writer);
            }
         }
      }

      writer.println("</outputColumns>");

      writer.println("<columnTypes>");

      for(String col : typemap.keySet()) {
         writer.println("<columnType>");
         writer.println("<name><![CDATA[" + col + "]]></name>");
         writer.println("<type><![CDATA[" + typemap.get(col) + "]]></type>");
         writer.println("</columnType>");
      }

      writer.println("</columnTypes>");

      writer.println("<columnFormats>");

      for(String col : fmtmap.keySet()) {
         writer.println("<columnFormat>");
         writer.println("<name><![CDATA[" + col + "]]></name>");
         writer.println("<format><![CDATA[" + fmtmap.get(col) + "]]></format>");
         writer.println("</columnFormat>");
      }

      writer.println("</columnFormats>");

      writer.println("<columnFormatExtents>");

      for(String col : extentmap.keySet()) {
         writer.println("<columnFormatExtent>");
         writer.println("<name><![CDATA[" + col + "]]></name>");
         writer.println("<FormatExtent><![CDATA[" + extentmap.get(col) + "]]></FormatExtent>");
         writer.println("</columnFormatExtent>");
      }

      writer.println("</columnFormatExtents>");
   }

   /**
    * Parse the attributes of the XML tag.
    */
   protected void parseAttributes(Element tag) throws Exception {
   }

   /**
    * Parse the contents of the XML tag.
    */
   protected void parseContents(Element tag) throws Exception {
      Element elem = Tool.getChildNodeByTagName(tag, "outputColumns");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "element");
         cols = new XTypeNode[list.getLength()];

         for(int i = 0; i < cols.length; i++) {
            Element node = (Element) list.item(i);
            cols[i] = XSchema.createPrimitiveType(Tool.getAttribute(node, "type"));
            cols[i].setName(Tool.getAttribute(node, "name"));
         }
      }

      elem = Tool.getChildNodeByTagName(tag, "columnTypes");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "columnType");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            typemap.put(Tool.getChildValueByTagName(node, "name"),
                        Tool.getChildValueByTagName(node, "type"));
         }
      }

      elem = Tool.getChildNodeByTagName(tag, "columnFormats");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "columnFormat");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            fmtmap.put(Tool.getChildValueByTagName(node, "name"),
                       Tool.getChildValueByTagName(node, "format"));
         }
      }

      elem = Tool.getChildNodeByTagName(tag, "columnFormatExtents");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "columnFormatExtent");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            fmtmap.put(Tool.getChildValueByTagName(node, "name"),
                       Tool.getChildValueByTagName(node, "FormatExtent"));
         }
      }
   }

   /**
    * The shape of the response this query's last execution parsed: which paths exist and what type
    * each leaf is, carrying none of the values.
    *
    * <p>A SLOT, not a computation. Core defines it and never fills it, because only the connector
    * sees a raw response and only the connector knows its format; the JSON connectors distil theirs
    * with {@code JsonShapeDistiller} (which lives beside the JSON tables, in a module core does not
    * depend on). Anything absent means the connector does not report a shape, which is the normal
    * state for most tabular types and never an error.</p>
    *
    * <p>It describes ONE EXECUTION, not the query, so it is deliberately not written to XML by
    * {@link #writeContents} and not read back by {@link #parseContents}. A saved query carries no
    * shape; running it produces one again.</p>
    *
    * <p>Note for anyone tracing why this is null: {@code TabularHandler.execute} runs a CLONE of the
    * query and only writes back to the original through {@code setOutputColumns}. A shape set by a
    * runner therefore lands on the clone, and reaches the caller only because that method copies it
    * across explicitly. Removing that copy makes this silently null on every path.</p>
    */
   public Object getResponseShape() {
      return responseShape;
   }

   /**
    * @param shape     the distilled shape, or null to report none.
    * @param truncated whether a node or depth cap cut the distillation short — a consumer has to
    *                  tell "this path is not in the response" from "this path was not reached".
    */
   public void setResponseShape(Object shape, boolean truncated) {
      this.responseShape = shape;
      this.responseShapeTruncated = truncated;
   }

   /**
    * Whether {@link #getResponseShape} was cut off by a cap rather than completed.
    */
   public boolean isResponseShapeTruncated() {
      return responseShapeTruncated;
   }

   /**
    * A bounded sample of the rows this query's last execution actually read.
    *
    * <p>The same kind of slot as {@link #getResponseShape}, on the same terms — core defines it and
    * never fills it, only the connector sees a response, absent is the normal state — and it
    * describes ONE EXECUTION too, so it is likewise cleared by {@link #clone()} and never written
    * to XML.</p>
    *
    * <p>What differs is what it carries: a shape is a property of the connector, these rows are
    * CUSTOMER DATA. So this one must not be recorded and reused for anyone else, and the connector
    * that fills it bounds it at the source ({@code JsonRowSampler}, and the
    * {@code rest.sample.rows} property that can switch sampling off entirely).</p>
    *
    * <p>A list, but core never looks inside an element: rows are the connector's response format.
    * Unmodifiable, recursively — see the field comment for why that matters.</p>
    */
   public List<?> getSampleRows() {
      return sampleRows;
   }

   /**
    * @param rows      the sampled rows, or null to report none.
    * @param truncated whether a limit cut the sample short (rows left out, or a value replaced by a
    *                  marker) — a consumer extracting parameter values has to tell "this value is
    *                  not in the response" from "this value was not sampled".
    */
   public void setSampleRows(List<?> rows, boolean truncated) {
      this.sampleRows = rows;
      this.sampleRowsTruncated = truncated;
   }

   /**
    * Whether {@link #getSampleRows} left something out rather than copying every row faithfully.
    */
   public boolean isSampleRowsTruncated() {
      return sampleRowsTruncated;
   }

   /**
    * How many rows the caller asked to have reported back. 0, the default, means none — sampling is
    * OPT-IN, because rows are of no use to a caller that only wanted the column list and are paid
    * for in that caller's response.
    *
    * <p>An INPUT, and that makes it the mirror image of {@link #getSampleRows} in two ways. It is
    * NOT cleared by {@link #clone()}: the clone is the object that executes, so a limit the caller
    * set has to reach it or nothing is ever sampled. And it is not written to XML deliberately
    * rather than incidentally — a saved query that carried it would sample again on every later
    * render, holding rows of customer data on a query nobody is reading them from.</p>
    */
   public int getSampleRowLimit() {
      return sampleRowLimit;
   }

   public void setSampleRowLimit(int sampleRowLimit) {
      this.sampleRowLimit = sampleRowLimit;
   }

   /**
    * Copy query properties from an existing query.
    */
   public void copyInfo(TabularQuery query) {
   }

   @Override
   public TabularQuery clone() {
      TabularQuery copy = (TabularQuery) super.clone();
      copy.typemap = new HashMap<>(typemap);
      copy.fmtmap = new HashMap<>(fmtmap);
      copy.extentmap = new HashMap<>(extentmap);

      if(cols != null) {
         copy.cols = Arrays.copyOf(cols, cols.length);
      }

      if(variableTable != null) {
         copy.variableTable = variableTable.clone();
      }

      // CLEARED, not carried over. A shape describes ONE execution, and the clone is the object that
      // executes -- so it has to start with nothing to report, or "the runner recorded no shape this
      // time" becomes indistinguishable from "the runner recorded the same shape again".
      //
      // Without this the guarantee TabularHandler.execute's copy-back is written for does not hold.
      // super.clone() is shallow, so the clone would inherit whatever the ORIGINAL had from an
      // earlier run; a re-execution whose request then fails never reaches setResponseShape --
      // EndpointJsonQueryRunner.runStream catches its own exceptions and returns the table -- and the
      // copy-back would write the previous run's shape back onto the original as if it were current.
      copy.responseShape = null;
      copy.responseShapeTruncated = false;

      // Cleared for the same reason, and the reason applies harder here: these are rows of customer
      // data, so a clone inheriting them would report one execution's data as another's.
      copy.sampleRows = null;
      copy.sampleRowsTruncated = false;

      return copy;
   }

   private XTypeNode[] cols;
   /**
    * See {@link #getResponseShape}. Cleared by {@link #clone()} rather than shallow-copied, so it
    * always describes the execution that produced it.
    *
    * The tree itself is shared by reference once set (with the caller, and with whatever
    * {@code TabularHandler} copied it from), so it must not be mutated in place. That is enforced at
    * the producing end: {@code JsonShapeDistiller} returns a recursively unmodifiable structure.
    */
   private Object responseShape;
   private boolean responseShapeTruncated;
   /**
    * See {@link #getSampleRows}. Cleared by {@link #clone()}, same as {@code responseShape}, and
    * shared by reference once set in the same way — so it must not be mutated in place. Enforced at
    * the producing end: {@code JsonRowSampler} returns a recursively unmodifiable structure.
    */
   private List<?> sampleRows;
   private boolean sampleRowsTruncated;
   /**
    * See {@link #getSampleRowLimit}. Deliberately NOT cleared by {@link #clone()} — it is the
    * caller's request rather than an execution's result, and the clone is what executes.
    */
   private int sampleRowLimit;
   private Map<String, String> typemap = new HashMap<>();
   private Map<String, String> fmtmap = new HashMap<>();
   private Map<String, String> extentmap = new HashMap<>();
   private VariableTable variableTable;
   public static final String OUTER_TABLE_NAME_PROPERTY_PREFIX = "outer.table.name.";
   public static final String IS_OUTER_TABLE = "__is_outer_table__";
}
