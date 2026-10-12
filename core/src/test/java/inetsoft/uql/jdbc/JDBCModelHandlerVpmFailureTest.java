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
package inetsoft.uql.jdbc;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #77629. {@link JDBCModelHandler#prepareQuery} must fail closed when the vpm can't
 * be applied, rather than log the error and return the query without the vpm.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  JDBCModelHandlerVpmFailureTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCModelHandlerVpmFailureTest {
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:bug77629");
      ds.setRequireLogin(false);

      XLogicalModel lm = new XLogicalModel("lm");
      lm.setPartition("p");
      lm.setDataSource("ds");
      XEntity entity = new XEntity("Customers");
      entity.addAttribute(new XAttribute("ID", "CUSTOMERS", "ID"));
      entity.addAttribute(new XAttribute("STATE", "CUSTOMERS", "STATE"));
      lm.addEntity(entity);
      XPartition partition = new XPartition("p");
      partition.addTable("CUSTOMERS", null, null);

      model = mock(XDataModel.class);
      lm.setDataModel(model);
      when(model.getDataSource()).thenReturn("ds");
      when(model.getLogicalModel(eq("lm"), any())).thenReturn(lm);
      when(model.getPartition(eq("p"), any())).thenReturn(partition);
      when(repository.getDataSource("ds")).thenReturn(ds);
      vpm.failure = null;
      vpm.failHiddenColumns = false;
   }

   @AfterEach
   void tearDown() {
      reset(repository);
   }

   // control: a vpm that applies is kept, and JDBCHandler isn't asked to apply it again
   @Test
   void appliedVpmIsReturned() {
      JDBCQuery query = prepare();

      assertTrue(query.getProperty(APPLIED) != null, "vpm not applied");
      assertFalse(query.isVPMEnabled());
   }

   @Test
   void conditionsRuntimeExceptionPropagates() {
      RuntimeException failure = new RuntimeException("Query rejected by VPM");
      vpm.failure = failure;

      RuntimeException ex = assertThrows(RuntimeException.class, this::prepare);
      assertSame(failure, ex);
   }

   @Test
   void conditionsCheckedExceptionPropagatesWithCause() {
      Exception failure = new Exception("condition script failed");
      vpm.failure = failure;

      RuntimeException ex = assertThrows(RuntimeException.class, this::prepare);
      assertSame(failure, ex.getCause());
      assertEquals(failure.getMessage(), ex.getMessage());
   }

   @Test
   void hiddenColumnsRuntimeExceptionPropagates() {
      RuntimeException failure = new RuntimeException("Query rejected by VPM");
      vpm.failure = failure;
      vpm.failHiddenColumns = true;

      RuntimeException ex = assertThrows(RuntimeException.class, this::prepare);
      assertSame(failure, ex);
   }

   @Test
   void hiddenColumnsCheckedExceptionPropagatesWithCause() {
      Exception failure = new Exception("hidden columns trigger failed");
      vpm.failure = failure;
      vpm.failHiddenColumns = true;

      RuntimeException ex = assertThrows(RuntimeException.class, this::prepare);
      assertSame(failure, ex.getCause());
      assertEquals(failure.getMessage(), ex.getMessage());
   }

   private JDBCQuery prepare() {
      XDataSelection selection = new XDataSelection(true);
      selection.setSource("ds.lm");
      selection.addAttribute(new AttributeRef("Customers", "ID"));
      selection.addAttribute(new AttributeRef("Customers", "STATE"));
      JDBCModelHandler handler = new JDBCModelHandler(new JDBCHandler());
      return (JDBCQuery) handler.prepareQuery(selection, model, new VariableTable(), USER);
   }

   /**
    * Applies the vpm by marking a clone of the query, or throws the configured failure from
    * applyConditions or applyHiddenColumns.
    */
   private static final class TestVpmProcessor extends VpmProcessor {
      @Override
      public XQuery applyConditions(XQuery query, VariableTable vars, boolean checkVariable,
                                    Principal user) throws Exception
      {
         if(failure != null && !failHiddenColumns) {
            throw failure;
         }

         XQuery clone = (XQuery) query.clone();
         clone.setProperty(APPLIED, "true");
         return clone;
      }

      @Override
      public XQuery applyHiddenColumns(XQuery query, VariableTable vars, Principal user)
         throws Exception
      {
         if(failure != null && failHiddenColumns) {
            throw failure;
         }

         return (XQuery) query.clone();
      }

      private Exception failure;
      private boolean failHiddenColumns;
   }

   private static Field vpmProcessorField() throws Exception {
      Field field = VpmProcessor.class.getDeclaredField("processor");
      field.setAccessible(true);
      return field;
   }

   @BeforeAll
   static void installVpmProcessor() throws Exception {
      Field field = vpmProcessorField();
      oldVpmProcessor = field.get(null);
      field.set(null, vpm);
   }

   @AfterAll
   static void restoreVpmProcessor() throws Exception {
      vpmProcessorField().set(null, oldVpmProcessor);
   }

   private static final String APPLIED = "bug77629.vpmApplied";
   private static final Principal USER = new XPrincipal(new IdentityID("bug77629", "host-org"));
   private static final TestVpmProcessor vpm = new TestVpmProcessor();
   private static Object oldVpmProcessor;

   @Autowired
   private XRepository repository;
   private XDataModel model;
}
