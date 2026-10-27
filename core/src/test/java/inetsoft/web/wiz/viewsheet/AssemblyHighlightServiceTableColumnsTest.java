package inetsoft.web.wiz.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.internal.table.TableHighlightAttr;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79397: a name-only delete on a plain table reads only the first data cell, so a cell-level
 * highlight on another column was reported as missing ("It has: BigOrder.") with nothing saying
 * it exists elsewhere. The refusal now names the other column and says to pass colName.
 */
@Tag("core")
class AssemblyHighlightServiceTableColumnsTest {
   private static HighlightGroup group(String... names) {
      HighlightGroup g = new HighlightGroup();

      for(String n : names) {
         TextHighlight t = new TextHighlight();
         t.setName(n);
         g.addHighlight(n, t);
      }

      return g;
   }

   private static TableDataPath column(String name) {
      return new TableDataPath(-1, TableDataPath.DETAIL, "string", new String[]{ name });
   }

   private static TableHighlightAttr attr() {
      TableHighlightAttr attr = new TableHighlightAttr();
      attr.setHighlight(column("Product:Total"), group("HugeCell"));
      attr.setHighlight(column("Product:Id"), group("OwnCell"));
      // The shared row-level group is reported by the read itself, never as a column.
      attr.setHighlight(new TableDataPath(-1, TableDataPath.DETAIL), group("BigOrder"));
      return attr;
   }

   @Test
   void columnGroupsListsEachColumnAndSkipsTheRowGroupAndAlreadyReportedNames() {
      Map<String, List<String>> groups =
         AssemblyHighlightService.columnGroups(attr(), List.of("BigOrder", "ownCELL"));

      assertEquals(List.of("Product:Total"), List.copyOf(groups.keySet()));
      assertEquals(List.of("HugeCell"), groups.get("Product:Total"));
   }

   @Test
   void columnGroupsIsEmptyWithoutAnyAttrOrAnyColumnHighlight() {
      assertTrue(AssemblyHighlightService.columnGroups(null, List.of()).isEmpty());

      TableHighlightAttr rowOnly = new TableHighlightAttr();
      rowOnly.setHighlight(new TableDataPath(-1, TableDataPath.DETAIL), group("BigOrder"));
      assertTrue(AssemblyHighlightService.columnGroups(rowOnly, List.of()).isEmpty());
   }

   @Test
   void theHintKeepsThePrefixNamesTheColumnAndSaysToPassColName() {
      String message = AssemblyHighlightService.tableHint(
         List.of("BigOrder"), AssemblyHighlightService.columnGroups(attr(), List.of("BigOrder")));

      assertTrue(message.startsWith("It has: BigOrder."), message);
      assertTrue(message.contains("Product:Total (HugeCell"), message);
      assertTrue(message.contains("colName"), message);
   }

   @Test
   void theHintNeverSaysNoneWhenAnotherColumnHoldsHighlights() {
      String message = AssemblyHighlightService.tableHint(
         List.of(), AssemblyHighlightService.columnGroups(attr(), List.of()));

      assertFalse(message.contains("(none)"), message);
      assertTrue(message.contains("Product:Total (HugeCell)"), message);
      assertTrue(message.contains("colName"), message);
   }
}
