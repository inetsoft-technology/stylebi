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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.TableLens;
import inetsoft.uql.DrillPath;
import inetsoft.uql.XDrillInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80475: get_hyperlink reported only a cell's own stored hyperlink, so a column carrying an
 * Auto Drill path from the logical model read back as "no link" while the Viewer showed the drill
 * menu. The drill paths are now reported as {@code inheritedLinks}.
 */
@Tag("core")
class AssemblyHyperlinkInheritedLinksTest {
   @Test
   void reportsEachDrillPathOfTheCell() {
      DrillPath web = new DrillPath("Search");
      web.setLinkType(DrillPath.WEB_LINK);
      web.setLink("https://example.com/q");
      web.setTargetFrame("_blank");
      web.setToolTip("look it up");
      web.setParameterField("id", "CUSTOMER_ID");
      web.setParameterType("id", "string");

      DrillPath vs = new DrillPath("Detail");
      vs.setLinkType(DrillPath.VIEWSHEET_LINK);
      vs.setLink("Reports/Detail");

      DrillPath odd = new DrillPath("Odd");
      odd.setLinkType(99);

      XDrillInfo info = new XDrillInfo();
      info.addDrillPath(web);
      info.addDrillPath(vs);
      info.addDrillPath(odd);

      TableLens lens = mock(TableLens.class);
      when(lens.getXDrillInfo(3, 1)).thenReturn(info);

      List<Map<String, Object>> links = AssemblyHyperlinkService.inheritedLinks(lens, 3, 1);

      assertEquals(3, links.size());
      Map<String, Object> first = links.get(0);
      assertEquals("dataModel", first.get("source"));
      assertEquals("Search", first.get("name"));
      assertEquals("web", first.get("linkType"));
      assertEquals("https://example.com/q", first.get("target"));
      assertEquals("_blank", first.get("targetFrame"));
      assertEquals("look it up", first.get("tooltip"));
      List<?> params = (List<?>) first.get("parameters");
      assertEquals(1, params.size());
      Map<?, ?> param = (Map<?, ?>) params.get(0);
      assertEquals("id", param.get("name"));
      assertEquals("CUSTOMER_ID", param.get("field"));
      assertEquals("string", param.get("type"));
      assertNotNull(param.get("hardCoded"));

      assertEquals("viewsheet", links.get(1).get("linkType"));
      assertEquals("Reports/Detail", links.get(1).get("target"));
      assertEquals(99, links.get(2).get("linkType"));
   }

   @Test
   void emptyWhenTheCellHasNoDrillInfo() {
      TableLens lens = mock(TableLens.class);

      assertTrue(AssemblyHyperlinkService.inheritedLinks(lens, 0, 0).isEmpty());
      assertTrue(AssemblyHyperlinkService.inheritedLinks(null, 0, 0).isEmpty());
   }

   @Test
   void emptyWhenTheDrillInfoHoldsNoPath() {
      TableLens lens = mock(TableLens.class);
      when(lens.getXDrillInfo(0, 0)).thenReturn(new XDrillInfo());

      assertTrue(AssemblyHyperlinkService.inheritedLinks(lens, 0, 0).isEmpty());
   }
}
