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
package inetsoft.web.viewsheet.model.table;

import inetsoft.report.composition.VSTableLens;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.viewsheet.model.VSFormatModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The format-model cache is keyed by format alone, so the model it holds must already carry the
 * padding. A seeded padding is on every cell of a marked table: a cached model built without it
 * would miss on every cell and rebuild the format model per cell instead of per format.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BaseTableCellModelFormatCacheTest {
   @Test
   void cellsSharingAFormatAndASeededPaddingShareOneModel() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(new Insets(4, 6, 4, 6), CompositeValue.Type.DEFAULT);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      Map<BaseTableCellModel.FullHashObjWrapper, VSFormatModel> cache = new HashMap<>();

      BaseTableCellModel first = BaseTableCellModel.createTableCell(info, lens, 1, 0, 1, cache);
      BaseTableCellModel second = BaseTableCellModel.createTableCell(info, lens, 2, 0, 2, cache);

      assertEquals(1, cache.size(), "precondition: both cells share one format");
      assertEquals(new Insets(4, 6, 4, 6), first.getVsFormatModel().getPadding());
      assertSame(first.getVsFormatModel(), second.getVsFormatModel());
   }

   @Test
   void aTableWithNoPaddingStillSharesOneModel() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(null, CompositeValue.Type.DEFAULT);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      Map<BaseTableCellModel.FullHashObjWrapper, VSFormatModel> cache = new HashMap<>();

      BaseTableCellModel first = BaseTableCellModel.createTableCell(info, lens, 1, 0, 1, cache);
      BaseTableCellModel second = BaseTableCellModel.createTableCell(info, lens, 2, 0, 2, cache);

      assertNull(first.getVsFormatModel().getPadding());
      assertSame(first.getVsFormatModel(), second.getVsFormatModel());
   }
}
