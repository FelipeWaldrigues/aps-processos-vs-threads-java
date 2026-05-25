import java.util.concurrent.*;

/**
 * Versão B — Multiplicação paralela com Threads.
 *
 * Estratégia: divide as linhas da matriz resultado C em faixas contíguas.
 * Cada thread recebe [startRow, endRow) e lê A e B por referência (heap compartilhada).
 * Não há sincronização no loop interno porque cada thread escreve em linhas distintas de C.
 */
public class ThreadMultiplier {

    /**
     * Multiplica A × B usando um pool de {@code numThreads} threads.
     *
     * @param a          matriz A (n × n), compartilhada por referência
     * @param b          matriz B (n × n), compartilhada por referência
     * @param n          dimensão das matrizes
     * @param numThreads número de threads trabalhadoras
     * @return           matriz resultado C (n × n)
     */
    public static double[][] multiply(double[][] a, double[][] b, int n, int numThreads)
            throws InterruptedException, ExecutionException {

        double[][] c = new double[n][n];

        ExecutorService pool  = Executors.newFixedThreadPool(numThreads);
        CountDownLatch  latch = new CountDownLatch(numThreads);

        int rowsPerThread = n / numThreads;

        for (int t = 0; t < numThreads; t++) {
            final int startRow = t * rowsPerThread;
            // última thread absorve eventuais linhas residuais se n não for divisível
            final int endRow = (t == numThreads - 1) ? n : startRow + rowsPerThread;

            pool.submit(() -> {
                try {
                    computeRows(a, b, c, n, startRow, endRow);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();          // aguarda todas as threads terminarem
        pool.shutdown();
        return c;
    }

    /** Kernel de multiplicação ikj para as linhas [startRow, endRow). */
    private static void computeRows(double[][] a, double[][] b, double[][] c,
                                    int n, int startRow, int endRow) {
        for (int i = startRow; i < endRow; i++) {
            for (int k = 0; k < n; k++) {
                double aik = a[i][k];
                for (int j = 0; j < n; j++) {
                    c[i][j] += aik * b[k][j];
                }
            }
        }
    }
}
