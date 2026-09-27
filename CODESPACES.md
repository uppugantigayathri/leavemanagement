# Run the POC with a GitHub-provided URL

1. Open https://codespaces.new/uppugantigayathri/leavemanagement and sign in to GitHub.
2. Choose `main`, keep the smallest available machine, and create the Codespace. Check your GitHub allowance/billing before creating it; do not enable paid usage unless you want it.
3. Wait for the Java 17 container to build, Maven tests to finish, and CampusFlow to start. This can take several minutes on first creation.
4. In the **Ports** panel, find **8080 — CampusFlow POC** and use **Open in Browser**. If it is absent, add port `8080` manually.
5. To let other people open the POC, right-click port 8080, choose **Port Visibility → Public**, and copy its forwarded address. If your account/organization prohibits public ports, GitHub's policy must be resolved by the owner.

GitHub generates the actual address, in this format:

```text
https://YOUR-CODESPACE-NAME-8080.app.github.dev
```

That example is not a created endpoint. The real URL appears in your Codespace's Ports panel. GitHub Actions continues to build/test the repository; the running Codespace provides the web endpoint.

## Demo access

Use the role buttons on the login page, or these accounts:

| Role | PIN | Password |
|---|---|---|
| Student | 24093-CM-228 | Campus@2026 |
| Teacher | FAC-CM-101 | Campus@2026 |
| HOD | HOD-CM-001 | Campus@2026 |

This is a shared sample-data POC. People with the public URL can use the published demo accounts, including HOD, and change demo records. Do not enter real student data or secrets. SMS is disabled by the demo profile.

## Starting, stopping, and updating

The app starts automatically when the Codespace starts. If needed, run:

```bash
bash .devcontainer/start-poc.sh
```

To update after pulling new code:

```bash
bash .devcontainer/stop-poc.sh
git pull --ff-only
bash .devcontainer/start-poc.sh
```

Logs: `.poc/app.log`. H2 database: `.poc/data/campusflow.mv.db`. Both are excluded from Git. Workspace files survive stopping/restarting the same Codespace and normal container rebuilds, but deleting the Codespace deletes them. A different Codespace gets a separate sample database. Export/back up data you want to retain before deleting a Codespace.

The URL works only while the Codespace and app are running. Codespaces can stop automatically after inactivity; public visitors should not be relied on to prevent this. Stop the Codespace when not demonstrating it, and restart it from https://github.com/codespaces when needed. Check port visibility again after restarting. GitHub usage limits and retention rules still apply; this is not permanent hosting.

If you created a Codespace before this configuration was committed, run **Codespaces: Rebuild Container** from the Command Palette.

## Verification

The configuration and Bash syntax were checked locally. Actual container creation, public-port forwarding, and the generated URL must be verified inside your signed-in GitHub account. No public endpoint is claimed until that step completes.

Official documentation: https://docs.github.com/en/codespaces/developing-in-a-codespace/forwarding-ports-in-your-codespace
