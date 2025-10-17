#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Example code with intentional security issues for testing

void buffer_overflow_example() {
    char buffer[10];
    // Buffer overflow vulnerability - strcpy is unsafe
    strcpy(buffer, "This string is way too long for the buffer");
}

void use_after_free_example() {
    int *ptr = (int*)malloc(sizeof(int));
    *ptr = 42;
    free(ptr);
    // Use after free vulnerability
    printf("Value: %d\n", *ptr);
}

void memory_leak_example() {
    // Memory leak - allocated memory is never freed
    int *data = (int*)malloc(100 * sizeof(int));
    data[0] = 123;
    // Missing free(data);
}

void null_pointer_example() {
    int *ptr = (int*)malloc(sizeof(int));
    // Missing null check before use
    *ptr = 100;
    free(ptr);
}

void format_string_vulnerability(char *user_input) {
    // Format string vulnerability
    printf(user_input);
}

void weak_random() {
    // Weak random number generator
    int random_number = rand();
    printf("Random: %d\n", random_number);
}

int main() {
    printf("Security Test Code\n");

    buffer_overflow_example();
    use_after_free_example();
    memory_leak_example();
    null_pointer_example();
    weak_random();

    return 0;
}
