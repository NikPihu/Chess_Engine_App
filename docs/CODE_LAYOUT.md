# Code layout rules

## Flat, phase-wise folders
```
chess_engine/
├─ pom.xml          the only build file
├─ engine.cmd       shortcut to run the engine:  engine "position startpos" d
├─ phase1/          all Phase 1 code, flat         (package phase1)
│   └─ tests/       Phase 1 tests + test data      (package phase1.tests)
├─ phase2/          Phase 2 code; it imports what it needs: import phase1.Board;
│   └─ tests/
├─ uci/             the program you run (UCI commands); it wires the phases together
│   └─ tests/
└─ docs/
```

Any class is at most two folders deep: `phase1/Board.java`, `phase1/tests/BoardTest.java`.

## The rules
1. **One concept per file.** Each file starts with a comment explaining its idea.
2. **A phase folder is frozen once its phase is tagged.** After `phase-1` is tagged, `phase1/`
   only gets bug fixes, and each fix comes with a test that reproduces the bug.
3. **Later phases build on earlier folders, never edit them.** `phase2/` imports `phase1.Board`
   instead of copying it. If a later phase needs a *better* version of something, it writes the
   new version in its own folder. For example, `phase2/` will have both a simple `RayMoveGenerator`
   and a fast `MagicMoveGenerator`.
4. **Old code keeps its tests running.** CI proves every phase correct on every build, and newer
   code is tested against older code. For example, the magic generator must produce exactly the
   same moves as the ray generator on thousands of positions.
5. **Dependencies only point backwards.** `phase2` may use `phase1`, but never the other way round.
6. **`uci/` is not a phase.** It's the front door. It grows each phase to expose new commands, and
   picks which phase's code to use.

## How the build finds the code
The project folder itself is the Java source root (`<sourceDirectory>` in `pom.xml`), so the
folder name *is* the package name (`phase1/Board.java` → `package phase1;`). Tests are compiled
along with the code. JUnit is only available at build time ("provided" scope), and test classes
are left out of `target/engine.jar`.
