clear
for i in $(seq 10); do
	LD_PRELOAD=../lib/lib440lib.so ../tools/440cat foo.txt > out_${i}.txt &
done
wait