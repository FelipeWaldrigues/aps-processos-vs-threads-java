import java.util.*;

/**
 * Orquestrador principal do benchmark Processos vs Threads.
 *
 * Fluxo:
 *  1. Gera matrizes A e B (tamanho N×N, definido em MatrixUtils).
 *  2. Valida corretude das versões paralelas contra a multiplicação sequencial
 *     usando uma matriz menor (VALIDATION_N) para agilidade.
 *  3. Dorme SLEEP_MS para o usuário abrir htop / vmstat / perf antes dos picos.
 *  4. Roda benchmark de Threads para 2, 4 e 8 workers.
 *  5. Roda benchmark de Processos para 2, 4 e 8 workers.
 *  6. Imprime tabela comparativa final com speedup relativo.
 *
 * Compilar:  javac *.java
 * Executar:  java -Xmx2g MainBenchmark
 */
public class MainBenchmark {

    // =========================================================================
    //  PARÂMETROS DO BENCHMARK — ajuste conforme necessário
    // =========================================================================
    static final int    N             = MatrixUtils.N; // herdado de MatrixUtils
    static final int[]  WORKER_COUNTS = {2, 4, 8};
    static final int    TOTAL_RUNS    = 5;    // execuções por cenário
    static final int    WARMUP_RUNS   = 1;    // primeiras execuções descartadas (aquecimento da JVM)
    static final int    VALIDATION_N  = 200;  // dimensão da matriz de validação (rápida)
    static final long   SLEEP_MS      = 5_000; // pausa pré-benchmark para monitoramento de CPU/RAM

    // =========================================================================
    //  Armazenamento de resultados para a tabela final
    // =========================================================================
    record BenchResult(String tipo, int workers, double mediams) {}
    static final List<BenchResult> results = new ArrayList<>();

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) throws Exception {

        printHeader();

        // 1. Gera matrizes (fixas para todos os cenários — comparação justa)
        System.out.println("Gerando matrizes " + N + " x " + N + "...");
        double[][] a = MatrixUtils.generate(N, 42L);
        double[][] b = MatrixUtils.generate(N, 7L);
        System.out.println("Matrizes geradas.\n");

        // 2. Validação de corretude
        runValidation();

        // 3. Pausa para preparar o monitoramento
        System.out.printf("Aguardando %d s antes de iniciar o benchmark.%n", SLEEP_MS / 1000);
        System.out.println(">>> Abra htop / vmstat / sar agora e observe os picos de CPU e RAM <<<\n");
        Thread.sleep(SLEEP_MS);

        System.out.println("Iniciando benchmark!\n");

        // 4. Benchmark de Threads
        printSectionHeader("VERSAO B - THREADS (ExecutorService)");
        for (int numThreads : WORKER_COUNTS) {
            double media = runBenchmarkThreads(a, b, numThreads);
            results.add(new BenchResult("Threads", numThreads, media));
        }

        // 5. Benchmark de Processos
        printSectionHeader("VERSAO A - PROCESSOS (ProcessBuilder + IPC via arquivo)");
        for (int numProcs : WORKER_COUNTS) {
            double media = runBenchmarkProcesses(a, b, numProcs);
            results.add(new BenchResult("Processos", numProcs, media));
        }

        // 6. Tabela final
        printSummaryTable();
    }

    // =========================================================================
    //  Validação de corretude (usa matriz menor para ser rápida)
    // =========================================================================
    private static void runValidation() throws Exception {
        System.out.println("=== VALIDACAO DE CORRETUDE (N=" + VALIDATION_N + ") ===");

        double[][] va = MatrixUtils.generate(VALIDATION_N, 1L);
        double[][] vb = MatrixUtils.generate(VALIDATION_N, 2L);

        double[][] expected = MatrixUtils.multiplySequential(va, vb, VALIDATION_N);

        // Valida versão Threads
        double[][] resultThreads = ThreadMultiplier.multiply(va, vb, VALIDATION_N, 2);
        boolean threadOk = MatrixUtils.validate(expected, resultThreads, VALIDATION_N, 1e-6);
        System.out.println("  Threads   (2 workers) -> " + (threadOk ? "CORRETO" : "INCORRETO *** ERRO ***"));

        // Valida versão Processos
        double[][] resultProcs = ProcessMultiplier.multiply(va, vb, VALIDATION_N, 2);
        boolean procOk = MatrixUtils.validate(expected, resultProcs, VALIDATION_N, 1e-6);
        System.out.println("  Processos (2 workers) -> " + (procOk ? "CORRETO" : "INCORRETO *** ERRO ***"));

        if (!threadOk || !procOk) {
            System.err.println("\n[ERRO CRITICO] Validacao falhou. Corrija antes de continuar.");
            System.exit(1);
        }
        System.out.println();
    }

    // =========================================================================
    //  Benchmark de Threads
    //  Retorna a media em ms dos runs apos descarte do aquecimento.
    // =========================================================================
    private static double runBenchmarkThreads(double[][] a, double[][] b, int numThreads)
            throws Exception {
        System.out.printf("  [Threads = %d]%n", numThreads);
        long[] times = new long[TOTAL_RUNS];

        for (int run = 0; run < TOTAL_RUNS; run++) {
            long t0 = System.nanoTime();
            ThreadMultiplier.multiply(a, b, N, numThreads);
            long t1 = System.nanoTime();

            times[run] = t1 - t0;
            String warmupTag = (run < WARMUP_RUNS) ? "  [aquecimento - descartado]" : "";
            System.out.printf("    Run %d: %,.1f ms%s%n", run + 1, toMs(times[run]), warmupTag);
        }

        double media = averageMs(times, WARMUP_RUNS);
        System.out.printf("    --> Media (runs %d-%d): %.2f ms%n%n", WARMUP_RUNS + 1, TOTAL_RUNS, media);
        return media;
    }

    // =========================================================================
    //  Benchmark de Processos
    //  Tempo inclui: serialização de A/B, lançamento das JVMs, computação,
    //  serialização do resultado e leitura. Overhead inerente ao modelo de processos.
    // =========================================================================
    private static double runBenchmarkProcesses(double[][] a, double[][] b, int numProcs)
            throws Exception {
        System.out.printf("  [Processos = %d]%n", numProcs);
        long[] times = new long[TOTAL_RUNS];

        for (int run = 0; run < TOTAL_RUNS; run++) {
            long t0 = System.nanoTime();
            ProcessMultiplier.multiply(a, b, N, numProcs);
            long t1 = System.nanoTime();

            times[run] = t1 - t0;
            String warmupTag = (run < WARMUP_RUNS) ? "  [aquecimento - descartado]" : "";
            System.out.printf("    Run %d: %,.1f ms%s%n", run + 1, toMs(times[run]), warmupTag);
        }

        double media = averageMs(times, WARMUP_RUNS);
        System.out.printf("    --> Media (runs %d-%d): %.2f ms%n%n", WARMUP_RUNS + 1, TOTAL_RUNS, media);
        return media;
    }

    // =========================================================================
    //  Tabela comparativa final
    // =========================================================================
    private static void printSummaryTable() {
        System.out.println();
        System.out.println("==============================================================");
        System.out.println("                   TABELA COMPARATIVA FINAL                  ");
        System.out.printf(" Matriz: %d x %d  |  Runs validos: %d  |  Java: %s%n",
                N, N, TOTAL_RUNS - WARMUP_RUNS, System.getProperty("java.version"));
        System.out.println("==============================================================");
        System.out.printf("  %-12s  %8s  %14s  %10s%n",
                "Tipo", "Workers", "Media (ms)", "Speedup*");
        System.out.println("  ------------  --------  --------------  ----------");

        // Speedup relativo ao melhor tempo de Threads com 2 workers
        double baseline = results.stream()
                .filter(r -> r.tipo().equals("Threads") && r.workers() == 2)
                .findFirst()
                .map(BenchResult::mediams)
                .orElse(1.0);

        for (BenchResult r : results) {
            System.out.printf("  %-12s  %8d  %14.2f  %9.2fx%n",
                    r.tipo(), r.workers(), r.mediams(), baseline / r.mediams());
        }

        System.out.println("==============================================================");
        System.out.println("  * Speedup relativo ao tempo de Threads com 2 workers.");
        System.out.println("    Valores > 1.0x indicam desempenho superior a essa baseline.");
        System.out.println("==============================================================");
    }

    // =========================================================================
    //  Helpers de formatação e cálculo
    // =========================================================================

    private static double toMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    /** Calcula a media em ms descartando os primeiros skipFirst elementos. */
    private static double averageMs(long[] times, int skipFirst) {
        long sum = 0;
        int count = times.length - skipFirst;
        for (int i = skipFirst; i < times.length; i++) sum += times[i];
        return (sum / (double) count) / 1_000_000.0;
    }

    private static void printHeader() {
        System.out.println("==============================================================");
        System.out.println("         BENCHMARK: Processos vs Threads em Java              ");
        System.out.println("==============================================================");
        System.out.printf("  Dimensao           : %d x %d%n", N, N);
        System.out.printf("  Runs por cenario   : %d (descartando %d de aquecimento)%n",
                TOTAL_RUNS, WARMUP_RUNS);
        System.out.printf("  Configuracoes      : %s workers%n", Arrays.toString(WORKER_COUNTS));
        System.out.printf("  JVM                : %s%n", System.getProperty("java.version"));
        System.out.printf("  CPUs disponiveis   : %d%n", Runtime.getRuntime().availableProcessors());
        System.out.println("==============================================================");
        System.out.println();
    }

    private static void printSectionHeader(String title) {
        System.out.println("--------------------------------------------------------------");
        System.out.println("  " + title);
        System.out.println("--------------------------------------------------------------");
    }
}
