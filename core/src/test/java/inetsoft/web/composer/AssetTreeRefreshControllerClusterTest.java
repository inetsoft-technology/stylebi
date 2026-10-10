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
package inetsoft.web.composer;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.web.composer.model.AssetChangeEventModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78229: in a cluster, a change made on one node (e.g. a Composer save proxied to the node
 * that holds the runtime sheet) must refresh the asset tree of a user whose web socket is on
 * another node. Each "node" here is a real {@link AssetTreeRefreshController} with its own
 * subscriptions and messaging template. The nodes are connected by a message bus that behaves
 * like Ignite's {@code sendOrdered}: every node, the sender included, receives the message, and
 * the sender's copy is marked local. The bus holds the messages until {@link Bus#deliverAll()},
 * so the tests control when they arrive.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetTreeRefreshControllerClusterTest {
   @BeforeEach
   void setUp() {
      orgId = Organization.getDefaultOrganizationID();
      bus = new Bus();
   }

   @AfterEach
   void tearDown() {
      for(Node node : nodes) {
         node.controller.preDestroy();
      }

      AssetUtil.setAssetRepository(false, null);
   }

   @Test
   void changeOnAnotherNodeIsDeliveredOnceToEachNode() throws Exception {
      Node nodeA = node("A", mockRepository());
      Node nodeB = node("B", mockRepository());
      Principal userA = nodeA.subscribe("s1", "admin");
      Principal userB = nodeB.subscribe("s2", "admin");
      // a user of another organization on node A must not get the change
      Principal otherOrgUserA =
         nodeA.subscribe("s3", new XPrincipal(new IdentityID("admin", "otherOrg78229")));
      AssetEntry viewsheet = newViewsheetInRoot();

      // the save runs on node B, the user's web socket is on node A
      nodeB.fire(event(viewsheet));
      awaitSent(1);
      assertEquals("B", bus.sent.get(0).sender());

      // node B delivers the change to its own subscriber
      nodeB.awaitDelivery(userB);

      // the message reaches node A and, as a local copy, node B again. Node B has already
      // delivered the change, so a second delivery there would show as a second send
      bus.deliverAll();

      AssetChangeEventModel model = nodeA.awaitDelivery(userA);
      assertEquals(viewsheet.getParent().toIdentifier(), model.parentEntry().toIdentifier());
      assertEquals(viewsheet.toIdentifier(), model.newIdentifier());
      Thread.sleep(300L);
      verify(nodeB.template, times(1))
         .convertAndSendToUser(anyString(), eq("/asset-changed"), any());
      verify(nodeA.template, times(1))
         .convertAndSendToUser(anyString(), eq("/asset-changed"), any());
      verify(nodeA.template, never()).convertAndSendToUser(
         eq(SUtil.getUserDestination(otherOrgUserA)), eq("/asset-changed"), any());
   }

   @Test
   void storageRefreshEventIsNotBroadcast() throws Exception {
      TestAssetEngine engine = new TestAssetEngine();
      node("A", mockRepository());
      Node nodeB = node("B", engine);
      Principal userB = nodeB.subscribe("s2", "admin");
      AssetEntry viewsheet = newViewsheetInRoot();

      // the shared storage reports the change on every node, here on node B
      engine.storageRefreshListener.storageRefreshed(new StorageRefreshEvent(
         this, System.currentTimeMillis(),
         List.of(new TimestampIndexChange(viewsheet.toIdentifier(),
                                          TimestampIndexChangeType.MODIFY))));

      Thread.sleep(500L);
      assertTrue(bus.sent.isEmpty(), "a storage refresh event must not be broadcast");

      // a change made on the node itself is still broadcast after a storage refresh
      engine.fireLocalChange(viewsheet);
      awaitSent(1);
      assertEquals("B", bus.sent.get(0).sender());

      // the storage refresh event is still delivered to the node's own subscribers
      AssetChangeEventModel model = nodeB.awaitDelivery(userB);
      assertEquals(viewsheet.toIdentifier(), model.newIdentifier());
   }

   @Test
   void changeFiredByTwoRepositoriesIsBroadcastOnce() throws Exception {
      Node nodeA = node("A", mockRepository(), mockRepository());
      assertEquals(2, nodeA.listeners.size());

      nodeA.fire(event(newViewsheetInRoot()));

      awaitSent(1);
      Thread.sleep(500L);
      assertEquals(1, bus.sent.size());
   }

   @Test
   void changesTheTreeIgnoresAreNotForwarded() throws Exception {
      Node nodeB = node("B", mockRepository());
      AssetEntry viewsheet = newViewsheetInRoot();

      // an auto-save, a change below the root event and a root folder update are not
      // tree changes, so they are not sent to the other nodes
      nodeB.fire(new AssetChangeEvent(this, viewsheet.getType().id(),
                                      AssetChangeEvent.AUTO_SAVE_ADD, viewsheet, null, true,
                                      null, "autoSave"));
      nodeB.fire(new AssetChangeEvent(this, viewsheet.getType().id(),
                                      AssetChangeEvent.ASSET_MODIFIED, viewsheet, null, false,
                                      null, "nested"));
      nodeB.fire(event(viewsheet.getParent()));

      Thread.sleep(500L);
      assertTrue(bus.sent.isEmpty(), "ignored changes must not be broadcast: " + bus.sent);
   }

   private AssetEntry newViewsheetInRoot() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "Bug78229VS", null, orgId);
   }

   private AssetChangeEvent event(AssetEntry entry) {
      return new AssetChangeEvent(this, entry.getType().id(), AssetChangeEvent.ASSET_MODIFIED,
                                  entry, null, true, null, "setSheet: " + entry);
   }

   private void awaitSent(int count) {
      long end = System.currentTimeMillis() + 5000L;

      while(bus.sent.size() < count && System.currentTimeMillis() < end) {
         try {
            Thread.sleep(20L);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
         }
      }

      assertEquals(count, bus.sent.size(), "cluster messages sent");
   }

   private static AssetRepository mockRepository() {
      return mock(AssetRepository.class);
   }

   private Node node(String name, AssetRepository repository) {
      return node(name, repository, repository);
   }

   private Node node(String name, AssetRepository repository, AssetRepository runtime) {
      Node node = new Node(name, repository, runtime);
      nodes.add(node);
      return node;
   }

   private final class Node {
      Node(String name, AssetRepository repository, AssetRepository runtime) {
         template = mock(SimpMessagingTemplate.class);
         SecurityEngine security = mock(SecurityEngine.class);
         when(security.getOrganizations()).thenReturn(new String[0]);
         LibManagerProvider libs = mock(LibManagerProvider.class);
         when(libs.getManager(nullable(String.class))).thenReturn(mock(LibManager.class));
         Cluster cluster = mock(Cluster.class);
         doAnswer(i -> bus.listeners.put(name, i.getArgument(0)))
            .when(cluster).addMessageListener(any());

         try {
            doAnswer(i -> bus.sent.add(new Sent(name, i.getArgument(0))))
               .when(cluster).sendMessage(any(Serializable.class));
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         controller = new AssetTreeRefreshController();
         controller.setAssetRepository(repository);
         controller.setMessagingTemplate(template);
         controller.setSecurityEngine(security);
         controller.setDataSourceRegistry(mock(DataSourceRegistry.class));
         controller.setLibManagerProvider(libs);
         controller.setCluster(cluster);
         // the runtime repository the controller also listens to
         AssetUtil.setAssetRepository(false, runtime);
         controller.addListeners();

         for(AssetRepository repo : new LinkedHashSet<>(List.of(repository, runtime))) {
            if(mockingDetails(repo).isMock()) {
               ArgumentCaptor<AssetChangeListener> captor =
                  ArgumentCaptor.forClass(AssetChangeListener.class);
               verify(repo).addAssetChangeListener(captor.capture());
               listeners.add(captor.getValue());
            }
         }
      }

      Principal subscribe(String sessionId, String userName) {
         return subscribe(sessionId, new XPrincipal(new IdentityID(userName, orgId)));
      }

      Principal subscribe(String sessionId, Principal principal) {
         StompHeaderAccessor header = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
         header.setSessionId(sessionId);
         controller.subscribeToTopic(header, principal);
         return principal;
      }

      void fire(AssetChangeEvent event) {
         listeners.forEach(l -> l.assetChanged(event));
      }

      AssetChangeEventModel awaitDelivery(Principal user) {
         ArgumentCaptor<AssetChangeEventModel> captor =
            ArgumentCaptor.forClass(AssetChangeEventModel.class);
         verify(template, timeout(DEBOUNCE_WAIT)).convertAndSendToUser(
            eq(SUtil.getUserDestination(user)), eq("/asset-changed"), captor.capture());
         return captor.getValue();
      }

      final AssetTreeRefreshController controller;
      final SimpMessagingTemplate template;
      final List<AssetChangeListener> listeners = new ArrayList<>();
   }

   private record Sent(String sender, Serializable message) {
   }

   /**
    * Delivers like Ignite's ordered messages: to every node including the sender, whose copy is
    * local.
    */
   private static final class Bus {
      void deliverAll() {
         for(Sent s : new ArrayList<>(sent)) {
            listeners.forEach((name, listener) -> listener.messageReceived(
               new MessageEvent(this, s.sender(), name.equals(s.sender()), s.message())));
         }
      }

      final Map<String, MessageListener> listeners = new LinkedHashMap<>();
      final List<Sent> sent = new CopyOnWriteArrayList<>();
   }

   /**
    * A real asset engine with a mocked indexed storage, so the events it fires for a storage
    * refresh go through the real refresh listener.
    */
   private static final class TestAssetEngine extends AbstractAssetEngine {
      TestAssetEngine() {
         super((LibManagerProvider) null, (Cluster) null);
         istore = mock(IndexedStorage.class);
         initStorageRefreshListener();
         ArgumentCaptor<StorageRefreshListener> captor =
            ArgumentCaptor.forClass(StorageRefreshListener.class);
         verify(istore).addStorageRefreshListener(captor.capture());
         storageRefreshListener = captor.getValue();
      }

      void fireLocalChange(AssetEntry entry) {
         fireEvent(entry.getType().id(), AssetChangeEvent.ASSET_MODIFIED, entry, null, true, null,
                   "setSheet: " + entry);
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type,
                                     IdentityID resource, EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source,
                                                       Principal user)
      {
         return true;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return true;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return true;
      }

      final StorageRefreshListener storageRefreshListener;
   }

   // the delivery is debounced for 2 seconds
   private static final long DEBOUNCE_WAIT = 3500L;

   private String orgId;
   private Bus bus;
   private final List<Node> nodes = new ArrayList<>();
}
