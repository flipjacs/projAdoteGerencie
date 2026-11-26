# Demo-local NGO Logos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show two openly licensed animal-protection organization logos in the Android demo while clearly identifying them as references without affiliation.

**Architecture:** Store the image assets in Android `drawable-nodpi` resources and render them from one small shared composable keyed by demo NGO ID. Keep source attribution and license notes in the demo artifact directory; do not add API fields or change server data.

**Tech Stack:** Kotlin, Jetpack Compose, Android drawable resources.

**Spec:** `docs/superpowers/specs/2026-11-26-logos-demo-local-design.md`

## Global Constraints

- Do not modify the Kotlin backend, database schema, or Patas Unidas Web.
- Do not add dependencies or generate images.
- Keep previously uploaded animal photos and their database records unchanged.
- Display a clear non-affiliation notice with each demo logo.

## Review Focus

- Unknown NGO ID: the shared component should render nothing, leaving existing UI unchanged.
- NGO IDs 1 and 2: each must select its own local logo asset.
- Attribution: both source pages, authors, and licenses must be recorded.
- Accessibility: logo image needs descriptive contentDescription and the disclaimer must remain readable.
- Demo-only mapping: no API contract or database migration should be introduced.

---

### Task 1: Add and credit the local logo assets

**Files:**
- Create: `frontend/app/src/main/res/drawable-nodpi/demo_ong_1_logo.jpg`
- Create: `frontend/app/src/main/res/drawable-nodpi/demo_ong_2_logo.png`
- Create: `artifacts/demo/logos/LEIA-ME.md`

- [x] Download the two selected openly licensed logo originals from their Wikimedia Commons file pages, retaining their identities and authorship.
- [x] Record the source page, author, license, and non-affiliation explanation in `artifacts/demo/logos/LEIA-ME.md`.
- [x] Inspect file type and dimensions; confirm both resources are valid images.

### Task 2: Add the shared demo logo component

**Files:**
- Create: `frontend/app/src/main/java/br/com/patasunidas/ui/DemoOngLogo.kt`

**Interface:** `@Composable fun DemoOngLogo(ongId: Int?, modifier: Modifier = Modifier)`

- [x] Map IDs 1 and 2 to the drawable resources; render nothing for any other ID.
- [x] Show a contained logo image with accessible description and a readable “Logo de referência; ONG demonstrativa sem vínculo com a organização.” notice.

### Task 3: Place logos in existing NGO identity surfaces

**Files:**
- Modify: `frontend/app/src/main/java/br/com/patasunidas/ui/InstitutionalScreens.kt`
- Modify: `frontend/app/src/main/java/br/com/patasunidas/animais/PublicAnimalScreens.kt`

- [x] Add the component to each platform NGO list card and selected NGO detail, passing the real response `id`.
- [x] Add the component to the public animal detail’s “ONG responsável” section, passing `animal.ongId`.
- [x] Preserve current layouts and all server behavior.
- [ ] Run `:app:assembleDebug` and inspect the changed files and resulting APK resources.
