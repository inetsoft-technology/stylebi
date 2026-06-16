package inetsoft.web.wiz.worksheet;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import java.security.Principal;

final class TestPrincipals {
   static Principal user(String name, String org) {
      return new SRPrincipal(new IdentityID(name, org), new IdentityID[0], new String[0], org,
                             System.nanoTime());   // distinct secureID -> distinct session
   }
}
