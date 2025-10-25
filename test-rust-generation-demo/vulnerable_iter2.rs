use std::env;
use std::io::{self, Result};

// Iteration 2: Added error handling

fn safe_copy(dest: &mut [u8], src: &[u8]) -> Result<()> {
    if src.len() > dest.len() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "Source too large for destination"
        ));
    }
    dest[..src.len()].copy_from_slice(src);
    Ok(())
}

fn safe_operation() -> Result<()> {
    let data = String::from("Safe data");
    println!("{}", data);
    Ok(())
}

fn create_buffer() -> Result<Vec<u8>> {
    Ok(vec![0; 256])
}

fn safe_allocation(size: usize) -> Result<()> {
    if size == 0 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "Size must be positive"
        ));
    }
    if size > usize::MAX / std::mem::size_of::<u8>() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "Size too large, potential overflow"
        ));
    }
    let _buffer = vec![0u8; size];
    Ok(())
}

fn print_safe(input: &str) -> Result<()> {
    println!("{}", input);
    Ok(())
}

fn main() -> Result<()> {
    let args: Vec<String> = env::args().collect();
    
    if args.len() > 1 {
        let mut buffer = [0u8; 10];
        safe_copy(&mut buffer, args[1].as_bytes())?;
        print_safe(&args[1])?;
    }
    
    safe_operation()?;
    let _data = create_buffer()?;
    safe_allocation(1000000000)?;
    
    println!("Done");
    Ok(())
}
