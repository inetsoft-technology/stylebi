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
package inetsoft.uql;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XValueNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * VariableTable - single-pass
 *
 * Risk-first coverage:
 *   [Risk 3] - clone()/getSubset()/printKey()/addAll(); all-null-array put() conversion
 *   [Risk 2] - put/get/contains/remove/clear/size/keys/base-table delegation, equals()/
 *              hashCode() key-set contract, format/session/builtin-variable/context-variable
 *              accessors, copyParameters(XPrincipal), get(UserVariable) type dispatch and
 *              runtimeValue resolution, JSON Serializer/Deserializer round trip
 */

/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] clone() does not copy notIgnoreNull (shared HashSet with original).
 *             Conclusion (do not fix): real incomplete clone, but setNotIgnoredNull has no
 *             meaningful production writers — not frontend-reproducible.
 *
 * [Suspect 2] getSubset prefix branch skips setAsIs/setNotIgnoredNull (other branches copy).
 *             Conclusion (do not fix): real inconsistency, but getSubset has no external
 *             callers — not frontend-reproducible.
 *
 * [Suspect 3] printKey joins name/value with unescaped ':' / ',', so collisions are possible.
 *             Conclusion (do not fix): theoretical cache-key ambiguity; needs exotic names
 *             containing delimiters — not frontend-reproducible.
 *
 * [Suspect 4] addAll merges asIs only when destination.asIs is already non-null.
 *             Conclusion (do not fix): real merge gap, but common path is clone-then-addAll
 *             (clone copies asIs when present); no clear FE repro without crafted script state.
 */

/*
 * Cases deferred - require a different test tier:
 *
 * [VariableTable] getBuiltinVariable(name[,type]) - the value-producing branches for
 *             _TODAY / _USER_ / _ROLES_ / _GROUPS_ / _BEGINNING_OF_xxx / _END_OF_xxx are
 *             reachable only via get(String)/get(UserVariable); they call static
 *             ScheduleParameterScope.* methods and ThreadContext.getContextPrincipal().
 *             Needs Mockito.mockStatic on both classes - not covered in this [unit]-tier file.
 */
@Tag("core")
class VariableTableTest {

   private VariableTable table;

   @BeforeEach
   void setUp() {
      table = new VariableTable();
   }

   // ==========================================================================
   // put / get
   // ==========================================================================

   @Test
   void put_and_get_stringValue() throws Exception {
      table.put("name", "Alice");
      assertEquals("Alice", table.get("name"));
   }

   @Test
   void put_and_get_integerValue() throws Exception {
      table.put("count", 42);
      assertEquals(42, table.get("count"));
   }

   @Test
   void put_overwritesPreviousValue() throws Exception {
      table.put("x", "first");
      table.put("x", "second");
      assertEquals("second", table.get("x"));
   }

   @Test
   void get_undefinedVariableReturnsNull() throws Exception {
      assertNull(table.get("nonexistent"));
   }

   @Test
   void put_nullValueIsStored() throws Exception {
      table.put("nullVar", (Object) null);
      assertTrue(table.contains("nullVar"), "Key should be present even with null value");
   }

   /**
    * An Object[] containing only nulls is treated as null and stored as null.
    */
   @Test
   void put_allNullArrayIsStoredAsNull() throws Exception {
      Object[] arr = {null, null, null};
      table.put("arr", arr);
      // The implementation converts all-null arrays to null
      assertNull(table.get("arr"), "All-null array should be stored as null");
   }

   /**
    * An Object[] with at least one non-null element is stored as-is.
    */
   @Test
   void put_partialNullArrayIsStoredAsIs() throws Exception {
      Object[] arr = {null, "value", null};
      table.put("arr", arr);
      Object result = table.get("arr");
      assertNotNull(result, "Array with non-null element should be stored as-is");
      assertInstanceOf(Object[].class, result);
   }

   // ==========================================================================
   // contains
   // ==========================================================================

   @Test
   void contains_returnsTrueForExistingKey() {
      table.put("k", "v");
      assertTrue(table.contains("k"));
   }

   @Test
   void contains_returnsFalseForMissingKey() {
      assertFalse(table.contains("missing"));
   }

   @Test
   void contains_returnsTrueForNullValueKey() {
      table.put("nullKey", (Object) null);
      assertTrue(table.contains("nullKey"));
   }

   // ==========================================================================
   // remove
   // ==========================================================================

   @Test
   void remove_removesKey() throws Exception {
      table.put("r", "value");
      table.remove("r");
      assertFalse(table.contains("r"));
      assertNull(table.get("r"));
   }

   @Test
   void remove_nonexistentKey_doesNotThrow() {
      assertDoesNotThrow(() -> table.remove("does_not_exist"));
   }

   // ==========================================================================
   // clear
   // ==========================================================================

   @Test
   void clear_removesAllEntries() throws Exception {
      table.put("a", 1);
      table.put("b", 2);
      table.clear();
      assertFalse(table.contains("a"));
      assertFalse(table.contains("b"));
   }

   @Test
   void clear_doesNotAffectBaseTable() throws Exception {
      VariableTable base = new VariableTable();
      base.put("baseVar", "from base");
      table.setBaseTable(base);
      table.put("local", "local value");

      table.clear();

      // base table is untouched
      assertTrue(table.contains("baseVar"), "Base table should still be reachable after clear");
   }

   // ==========================================================================
   // size
   // ==========================================================================

   @Test
   void size_emptyTableIsZero() {
      assertEquals(0, table.size());
   }

   @Test
   void size_countsLocalEntries() {
      table.put("a", 1);
      table.put("b", 2);
      assertEquals(2, table.size());
   }

   @Test
   void size_includesBaseTableEntries() {
      VariableTable base = new VariableTable();
      base.put("x", 10);
      table.setBaseTable(base);
      table.put("y", 20);
      assertEquals(2, table.size());
   }

   // ==========================================================================
   // keys enumeration
   // ==========================================================================

   @Test
   void keys_enumeratesLocalKeys() {
      table.put("alpha", 1);
      table.put("beta", 2);

      Set<String> found = new HashSet<>();
      Enumeration<String> e = table.keys();
      while(e.hasMoreElements()) {
         found.add(e.nextElement());
      }

      assertTrue(found.contains("alpha"));
      assertTrue(found.contains("beta"));
   }

   @Test
   void keys_includesBaseTableKeys() {
      VariableTable base = new VariableTable();
      base.put("fromBase", "yes");
      table.setBaseTable(base);
      table.put("fromLocal", "yes");

      Set<String> found = new HashSet<>();
      Enumeration<String> e = table.keys();
      while(e.hasMoreElements()) {
         found.add(e.nextElement());
      }

      assertTrue(found.contains("fromBase"));
      assertTrue(found.contains("fromLocal"));
   }

   // ==========================================================================
   // Base-table delegation
   // ==========================================================================

   @Test
   void get_delegatesToBaseTable() throws Exception {
      VariableTable base = new VariableTable();
      base.put("inherited", "value");
      table.setBaseTable(base);

      assertEquals("value", table.get("inherited"),
                   "Should retrieve value from base table");
   }

   @Test
   void get_localOverridesBase() throws Exception {
      VariableTable base = new VariableTable();
      base.put("key", "base-value");
      table.setBaseTable(base);
      table.put("key", "local-value");

      assertEquals("local-value", table.get("key"),
                   "Local value should shadow base table value");
   }

   @Test
   void setBaseTable_preventsSelfCycle() {
      table.setBaseTable(table);  // should be silently ignored
      assertNull(table.getBaseTable(), "Setting self as base should be ignored");
   }

   @Test
   void setBaseTable_preventsIndirectCycle() {
      VariableTable base = new VariableTable();
      base.setBaseTable(table);
      table.setBaseTable(base);   // would create cycle table->base->table
      assertNull(table.getBaseTable(), "Cycle base should be rejected");
   }

   @Test
   void addBaseTable_appendsToChain() throws Exception {
      VariableTable first  = new VariableTable();
      VariableTable second = new VariableTable();
      first.put("f", "first");
      second.put("s", "second");

      table.setBaseTable(first);
      table.addBaseTable(second);

      assertEquals("second", table.get("s"),
                   "Should reach second-level base");
   }

   @Test
   void removeBaseTable_byReference_removesIt() {
      VariableTable base = new VariableTable();
      base.put("x", 1);
      table.setBaseTable(base);
      table.removeBaseTable(base);
      assertNull(table.getBaseTable());
   }

   @Test
   void removeBaseTable_byType_removesIt() {
      VariableTable base = new VariableTable();
      table.setBaseTable(base);
      table.removeBaseTable(VariableTable.class);
      assertNull(table.getBaseTable());
   }

   @Test
   void removeBaseTable_byReference_recursesIntoDeeperChainLevel() {
      // covers the recursive branch: basetable != null && basetable != table
      VariableTable middle = new VariableTable();
      VariableTable deepest = new VariableTable();
      table.setBaseTable(middle);
      middle.setBaseTable(deepest);

      table.removeBaseTable(deepest);

      assertSame(middle, table.getBaseTable(), "Immediate base table should be unaffected");
      assertNull(middle.getBaseTable(), "Deeper base table should be removed via recursion");
   }

   @Test
   void removeBaseTable_byType_recursesWhenImmediateBaseIsDifferentType() {
      // covers the recursive branch of removeBaseTable(Class): the immediate base table
      // is a plain VariableTable (not assignable from SubVariableTable), so removal must
      // recurse into the next level of the chain.
      VariableTable middle = new VariableTable();
      SubVariableTable deepest = new SubVariableTable();
      table.setBaseTable(middle);
      middle.setBaseTable(deepest);

      table.removeBaseTable(SubVariableTable.class);

      assertSame(middle, table.getBaseTable());
      assertNull(middle.getBaseTable());
   }

   private static class SubVariableTable extends VariableTable {
   }

   // ==========================================================================
   // clone
   // ==========================================================================

   @Test
   void clone_producesIndependentCopy() throws Exception {
      table.put("x", "original");
      VariableTable copy = table.clone();

      copy.put("x", "modified");

      assertEquals("original", table.get("x"), "Clone modification should not affect original");
      assertEquals("modified", copy.get("x"));
   }

   @Test
   void clone_copiesAllEntries() throws Exception {
      table.put("a", 1);
      table.put("b", "hello");
      VariableTable copy = table.clone();

      assertEquals(1,       copy.get("a"));
      assertEquals("hello", copy.get("b"));
   }

   @Test
   void clone_baseTableIsAlsoCloned() throws Exception {
      VariableTable base = new VariableTable();
      base.put("baseVar", "bv");
      table.setBaseTable(base);

      VariableTable copy = table.clone();
      assertNotNull(copy.getBaseTable());
      assertEquals("bv", copy.get("baseVar"));
   }

   // ==========================================================================
   // equals / hashCode
   // ==========================================================================

   @Test
   void equals_twoEmptyTablesAreEqual() {
      assertEquals(new VariableTable(), new VariableTable());
   }

   @Test
   void equals_tablesWithSameEntriesAreEqual() {
      VariableTable t1 = new VariableTable();
      VariableTable t2 = new VariableTable();
      t1.put("k", "v");
      t2.put("k", "v");
      assertEquals(t1, t2);
   }

   @Test
   void equals_tablesWithDifferentEntriesAreNotEqual() {
      VariableTable t1 = new VariableTable();
      VariableTable t2 = new VariableTable();
      t1.put("k", "v1");
      t2.put("k", "v2");
      assertNotEquals(t1, t2);
   }

   @Test
   void equals_extraKeyOnOneSide_areNotEqual() {
      // covers the asymmetric-key-set-size path: both contains(a,b)/contains(b,a) checks
      // must pass for equality, not just a same-key value comparison.
      VariableTable t1 = new VariableTable();
      VariableTable t2 = new VariableTable();
      t1.put("k", "v");
      t2.put("k", "v");
      t2.put("k2", "v2");

      assertNotEquals(t1, t2);
   }

   @Test
   void equals_ignoresHttpRequestResponseKeys() {
      VariableTable t1 = new VariableTable();
      VariableTable t2 = new VariableTable();
      t1.put(VariableTable.HTTP_REQUEST, "req1");
      t2.put(VariableTable.HTTP_REQUEST, "req2");

      assertEquals(t1, t2, "Ignored keys like HTTP_REQUEST should not affect equality");
   }

   @Test
   void equals_sameInstanceIsEqual() {
      assertEquals(table, table);
   }

   @Test
   void equals_notEqualToNull() {
      assertNotEquals(null, table);
   }

   @Test
   void equals_notEqualToDifferentType() {
      assertNotEquals("string", table);
   }

   @Test
   void hashCode_equalTablesHaveSameHashCode() {
      VariableTable t1 = new VariableTable();
      VariableTable t2 = new VariableTable();
      t1.put("k", "v");
      t2.put("k", "v");
      assertEquals(t1.hashCode(), t2.hashCode());
   }

   // ==========================================================================
   // asIs
   // ==========================================================================

   @Test
   void setAsIs_and_isAsIs() {
      table.put("param", "value");
      assertFalse(table.isAsIs("param"), "Default should be false");
      table.setAsIs("param", true);
      assertTrue(table.isAsIs("param"));
      table.setAsIs("param", false);
      assertFalse(table.isAsIs("param"));
   }

   @Test
   void isAsIs_unknownVariable_returnsFalse() {
      assertFalse(table.isAsIs("unknownVar"));
   }

   // ==========================================================================
   // notIgnoreNull
   // ==========================================================================

   @Test
   void setNotIgnoredNull_and_isNotIgnoredNull() {
      assertFalse(table.isNotIgnoredNull("v"));
      table.setNotIgnoredNull("v");
      assertTrue(table.isNotIgnoredNull("v"));
   }

   @Test
   void clearNullIgnored_removesAll() {
      table.setNotIgnoredNull("a");
      table.setNotIgnoredNull("b");
      table.clearNullIgnored();
      assertFalse(table.isNotIgnoredNull("a"));
      assertFalse(table.isNotIgnoredNull("b"));
   }

   // ==========================================================================
   // putFormat / getFormat / removeFormat
   // ==========================================================================

   @Test
   void putFormat_and_getFormat() {
      table.putFormat("dateCol", "yyyy-MM-dd");
      assertEquals("yyyy-MM-dd", table.getFormat("dateCol"));
   }

   @Test
   void getFormat_missingKey_returnsNull() {
      assertNull(table.getFormat("missing"));
   }

   @Test
   void removeFormat_removesEntry() {
      table.putFormat("col", "MM/dd/yyyy");
      table.removeFormat("col");
      assertNull(table.getFormat("col"));
   }

   // ==========================================================================
   // isBuiltinVariable
   // ==========================================================================

   @Test
   void isBuiltinVariable_recognisesBuiltins() {
      assertTrue(VariableTable.isBuiltinVariable("_TODAY"));
      assertTrue(VariableTable.isBuiltinVariable("_USER_"));
      assertTrue(VariableTable.isBuiltinVariable("_ROLES_"));
      assertTrue(VariableTable.isBuiltinVariable("_GROUPS_"));
      assertTrue(VariableTable.isBuiltinVariable("_BEGINNING_OF_YEAR"));
      assertTrue(VariableTable.isBuiltinVariable("_END_OF_YEAR"));
      assertTrue(VariableTable.isBuiltinVariable("_BEGINNING_OF_MONTH"));
      assertTrue(VariableTable.isBuiltinVariable("_END_OF_MONTH"));
      assertTrue(VariableTable.isBuiltinVariable("_BEGINNING_OF_QUARTER"));
      assertTrue(VariableTable.isBuiltinVariable("_END_OF_QUARTER"));
      assertTrue(VariableTable.isBuiltinVariable("_BEGINNING_OF_WEEK"));
      assertTrue(VariableTable.isBuiltinVariable("_END_OF_WEEK"));
   }

   @Test
   void isBuiltinVariable_returnsFalseForUserVar() {
      assertFalse(VariableTable.isBuiltinVariable("myVariable"));
   }

   // ==========================================================================
   // isContextVariable
   // ==========================================================================

   @Test
   void isContextVariable_recognisesContextVars() {
      assertTrue(VariableTable.isContextVariable("_USER_"));
      assertTrue(VariableTable.isContextVariable("_ROLES_"));
      assertTrue(VariableTable.isContextVariable("_GROUPS_"));
      assertTrue(VariableTable.isContextVariable("__principal__"));
   }

   @Test
   void isContextVariable_returnsFalseForNonContextVar() {
      assertFalse(VariableTable.isContextVariable("_TODAY"));
      assertFalse(VariableTable.isContextVariable("myVar"));
   }

   // ==========================================================================
   // session
   // ==========================================================================

   @Test
   void setSession_and_getSession() {
      Object session = new Object();
      table.setSession(session);
      assertSame(session, table.getSession());
   }

   // ==========================================================================
   // getSubset
   // ==========================================================================

   @Test
   void getSubset_returnsVariablesWithMatchingPrefix() throws Exception {
      table.put("report.name",  "Sales");
      table.put("report.year",  "2023");
      table.put("other.value",  "irrelevant");

      VariableTable subset = table.getSubset("report.");
      assertEquals("Sales", subset.get("name"),
                   "Prefix should be stripped from key");
      assertEquals("2023",  subset.get("year"));
   }

   @Test
   void getSubset_includesVariablesWithoutDotInName() throws Exception {
      table.put("simple", "value");
      table.put("prefix.qualified", "other");

      VariableTable subset = table.getSubset("prefix.");
      // "prefix.qualified" matches the prefix → stripped to "qualified"
      assertTrue(subset.contains("qualified"), "Prefix-matched key should be in subset");
      assertEquals("other", subset.get("qualified"));
      // "simple" has no dot → included as-is with lower priority
      assertTrue(subset.contains("simple"), "Dot-free key should also be in subset");
      assertEquals("value", subset.get("simple"));
   }

   @Test
   void getSubset_emptyPrefix_returnsAllVariables() throws Exception {
      table.put("a", 1);
      table.put("b", 2);
      VariableTable subset = table.getSubset("");
      assertTrue(subset.contains("a"));
      assertTrue(subset.contains("b"));
   }

   // ==========================================================================
   // addAll
   // ==========================================================================

   @Test
   void addAll_mergesVariablesFromAnotherTable() throws Exception {
      VariableTable other = new VariableTable();
      other.put("x", 10);
      other.put("y", 20);

      table.put("z", 30);
      table.addAll(other);

      assertEquals(10, table.get("x"));
      assertEquals(20, table.get("y"));
      assertEquals(30, table.get("z"));
   }

   @Test
   void addAll_nullTableDoesNotThrow() {
      assertDoesNotThrow(() -> table.addAll(null));
   }

   @Test
   void addAll_mergesFormatsFromSourceTable() throws Exception {
      VariableTable other = new VariableTable();
      other.putFormat("x", "yyyy-MM-dd");

      table.addAll(other);

      assertEquals("yyyy-MM-dd", table.getFormat("x"));
   }

   // ==========================================================================
   // isInternalParameter
   // ==========================================================================

   @Test
   void isInternalParameter_returnsTrueForKnownInternalParam() {
      assertTrue(table.isInternalParameter("inetsoft.sree.web.SessionTimeoutListener"));
   }

   @Test
   void isInternalParameter_returnsFalseForRegularParam() {
      assertFalse(table.isInternalParameter("myParam"));
   }

   // ==========================================================================
   // get(UserVariable) - type dispatch and runtimeValue resolution
   // ==========================================================================

   @Test
   void get_nullUserVariable_returnsNull() throws Exception {
      assertNull(table.get((UserVariable) null));
   }

   @Test
   void get_userVariableWithNullTypeNode_returnsNull() throws Exception {
      UserVariable uv = new UserVariable("x");
      uv.setTypeNode(null);
      assertNull(table.get(uv));
   }

   @Test
   void get_userVariableWithNullName_returnsNull() throws Exception {
      UserVariable uv = new UserVariable(); // name is never set
      assertNull(table.get(uv));
   }

   @Test
   void get_userVariableNonDateType_looksUpPlainName() throws Exception {
      UserVariable uv = new UserVariable("plainVar"); // default type is StringType
      table.put("plainVar", "value");
      assertEquals("value", table.get(uv));
   }

   @Test
   void get_userVariableDateType_looksUpTypeQualifiedKeyFirst() throws Exception {
      UserVariable uv = new UserVariable("dateVar");
      uv.setTypeNode(XSchema.createPrimitiveType(XSchema.DATE));
      table.put("dateVar^_^" + XSchema.DATE, "2024-01-01");

      assertEquals("2024-01-01", table.get(uv));
   }

   @Test
   void get_userVariableDateType_fallsBackToPlainNameWhenTypedKeyAbsent() throws Exception {
      UserVariable uv = new UserVariable("dateVar2");
      uv.setTypeNode(XSchema.createPrimitiveType(XSchema.DATE));
      table.put("dateVar2", "plain-value"); // no "dateVar2^_^date" entry stored

      assertEquals("plain-value", table.get(uv));
   }

   @Test
   void get_runtimeValueTrue_resolvesStoredUserVariableToActualValue() throws Exception {
      VariableTable runtimeTable = new VariableTable(true);
      UserVariable stored = new UserVariable("v1");
      // explicit (Object, String) overload - createValueNode(String, String) means something
      // different (create-by-type) and would silently produce a node with no value set
      stored.setValueNode(XValueNode.createValueNode((Object) "resolved-value", "v1"));
      runtimeTable.put("v1", stored);

      assertEquals("resolved-value", runtimeTable.get("v1"));
   }

   @Test
   void get_runtimeValueFalse_returnsStoredUserVariableAsIs() throws Exception {
      VariableTable rawTable = new VariableTable(false);
      UserVariable stored = new UserVariable("v1");
      stored.setValueNode(XValueNode.createValueNode((Object) "resolved-value", "v1"));
      rawTable.put("v1", stored);

      assertSame(stored, rawTable.get("v1"));
   }

   // ==========================================================================
   // copyParameters(XPrincipal)
   // ==========================================================================

   @Nested
   class CopyParametersTests {

      @Test
      void copiesNewParameterFromPrincipal() throws Exception {
         XPrincipal principal = mock(XPrincipal.class);
         when(principal.getParameterNames()).thenReturn(Set.of("p1"));
         when(principal.getParameter("p1")).thenReturn("v1");
         when(principal.getParameterTS("p1")).thenReturn(1000L);

         table.copyParameters(principal);

         assertEquals("v1", table.get("p1"));
      }

      @Test
      void unchangedParameterAfterFirstCopy_doesNotOverwriteLocalValue() throws Exception {
         XPrincipal principal = mock(XPrincipal.class);
         when(principal.getParameterNames()).thenReturn(Set.of("p1"));
         when(principal.getParameter("p1")).thenReturn("from-principal");
         when(principal.getParameterTS("p1")).thenReturn(1000L); // fixed, "old" timestamp

         table.copyParameters(principal); // brings in p1 the first time
         table.put("p1", "from-report");  // report/onload sets a higher-priority value afterwards

         table.copyParameters(principal); // principal's parameter TS unchanged -> must not overwrite

         assertEquals("from-report", table.get("p1"));
      }

      @Test
      void parameterChangedAfterFirstCopy_overwritesLocalValue() throws Exception {
         XPrincipal principal = mock(XPrincipal.class);
         when(principal.getParameterNames()).thenReturn(Set.of("p1"));
         when(principal.getParameter("p1")).thenReturn("v1");
         when(principal.getParameterTS("p1")).thenReturn(1000L);

         table.copyParameters(principal); // table.copyParameterTS becomes System.currentTimeMillis()

         // simulate the principal's parameter being changed after the first copy: use a
         // fixed far-future timestamp so the test does not depend on real elapsed wall-clock
         // time between the two copyParameters() calls.
         when(principal.getParameter("p1")).thenReturn("v2");
         when(principal.getParameterTS("p1")).thenReturn(System.currentTimeMillis() + 60_000L);

         table.copyParameters(principal);

         assertEquals("v2", table.get("p1"));
      }
   }

   // ==========================================================================
   // Jackson Serializer / Deserializer round trip
   // ==========================================================================

   @Nested
   class JsonSerializationTests {

      private final ObjectMapper mapper = new ObjectMapper();

      @Test
      void roundTrip_preservesSimpleStringValue() throws Exception {
         VariableTable original = new VariableTable();
         original.put("name", "Alice");

         String json = mapper.writeValueAsString(original);
         VariableTable restored = mapper.readValue(json, VariableTable.class);

         assertEquals("Alice", restored.get("name"));
      }

      @Test
      void roundTrip_preservesNullValueEntry() throws Exception {
         VariableTable original = new VariableTable();
         original.put("nullVar", (Object) null);

         String json = mapper.writeValueAsString(original);
         VariableTable restored = mapper.readValue(json, VariableTable.class);

         assertTrue(restored.contains("nullVar"));
         assertNull(restored.get("nullVar"));
      }

      @Test
      void roundTrip_preservesNotIgnoredNullMarker() throws Exception {
         VariableTable original = new VariableTable();
         original.setNotIgnoredNull("v");

         String json = mapper.writeValueAsString(original);
         VariableTable restored = mapper.readValue(json, VariableTable.class);

         assertTrue(restored.isNotIgnoredNull("v"));
      }

      @Test
      void roundTrip_preservesAsIsFlag() throws Exception {
         VariableTable original = new VariableTable();
         original.put("x", "y");
         original.setAsIs("x", true);

         String json = mapper.writeValueAsString(original);
         VariableTable restored = mapper.readValue(json, VariableTable.class);

         assertTrue(restored.isAsIs("x"));
      }

      @Test
      void roundTrip_preservesBaseTableChain() throws Exception {
         VariableTable base = new VariableTable();
         base.put("baseVar", "bv");
         VariableTable original = new VariableTable();
         original.setBaseTable(base);
         original.put("localVar", "lv");

         String json = mapper.writeValueAsString(original);
         VariableTable restored = mapper.readValue(json, VariableTable.class);

         assertEquals("lv", restored.get("localVar"));
         assertEquals("bv", restored.get("baseVar"), "base table chain should round-trip");
      }
   }

   // ==========================================================================
   // printKey
   // ==========================================================================

   @Test
   void printKey_doesNotThrow_andReturnsTrue() throws Exception {
      table.put("a", "1");
      StringWriter sw = new StringWriter();
      assertTrue(table.printKey(new PrintWriter(sw)));
      assertTrue(sw.toString().startsWith("VT["));
   }
}
