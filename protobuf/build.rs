
fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::compile_protos("brackets_service.proto")?;
    tonic_build::compile_protos("example.proto")?;
    Ok(())
}