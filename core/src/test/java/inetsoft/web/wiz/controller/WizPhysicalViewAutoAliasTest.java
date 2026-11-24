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
package inetsoft.web.wiz.controller;

import inetsoft.uql.erm.AutoAlias;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.wiz.controller.WizDataModelWire.Failure;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.WriteResponse;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The auto aliases of a physical view through the one-request session, against the real
 * registry, runtime service and manager service: written on create and update, read back by get,
 * replaced, cleared, kept when the key is absent, and refused where the Portal's dialog would not
 * offer them.
 */
class WizPhysicalViewAutoAliasTest extends WizPhysicalViewTestBase {
   private static Map<String, Object> autoAlias(String from, String alias, boolean keep,
                                                String prefix)
   {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("from", from);
      entry.put("alias", alias);
      entry.put("keepOutgoing", keep);

      if(prefix != null) {
         entry.put("prefix", prefix);
      }

      return entry;
   }

   private static Map<String, Object> withAutoAliases(Map<String, Object> table,
                                                      Object... entries)
   {
      table.put("autoAliases", new ArrayList<>(List.of(entries)));
      return table;
   }

   private WriteResponse createWithAutoAlias() throws Exception {
      return createView(definition(
         "v", tables(withAutoAliases(plain("T1"), autoAlias("T2", "t2a", true, "p_")),
                     plain("T2")),
         join("T2", "T1_ID", "T1", "ID")));
   }

   private static Map<?, ?> table(Map<String, Object> definition, String name) {
      for(Object table : (List<?>) definition.get("tables")) {
         if(name.equals(((Map<?, ?>) table).get("name"))) {
            return (Map<?, ?>) table;
         }
      }

      return fail("no table " + name + " in " + definition);
   }

   private static List<String> fields(Object failure) {
      return ((Failure) failure).errors().stream().map(WizDataModelWire.Err::field).toList();
   }

   private static List<String> errorCodes(Object failure) {
      return ((Failure) failure).errors().stream().map(WizDataModelWire.Err::code).toList();
   }

   @Test
   void anAutoAliasIsWrittenOnCreateAndReadBack() throws Exception {
      WriteResponse created = createWithAutoAlias();

      assertTrue(created.ok());
      AutoAlias stored = storedView("v").getAutoAlias("T1");
      assertNotNull(stored);
      assertEquals(1, stored.getIncomingJoinCount());
      AutoAlias.IncomingJoin join = stored.getIncomingJoin(0);
      assertEquals("T2", join.getSourceTable());
      assertEquals("t2a", join.getAlias());
      assertTrue(join.isKeepOutgoing());
      assertEquals("p_", join.getPrefix());

      List<?> read = (List<?>) table(getView("v").definition(), "T1").get("autoAliases");
      assertEquals(List.of(autoAlias("T2", "t2a", true, "p_")), read);
      assertNull(table(getView("v").definition(), "T2").get("autoAliases"),
                 "a table without auto aliases carries no key");
      assertEquals(created.definition(), getView("v").definition());
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void aGetResultFedBackIsANoOp() throws Exception {
      createWithAutoAlias();
      int puts = runtimePuts.get();
      WriteResponse same = ok(updateView("v", getView("v").definition(), null));

      assertTrue(same.ok());
      assertEquals(puts, runtimePuts.get(), "an unchanged definition opens no runtime");
      assertEquals(1, storedView("v").getAutoAlias("T1").getIncomingJoinCount());
   }

   @Test
   void aListReplacesTheSetAndAnEmptyListClearsIt() throws Exception {
      createWithAutoAlias();

      // replace: another name, no kept outgoing joins (the prefix goes away with the flag)
      Map<String, Object> next = definition(
         "v", tables(withAutoAliases(plain("T1"), autoAlias("T2", "t2b", false, null)),
                     plain("T2")),
         join("T2", "T1_ID", "T1", "ID"));
      assertTrue(ok(updateView("v", next, null)).ok());
      AutoAlias.IncomingJoin join = storedView("v").getAutoAlias("T1").getIncomingJoin(0);
      assertEquals("t2b", join.getAlias());
      assertFalse(join.isKeepOutgoing());
      assertEquals(List.of(autoAlias("T2", "t2b", false, null)),
                   table(getView("v").definition(), "T1").get("autoAliases"));

      // clear
      Map<String, Object> clear = definition(
         "v", tables(withAutoAliases(plain("T1")), plain("T2")),
         join("T2", "T1_ID", "T1", "ID"));
      assertTrue(ok(updateView("v", clear, null)).ok());
      assertNull(storedView("v").getAutoAlias("T1"));
      assertNull(table(getView("v").definition(), "T1").get("autoAliases"));
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void anAbsentKeyKeepsTheStoredAutoAliases() throws Exception {
      createWithAutoAlias();
      Map<String, Object> description = new LinkedHashMap<>();
      description.put("name", "v");
      description.put("description", "keeps it");
      assertTrue(ok(updateView("v", description, null)).ok());
      assertEquals(1, storedView("v").getAutoAlias("T1").getIncomingJoinCount());

      // a table list that does not mention autoAliases keeps them too
      Map<String, Object> tablesOnly = definition(
         "v", tables(plain("T1"), plain("T2")), join("T2", "T1_ID", "T1", "ID"));
      tablesOnly.put("description", "still keeps it");
      assertTrue(ok(updateView("v", tablesOnly, null)).ok());
      assertEquals(1, storedView("v").getAutoAlias("T1").getIncomingJoinCount());
   }

   @Test
   void removingTheLastJoinOfAnAutoAliasIsRefusedUntilTheAliasIsDroppedExplicitly()
      throws Exception
   {
      createWithAutoAlias();
      Map<String, Object> noJoin = definition("v", tables(plain("T1"), plain("T2")));
      Object refused = updateView("v", noJoin, null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) refused).reason());
      assertTrue(fields(refused).contains("tables[T1].autoAliases[0].from"), fields(refused).toString());
      assertTrue(errorCodes(refused).contains("AUTO_ALIAS_DROPPED"), errorCodes(refused).toString());
      assertEquals(1, storedView("v").getRelationshipCount(), "nothing was written");
      assertEquals(1, storedView("v").getAutoAlias("T1").getIncomingJoinCount());
      assertTrue(runtimes.isEmpty());

      Map<String, Object> dropped = definition("v", tables(withAutoAliases(plain("T1")),
                                                           plain("T2")));
      assertTrue(ok(updateView("v", dropped, null)).ok());
      assertEquals(0, storedView("v").getRelationshipCount());
      assertNull(storedView("v").getAutoAlias("T1"));
   }

   @Test
   void replacingTheJoinOfAnAutoAliasKeepsIt() throws Exception {
      createWithAutoAlias();
      Map<String, Object> changed = join("T1", "ID", "T2", "T1_ID");
      changed.put("type", "LEFT_OUTER");
      Map<String, Object> next = definition("v", tables(plain("T1"), plain("T2")), changed);
      assertTrue(ok(updateView("v", next, null)).ok());

      assertEquals(1, storedView("v").getAutoAlias("T1").getIncomingJoinCount());
   }

   @Test
   void anAutoAliasOnAnAliasTableIsRefused() throws Exception {
      Object result = createViewRaw(definition(
         "v", tables(plain("T1"), plain("T2"),
                     withAutoAliases(alias("T1_A", "T1"), autoAlias("T2", "x", false, null))),
         join("T2", "T1_ID", "T1_A", "ID")), null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) result).reason());
      assertTrue(fields(result).contains("tables[T1_A].autoAliases"), fields(result).toString());
      assertNull(storedView("v"));
   }

   @Test
   void anAutoAliasOnAnInlineViewIsRefused() throws Exception {
      Object result = createViewRaw(definition(
         "v", tables(plain("T2"), withAutoAliases(
            inline("IV", "select ID from T1"), autoAlias("T2", "x", false, null))),
         join("T2", "T1_ID", "IV", "ID")), null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) result).reason());
      assertTrue(fields(result).contains("tables[IV].autoAliases"), fields(result).toString());
   }

   @Test
   void everyAutoAliasProblemIsNamed() throws Exception {
      // a table that is not joined to T1, a collision with a table, a duplicate name, a table
      // that is not in the view, itself
      Object result = createViewRaw(definition(
         "v", tables(withAutoAliases(plain("T1"),
                                     autoAlias("T3", "a1", false, null),
                                     autoAlias("T2", "T2", false, null),
                                     autoAlias("T9", "a1", false, null),
                                     autoAlias("T1", "a2", false, null)),
                     plain("T2"), plain("T3")),
         join("T2", "T1_ID", "T1", "ID")), null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) result).reason());
      List<String> fields = fields(result);
      assertTrue(fields.contains("tables[T1].autoAliases[0].from"), fields.toString());
      assertTrue(fields.contains("tables[T1].autoAliases[1].alias"), fields.toString());
      assertTrue(fields.contains("tables[T1].autoAliases[2].from"), fields.toString());
      assertTrue(fields.contains("tables[T1].autoAliases[2].alias"), fields.toString());
      assertTrue(fields.contains("tables[T1].autoAliases[3].from"), fields.toString());
      assertEquals(0, runtimePuts.get());
      assertNull(storedView("v"));
   }

   @Test
   void theAliasNameIsValidatedByThePortalsOwnRule() throws Exception {
      Object result = createViewRaw(definition(
         "v", tables(withAutoAliases(plain("T1"), autoAlias("T2", "has.dot", false, null)),
                     plain("T2")),
         join("T2", "T1_ID", "T1", "ID")), null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) result).reason());
      assertTrue(fields(result).contains("tables[T1].autoAliases[0].alias"),
                 fields(result).toString());
      assertNull(storedView("v"));
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void theAutoAliasKeysAndPrefixRuleAreNamed() throws Exception {
      Map<String, Object> bad = autoAlias("T2", "t2a", true, null);
      bad.put("colour", "red");
      Map<String, Object> strayPrefix = autoAlias("T2", "t2b", false, "p_");
      Object result = createViewRaw(definition(
         "v", tables(withAutoAliases(plain("T1"), bad, strayPrefix), plain("T2")),
         join("T2", "T1_ID", "T1", "ID")), null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) result).reason());
      List<String> fields = fields(result);
      assertTrue(fields.contains("tables[T1].autoAliases[0].colour"), fields.toString());
      assertTrue(fields.contains("tables[T1].autoAliases[0].prefix"), fields.toString());
      assertTrue(fields.contains("tables[T1].autoAliases[1].prefix"), fields.toString());
      assertEquals(0, runtimePuts.get());
   }

   @Test
   void theDryRunReportsWithoutWriting() throws Exception {
      createView(definition("v", tables(plain("T1"), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));
      Object dry = updateView("v", definition(
         "v", tables(withAutoAliases(plain("T1"), autoAlias("T2", "t2a", false, null)),
                     plain("T2")),
         join("T2", "T1_ID", "T1", "ID")), true);

      assertTrue(ok(dry).ok());
      assertNull(storedView("v").getAutoAlias("T1"));
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void anExtendedViewCannotChangeTheAutoAliasesOfAnInheritedTable() throws Exception {
      createWithAutoAlias();
      Map<String, Object> ext = definition(
         "ext", tables(withAutoAliases(plain("T1"), autoAlias("T2", "other", false, null)),
                       plain("T2")),
         join("T2", "T1_ID", "T1", "ID"));
      ext.put("parent", "v");
      Object result = createViewRaw(ext, null);

      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, ((Failure) result).reason());
      assertNull(storedView("ext"));
   }
}
