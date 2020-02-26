#include<stdio.h> 
#include<fcntl.h> 
#include<errno.h> 

int main(int argc, char**argv) {

    int fd = open("opencreate.txt", O_RDWR|O_CREAT, 0666);  //O_RDWR|O_CREAT
    int ret_val = write(fd, "This will be output to opencreate.txt\n", 36);
    close(fd);
    return 0;
}


