const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
});

test("Authenticated user: cannot read another user's transactions", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("transactions").doc("tx_bob_1").set({
      userId: BOB_UID,
      type: "EXPENSE",
      amount: 120.0,
      category: "อาหาร",
      date: new Date(),
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("tx_bob_1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("transactions").get());
});

test("Authenticated user: can create and read own transactions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice_1");

  await assertSucceeds(
    txRef.set({
      userId: ALICE_UID,
      type: "EXPENSE",
      amount: 85.5,
      category: "อาหาร",
      note: "ข้าวมันไก่",
      date: new Date(),
      createdAt: new Date(),
    })
  );

  await assertSucceeds(txRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get());
});

test("Authenticated user: cannot create transaction with mismatched userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_spoof_1");

  await assertFails(
    txRef.set({
      userId: BOB_UID, // Spoofed!
      type: "EXPENSE",
      amount: 100.0,
      category: "อาหาร",
      date: new Date(),
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: can update own transaction and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice_2");

  const now = new Date();
  await assertSucceeds(
    txRef.set({
      userId: ALICE_UID,
      type: "INCOME",
      amount: 50000.0,
      category: "เงินเดือน",
      date: now,
      createdAt: now,
    })
  );

  await assertSucceeds(
    txRef.update({
      amount: 55000.0,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(txRef.delete());
});
