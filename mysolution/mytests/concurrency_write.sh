clear
for i in $(seq 10); do
    cat ../README | LD_PRELOAD=../lib/lib440lib.so ./../tools/440write foo_${i}.txt &
done
wait


# > out_${i}.txt