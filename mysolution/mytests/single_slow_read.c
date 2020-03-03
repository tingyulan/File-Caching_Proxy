#include<stdio.h> 
#include<fcntl.h> 
#include<errno.h> 

int main(int argc, char**argv) {
    char buf[10000];
    int i;

    
    int fd2 = open("A", O_RDONLY, 0777);
    int ret_val2 = read(fd2, &buf, 10000);
    sleep(100);

    int ret2 = close(fd2);

    printf("buf\n");
    for(i=0; i<10000; i++){
        printf("%c", buf[i]);
    }

    return 0;
}