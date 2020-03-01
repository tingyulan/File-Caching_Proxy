export CLASSPATH=$PWD:$PWD/../lib
export proxy15440=127.0.0.1
export proxyport15440=11122
export pin15440=520520520


java Server 11122 ./
java -Xms5000k -Xmx5000k Server 13579 ./server_root

export proxyport15440=17684
java Proxy 127.0.0.1 13524 /tmp/cache 100000
java -Xms5000k -Xmx5000k Proxy 127.0.0.1 13524 ./cache_root1 100000

java -Xms5000k -Xmx5000k Proxy 127.0.0.1 13579 ./cache_root1 100000000
17237


LD_PRELOAD=../lib/lib440lib.so ../tools/440read ../README
LD_PRELOAD=../lib/lib440lib.so ./open_dir
sh concurrency_write.sh
LD_PRELOAD=../lib/lib440lib.so ./../tools/440read ./../README
LD_PRELOAD=../lib/lib440lib.so ./../tools/440cat ./../README

cat README | LD_PRELOAD=../lib/lib440lib.so ./../tools/440write output.txt

tar cvzf ../mysolution.tgz Makefile Proxy.java Server.java ServerIntf.java ServerData.java LRUCache.java project2.pdf