#include <pthread.h>
#include <stdio.h>

int shared_counter = 0;  // Race condition - no synchronization

void* increment_counter(void* arg) {
    for (int i = 0; i < 100000; i++) {
        shared_counter++;  // Race condition
    }
    return NULL;
}

int main() {
    pthread_t thread1, thread2;
    
    pthread_create(&thread1, NULL, increment_counter, NULL);
    pthread_create(&thread2, NULL, increment_counter, NULL);
    
    pthread_join(thread1, NULL);
    pthread_join(thread2, NULL);
    
    printf("Counter: %d\n", shared_counter);
    return 0;
}
