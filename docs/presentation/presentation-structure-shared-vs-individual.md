# Presentation structure — shared vs individual, simplified

Written 2026-08-08. Revises and simplifies
[`individual-use-case-template-analysis.md`](individual-use-case-template-analysis.md)
(which keeps the full requirement audit and source citations). This file answers one
question: **who must show what, and where.** No teammate content is filled in.

Sources as before: Individual Presentation Rubric (S-017), Group Presentation Rubric
incl. Scope Cap (S-016), Presentation Expectations (S-015), Piazza @280/@286/@339/@353,
and the posted timing table. "Required" below always means the course says so;
"recommended" means this analysis says so.

---

## 1. Shared vs individual — the one table

| Presentation element | Every person? | Once by team? | Required / recommended | Best location | Why |
|---|---|---|---|---|---|
| Team user story | No | **Yes** | Required (Group rubric) | Team opening | The rubric asks for one team story; give it a literal sentence on the opening slide, not just the Bob narrative implicitly |
| User story (own) | **Yes — say it** | — | Required (scope cap; @286) | Part A, first sentence | Each member needs a significant, distinct story; the grader must hear whose story each demo beat is |
| Before View (own) | **Yes — show it** | — | **Required** (Individual rubric 4/5 gate) | Part A, live UI | One of the three named Required Elements; live state counts as a View, screenshot backup in appendix |
| After View (own) | **Yes — show it** | — | **Required** (Individual rubric 4/5 gate) | Part A, live UI | Same |
| Runnable artifact | No | **Yes** | Required (Group rubric, 5 pts) | The demo itself (start from .JAR) | One artifact for the team; it is the vehicle of everyone's Part A |
| Use-case class diagram (own, full slice) | **Yes — show it** | — | **Required** (Individual rubric 4/5 gate) | Part B, one slide | Third named Required Element; per person, no exceptions |
| Interactor code (own) | **Yes — show it** | — | **Required** (Individual rubric 4/5 gate) | Part B, same slide as diagram | Second named Required Element; 6–12 lines, pointed at, not read |
| Clean Architecture explanation (project level: layers, dependency rule) | No | **Yes** | Required (Group rubric CA 15) | Part B opener, ~30 s, once | Stated once by whoever opens the technical section; the five slice diagrams then *instantiate* it — nobody re-lectures the rule |
| CA terminology in own segment | **Yes — say it** | — | Required (Individual rubric Terminology 5 pts) | Woven through A and B | Graded per person; name the roles while pointing ("the View delegates to the Controller…") |
| Meaningful design decision (own) | **Yes — say it** | — | Required (Scope Cap wording) | Part B, one line | "Meaningful design/implementation decisions" is the no-cap condition; one X-over-Y-because-Z sentence per person |
| SOLID principles | No | **Yes — exactly 2 in depth** | Required (Group rubric 15) | Part B: two members' cards expand | Team total is two examples / two principles; the two members whose slices show them best deliver them; the other three name **no** principles |
| Design pattern (introduced) | No | **Yes — at least 1** | Required (Group rubric, same 15) | Part B, inside one of those two cards | Team total; one clearly introduced pattern beats four name-drops |
| Testing / coverage | No | **Yes** | Required (Group rubric 10; @339) | Separate team section (Bianca) | Measured coverage shown once; optional one-line per-person evidence tag in Part B if time allows (recommended, cuttable) |
| Code quality (Checkstyle etc.) | No | **Yes** | Required (Group rubric 5; @275) | Separate team section (Emily) | Once |
| API usage (≤2 endpoints) | No | **Yes** | Required (Group rubric 5) | Separate team section (Raashid) | Once; a member whose demo beat hits a live service needs a rehearsed fallback (@280) — preparedness, not a slide |
| Code organization / packaging | No | **Yes** | Required (Group rubric 5) | Separate team section (Bianca) | Once |
| Accessibility | No | **Yes** | Required (Group rubric 5; S-023) | Separate team section (Alex) | Once |
| Edge / failure case (own) | Where natural | — | **Recommended** (not a rubric line) | Part A, ~10 s beat | Strengthens use-case explanation and pre-empts @353 questioning; skip if the story has no natural failure |
| Limitations / future work | No | **Yes** | Recommended | Team wrap-up | Once at the close; at most one honest line inside a Part B card if it teaches something (optional) |
| Q&A readiness on own slice | **Yes — prepare** | — | Required in effect (@353) | Not presented; back-pocket slides | The TA may question any member on their own or shared work |

**The distinction in one paragraph:** the Individual rubric grades each member
separately, and its evidence gate names exactly three artifacts — **before/after Views,
Interactor code, use-case class diagram**. Those, plus the member's own story sentence,
design decision and correct terminology, are *individual*: all five people, no
exceptions. Everything else — SOLID, pattern, testing, quality, API, organization,
accessibility, the CA lecture itself, the artifact, limitations — is a *team* score
that should be presented exactly once by one owner. Optional supporting material
(failure beats, evidence tags, per-person limitation lines) is garnish: add when time
allows, cut first.

---

## 2. The template, restated plainly

### Part A — story-driven demo (when Bob reaches your feature, ~60 s)

1. **Say your user story** — one sentence, "As Bob, I want …, so that …". *(mandatory per person)*
2. **Point at the Before View** — name what Bob sees now. *(mandatory per person — rubric artifact 1a)*
3. **Do the action** — one gesture, naming the layer: "the View hands this to my Controller; no logic lives here." *(mandatory per person — terminology)*
4. **Point at the After View** — what changed, what was preserved. *(mandatory per person — rubric artifact 1b)*
5. **Failure beat** — one rehearsed edge case, only if your story has a natural one. *(recommended)*
6. **Hand Bob off** — one sentence into the next member's beat. *(mandatory for flow)*

Nothing else. No diagrams, no code, no principle names, no numbers.

### Part B — technical explanation (after the demo, one slide each, ~60 s)

0. *(Section opener only, once, ~30 s: the project CA diagram and the dependency rule —
   team-level, spoken by the first presenter of this section.)*
1. **Your use-case class diagram** — the full slice, highlighted on the shared layer
   skeleton (see §4). *(mandatory per person — rubric artifact 3)*
2. **Your Interactor excerpt** — 6–12 lines on the same slide; point at the boundary
   in, the entity work, the boundary out. *(mandatory per person — rubric artifact 2)*
3. **Your design decision** — X over Y because Z, one line. *(mandatory per person — scope cap)*
4. **SOLID / pattern expansion** — only the two designated members, ~45 s extra: one
   principle in depth each, one of them also naming the introduced pattern. *(team-level, delivered through their own slice)*
5. **Evidence tag** — one sentence of proof (a pinning test, a measured number).
   *(recommended, first thing to cut)*
6. **Limitation** — one honest line. *(optional)*

Separate team sections (API, organization, quality, testing, accessibility, wrap-up)
stay exactly as the posted timing table has them — one owner each, untouched by this
template.

---

## 3. Where should the technical explanation happen? A, B, C — reassessed

| Criterion | **A** — full demo, then five tech blocks | **B** — pause the demo at each feature | **C** — Before/After in demo, tech revisit after |
|---|---|---|---|
| Grading clarity | Good: each member contiguous in the tech half; grader ticks Views during demo, rest later | Best on paper: everything per member contiguous | Good: each member appears exactly twice, both times labelled |
| Story flow | Demo pristine | **Destroyed** — five interruptions in five minutes; the narrative the TA liked stops being one | Demo pristine |
| Audience engagement | Risk concentrated at the back: five UML slides in a row after the fun part | Whiplash: app → UML → app × 5 | Same parade risk as A, but the CA/SOLID slots break it into 3 + 2 |
| Five-UMLs-in-a-row awkwardness | **High** — this is exactly the "here's my diagram, next" failure | Low per moment, high overall confusion | Present but fixable (§4) |
| Screen switching | One switch (app → deck) | **~10 switches**, live app and deck fighting; highest fumble probability | One switch |
| Time risk | Medium: tech half balloons unnoticed once the demo is done | **Highest**: every pause costs re-orientation twice; demo will blow through 5:00 | Lowest: each half lives inside a fixed timing-table slot |
| Individual evidence unmistakable? | Yes | Yes, if the TA survives the switching | Yes — and each rubric artifact has a *predictable* home (Views always in demo, diagram/code always in tech block) |

**Option B is eliminated**, not merely disfavoured: it spends the team's scarcest
resource (demo minutes) on re-orientation, maximizes technical risk at the podium, and
sacrifices the one thing the TA has already praised. Options A and C are close — C wins
because the posted timing table *already* provides two separated technical slots (CA
3:00, SOLID 3:00) that a five-card block can occupy without moving anything, and
because splitting the five cards 3 + 2 across those slots is what breaks the parade.

---

## 4. Final recommendation — hybrid C, with the parade explicitly designed out

**Flow:** Opening (team story + five story sentences as a roadmap) → API → **demo: five
Part A beats as one continuous Bob story** → **technical section: CA opener once, then
five Part B cards** → organization → quality → testing → accessibility → wrap-up.

Four devices keep the technical section from becoming "my UML, next, their UML":

1. **One skeleton, five highlights.** The section opener puts up the project CA diagram
   *as a template*: View → Controller → Input Boundary → Interactor → Entities +
   Data-Access Interface → Output Boundary → Presenter → ViewModel. Every member's
   slide is that same skeleton with **their** classes filled in and their slice
   highlighted. Nobody re-explains the shape; the shape is the connective tissue.
2. **Spend the seconds on the difference.** Each card leads with the one thing that is
   *distinctive* about that slice — an extra port, a background task, a second
   presenter, a hard-constraint validator — not with the parts every slice shares.
   "Same shape; mine differs here, because…" is a sentence, and then the design
   decision. Five differences are interesting; five identical walkthroughs are not.
3. **Replay the story order.** The five cards run in the same order Bob met the
   features. The technical section becomes "the same journey, seen at the architecture
   level," not five unrelated diagrams — and the grader maps demo beat to tech card
   with zero effort.
4. **Let the two SOLID carriers change the register.** Cards 1–3 are tight (~60 s);
   cards 4–5 expand (~105 s) to deliver the two in-depth principles and the introduced
   pattern through their own slices. The section ends on analysis, not on a fifth
   repetition.

**Why this is the strongest:** it preserves the praised narrative untouched, needs one
screen switch, fits the posted table without moving a slot, gives every Individual-
rubric artifact a predictable location, and converts the five-diagram liability into a
structured reprise of the story.

---

## 5. Time per person

| Piece | Time |
|---|---|
| Part A (demo beat) | ~60 s each → 5:00 total, fills the demo slot |
| Part B (cards 1–3, non-SOLID members) | ~60 s each |
| Part B (cards 4–5, SOLID/pattern carriers) | ~105 s each |
| CA opener (once) | ~30 s |
| Technical section total | ~30 s + 3×60 s + 2×105 s ≈ **6:00**, spanning the CA (3:00) + SOLID (3:00) slots exactly |
| **Per person, both parts** | **~2:00** (non-carrier) / **~2:45** (SOLID carrier) |

Zero slack against the 20:00 table — rehearse to 18:30, and cut evidence tags (B5)
first, failure beats (A5) second. The 15-vs-20-minute rubric conflict from the
analysis doc still stands as the first TA question.
