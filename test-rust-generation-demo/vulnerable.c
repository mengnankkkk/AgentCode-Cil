#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Issue 1: Buffer overflow
void unsafe_copy(char *dest, const char *src) {
    strcpy(dest, src);  // No bounds checking!
}

// Issue 2: Use after free
void use_after_free_bug() {
    char *ptr = malloc(100);
    free(ptr);
    strcpy(ptr, "Dangerous!");  // Use after free!
}

// Issue 3: Memory leak
char* memory_leak() {
    char *buffer = malloc(256);
    // Never freed!
    return buffer;
}

// Issue 4: Integer overflow
void integer_overflow(int size) {
    if (size > 0) {
        char *buf = malloc(size * sizeof(char));  // Overflow risk
        free(buf);
    }
}

// Issue 5: Format string vulnerability
void format_string_bug(char *user_input) {
    printf(user_input);  // Direct printf of user input!
}

int main(int argc, char *argv[]) {
    char small_buffer[10];
    
    if (argc > 1) {
        unsafe_copy(small_buffer, argv[1]);
        format_string_bug(argv[1]);
    }
    
    use_after_free_bug();
    char *leaked = memory_leak();
    integer_overflow(1000000000);
    
    printf("Done\n");
    return 0;
}
