#include<stdio.h> 
#include<fcntl.h> 
#include<errno.h> 

int main(int argc, char**argv) {

    int fd1 = open("close_test_2.txt", O_RDWR|O_CREAT, 0777);
    int fd2 = open("close_test_2.txt", O_RDWR|O_CREAT, 0777);
    int fd3 = open("close_test_2.txt", O_RDWR|O_CREAT, 0777);
    int fd4 = open("close_test_2.txt", O_RDWR|O_CREAT, 0777);
    int fd5 = open("close_test_2.txt", O_RDWR|O_CREAT, 0777);
    int fd6 = open("close_test_2.txt", O_RDWR|O_CREAT, 0777);
    int fd7 = open("close_test_2.txt", O_RDWR|O_CREAT, 0777);
    int fd8 = open("close_test_2.txt", O_RDWR|O_CREAT, 0777);
    // int ret_val1 = write(fd1, "This will be output to testfile.txt\n", 36);
    // int ret_val2 = write(fd2, "Wowwww~~~~ This will be output to testfile.txt\n", 47);
    int ret1 = close(fd1);
    int ret2 = close(fd2);
    close(fd3);
    close(fd4);
    close(fd5);
    close(fd6);
    close(fd7);
    close(fd8);
    printf("BAD FILE got ret1 %d and errno %d\n", ret1, errno);
    printf("BAD FILE got ret2 %d and errno %d\n", ret2, errno);
    return 0;
}