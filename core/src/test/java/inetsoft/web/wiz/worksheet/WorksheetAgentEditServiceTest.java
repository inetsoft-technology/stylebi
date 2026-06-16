package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.PropertiesEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.DataCacheSweeper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("core")
class WorksheetAgentEditServiceTest {
   // new Worksheet() builds a WorksheetInfo which reads SreeEnv (a PropertiesEngine
   // Spring bean); building an SRPrincipal resolves an XSessionService bean; Mockito's
   // inline mock maker forces WorksheetEngine static init which resolves DataCacheSweeper.
   // A minimal application context supplying all three must be present first.
   private static GenericApplicationContext appContext;

   @BeforeAll
   static void initContext() {
      PropertiesEngine props = mock(PropertiesEngine.class);
      when(props.getProperty(anyString(), anyBoolean())).thenReturn("");
      when(props.getProperty(anyString())).thenReturn("");

      appContext = new GenericApplicationContext();
      appContext.getBeanFactory().registerSingleton("propertiesEngine", props);
      appContext.getBeanFactory().registerSingleton(
         "dataCacheSweeper", mock(DataCacheSweeper.class));
      appContext.getBeanFactory().registerSingleton(
         "xSessionService", new XSessionService());
      appContext.refresh();
      ConfigurationContext.getContext().setApplicationContext(appContext);
   }

   @AfterAll
   static void tearDownContext() {
      ConfigurationContext.getContext().setApplicationContext(null);

      if(appContext != null) {
         appContext.close();
      }
   }

   @Test
   void removeColumnMutatesSharedRuntimeAndBroadcasts() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetJoinService join = mock(WorksheetJoinService.class);
      WorksheetAgentBroadcastService broadcast = mock(WorksheetAgentBroadcastService.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      when(join.join("CODE", agent)).thenReturn(rws);

      WorksheetAgentEditService svc = new WorksheetAgentEditService(join, broadcast);
      svc.removeColumn("CODE", agent, "T", "a");

      ColumnSelection cs = t.getColumnSelection(false);
      assertNull(cs.getAttribute("a"), "column a removed");
      assertNotNull(cs.getAttribute("b"), "column b kept");
      verify(broadcast).broadcastRefresh(eq(rws), any(), eq(agent));
   }
}
