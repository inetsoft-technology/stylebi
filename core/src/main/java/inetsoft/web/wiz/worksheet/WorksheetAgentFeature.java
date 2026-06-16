package inetsoft.web.wiz.worksheet;

import inetsoft.sree.SreeEnv;
import org.springframework.stereotype.Component;

/** Off-by-default gate for the worksheet agent pairing capability. */
@Component
public class WorksheetAgentFeature {
   public static final String FLAG = "worksheet.agent.pairing.enabled";

   public boolean isEnabled() {
      return SreeEnv.getBooleanProperty(FLAG);   // false unless an admin sets it true
   }
}
