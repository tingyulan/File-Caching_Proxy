#include<stdio.h> 
#include<fcntl.h> 
#include<errno.h> 

int main(int argc, char**argv) {
    char buf[100], buf2[100], buf3[100], buf4[100], buf5[100];
    int i;

    int fd1 = open("slow_write.txt", O_RDWR|O_CREAT, 0777);
    int fd2 = open("slow_write.txt", O_RDONLY, 0777);
    int fd3 = open("slow_write.txt", O_RDONLY, 0777);
    int fd4 = open("slow_write.txt", O_RDONLY, 0777);
    int ret_val1 = write(fd1, "This will be output to testfile.txt\n", 36);
    int ret_val2 = read(fd2, &buf, 36);
    read(fd3, &buf2, 36);
    read(fd4, &buf3, 36);
    int ret1 = close(fd1);
    int ret2 = close(fd2);
    close(fd3);
    close(fd4);

    int fd = open("slow_write.txt", O_RDONLY, 0777);
    int ret_val = read(fd, &buf2, 36);
    int ret = close(fd);
    // printf("BAD FILE got ret1 %d and errno %d\n", ret1, errno);
    // printf("BAD FILE got ret2 %d and errno %d\n", ret2, errno);
    printf("buf\n");
    for(i=0; i<36; i++){
        printf("%c", buf[i]);
    }

    printf("buf2\n");
    for(i=0; i<36; i++){
        printf("%c", buf2[i]);
    }

    // int fd1 = open("slow_write.txt", O_RDWR|O_CREAT, 0777);
    // int ret_val1 = write(fd1, "This will be output to testfile.txt\n", 36);
    // int ret1 = close(fd1);
    // printf("BAD FILE got ret1 %d and errno %d\n", ret_val1, errno);

    return 0;
}