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

package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.HiddenColumns;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77560: a worksheet table whose merged selection is a single date column came back
 * with no data whenever any VPM of the data model hid a lower-case column whose name
 * contains "date" (e.g. customers.birthdate), even in an unrelated table. The old guard
 * AssetQuery.isGroupedByHiddenCols substring-matched the selection's type token
 * ("date", from ColumnSelection.toString()) against the hidden column names and made
 * getPreBaseTableLens return null.
 *
 * <p>The query is a CALLS_REAL_METHODS mock of AssetQuery so the real getPreBaseTableLens
 * runs; the VPM / HiddenColumns / ColumnSelection objects are real. Only the repository
 * lookup (to hand back the data model) and the SQL execution (XSessionManager) are stubbed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetQueryVpmHiddenDateColumnTest {
   private static final String DS_NAME = "orders_ds";

   private MockedStatic<XRepository> repositoryStatic;
   private MockedStatic<XSessionManager> sessionStatic;
   private XPrincipal user;
   private HiddenColumns hidden;

   @BeforeEach
   void setUp() throws Exception {
      // non-exempt user: XUtil.getUserRoles short-circuits for "unknown_user"
      user = new XPrincipal(new IdentityID("unknown_user", null),
                            new IdentityID[] { new IdentityID("viewer", null) },
                            new String[0], null);

      // VPM on the data model hides a column of an unrelated table, in lower case
      hidden = new HiddenColumns();
      hidden.addHiddenColumn(new AttributeRef("customers", "birthdate"));
      VirtualPrivateModel vpm = new VirtualPrivateModel("hideBirthdate");
      vpm.setHiddenColumns(hidden);

      XDataModel model = mock(XDataModel.class);
      when(model.getVirtualPrivateModelNames()).thenReturn(new String[] { "hideBirthdate" });
      when(model.getVirtualPrivateModel("hideBirthdate")).thenReturn(vpm);

      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(DS_NAME)).thenReturn(model);
      repositoryStatic = mockStatic(XRepository.class);
      repositoryStatic.when(XRepository::getRepository).thenReturn(repository);

      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "orderdate" },
         { new Date(0L) },
         { new Date(86_400_000L) }
      });
      data.setHeaderRowCount(1);

      XSessionManager manager = mock(XSessionManager.class);
      when(manager.getXNodeTableLens(any(XQuery.class), any(VariableTable.class), any(),
                                     any(), any(), anyLong()))
         .thenReturn(data);
      sessionStatic = mockStatic(XSessionManager.class);
      sessionStatic.when(XSessionManager::getSessionManager).thenReturn(manager);
   }

   @AfterEach
   void tearDown() {
      if(sessionStatic != null) {
         sessionStatic.close();
      }

      if(repositoryStatic != null) {
         repositoryStatic.close();
      }
   }

   @Test
   void dateGroupedTableReturnsDataWhenAnUnrelatedDateColumnIsHidden() throws Exception {
      // precondition: the VPM really hides customers.birthdate for this user
      assertArrayEquals(new String[] { "customers.birthdate" },
                        hidden.evaluate(new String[0], new String[0], new VariableTable(),
                                        user, false, null));

      AssetQuery query = createQuery(true);
      assertEquals("orders.orderdate(date)", query.gcolumns.toString(),
                   "the guard used to derive the needle 'date' from this string");

      TableLens lens = query.getPreBaseTableLens(new VariableTable());

      assertNotNull(lens, "a table grouped on orders.orderdate must not be blanked by a "
         + "VPM that hides customers.birthdate");
      assertEquals(2, countDataRows(lens));
   }

   @Test
   void singleDateColumnDetailTableReturnsDataWhenAnUnrelatedDateColumnIsHidden()
      throws Exception
   {
      AssetQuery query = createQuery(false);

      TableLens lens = query.getPreBaseTableLens(new VariableTable());

      assertNotNull(lens);
      assertEquals(2, countDataRows(lens));
   }

   private AssetQuery createQuery(boolean grouped) throws Exception {
      XDataSource source = mock(XDataSource.class);
      when(source.getFullName()).thenReturn(DS_NAME);

      JDBCQuery jdbcQuery = mock(JDBCQuery.class);
      when(jdbcQuery.getDataSource()).thenReturn(source);

      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("orders");

      SQLHelper helper = mock(SQLHelper.class);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getUser()).thenReturn(user);

      AssetQuery query = mock(AssetQuery.class, CALLS_REAL_METHODS);
      doReturn(jdbcQuery).when(query).getQuery();
      doReturn(table).when(query).getTable();
      doReturn(new UniformSQL()).when(query).getUniformSQL();
      doReturn(helper).when(query).getSQLHelper(any());
      doReturn(false).when(query).isDriversDataCached();
      doReturn(-1).when(query).getTimeout();

      ColumnRef orderDate = new ColumnRef(new AttributeRef("orders", "orderdate"));
      orderDate.setDataType(XSchema.DATE);
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(orderDate);

      query.box = box;
      query.mode = AssetQuerySandbox.RUNTIME_MODE;
      query.used = columns;
      query.gmerged = grouped;
      query.gcolumns = grouped ? columns.clone() : null;
      return query;
   }

   private static int countDataRows(TableLens lens) {
      int rows = 0;

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         rows++;
      }

      return rows;
   }
}
