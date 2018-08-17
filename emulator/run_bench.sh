#1/bin/bash

outdir=./output
benchmark_list=$1
config=$2

riscv_benchmarks="dhrystone median qsort rsort towers vvadd multiply mm spmv mt-vvadd mt-matmul fl-matmul pmp"

#====
max_cycles=100000000000
emu_opts="+cycle-count +max-cycles=$max_cycles +verbose"

mkdir -p output

#====
case "$1" in
	"")
		benchmark_list=dhrystone
		;;
	"all")
		benchmark_list=$riscv_benchmarks
		rm -rf $outdir/*
		;;
  "help")
		echo ">>> Syntax:  run_bench.sh <benchmark_name> <emu_config> <dramsim>" 
		exit -1
		;;
  "h")
		echo ">>> Syntax:  run_bench.sh <benchmark_name> <emu_config> <dramsim>" 
		exit -1
		;;
	*)
		benchmark_list=$1
		;;
esac

if [ "$2" == "" ]; then
	config=myCore
fi

if [ "$3" == "dramsim" ]; then
	dramsim_msg="Enable"
else
	dramsim_msg="Disable"
fi

#====
emulator=./emulator-freechips.rocketchip.system-$config

echo ""
echo "=================================================================="
echo ">>> Emulator          $emulator"
echo ">>> Max cycle         $max_cycles"
echo ">>> DRAM sim          $dramsim_msg"
echo "=================================================================="
echo ""

#==== 
for benchmark in $benchmark_list
do
	#==== setup
	bm_bin=$benchmark.riscv
	bm_hex=$benchmark.riscv.hex
	bm_run=$benchmark.riscv.run
	bm_log=$benchmark.riscv.log
	
	#==== clean
	rm -rf $outdir/$bm_bin $outdir/$bm_hex $outdir/$bm_run $outdir/$bm_log
	ln -fs $RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/$bm_bin   $outdir/$bm_bin
	ln -fs $RISCV/riscv64-unknown-elf/share/riscv-tests/benchmarks/$bm_hex   $outdir/$bm_hex

	#==== simulate
	if [ "$3" == "dramsim" ]; then
		emu_opts="$emu_opts +dramsim"
    emu_cmds="$emulator $emu_opts +loadmem=$outdir/$bm_hex 2> /dev/null 2> $outdir/$bm_run"
		#echo ">>> Emu. cmd          $emu_cmds" | tee -a $outdir/$bm_log
		$emulator $emu_opts +loadmem=$outdir/$bm_hex 2> /dev/null 2> $outdir/$bm_run | tee -a $outdir/$bm_log
	else
		emu_cmds="$emulator $emu_opts $outdir/$bm_bin 2> /dev/null 2> $outdir/$bm_run"
		#echo ">>> Emu. cmd          $emu_cmds" | tee -a $outdir/$bm_log
		$emulator $emu_opts $outdir/$bm_bin 2> /dev/null 2> $outdir/$bm_run | tee -a $outdir/$bm_log	
	fi

	echo "" | tee -a $outdir/$bm_log
	echo ">>> Helps:"                          | tee -a $outdir/$bm_log
	echo ">>> Benchmark         $benchmark"    | tee -a $outdir/$bm_log
	echo ">>> Configuration     $config"       | tee -a $outdir/$bm_log
	echo ">>> Log file          $bm_log"       | tee -a $outdir/$bm_log
	echo ">>> DRAM sim          $dramsim_msg"	 | tee -a $outdir/$bm_log
	echo ">>> Emu. opts         $emu_opts"	   | tee -a $outdir/$bm_log
	echo ">>> Emu. cmds         $emu_cmds"	   | tee -a $outdir/$bm_log
	echo "" | tee -a $outdir/$bm_log
	cat $outdir/$bm_log
done

if [ "$1" == "all" ]; then
	result_file=result_$config\_riscv_bench.csv
	./get_results.sh $config $outdir
	rm -rf data/$config && mkdir -p data/$config
	cp -r output/*.log $result_file data/$config
fi
