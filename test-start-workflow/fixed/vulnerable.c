#include <stdio.h>
#include <string.h>
#include <stdlib.h>

// Fix 1: Safe strcpy with bounds checking
void safe_strcpy(char *dest, const char *src, size_t dest_size) {
    strncpy(dest, src, dest_size - 1);
    dest[dest_size - 1] = '\0';
}

// Fix 2: Removed use after free
void safe_memory_demo() {
    char *ptr = malloc(100);
    strcpy(ptr, "This is safe");
    printf("%s\n", ptr);
    free(ptr);
    // No access after free
}

// Fix 3: Fixed memory leak
char* create_buffer_safe() {
    char *buffer = malloc(256);
    // Caller must free this buffer
    return buffer;
}

// Fix 4: Integer overflow check
void safe_allocation(int size) {
    if (size > 0 && size < INT_MAX / sizeof(char)) {
        char *buffer = malloc(size * sizeof(char));
        if (buffer) {
            // Use buffer
            free(buffer);
        }
    }
}

// Fix 5: Safe format string
void print_user_input_safe(char *input) {
    printf("%s", input);  // Safe format string
}

int main(int argc, char *argv[]) {
    char small_buffer[10];
    
    if (argc > 1) {
        safe_strcpy(small_buffer, argv[1], sizeof(small_buffer));
        print_user_input_safe(argv[1]);
    }
    
    safe_memory_demo();
    char *buffer = create_buffer_safe();
    free(buffer);  // Properly freed
    safe_allocation(1000000000);
    
    printf("Program completed safely\n");
    return 0;
}
