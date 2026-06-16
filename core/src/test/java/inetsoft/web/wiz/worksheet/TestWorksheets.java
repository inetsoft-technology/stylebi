package inetsoft.web.wiz.worksheet;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;

/**
 * Test fixtures for building concrete worksheets with table assemblies.
 */
final class TestWorksheets {
   private TestWorksheets() {
   }

   /**
    * Build a concrete table assembly named {@code name} whose column selection
    * contains one {@link ColumnRef} per supplied column name. The caller is
    * responsible for adding it to the worksheet via {@code ws.addAssembly(...)}.
    */
   static TableAssembly tableWithColumns(Worksheet ws, String name, String... cols) {
      BoundTableAssembly table = new BoundTableAssembly(ws, name);
      ColumnSelection columns = new ColumnSelection();

      for(String col : cols) {
         columns.addAttribute(new ColumnRef(new AttributeRef(null, col)));
      }

      table.setColumnSelection(columns, false);
      return table;
   }
}
