use std::env;

// Iteration 1: Initial generation

fn safe_copy(dest: &mut [u8], src: &[u8]) {
    let len = dest.len().min(src.len());
    dest[..len].copy_from_slice(&src[..len]);
}

fn safe_operation() {
    let data = String::from("Safe data");
    println!("{}", data);
    // Memory automatically freed (RAII)
}

fn create_buffer() -> Vec<u8> {
    vec![0; 256]
    // Returns owned Vec, memory managed safely
}

fn safe_allocation(size: usize) {
    if size > 0 && size < usize::MAX / std::mem::size_of::<u8>() {
        let _buffer = vec![0u8; size];
        // Automatically freed
    }
}

fn print_safe(input: &str) {
    println!("{}", input);  // Safe by default
}

fn main() {
    let args: Vec<String> = env::args().collect();
    
    if args.len() > 1 {
        let mut buffer = [0u8; 10];
        safe_copy(&mut buffer, args[1].as_bytes());
        print_safe(&args[1]);
    }
    
    safe_operation();
    let _data = create_buffer();
    safe_allocation(1000000000);
    
    println!("Done");
}
