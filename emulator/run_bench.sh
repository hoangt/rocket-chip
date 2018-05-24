#1/bin/bash

outdir=./output
benchmark_list=$1
config=$2

riscv_benchmarks="dhrystone median qsort rsort towers vvadd multiply mm spmv mt-vvadd mt-matmul fl-matmul pmp"

#====
case "$1" in
	"")
		benchmark_list=dhrystone
		;;
	"all")
		benchmark_list=$riscv_benchmarks
		rm -rf $outdir/*
		;;
	*)
		benchmark_list=$1
		;;
esac

if [ "$2" == "" ]; then
	config=cBC16KL1
fi

echo ">>> Syntax         run_bench.sh <benchmark_name> <config_name>"
echo ">>> Config         $config"
#echo ">>> Benchmark      $benchmark"
echo ""

#====
emulator=./emulator-freechips.rocketchip.system-$config
max_cycles=10000000000

#==== 
for benchmark in $benchmark_list
do
	#==== setup
	bm_riscv=$outdir/$benchmark.riscv
	bm_run=$outdir/$benchmark.riscv.run
	bm_log=$outdir/$benchmark.riscv.log

	#==== clean
	rm -rf output/$benchmark.riscv $bm_run $bm_log
	ln -fs $RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/$benchmark.riscv output/$benchmark.riscv

	#==== simulate
	echo > $bm_log
	echo ">>> Benchmark $benchmark, Configuration $$config" >> $bm_log
	$emulator +max-cycles=$max_cycles  $bm_riscv 2> /dev/null 2> $bm_run | tee -a $bm_log
	echo >> $bm_log
done

if [ "$1" == "all" ]; then
	result_file=result_$config\_riscv_bench.csv
	./get_results.sh $config
	rm -rf data/$config && mkdir -p data/$config
	cp -r output/*.log $result_file data/$config
fi
