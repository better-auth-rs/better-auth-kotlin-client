import { randomBytes } from "node:crypto";
import { createServer } from "node:http";
import { DatabaseSync } from "node:sqlite";
import { expo } from "@better-auth/expo";
import { passkey } from "@better-auth/passkey";
import { betterAuth } from "better-auth";
import { getMigrations } from "better-auth/db/migration";
import { toNodeHandler } from "better-auth/node";
import {
  admin,
  anonymous,
  bearer,
  emailOTP,
  jwt,
  magicLink,
  multiSession,
  oneTimeToken,
  organization,
  phoneNumber,
  twoFactor,
} from "better-auth/plugins";
import { defaultAc } from "better-auth/plugins/organization/access";

export async function createFixture() {
  const messages = new Map();
  const key = randomBytes(24).toString("hex");
  const database = new DatabaseSync(":memory:");
  let handler;
  const server = createServer((request, response) => {
    const url = new URL(request.url, "http://127.0.0.1");
    if (url.pathname === "/test/mailbox") {
      if (request.headers["x-test-key"] !== key) {
        response.writeHead(403).end();
        return;
      }
      const message = messages.get(url.searchParams.get("key"));
      response.writeHead(message ? 200 : 404, {
        "content-type": "application/json",
      });
      response.end(JSON.stringify(message ?? null));
      return;
    }
    handler(request, response).catch((error) => {
      console.error(error);
      if (!response.headersSent) response.writeHead(500);
      response.end();
    });
  });
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const origin = `http://127.0.0.1:${server.address().port}`;
  const save = (type, recipient, data) =>
    messages.set(`${type}:${recipient}`, data);
  try {
    const options = {
      baseURL: origin,
      secret: randomBytes(32).toString("hex"),
      database,
      trustedOrigins: [origin, "testapp://"],
      rateLimit: { enabled: false },
      emailAndPassword: {
        enabled: true,
        sendResetPassword: async (data) => {
          save("reset", data.user.email, data);
        },
      },
      emailVerification: {
        sendVerificationEmail: async (data) => {
          save("verify", data.user.email, data);
        },
      },
      user: { deleteUser: { enabled: true }, changeEmail: { enabled: true } },
      plugins: [
        bearer({ requireSignature: true }),
        admin(),
        anonymous(),
        emailOTP({
          sendVerificationOTP: async (data) => {
            save(data.type, data.email, data);
          },
        }),
        jwt(),
        magicLink({
          sendMagicLink: async (data) => {
            save("magic", data.email, data);
          },
        }),
        multiSession(),
        oneTimeToken(),
        organization({
          ac: defaultAc,
          teams: { enabled: true },
          dynamicAccessControl: { enabled: true },
        }),
        phoneNumber({
          sendOTP: async (data) => {
            save("phone", data.phoneNumber, data);
          },
        }),
        twoFactor({
          otpOptions: {
            sendOTP: async (data) => {
              save("2fa", data.user.email, data);
            },
          },
        }),
        passkey({ rpID: "localhost", rpName: "SDK integration", origin }),
        expo(),
      ],
    };
    await (await getMigrations(options)).runMigrations();
    const auth = betterAuth(options);
    const context = await auth.$context;
    const seed = await auth.api.signUpEmail({
      body: {
        name: "Admin",
        email: "admin@example.com",
        password: "integration-admin-password",
      },
    });
    await context.internalAdapter.updateUser(seed.user.id, {
      role: "admin",
      emailVerified: true,
    });
    handler = toNodeHandler(auth);
    return {
      origin,
      key,
      close: async () => {
        await new Promise((resolve, reject) =>
          server.close((error) => (error ? reject(error) : resolve())),
        );
        database.close();
      },
    };
  } catch (error) {
    server.close();
    database.close();
    throw error;
  }
}
