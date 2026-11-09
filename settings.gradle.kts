pluginManagement {
    repositories {
        // Google Maven mirror (dl.google.com is unreliable from Iran)
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Maven Central mirror + fallback
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
            content {
                excludeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
            content {
                excludeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Myket billing client is published via JitPack (github.com/myketstore/myket-billing-client)
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.myketstore")
            }
        }
    }
}

rootProject.name = "BlockBoom"
include(":app")