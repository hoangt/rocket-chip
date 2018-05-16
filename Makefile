#==== Build opts ====
ROCKET_DIR=/home/hoangt/WORK/TOOLS/risc-v/rocket-chip
CONFIG=DefaultConfig
N=8

#==== TRACE opts ====
TRACE_FILE=
RISCV_BIN=$(ROCKET_DIR)/toolchain/bin
GEM5_DIR=/home/hoangt/WORK/TOOLS/Gem5/gem5-base/gem5

#==== TEST opts ====
RISCV_TESTS_ISA=$(ROCKET_DIR)/riscv-tests/isa/rv64um

#==== EMU opts ====
EMU_MODE?=

#***************************************
#==== Update rules ====
update:
	@git pull origin master
	@git submodule update --init
	@git submodule update --init --recursive

#==== Build rules ==== 
build-tools:
	rm -rf toolchain
	cd $(ROCKET_DIR)/riscv-tools && git submodule update --init --recursive
	cd $(ROCKET_DIR)/riscv-tools && ./build.sh
	cd $(ROCKET_DIR)/riscv-tools && ./build-rv32ima.sh

#==== Genreate emulator and verilog ====
gen-emu:
	cd $(ROCKET_DIR)/emulator && make -j$(N) $(EMU_MODE) CONFIG=$(CONFIG)

gen-vlog:
	cd $(ROCKET_DIR)/vsim && make -j$(N) $(EMU_MODE) verilog CONFIG=$(CONFIG) 

gen-ev:
	make gen-emu  CONFIG=$(CONFIG)
	make gen-vlog CONFIG=$(CONFIG)

#====	Simulation rules ==== 
run-asm:
	cd $(ROCKET_DIR)/vsim && make -j$(N)  $(EMU_MODE) CONFIG=$(CONFIG) run-asm-tests
	cd $(ROCKET_DIR)/vsim && make  -j$(N) $(EMU_MODE) CONFIG=$(CONFIG) run-bmark-tests

run-emuvcd:
	cd $(ROCKET_DIR)/emulator && make -j$(N) $(EMU_MODE) CONFIG=$(CONFIG) run-asm-tests-debug
	cd $(ROCKET_DIR)/emulator && make -j$(N) $(EMU_MODE) CONFIG=$(CONFIG) run-bmark-tests-debug

#==== Verilog generator rules ==== 
gen-vlog:
	cd $(ROCKET_DIR)/vsim && make -j$(N) verilog CONFIG=$(CONFIG)

#==== VLSI rules ==== 
run-dc:

run-vcs:

run-ics:

run-vsim:

#==== FPGA rules ==== 
run-fpga:
	

#====
help:
	@grep ":" Makefile	
