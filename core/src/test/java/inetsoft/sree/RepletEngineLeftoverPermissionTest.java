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
package inetsoft.sree;

import inetsoft.report.internal.Util;
import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.sree.security.*;
import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.composer.RemoveAssetController;
import inetsoft.web.composer.model.RemoveAssetEvent;
import inetsoft.web.portal.controller.RepositoryTreeController;
import inetsoft.web.portal.model.RemoveRepositoryEntryEvent;
import inetsoft.web.portal.model.RepositoryEntryModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77939: deleting or moving a global asset removes the permission at its path as a side
 * effect. When that storage write fails the grant stays at the path and a later asset created
 * there receives it, so the delete must report it to the user. Runs against the real asset
 * repository (RepletEngine) and a real FileAuthorizationProvider whose storage fails the remove
 * of chosen keys.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepletEngineLeftoverPermissionTest {
   @BeforeEach
   void setUp() throws Exception {
      FileAuthenticationProvider authc = new FileAuthenticationProvider();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(authc));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      SecurityEngine.getSecurity().init();

      AuthorizationChain chain = (AuthorizationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();
      provider = (FileAuthorizationProvider) chain.getProviders().get(0);
      // opens the storage
      provider.getPermission(ResourceType.REPORT, "probe", ORG);
      real = storage();

      // permission checks are not under test
      AssetRepository.IGNORE_PERM.set(true);
      repository = AssetUtil.getAssetRepository(false);
      admin = new SRPrincipal(new IdentityID("admin", ORG),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], ORG, 1L);
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(provider != null) {
            setStorage(real);
         }

         for(AssetEntry entry : cleanup) {
            try {
               if(entry.isFolder()) {
                  if(repository.containsEntry(entry)) {
                     repository.removeFolder(entry, null, true);
                  }
               }
               else if(repository.containsEntry(entry)) {
                  repository.removeSheet(entry, null, true);
               }
            }
            catch(Exception ignore) {
               // best-effort cleanup
            }
         }
      }
      finally {
         cleanup.clear();
         Tool.clearUserMessage();
         AssetRepository.IGNORE_PERM.remove();

         if(provider != null) {
            provider.tearDown();
         }

         SreeEnv.remove("security.enabled");
      }
   }

   // EM permanent delete (corrupt sheet), enterprise REST delete, save-as overwrite -> removeSheet0
   @Test
   void removeSheet_removeFails_reportsWarningAndGrantStays() throws Exception {
      String path = "vs77939a";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));
      failRemoves(key(path), 1);

      repository.removeSheet(entry, null, true);

      setStorage(real);
      assertFalse(repository.containsEntry(entry));
      assertTrue(granted(real.get(key(path))), "the test expects the grant to be left");
      assertLeftoverWarning(Tool.getUserMessage());
   }

   // portal/composer/EM delete to the recycle bin -> changeSheet0 rename branch
   @Test
   void moveSheetToRecycleBin_removeFails_reportsWarning() throws Exception {
      String path = "vs77939b";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));
      failRemoves(key(path), 1);

      RecycleUtils.moveSheetToRecycleBin(entry, null, recycleBin(), true);

      setStorage(real);
      assertFalse(repository.containsEntry(entry));
      assertTrue(granted(real.get(key(path))), "the test expects the grant to be left");
      assertLeftoverWarning(Tool.getUserMessage());
   }

   @Test
   void removeSheet_removeSucceeds_reportsNothing() throws Exception {
      String path = "vs77939c";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));

      repository.removeSheet(entry, null, true);

      assertNull(real.get(key(path)));
      assertNull(Tool.getUserMessage());
   }

   @Test
   void moveSheetToRecycleBin_removeSucceeds_reportsNothing() throws Exception {
      String path = "vs77939d";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));

      RecycleUtils.moveSheetToRecycleBin(entry, null, recycleBin(), true);

      assertNull(real.get(key(path)));
      assertNull(Tool.getUserMessage());
   }

   // the check that the remove failed can't read the permission, so it may still be there
   @Test
   void removeSheet_removeAndReReadFail_reportsWarning() throws Exception {
      String path = "vs77939e";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));
      failRemoves(key(path), 1);
      failReadsAfterFailedRemove = true;

      repository.removeSheet(entry, null, true);

      setStorage(real);
      assertLeftoverWarning(Tool.getUserMessage());
   }

   // a worksheet folder is moved (e.g. to the recycle bin) by changeFolder, whose permission move
   // in updatePermission is the only pass for it
   @Test
   void assetFolderMove_removeFails_reportsWarning() throws Exception {
      String path = "W77939f";
      AssetEntry folder = addFolder(AssetEntry.Type.FOLDER, path);
      AssetEntry moved = folder(AssetEntry.Type.FOLDER, path + "moved");
      cleanup.add(moved);
      String key = key("ASSET", path);
      grant(key);
      failRemoves(key, 1);

      repository.changeFolder(folder, moved, null, true);

      setStorage(real);
      assertTrue(granted(real.get(key)), "the test expects the grant to be left");
      assertLeftoverWarning(Tool.getUserMessage());
   }

   // a repository folder's permission is moved twice, by updatePermission and then by the
   // registry rename event; the first failure must not be reported if the second pass succeeds
   @Test
   void repositoryFolderRename_firstPassFails_secondPassSucceeds_reportsNothing()
      throws Exception
   {
      String path = "F77939g";
      addFolder(AssetEntry.Type.REPOSITORY_FOLDER, path);
      cleanup.add(folder(AssetEntry.Type.REPOSITORY_FOLDER, path + "moved"));
      grant(key(path));
      failRemoves(key(path), 1);

      renameRepositoryFolder(path, path + "moved");

      setStorage(real);
      assertEquals(0, remainingRemoveFailures(key(path)), "the first pass did not fail");
      assertNull(real.get(key(path)));
      assertTrue(granted(real.get(key(path + "moved"))));
      assertNull(Tool.getUserMessage());
   }

   @Test
   void repositoryFolderRename_bothPassesFail_reportsWarning() throws Exception {
      String path = "F77939h";
      addFolder(AssetEntry.Type.REPOSITORY_FOLDER, path);
      cleanup.add(folder(AssetEntry.Type.REPOSITORY_FOLDER, path + "moved"));
      grant(key(path));
      failRemoves(key(path), 2);

      renameRepositoryFolder(path, path + "moved");

      setStorage(real);
      assertEquals(0, remainingRemoveFailures(key(path)), "both passes did not fail");
      assertTrue(granted(real.get(key(path))), "the test expects the grant to be left");
      assertLeftoverWarning(Tool.getUserMessage());
   }

   // the composer asset tree delete returns the warning
   @Test
   void composerRemoveAsset_removeFails_returnsWarning() throws Exception {
      String path = "vs77939i";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));
      failRemoves(key(path), 1);
      // left by an earlier request on this pooled thread, not returned
      Tool.addUserMessage("stale");

      MessageCommand result;

      try(MockedStatic<Audit> ignored = mockAudit()) {
         result = new RemoveAssetController(repository, null, null, null, recycleBin(), null)
            .removeAsset(new RemoveAssetEvent.Builder().entry(entry).confirmed(true).build(),
                         admin);
      }

      setStorage(real);
      assertFalse(repository.containsEntry(entry));
      assertNotNull(result, "the delete did not report the permission left at its path");
      assertEquals(MessageCommand.Type.WARNING, result.getType());
      assertEquals(leftoverMessage(), result.getMessage());
      assertNull(Tool.getUserMessage(), "the message was not drained");
   }

   @Test
   void composerRemoveAsset_removeSucceeds_returnsNothing() throws Exception {
      String path = "vs77939j";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));

      MessageCommand result;

      try(MockedStatic<Audit> ignored = mockAudit()) {
         result = new RemoveAssetController(repository, null, null, null, recycleBin(), null)
            .removeAsset(new RemoveAssetEvent.Builder().entry(entry).confirmed(true).build(),
                         admin);
      }

      assertNull(result);
      assertNull(real.get(key(path)));
   }

   // the portal repository tree delete returns the warning
   @Test
   void portalRemoveRepositoryEntry_removeFails_returnsWarning() throws Exception {
      String path = "vs77939k";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));
      failRemoves(key(path), 1);

      MessageCommand result = portalRemove(entry);

      setStorage(real);
      assertFalse(repository.containsEntry(entry));
      assertNotNull(result, "the delete did not report the permission left at its path");
      assertEquals(MessageCommand.Type.WARNING, result.getType());
      assertEquals(leftoverMessage(), result.getMessage());
      assertNull(Tool.getUserMessage(), "the message was not drained");
   }

   @Test
   void portalRemoveRepositoryEntry_removeSucceeds_returnsNothing() throws Exception {
      String path = "vs77939l";
      AssetEntry entry = saveViewsheet(path);
      grant(key(path));
      Tool.addUserMessage("stale");

      MessageCommand result = portalRemove(entry);

      assertNull(result);
      assertNull(real.get(key(path)));
   }

   private MessageCommand portalRemove(AssetEntry entry) throws Exception {
      AnalyticRepository analytic = mock(AnalyticRepository.class);
      when(analytic.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      ViewsheetEntry ventry = new ViewsheetEntry(entry.getPath());
      ventry.setAssetEntry(entry);
      RemoveRepositoryEntryEvent event = new RemoveRepositoryEntryEvent.Builder()
         .entry(new RepositoryEntryModel<>(ventry))
         .confirmed(true)
         .build();

      try(MockedStatic<Audit> ignored = mockAudit()) {
         return new RepositoryTreeController(analytic, null, null, null, recycleBin(),
                                             RepletRegistryManager.getInstance())
            .removeRepositoryEntry(event, admin);
      }
   }

   private void renameRepositoryFolder(String oname, String nname) {
      // what RepletRegistry.renameFolder fires for the folder that is renamed directly
      ((AnalyticEngine) repository).propertyChange(new PropertyChangeEvent(
         Util.getOrgEventSourceID("registry_true", ORG), RepletRegistry.RENAME_FOLDER_EVENT,
         oname, nname));
   }

   private static MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      return audit;
   }

   private RecycleBin recycleBin() {
      RecycleBin bin = mock(RecycleBin.class);
      // the sheet moved into the bin is removed after the test
      doAnswer(inv -> {
         cleanup.add(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                    inv.getArgument(0), null, ORG));
         return null;
      }).when(bin).addEntry(anyString(), anyString(), anyString(), any(), anyInt(), anyInt(),
                            any());
      return bin;
   }

   private AssetEntry saveViewsheet(String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, ORG);
      cleanup.add(entry);
      repository.setSheet(entry, new Viewsheet(), admin, true);
      assertTrue(repository.containsEntry(entry));
      return entry;
   }

   private AssetEntry addFolder(AssetEntry.Type type, String path) throws Exception {
      AssetEntry entry = folder(type, path);
      cleanup.add(entry);
      repository.addFolder(entry, null);
      assertTrue(repository.containsEntry(entry));
      return entry;
   }

   private static AssetEntry folder(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null, ORG);
   }

   private static void assertLeftoverWarning(UserMessage message) {
      assertNotNull(message, "the permission left at the path was not reported");
      assertEquals(ConfirmException.WARNING, message.getLevel());
      assertEquals(leftoverMessage(), message.getMessage());
   }

   private static String leftoverMessage() {
      return Catalog.getCatalog().getString("em.repository.permissionsMayRemain");
   }

   private void grant(String key) throws Exception {
      Permission p = new Permission();
      p.setUserGrantsForOrg(ResourceAction.READ, Set.of("mallory"), ORG);
      real.put(key, p).get();
   }

   private static boolean granted(Permission p) {
      return p != null && p.getUserGrants(ResourceAction.READ, ORG).stream()
         .anyMatch(i -> "mallory".equals(i.getName()));
   }

   private static String key(String path) {
      return key("REPORT", path);
   }

   private static String key(String type, String path) {
      return type + ":" + ORG + ":" + path;
   }

   private int remainingRemoveFailures(String key) {
      return removeFailures.getOrDefault(key, 0);
   }

   /**
    * Fails the next {@code count} removes of the key. The other writes and the reads go to the
    * real storage.
    */
   @SuppressWarnings("unchecked")
   private void failRemoves(String failKey, int count) throws Exception {
      removeFailures.put(failKey, count);
      KeyValueStorage<Permission> failing =
         mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doAnswer(inv -> {
         String key = inv.getArgument(0);
         int left = removeFailures.getOrDefault(key, 0);

         if(left > 0) {
            removeFailures.put(key, left - 1);
            failedRemoves.add(key);
            return CompletableFuture.failedFuture(new IOException("simulated write failure"));
         }

         return real.remove(key);
      }).when(failing).remove(anyString());
      doAnswer(inv -> {
         String key = inv.getArgument(0);

         if(failReadsAfterFailedRemove && failedRemoves.contains(key)) {
            throw new IllegalStateException("simulated read failure");
         }

         return copy(real.get(key));
      }).when(failing).get(anyString());
      doAnswer(inv -> real.stream().map(p -> new KeyValuePair<>(p.getKey(), copy(p.getValue()))))
         .when(failing).stream();
      setStorage(failing);
   }

   private static Permission copy(Permission p) {
      return p == null ? null : (Permission) p.clone();
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<Permission> storage() throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      return (KeyValueStorage<Permission>) f.get(provider);
   }

   private void setStorage(KeyValueStorage<Permission> s) throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(provider, s);
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private final List<AssetEntry> cleanup = new ArrayList<>();
   private final Map<String, Integer> removeFailures = new HashMap<>();
   private final Set<String> failedRemoves = new HashSet<>();
   private boolean failReadsAfterFailedRemove;
   private FileAuthorizationProvider provider;
   private KeyValueStorage<Permission> real;
   private AssetRepository repository;
   private SRPrincipal admin;
}
