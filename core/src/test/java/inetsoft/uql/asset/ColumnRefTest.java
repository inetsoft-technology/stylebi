/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ColumnRefTest {
   /**
    * Regression test for a data-corruption bug: renaming a column's qualifying table (e.g. when
    * a worksheet dedup-merge renames a shared physical table, as in
    * WsMergeService#ensureBaseHasPrevMirror) rebuilds the column's AttributeRef but silently
    * dropped its dataType (AttributeRef's field defaults to null), so any join keyed on that
    * column downstream ends up typed null/"string" instead of its real type (e.g. "integer") --
    * breaking the join and any chart depending on it. Discovered via a StyleBI dashboard
    * composing two charts that share a physical source table.
    *
    * <p>Deliberately does NOT call {@code column.setDataType(...)} on the ColumnRef itself:
    * {@link ColumnRef#getDataType()} returns its own cached {@code dtype} first, falling back to
    * the wrapped ref's type only when that field is unset -- calling it here would mask the bug
    * (the ColumnRef's own cache would win regardless of what happens to the wrapped ref). This
    * matches how a join's own key ColumnRef is actually built -- typed only via its wrapped
    * AttributeRef, with no override on the ColumnRef itself.</p>
    */
   @Test
   void renameColumnPreservesDataType() {
      AttributeRef attr = new AttributeRef("PT", "id");
      attr.setDataType("integer");
      attr.setCaption("Product Template Id");
      ColumnRef column = new ColumnRef(attr);

      assertEquals("integer", column.getDataType(), "sanity check: type resolves through the wrapped ref before rename");

      ColumnRef.renameColumn(column, "PT", "PT_base");

      assertEquals("PT_base", column.getEntity());
      assertEquals("id", column.getAttribute());
      assertEquals("integer", column.getDataType(), "renaming a column's table qualifier must preserve its data type");
      assertEquals("Product Template Id", ((AttributeRef) column.getDataRef()).getCaption(), "caption must still be preserved");
   }

   @Test
   void renameColumnLeavesNonMatchingEntityUntouched() {
      AttributeRef attr = new AttributeRef("Other", "id");
      attr.setDataType("integer");
      ColumnRef column = new ColumnRef(attr);

      ColumnRef.renameColumn(column, "PT", "PT_base");

      assertEquals("Other", column.getEntity());
      assertEquals("integer", column.getDataType());
   }

   // =========================================================================
   // Data-type provenance (bug #77000/WBS-082) -- setDataType/setInferredDataType/
   // clearInferredDataType/getDataTypeProvenance, and their XML round-trip.
   // =========================================================================

   @Test
   void setDataTypeMarksProvenanceExplicit() {
      ColumnRef column = new ColumnRef(new AttributeRef("a"));
      column.setDataType("double");

      assertEquals(Boolean.FALSE, column.getDataTypeProvenance());
   }

   @Test
   void setInferredDataTypeMarksProvenanceInferred() {
      ColumnRef column = new ColumnRef(new AttributeRef("a"));
      column.setInferredDataType("double");

      assertEquals("double", column.getDataType());
      assertEquals(Boolean.TRUE, column.getDataTypeProvenance());
   }

   @Test
   void clearInferredDataTypeResetsToUntypedDefaultAndUnknownProvenance() {
      ColumnRef column = new ColumnRef(new AttributeRef("a"));
      column.setInferredDataType("double");

      column.clearInferredDataType();

      assertFalse(column.isDataTypeSet());
      assertNull(column.getDataTypeProvenance());
   }

   @Test
   void newColumnRefHasUnknownProvenanceUntilTypeIsSet() {
      ColumnRef column = new ColumnRef(new AttributeRef("a"));

      assertNull(column.getDataTypeProvenance());
   }

   @Test
   void xmlRoundTripPreservesInferredProvenance() throws Exception {
      ColumnRef column = new ColumnRef(new AttributeRef("a"));
      column.setInferredDataType("double");

      ColumnRef copy = (ColumnRef) writeAndParse(column);

      assertEquals("double", copy.getDataType());
      assertEquals(Boolean.TRUE, copy.getDataTypeProvenance());
   }

   @Test
   void xmlRoundTripPreservesExplicitProvenance() throws Exception {
      ColumnRef column = new ColumnRef(new AttributeRef("a"));
      column.setDataType("double");

      ColumnRef copy = (ColumnRef) writeAndParse(column);

      assertEquals("double", copy.getDataType());
      assertEquals(Boolean.FALSE, copy.getDataTypeProvenance());
   }

   /**
    * Simulates a worksheet persisted by a version of the product before the
    * {@code dataTypeInferred} XML attribute existed: the same XML {@code writeAndParse}
    * produces for an explicitly-typed column, but with that one attribute stripped out
    * before parsing. Confirms the legacy-migration default is "provenance unknown"
    * ({@code null}), not silently guessed as either explicit or inferred -- see
    * {@code WorksheetMutationSupport.isReInferEligible}'s fix notes (bug
    * #77000/WBS-082) for why that default matters.
    */
   @Test
   void legacyXmlWithoutProvenanceAttributeParsesAsUnknownProvenance() throws Exception {
      ColumnRef column = new ColumnRef(new AttributeRef("a"));
      column.setDataType("double");

      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      column.writeXML(writer);
      writer.flush();

      String legacyXml = sw.toString().replaceAll(" dataTypeInferred=\"[a-z]+\"", "");
      assertFalse(legacyXml.contains("dataTypeInferred"),
                  "sanity check: the provenance attribute must actually be stripped");

      ColumnRef copy = (ColumnRef) parseXml(legacyXml);

      assertEquals("double", copy.getDataType(),
                   "the data type itself must still round-trip from legacy XML");
      assertNull(copy.getDataTypeProvenance(),
                 "a worksheet saved before this field existed has no recorded provenance");
   }

   private static DataRef writeAndParse(DataRef ref) throws Exception {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      ref.writeXML(writer);
      writer.flush();

      return parseXml(sw.toString());
   }

   private static DataRef parseXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      Document doc = factory.newDocumentBuilder().parse(
         new ByteArrayInputStream(xml.getBytes()));
      Element elem = doc.getDocumentElement();

      return inetsoft.uql.erm.AbstractDataRef.createDataRef(elem);
   }
}
