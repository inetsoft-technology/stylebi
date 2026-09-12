package inetsoft.report.script.formula;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.DefaultTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.ScriptUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import inetsoft.report.TableLens;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableAssemblyScriptableTest {
   private TableAssemblyScriptable tableAssemblyScriptable;
   private AssetQuerySandbox mockSandbox;
   private Worksheet mockWorksheet;
   private EmbeddedTableAssembly mockEmbeddedTableAssembly;
   private TestLogAppender logAppender;

   @BeforeEach
   void setUp() {
      mockSandbox = mock(AssetQuerySandbox.class);
      mockWorksheet = mock(Worksheet.class);
      mockEmbeddedTableAssembly = mock(EmbeddedTableAssembly.class);

      when(mockSandbox.getWorksheet()).thenReturn(mockWorksheet);
      tableAssemblyScriptable = new TableAssemblyScriptable("testTable", mockSandbox, AssetQuerySandbox.LIVE_MODE);

      // Attach custom log appender
      Logger logger = (Logger) LoggerFactory.getLogger(TableAssemblyScriptable.class);
      logAppender = new TestLogAppender();
      logger.addAppender(logAppender);
      logAppender.start();
   }

   /**
    * test put with  null assembly
    */
   @Test
   void testPutWithNullAssembly() {
      when(mockWorksheet.getAssembly("testTable")).thenReturn(null);

      XEmbeddedTable mockData = mock(XEmbeddedTable.class);
      Object value = ScriptUtil.unwrap(mockData);
      tableAssemblyScriptable.putMember("table", value);
      assertNull(tableAssemblyScriptable.getMember("table"));
      assertTrue(logAppender.contains("Table 'testTable' does not exist", "ERROR"));
   }

   /**
    * test put with not EmbeddedTableAssembly
    */
   @Test
   void testPutWithInvalidAssemblyType() {
      MirrorTableAssembly mockMirrorTableAssembly = mock(MirrorTableAssembly.class);
      when(mockWorksheet.getAssembly("testTable")).thenReturn(mockMirrorTableAssembly);
      when(mockMirrorTableAssembly.getTableAssembly()).thenReturn(mock(TableAssembly.class));

      tableAssemblyScriptable.putMember("table", new Object());

      assertNull(tableAssemblyScriptable.getMember("table"));
      assertTrue(logAppender.contains("Table data can only be set on embedded tables", "ERROR"));
   }

   /**
    * test put with EmbeddedTableAssembly and data value is null
    */
   @Test
   void testPutWithXEmbeddedTableAndDataValueIsInvalid() {
      EmbeddedTableAssembly mockEmbeddedTableAssembly = mock(EmbeddedTableAssembly.class);
      when(mockWorksheet.getAssembly("testTable")).thenReturn(mockEmbeddedTableAssembly);

      //check data value is null
      tableAssemblyScriptable.putMember("table", null);
      assertTrue(logAppender.contains("Cannot set data of table 'testTable' to null", "ERROR"));

      //check data value is string
      when(mockWorksheet.getAssembly("testTable")).thenReturn(mockEmbeddedTableAssembly);
      tableAssemblyScriptable.putMember("table", "aaa");
      assertTrue(logAppender.contains("Invalid type for data of table", "ERROR"));
   }

   /**
    * test put with EmbeddedTableAssembly and data value is XTable and object[][]
    */
   @Test
   void testPutWithXEmbeddedTableAndDataValueXTable() {
      when(mockWorksheet.getAssembly("testTable")).thenReturn(mockEmbeddedTableAssembly);

      //test put a xtable
      DefaultTable defaultTable = new DefaultTable(objData);
      tableAssemblyScriptable.putMember("table", defaultTable);
      assertNull(tableAssemblyScriptable.getMember("table"));

      //test put object
      tableAssemblyScriptable.putMember("table", objData);
      assertNull(tableAssemblyScriptable.getElementTable());
   }

   /**
    * #75663: a worksheet table referenced by name from a script reads its column
    * values as a flat table, so its bare-column access must bypass the grouped/
    * crosstab summary-cell routing (a shared source table can carry a spurious
    * runtime crosstab descriptor pushed down by a crosstab-optimized freehand).
    */
   @Test
   void isBaseTableReferenceTrueForWorksheetTable() {
      assertTrue(tableAssemblyScriptable.isBaseTableReference());
   }

   /**
    * #75663: a cube/OLAP table is genuinely pivot-shaped, so its by-name reference
    * must retain the crosstab summary-cell routing (must NOT read flat).
    */
   @Test
   void isBaseTableReferenceFalseForCubeTable() {
      CubeTableAssemblyScriptable cube = new CubeTableAssemblyScriptable(
         "testTable", mockSandbox, AssetQuerySandbox.LIVE_MODE, mock(TableLens.class));
      assertFalse(cube.isBaseTableReference());
   }

   // Custom log appender for testing
   static class TestLogAppender extends AppenderBase<ILoggingEvent> {
      private final List<ILoggingEvent> events = new ArrayList<>();

      @Override
      protected void append(ILoggingEvent eventObject) {
         events.add(eventObject);
      }

      public boolean contains(String message, String level) {
         return events.stream()
            .anyMatch(event -> event.getFormattedMessage().contains(message) &&
               event.getLevel().toString().equals(level));
      }
   }

   static Object[][] objData = new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   };
}
