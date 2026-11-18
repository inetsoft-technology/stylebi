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
package inetsoft.report.composition;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.filter.ColumnHighlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80476: the fields a Table highlight condition or a hyperlink parameter reads must reach the
 * trap check even when the table's own column lookup does not list them (here: no live viewsheet,
 * so the lookup is empty), otherwise a highlight or link that introduces a data-model trap is
 * never reported.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSModelContextTableFieldsTest {
   @Test
   void aHighlightConditionFieldIsCollectedWhenTheColumnLookupMissesIt() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      ColumnRef ref = new ColumnRef(new AttributeRef("Contact", "FIRST_NAME"));
      Condition cond = new Condition();
      cond.addValue("a");
      cond.setOperation(XCondition.EQUAL_TO);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref, cond, 0));
      ColumnHighlight highlight = new ColumnHighlight();
      highlight.setConditionGroup(list);
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("h1", highlight);
      TableHighlightAttr attr = new TableHighlightAttr();
      TableDataPath path = new TableDataPath("Customer:STATE");
      path.setType(TableDataPath.DETAIL);
      attr.setHighlight(path, group);
      info.setHighlightAttr(attr);

      HashSet<DataRef> all = collect(info);

      assertEquals(1, all.size(), all.toString());
      assertEquals("FIRST_NAME", all.iterator().next().getAttribute());
   }

   @Test
   void aHyperlinkParameterFieldIsCollectedWhenTheColumnLookupMissesIt() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      Hyperlink link = new Hyperlink("https://example.com");
      link.setParameterField("p", "Contact:FIRST_NAME");
      TableHyperlinkAttr attr = new TableHyperlinkAttr();
      TableDataPath path = new TableDataPath("Customer:STATE");
      path.setType(TableDataPath.DETAIL);
      attr.setHyperlink(path, link);
      info.setHyperlinkAttr(attr);

      HashSet<DataRef> all = collect(info);

      assertEquals(1, all.size(), all.toString());
      assertEquals("Contact:FIRST_NAME", all.iterator().next().getAttribute());
   }

   @Test
   void aTableWithNeitherCollectsNothing() {
      assertTrue(collect(new TableVSAssemblyInfo()).isEmpty());
   }

   private static HashSet<DataRef> collect(TableVSAssemblyInfo info) {
      RuntimeViewsheet rvs = Mockito.mock(RuntimeViewsheet.class);
      VSModelContext context = new VSModelContext(rvs, null);
      HashSet<DataRef> all = new HashSet<>();
      context.getAttributes(info, all, new HashSet<>());
      return all;
   }
}
