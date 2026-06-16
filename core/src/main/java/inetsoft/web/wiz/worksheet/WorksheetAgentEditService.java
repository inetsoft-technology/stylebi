package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * End-to-end agent edit: join a paired runtime worksheet, apply one concrete
 * mutation, and broadcast a refresh to the browser. This proves the
 * join -> mutate -> broadcast loop; the full mutator set is a later plan.
 */
@Service
public class WorksheetAgentEditService {
   private final WorksheetJoinService join;
   private final WorksheetAgentBroadcastService broadcast;

   public WorksheetAgentEditService(WorksheetJoinService join,
                                    WorksheetAgentBroadcastService broadcast)
   {
      this.join = join;
      this.broadcast = broadcast;
   }

   /**
    * Remove a column from a named table assembly in the shared runtime
    * worksheet, then broadcast a refresh.
    */
   public void removeColumn(String code, Principal agent, String tableName, String column)
      throws PairingException
   {
      RuntimeWorksheet rws = join.join(code, agent);
      Worksheet ws = rws.getWorksheet();
      Assembly a = ws.getAssembly(tableName);

      if(!(a instanceof TableAssembly t)) {
         throw new IllegalArgumentException("No table assembly named " + tableName);
      }

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef ref = cs.getAttribute(column);

      if(ref != null) {
         cs.removeAttribute(ref);
         t.setColumnSelection(cs, false);
      }

      broadcast.broadcastRefresh(rws, t.getName(), agent);
   }
}
