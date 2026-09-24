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

    // When a `lekhani.udl` file exists, generate UniFFI scaffolding from it.
    // Currently we use proc-macro mode (`uniffi::setup_scaffolding!()` in
    // lib.rs), so UDL generation is commented out.  Uncomment the line below
    // to switch to UDL-driven binding generation:
    //
    //   uniffi::generate_scaffolding("src/lekhani.udl").unwrap();
}
