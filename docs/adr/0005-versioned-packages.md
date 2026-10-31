# ADR-0005 — Phase folders as versions, one concept per file

- **Status:** accepted (revised 2026-10-31: phase folders replace `board/v1`-style packages)
- **Date:** 2026-10-31

## Context
This is a learning project. Each improvement (better move generation, better search) should be
understandable on its own, and earlier code must stay provably correct when later code arrives.

## Decision
- One major concept per file.
- **Each phase folder is a version.** Once a phase is tagged, its folder is frozen. Later phases
  import from earlier folders, and put any improved version of a component in their own folder.
  Old code keeps its tests in CI. The full rules are in [CODE_LAYOUT.md](../CODE_LAYOUT.md).

## Consequences
- **Easier:** you can read one phase in isolation, see exactly what each phase added, and use old
  implementations as test oracles. For example, Phase 2's magic move generator must match its ray
  generator on thousands of positions.
- **Harder:** a later phase that needs a changed `Board` must write its own version and decide
  which earlier pieces it still uses.

## Alternatives considered
- **Git tags only:** history is safe, but you can't run old and new code side by side or compare them in one test.
- **`board/v1`, `board/v2` sub-packages:** the same idea, but deeper folders and spread across phases.
