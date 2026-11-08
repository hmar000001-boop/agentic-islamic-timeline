pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "agentic-islamic-timeline"
include(":app")
include(":core:common")
include(":core:timeline-api")
include(":core:agent-api")
include(":core:authorization")
include(":core:security")
include(":core:localization")
include(":core:persistence")

include(":feature:timeline")
include(":feature:canvas")

include(":core:agent-runtime")

include(":core:knowledge-api")
include(":core:corpus")
include(":core:retrieval")
