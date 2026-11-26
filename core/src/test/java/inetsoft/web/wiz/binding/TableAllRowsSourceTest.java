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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Where "Include all rows" (Bug #82313) is allowed: the table or crosstab must read from one
 * logical model table, found through the mirrors that wrap it, on a data source that can do a
 * full outer join; turning it off is allowed whatever the table has become.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableAllRowsSourceTest {
   private static FieldRef dim(String column, Boolean allRows) {
      return new FieldRef(column, "dimension", null, null, null, null, null, null, null, null,
                          null, null, null, null, null, null, allRows);
   }

   /** A viewsheet whose base worksheet holds VS mirror -> M -> M_O -> bound (of {@code type}). */
   private static RuntimeViewsheet rvs(int sourceType) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly bound = new BoundTableAssembly(ws, "M_O");
      bound.setSourceInfo(new SourceInfo(sourceType, "Orders", "Order Model"));
      ws.addAssembly(bound);
      MirrorTableAssembly outer = new MirrorTableAssembly(ws, "Order Model", bound);
      ws.addAssembly(outer);
      ws.getVSTableAssembly("Order Model");

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(ws);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static SourceInfo assetSource() {
      return new SourceInfo(SourceInfo.ASSET, null, "Order Model");
   }

   /** Runs {@code body} with a repository whose data source does or does not full outer join. */
   private static void withModel(boolean fullOuterJoin, Runnable body) throws Exception {
      XRepository repository = mock(XRepository.class);
      XDataModel dataModel = mock(XDataModel.class);
      XLogicalModel lmodel = mock(XLogicalModel.class);
      XDataSource ds = mock(XDataSource.class);
      SQLHelper helper = mock(SQLHelper.class);
      when(repository.getDataModel("Orders")).thenReturn(dataModel);
      when(dataModel.getLogicalModel(eq("Order Model"), any())).thenReturn(lmodel);
      when(dataModel.getDataSource()).thenReturn("Orders");
      when(lmodel.getName()).thenReturn("Order Model");
      when(lmodel.getEntity("Product")).thenReturn(mock(XEntity.class));
      when(repository.getDataSource("Orders")).thenReturn(ds);
      when(ds.getName()).thenReturn("Orders");
      when(helper.supportsOperation(SQLHelper.FULL_OUTERJOIN)).thenReturn(fullOuterJoin);

      try(MockedStatic<XRepository> repositories = mockStatic(XRepository.class);
          MockedStatic<SQLHelper> helpers = mockStatic(SQLHelper.class))
      {
         repositories.when(XRepository::getRepository).thenReturn(repository);
         helpers.when(() -> SQLHelper.getSQLHelper(ds)).thenReturn(helper);
         body.run();
      }
   }

   @Test
   void aTableOverAMirrorChainOfALogicalModelIsAccepted() throws Exception {
      withModel(true, () -> {
         CrosstabBindingModel model = new CrosstabBindingModel();

         TableAllRows.apply(model, List.of(dim("Product:Category", true)),
                            rvs(SourceInfo.MODEL), assetSource());

         assertEquals(List.of("Product"), model.getAllRows());
      });
   }

   @Test
   void theSourceNamedByThePrefixedViewsheetTableIsFoundToo() throws Exception {
      withModel(true, () -> {
         CrosstabBindingModel model = new CrosstabBindingModel();

         TableAllRows.apply(model, List.of(dim("Product:Category", true)), rvs(SourceInfo.MODEL),
                            new SourceInfo(SourceInfo.ASSET, null,
                                           TableAssembly.TABLE_VS + "Order Model"));

         assertEquals(List.of("Product"), model.getAllRows());
      });
   }

   @Test
   void aDataSourceWithoutFullOuterJoinIsRefusedAndTheMessageSaysSo() throws Exception {
      withModel(false, () -> {
         CrosstabBindingModel model = new CrosstabBindingModel();

         IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> TableAllRows.apply(model, List.of(dim("Product:Category", true)),
                                     rvs(SourceInfo.MODEL), assetSource()));

         assertTrue(e.getMessage().contains("'Product:Category'"), e.getMessage());
         assertTrue(e.getMessage().contains("data source 'Orders'"), e.getMessage());
         assertTrue(e.getMessage().contains("does not support full outer joins"), e.getMessage());
         assertNull(model.getAllRows());
      });
   }

   @Test
   void aSourceThatIsNotALogicalModelIsRefused() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> TableAllRows.apply(model, List.of(dim("Product:Category", true)),
                                  rvs(SourceInfo.PHYSICAL_TABLE), assetSource()));

      assertTrue(e.getMessage().contains("logical model"), e.getMessage());
   }

   @Test
   void aFieldOfNoTableOfTheModelIsRefused() throws Exception {
      withModel(true, () -> {
         CrosstabBindingModel model = new CrosstabBindingModel();

         IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> TableAllRows.apply(model, List.of(dim("Total Price", true)),
                                     rvs(SourceInfo.MODEL), assetSource()));

         assertTrue(e.getMessage().contains("calculated or formula field"), e.getMessage());
      });
   }

   @Test
   void turningItOffNeedsNoSourceAndWorksOnAnyTable() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setAllRows(List.of("Product", "Order"));

      // the source is not a logical model any more, and not even given: off is still off
      TableAllRows.apply(model, List.of(dim("Product:Category", false)),
                         rvs(SourceInfo.PHYSICAL_TABLE), assetSource());
      assertEquals(List.of("Order"), model.getAllRows());

      TableAllRows.apply(model, List.of(dim("Order:Date", false)), null, null);
      assertNull(model.getAllRows());
   }

   @Test
   void turningItOffOnADataSourceThatCannotFullOuterJoinStillWorks() throws Exception {
      withModel(false, () -> {
         CrosstabBindingModel model = new CrosstabBindingModel();
         model.setAllRows(List.of("Product"));

         TableAllRows.apply(model, List.of(dim("Product:Category", false)),
                            rvs(SourceInfo.MODEL), assetSource());

         assertNull(model.getAllRows());
      });
   }
}
