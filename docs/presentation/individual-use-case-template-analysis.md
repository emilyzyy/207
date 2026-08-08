# Individual use-case presentation template — Phase 1 analysis

Written 2026-08-08. **Determines the template only; fills it in for nobody.**

Evidence base: the course audit at `~/Documents/207-course-audit-2026-08-04/`, whose
source ledger records the Quercus pages read in full and re-verified live on 2026-08-05
(source IDs S-011 – S-025), the Piazza review (34 threads opened, staff posts cited by
number), and the course lecture PDFs. Where this document says **course requirement** it
cites one of those sources; where it says **recommendation** it is this analysis's
judgment and is labelled as such. The timing table supplied by Emily on 2026-08-08 is
treated as newly posted instructor guidance; it was not in the audit snapshot, so its
authority is "user-reported Quercus guidance" and each use below says so.

---

## 1. Requirement audit

Every requirement relevant to one member's own use-case segment, with source and scope.

| # | Requirement | Exact source | Required or recommended | Applies |
|---|---|---|---|---|
| R1 | Every team member has one user story; the team has one team story | Group rubric scope cap (S-016); Piazza @286; restated in the new timing table | **Required** | Once per person |
| R2 | Each member's story must be *significant, distinct, non-trivial, with meaningful design/implementation decisions* for the team to escape the scope cap | Project Scope Cap inside the Group rubric (S-016, live-verified) | **Required** for no-cap eligibility | Once per person — the "meaningful decision" must be visible per member |
| R3 | **Before and after Views** of the member's use case | Individual rubric, Required Elements 4/5 threshold (S-017, live-verified) | **Required** (4/5 gate) | Once per person |
| R4 | **Use Case Interactor code** for the member's use case | Individual rubric, same threshold (S-017) | **Required** (4/5 gate) | Once per person |
| R5 | **Class diagram of the full Use Case** — every real class/interface in the slice, not the whole project | Individual rubric, same threshold (S-017) | **Required** (4/5 gate) | Once per person |
| R6 | 5/5 Required Elements needs the same three at *exceptional* quality | Individual rubric (S-017) | Threshold, not extra content | Once per person |
| R7 | Precise course terminology (View, Controller, Input Boundary, Interactor, Output Boundary, Presenter, ViewModel, Entity, Data Access Interface; dependency rule vs runtime flow) | Individual rubric Terminology 5 pts (S-017); Piazza @230/@273 | **Required** (graded per person) | Per person, woven through their segment |
| R8 | Clear use-case explanation leaving "no explanatory doubt", understandable to a second grader unfamiliar with the project | Individual rubric Use-case Explanation 5 pts (S-017); Expectations page (S-015) | **Required** | Once per person |
| R9 | Verbal delivery: engaging, not read from notes — reading notes caps this criterion at 3/5 | Individual rubric Verbal Presentation 5 pts (S-017) | **Required** | Per person |
| R10 | Team must *affirmatively show* rubric evidence; graders do not go digging | Expectations page (S-015) | **Required** | Whole presentation |
| R11 | Clean Architecture adherence explained; UML sufficient, no code walkthrough needed; "every member can walk through their user stories" | Group rubric CA 15 pts (S-016); new timing table's CA row | **Required** (team category, per-person walkthrough permitted) | Team category; the per-person diagram (R5) doubles as its evidence |
| R12 | SOLID: **two specific examples, at least two principles in depth**; **at least one design pattern the team introduced** | Group rubric SOLID/patterns 15 pts (S-016); new timing table wording | **Required** — note the counts are team totals | **Team-level: two examples total, not ten** |
| R13 | Demonstration of core functionality; live or recorded, with a working artifact available for questions; may start from a .JAR | Group rubric functionality 15 + runnable 5 (S-016); Expectations (S-015); new timing table | **Required** | Once for the team (the demo itself) |
| R14 | API usage: up to two endpoints, briefly | Group rubric API 5 pts (S-016); new timing table | **Required** | Once for the team |
| R15 | Code organization / packaging | Group rubric organization 5 pts (S-016); Piazza @348 | **Required** | Once for the team |
| R16 | Code quality process (Checkstyle etc.) | Group rubric quality 5 pts (S-016); Piazza @275 | **Required** | Once for the team |
| R17 | Testing: measured coverage evidence (5/5 = >90% interactor, >70% overall) | Group rubric testing 10 pts (S-016); Piazza @339 | **Required** | Once for the team |
| R18 | Accessibility: 1–2 Universal Design principles, target audience, excluded group | Group rubric accessibility 5 pts (S-016); `accessibility-report.md` requirement (S-023); Piazza @358 | **Required** | Once for the team |
| R19 | A TA may question any individual about a shared feature or their contribution | Piazza @353 (S-047) | Not a slide requirement — a preparedness requirement | Per person, Q&A |
| R20 | Live API should work at presentation time; mock/fallback/video is the contingency | Piazza @280 (S-059) | **Required** (functionality claims) | Team demo; members whose story touches a live service need a rehearsed fallback |
| R21 | Diagrams "where natural"; rehearse; stay in time | Expectations (S-015); Piazza @361 | Required in spirit | Whole presentation |
| R22 | Edge/failure cases | **Not named by either rubric as a distinct item.** Supported indirectly: use-case explanation quality (S-017), testing descriptors (S-016), and the audit's presentation plan treats a rehearsed failure case as strong evidence | **Recommendation**, not a rubric line | Per person where the story has a natural failure |
| R23 | Limitations / future work | Not a rubric category; Expectations and @361 favour honest, well-chosen detail; the timing table's wrap-up row lists "future work" once | **Recommendation** | Once for the team, one line per person at most |

**The single most consequential fact:** the Individual rubric is a *separate 20-point
grade for each of the five members*, and its 4/5 Required-Elements gate names exactly
three artifacts — before/after Views, Interactor code, use-case class diagram. Those
three are non-negotiable **per person**. Everything else in the technical categories is
a *team* score that should appear once.

---

## 2. Team-level vs individual-use-case content

### Team-level — present once, by one owner

| Content | Rubric line | Timing-table owner (user-reported) |
|---|---|---|
| Opening, project specification, team user story | Specification 10 | shared opening |
| API summary, ≤2 endpoints | API 5 | Raashid |
| The demo itself / runnable artifact (.JAR start) | Functionality 15 + Runnable 5 | whole team, story-driven |
| Overall CA picture (one project-level diagram, the dependency rule stated once) | CA 15 (partly) | whoever opens the CA block |
| Two SOLID examples in depth + one introduced pattern | SOLID/patterns 15 | the 1–2 members whose slices give the best examples |
| Code organization / packaging | Organization 5 | Bianca |
| Code quality process | Quality 5 | Emily |
| Coverage evidence | Testing 10 | Bianca |
| Accessibility report summary | Accessibility 5 | Alex |
| Wrap-up, README pointer, future work | Presentation 10 (pacing) | shared close |

### Individual — each member, for their own use case

1. User story, stated in one sentence (R1, R2).
2. Before View → action → After View, ideally as their demo moment (R3).
3. Use-case class diagram — full slice (R5).
4. Interactor code excerpt (R4).
5. One meaningful design decision, with the rejected alternative (R2 — this is what
   "meaningful design/implementation decisions" means in the scope cap).
6. Correct terminology throughout (R7).
7. Q&A readiness on their slice (R19) — prepared, not presented.

### Ambiguous — flagged

- **Does the individual grade require per-person testing evidence?** The Individual
  rubric does not name tests. The Group testing score is team-wide. *Recommendation:*
  one sentence per person ("the Interactor is at N% with fake gateways") only if it
  fits; the real coverage slide is Bianca's. Ask the TA only if time forces a cut.
- **Must the three Required Elements appear inside the 20 minutes, or is having them
  ready enough?** Expectations says evidence must be shown affirmatively (S-015), so
  assume **inside the 20 minutes**, compressed.
- **15 vs 20 minutes.** The live Group rubric descriptor says "within a 15-minute
  limit" while Expectations and the new timing table say 20. Already flagged in the
  audit (S-085). Ask Sajad; rehearse so the deck survives a 15-minute cut.
- **Does a live demo count as the "before/after Views"?** Rubric says "Views", not
  "screenshots". *Recommendation:* live demo as primary, screenshot backup slide per
  person in an appendix — this also covers the @280 outage contingency.

---

## 3. The reusable individual-use-case template

Presentation sequence for ONE member. Two physical placements: the **demo moment**
(inside the 5-minute story demo) and the **technical debrief card** (inside the CA /
SOLID block). Together they cover the whole Individual rubric.

### Part A — demo moment (~55–70 s, inside the team demo)

| # | Section | Content | Why it earns marks | Visual/evidence | Status | Time |
|---|---|---|---|---|---|---|
| A1 | User story | One spoken sentence: "As Bob, …, so that …" | R1; scope cap distinctness; anchors the grader's rubric sheet | Story beat on screen; no paragraph | **Mandatory** | 10 s |
| A2 | Before View | The state Bob is in before the feature acts — pointed at, named | R3 first half | Live UI (screenshot fallback in appendix) | **Mandatory** | 10–15 s |
| A3 | The action | One user gesture; say which layer receives it: "the View delegates to the Controller; no logic lives here" | R7 terminology in context; use-case explanation | Live click | **Mandatory** | 10 s |
| A4 | After View | The visible consequence; name what changed and what was preserved | R3 second half; functionality | Live UI | **Mandatory** | 15–20 s |
| A5 | Edge/failure beat | One rehearsed failure or constraint honoured, *if the story has a natural one* | R22 (recommendation); pre-empts @353 questions | Live or one screenshot | Recommended | 10 s |
| A6 | Hand-off | One sentence handing Bob to the next member's story | Presentation 10 (flow) | — | **Mandatory** | 5 s |

**Not here:** diagrams, code, SOLID names, coverage numbers. Nothing kills a story-demo
like a UML slide mid-click.

### Part B — technical debrief card (~60–75 s, in the CA block; ONE slide per person)

| # | Section | Content | Why it earns marks | Visual/evidence | Status | Time |
|---|---|---|---|---|---|---|
| B1 | Use-case class diagram | The full slice: View → Controller → Input Boundary/Data → Interactor → Entities + Data-Access interfaces → Output Boundary → Presenter → ViewModel; adapters outside | R5; carries the team CA score with five concrete instances of the dependency rule | One diagram, pre-simplified to fit a slide legibly | **Mandatory** | 25–30 s |
| B2 | Interactor excerpt | 6–12 lines beside the diagram: load through the repository interface, decide, present through the Output Boundary. Point, don't read | R4 | Code panel on the same slide | **Mandatory** | 15–20 s |
| B3 | One design decision | "I chose X over Y because Z" — one sentence each for choice, rejected alternative, consequence | R2 scope cap; use-case explanation depth | One line on the slide | **Mandatory** | 15 s |
| B4 | Evidence tag | One sentence of the member's strongest proof (a test that pins the behaviour, a boundary honoured, a measured number) | Feeds testing/functionality credibility without repeating Bianca's slide | One line | Recommended | 10 s |
| B5 | Limitation | One honest sentence, only if it teaches something | R23 (recommendation) | One line | Optional | 0–10 s |

**Not here:** re-demoing, project-level architecture repetition (stated once by the
block opener), SOLID lectures (that is the shared block), reading the diagram node by
node.

### SOLID / pattern block — NOT per person

Course requirement (R12): two examples, two principles in depth, one introduced
pattern — **team totals**. The 1–2 members whose slices give the cleanest examples
present them *using their own use case as the material* (e.g. one member's DIP with an
inward port + fake; another's SRP across Controller/Interactor/Presenter; Observer or
Adapter as the introduced pattern). Everyone else does **not** name principles; their
debrief card already demonstrates them silently.

---

## 4. What must be individualized — category by category

| Category | Per person? | Basis |
|---|---|---|
| Clean Architecture | **Yes** — each member's own use-case diagram + interactor (R4/R5). The *project-level* CA statement happens once. | Individual rubric names these artifacts per person; group CA row says members may walk their own stories |
| SOLID | **No** — two team examples in depth, delivered by whoever's slice fits best | Group rubric counts are team totals (S-016) |
| Design patterns | **No** — at least one introduced pattern, once | Same |
| Testing | **No** — one coverage slide (Bianca). Optional one-line per-person evidence tag (B4) | Testing is a group category; Individual rubric silent |
| Code quality | **No** — once (Emily) | Group category |
| Edge cases | **Per person where natural** — recommendation, not rubric | Supports use-case explanation and Q&A; not a rubric line |
| Accessibility | **No** — once (Alex) | Group category |
| APIs | **No** — once (Raashid). A member whose demo moment calls a live service needs a rehearsed fallback (@280), which is preparedness, not slide content | Group category |
| Limitations | **Once at wrap-up**; optional single line per person | Not a rubric category |

---

## 5. Recommended final template and time budget

**One person:** Part A 55–70 s + Part B 60–75 s ≈ **2:00–2:25 total**.
**Five people:** demo narration ≈ 5:00 (fills the demo slot exactly) + five debrief
cards ≈ 5:30 spanning the CA slot (3:00) and the SOLID slot (3:00), where two of the
five cards expand by ~45 s to deliver the team SOLID/pattern examples.

Fit check against the posted 20:00 table (user-reported):

| Slot | Table | This plan |
|---|---|---|
| Opening/spec | 3:00 | 3:00 — includes team story + the five user-story sentences as a roadmap |
| API | 1:00 | 1:00 (Raashid) |
| Demo | 5:00 | 5 × Part A, continuous Bob story |
| CA | 3:00 | project-level dependency rule once (~30 s) + 3 debrief cards |
| SOLID/patterns | 3:00 | 2 debrief cards expanded with the two deep principles + the pattern |
| Organization | 1:00 | Bianca |
| Quality | 1:00 | Emily |
| Testing | 1:00 | Bianca |
| Accessibility | 1:00 | Alex |
| Wrap-up | 1:00 | README, future work, close |
| **Total** | **20:00** | fits, with zero slack — rehearse to 18:30 in case the 15-minute reading wins |

- **During the demo:** A1–A6 only.
- **In the debrief:** B1–B5 only.
- **Duplication to avoid:** the dependency rule stated once, not five times; SOLID
  named twice total; coverage numbers only on Bianca's slide; no member re-explains
  another's layer.

---

## 6. Alternative structures compared

| Option | Description | Grading clarity | Time risk |
|---|---|---|---|
| 1. Explain-in-demo | Each member does story + diagram + code + decision immediately at their demo moment | Everything per person is contiguous — easy to grade one person | **High.** 5 × ~2:20 inside the demo ≈ 12 min; the story stalls at every UML slide; live UI and slides fight for the screen |
| 2. Demo-then-debrief | Clean 5-minute story demo, then all five debrief cards in a row | Story flows; technical block is coherent | Medium. A 5-card wall (~6 min) gets monotonous; CA/SOLID slots absorb it only if cards stay under 75 s |
| 3. **Hybrid (recommended)** | User-facing behaviour in the demo (Part A); technical evidence as one card per person in the CA/SOLID blocks (Part B), two cards carrying the SOLID examples | Each Individual-rubric artifact has a predictable home; the grader sees each member exactly twice; team categories keep single owners | Lowest. Matches the posted table's slots without moving any of them |

**Recommendation: Option 3.** It is also what the TA already liked — the narrative
stays a narrative, and the rubric evidence arrives labelled.

---

## 7. Evidence checklist for one person (blank)

```markdown
Member: ______________  Use case: ______________

[ ] User story sentence (as-a / I-want / so-that), ≤ 25 words
[ ] Demo action: exact click(s), rehearsed, with deterministic data
[ ] Before View: live state + backup screenshot in appendix
[ ] After View: live state + backup screenshot in appendix
[ ] Use-case class diagram: full slice, legible on one slide, roles labelled
[ ] Interactor excerpt: 6–12 lines, boundaries visible, on the same slide
[ ] Design decision: choice / rejected alternative / consequence (one line each)
[ ] SOLID carrier? (only if this member is one of the two): principle + where visible
[ ] Pattern carrier? (only if applicable): pattern + where introduced
[ ] Evidence tag: one sentence (test name, measured number, or honoured constraint)
[ ] Edge/failure beat: what breaks, what the user sees, plan is not corrupted
[ ] Limitation: one honest sentence (optional)
[ ] Transition line to the next story beat
[ ] Q&A prep: 3 likely TA questions + answers (per @353)
```

---

## 8. Risks and clarifications for the TA

1. **15 vs 20 minutes** — live conflict between the Group rubric descriptor and the
   Expectations page (S-085). Highest-priority TA question; rehearse a 15-minute cut.
2. **Do live demo states satisfy "before and after Views," or are slides expected?**
   Prepare both; ask if the TA wants the appendix shown.
3. **Must all three Required Elements appear within the 20 minutes for each member,**
   or is showing them on request in Q&A acceptable? Assume in-time until told otherwise.
4. **Duplication risk:** five members each re-stating the dependency rule, or each
   naming SOLID principles. The template gives both a single home.
5. **Does 5 × Part B fit?** Yes, but with zero slack. If a card overruns 75 s, the
   wrap-up buffer is the first casualty — appoint a timekeeper.
6. **Impressive but not worth presenting:** search-pruning internals, benchmark tables,
   Checkstyle counts beyond one number, CAVE packaging detail, full test listings,
   per-provider API fallback chains. All of it belongs in Q&A back-pocket slides, not
   the 20 minutes. The rubric rewards labelled evidence, not volume.
7. **The team story** (R1's "team must have one team user story") needs an explicit
   home — the opening 3:00 — and an owner. Currently implicit in the Bob narrative;
   make the sentence itself appear on the opening slide.
