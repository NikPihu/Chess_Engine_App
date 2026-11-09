package com.gambyte;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Keeps the modular monolith modular. If one of these fails, a feature reached into another
 * feature's internals: go through that feature's service instead.
 */
@AnalyzeClasses(packages = "com.gambyte", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /** Only the user feature touches user tables. */
  @ArchTest
  static final ArchRule userRepositoriesArePrivate =
      noClasses().that().resideOutsideOfPackage("com.gambyte.user..")
          .should().dependOnClassesThat().resideInAPackage("com.gambyte.user.repo..");

  /** Only the game feature touches the games table. */
  @ArchTest
  static final ArchRule gameRepositoriesArePrivate =
      noClasses().that().resideOutsideOfPackages("com.gambyte.game..", "com.gambyte.bot..")
          .should().dependOnClassesThat().resideInAPackage("com.gambyte.game.repo..");

  /** Controllers are entry points: no service may call a controller. */
  @ArchTest
  static final ArchRule servicesDoNotUseControllers =
      noClasses().that().haveSimpleNameEndingWith("Service")
          .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller");

  /** The chess-rules library stays behind the game/engine/analysis/puzzle/learn features. */
  @ArchTest
  static final ArchRule rulesUsedOnlyWhereNeeded =
      noClasses().that().resideInAnyPackage("com.gambyte.user..", "com.gambyte.payment..", "com.gambyte.social..", "com.gambyte.auth..")
          .should().dependOnClassesThat().resideInAnyPackage("phase1..", "phase2..", "phase3..", "phase6..");
}
