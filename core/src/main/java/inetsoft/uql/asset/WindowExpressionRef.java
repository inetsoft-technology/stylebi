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
package inetsoft.uql.asset;

import inetsoft.uql.XConstants;
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;

/**
 * WindowExpressionRef represents a structured SQL window (analytic) function column, e.g.
 * {@code ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...)}.
 * <p>
 * This is a first-class model of the window column so callers (worksheet table binding, wiz
 * pushdown) don't need to hand-author an opaque {@code sql:true} expression string. The
 * synthesized {@link #getExpression()} text is byte-for-byte identical to the text the wiz
 * {@code expandWindowColumns} helper produces today, so SQL pushdown parity is preserved.
 * <p>
 * The column-reference token form emitted is {@code field['<name>']}, exactly like
 * {@link DateRangeRef}, so {@code PreAssetQuery.getExpressionColumn} rewrites the tokens into
 * dialect-quoted identifiers. No direct {@code SQLHelper} quoting is performed here.
 *
 * @version 14.0
 * @author InetSoft Technology Corp
 */
public final class WindowExpressionRef extends ExpressionRef implements SQLExpressionRef
{
   // NOTE: intentionally NOT a DataRefWrapper. A window function references MULTIPLE columns
   // (arg + partitionBy + orderBy), exposed via getAttributes() — not a single wrapped ref. If it
   // were a DataRefWrapper, AssetUtil.getBaseAttribute would unwrap a ColumnRef(WindowExpressionRef)
   // straight past this ref to getDataRef()==argRef, which is null for ROW_NUMBER/RANK/DENSE_RANK/
   // NTILE/PERCENT_RANK/CUME_DIST — NPE-ing query validation and defeating the AssetQuery window
   // pushdown guard (which relies on getBaseAttribute returning this ref). Treated as a plain
   // expression attribute instead, exactly like ExpressionRef / DateRangeRef.
   /**
    * Constructor.
    */
   public WindowExpressionRef() {
      super();
   }

   /**
    * Constructor.
    *
    * @param fn the window function name, e.g. "ROW_NUMBER", "NTILE", "LAG", "SUM". This is a
    *           plain string (not an enum) — the set of supported functions is validated by the
    *           caller (wiz).
    * @param argRef the column argument ref for functions that take one (LAG/LEAD/SUM/AVG/COUNT/
    *               MIN/MAX/FIRST_VALUE/LAST_VALUE); {@code null} for ROW_NUMBER/RANK/DENSE_RANK/
    *               PERCENT_RANK/CUME_DIST/NTILE.
    * @param n the numeric argument. Used as the tile count for NTILE (required, must be &gt; 0),
    *          or the optional offset for LAG/LEAD (a value &lt;= 0 means "not specified" and is
    *          omitted from the emitted call). Ignored by all other functions.
    * @param partitionBy the PARTITION BY column refs, in order; may be empty.
    * @param orderBy the ORDER BY sort refs, in order; may be empty.
    */
   public WindowExpressionRef(String fn, DataRef argRef, int n, List<DataRef> partitionBy,
                               List<SortRef> orderBy)
   {
      this();
      this.fn = fn;
      this.argRef = argRef;
      this.n = n;
      this.partitionBy = partitionBy == null ? new ArrayList<>() : partitionBy;
      this.orderBy = orderBy == null ? new ArrayList<>() : orderBy;
   }

   /**
    * Get the window function name.
    */
   public String getFn() {
      return fn;
   }

   /**
    * Set the window function name.
    */
   public void setFn(String fn) {
      this.fn = fn;
   }

   /**
    * Get the column argument ref.
    */
   public DataRef getArgRef() {
      return argRef;
   }

   /**
    * Set the column argument ref.
    */
   public void setArgRef(DataRef argRef) {
      this.argRef = argRef;
   }

   /**
    * Get the numeric argument (NTILE tile count, or LAG/LEAD offset).
    */
   public int getN() {
      return n;
   }

   /**
    * Set the numeric argument (NTILE tile count, or LAG/LEAD offset).
    */
   public void setN(int n) {
      this.n = n;
   }

   /**
    * Get the PARTITION BY column refs.
    */
   public List<DataRef> getPartitionBy() {
      return partitionBy;
   }

   /**
    * Set the PARTITION BY column refs.
    */
   public void setPartitionBy(List<DataRef> partitionBy) {
      this.partitionBy = partitionBy == null ? new ArrayList<>() : partitionBy;
   }

   /**
    * Get the ORDER BY sort refs.
    */
   public List<SortRef> getOrderBy() {
      return orderBy;
   }

   /**
    * Set the ORDER BY sort refs.
    */
   public void setOrderBy(List<SortRef> orderBy) {
      this.orderBy = orderBy == null ? new ArrayList<>() : orderBy;
   }

   /**
    * Get the database version.
    */
   @Override
   public String getDBVersion() {
      return dbversion;
   }

   /**
    * Set the database version.
    */
   @Override
   public void setDBVersion(String version) {
      this.dbversion = version;
   }

   /**
    * Get the contained data ref (the column argument ref). Alias for {@link #getArgRef()};
    * retained for callers that discover an expression's single wrapped ref generically.
    */
   public DataRef getDataRef() {
      return argRef;
   }

   /**
    * Set the contained data ref (the column argument ref). Alias for {@link #setArgRef(DataRef)}.
    */
   public void setDataRef(DataRef ref) {
      this.argRef = ref;
   }

   /**
    * Get the SQL expression of this reference: {@code <fn>(<argFrag>) OVER (<partitionFrag>
    * <orderFrag>)}. This is emitted verbatim regardless of database type — the {@code field['..']}
    * tokens are rewritten downstream by {@code PreAssetQuery.getExpressionColumn}, mirroring the
    * wiz {@code expandWindowColumns} text exactly (pushdown parity contract).
    */
   @Override
   public String getExpression() {
      StringBuilder sb = new StringBuilder();
      sb.append(getFuncFragment());
      sb.append(" OVER (");
      sb.append(getOverBody());
      sb.append(")");

      return sb.toString();
   }

   /**
    * Get the function-call fragment, e.g. {@code ROW_NUMBER()}, {@code NTILE(4)},
    * {@code LAG(field['amount'], 1)}, {@code SUM(field['amount'])}.
    */
   private String getFuncFragment() {
      switch(fn) {
      case "ROW_NUMBER":
      case "RANK":
      case "DENSE_RANK":
      case "PERCENT_RANK":
      case "CUME_DIST":
         return fn + "()";
      case "NTILE":
         return "NTILE(" + n + ")";
      case "LAG":
      case "LEAD":
         return n > 0 ?
            fn + "(" + fieldToken(argRef) + ", " + n + ")" :
            fn + "(" + fieldToken(argRef) + ")";
      default:
         // SUM/AVG/COUNT/MIN/MAX/FIRST_VALUE/LAST_VALUE and any other single-column-arg function.
         return fn + "(" + fieldToken(argRef) + ")";
      }
   }

   /**
    * Get the OVER clause body (without the surrounding parens): the space-joined concatenation
    * of the PARTITION BY and ORDER BY fragments, omitting either when empty.
    */
   private String getOverBody() {
      String partitionFrag = getPartitionFragment();
      String orderFrag = getOrderFragment();

      if(!partitionFrag.isEmpty() && !orderFrag.isEmpty()) {
         return partitionFrag + " " + orderFrag;
      }

      return partitionFrag + orderFrag;
   }

   /**
    * Get the {@code PARTITION BY field['p1'], field['p2']} fragment, or "" if there is no
    * partitioning.
    */
   private String getPartitionFragment() {
      if(partitionBy == null || partitionBy.isEmpty()) {
         return "";
      }

      StringBuilder sb = new StringBuilder("PARTITION BY ");

      for(int i = 0; i < partitionBy.size(); i++) {
         if(i > 0) {
            sb.append(", ");
         }

         sb.append(fieldToken(partitionBy.get(i)));
      }

      return sb.toString();
   }

   /**
    * Get the {@code ORDER BY field['o1'] DESC, field['o2'] ASC} fragment, or "" if there is no
    * ordering.
    */
   private String getOrderFragment() {
      if(orderBy == null || orderBy.isEmpty()) {
         return "";
      }

      StringBuilder sb = new StringBuilder("ORDER BY ");

      for(int i = 0; i < orderBy.size(); i++) {
         if(i > 0) {
            sb.append(", ");
         }

         SortRef sort = orderBy.get(i);
         sb.append(fieldToken(sort.getDataRef()));
         sb.append(sort.getOrder() == XConstants.SORT_ASC ? " ASC" : " DESC");
      }

      return sb.toString();
   }

   /**
    * Get the {@code field['<name>']} token for a data ref. This matches the token form
    * {@link DateRangeRef#getExpression()} uses, and the exact form wiz's {@code expandWindowColumns}
    * emits.
    */
   private static String fieldToken(DataRef ref) {
      return "field['" + ref.getName() + "']";
   }

   /**
    * Check if this expression is a sql expression.
    * @return true if is, false otherwise.
    */
   @Override
   public boolean isSQL() {
      return true;
   }

   /**
    * Check if expression is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpressionEditable() {
      return false;
   }

   /**
    * Check if this window expression ref is mergeable. The emitted text is a plain OVER(...)
    * expression with {@code field['..']} tokens, which is always rewritable regardless of
    * database type (the pushdown parity contract), so this is unconditionally mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isMergeable() {
      return true;
   }

   // getAttributes() is intentionally NOT overridden. The inherited ExpressionRef implementation
   // parses the field['..'] tokens out of getExpression() and yields AttributeRefs — which is the
   // contract the query framework relies on (PreAssetQuery.getExprAttributes casts each contained
   // ref to AttributeRef). Every column this window references (arg + partitionBy + orderBy) is
   // emitted as a field['..'] token by getExpression(), so token-parsing captures them all. An
   // override returning the structured ColumnRefs (from the wire's ColumnSelection.getAttribute)
   // would ClassCastException during column-selection validation.

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      // Chain to ExpressionRef so the base attributes (name/dataType/refType) are persisted.
      // Unlike DateRangeRef (which derives getName() from its option+attr and so needs no stored
      // name), this ref carries a caller-assigned column name/alias — dropping super here would
      // lose it across the worksheet's XML serialize/reload cycle, and the column would reload
      // nameless (unbindable by create_viewsheet).
      super.writeAttributes(writer);
      writer.print(" fn=\"" + Tool.escape(fn) + "\"");
      writer.print(" n=\"" + n + "\"");

      if(getDBType() != null) {
         writer.print(" dbType=\"" + Tool.escape(getDBType()) + "\"");
      }

      if(dbversion != null) {
         writer.print(" dbVersion=\"" + Tool.escape(dbversion) + "\"");
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      // Restore the base attributes (name/dataType/refType) written by super.writeAttributes.
      super.parseAttributes(tag);
      fn = Tool.getAttribute(tag, "fn");
      String nstr = Tool.getAttribute(tag, "n");
      n = nstr == null ? 0 : Integer.parseInt(nstr);
      setDBType(Tool.getAttribute(tag, "dbType"));
      dbversion = Tool.getAttribute(tag, "dbVersion");
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      if(argRef != null) {
         writer.print("<argRef>");
         argRef.writeXML(writer);
         writer.println("</argRef>");
      }

      writer.print("<partitionBy>");

      for(DataRef ref : partitionBy) {
         ref.writeXML(writer);
      }

      writer.println("</partitionBy>");

      writer.print("<orderBy>");

      for(SortRef sort : orderBy) {
         sort.writeXML(writer);
      }

      writer.println("</orderBy>");
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element argNode = Tool.getChildNodeByTagName(tag, "argRef");

      if(argNode != null) {
         Element dnode = Tool.getChildNodeByTagName(argNode, "dataRef");
         argRef = dnode == null ? null : createDataRef(dnode);
      }
      else {
         argRef = null;
      }

      partitionBy = new ArrayList<>();
      Element partitionNode = Tool.getChildNodeByTagName(tag, "partitionBy");

      if(partitionNode != null) {
         NodeList nodes = Tool.getChildNodesByTagName(partitionNode, "dataRef");

         for(int i = 0; i < nodes.getLength(); i++) {
            partitionBy.add(createDataRef((Element) nodes.item(i)));
         }
      }

      orderBy = new ArrayList<>();
      Element orderNode = Tool.getChildNodeByTagName(tag, "orderBy");

      if(orderNode != null) {
         NodeList nodes = Tool.getChildNodesByTagName(orderNode, "dataRef");

         for(int i = 0; i < nodes.getLength(); i++) {
            orderBy.add((SortRef) createDataRef((Element) nodes.item(i)));
         }
      }
   }

   /**
    * Create a copy of this object. Deep-copies the {@code partitionBy}/{@code orderBy} lists and
    * the {@code argRef} — the inherited {@code AbstractDataRef.clone()} is shallow, which would
    * otherwise let a clone's mutations leak back into the original's lists.
    */
   @Override
   public Object clone() {
      WindowExpressionRef ref = (WindowExpressionRef) super.clone();

      ref.argRef = argRef == null ? null : (DataRef) argRef.clone();

      ref.partitionBy = new ArrayList<>();

      for(DataRef p : partitionBy) {
         ref.partitionBy.add((DataRef) p.clone());
      }

      ref.orderBy = new ArrayList<>();

      for(SortRef s : orderBy) {
         ref.orderBy.add((SortRef) s.clone());
      }

      return ref;
   }

   private DataRef argRef;
   private String fn;
   private int n;
   private List<DataRef> partitionBy = new ArrayList<>();
   private List<SortRef> orderBy = new ArrayList<>();
   private transient String dbversion;
}
