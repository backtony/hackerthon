rootProject.name = "sample"

include("domain")
include("application")

include("adapter:inbound:controller")

include("adapter:outbound:repository")

include("infrastructure:h2")

include("server:web")
include("architecture-test")

includeBuild("build-plugin")
includeBuild("common")
