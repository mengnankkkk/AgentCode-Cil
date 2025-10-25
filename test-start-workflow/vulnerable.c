#include <stdio.h>
#include <string.h>
#include <stdlib.h>

// Vulnerability 1: Buffer overflow
void unsafe_strcpy(char *dest, const char *src) {
    strcpy(dest, src);  // No bounds checking
}

// Vulnerability 2: Use after free
void use_after_free_demo() {
    char *ptr = malloc(100);
    free(ptr);
    strcpy(ptr, "This is dangerous");  // Use after free
}

// Vulnerability 3: Memory leak
char* create_buffer() {
    char *buffer = malloc(256);
    // Missing free - memory leak
    return buffer;
}

// Vulnerability 4: Integer overflow
void integer_overflow(int size) {
    if (size > 0) {
        char *buffer = malloc(size * sizeof(char));
        // No check for integer overflow
    }
}

// Vulnerability 5: Format string vulnerability
void print_user_input(char *input) {
    printf(input);  // Format string vulnerability
}

int main(int argc, char *argv[]) {
    char small_buffer[10];
    
    if (argc > 1) {
        unsafe_strcpy(small_buffer, argv[1]);  // Buffer overflow risk
        print_user_input(argv[1]);             // Format string risk
    }
    
    use_after_free_demo();
    char *leaked = create_buffer();
    integer_overflow(1000000000);
    
    printf("Program completed\n");
    return 0;
}
