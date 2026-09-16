---
description: Show the current StyleBI login for admin-chat.
---

1. Call the MCP tool `status`. It probes the admin API, so its answer is about now, not about what is stored.
2. Print the returned `summary`.
3. If it reports not logged in, tell the user to run `/stylebi-admin-chat:login <stylebi-url>` and stop.
4. If `adminReachable` is false, admin calls will not work. Relay the summary verbatim and stop — do not attempt any admin tool until it is resolved. The remedy depends on `reachability`: `unauthorized` and `misrouted` need a fresh login (`login_start` takes only the StyleBI URL — there is no other parameter to get wrong); `forbidden` needs a Site Administrator account; `unreachable` needs the server started or the URL corrected; `server-error` means the server is up and the login is fine, so it needs the server's own logs — none of the last three is a login problem.
