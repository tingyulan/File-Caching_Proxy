#include<stdio.h> 
#include<fcntl.h> 
#include<errno.h> 

int main(int argc, char**argv) {

    int fd = open("./close_test_2.txt", O_RDWR|O_CREAT, 0777);
    int ret_val = write(fd, "This will be output to testfile.txt\n", 36);
    close(fd);

    int ret = close(fd);
    printf("BAD FILE got ret %d and errno %d\n", ret, errno);
    return 0;
}