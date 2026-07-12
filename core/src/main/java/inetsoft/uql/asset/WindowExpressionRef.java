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
    * Get the window frame start bound, e.g. {@code "PRECEDING"}, {@code "UNBOUNDED_PRECEDING"},
    * {@code "CURRENT_ROW"}. {@code null} means no explicit frame was set. Applies to any frame
    * mode (ROWS, RANGE, or GROUPS) — see {@link #getFrameMode()}.
    */
   public String getFrameStartBound() {
      return frameStartBound;
   }

   /**
    * Get the window frame start offset (meaningful only when {@link #getFrameStartBound()} is
    * {@code "PRECEDING"} or {@code "FOLLOWING"}).
    */
   public int getFrameStartOffset() {
      return frameStartOffset;
   }

   /**
    * Get the window frame end bound, e.g. {@code "FOLLOWING"}, {@code "UNBOUNDED_FOLLOWING"},
    * {@code "CURRENT_ROW"}. {@code null} means no explicit frame was set. Applies to any frame
    * mode (ROWS, RANGE, or GROUPS) — see {@link #getFrameMode()}.
    */
   public String getFrameEndBound() {
      return frameEndBound;
   }

   /**
    * Get the window frame end offset (meaningful only when {@link #getFrameEndBound()} is
    * {@code "PRECEDING"} or {@code "FOLLOWING"}).
    */
   public int getFrameEndOffset() {
      return frameEndOffset;
   }

   /**
    * Check whether an explicit window frame (ROWS, RANGE, or GROUPS) was set via
    * {@link #setFrame}.
    */
   public boolean hasExplicitFrame() {
      return frameStartBound != null;
   }

   /**
    * Get the frame mode: {@code ROWS}, {@code RANGE}, or {@code GROUPS}. Defaults to
    * {@code "ROWS"} when no explicit mode was set (Phase 3 frames / frame-less refs).
    */
   public String getFrameMode() {
      return frameMode == null ? "ROWS" : frameMode;
   }

   /**
    * Get the frame offset unit (e.g. {@code "day"}), used to render a date-valued {@code RANGE}
    * frame's {@code PRECEDING}/{@code FOLLOWING} offset as a dialect-specific {@code INTERVAL}
    * literal (via the {@code __WIZ_WINFRAME_INTERVAL(..)} token, expanded downstream by
    * {@code SQLHelper.formatWindowFrameInterval}) instead of a bare integer. {@code null} for
    * ROWS/GROUPS and numeric RANGE frames.
    */
   public String getFrameOffsetUnit() {
      return frameOffsetUnit;
   }

   /**
    * Set an explicit ROWS frame (legacy Phase-3 entry point). Bound values must be one of
    * {@code UNBOUNDED_PRECEDING}, {@code PRECEDING}, {@code CURRENT_ROW}, {@code FOLLOWING},
    * {@code UNBOUNDED_FOLLOWING}. Delegates to the 6-arg {@link #setFrame(String, String, int,
    * String, int, String)} with {@code mode="ROWS"} and no offset unit.
    *
    * @param startBound the frame start bound.
    * @param startOffset the frame start offset (used for PRECEDING/FOLLOWING).
    * @param endBound the frame end bound.
    * @param endOffset the frame end offset (used for PRECEDING/FOLLOWING).
    */
   public void setFrame(String startBound, int startOffset, String endBound, int endOffset) {
      setFrame("ROWS", startBound, startOffset, endBound, endOffset, null);
   }

   /**
    * Set an explicit frame. Bound values must be one of {@code UNBOUNDED_PRECEDING},
    * {@code PRECEDING}, {@code CURRENT_ROW}, {@code FOLLOWING}, {@code UNBOUNDED_FOLLOWING}.
    *
    * @param mode the frame mode: {@code ROWS}, {@code RANGE}, or {@code GROUPS}. {@code null} is
    *             normalized to {@code "ROWS"}.
    * @param startBound the frame start bound.
    * @param startOffset the frame start offset (used for PRECEDING/FOLLOWING).
    * @param endBound the frame end bound.
    * @param endOffset the frame end offset (used for PRECEDING/FOLLOWING).
    * @param offsetUnit the date interval unit (e.g. {@code "day"}) for a date-valued RANGE frame's
    *                   PRECEDING/FOLLOWING offset; {@code null} for a bare-integer offset (ROWS,
    *                   GROUPS, and numeric RANGE frames).
    */
   public void setFrame(String mode, String startBound, int startOffset, String endBound,
                         int endOffset, String offsetUnit)
   {
      requireValidFrameBound(startBound, startOffset);
      requireValidFrameBound(endBound, endOffset);
      String resolved = (mode == null) ? "ROWS" : mode.toUpperCase();
      requireValidFrameMode(resolved);
      requireValidFrameOffsetUnit(offsetUnit);
      // Store null for the default ROWS mode (not the resolved "ROWS" string) so writeAttributes'
      // `if(frameMode != null)` guard only emits the frameMode attribute for RANGE/GROUPS. This
      // preserves XML byte-parity with Phase 3 ROWS-frame worksheets, which never wrote this
      // attribute. getFrameMode() already maps a null field back to "ROWS".
      this.frameMode = "ROWS".equals(resolved) ? null : resolved;
      this.frameStartBound = startBound;
      this.frameStartOffset = startOffset;
      this.frameEndBound = endBound;
      this.frameEndOffset = endOffset;
      this.frameOffsetUnit = offsetUnit;
   }

   /** Recognized ROWS frame bound tokens (see {@link #setFrame}). */
   private static final Set<String> VALID_FRAME_BOUNDS = Set.of(
      "UNBOUNDED_PRECEDING", "PRECEDING", "CURRENT_ROW", "FOLLOWING", "UNBOUNDED_FOLLOWING");

   /** Recognized frame mode tokens (see {@link #setFrame}). */
   private static final Set<String> VALID_FRAME_MODES = Set.of("ROWS", "RANGE", "GROUPS");

   /** Recognized frame offset unit tokens (see {@link #setFrame}). */
   private static final Set<String> VALID_FRAME_OFFSET_UNITS = Set.of(
      "year", "quarter", "month", "week", "day", "hour", "minute", "second");

   /**
    * Guard against a caller constructing a {@code WindowExpressionRef} frame directly (bypassing
    * the wire-level validation in {@code WorksheetTableService}): reject an unrecognized bound
    * token, or a non-positive offset on a {@code PRECEDING}/{@code FOLLOWING} bound. Fixed bounds
    * (the two UNBOUNDED bounds and CURRENT_ROW) are not required to carry offset {@code 0} here
    * -- the normal gateway already normalizes those.
    */
   private static void requireValidFrameBound(String bound, int offset) {
      if(bound == null || !VALID_FRAME_BOUNDS.contains(bound)) {
         throw new IllegalArgumentException("invalid window frame bound: " + bound);
      }

      if(("PRECEDING".equals(bound) || "FOLLOWING".equals(bound)) && offset <= 0) {
         throw new IllegalArgumentException(
            "window frame bound '" + bound + "' requires a positive offset");
      }
   }

   /**
    * Guard against a caller constructing a {@code WindowExpressionRef} frame directly with an
    * unrecognized mode token, mirroring {@link #requireValidFrameBound}. {@code mode} must
    * already be uppercased/resolved (i.e. never {@code null}) by the caller.
    */
   private static void requireValidFrameMode(String mode) {
      if(!VALID_FRAME_MODES.contains(mode)) {
         throw new IllegalArgumentException("invalid window frame mode: " + mode);
      }
   }

   /**
    * Guard against a caller constructing a {@code WindowExpressionRef} frame directly with an
    * unrecognized offset unit token, mirroring {@link #requireValidFrameBound}. {@code null} is
    * always valid (a bare-integer offset).
    */
   private static void requireValidFrameOffsetUnit(String unit) {
      if(unit != null && !VALID_FRAME_OFFSET_UNITS.contains(unit)) {
         throw new IllegalArgumentException("invalid window frame offset unit: " + unit);
      }
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
    * of the non-empty PARTITION BY, ORDER BY, and ROWS frame fragments.
    */
   private String getOverBody() {
      List<String> fragments = new ArrayList<>();
      String partitionFrag = getPartitionFragment();
      String orderFrag = getOrderFragment();
      String frameFrag = getFrameFragment();

      if(!partitionFrag.isEmpty()) {
         fragments.add(partitionFrag);
      }

      if(!orderFrag.isEmpty()) {
         fragments.add(orderFrag);
      }

      if(!frameFrag.isEmpty()) {
         fragments.add(frameFrag);
      }

      return String.join(" ", fragments);
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
    * Get the {@code ROWS|RANGE|GROUPS BETWEEN ... AND ...} window frame fragment, or "" if no
    * frame clause should be emitted.
    * <p>
    * Frame-less aggregates and {@code FIRST_VALUE} emit no frame clause (byte-parity with the
    * pre-frame pushdown text — the database's default frame is what today's behavior relies on).
    * Frame-less {@code LAST_VALUE} is the one exception: without an explicit frame, most databases
    * default to {@code RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW}, which makes
    * {@code LAST_VALUE} return the current row instead of the last row of the partition — so an
    * explicit whole-partition frame is synthesized to give the function its expected semantics.
    */
   private String getFrameFragment() {
      if(frameStartBound == null) {
         return "LAST_VALUE".equalsIgnoreCase(fn) ?
            "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING" : "";
      }

      String mode = getFrameMode();   // ROWS | RANGE | GROUPS
      return mode + " BETWEEN " + frameBoundSql(frameStartBound, frameStartOffset)
         + " AND " + frameBoundSql(frameEndBound, frameEndOffset);
   }

   /**
    * Get the SQL text for a single frame bound.
    */
   private String frameBoundSql(String bound, int offset) {
      switch(bound) {
      case "UNBOUNDED_PRECEDING":
         return "UNBOUNDED PRECEDING";
      case "UNBOUNDED_FOLLOWING":
         return "UNBOUNDED FOLLOWING";
      case "CURRENT_ROW":
         return "CURRENT ROW";
      case "PRECEDING":
         return frameOffsetSql(offset) + " PRECEDING";
      case "FOLLOWING":
         return frameOffsetSql(offset) + " FOLLOWING";
      default:
         throw new RuntimeException("invalid window frame bound: " + bound);
      }
   }

   /**
    * Render the PRECEDING/FOLLOWING offset: a bare integer for ROWS/GROUPS/numeric-RANGE frames,
    * or -- when {@link #frameOffsetUnit} is set (a date-valued RANGE frame) -- a canonical
    * {@code __WIZ_WINFRAME_INTERVAL(<n>,<unit>)} token. This is deliberately NOT a rendered SQL
    * literal: the dialect-appropriate {@code INTERVAL} syntax varies (Postgres:
    * {@code INTERVAL '<n> <unit>'}; other dialects differ), so this ref stays database-agnostic
    * and defers to the dialect-rewrite pass ({@code PreAssetQuery.getExpressionColumn}, via
    * {@code SQLHelper.formatWindowFrameInterval}) that already rewrites this expression's
    * {@code field['..']} tokens per dialect. Kept token-shaped (parenthesized call syntax, like a
    * function) so it survives that rewrite intact and is unambiguous to scan for. The
    * {@code __WIZ_} prefix namespaces the token so it can never collide with user-authored
    * {@code sql:true} expression-column text (which shares this string namespace) -- a user would
    * never author this exact identifier by accident.
    */
   private String frameOffsetSql(int offset) {
      if(frameOffsetUnit != null) {
         return "__WIZ_WINFRAME_INTERVAL(" + offset + "," + frameOffsetUnit + ")";
      }

      return Integer.toString(offset);   // numeric offset — ANSI-standard, no rewrite needed
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

      if(frameStartBound != null) {
         writer.print(" frameStartBound=\"" + Tool.escape(frameStartBound) + "\"");
         writer.print(" frameStartOffset=\"" + frameStartOffset + "\"");
         writer.print(" frameEndBound=\"" + Tool.escape(frameEndBound) + "\"");
         writer.print(" frameEndOffset=\"" + frameEndOffset + "\"");

         if(frameMode != null) {
            writer.print(" frameMode=\"" + Tool.escape(frameMode) + "\"");
         }

         if(frameOffsetUnit != null) {
            writer.print(" frameOffsetUnit=\"" + Tool.escape(frameOffsetUnit) + "\"");
         }
      }

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
      frameStartBound = Tool.getAttribute(tag, "frameStartBound");
      String startOffsetStr = Tool.getAttribute(tag, "frameStartOffset");
      frameStartOffset = startOffsetStr == null ? 0 : Integer.parseInt(startOffsetStr);
      frameEndBound = Tool.getAttribute(tag, "frameEndBound");
      String endOffsetStr = Tool.getAttribute(tag, "frameEndOffset");
      frameEndOffset = endOffsetStr == null ? 0 : Integer.parseInt(endOffsetStr);
      frameMode = Tool.getAttribute(tag, "frameMode");
      frameOffsetUnit = Tool.getAttribute(tag, "frameOffsetUnit");
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
   private String frameStartBound;   // null = no explicit frame
   private int frameStartOffset;
   private String frameEndBound;
   private int frameEndOffset;
   private String frameMode;         // null = ROWS (default)
   private String frameOffsetUnit;   // non-null only for date-valued RANGE
}
