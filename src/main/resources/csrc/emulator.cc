// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

#include "verilated.h"
#if VM_TRACE
#include <memory>
#include "verilated_vcd_c.h"
#endif

#include "mm.h"
#include "mm_dramsim2.h"

#include <fesvr/dtm.h>
#include "remote_bitbang.h"
#include <iostream>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <getopt.h>

#include "emulator_type.h"

#define MEM_SIZE_BITS  3
#define MEM_LEN_BITS   8
#define MEM_RESP_BITS  2

//#define USE_DRAMSIM2   1

//==========================================================================================
// For option parsing, which is split across this file, Verilog, and
// FESVR's HTIF, a few external files must be pulled in. The list of
// files and what they provide is enumerated:
//
// $RISCV/include/fesvr/htif.h:
//   defines:
//     - HTIF_USAGE_OPTIONS
//     - HTIF_LONG_OPTIONS_OPTIND
//     - HTIF_LONG_OPTIONS
// $(ROCKETCHIP_DIR)/generated-src(-debug)?/$(CONFIG).plusArgs:
//   defines:
//     - PLUSARG_USAGE_OPTIONS
//   variables:
//     - static const char * verilog_plusargs
//==========================================================================================

extern dtm_t* dtm;
extern remote_bitbang_t * jtag;

static uint64_t trace_count = 0;
bool verbose;
bool done_reset;

void handle_sigterm(int sig)
{
  dtm->stop();
}

double sc_time_stamp()
{
  return trace_count;
}

extern "C" int vpi_get_vlog_info(void* arg)
{
  return 0;
}

static void usage(const char * program_name)
{
  printf("Usage: %s [EMULATOR OPTION]... [VERILOG PLUSARG]... [HOST OPTION]... BINARY [TARGET OPTION]...\n",
         program_name);
  fputs("\
Run a BINARY on the Rocket Chip emulator.\n\
\n\
Mandatory arguments to long options are mandatory for short options too.\n\
\n\
EMULATOR OPTIONS\n\
  -c, --cycle-count        Print the cycle count before exiting\n\
       +cycle-count\n\
  -h, --help               Display this help and exit\n\
  -m, --max-cycles=CYCLES  Kill the emulation after CYCLES\n\
       +max-cycles=CYCLES\n\
  -d, --dramsim            Simulate with DRAM models (DRAMSim2)\n\
       +dramsim\n\
  -l, --loadmem            Load RISCV hexa file into main memory\n\
       +loadmem\n\
  -s, --seed=SEED          Use random number seed SEED\n\
  -r, --rbb-port=PORT      Use PORT for remote bit bang (with OpenOCD and GDB) \n\
                           If not specified, a random port will be chosen\n\
                           automatically.\n\
  -V, --verbose            Enable all Chisel printfs (cycle-by-cycle info)\n\
       +verbose\n\
", stdout);
#if VM_TRACE == 0
  fputs("\
\n\
EMULATOR DEBUG OPTIONS (only supported in debug build -- try `make debug`)\n",
        stdout);
#endif
  fputs("\
  -v, --vcd=FILE,          Write vcd trace to FILE (or '-' for stdout)\n\
  -x, --dump-start=CYCLE   Start VCD tracing at CYCLE\n\
       +dump-start\n\
", stdout);
  fputs("\n" PLUSARG_USAGE_OPTIONS, stdout);
  fputs("\n" HTIF_USAGE_OPTIONS, stdout);
  printf("\n"
"EXAMPLES\n"
"  - run a bare metal test:\n"
"    %s $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add\n"
"  - run a bare metal test showing cycle-by-cycle information:\n"
"    %s +verbose $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add 2>&1 | spike-dasm\n"
#if VM_TRACE
"  - run a bare metal test to generate a VCD waveform:\n"
"    %s -v rv64ui-p-add.vcd $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add\n"
#endif
"  - run an ELF (you wrote, called 'hello') using the proxy kernel:\n"
"    %s pk hello\n",
         program_name, program_name, program_name
#if VM_TRACE
         , program_name
#endif
         );
}

int main(int argc, char** argv)
{
  unsigned random_seed = (unsigned)time(NULL) ^ (unsigned)getpid();
  uint64_t max_cycles = -1;
  int ret = 0;
  bool print_cycles = false;

  const char *loadmem = NULL;
	FILE * hexfile = NULL;

	//==== DRAMSim2
	bool dramsim2=false;
	uint64_t memsz_mb = MEM_SIZE / (1024*1024);
  mm_t *mm[N_MEM_CHANNELS];

  // Port numbers are 16 bit unsigned integers.
  uint16_t rbb_port = 0;

#if VM_TRACE
  FILE * vcdfile = NULL;
  uint64_t start = 0;
#endif
  char ** htif_argv = NULL;
  int verilog_plusargs_legal = 1;

  while (1) {
    static struct option long_options[] = {
      {"cycle-count", no_argument,       0, 'c' },
      {"help",        no_argument,       0, 'h' },
      {"max-cycles",  required_argument, 0, 'm' },
#ifdef USE_DRAMSIM2
      {"dramsim",     no_argument,       0, 'd' },
      {"loadmem",     required_argument, 0, 'l' },
#endif
      {"seed",        required_argument, 0, 's' },
      {"rbb-port",    required_argument, 0, 'r' },
      {"verbose",     no_argument,       0, 'V' },
#if VM_TRACE
      {"vcd",         required_argument, 0, 'v' },
      {"dump-start",  required_argument, 0, 'x' },
#endif
      HTIF_LONG_OPTIONS
    };
    int option_index = 0;
#if VM_TRACE
    int c = getopt_long(argc, argv, "-chm:s:r:v:Vx:", long_options, &option_index);
#else
    int c = getopt_long(argc, argv, "-chm:s:r:V", long_options, &option_index);
#endif
    if (c == -1) break;
 retry:
    switch (c) {
      // Process long and short EMULATOR options
      case '?': usage(argv[0]);             return 1;
      case 'c': print_cycles = true;        break;
      case 'h': usage(argv[0]);             return 0;
      case 'm': max_cycles = atoll(optarg); break;
      case 's': random_seed = atoi(optarg); break;
      case 'r': rbb_port = atoi(optarg);    break;
      case 'V': verbose = true;             break;
			// TH
#ifdef USE_DRAMSIM2
      case 'd': dramsim2 = true;             break;
      case 'l': {
        hexfile = strcmp(optarg, "-") == 0 ? stdout : fopen(optarg, "w");
        if (!hexfile) {
          std::cerr << "Unable to open HEX" << optarg << " RISCV file for memory load\n";
          return 1;
        }
				loadmem = optarg;
				fclose(hexfile);
        break;
      }
#endif

#if VM_TRACE
      case 'v': {
        vcdfile = strcmp(optarg, "-") == 0 ? stdout : fopen(optarg, "w");
        if (!vcdfile) {
          std::cerr << "Unable to open " << optarg << " for VCD write\n";
          return 1;
        }
        break;
      }
      case 'x': start = atoll(optarg);      break;
#endif
      // Process legacy '+' EMULATOR arguments by replacing them with
      // their getopt equivalents
      case 1: {
        std::string arg = optarg;
        if (arg.substr(0, 1) != "+") {
          optind--;
          goto done_processing;
        }
        if (arg == "+verbose")
          c = 'V';
        else if (arg.substr(0, 12) == "+max-cycles=") {
          c = 'm';
          optarg = optarg+12;
        }
#if VM_TRACE
        else if (arg.substr(0, 12) == "+dump-start=") {
          c = 'x';
          optarg = optarg+12;
        }
#endif
        else if (arg.substr(0, 12) == "+cycle-count")
          c = 'c';
        // If we don't find a legacy '+' EMULATOR argument, it still could be
        // a VERILOG_PLUSARG and not an error.
        else if (verilog_plusargs_legal) {
          const char ** plusarg = &verilog_plusargs[0];
          int legal_verilog_plusarg = 0;
          while (*plusarg && (legal_verilog_plusarg == 0)){
            if (arg.substr(1, strlen(*plusarg)) == *plusarg) {
              legal_verilog_plusarg = 1;
            }
            plusarg ++;
          }
          if (!legal_verilog_plusarg) {
            verilog_plusargs_legal = 0;
          } else {
            c = 'P';
          }
          goto retry;
        }
        // If we STILL don't find a legacy '+' argument, it still could be
        // an HTIF (HOST) argument and not an error. If this is the case, then
        // we're done processing EMULATOR and VERILOG arguments.
        else {
          static struct option htif_long_options [] = { HTIF_LONG_OPTIONS };
          struct option * htif_option = &htif_long_options[0];
          while (htif_option->name) {
            if (arg.substr(1, strlen(htif_option->name)) == htif_option->name) {
              optind--;
              goto done_processing;
            }
            htif_option++;
          }
          std::cerr << argv[0] << ": invalid plus-arg (Verilog or HTIF) \""
                    << arg << "\"\n";
          c = '?';
        }
        goto retry;
      }
      case 'P': break; // Nothing to do here, Verilog PlusArg
      // Realize that we've hit HTIF (HOST) arguments or error out
      default:
        if (c >= HTIF_LONG_OPTIONS_OPTIND) {
          optind--;
          goto done_processing;
        }
        c = '?';
        goto retry;
    }
  }

done_processing:
  if (optind == argc) {
    std::cerr << "No binary specified for emulator\n";
    usage(argv[0]);
    return 1;
  }
  int htif_argc = 1 + argc - optind;
  htif_argv = (char **) malloc((htif_argc) * sizeof (char *));
  htif_argv[0] = argv[0];
  for (int i = 1; optind < argc;) htif_argv[i++] = argv[optind++];

  if (verbose)
    fprintf(stderr, "using random seed %u\n", random_seed);

  srand(random_seed);
  srand48(random_seed);

  Verilated::randReset(2);
  Verilated::commandArgs(argc, argv);
  TEST_HARNESS *tile = new TEST_HARNESS;

#if VM_TRACE
  Verilated::traceEverOn(true); // Verilator must compute traced signals
  std::unique_ptr<VerilatedVcdFILE> vcdfd(new VerilatedVcdFILE(vcdfile));
  std::unique_ptr<VerilatedVcdC> tfp(new VerilatedVcdC(vcdfd.get()));
  if (vcdfile) {
    tile->trace(tfp.get(), 99);  // Trace 99 levels of hierarchy
    tfp->open("");
  }
#endif

//**** Instantiate DRAMSIM2 memory attached with LLC ****
  uint64_t mem_width = MEM_DATA_BITS / 8;
  // Instantiate and initialize main memory
  for (int i = 0; i < N_MEM_CHANNELS; i++) {
		//TH: use simple memory or DRAMSIM2
		mm[i] = dramsim2 ? (mm_t*)(new mm_dramsim2_t) : (mm_t*)(new mm_magic_t);
		if (dramsim2)
			fprintf(stdout, ">>> DRAM memory is used (connected with DRAMSim2)\n");

    try {
      mm[i]->init(memsz_mb*1024*1024 / N_MEM_CHANNELS, mem_width, CACHE_BLOCK_BYTES);
    } catch (const std::bad_alloc& e) {
      fprintf(stderr,
          "Failed to allocate %ld bytes (%ld MiB) of memory\n"
          "Set smaller amount of memory using +memsize=<N> (in MiB)\n",
              memsz_mb*1024*1024, memsz_mb);
      exit(-1);
    }
  }

  if (loadmem) {
    void *mems[N_MEM_CHANNELS];
    for (int i = 0; i < N_MEM_CHANNELS; i++)
      mems[i] = mm[i]->get_data();
    load_mem(mems, loadmem, CACHE_BLOCK_BYTES, N_MEM_CHANNELS);
		fprintf(stdout, ">>> Application binary %s is stored in DRAM\n", loadmem);
  }
//****

  jtag = new remote_bitbang_t(rbb_port);
  dtm = new dtm_t(htif_argc, htif_argv);

  signal(SIGTERM, handle_sigterm);

  bool dump;
  // reset for several cycles to handle pipelined reset
  for (int i = 0; i < 10; i++) {
    tile->reset = 1;
    tile->clock = 0;
    tile->eval();
#if VM_TRACE
    dump = tfp && trace_count >= start;
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2));
#endif
    tile->clock = 1;
    tile->eval();
#if VM_TRACE
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
#endif
    trace_count ++;
  }
  tile->reset = 0;
  done_reset = true;

//**** Instantiate DRAMSIM2 memory attached with LLC ****
#ifdef USE_DRAMSIM2
  bool_t     *mem_ar_valid[N_MEM_CHANNELS];
  bool_t     *mem_ar_ready[N_MEM_CHANNELS];
  mem_addr_t *mem_ar_bits_addr[N_MEM_CHANNELS];
  mem_id_t   *mem_ar_bits_id[N_MEM_CHANNELS];
  mem_size_t *mem_ar_bits_size[N_MEM_CHANNELS];
  mem_len_t  *mem_ar_bits_len[N_MEM_CHANNELS];

  bool_t     *mem_aw_valid[N_MEM_CHANNELS];
  bool_t     *mem_aw_ready[N_MEM_CHANNELS];
  mem_addr_t *mem_aw_bits_addr[N_MEM_CHANNELS];
  mem_id_t   *mem_aw_bits_id[N_MEM_CHANNELS];
  mem_size_t *mem_aw_bits_size[N_MEM_CHANNELS];
  mem_len_t  *mem_aw_bits_len[N_MEM_CHANNELS];

  bool_t     *mem_w_valid[N_MEM_CHANNELS];
  bool_t     *mem_w_ready[N_MEM_CHANNELS];
  mem_data_t *mem_w_bits_data[N_MEM_CHANNELS];
  mem_strb_t *mem_w_bits_strb[N_MEM_CHANNELS];
  bool_t     *mem_w_bits_last[N_MEM_CHANNELS];

  bool_t     *mem_b_valid[N_MEM_CHANNELS];
  bool_t     *mem_b_ready[N_MEM_CHANNELS];
  mem_resp_t *mem_b_bits_resp[N_MEM_CHANNELS];
  mem_id_t   *mem_b_bits_id[N_MEM_CHANNELS];

  bool_t     *mem_r_valid[N_MEM_CHANNELS];
  bool_t     *mem_r_ready[N_MEM_CHANNELS];
  mem_resp_t *mem_r_bits_resp[N_MEM_CHANNELS];
  mem_id_t   *mem_r_bits_id[N_MEM_CHANNELS];
  mem_data_t *mem_r_bits_data[N_MEM_CHANNELS];
  bool_t     *mem_r_bits_last[N_MEM_CHANNELS];
#endif
//****

  while (!dtm->done() && !jtag->done() &&
         !tile->io_success && trace_count < max_cycles) {
//**** Copy binary into memory storage
#if USE_DRAMSIM2
    for (int i = 0; i < N_MEM_CHANNELS; i++) {
      value(mem_ar_ready[i]) = mm[i]->ar_ready();
      value(mem_aw_ready[i]) = mm[i]->aw_ready();
      value(mem_w_ready[i]) = mm[i]->w_ready();

      value(mem_b_valid[i]) = mm[i]->b_valid();
      value(mem_b_bits_resp[i]) = mm[i]->b_resp();
      value(mem_b_bits_id[i]) = mm[i]->b_id();

      value(mem_r_valid[i]) = mm[i]->r_valid();
      value(mem_r_bits_resp[i]) = mm[i]->r_resp();
      value(mem_r_bits_id[i]) = mm[i]->r_id();
      value(mem_r_bits_last[i]) = mm[i]->r_last();

      memcpy(values(mem_r_bits_data[i]), mm[i]->r_data(), mem_width);
    }
    //value(field(io_debug_resp_ready)) = dtm->resp_ready();
    //value(field(io_debug_req_valid)) = dtm->req_valid();
    //value(field(io_debug_req_bits_addr)) = dtm->req_bits().addr;
    //value(field(io_debug_req_bits_op)) = dtm->req_bits().op;
    //value(field(io_debug_req_bits_data)) = dtm->req_bits().data;
#endif
//****

    tile->clock = 0;
    tile->eval();

#if USE_DRAMSIM2
		/*
    dtm_t::resp debug_resp_bits;
    debug_resp_bits.resp = value(field(io_debug_resp_bits_resp));
    debug_resp_bits.data = value(field(io_debug_resp_bits_data));

    dtm->tick(
      value(field(io_debug_req_ready)),
      value(field(io_debug_resp_valid)),
      debug_resp_bits
    );
		*/
#endif

//****
#if USE_DRAMSIM2
    for (int i = 0; i < N_MEM_CHANNELS; i++) {
      mm[i]->tick(
        value(mem_ar_valid[i]),
        value(mem_ar_bits_addr[i]) - MEM_BASE,
        value(mem_ar_bits_id[i]),
        value(mem_ar_bits_size[i]),
        value(mem_ar_bits_len[i]),

        value(mem_aw_valid[i]),
        value(mem_aw_bits_addr[i]) - MEM_BASE,
        value(mem_aw_bits_id[i]),
        value(mem_aw_bits_size[i]),
        value(mem_aw_bits_len[i]),

        value(mem_w_valid[i]),
        value(mem_w_bits_strb[i]),
        values(mem_w_bits_data[i]),
        value(mem_w_bits_last[i]),

        value(mem_r_ready[i]),
        value(mem_b_ready[i])
      );
    }
#endif

#if VM_TRACE
    dump = tfp && trace_count >= start;
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2));
#endif

    tile->clock = 1;
    tile->eval();
#if VM_TRACE
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
#endif
		// running next cycle
    trace_count++;
  }

#if VM_TRACE
  if (tfp)
    tfp->close();
  if (vcdfile)
    fclose(vcdfile);
#endif

  if (dtm->exit_code())
  {
    fprintf(stderr, "*** FAILED *** via dtm (code = %d, seed %d) after %ld cycles\n", dtm->exit_code(), random_seed, trace_count);
    ret = dtm->exit_code();
  }
  else if (jtag->exit_code())
  {
    fprintf(stderr, "*** FAILED *** via jtag (code = %d, seed %d) after %ld cycles\n", jtag->exit_code(), random_seed, trace_count);
    ret = jtag->exit_code();
  }
  else if (trace_count == max_cycles)
  {
    fprintf(stderr, "*** FAILED *** via trace_count (timeout, seed %d) after %ld cycles\n", random_seed, trace_count);
    ret = 2;
  }
  else if (verbose || print_cycles)
  {
    fprintf(stderr, "Completed after %ld cycles\n", trace_count);
  }

  if (dtm) delete dtm;
  if (jtag) delete jtag;
  if (tile) delete tile;
  if (htif_argv) free(htif_argv);
  return ret;
}
