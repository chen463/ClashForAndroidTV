plugins {
    kotlin("android")
    id("com.android.library")
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
//    implementation(libs.ktor.core)
//    implementation(libs.ktor.ws)
//    implementation(libs.ktor.jackson)
//    implementation(libs.ktor.jetty)
    implementation(libs.nanohttpd)
    implementation(libs.gson)
}
