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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XSourceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81075: the default aggregate formula of a logical model attribute must be looked up
 * from the column's own entity/attribute, not by re-parsing the display name, so names that
 * contain '.' or ':' still resolve. Pure mocks of the repository/worksheet; no Spring bean is
 * reached.
 */
@Tag("core")
class WSBindingHelperDefaultFormulaTest {
   private BoundTableAssembly table;
   private AnalyticRepository repository;
   private RuntimeWorksheet rws;
   private XLogicalModel lm;

   @BeforeEach
   void setUp() throws Exception {
      table = mock(BoundTableAssembly.class);
      repository = mock(AnalyticRepository.class);
      rws = mock(RuntimeWorksheet.class);
      lm = new XLogicalModel("LM");

      SourceInfo sinfo = new SourceInfo(XSourceInfo.MODEL, "DS", "LM");
      when(table.getSourceInfo()).thenReturn(sinfo);
      when(repository.getLogicalModel(eq("LM::DS"), any())).thenReturn(lm);

      addAttr("Ent", "Amt", "Max");
      addAttr("Ent", "Net.Amt", "Max");
      addAttr("Ent", "Net:Qty", "Sum");
      addAttr("Dot.Ent", "Amt", "Min");
      addAttr("Colon:Ent", "Net.Amt", "Average");
      addAttr("Ent", "Plain", "None");
   }

   private void addAttr(String entity, String attr, String formula) {
      XEntity xe = lm.getEntity(entity);

      if(xe == null) {
         xe = new XEntity(entity);
         lm.addEntity(xe);
      }

      XAttribute xa = new XAttribute(attr, "T", attr);
      xa.setDefaultFormula(formula);
      xe.addAttribute(xa);
   }

   private String formula(ColumnRef ref) throws Exception {
      return WSBindingHelper.getModelDefaultFormula(table, ref, rws, repository);
   }

   private static ColumnRef col(String entity, String attr) {
      return new ColumnRef(new AttributeRef(entity, attr));
   }

   @Test
   void plainNamesStillResolve() throws Exception {
      assertEquals("Max", formula(col("Ent", "Amt")));
   }

   @Test
   void attributeNameContainingDotResolves() throws Exception {
      assertEquals("Max", formula(col("Ent", "Net.Amt")));
   }

   @Test
   void attributeNameContainingColonResolves() throws Exception {
      assertEquals("Sum", formula(col("Ent", "Net:Qty")));
   }

   @Test
   void entityNameContainingDotOrColonResolves() throws Exception {
      assertEquals("Min", formula(col("Dot.Ent", "Amt")));
      assertEquals("Average", formula(col("Colon:Ent", "Net.Amt")));
   }

   @Test
   void aliasedColumnResolvesFromEntityAndAttribute() throws Exception {
      ColumnRef ref = col("Ent", "Net.Amt");
      ref.setAlias("Renamed");
      assertEquals("Max", formula(ref));
   }

   @Test
   void noneFormulaAndUnknownAttributeGiveNull() throws Exception {
      assertNull(formula(col("Ent", "Plain")));
      assertNull(formula(col("Ent", "Missing")));
      assertNull(formula(col("NoEnt", "Amt")));
   }

   @Test
   void refWithoutEntityGivesNull() throws Exception {
      assertNull(formula(new ColumnRef(new AttributeRef(null, "Amt"))));
   }
}
