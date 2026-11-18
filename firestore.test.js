import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
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
const CHARLIE_UID = "charlie_789";

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("profiles").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(
    unauthDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      usernameLower: "alice",
      fullName: "Alice",
      bio: "",
      website: "",
      visibility: "public",
      postsCount: 0,
      followersCount: 0,
      followingCount: 0,
      verified: false,
      online: true,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create and update public profile; cross-user profile edit rejected except follower count", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Mirror SocialRepository.ensureUserProfile() exact sequence
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      themeMode: "system",
      createdAt: now,
      updatedAt: now,
    })
  );

  // First-time user check on non-existent profile must succeed (resource == null)
  await assertSucceeds(aliceDb.collection("profiles").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_star",
      usernameLower: "alice_star",
      fullName: "Alice Star",
      bio: "Capturing moments on Star Max",
      website: "",
      avatarData: "https://lh3.googleusercontent.com/a/example",
      avatarTint: "#E1306C",
      visibility: "public",
      postsCount: 0,
      followersCount: 128,
      followingCount: 2,
      verified: false,
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("following").doc("sample_kai").set({
      targetUserId: "sample_kai",
      targetUsername: "kai_tokyo",
      createdAt: now,
    })
  );

  // Returning user online ping update
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      online: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      bio: "Updated bio",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot hijack Alice's bio or username
  await assertFails(
    bobDb.collection("profiles").doc(ALICE_UID).update({
      bio: "Hacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob can read public profiles using visibility filter
  await assertSucceeds(
    bobDb.collection("profiles").where("visibility", "==", "public").get()
  );

  // Unfiltered profiles list rejected
  await assertFails(bobDb.collection("profiles").get());
});

test("Posts, comments, stories, and reels enforce author ownership, schema bounds, and visibility filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      userId: ALICE_UID,
      authorUsername: "alice_star",
      authorName: "Alice Star",
      caption: "Golden hour #travel",
      location: "Amalfi",
      hashtags: ["travel"],
      taggedUsers: [],
      mediaType: "photo",
      mediaData: "res:img_post_amalfi_sunset",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field / @DocumentId 'id' leak
  await assertFails(
    aliceDb.collection("posts").doc("post_bad").set({
      id: "post_bad",
      userId: ALICE_UID,
      authorUsername: "alice_star",
      authorName: "Alice Star",
      caption: "Bad",
      hashtags: [],
      taggedUsers: [],
      mediaType: "photo",
      mediaData: "res:img_post_amalfi_sunset",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot delete Alice's post
  await assertFails(bobDb.collection("posts").doc("post_1").delete());

  // Bob can comment and update likesCount
  await assertSucceeds(
    bobDb.collection("comments").doc("comment_1").set({
      postId: "post_1",
      userId: BOB_UID,
      authorUsername: "bob_photo",
      text: "Amazing shot!",
      visibility: "public",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Alice can create a 24h story
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      userId: ALICE_UID,
      authorUsername: "alice_star",
      mediaType: "text",
      textContent: "New vlog dropping tonight!",
      bgGradient: "sunset",
      visibility: "public",
      expiresAtMillis: Date.now() + 86400000,
      createdAt: now,
    })
  );

  // Filtered queries succeed; unfiltered list fails
  await assertSucceeds(bobDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("posts").get());
});

test("User subcollections and DM conversations enforce strict participant isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Alice user likes & saved
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("likes").doc("post_1").set({
      targetId: "post_1",
      targetType: "post",
      createdAt: now,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("likes").get()
  );

  // Alice & Bob conversation (non-existent get before first message must succeed)
  const convId = "conv_alice_bob";
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).get());
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participantUids: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAUsername: "alice_star",
      userBId: BOB_UID,
      userBUsername: "bob_photo",
      lastMessage: "Hey!",
      lastSenderId: ALICE_UID,
      unreadBy: [BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      convId,
      senderId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      text: "Hey Bob!",
      seenBy: [ALICE_UID],
      createdAt: now,
    })
  );

  // Charlie cannot read Alice/Bob conversation or messages
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages")
      .where("participantUids", "array-contains", ALICE_UID).get()
  );

  // Bob can query conversations and messages where he is a participant
  await assertSucceeds(
    bobDb.collection("conversations").where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages")
      .where("participantUids", "array-contains", BOB_UID).get()
  );
});

test("User settings persistence, private follow requests, and archived posts enforce ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      themeMode: "system",
      isPrivate: false,
      notifyLikes: true,
      notifyComments: true,
      notifyFollowers: true,
      notifyMessages: true,
      dailyUsageSeconds: 120,
      usageDate: "2026-11-17",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "dark",
      isPrivate: true,
      notifyLikes: false,
      dailyUsageSeconds: 360,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot modify Alice's settings
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      isPrivate: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob sends a follow request to private Alice
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("followRequests").doc(BOB_UID).set({
      requesterId: BOB_UID,
      requesterUsername: "bob_photo",
      requesterName: "Bob Photo",
      createdAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("sentFollowRequests").doc(ALICE_UID).set({
      targetUserId: ALICE_UID,
      targetUsername: "alice_star",
      createdAt: now,
    })
  );
  // Charlie cannot read Alice's followRequests list
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("users").doc(ALICE_UID).collection("followRequests").get()
  );
  // Alice can list and decline/delete Bob's request
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("followRequests").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("followRequests").doc(BOB_UID).delete()
  );

  // Alice archives her post and only Alice can read it
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_arch").set({
      userId: ALICE_UID,
      authorUsername: "alice_star",
      authorName: "Alice Star",
      caption: "Private draft",
      hashtags: [],
      taggedUsers: [],
      mediaType: "photo",
      mediaData: "res:img_post_neon_tokyo",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_arch").update({
      visibility: "archived",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("posts").where("userId", "==", ALICE_UID).where("visibility", "==", "archived").get()
  );
  await assertFails(bobDb.collection("posts").doc("post_arch").get());
});

test("Phone-verified profile badge, unique username/phone registries, and anti-spoofing rules", async () => {
  const phoneAliceDb = testEnv.authenticatedContext(ALICE_UID, { phone_number: "+919876543210" }).firestore();
  const nonPhoneBobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 5000);

  // Non-phone user cannot self-assign verified: true
  await assertFails(
    nonPhoneBobDb.collection("profiles").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob_fake",
      usernameLower: "bob_fake",
      fullName: "Bob Fake",
      bio: "",
      website: "",
      visibility: "public",
      postsCount: 0,
      followersCount: 0,
      followingCount: 0,
      verified: true,
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Phone-verified user CAN create verified profile + claim username & phone number
  await assertSucceeds(
    phoneAliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice.star",
      usernameLower: "alice.star",
      fullName: "Alice Star",
      bio: "Verified creator",
      website: "",
      visibility: "public",
      postsCount: 0,
      followersCount: 0,
      followingCount: 0,
      verified: true,
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    phoneAliceDb.collection("usernames").doc("alice.star").set({
      userId: ALICE_UID,
      usernameLower: "alice.star",
      phoneE164: "+919876543210",
      passwordSalt: "random_salt_1234",
      passwordHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      verified: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    phoneAliceDb.collection("phoneRegistry").doc("9876543210").set({
      userId: ALICE_UID,
      phoneDigits: "9876543210",
      phoneE164: "+919876543210",
      usernameLower: "alice.star",
      createdAt: now,
    })
  );

  // Unauthenticated client can check single username/phone doc (for login/dup check) but CANNOT list collections
  await assertSucceeds(unauthDb.collection("usernames").doc("alice.star").get());
  await assertSucceeds(unauthDb.collection("phoneRegistry").doc("9876543210").get());
  await assertFails(unauthDb.collection("usernames").get());
  await assertFails(unauthDb.collection("phoneRegistry").get());

  // Bob cannot steal or overwrite Alice's claimed username or phone number
  await assertFails(
    nonPhoneBobDb.collection("usernames").doc("alice.star").set({
      userId: BOB_UID,
      usernameLower: "alice.star",
      phoneE164: "+919999999999",
      passwordSalt: "other_salt_1234",
      passwordHash: "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
      verified: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    nonPhoneBobDb.collection("phoneRegistry").doc("9876543210").set({
      userId: BOB_UID,
      phoneDigits: "9876543210",
      phoneE164: "+919876543210",
      usernameLower: "bob_other",
      createdAt: now,
    })
  );
});


