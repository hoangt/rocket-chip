#1/bin/bash

outdir=./output
config=$1

riscv_benchmarks="dhrystone median qsort rsort towers multiply mm spmv mt-matmul"

if [ "$1" == "" ]
then
	config=BigCoreDefaultCache
fi

echo ">>> Syntax         get_result.sh <config_name>"
echo ">>> Config         $config"
echo ""

#====
result_file=result_$config\_riscv_bench.csv
echo "Benchmark, cycle cout, instruction count" > $result_file

#==== 
for benchmark in $riscv_benchmarks
do
	#==== setup
	bm_log=$outdir/$benchmark.riscv.log
	cat $bm_log
	cycle=$(grep "cycle" $bm_log)
	instret=$(grep "instret" $bm_log)	
	cpi==$(grep "CPI" $bm_log)
		
	if [ "$instret" == "" ]
	then
		instret=$(grep "instructions" $bm_log)	
	fi

	echo "$benchmark, $cycle, $instret, $cpi" | tee -a $result_file
done
