package io.outcomeescrow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.util.UUID;

@AnalyzeClasses(
    packages = "io.outcomeescrow.domain",
    importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class DomainPurityTest {

  @ArchTest
  static final ArchRule onlyJdkAndDomainDependencies =
      classes()
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "io.outcomeescrow.domain..");

  @ArchTest
  static final ArchRule noSystemClock =
      noClasses()
          .should()
          .callMethod(Instant.class, "now")
          .orShould()
          .callMethod(java.time.Clock.class, "systemUTC");

  @ArchTest
  static final ArchRule noRandomness =
      noClasses()
          .should()
          .callMethod(java.util.Random.class, "nextDouble")
          .orShould()
          .callMethod(java.util.Random.class, "nextInt")
          .orShould()
          .callMethod(java.util.Random.class, "nextBoolean")
          .orShould()
          .callMethod(UUID.class, "randomUUID");

  @ArchTest
  static final ArchRule noSpring =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "javax..", "jakarta..");
}
