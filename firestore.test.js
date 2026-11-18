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

function baseUserDoc(uid, username, fullName, now, overrides = {}) {
  return {
    uid,
    userId: uid,
    fullName,
    username,
    usernameLower: username.toLowerCase(),
    mobile: "+919876543210",
    isVerified: false,
    verified: false,
    profilePic: "",
    avatarData: "",
    avatarTint: "#E1306C",
    bio: "Capturing moments on Star Max",
    website: "",
    visibility: "public",
    postsCount: 0,
    followersCount: 0,
    followingCount: 0,
    online: true,
    themeMode: "system",
    isPrivate: false,
    notifyLikes: true,
    notifyComments: true,
    notifyFollowers: true,
    notifyMessages: true,
    dailyUsageSeconds: 0,
    usageDate: "2026-11-17",
    verificationStatus: "none",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user cannot read or write users, profiles, posts, or verification_requests", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("profiles").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("verification_requests").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(
      baseUserDoc(ALICE_UID, "alice", "Alice", new Date(Date.now() - 1000))
    )
  );
});

test("Users collection supports registration, real-time public search, and strict ownership on edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(baseUserDoc(ALICE_UID, "alice_star", "Alice Star", now))
  );

  await assertSucceeds(aliceDb.collection("profiles").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_star",
      usernameLower: "alice_star",
      fullName: "Alice Star",
      mobile: "+919876543210",
      bio: "Capturing moments on Star Max",
      website: "",
      avatarData: "https://lh3.googleusercontent.com/a/example",
      profilePic: "https://lh3.googleusercontent.com/a/example",
      avatarTint: "#E1306C",
      visibility: "public",
      postsCount: 0,
      followersCount: 0,
      followingCount: 0,
      isVerified: false,
      verified: false,
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Returning user update on users & profiles
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Updated bio",
      online: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      bio: "Updated bio",
      online: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob can query public users collection in real time (Explore Search)
  await assertSucceeds(
    bobDb.collection("users").where("visibility", "==", "public").get()
  );
  await assertSucceeds(
    bobDb.collection("profiles").where("visibility", "==", "public").get()
  );

  // Bob can increment followersCount when following Alice, but CANNOT hijack Alice's bio or username
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).update({
      followersCount: 1,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      bio: "Hacked",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    bobDb.collection("profiles").doc(ALICE_UID).update({
      bio: "Hacked",
      updatedAt: new Date(Date.now() - 300),
    })
  );
});

test("Posts, comments, stories (with filters & music), and reels enforce ownership and validation", async () => {
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

  // Reject @DocumentId 'id' leak
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

  await assertFails(bobDb.collection("posts").doc("post_1").delete());

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

  // Alice can create a 24h photo story with Vintage filter and trending music track
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      userId: ALICE_UID,
      authorUsername: "alice_star",
      mediaType: "photo",
      mediaData: "data:image/jpeg;base64,AAAA",
      textContent: "Golden night ✨",
      bgGradient: "sunset",
      filterName: "Vintage",
      songTitle: "Neon Skyline",
      songArtist: "Luna Wave",
      songUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
      visibility: "public",
      expiresAtMillis: Date.now() + 86400000,
      createdAt: now,
    })
  );

  // Invalid story filter name is rejected
  await assertFails(
    aliceDb.collection("stories").doc("story_bad_filter").set({
      userId: ALICE_UID,
      authorUsername: "alice_star",
      mediaType: "photo",
      mediaData: "data:image/jpeg;base64,AAAA",
      filterName: "UnknownFilter",
      visibility: "public",
      expiresAtMillis: Date.now() + 86400000,
      createdAt: now,
    })
  );

  await assertSucceeds(bobDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("posts").get());
});

test("User subcollections and DM conversations enforce strict participant isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 5000);

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

  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").get()
  );

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
    aliceDb.collection("users").doc(ALICE_UID).set(baseUserDoc(ALICE_UID, "alice_star", "Alice Star", now))
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

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      isPrivate: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

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
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("users").doc(ALICE_UID).collection("followRequests").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("followRequests").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("followRequests").doc(BOB_UID).delete()
  );

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

test("Blue Tick verification prevents self-approval and enforces pending verification_requests", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { phone_number: "+919876543210" }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 5000);

  // Even a phone-verified client CANNOT create users/profiles with isVerified: true or verified: true
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      baseUserDoc(ALICE_UID, "alice.star", "Alice Star", now, { isVerified: true })
    )
  );
  await assertFails(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice.star",
      usernameLower: "alice.star",
      fullName: "Alice Star",
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

  // Normal registration with isVerified: false succeeds across users, profiles, usernames, phoneRegistry
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      baseUserDoc(ALICE_UID, "alice.star", "Alice Star", now, { phoneE164: "+919876543210" })
    )
  );
  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice.star").set({
      userId: ALICE_UID,
      usernameLower: "alice.star",
      phoneE164: "+919876543210",
      passwordSalt: "random_salt_1234",
      passwordHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      verified: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("phoneRegistry").doc("9876543210").set({
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

  // User CANNOT self-approve Blue Tick via update on users
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      verificationStatus: "verified",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Submitting a pending verification request to verification_requests + setting verificationStatus: "pending" succeeds
  await assertSucceeds(
    aliceDb.collection("verification_requests").doc("req_alice_1").set({
      userId: ALICE_UID,
      username: "alice.star",
      fullName: "Alice Star",
      category: "Creator",
      knownAs: "AliceLens",
      idOrLink: "https://alicestar.studio",
      idProofData: "data:image/jpeg;base64,AAAA",
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      verificationStatus: "pending",
      verificationFullName: "Alice Star",
      verificationCategory: "Creator",
      verificationKnownAs: "AliceLens",
      verificationLink: "https://alicestar.studio",
      verificationSubmittedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Client CANNOT create an already approved request or update their request to approved
  await assertFails(
    aliceDb.collection("verification_requests").doc("req_alice_bad").set({
      userId: ALICE_UID,
      username: "alice.star",
      fullName: "Alice Star",
      category: "Creator",
      idOrLink: "https://alicestar.studio",
      status: "approved",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("verification_requests").doc("req_alice_1").update({
      status: "approved",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Alice can read her own verification request; Bob cannot read Alice's request
  await assertSucceeds(
    aliceDb.collection("verification_requests").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("verification_requests").doc("req_alice_1").get()
  );
  await assertFails(
    bobDb.collection("verification_requests").where("userId", "==", ALICE_UID).get()
  );
});
