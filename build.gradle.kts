// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // These plugins are registered here globally but marked 'apply false' so they don't
    // inject overhead into the root module. The actual ':app' module will activate them individually.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.owasp.dependencycheck) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}