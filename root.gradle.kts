plugins {
    id("dev.deftu.gradle.multiversion-root") version "2.82.0"
}

/**
 * Version nodes and the translation chain between them.
 *
 * <p>The node named in `versions/mainProject` is the one the sources are edited in; the others are
 * produced from it by the preprocessor plus the Bloom replacement rules in `build.gradle.kts`.
 */
preprocess {
    strictExtraMappings.set(true)

    val fabric262 = createNode("26.2-fabric", 26_02_00, null)
    val fabric263 = createNode("26.3-fabric", 26_03_00, null)

    // 26.3 is produced from 26.2 by the preprocessor plus the Bloom rules in build.gradle.kts.
    fabric263.link(fabric262)
}
