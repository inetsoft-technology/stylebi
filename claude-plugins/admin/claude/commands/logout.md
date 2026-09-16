---
description: Log out of a StyleBI deployment (admin-chat). Usage - /stylebi-admin-chat:logout [stylebi-url]
---

1. If the user provided a URL in `$ARGUMENTS`, call the MCP tool `logout` with `{ deployment: "<the-url>" }`. Otherwise call `logout` with no arguments, which clears the currently active deployment.
2. Print the returned `summary` verbatim. If `loggedOut` is false, the summary says why — either there was no active login, or there were no stored credentials for the URL given. Do not describe it as a successful logout.
3. Do not claim anything the tool did not report. `logout` deletes the stored token; it does not end any StyleBI browser session, and it does not undo property changes that were already applied.
