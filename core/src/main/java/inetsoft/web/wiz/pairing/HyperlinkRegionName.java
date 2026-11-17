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
package inetsoft.web.wiz.pairing;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parsed {@code name} of a {@code vsHyperlinkExpression} context
 * ({@link EditorContext#VS_HYPERLINK_EXPRESSION}): which hyperlink of an assembly the Hyperlink
 * dialog is open on, in the vocabulary of the plugin's {@code set_hyperlink} / {@code
 * get_hyperlink} tools, so the agent can read back what it pushed.
 *
 * <p>Grammar: {@code link | title | emptyPlot | cell[ROW,COL] | field[COLUMN] | axis[COLUMN] |
 * text[COLUMN]}. {@code ROW}/{@code COL} are 0-based indexes without leading zeros. {@code COLUMN}
 * is everything between the FIRST {@code '['} and the LAST {@code ']'}: a field may be called
 * "Total Sales" or even contain a {@code ']'}. The form word is case-sensitive (one spelling per
 * target: the grant and every target are compared by exact string).
 *
 * <p>This class decides only whether a name is well formed and which assembly types it applies to.
 * Pure and static, so it needs no Spring bean (the deployed instance runs AOT).
 *
 * @param form   {@code link}, {@code title}, {@code emptyPlot}, {@code cell}, {@code field},
 *               {@code axis} or {@code text}
 * @param column the column of {@code field}/{@code axis}/{@code text}, else {@code null}
 * @param row    the row of {@code cell}, else {@code -1}
 * @param col    the column index of {@code cell}, else {@code -1}
 */
public record HyperlinkRegionName(String form, String column, int row, int col) {
   public static final String LINK = "link";
   public static final String TITLE = "title";
   public static final String EMPTY_PLOT = "emptyPlot";
   public static final String CELL = "cell";
   public static final String FIELD = "field";
   public static final String AXIS = "axis";
   public static final String TEXT = "text";

   public static final int MAX_COLUMN_LENGTH = 200;
   public static final int MAX_LENGTH = 260;

   private static final String FORMS =
      "link, title, emptyPlot, cell[row,col], field[column], axis[column], text[column]";

   private static final Pattern CELL_NAME =
      Pattern.compile("^cell\\[(0|[1-9][0-9]{0,5}),(0|[1-9][0-9]{0,5})\\]$");

   /** The name as it travels on the wire. */
   public String wire() {
      return switch(form) {
         case CELL -> CELL + "[" + row + "," + col + "]";
         case FIELD, AXIS, TEXT -> form + "[" + column + "]";
         default -> form;
      };
   }

   /** The parsed name, or {@code null} when {@link #problem} says it is malformed. */
   public static HyperlinkRegionName parse(String name) {
      if(problem(name) != null) {
         return null;
      }

      switch(name) {
      case LINK:
      case TITLE:
      case EMPTY_PLOT:
         return new HyperlinkRegionName(name, null, -1, -1);
      default:
         break;
      }

      if(name.startsWith(CELL + "[")) {
         Matcher m = CELL_NAME.matcher(name);

         // problem() already ran the same match
         m.matches();

         return new HyperlinkRegionName(CELL, null, Integer.parseInt(m.group(1)),
                                        Integer.parseInt(m.group(2)));
      }

      int open = name.indexOf('[');

      return new HyperlinkRegionName(name.substring(0, open),
                                     name.substring(open + 1, name.length() - 1), -1, -1);
   }

   /** Why {@code name} is not a well-formed hyperlink region name, or {@code null} when it is. */
   public static String problem(String name) {
      if(name == null || name.isBlank()) {
         return "name is required: one of " + FORMS;
      }

      if(name.length() > MAX_LENGTH) {
         return "name is longer than " + MAX_LENGTH + " characters";
      }

      if(LINK.equals(name) || TITLE.equals(name) || EMPTY_PLOT.equals(name)) {
         return null;
      }

      int open = name.indexOf('[');

      if(open < 0 || name.charAt(name.length() - 1) != ']') {
         return "'" + name + "' is not a hyperlink region; expected one of " + FORMS;
      }

      String form = name.substring(0, open);

      if(CELL.equals(form)) {
         return CELL_NAME.matcher(name).matches() ? null :
            "'" + name + "' is not a cell region; expected cell[row,col] with 0-based row and " +
            "col numbers without leading zeros";
      }

      if(!FIELD.equals(form) && !AXIS.equals(form) && !TEXT.equals(form)) {
         return "unknown hyperlink region '" + form + "' in '" + name + "'; expected one of " +
            FORMS;
      }

      String column = name.substring(open + 1, name.length() - 1);

      if(column.isBlank()) {
         return "'" + name + "' needs a column name between the brackets";
      }

      if(column.length() > MAX_COLUMN_LENGTH) {
         return "the column name in '" + form + "[...]' is longer than " + MAX_COLUMN_LENGTH +
            " characters";
      }

      for(int i = 0; i < column.length(); i++) {
         if(Character.isISOControl(column.charAt(i))) {
            return "the column name must not contain control characters";
         }
      }

      return null;
   }

   /**
    * The {@link inetsoft.web.wiz.viewsheet.PropertyAliases#typeKeyOf} keys of the assembly types
    * the region {@code form} exists on (the {@code get_hyperlink} description: a chart has field,
    * axis, text, title and empty plot links; a table, crosstab and freehand table one link per
    * cell; a text, image and gauge exactly one link). Empty for an unknown form.
    */
   public static Set<String> typesOf(String form) {
      if(form == null) {
         return Set.of();
      }

      return switch(form) {
         case LINK -> Set.of("text", "image", "gauge");
         case TITLE, EMPTY_PLOT, FIELD, AXIS, TEXT -> Set.of("chart");
         case CELL -> Set.of("table", "crosstab", "calctable");
         default -> Set.of();
      };
   }

   /** The assembly types a wire {@code name} applies to, or an empty set when it is malformed. */
   public static Set<String> typesOfName(String name) {
      HyperlinkRegionName parsed = parse(name);

      return parsed == null ? Set.of() : typesOf(parsed.form());
   }
}
