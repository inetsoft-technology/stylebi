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
package inetsoft.web.binding.service;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.drm.FormRefModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78981 item 3: a Form Table's details write whose model alias differs from the live
 * column's name looked the old column up by the new name, got null, and threw an NPE.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTableBindingFactoryFormLabelTest {
   private static FormRef formRef(String attribute, ColumnOption option) {
      FormRef ref = FormRef.toFormRef(new ColumnRef(new AttributeRef(null, attribute)));
      ref.setOption(option);
      return ref;
   }

   private static TableVSAssembly tableOf(FormRef live) {
      TableVSAssembly table = new TableVSAssembly(mock(Viewsheet.class), "Table1");
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(live);
      info.setColumnSelection(cols);
      info.setForm(true);
      return table;
   }

   private static TableBindingModel modelWithAlias(String attribute, String alias) {
      FormRef source = formRef(attribute, new TextColumnOption());
      source.setAlias(alias);
      TableBindingModel model = new TableBindingModel();
      model.getDetails().add(new FormRefModel(source));
      return model;
   }

   @Test
   void aliasMissingFromTheLiveColumnKeepsTheNewDefaultOptionInsteadOfThrowing() {
      TableVSAssembly table = tableOf(formRef("ORDER_ID", new IntegerColumnOption(9, 0, "bad", true)));

      assertDoesNotThrow(() -> VSTableBindingFactory.updateTableAssembly(
         modelWithAlias("ORDER_ID", "Order #"), table));

      ColumnSelection cols = ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getColumnSelection();
      assertEquals(1, cols.getAttributeCount());
      assertEquals("Order #", cols.getAttribute(0).getName());
   }

   @Test
   void liveColumnAliasedInPlaceHasItsOptionCarriedAcrossTheRelabel() {
      IntegerColumnOption integer = new IntegerColumnOption(9, 0, "bad", true);
      FormRef live = formRef("ORDER_ID", integer);
      live.setAlias("Order #");
      TableVSAssembly table = tableOf(live);

      VSTableBindingFactory.updateTableAssembly(modelWithAlias("ORDER_ID", "Order #"), table);

      ColumnSelection cols = ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getColumnSelection();
      assertSame(integer, ((FormRef) cols.getAttribute(0)).getOption());
      assertEquals("Order #", cols.getAttribute(0).getName());
   }
}
