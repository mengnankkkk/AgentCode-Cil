// 简单的测试文件，用于验证战略分析功能
// 包含一些故意的安全问题

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// 缓冲区溢出漏洞
void vulnerable_function(char* input) {
    char buffer[100];
    strcpy(buffer, input);  // 危险：没有边界检查
    printf("Buffer content: %s\n", buffer);
}

// 内存泄漏
void memory_leak_function() {
    char* ptr = malloc(1024);
    // 忘记调用 free(ptr)
    printf("Memory allocated but not freed\n");
}

// 空指针解引用
void null_pointer_function(char* data) {
    if (data == NULL) {
        return;
    }
    // 潜在的空指针问题
    printf("Data length: %d\n", strlen(data));
}

// 使用未初始化的变量
void uninitialized_variable() {
    int value;
    printf("Uninitialized value: %d\n", value);  // 危险
}

int main() {
    char large_input[200];
    memset(large_input, 'A', 199);
    large_input[199] = '\0';
    
    vulnerable_function(large_input);  // 会导致缓冲区溢出
    memory_leak_function();
    null_pointer_function(NULL);
    uninitialized_variable();
    
    return 0;
}