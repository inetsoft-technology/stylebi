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
package inetsoft.web.admin.content.repository;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.xmla.*;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78335: deleting or renaming an XMLA data source X left the grants of its cubes, CUBE
 * "X::cube" for each cube of the domain of X, under the old name. After a rename the cube was
 * open to every reader of the data source, and a data source created later at X got the old
 * cube grants. They are now removed and moved with the data source, read from the stored domain
 * of the data source. Axis: the registry delete, the portal and public API delete (the data
 * model removed first), a data source folder delete, the registry rename, a folder rename of a
 * loadable member and of a member that can't be loaded (moved with the rest of the folder), and
 * a domain that can't be read, whose cube grants are left (logged) without stopping the delete
 * or the rename. Each checks the exact keys, and that a data source "X2" keeps its cube grants.
 * <p>
 * The registry, the repository and the stored domains are the real ones. The permissions are
 * kept in a map behind a mocked security engine and provider.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceCubePermissionTest {
   private static final List<String> CUBES = List.of("Sales", "Stock Levels");

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private IndexedStorage indexedStorage;
   private final Map<String, Permission> store = new HashMap<>();
   private MockedStatic<SecurityEngine> securityStatic;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(security.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(inv -> store.get(key(inv.getArgument(0), inv.getArgument(1))));
      doAnswer(inv -> store.put(key(inv.getArgument(0), inv.getArgument(1)), inv.getArgument(2)))
         .when(security).setPermission(any(ResourceType.class), anyString(), any());
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(security).removePermission(any(ResourceType.class), anyString());
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
   }

   @AfterEach
   void tearDown() {
      registry.clearCache();
      securityStatic.close();
   }

   // the registry delete (EM, data source folder delete, a deleted user's resources)
   @Test
   void registryDeleteRemovesCubeGrants() throws Exception {
      seed("CbA");
      seed("CbA2");

      registry.removeDataSource("CbA");

      assertEquals(List.of(), grants("CbA"));
      assertEquals(keys("CbA2"), grants("CbA2"));
   }

   // the portal and public API delete, which removes the data model before the data source
   @Test
   void dataModelFirstDeleteRemovesCubeGrants() throws Exception {
      seed("CbB");
      seed("CbB2");

      assertTrue(repository.removeDataSource("CbB", true));

      assertEquals(List.of(), grants("CbB"));
      assertEquals(keys("CbB2"), grants("CbB2"));
   }

   // the removal of the data model alone leaves the domain, and the grants of its cubes
   @Test
   void dataModelRemovalKeepsCubeGrants() throws Exception {
      seed("CbM");

      ((XEngine) repository).removeDataModel("CbM");

      assertEquals(keys("CbM"), grants("CbM"));
   }

   @Test
   void folderDeleteRemovesCubeGrants() throws Exception {
      addFolder("CbFd");
      addFolder("CbFd2");
      seed("CbFd/CbE");
      seed("CbFd2/CbE");

      assertTrue(repository.removeDataSourceFolder("CbFd"));

      assertEquals(List.of(), grants("CbFd/CbE"));
      assertEquals(keys("CbFd2/CbE"), grants("CbFd2/CbE"));
   }

   @Test
   void registryRenameMovesCubeGrants() throws Exception {
      seed("CbC");
      seed("CbC2");

      registry.renameDatasource("CbC", "CbCx");

      assertEquals(List.of(), grants("CbC"));
      assertEquals(keys("CbCx"), grants("CbCx"));
      assertEquals(keys("CbC2"), grants("CbC2"));
   }

   @Test
   void folderRenameMovesCubeGrants() throws Exception {
      addFolder("CbFr");
      seed("CbFr/CbF");

      repository.updateDataSourceFolder(
         new DataSourceFolder("CbFs", LocalDateTime.now(), null), "CbFr");

      assertEquals(List.of(), grants("CbFr/CbF"));
      assertEquals(keys("CbFs/CbF"), grants("CbFs/CbF"));
   }

   // a member whose definition is damaged isn't renamed by renameDatasource, it is moved with the
   // rest of the folder
   @Test
   void folderRenameOfAMemberThatCantBeLoadedMovesCubeGrants() throws Exception {
      addFolder("CbUr");
      seed("CbUr/CbU");
      seed("CbUr/CbU2");
      corrupt("CbUr/CbU");

      registry.renameDataSourceFolder("CbUr", "CbUs");

      assertTrue(containsDataSource("CbUs/CbU"));
      assertTrue(registry.containObject(entry(AssetEntry.Type.DOMAIN, "CbUs/CbU")));
      assertEquals(List.of(), grants("CbUr/CbU"));
      assertEquals(List.of(), grants("CbUr/CbU2"));
      assertEquals(keys("CbUs/CbU"), grants("CbUs/CbU"));
      assertEquals(keys("CbUs/CbU2"), grants("CbUs/CbU2"));
   }

   // a domain that can't be read has no known cubes: the rename isn't stopped and the cube
   // grants stay at the old name, the grant of the data source is moved
   @Test
   void renameWithADomainThatCantBeReadLeavesCubeGrants() throws Exception {
      seed("CbR");
      damageDomain("CbR");

      registry.renameDatasource("CbR", "CbRx");

      assertNotNull(registry.getDataSource("CbRx"));
      assertEquals(cubeKeys("CbR"), grants("CbR"));
      assertEquals(List.of(key(ResourceType.DATA_SOURCE, "CbRx")), grants("CbRx"));
   }

   // the same in the move of the rest of a folder, which moves the domain with its content
   @Test
   void folderRenameWithADomainThatCantBeReadLeavesCubeGrants() throws Exception {
      addFolder("CbDr");
      seed("CbDr/CbD");
      corrupt("CbDr/CbD");
      damageDomain("CbDr/CbD");

      registry.renameDataSourceFolder("CbDr", "CbDs");

      assertTrue(containsDataSource("CbDs/CbD"));
      assertTrue(registry.containObject(entry(AssetEntry.Type.DOMAIN, "CbDs/CbD")));
      assertEquals(cubeKeys("CbDr/CbD"), grants("CbDr/CbD"));
      assertEquals(List.of(key(ResourceType.DATA_SOURCE, "CbDs/CbD")), grants("CbDs/CbD"));
   }

   // a delete isn't stopped either
   @Test
   void deleteWithADomainThatCantBeReadLeavesCubeGrants() throws Exception {
      seed("CbW");
      damageDomain("CbW");

      assertTrue(repository.removeDataSource("CbW", true));

      assertFalse(containsDataSource("CbW"));
      assertEquals(cubeKeys("CbW"), grants("CbW"));
   }

   // an XMLA data source p with the cubes of CUBES in its domain, and a grant of the data source
   // and of each cube
   private void seed(String p) {
      XMLADataSource dataSource = new XMLADataSource();
      dataSource.setName(p);
      dataSource.setURL("http://localhost/xmla");
      registry.setDataSource(dataSource, false);
      Domain domain = new Domain();
      domain.setDataSource(p);
      List<Cube> cubes = new ArrayList<>();

      for(String name : CUBES) {
         Cube cube = new Cube();
         cube.setName(name);
         cubes.add(cube);
      }

      domain.setCubes(cubes);
      registry.setDomain(domain);
      registry.clearCache();
      XDomain stored = registry.getDomain(p);
      assertNotNull(stored, "not seeded: " + p);
      assertNotNull(stored.getCube("Sales"), "not seeded: " + p);

      for(String key : keys(p)) {
         int index = key.indexOf('|');
         grant(ResourceType.valueOf(key.substring(0, index)), key.substring(index + 1));
      }
   }

   // the grants seeded for a data source p, sorted
   private static List<String> keys(String p) {
      List<String> keys = new ArrayList<>(cubeKeys(p));
      keys.add(key(ResourceType.DATA_SOURCE, p));
      Collections.sort(keys);
      return keys;
   }

   private static List<String> cubeKeys(String p) {
      List<String> keys = new ArrayList<>();

      for(String cube : CUBES) {
         keys.add(key(ResourceType.CUBE, p + "::" + cube));
      }

      Collections.sort(keys);
      return keys;
   }

   // the stored grants of a data source p and its cubes, by their exact keys, sorted
   private List<String> grants(String p) {
      List<String> result = new ArrayList<>();

      for(String key : store.keySet()) {
         String resource = key.substring(key.indexOf('|') + 1);

         if(resource.equals(p) || resource.startsWith(p + "::")) {
            result.add(key);
         }
      }

      Collections.sort(result);
      return result;
   }

   // stores the data source as an empty wrapper, which can't be loaded
   private void corrupt(String path) throws Exception {
      indexedStorage.putXMLSerializable(
         entry(AssetEntry.Type.DATA_SOURCE, path).toIdentifier(), new XDataSourceWrapper());
      registry.clearCache();
      assertNull(registry.getDataSource(path));
      assertTrue(containsDataSource(path));
   }

   // stores a domain whose class can't be loaded, which is read as a wrapper of no domain
   private void damageDomain(String path) throws Exception {
      String xml = "<Domain datasource=\"" + path + "\" version=\"10.1\" " +
         "class=\"no.such.Domain\"><Cubes></Cubes></Domain>";
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      indexedStorage.putDocument(entry(AssetEntry.Type.DOMAIN, path).toIdentifier(), doc,
                                 XDomainWrapper.class.getName());
      registry.clearCache();
      XDomainWrapper wrapper =
         (XDomainWrapper) registry.getObject(entry(AssetEntry.Type.DOMAIN, path), false);
      assertNotNull(wrapper);
      assertNull(wrapper.getDomain(), "the domain can be read");
   }

   private boolean containsDataSource(String path) {
      return registry.containObject(entry(AssetEntry.Type.DATA_SOURCE, path));
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private static AssetEntry entry(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
   }

   private static String key(ResourceType type, String path) {
      return type + "|" + path;
   }

   private void grant(ResourceType type, String path) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(key(type, path), permission);
   }
}
