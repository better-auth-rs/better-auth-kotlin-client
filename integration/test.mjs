import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { createFixture } from "./server.mjs";

const fixture = await createFixture();
try {
  const child = spawn("./gradlew", ["--no-daemon", ":serverIntegrationTest"], {
    cwd: fileURLToPath(new URL("..", import.meta.url)),
    stdio: "inherit",
    env: {
      ...process.env,
      BETTER_AUTH_TEST_URL: `${fixture.origin}/api/auth`,
      BETTER_AUTH_TEST_KEY: fixture.key,
    },
  });
  process.exitCode = await new Promise((resolve, reject) => {
    child.once("error", reject);
    child.once("exit", (code, signal) =>
      signal
        ? reject(new Error(`Gradle terminated: ${signal}`))
        : resolve(code),
    );
  });
} finally {
  await fixture.close();
}
