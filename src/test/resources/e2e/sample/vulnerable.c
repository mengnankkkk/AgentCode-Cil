/**
 * Sample C code for testing security analysis
 * Contains intentional security vulnerabilities for testing purposes
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/**
 * Buffer overflow vulnerability - unsafe strcpy
 */
void unsafe_copy(const char* input) {
    char buffer[64];
    strcpy(buffer, input);  // VULNERABILITY: No bounds checking
    printf("Copied: %s\n", buffer);
}

/**
 * Null pointer dereference vulnerability
 */
int* get_value(int condition) {
    if (condition > 0) {
        int* value = (int*)malloc(sizeof(int));
        *value = 42;
        return value;
    }
    return NULL;  // VULNERABILITY: Can return NULL
}

void use_value(int condition) {
    int* ptr = get_value(condition);
    printf("Value: %d\n", *ptr);  // VULNERABILITY: No null check
    free(ptr);
}

/**
 * Memory leak vulnerability
 */
void allocate_memory(int count) {
    for (int i = 0; i < count; i++) {
        char* buffer = (char*)malloc(1024);
        // VULNERABILITY: Memory not freed in error path
        if (buffer == NULL) {
            return;
        }
        sprintf(buffer, "Buffer %d", i);
        printf("%s\n", buffer);
        // VULNERABILITY: Memory not freed even in success case
    }
}

/**
 * Use-after-free vulnerability
 */
void use_after_free() {
    int* data = (int*)malloc(sizeof(int) * 10);
    *data = 100;

    free(data);

    // VULNERABILITY: Using freed memory
    printf("Data: %d\n", *data);
}

/**
 * Integer overflow vulnerability
 */
void calculate_size(unsigned int count, unsigned int item_size) {
    unsigned int total = count * item_size;  // VULNERABILITY: No overflow check

    void* buffer = malloc(total);
    if (buffer == NULL) {
        printf("Allocation failed\n");
        return;
    }

    printf("Allocated %u bytes\n", total);
    free(buffer);
}

/**
 * Format string vulnerability
 */
void log_message(const char* user_input) {
    printf(user_input);  // VULNERABILITY: Format string bug
}

/**
 * Double free vulnerability
 */
void double_free_bug() {
    char* data = (char*)malloc(128);
    strcpy(data, "test data");

    free(data);

    // VULNERABILITY: Double free
    if (strlen(data) > 0) {
        free(data);
    }
}

/**
 * Main function with multiple issues
 */
int main(int argc, char* argv[]) {
    // Test buffer overflow
    if (argc > 1) {
        unsafe_copy(argv[1]);  // VULNERABILITY: User input to unsafe function
    }

    // Test null pointer dereference
    use_value(-1);

    // Test memory leak
    allocate_memory(10);

    // Test use-after-free
    use_after_free();

    // Test integer overflow
    calculate_size(0xFFFFFFFF, 2);

    // Test format string
    if (argc > 2) {
        log_message(argv[2]);  // VULNERABILITY: Format string with user input
    }

    // Test double free
    double_free_bug();

    return 0;
}
