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
package inetsoft.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77109: {@link Catalog#getString(String, Object...)} runs {@link MessageFormat} on a
 * catalog value when parameters are passed. In a MessageFormat pattern a single {@code '}
 * starts a quoted literal, so a literal apostrophe must be written as {@code ''}. A single
 * {@code '} is dropped and can leave later {@code {n}} placeholders unfilled.
 * <p>
 * This test formats every srinter value that contains a {@code {n}} placeholder with dummy
 * arguments and checks that every placeholder is filled and no apostrophe is lost.
 * <p>
 * Bug #77187: the opposite case. A value that is displayed raw (no MessageFormat) must use a
 * single {@code '}, because {@code ''} is shown doubled. See
 * {@link #rawMessagesUseSingleApostrophes(String)}.
 */
@Tag("core")
class SrinterMessageFormatQuotesTest {
   /**
    * Keys that are only used by the Angular client. The web reads them through
    * {@code LocalizationService} with no params (raw value) and fills {@code {n}} by plain
    * string replacement ({@code Tool.formatCatalogString}, {@code ExpandStringDirective},
    * message-dialog {@code "_*"} expand values). MessageFormat never runs on them, so a single
    * {@code '} is correct there and {@code ''} would show doubled. One combined list is applied
    * to every bundle. A new web-only key with a {@code {n}} placeholder and a single
    * {@code '} must be added here; a server-formatted key must use {@code ''} instead.
    */
   private static final Set<String> WEB_ONLY_KEYS = Set.of(
      "common.PrimaryWarning",
      "common.cube.defaulecubesname",
      "common.permit.view",
      "common.tree.editCalcField",
      "composer.ws.joinTableLiveDataZeroRowsUserHint",
      "data.vpm.conditionDuplicateNameSaveError",
      "date.comparison.sameX.dayByYear.label",
      "date.comparison.sameX.monthByYear.label",
      "date.comparison.sameX.quarterByYear.label",
      "date.comparison.sameX.weekByYear.label",
      "em.scheduleBatchAction.taskNotExists",
      "em.scheduleRepletAction.bookmarkNotExists",
      "em.scheduleRepletAction.dashboardNotExists",
      "formula.editor.function.invalid",
      // uses the LocalizationService escape syntax |\{1\}|, which is not a MessageFormat pattern
      "hide.mark.column.tooltip",
      "highlight.names.already.used",
      "parse.argument.number",
      "viewer.viewsheet.bookmark.deleteSelected",
      "viewer.viewsheet.chart.tooltip.invalid",
      "viewer.viewsheet.textInput.validError2",
      "viewer.wrongDateFmt.note4"
   );

   /**
    * Keys that are read raw by the web client and are also formatted by the server with
    * {@link Catalog#getString(String, Object...)} and arguments. No ASCII-apostrophe spelling
    * is correct on both paths ({@code '} is dropped by MessageFormat, {@code ''} is shown
    * doubled on the web), so these values use typographic quotes (&rsquo; and
    * &lsquo;{0}&rsquo;) and must not contain an ASCII {@code '} at all.
    */
   private static final Set<String> DUAL_USE_KEYS = Set.of(
      "em.common.graph.incompatibleTypes",
      "em.repository.missingResource",
      "getting.started.new.asset.unauthorized"
   );

   private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d");
   private static final Pattern ARG = Pattern.compile("\\{(\\d+)(?:\\s*,\\s*(\\w+))?");
   private static final Pattern QUOTE_RUN = Pattern.compile("'{2,}");

   @ParameterizedTest
   @MethodSource("bundlePaths")
   void placeholderMessagesKeepApostrophesAndFillArgs(String relativePath) throws Exception {
      Path file = resolve(relativePath);
      PropertyResourceBundle bundle = load(file);
      List<String> failures = new ArrayList<>();
      int checked = 0;

      for(String key : new TreeSet<>(bundle.keySet())) {
         String value = bundle.getString(key);

         if(!PLACEHOLDER.matcher(value).find() || WEB_ONLY_KEYS.contains(key)) {
            continue;
         }

         checked++;
         String result;

         try {
            result = MessageFormat.format(value, dummyArgs(value));
         }
         catch(IllegalArgumentException ex) {
            failures.add(key + ": MessageFormat failed (" + ex.getMessage() + ")");
            continue;
         }

         if(PLACEHOLDER.matcher(result).find()) {
            failures.add(key + ": placeholder not filled -> " + result);
         }
         else if(countQuotes(result) != countQuotes(value.replace("''", "'"))) {
            failures.add(key + ": apostrophe lost -> " + result);
         }
      }

      assertTrue(checked > 0, "No placeholder messages found in " + file);
      assertTrue(failures.isEmpty(), file.getFileName() + ": " + failures.size() +
         " MessageFormat-formatted message(s) use a single ' (write '' instead, or add the key " +
         "to WEB_ONLY_KEYS if it is only formatted by the web client):\n  " +
         String.join("\n  ", failures));
   }

   /**
    * Bug #77187: values that are displayed raw must not escape apostrophes.
    * <ol>
    *    <li>A value without a {@code {n}} placeholder must not contain {@code ''}. The only
    *    exception is a {@code '''} run, which is a quoted apostrophe character (for example
    *    the last item of {@code viewer.worksheet.Grouping.SpecialChar}).</li>
    *    <li>A {@link #WEB_ONLY_KEYS} value must not contain {@code ''}.</li>
    *    <li>A {@link #DUAL_USE_KEYS} value must not contain an ASCII {@code '}.</li>
    * </ol>
    * Rule 1 relies on a <b>bundle convention</b>, not on a {@link Catalog} guarantee: a value
    * without {@code {n}} is only read with no arguments (the web {@code LocalizationService}
    * path, or a server {@code Catalog.getString(key)} call), and {@code Catalog.getString}
    * skips MessageFormat when no arguments are passed. {@code Catalog.getString(key, args)} on
    * such a value would still run MessageFormat, and {@code Catalog.getIDString} always does.
    * So a server call that passes arguments must use a key whose value has a {@code {n}}
    * placeholder, or the value must not contain {@code ''}.
    */
   @ParameterizedTest
   @MethodSource("bundlePaths")
   void rawMessagesUseSingleApostrophes(String relativePath) throws Exception {
      Path file = resolve(relativePath);
      PropertyResourceBundle bundle = load(file);
      List<String> failures = new ArrayList<>();

      for(String key : new TreeSet<>(bundle.keySet())) {
         String value = bundle.getString(key);

         if(DUAL_USE_KEYS.contains(key)) {
            if(value.indexOf('\'') >= 0) {
               failures.add(key + ": dual-use key contains an ASCII ' (use typographic " +
                  "quotes) -> " + value);
            }
         }
         else if(WEB_ONLY_KEYS.contains(key)) {
            if(value.contains("''")) {
               failures.add(key + ": web-only key contains '' -> " + value);
            }
         }
         else if(!PLACEHOLDER.matcher(value).find() && hasDoubledQuote(value)) {
            failures.add(key + ": raw value without {n} contains '' -> " + value);
         }
      }

      assertTrue(failures.isEmpty(), file.getFileName() + ": " + failures.size() +
         " raw-displayed message(s) contain '' which is shown doubled (write ' instead):\n  " +
         String.join("\n  ", failures));
   }

   static Stream<String> bundlePaths() {
      return Stream.of(
         "src/main/resources/inetsoft/util/srinter.properties",
         "../community-examples/localize/srinter_en_US.properties",
         "../community-examples/localize/srinter_fr_FR.properties",
         "../community-examples/localize/srinter_ja_JP.properties",
         "../community-examples/localize/srinter_zh_CN.properties"
      );
   }

   private static PropertyResourceBundle load(Path file) throws Exception {
      assertTrue(Files.isRegularFile(file), "Catalog bundle not found: " + file);

      try(InputStream in = Files.newInputStream(file)) {
         return new PropertyResourceBundle(in);
      }
   }

   /**
    * Returns true if the value has a run of apostrophes other than exactly {@code '''}.
    */
   private static boolean hasDoubledQuote(String value) {
      Matcher matcher = QUOTE_RUN.matcher(value);

      while(matcher.find()) {
         if(matcher.end() - matcher.start() != 3) {
            return true;
         }
      }

      return false;
   }

   private static Path resolve(String relativePath) {
      String basedir = System.getProperty("basedir");
      assertNotNull(basedir, "basedir system property is not set (it is set by Maven surefire)");
      return Path.of(basedir).resolve(relativePath).normalize();
   }

   private static Object[] dummyArgs(String value) {
      Object[] args = new Object[10];

      for(int i = 0; i < args.length; i++) {
         args[i] = "<" + i + ">";
      }

      Matcher matcher = ARG.matcher(value);

      while(matcher.find()) {
         int index = Integer.parseInt(matcher.group(1));
         String type = matcher.group(2);

         if(index < args.length && type != null) {
            args[index] = "date".equals(type) || "time".equals(type) ? new Date(0) : 1;
         }
      }

      return args;
   }

   private static int countQuotes(String str) {
      int count = 0;

      for(int i = 0; i < str.length(); i++) {
         if(str.charAt(i) == '\'') {
            count++;
         }
      }

      return count;
   }
}
