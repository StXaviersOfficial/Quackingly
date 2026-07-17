# REQUIRED PROMPT — Execute EVERY request

This file documents the mandatory audit process for the Quackingly mod.
**Every time a change is requested, the following process MUST be followed:**

## Step 1: Pre-Change Full Audit
1. Read EVERY file in `src/main/java/com/quackcraft/quackingly/` (all subdirectories)
2. Read EVERY file in `src/main/resources/`
3. Read `build.gradle`, `gradle.properties`, `fabric.mod.json`
4. Note any existing bugs, stale references, or API mismatches
5. Plan all changes before writing any code

## Step 2: Make Changes
- Fix all bugs found in Step 1
- Implement requested features
- Improve UI where possible
- Ensure all code compiles against Fabric 1.21.1 + Yarn mappings

## Step 3: Post-Change Full Audit (MANDATORY)
1. Re-read EVERY file in the project
2. Verify no stale references (old field names, removed imports, etc.)
3. Verify all new code is consistent with existing patterns
4. Check for compile errors:
   - Missing imports
   - Wrong API signatures (Yarn 1.21.1 mapping names)
   - Type mismatches
   - Unused imports
5. If ANY error is found → fix it, then RE-READ the entire project again
6. If NO errors are found → stop and ship

## Step 4: Build Verification
- Commit and push to GitHub
- Wait for GitHub Actions build to complete
- If build fails → read the error, fix, push again
- If build succeeds → download the .jar, verify fabric.mod.json is correct

## Common Pitfalls to Check
- `PayloadTypeRegistry.playC2S().register()` MUST be called before `ServerPlayNetworking.registerGlobalReceiver()`
- `carpet`, `voicechat`, `modmenu` must be in `suggests` (not `depends`) in fabric.mod.json
- Yarn 1.21.1: `getWorld()` not `.world`, `isTouchingWater()` not `isInWater()`
- Carpet `EntityPlayerMPFake.createFake()` returns void in 1.4.147 — look up player by name after spawn
- `CustomPayload.Id<>` + `getId()` for 1.21.1 payload pattern
- `PacketCodec.tuple()` for payloads with fields, `PacketCodec.unit()` for empty payloads
- Mixin extending Screen needs a dummy constructor `protected X(Text title) { super(title); }`
