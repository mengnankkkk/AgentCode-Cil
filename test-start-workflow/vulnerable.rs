use std::env;

// Rust equivalent - Memory safe by default!

fn safe_copy(dest: &mut [u8], src: &[u8]) {
    let len = dest.len().min(src.len());
    dest[..len].copy_from_slice(&src[..len]);
}

fn safe_memory_demo() {
    let data = String::from("This is safe");
    println!("{}", data);
    // Memory automatically freed (RAII)
}

fn create_buffer_safe() -> Vec<u8> {
    vec![0; 256]
    // Returns owned Vec, memory managed safely
}

fn safe_allocation(size: usize) {
    if size > 0 {
        let buffer = vec![0u8; size];
        // Automatically freed at end of scope
    }
}

fn print_user_input_safe(input: &str) {
    println!("{}", input);  // Format string safety by default
}

fn main() {
    let args: Vec<String> = env::args().collect();
    
    if args.len() > 1 {
        let mut small_buffer = [0u8; 10];
        safe_copy(&mut small_buffer, args[1].as_bytes());
        print_user_input_safe(&args[1]);
    }
    
    safe_memory_demo();
    let _buffer = create_buffer_safe();
    safe_allocation(1000000000);
    
    println!("Program completed safely");
}
