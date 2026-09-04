/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.gdata;

import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers A10 -- the {@code spreadsheetId} scalar alias on {@link GDataQuery}: it round-trips a raw
 * value, is absent from {@code @View}, carries no {@code tagsMethod}, and does not delegate to the
 * side-effecting {@link GDataQuery#getSpreadsheet()}.
 *
 * <p><b>Construction risk:</b> {@code new GDataQuery()} runs {@code TabularQuery(String)} ->
 * {@code XQuery(String)}, which calls {@code OrganizationManager.getInstance().getCurrentOrgID()}
 * -- a live singleton. Tried first because a test against the real constructor is more honest; it
 * worked standalone in this environment, so no fallback to
 * {@code mock(GDataQuery.class, CALLS_REAL_METHODS)} was needed.
 */
class GDataQueryAliasTest {
   @Test
   void roundTripsARawValue() {
      GDataQuery query = new GDataQuery();
      query.setSpreadsheetId("1AbCdEf-driveFileId");

      // Stored verbatim: no trim, no case change, no validation.
      assertEquals("1AbCdEf-driveFileId", query.getSpreadsheetId());
   }

   @Test
   void roundTripsAValueThatWouldBeMangledByNormalisation() {
      // get(set(x)) == x for a raw x, per A10 -- exercised with a value trimming/case-folding
      // would silently alter, so a normalising setter would fail this and a non-normalising one
      // passes trivially.
      GDataQuery query = new GDataQuery();
      String raw = "  MixedCase-Id_123  ";
      query.setSpreadsheetId(raw);

      assertEquals(raw, query.getSpreadsheetId());
   }

   @Test
   void getterReturnsNullWhenNothingWasEverSet() {
      GDataQuery query = new GDataQuery();
      assertNull(query.getSpreadsheetId());
   }

   @Test
   void setterDoesNotThrowOnADetachedBeanWithNoDataSource() {
      // Unlike getSpreadsheet(), which throws MessageException when getDataSource() is null, the
      // alias must be usable on a bean that has no data source attached yet -- e.g. while wiz is
      // filling a query before it has been persisted.
      GDataQuery query = new GDataQuery();
      assertNull(query.getDataSource());
      assertDoesNotThrow(() -> query.setSpreadsheetId("someId"));
      assertDoesNotThrow(query::getSpreadsheetId);
   }

   @Test
   void aliasIsAbsentFromView() {
      // A10: the alias must be outside @View -- the dialog already edits the same state through
      // the picker, and a second control over one field would let the two disagree on screen.
      View view = GDataQuery.class.getAnnotation(View.class);
      assertNotNull(view, "GDataQuery must declare @View");

      boolean referencesAlias = Arrays.stream(view.value())
         .anyMatch(v1 -> "spreadsheetId".equals(v1.value()));
      assertFalse(referencesAlias, "spreadsheetId must not be referenced by @View");
   }

   @Test
   void aliasCarriesNoTagsMethod() throws NoSuchMethodException {
      // A10: no tagsMethod -- a tagsMethod is invoked on a background thread with a 30s join (and,
      // for a dependsOn chain, gated on a CountDownLatch); an alias whose whole purpose is to be
      // settable without the dialog must not acquire the dialog's machinery.
      Method getter = GDataQuery.class.getMethod("getSpreadsheetId");
      PropertyEditor editor = getter.getAnnotation(PropertyEditor.class);

      // No @PropertyEditor at all is the strongest form of "no tagsMethod"; if one were ever added
      // for some other reason, its tagsMethod must still be empty.
      assertTrue(editor == null || editor.tagsMethod().isEmpty());
   }

   @Test
   void aliasDoesNotDelegateToTheSideEffectingGetSpreadsheet() {
      // getSpreadsheet() refreshes the OAuth token and throws MessageException on a null data
      // source (GDataQuery.java:49-58). If the alias's getter/setter touched it, this test's
      // "does not throw on a detached bean" case above would fail instead of passing -- restated
      // here as its own test so a future refactor that routes the alias through getSpreadsheet()
      // fails for an obviously-named reason.
      GDataQuery query = new GDataQuery();
      query.setSpreadsheetId("someId");

      assertDoesNotThrow(() -> {
         assertEquals("someId", query.getSpreadsheetId());
      });

      // getSpreadsheet(), by contrast, DOES throw on this same detached bean -- confirms the two
      // properties are genuinely independent paths, not that getSpreadsheet() itself was broken by
      // this change.
      assertThrows(Exception.class, query::getSpreadsheet);
   }

   @Test
   void setterInstallsAGoogleFileSoCloneCannotNpe() throws Exception {
      // One pre-existing hazard the alias avoids: GooglePicker.clone() does a raw
      // (GoogleFile) selectedFile.clone() with no null guard. The setter above always installs a
      // GoogleFile, so a query filled purely through the alias never produces a null selectedFile.
      // Read the `spreadsheet` FIELD directly, not through getSpreadsheet() -- that getter throws
      // MessageException on this detached (no data source) bean, which is exactly the behaviour
      // the alias exists to route around.
      GDataQuery query = new GDataQuery();
      query.setSpreadsheetId("someId");

      java.lang.reflect.Field field = GDataQuery.class.getDeclaredField("spreadsheet");
      field.setAccessible(true);
      inetsoft.uql.tabular.GooglePicker picker = (inetsoft.uql.tabular.GooglePicker) field.get(query);

      assertNotNull(picker);
      assertNotNull(picker.getSelectedFile());
      assertEquals("someId", picker.getSelectedFile().getId());
   }
}
