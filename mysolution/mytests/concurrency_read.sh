clear
for i in $(seq 3); do
	LD_PRELOAD=../lib/lib440lib.so ../tools/440read README &
done
wait