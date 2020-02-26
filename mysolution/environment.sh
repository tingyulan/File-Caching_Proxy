export CLASSPATH=$PWD:$PWD/../lib
export proxy15440=127.0.0.1
export proxyport15440=11122
export pin15440=520520520

java Server 11122 ./

java Proxy 127.0.0.1 11122 /tmp/cache 100000


LD_PRELOAD=../lib/lib440lib.so ../tools/440read ../README
LD_PRELOAD=../lib/lib440lib.so ./open_dir
sh concurrency_write.sh
LD_PRELOAD=../lib/lib440lib.so ./../tools/440read ./../README
LD_PRELOAD=../lib/lib440lib.so ./../tools/440cat ./../README

cat README | LD_PRELOAD=../lib/lib440lib.so ./../tools/440write output.txt

tar cvzf ../mysolution.tgz Makefile Proxy.java Server.java ServerIntf.java