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
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
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
 * Bug #80097: a visible cross-worksheet table mirror reads back the saved worksheet it was
 * copied from (path, scope, owner for user scope); same-worksheet mirrors, plain tables and a
 * mirror's hidden copies carry no {@code mirrorOf}, and the non-table mirror model names its
 * source scope.
 */
@Tag("core")
@WizAgentTestSupport
class TableMirrorOf80097Test {
   @Test
   void globalCrossWorksheetMirrorNamesPathAndScope() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly hidden = hiddenCopy(ws, "OUTER_src_0");
      ws.addAssembly(new MirrorTableAssembly(ws, "M", entry(AssetRepository.GLOBAL_SCOPE, null),
                                             true, hidden));

      WorksheetModel.MirrorOf of = table(read(ws), "M").mirrorOf();

      assertNotNull(of);
      assertEquals("Folder/src", of.path());
      assertEquals("global", of.scope());
      assertNull(of.owner());
   }

   @Test
   void userScopeMirrorNamesScopeAndOwner() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly hidden = hiddenCopy(ws, "OUTER_src_0");
      ws.addAssembly(new MirrorTableAssembly(
         ws, "M", entry(AssetRepository.USER_SCOPE, new IdentityID("alice", "host-org")),
         true, hidden));

      WorksheetModel.MirrorOf of = table(read(ws), "M").mirrorOf();

      assertNotNull(of);
      assertEquals("user", of.scope());
      assertEquals("alice", of.owner());
   }

   @Test
   void sameWorksheetMirrorAndPlainTableCarryNoMirrorOf() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly base = table(ws, "T");
      ws.addAssembly(base);
      ws.addAssembly(new MirrorTableAssembly(ws, "M", base));

      WorksheetModel model = read(ws);

      assertNull(table(model, "T").mirrorOf());
      assertNull(table(model, "M").mirrorOf());
   }

   @Test
   void hiddenNestedMirrorCopyCarriesNoMirrorOf() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly base = hiddenCopy(ws, "OUTER_base_0");
      MirrorTableAssembly nested = new MirrorTableAssembly(
         ws, "OUTER_mid_1", entry(AssetRepository.GLOBAL_SCOPE, null), true, base);
      nested.setOuter(true);
      ws.addAssembly(nested);
      ws.addAssembly(new MirrorTableAssembly(
         ws, "M", entry(AssetRepository.GLOBAL_SCOPE, null), true, nested));

      WorksheetModel model = read(ws);

      assertNull(table(model, "OUTER_mid_1").mirrorOf());
      assertNotNull(table(model, "M").mirrorOf());
   }

   @Test
   void nonTableMirrorNamesSourceScope() {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly v = new DefaultVariableAssembly(ws, "OUTER_var_0");
      v.setVariable(new AssetVariable("RATE"));
      v.setOuter(true);
      ws.addAssembly(v);
      ws.addAssembly(new MirrorVariableAssembly(
         ws, "MV", entry(AssetRepository.USER_SCOPE, new IdentityID("alice", "host-org")),
         true, v));

      WorksheetModel.MirrorModel mirror = read(ws).mirrors().stream()
         .filter(m -> m.name().equals("MV")).findFirst().orElseThrow();

      assertEquals("Folder/src", mirror.source());
      assertEquals("user", mirror.sourceScope());
   }

   private static AssetEntry entry(int scope, IdentityID owner) {
      return new AssetEntry(scope, AssetEntry.Type.WORKSHEET, "Folder/src", owner, "host-org");
   }

   private static EmbeddedTableAssembly hiddenCopy(Worksheet ws, String name) {
      EmbeddedTableAssembly t = table(ws, name);
      t.setOuter(true);
      ws.addAssembly(t);
      return t;
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

   private static WorksheetModel read(Worksheet ws) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return new WorksheetReadService().read(rws);
   }

   private static WorksheetModel.TableModel table(WorksheetModel model, String name) {
      return model.tables().stream().filter(t -> t.name().equals(name)).findFirst()
         .orElseThrow(() -> new AssertionError("no table " + name));
   }
}
