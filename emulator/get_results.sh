#1/bin/bash

config=$1
input_dir=$2

riscv_benchmarks="dhrystone median qsort rsort towers multiply mm spmv mt-matmul fl-matmul"

if [ "$1" == "" ]; then
	echo "ERROR: Config is undefined"
	exit -1
fi

if [ "$2" == "" ]; then
	echo "ERROR: Input data directory is undefined"
	exit -1	
fi

echo ">>> Syntax         get_result.sh <config_name>"
echo ">>> Input data     $input_dir"
echo ">>> Config         $config"
echo ""

#====
result_file=result_$config\_riscv_bench.csv
echo "#==== $config ====#" > $result_file
echo "Benchmark, cycle cout, instruction count" >> $result_file

#==== 
for benchmark in $riscv_benchmarks
do
	#==== setup
	bm_log=$input_dir/$benchmark.riscv.log
	cat $bm_log
	cycle=$(grep "cycle" $bm_log)
	instret=$(grep "instret" $bm_log)	
	cpi==$(grep "CPI" $bm_log)
		
	if [ "$instret" == "" ]
	then
		instret=$(grep "instructions" $bm_log)	
	fi

	echo "$benchmark, $cycle, $instret, $cpi" | tee -a $result_file
	echo "" | tee -a $result_file
done
