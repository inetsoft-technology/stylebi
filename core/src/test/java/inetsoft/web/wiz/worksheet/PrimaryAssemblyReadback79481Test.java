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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79481: read_worksheet_model reports {@code primary} for every assembly kind that
 * set_primary_assembly accepts (named group, variable, mirror), not only tables, and names the
 * primary assembly of any kind in {@code primaryAssembly}.
 */
@Tag("core")
@WizAgentTestSupport
class PrimaryAssemblyReadback79481Test {
   @Test
   void namedGroupPrimaryTrueThenFalseAfterSwitchingToTable() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "T"));
      ws.addAssembly(new DefaultNamedGroupAssembly(ws, "NG"));

      ws.setPrimaryAssembly("NG");
      WorksheetModel m = read(ws);
      assertTrue(m.namedGroups().get(0).primary());
      assertFalse(m.tables().get(0).primary());
      assertEquals("NG", m.primaryAssembly());

      ws.setPrimaryAssembly("T");
      m = read(ws);
      assertFalse(m.namedGroups().get(0).primary());
      assertTrue(m.tables().get(0).primary());
      assertEquals("T", m.primaryAssembly());
   }

   @Test
   void variablePrimary() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "T"));
      DefaultVariableAssembly v = new DefaultVariableAssembly(ws, "RATE");
      v.setVariable(new AssetVariable("RATE"));
      ws.addAssembly(v);

      ws.setPrimaryAssembly("RATE");
      WorksheetModel m = read(ws);
      assertTrue(m.variables().get(0).primary());
      assertEquals("RATE", m.primaryAssembly());

      ws.setPrimaryAssembly("T");
      assertFalse(read(ws).variables().get(0).primary());
   }

   @Test
   void mirrorPrimary() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly src = table(ws, "src");
      ws.addAssembly(src);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", ASSET, true, src);
      ws.addAssembly(mirror);

      ws.setPrimaryAssembly("M");
      WorksheetModel m = read(ws);
      WorksheetModel.TableModel mt = m.tables().stream()
         .filter(t -> t.name().equals("M")).findFirst().orElseThrow();
      assertTrue(mt.primary());
      assertEquals("M", m.primaryAssembly());
   }

   @Test
   void outerMirrorAssemblyPrimaryFlag() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "T"));
      DefaultVariableAssembly inner = new DefaultVariableAssembly(ws, "RATE");
      inner.setVariable(new AssetVariable("RATE"));
      MirrorVariableAssembly mv = new MirrorVariableAssembly(ws, "MV", ASSET, true, inner);
      ws.addAssembly(mv);

      ws.setPrimaryAssembly("MV");
      WorksheetModel m = read(ws);
      assertTrue(m.mirrors().stream().filter(x -> x.name().equals("MV")).findFirst()
                    .orElseThrow().primary());

      ws.setPrimaryAssembly("T");
      assertFalse(read(ws).mirrors().stream().filter(x -> x.name().equals("MV")).findFirst()
                     .orElseThrow().primary());
   }

   private static final AssetEntry ASSET = new AssetEntry(
      AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ext", null);

   private static WorksheetModel read(Worksheet ws) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return new WorksheetReadService().read(rws);
   }

   private static EmbeddedTableAssembly table(Worksheet ws, String name) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "id"));
      ref.setDataType(XSchema.INTEGER);
      cs.addAttribute(ref);
      t.setColumnSelection(cs, false);
      return t;
   }
}
