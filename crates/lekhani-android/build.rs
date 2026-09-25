/// Build script for the `lekhani-android` crate.
///
/// UniFFI proc-macro mode is the primary binding generation strategy.
/// This build script is retained so that `uniffi::generate_scaffolding()`
/// can be switched to UDL-file mode in the future without requiring a
/// structural change to the crate.
fn main() {
    // Tell Cargo to re-run this script only if the UDL file changes,
    // not on every build of every source file.
    println!("cargo:rerun-if-changed=src/lekhani.udl");

    // Android 15+ 16 KB page-size ELF LOAD and RELRO segment alignment
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        println!("cargo:rustc-link-arg=-Wl,-z,max-page-size=16384");
        println!("cargo:rustc-link-arg=-Wl,-z,common-page-size=16384");
    }
}
